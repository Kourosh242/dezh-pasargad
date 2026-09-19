package com.pasargad.dezh.presentation.setup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pasargad.dezh.domain.PasswordStrengthValidator
import com.pasargad.dezh.domain.SetupMasterPasswordUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class SetupError { AlreadySetUp, Unknown }

data class SetupUiState(
    val rating: PasswordStrengthValidator.Rating? = null,
    val failures: List<PasswordStrengthValidator.Failure> = emptyList(),
    val isValid: Boolean = false,
    val passwordsMatch: Boolean = true,
    val isSubmitting: Boolean = false,
    val error: SetupError? = null,
)

/**
 * Owns the master-password creation flow. The password lives here only for the
 * duration of a submission (as char[]), is never logged, never placed into
 * StateFlow values, and is zeroed after the use case consumes it.
 */
class SetupViewModel(
    private val setup: SetupMasterPasswordUseCase,
    private val strengthValidator: PasswordStrengthValidator,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SetupUiState())
    val uiState: StateFlow<SetupUiState> = _uiState.asStateFlow()

    fun onPasswordChanged(password: CharArray) {
        val result = strengthValidator.evaluate(password)
        _uiState.update {
            it.copy(
                rating = result.rating,
                failures = result.failures,
                isValid = result.isValid,
                error = null,
            )
        }
    }

    fun onConfirmationChanged(matches: Boolean) {
        _uiState.update { it.copy(passwordsMatch = matches) }
    }

    fun submit(password: CharArray, confirmation: CharArray) {
        val current = _uiState.value
        if (current.isSubmitting) return
        if (password.size != confirmation.size || !password.contentEquals(confirmation)) {
            _uiState.update { it.copy(passwordsMatch = false) }
            return
        }
        _uiState.update { it.copy(isSubmitting = true, error = null) }
        viewModelScope.launch {
            try {
                setup(password)
                // Success: repository flips the global lock state to Unlocked,
                // navigation reacts. Nothing sensitive is kept in this VM.
            } catch (_: com.pasargad.dezh.domain.VaultSecurityException.VaultAlreadySetUp) {
                _uiState.update { it.copy(isSubmitting = false, error = SetupError.AlreadySetUp) }
            } catch (_: Exception) {
                _uiState.update { it.copy(isSubmitting = false, error = SetupError.Unknown) }
            } finally {
                password.fill('\u0000')
                confirmation.fill('\u0000')
            }
        }
    }
}
