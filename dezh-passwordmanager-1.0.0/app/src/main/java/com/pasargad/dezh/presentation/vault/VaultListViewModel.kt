package com.pasargad.dezh.presentation.vault

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pasargad.dezh.domain.DeleteEntryUseCase
import com.pasargad.dezh.domain.ObserveCategoriesUseCase
import com.pasargad.dezh.domain.ObserveEntriesUseCase
import com.pasargad.dezh.domain.ToggleFavoriteUseCase
import com.pasargad.dezh.domain.VaultEntry
import com.pasargad.dezh.domain.VaultSortOption
import com.pasargad.dezh.settings.SettingsRepository
import com.pasargad.dezh.settings.ThemeMode
import com.pasargad.dezh.settings.ThemeModeController
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class VaultListUiState(
    val entries: List<VaultEntry> = emptyList(),
    val categories: List<String> = emptyList(),
    val query: String = "",
    val selectedCategory: String? = null,
    val favoritesOnly: Boolean = false,
    val sort: VaultSortOption = VaultSortOption.UPDATED_NEWEST,
    val isLoading: Boolean = true,
)

/**
 * Shared list/search/favorites state holder: filters flow through use cases to
 * the repository (DB-level category/favorites, in-memory text search + sort).
 * Also owns the compact theme cycle control for the vault header.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class VaultListViewModel(
    initialCategory: String? = null,
    initialFavoritesOnly: Boolean = false,
    observeEntries: ObserveEntriesUseCase,
    observeCategories: ObserveCategoriesUseCase,
    private val toggleFavorite: ToggleFavoriteUseCase,
    private val deleteEntry: DeleteEntryUseCase,
    val themeModeController: ThemeModeController,
    private val settingsRepository: SettingsRepository? = null,
) : ViewModel() {

    private val query = MutableStateFlow("")
    private val selectedCategory = MutableStateFlow(initialCategory)
    private val favoritesOnly = MutableStateFlow(initialFavoritesOnly)
    private val sort = MutableStateFlow(VaultSortOption.UPDATED_NEWEST)
    private val filters = combine(query, selectedCategory, favoritesOnly, sort, ::Filters)

    val themeMode: StateFlow<ThemeMode> = themeModeController.mode

    init {
        // Phase 5: sort order and (opt-in) last filters are restored from DataStore.
        viewModelScope.launch {
            val settings = settingsRepository?.current() ?: return@launch
            sort.value = settings.sortOrder
            if (settings.rememberSearchFilters && initialCategory == null && !initialFavoritesOnly) {
                selectedCategory.value = settings.lastSelectedCategory
                favoritesOnly.value = settings.lastFavoritesOnly
            }
        }
    }

    val uiState: StateFlow<VaultListUiState> = combine(
        filters.flatMapLatest { f -> observeEntries(f.query, f.category, f.favoritesOnly, f.sort) },
        observeCategories(),
        query,
        selectedCategory,
        favoritesOnly,
    ) { entries, categories, q, category, favs ->
        VaultListUiState(
            entries = entries,
            categories = categories,
            query = q,
            selectedCategory = category,
            favoritesOnly = favs,
            sort = sort.value,
            isLoading = false,
        )
    }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), VaultListUiState())

    fun onQueryChanged(value: String) = query.update { value }

    fun onCategorySelected(value: String?) {
        selectedCategory.update { value }
        settingsRepository?.let { repo ->
            viewModelScope.launch { repo.update { it.copy(lastSelectedCategory = value) } }
        }
    }

    fun onFavoritesFilterChanged(enabled: Boolean) {
        favoritesOnly.update { enabled }
        settingsRepository?.let { repo ->
            viewModelScope.launch { repo.update { it.copy(lastFavoritesOnly = enabled) } }
        }
    }

    fun onSortSelected(value: VaultSortOption) {
        sort.update { value }
        settingsRepository?.let { repo ->
            viewModelScope.launch { repo.update { it.copy(sortOrder = value) } }
        }
    }
    fun onThemeCycled() = themeModeController.cycle()

    fun onFavoriteToggled(id: String, favorite: Boolean) {
        viewModelScope.launch { toggleFavorite(id, favorite) }
    }

    fun onEntryDeleted(id: String) {
        viewModelScope.launch { deleteEntry(id) }
    }

    private data class Filters(val query: String, val category: String?, val favoritesOnly: Boolean, val sort: VaultSortOption)

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
