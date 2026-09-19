package com.pasargad.dezh.presentation.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pasargad.dezh.domain.ObserveCategoriesUseCase
import com.pasargad.dezh.domain.ObserveEntriesUseCase
import com.pasargad.dezh.domain.ToggleFavoriteUseCase
import com.pasargad.dezh.presentation.vault.VaultListUiState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Dedicated offline search screen. Same in-memory search contract as the vault
 * list: queries match decrypted title/username/email/notes after unlock only.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SearchViewModel(
    observeEntries: ObserveEntriesUseCase,
    observeCategories: ObserveCategoriesUseCase,
    private val toggleFavorite: ToggleFavoriteUseCase,
) : ViewModel() {

    private val query = MutableStateFlow("")

    val uiState: StateFlow<VaultListUiState> = combine(
        query.flatMapLatest { q -> observeEntries(query = q) },
        observeCategories(),
        query,
    ) { entries, categories, q ->
        VaultListUiState(entries = entries, categories = categories, query = q, isLoading = false)
    }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), VaultListUiState())

    fun onQueryChanged(value: String) { query.value = value }

    fun onFavoriteToggled(id: String, favorite: Boolean) {
        viewModelScope.launch { toggleFavorite(id, favorite) }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
