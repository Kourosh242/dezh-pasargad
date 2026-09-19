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
    private val quick = MutableStateFlow(QuickGeneratorState())
    val uiState: StateFlow<EntryEditUiState> = _uiState.asStateFlow()

    init {
        // New-entry form: preselect the configured default category.
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

    /** Live sheet state — never touches the form until [onQuickUse]. */
    val quickGenerator: StateFlow<QuickGeneratorState> = quick.asStateFlow()

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

    /**
     * Quick generator sheet (Proton-Pass-style inline flow): generate, tune and
     * adopt a password without leaving the edit form. Options are local to the
     * sheet; the form field only changes when the user adopts the result.
     */
    fun onQuickSheetOpened() = regenerateQuick()

    /** Single entry point for every sheet control — partial updates allowed. */
    fun onQuickOptionsChanged(
        length: Int = quick.value.length,
        uppercase: Boolean = quick.value.includeUppercase,
        lowercase: Boolean = quick.value.includeLowercase,
        digits: Boolean = quick.value.includeDigits,
        symbols: Boolean = quick.value.includeSymbols,
        excludeAmbiguous: Boolean = quick.value.excludeAmbiguous,
    ) {
        quick.update {
            it.copy(
                length = length,
                includeUppercase = uppercase,
                includeLowercase = lowercase,
                includeDigits = digits,
                includeSymbols = symbols,
                excludeAmbiguous = excludeAmbiguous,
            )
        }
        regenerateQuick()
    }

    /** Adopts the sheet's password into the form field (the only write path). */
    fun onQuickUse() {
        val password = quick.value.password
        if (password.isNotEmpty()) onPasswordChanged(password)
    }

    private fun regenerateQuick() {
        val options = quick.value
        val password = try {
            passwordGenerator.generate(
                PasswordGenerator.Options(
                    length = options.length,
                    includeUppercase = options.includeUppercase,
                    includeLowercase = options.includeLowercase,
                    includeDigits = options.includeDigits,
                    includeSymbols = options.includeSymbols,
                    excludeAmbiguous = options.excludeAmbiguous,
                    minUppercase = if (options.includeUppercase) 1 else 0,
                    minLowercase = if (options.includeLowercase) 1 else 0,
                    minDigits = if (options.includeDigits) 1 else 0,
                    minSymbols = if (options.includeSymbols) 1 else 0,
                ),
            )
        } catch (_: IllegalArgumentException) {
            ""
        }
        quick.update { it.copy(password = password, error = password.isEmpty()) }
    }
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

/** Options + live result of the quick generator sheet. */
data class QuickGeneratorState(
    val password: String = "",
    val length: Int = PasswordGenerator.Options.DEFAULT_LENGTH,
    val includeUppercase: Boolean = true,
    val includeLowercase: Boolean = true,
    val includeDigits: Boolean = true,
    val includeSymbols: Boolean = true,
    val excludeAmbiguous: Boolean = false,
    val error: Boolean = false,
)
