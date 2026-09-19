package com.pasargad.dezh.presentation.backup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pasargad.dezh.backup.BackupManager
import com.pasargad.dezh.backup.BackupParseException
import com.pasargad.dezh.backup.BackupPayload
import com.pasargad.dezh.backup.ParseFailure
import com.pasargad.dezh.backup.RestoreMode
import com.pasargad.dezh.backup.RestoreSummary
import com.pasargad.dezh.data.backup.BackupFileException
import com.pasargad.dezh.data.backup.BackupFileGateway
import com.pasargad.dezh.data.backup.FileFailure
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Restore-side failures with dedicated user-facing messages. */
enum class RestoreUiError {
    INVALID_PATH,
    EMPTY_FILE,
    FILE_TOO_LARGE,
    IO_ERROR,
    INVALID_FORMAT,
    UNSUPPORTED_VERSION,
    INTEGRITY_FAILURE,
    CORRUPTED,
    INCOMPLETE,
    WRONG_PASSWORD,
    IMPORT_FAILURE,
}

/** Stages of the restore flow — the user can cancel until the final apply. */
enum class RestoreStage { PICK_FILE, PASSPHRASE, CHOOSE_MODE, CONFIRM_REPLACE, RUNNING, DONE, FAILED }

data class RestoreUiState(
    val stage: RestoreStage = RestoreStage.PICK_FILE,
    val headerVersion: Int? = null,
    val headerCreatedAtEpochMs: Long? = null,
    val previewCount: Int? = null,
    val passphrase: String = "",
    val payload: BackupPayload? = null,
    val summary: RestoreSummary? = null,
    val error: RestoreUiError? = null,
) {
    val busy: Boolean get() = stage == RestoreStage.RUNNING
}

/**
 * Secure restore state holder. Enforces the non-destructive flow:
 * file → header checks (before any passphrase) → decrypt preview →
 * explicit mode choice with consequence text → confirmation for REPLACE.
 * A raw BackupPayload is only kept in memory between preview and apply.
 */
@Suppress("TooManyFunctions") // one handler per user action in the multi-stage flow
class RestoreViewModel(
    private val backupManager: BackupManager,
    private val fileGateway: BackupFileGateway,
) : ViewModel() {

    private val _uiState = MutableStateFlow(RestoreUiState())
    val uiState: StateFlow<RestoreUiState> = _uiState.asStateFlow()

    fun reset() = _uiState.update { RestoreUiState() }

    fun dismissError() = _uiState.update { it.copy(error = null) }

    fun onCanceledFilePicker() = _uiState.update { RestoreUiState() }

    fun onPassphraseChanged(value: String) = _uiState.update { it.copy(passphrase = value) }

    /** Entry point after the SAF OpenDocument launcher returns (null = canceled). */
    fun onFilePicked(uri: android.net.Uri?) {
        if (uri == null) {
            onCanceledFilePicker()
            return
        }
        _uiState.update { RestoreUiState(stage = RestoreStage.PICK_FILE) }
        viewModelScope.launch {
            val result = runCatching { fileGateway.read(uri) }
            result.fold(
                onSuccess = { bytes -> inspect(bytes) },
                onFailure = { failure -> fail(mapFileFailure(failure)) },
            )
        }
    }

    fun onPassphraseSubmitted() {
        val state = _uiState.value
        val bytes = pendingFileBytes ?: return
        if (state.passphrase.isEmpty()) return
        _uiState.update { it.copy(stage = RestoreStage.RUNNING, error = null) }
        viewModelScope.launch {
            try {
                val payload = backupManager.decrypt(bytes, state.passphrase.toCharArray())
                _uiState.update {
                    it.copy(
                        stage = RestoreStage.CHOOSE_MODE,
                        payload = payload,
                        previewCount = payload.entries.size,
                        error = null,
                    )
                }
            } catch (failure: BackupParseException) {
                fail(mapParseFailure(failure.reason))
            } catch (_: Exception) {
                fail(RestoreUiError.IMPORT_FAILURE)
            }
        }
    }

    /** MERGE: add missing entries + update strictly-newer ones; nothing is deleted. */
    fun onMergeChosen() = applyMode(RestoreMode.MERGE)

    /** REPLACE: destructive — requires the extra confirmation stage first. */
    fun onReplaceChosen() = _uiState.update { it.copy(stage = RestoreStage.CONFIRM_REPLACE) }

    /** The explicit, final destructive confirmation — the only path that wipes the vault. */
    fun onReplaceConfirmed() = applyMode(RestoreMode.REPLACE)

    fun onReplaceConfirmationDeclined() = _uiState.update { it.copy(stage = RestoreStage.CHOOSE_MODE) }

    fun onCancelAfterPreview() = reset()

    private fun applyMode(mode: RestoreMode) {
        val payload = _uiState.value.payload ?: return
        _uiState.update { it.copy(stage = RestoreStage.RUNNING, error = null) }
        viewModelScope.launch {
            try {
                val summary = backupManager.restore(payload, mode)
                _uiState.update { it.copy(stage = RestoreStage.DONE, summary = summary, payload = null) }
            } catch (_: IllegalArgumentException) {
                fail(RestoreUiError.IMPORT_FAILURE)
            } catch (_: Exception) {
                fail(RestoreUiError.IMPORT_FAILURE)
            }
        }
        pendingFileBytes = null
    }

    private var pendingFileBytes: ByteArray? = null

    private fun inspect(bytes: ByteArray) {
        pendingFileBytes = bytes
        try {
            val header = backupManager.readHeader(bytes)
            _uiState.update {
                it.copy(
                    stage = RestoreStage.PASSPHRASE,
                    headerVersion = header.version,
                    headerCreatedAtEpochMs = header.createdAtEpochMs,
                    error = null,
                )
            }
        } catch (failure: BackupParseException) {
            pendingFileBytes = null
            fail(mapParseFailure(failure.reason))
        } catch (_: Exception) {
            pendingFileBytes = null
            fail(RestoreUiError.IMPORT_FAILURE)
        }
    }

    private fun fail(error: RestoreUiError) = _uiState.update {
        it.copy(stage = RestoreStage.FAILED, error = error, payload = null)
    }

    private fun mapFileFailure(failure: Throwable): RestoreUiError = when {
        failure !is BackupFileException -> RestoreUiError.IO_ERROR
        else -> when (failure.failure) {
            FileFailure.INVALID_PATH -> RestoreUiError.INVALID_PATH
            FileFailure.INSUFFICIENT_STORAGE -> RestoreUiError.IO_ERROR
            FileFailure.FILE_TOO_LARGE -> RestoreUiError.FILE_TOO_LARGE
            FileFailure.EMPTY_FILE -> RestoreUiError.EMPTY_FILE
            FileFailure.IO_ERROR -> RestoreUiError.IO_ERROR
        }
    }

    private fun mapParseFailure(reason: ParseFailure): RestoreUiError = when (reason) {
        ParseFailure.INVALID_FORMAT -> RestoreUiError.INVALID_FORMAT
        ParseFailure.UNSUPPORTED_VERSION -> RestoreUiError.UNSUPPORTED_VERSION
        ParseFailure.CORRUPTED -> RestoreUiError.CORRUPTED
        ParseFailure.INCOMPLETE -> RestoreUiError.INCOMPLETE
        ParseFailure.INTEGRITY_FAILURE -> RestoreUiError.INTEGRITY_FAILURE
        ParseFailure.WRONG_PASSWORD -> RestoreUiError.WRONG_PASSWORD
    }
}
