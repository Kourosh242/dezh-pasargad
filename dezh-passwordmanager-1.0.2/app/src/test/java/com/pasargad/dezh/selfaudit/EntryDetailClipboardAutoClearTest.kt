package com.pasargad.dezh.selfaudit

import androidx.compose.ui.platform.ClipboardManager
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import com.pasargad.dezh.domain.DeleteEntryUseCase
import com.pasargad.dezh.domain.GetEntryUseCase
import com.pasargad.dezh.domain.ObserveEntryUseCase
import com.pasargad.dezh.domain.ToggleFavoriteUseCase
import com.pasargad.dezh.domain.VaultEntry
import com.pasargad.dezh.domain.VaultEntryDraft
import com.pasargad.dezh.domain.VaultEntryRepository
import com.pasargad.dezh.domain.VaultSortOption
import com.pasargad.dezh.presentation.vault.EntryDetailScreen
import com.pasargad.dezh.presentation.vault.EntryDetailViewModel
import java.util.concurrent.ConcurrentLinkedQueue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Deterministic clipboard auto-clear contract for the ENTRY DETAIL screen
 * (full-audit round). Mirrors the round-2 generator harness: delay gates keyed
 * by capture order, paused looper pumping, no real time.
 *
 * Contract under test: EVERY copy re-arms the wipe — a re-copy inside the
 * window must CANCEL the pending wipe and start a fresh full window for the
 * newest clipboard value. The shipped boolean-keyed effect does not restart on
 * a second copy, so the wipe fires while the newest value has NOT lived its
 * full window (differential assertion below fails on that logic).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "fa-rIR-w411dp-h891dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class EntryDetailClipboardAutoClearTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val mainDispatcher = UnconfinedTestDispatcher()

    private object Holder {
        var clipboard: ClipboardManager? = null
    }

    /** Captured delay gates in call order; resumed explicitly by the test. */
    private val gates = ConcurrentLinkedQueue<CompletableDeferred<Unit>>()

    private fun makeDelay(): suspend (Long) -> Unit = { _ ->
        val gate = CompletableDeferred<Unit>()
        gates.add(gate)
        gate.await()
    }

    /** Resumes the oldest un-resumed gate; returns how many gates existed. */
    private fun resumeOldest(): Int {
        val gate = gates.poll() ?: error("no pending delay gate")
        gate.complete(Unit)
        return gates.size
    }

    private fun pump() {
        composeRule.waitForIdle()
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).runToEndOfTasks()
        composeRule.waitForIdle()
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(mainDispatcher)
        Holder.clipboard = null
        gates.clear()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private class SingleEntryRepository : VaultEntryRepository {
        val entry = VaultEntry(
            id = "detail-1",
            title = "بانک ملت",
            username = "kourosh",
            email = "",
            password = "S3cret!Pass",
            notes = "",
            category = "بانکداری",
            favorite = false,
            createdAt = 1_700_000_000_000L,
            updatedAt = 1_700_000_000_000L,
        )

        override fun observeEntries(
            query: String,
            category: String?,
            favoritesOnly: Boolean,
            sort: VaultSortOption,
        ): Flow<List<VaultEntry>> = flowOf(listOf(entry))

        override fun observeEntry(id: String): Flow<VaultEntry?> = flowOf(entry)

        override suspend fun getEntry(id: String): VaultEntry = entry

        override fun observeCategories(): Flow<List<String>> = flowOf(listOf(entry.category))

        override suspend fun createEntry(draft: VaultEntryDraft): String = entry.id

        override suspend fun updateEntry(id: String, draft: VaultEntryDraft) = Unit

        override suspend fun deleteEntry(id: String) = Unit

        override suspend fun setFavorite(id: String, favorite: Boolean) = Unit

        override suspend fun upsertEntries(entries: List<VaultEntry>) = Unit

        override suspend fun replaceAllEntries(entries: List<VaultEntry>) = Unit
    }

    @Test
    fun entry_recopy_rearms_the_clipboard_wipe() {
        val repository = SingleEntryRepository()
        val viewModel = EntryDetailViewModel(
            entryId = "detail-1",
            observeEntry = ObserveEntryUseCase(repository),
            toggleFavorite = ToggleFavoriteUseCase(repository),
            deleteEntry = DeleteEntryUseCase(repository),
        )
        val delay = makeDelay()
        composeRule.setContent {
            Holder.clipboard = androidx.compose.ui.platform.LocalClipboardManager.current
            EntryDetailScreen(
                viewModel = viewModel,
                onEdit = {},
                onDeleted = {},
                clipboardTimeoutSeconds = 2,
                suspendDelay = delay,
            )
        }
        composeRule.waitUntil(timeoutMillis = 5_000) { viewModel.uiState.value.entry != null }
        pump()

        // Copy #1: clipboard filled, one wipe gate armed.
        composeRule.onNodeWithContentDescription("کپی رمز").performClick()
        pump()
        assertEquals("S3cret!Pass", Holder.clipboard?.getText()?.text)
        assertEquals(1, gates.size)

        // Copy #2 inside the window: the FIRST wipe must be CANCELLED and a
        // fresh window armed for the newest value.
        composeRule.onNodeWithContentDescription("کپی رمز").performClick()
        pump()
        assertEquals(2, gates.size)

        // Release the STALE first gate: it must be a cancelled no-op — the
        // clipboard still holds the value copied seconds ago.
        resumeOldest()
        pump()
        assertEquals("S3cret!Pass", Holder.clipboard?.getText()?.text)

        // Release the re-armed gate: now the wipe legitimately fires.
        resumeOldest()
        pump()
        assertTrue(Holder.clipboard?.getText()?.text.isNullOrEmpty())
    }
}
