package com.pasargad.dezh.vault

import com.pasargad.dezh.domain.VaultEntry
import com.pasargad.dezh.domain.VaultEntryRepository
import com.pasargad.dezh.domain.VaultEntryNotFoundException
import com.pasargad.dezh.domain.VaultEntryDraft
import com.pasargad.dezh.domain.VaultSortOption
import com.pasargad.dezh.domain.DeleteEntryUseCase
import com.pasargad.dezh.domain.ObserveEntryUseCase
import com.pasargad.dezh.domain.ToggleFavoriteUseCase
import com.pasargad.dezh.presentation.vault.EntryDetailViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Regression for the reported delete bug: after confirming deletion the
 * ViewModel must publish deleted=true (navigation signal) instead of leaving
 * the detail screen on the loading state forever.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class EntryDetailDeleteFlowTest {

    private val mainDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(mainDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    /** Entries live until deleted — mirrors the real repository behaviour. */
    private class DeleteTrackingRepository : VaultEntryRepository {
        val entries = MutableStateFlow(
            listOf(
                VaultEntry(
                    id = "entry-1", title = "جیمیل", username = "user", email = "",
                    password = "pw", notes = "", category = "ایمیل", favorite = false,
                    createdAt = 1L, updatedAt = 1L,
                ),
            ),
        )
        var deletedIds = mutableListOf<String>()

        override fun observeEntries(
            query: String,
            category: String?,
            favoritesOnly: Boolean,
            sort: VaultSortOption,
        ): Flow<List<VaultEntry>> = entries

        // Mirror Room: re-emits whenever the underlying data changes.
        override fun observeEntry(id: String): Flow<VaultEntry?> =
            entries.map { list -> list.firstOrNull { e -> e.id == id } }

        override suspend fun getEntry(id: String): VaultEntry? =
            entries.value.singleOrNull { it.id == id }

        override fun observeCategories(): Flow<List<String>> =
            kotlinx.coroutines.flow.flowOf(emptyList())

        override suspend fun createEntry(draft: VaultEntryDraft): String = "new-id"

        override suspend fun updateEntry(id: String, draft: VaultEntryDraft) = Unit

        override suspend fun deleteEntry(id: String) {
            if (entries.value.none { it.id == id }) throw VaultEntryNotFoundException()
            deletedIds += id
            entries.value = entries.value.filterNot { it.id == id }
        }

        override suspend fun setFavorite(id: String, favorite: Boolean) = Unit

        override suspend fun upsertEntries(entries: List<VaultEntry>) = Unit

        override suspend fun replaceAllEntries(entries: List<VaultEntry>) = Unit
    }

    @Test
    fun `delete confirms and publishes deleted signal`() = runBlocking {
        val repository = DeleteTrackingRepository()
        val viewModel = EntryDetailViewModel(
            entryId = "entry-1",
            observeEntry = ObserveEntryUseCase(repository),
            toggleFavorite = ToggleFavoriteUseCase(repository),
            deleteEntry = DeleteEntryUseCase(repository),
        )

        val loaded = viewModel.uiState.first { it.entry != null }
        assertNotNull(loaded.entry)

        viewModel.onDeleteConfirmed()

        assertEquals(listOf("entry-1"), repository.deletedIds)
        val finalState = viewModel.uiState.first { it.deleted }
        assertTrue(finalState.entry == null)
    }
}
