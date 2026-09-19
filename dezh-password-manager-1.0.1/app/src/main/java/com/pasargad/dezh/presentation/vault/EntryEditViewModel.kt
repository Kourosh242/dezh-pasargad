package com.pasargad.dezh.presentation.vault

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pasargad.dezh.domain.CreateEntryUseCase
import com.pasargad.dezh.domain.GetEntryUseCase
import com.pasargad.dezh.domain.UpdateEntryUseCase
import com.pasargad.dezh.domain.PasswordStrengthMeter
import com.pasargad.dezh.domain.VaultEntryDraft
import com.pasargad.dezh.generator.PasswordGenerator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class EntryEditError { SaveFailed }

enum class MeterBand { WEAK, FAIR, STRONG }

data class EntryEditUiState(
    val isEdit: Boolean = false,
    val title: String = "",
    val username: String = "",
    val email: String = "",
    val password: String = "",
    val notes: String = "",
    val category: String = "",
    val favorite: Boolean = false,
    val isSaving: Boolean = false,
    val isTitleInvalid: Boolean = false,
    val meterScore: Int = 0,
    val meterBand: MeterBand = MeterBand.WEAK,
    val error: EntryEditError? = null,
    val saved: Boolean = false,
)

/**
 * Add/Edit form state holder. Field values are plain form state until [save];
 * they become sensitive only when the use case/repository encrypts them.
 */
class EntryEditViewModel(
    private val initialEntryId: String?,
    private val createEntry: CreateEntryUseCase,
    private val updateEntry: UpdateEntryUseCase,
    private val getEntry: GetEntryUseCase,
    private val passwordGenerator: PasswordGenerator,
    private val strengthMeter: PasswordStrengthMeter,
    private val defaultCategoryProvider: (suspend () -> String)? = null,
) : ViewModel() {

    private val _uiState = MutableStateFlow(EntryEditUiState(isEdit = initialEntryId != null))
    val uiState: StateFlow<EntryEditUiState> = _uiState.asStateFlow()

    init {
        // New-entry form: preselect the configured default category (Phase 5).
        if (initialEntryId == null) {
            viewModelScope.launch {
                val defaultCategory = runCatching { defaultCategoryProvider?.invoke().orEmpty() }.getOrDefault("")
                if (defaultCategory.isNotBlank()) {
                    _uiState.update { state -> if (state.category.isBlank()) state.copy(category = defaultCategory) else state }
                }
            }
        }
        if (initialEntryId != null) {
            viewModelScope.launch {
                getEntry(initialEntryId)?.let { entry ->
                    // Evaluate the loaded password's strength too — otherwise an
                    // existing strong password showed a stuck "weak" red meter.
                    val meter = strengthMeter.evaluate(entry.password.toCharArray())
                    _uiState.update {
                        it.copy(
                            title = entry.title,
                            username = entry.username,
                            email = entry.email,
                            password = entry.password,
                            notes = entry.notes,
                            category = entry.category,
                            favorite = entry.favorite,
                            meterScore = meter.score,
                            meterBand = when (meter.band) {
                                PasswordStrengthMeter.Band.WEAK -> MeterBand.WEAK
                                PasswordStrengthMeter.Band.FAIR -> MeterBand.FAIR
                                PasswordStrengthMeter.Band.STRONG -> MeterBand.STRONG
                            },
                        )
                    }
                }
            }
        }
    }

    fun onTitleChanged(value: String) = _uiState.update { it.copy(title = value, isTitleInvalid = false) }
    fun onUsernameChanged(value: String) = _uiState.update { it.copy(username = value) }
    fun onEmailChanged(value: String) = _uiState.update { it.copy(email = value) }
    fun onPasswordChanged(value: String) = _uiState.update {
        val meter = strengthMeter.evaluate(value.toCharArray())
        it.copy(
            password = value,
            meterScore = meter.score,
            meterBand = when (meter.band) {
                PasswordStrengthMeter.Band.WEAK -> MeterBand.WEAK
                PasswordStrengthMeter.Band.FAIR -> MeterBand.FAIR
                PasswordStrengthMeter.Band.STRONG -> MeterBand.STRONG
            },
        )
    }

    /** Fills the password field with a freshly generated secure password (never logged). */
    fun onGeneratePassword() = onPasswordChanged(passwordGenerator.generate(PasswordGenerator.Options()))
    fun onNotesChanged(value: String) = _uiState.update { it.copy(notes = value) }
    fun onCategoryChanged(value: String) = _uiState.update { it.copy(category = value) }
    fun onFavoriteChanged(value: Boolean) = _uiState.update { it.copy(favorite = value) }

    fun save() {
        val current = _uiState.value
        if (current.isSaving) return
        if (current.title.isBlank()) {
            _uiState.update { it.copy(isTitleInvalid = true) }
            return
        }
        _uiState.update { it.copy(isSaving = true, error = null) }
        viewModelScope.launch {
            val draft = VaultEntryDraft(
                title = current.title,
                username = current.username,
                email = current.email,
                password = current.password,
                notes = current.notes,
                category = current.category,
                favorite = current.favorite,
            )
            try {
                if (current.isEdit && initialEntryId != null) {
                    updateEntry(initialEntryId, draft)
                } else {
                    createEntry(draft)
                }
                _uiState.update { it.copy(isSaving = false, saved = true) }
            } catch (_: IllegalArgumentException) {
                _uiState.update { it.copy(isSaving = false, isTitleInvalid = true) }
            } catch (_: Exception) {
                _uiState.update { it.copy(isSaving = false, error = EntryEditError.SaveFailed) }
            }
        }
    }
}
