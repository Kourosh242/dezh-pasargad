package com.pasargad.dezh.presentation.categories

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pasargad.dezh.domain.ObserveEntriesUseCase
import com.pasargad.dezh.domain.VaultEntry
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

data class CategorySummary(val name: String, val count: Int)

data class CategoriesUiState(
    val categories: List<CategorySummary> = emptyList(),
    val isLoading: Boolean = true,
)

/** Categories overview with live counts (decrypted in-memory aggregation). */
class CategoriesViewModel(observeEntries: ObserveEntriesUseCase) : ViewModel() {

    val uiState: StateFlow<CategoriesUiState> = observeEntries()
        .map { entries -> CategoriesUiState(categories = summarize(entries), isLoading = false) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), CategoriesUiState())

    private fun summarize(entries: List<VaultEntry>): List<CategorySummary> = entries
        .filter { it.category.isNotBlank() }
        .groupBy { it.category }
        .map { (name, list) -> CategorySummary(name = name, count = list.size) }
        .sortedBy { it.name.lowercase() }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
