package com.pasargad.dezh.presentation.unlock

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pasargad.dezh.domain.UnlockVaultUseCase
import com.pasargad.dezh.domain.VaultSecurityException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class UnlockError { WrongPassword, Backoff, Corrupted, NotSetUp }

data class UnlockUiState(
    val isSubmitting: Boolean = false,
    val error: UnlockError? = null,
    val backoffRemainingSeconds: Int = 0,
)

/**
 * Unlock flow state holder. Failure behavior:
 *  - wrong password → generic error + cooldown (from repository policy)
 *  - corrupted data → generic "data corrupted" message (no details)
 * The password is consumed by the use case and zeroed in `finally`.
 */
class UnlockViewModel(
    private val unlock: UnlockVaultUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(UnlockUiState())
    val uiState: StateFlow<UnlockUiState> = _uiState.asStateFlow()

    private var backoffJob: Job? = null

    fun onInputChanged() {
        _uiState.update { if (it.backoffRemainingSeconds == 0) it.copy(error = null) else it }
    }

    fun submit(password: CharArray) {
        val current = _uiState.value
        if (current.isSubmitting || current.backoffRemainingSeconds > 0) return
        _uiState.update { it.copy(isSubmitting = true, error = null) }
        viewModelScope.launch {
            try {
                unlock(password)
                // Success → global lock state flips to Unlocked; navigation reacts.
            } catch (e: VaultSecurityException.WrongMasterPassword) {
                startBackoffCountdown(e.nextAttemptDelayMillis)
                _uiState.update { it.copy(isSubmitting = false, error = UnlockError.WrongPassword) }
            } catch (e: VaultSecurityException.BackoffRequired) {
                startBackoffCountdown(e.remainingMillis)
                _uiState.update { it.copy(isSubmitting = false, error = UnlockError.Backoff) }
            } catch (_: VaultSecurityException.VaultNotSetUp) {
                _uiState.update { it.copy(isSubmitting = false, error = UnlockError.NotSetUp) }
            } catch (_: VaultSecurityException) {
                _uiState.update { it.copy(isSubmitting = false, error = UnlockError.Corrupted) }
            } finally {
                password.fill('\u0000')
            }
        }
    }

    private fun startBackoffCountdown(millis: Long) {
        backoffJob?.cancel()
        backoffJob = viewModelScope.launch {
            var remaining = millis
            while (remaining > 0) {
                _uiState.update {
                    it.copy(backoffRemainingSeconds = ((remaining + MS_ROUND_UP - 1) / MS_PER_SECOND).toInt())
                }
                val step = minOf(remaining, COUNTDOWN_STEP_MS)
                delay(step)
                remaining -= step
            }
            _uiState.update { it.copy(backoffRemainingSeconds = 0) }
        }
    }

    private companion object {
        const val MS_PER_SECOND = 1_000L
        const val MS_ROUND_UP = 1L
        const val COUNTDOWN_STEP_MS = 250L
    }

    override fun onCleared() {
        backoffJob?.cancel()
    }
}
