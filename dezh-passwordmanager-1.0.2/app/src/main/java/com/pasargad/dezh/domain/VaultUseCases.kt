package com.pasargad.dezh.domain

import kotlinx.coroutines.flow.Flow

/**
 * Vault CRUD use cases — thin orchestration above [VaultEntryRepository]
 * (Presentation → UseCase → Repository → DataSource → Room).
 * The only business rule enforced here: the title (service name) is mandatory.
 */
class ObserveEntriesUseCase(private val repository: VaultEntryRepository) {
    operator fun invoke(
        query: String = "",
        category: String? = null,
        favoritesOnly: Boolean = false,
        sort: VaultSortOption = VaultSortOption.UPDATED_NEWEST,
    ): Flow<List<VaultEntry>> = repository.observeEntries(query, category, favoritesOnly, sort)
}

class ObserveCategoriesUseCase(private val repository: VaultEntryRepository) {
    operator fun invoke(): Flow<List<String>> = repository.observeCategories()
}

class ObserveEntryUseCase(private val repository: VaultEntryRepository) {
    operator fun invoke(id: String): Flow<VaultEntry?> = repository.observeEntry(id)
}

class GetEntryUseCase(private val repository: VaultEntryRepository) {
    suspend operator fun invoke(id: String): VaultEntry? = repository.getEntry(id)
}

class CreateEntryUseCase(private val repository: VaultEntryRepository) {
    suspend operator fun invoke(draft: VaultEntryDraft): String {
        require(draft.title.isNotBlank()) { "Entry title is required" }
        return repository.createEntry(draft)
    }
}

class UpdateEntryUseCase(private val repository: VaultEntryRepository) {
    suspend operator fun invoke(id: String, draft: VaultEntryDraft) {
        require(draft.title.isNotBlank()) { "Entry title is required" }
        repository.updateEntry(id, draft)
    }
}

class DeleteEntryUseCase(private val repository: VaultEntryRepository) {
    suspend operator fun invoke(id: String) = repository.deleteEntry(id)
}

class ToggleFavoriteUseCase(private val repository: VaultEntryRepository) {
    suspend operator fun invoke(id: String, favorite: Boolean) = repository.setFavorite(id, favorite)
}
