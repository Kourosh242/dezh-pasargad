package com.pasargad.dezh.vault

import com.pasargad.dezh.domain.VaultEntry
import com.pasargad.dezh.domain.VaultEntryDraft
import com.pasargad.dezh.domain.VaultEntryRepository
import com.pasargad.dezh.domain.VaultSortOption
import com.pasargad.dezh.settings.DezhSettings
import com.pasargad.dezh.settings.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf

/** Empty in-memory repository for JVM Compose tests (no vault content involved). */
class StubVaultEntryRepository : VaultEntryRepository {

    override fun observeEntries(
        query: String,
        category: String?,
        favoritesOnly: Boolean,
        sort: VaultSortOption,
    ): Flow<List<VaultEntry>> = flowOf(emptyList())

    override fun observeEntry(id: String): Flow<VaultEntry?> = flowOf(null)

    override suspend fun getEntry(id: String): VaultEntry? = null

    override fun observeCategories(): Flow<List<String>> = flowOf(emptyList())

    override suspend fun createEntry(draft: VaultEntryDraft): String = "stub-id"

    override suspend fun updateEntry(id: String, draft: VaultEntryDraft) = Unit

    override suspend fun deleteEntry(id: String) = Unit

    override suspend fun setFavorite(id: String, favorite: Boolean) = Unit

    override suspend fun upsertEntries(entries: List<VaultEntry>) = Unit

    override suspend fun replaceAllEntries(entries: List<VaultEntry>) = Unit
}

/** In-memory settings double for JVM Compose tests. */
class StubSettingsRepository : SettingsRepository {

    private val state = MutableStateFlow(DezhSettings())

    override val settings: Flow<DezhSettings> = state

    override suspend fun current(): DezhSettings = state.value

    override suspend fun update(transform: (DezhSettings) -> DezhSettings) {
        state.value = transform(state.value)
    }

    override suspend fun resetToDefaults() {
        state.value = DezhSettings()
    }
}
