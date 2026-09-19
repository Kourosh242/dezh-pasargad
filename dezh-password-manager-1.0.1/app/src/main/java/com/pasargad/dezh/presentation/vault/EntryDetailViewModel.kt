package com.pasargad.dezh.presentation.vault

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pasargad.dezh.domain.DeleteEntryUseCase
import com.pasargad.dezh.domain.ObserveEntryUseCase
import com.pasargad.dezh.domain.ToggleFavoriteUseCase
import com.pasargad.dezh.domain.VaultEntry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class EntryDetailUiState(
    val entry: VaultEntry? = null,
    val passwordRevealed: Boolean = false,
    val deleted: Boolean = false,
)

/**
 * Entry details state holder. The plaintext password is shown only after an
 * explicit reveal action; plaintext lives only in the unlocked session's memory.
 */
class EntryDetailViewModel(
    entryId: String,
    observeEntry: ObserveEntryUseCase,
    private val toggleFavorite: ToggleFavoriteUseCase,
    private val deleteEntry: DeleteEntryUseCase,
) : ViewModel() {

    private val revealed = MutableStateFlow(false)
    private val deleted = MutableStateFlow(false)

    val uiState: StateFlow<EntryDetailUiState> = combine(
        observeEntry(entryId),
        revealed,
        deleted,
    ) { entry, isRevealed, isDeleted ->
        EntryDetailUiState(entry = entry, passwordRevealed = isRevealed, deleted = isDeleted)
    }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), EntryDetailUiState())

    fun togglePasswordRevealed() = revealed.update { !it }

    fun onFavoriteToggled(favorite: Boolean) {
        val id = uiState.value.entry?.id ?: return
        viewModelScope.launch { toggleFavorite(id, favorite) }
    }

    fun onDeleteConfirmed() {
        val id = uiState.value.entry?.id ?: return
        viewModelScope.launch {
            deleteEntry(id)
            // Signal navigation BEFORE the Room invalidation flips entry=null,
            // otherwise the screen would sit on the loading state forever
            // (reported bug: delete never returned to the vault).
            deleted.value = true
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
