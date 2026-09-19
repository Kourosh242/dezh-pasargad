package com.pasargad.dezh.presentation.backup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pasargad.dezh.backup.BackupManager
import com.pasargad.dezh.data.backup.BackupFileException
import com.pasargad.dezh.data.backup.BackupFileGateway
import com.pasargad.dezh.data.backup.FileFailure
import com.pasargad.dezh.domain.PasswordStrengthMeter
import com.pasargad.dezh.presentation.vault.MeterBand
import com.pasargad.dezh.settings.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Export-side failures, each with a dedicated user-facing message. */
enum class BackupUiError {
    INVALID_PATH,
    INSUFFICIENT_STORAGE,
    IO_ERROR,
    CORRUPTED_OUTPUT,
    CANCELED,
}

data class BackupUiState(
    val passphrase: String = "",
    val confirmPassphrase: String = "",
    val meterScore: Int = 0,
    val meterBand: MeterBand = MeterBand.WEAK,
    val busy: Boolean = false,
    val successCount: Int? = null,
    val error: BackupUiError? = null,
    val defaultFilename: String = com.pasargad.dezh.settings.DezhSettings.DEFAULT_BACKUP_FILENAME,
) {
    val canExport: Boolean
        get() = !busy && passphrase.isNotEmpty() && passphrase == confirmPassphrase
}

/**
 * Encrypted backup export state holder. The passphrase lives only in memory,
 * is never logged, and is only ever fed to the BackupManager crypto boundary.
 */
class BackupViewModel(
    private val backupManager: BackupManager,
    private val fileGateway: BackupFileGateway,
    private val settingsRepository: SettingsRepository,
    private val strengthMeter: PasswordStrengthMeter = PasswordStrengthMeter(),
) : ViewModel() {

    private val _uiState = MutableStateFlow(BackupUiState())
    val uiState: StateFlow<BackupUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val settings = settingsRepository.current()
            _uiState.update { it.copy(defaultFilename = settings.backupFilenameBase) }
        }
    }

    fun onCanceledFilePicker() = _uiState.update { it.copy(error = BackupUiError.CANCELED) }

    fun onPassphraseChanged(value: String) = _uiState.update {
        val meter = strengthMeter.evaluate(value.toCharArray())
        it.copy(
            passphrase = value,
            meterScore = meter.score,
            meterBand = bandOf(meter.band),
            successCount = null,
        )
    }

    fun onConfirmPassphraseChanged(value: String) = _uiState.update {
        it.copy(confirmPassphrase = value, successCount = null)
    }

    fun dismissError() = _uiState.update { it.copy(error = null) }

    /** Entry point after the SAF CreateDocument launcher returns a URI (null = user canceled). */
    fun onExportLocationPicked(uri: android.net.Uri?) {
        if (uri == null) {
            onCanceledFilePicker()
            return
        }
        val current = _uiState.value
        if (!current.canExport) return
        _uiState.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            val result = runCatching {
                val bytes = backupManager.export(current.passphrase.toCharArray())
                fileGateway.write(uri, bytes)
                // Verify the written artifact end-to-end: catches corrupted output,
                // partial writes and storage truncation before we claim success.
                val written = fileGateway.read(uri)
                check(written.contentEquals(bytes)) { "Written backup differs from source bytes" }
                backupManager.readHeader(written)
            }
            _uiState.update { state ->
                result.fold(
                    onSuccess = { header ->
                        state.copy(busy = false, successCount = header.entryCount, error = null)
                    },
                    onFailure = { failure ->
                        state.copy(busy = false, error = mapFailure(failure))
                    },
                )
            }
        }
    }

    private fun mapFailure(failure: Throwable): BackupUiError = when {
        failure is BackupFileException -> when (failure.failure) {
            FileFailure.INVALID_PATH -> BackupUiError.INVALID_PATH
            FileFailure.INSUFFICIENT_STORAGE -> BackupUiError.INSUFFICIENT_STORAGE
            FileFailure.FILE_TOO_LARGE -> BackupUiError.IO_ERROR
            FileFailure.EMPTY_FILE -> BackupUiError.CORRUPTED_OUTPUT
            FileFailure.IO_ERROR -> BackupUiError.IO_ERROR
        }

        failure is IllegalStateException || failure is com.pasargad.dezh.backup.BackupParseException ->
            BackupUiError.CORRUPTED_OUTPUT

        else -> BackupUiError.IO_ERROR
    }

    private fun bandOf(band: PasswordStrengthMeter.Band): MeterBand = when (band) {
        PasswordStrengthMeter.Band.WEAK -> MeterBand.WEAK
        PasswordStrengthMeter.Band.FAIR -> MeterBand.FAIR
        PasswordStrengthMeter.Band.STRONG -> MeterBand.STRONG
    }
}
