package com.pasargad.dezh.selfaudit

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import com.pasargad.dezh.domain.DeleteEntryUseCase
import com.pasargad.dezh.domain.ObserveEntryUseCase
import com.pasargad.dezh.domain.ToggleFavoriteUseCase
import com.pasargad.dezh.presentation.vault.EntryDetailScreen
import com.pasargad.dezh.presentation.vault.EntryDetailViewModel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "fa-rIR-w411dp-h891dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class EntryDetailAccountCopyTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val mainDispatcher = UnconfinedTestDispatcher()
    private object Holder { var clipboard: androidx.compose.ui.platform.ClipboardManager? = null }
    private val gates = java.util.concurrent.ConcurrentLinkedQueue<CompletableDeferred<Unit>>()

    private fun resumeOldest() {
        val gate = gates.poll() ?: error("no pending delay gate")
        gate.complete(Unit)
    }

    @Before fun setUp() { Dispatchers.setMain(mainDispatcher); Holder.clipboard = null; gates.clear() }
    @After fun tearDown() { Dispatchers.resetMain() }

    @Test
    fun username_then_password_copy_single_wipe_window() {
        val repo = singleEntryRepository()
        val viewModel = EntryDetailViewModel(
            entryId = "detail-2",
            observeEntry = ObserveEntryUseCase(repo),
            toggleFavorite = ToggleFavoriteUseCase(repo),
            deleteEntry = DeleteEntryUseCase(repo),
        )
        composeRule.setContent {
            Holder.clipboard = androidx.compose.ui.platform.LocalClipboardManager.current
            EntryDetailScreen(
                viewModel = viewModel,
                onEdit = {},
                onDeleted = {},
                clipboardTimeoutSeconds = 2,
                suspendDelay = { _ ->
                    val gate = CompletableDeferred<Unit>()
                    gates.add(gate)
                    gate.await()
                },
            )
        }
        composeRule.waitUntil(timeoutMillis = 5_000) { viewModel.uiState.value.entry != null }
        composeRule.waitForIdle()

        composeRule.onNodeWithContentDescription("کپی نام کاربری").performClick()
        composeRule.waitForIdle()
        org.junit.Assert.assertEquals("m.rezaei92", Holder.clipboard?.getText()?.text)
        org.junit.Assert.assertEquals(
            1,
            composeRule.onAllNodesWithText("کپی شد").fetchSemanticsNodes().size,
        )

        // The account copy MUST arm the same wipe window as the password copy.
        org.junit.Assert.assertEquals(1, gates.size)

        composeRule.onNodeWithContentDescription("کپی رمز").performClick()
        composeRule.waitForIdle()
        org.junit.Assert.assertEquals("S3cret!Pass", Holder.clipboard?.getText()?.text)

        // Two queued gates; the FIRST (username's, superseded) must be a
        // cancelled no-op — the clipboard still holds the newest copy.
        org.junit.Assert.assertEquals(2, gates.size)
        resumeOldest()
        composeRule.waitForIdle()
        org.junit.Assert.assertEquals("S3cret!Pass", Holder.clipboard?.getText()?.text)

        // The live window wipes the clipboard AND every "copied" badge.
        resumeOldest()
        composeRule.waitForIdle()
        org.junit.Assert.assertTrue(Holder.clipboard?.getText()?.text.isNullOrEmpty())
        org.junit.Assert.assertEquals(
            0,
            composeRule.onAllNodesWithText("کپی شد").fetchSemanticsNodes().size,
        )
    }

    @Test
    fun username_copy_alone_wipes_and_clears_badge() {
        val repo = singleEntryRepository()
        val viewModel = EntryDetailViewModel(
            entryId = "detail-2",
            observeEntry = ObserveEntryUseCase(repo),
            toggleFavorite = ToggleFavoriteUseCase(repo),
            deleteEntry = DeleteEntryUseCase(repo),
        )
        composeRule.setContent {
            Holder.clipboard = androidx.compose.ui.platform.LocalClipboardManager.current
            EntryDetailScreen(
                viewModel = viewModel,
                onEdit = {},
                onDeleted = {},
                clipboardTimeoutSeconds = 2,
                suspendDelay = { _ ->
                    val gate = CompletableDeferred<Unit>()
                    gates.add(gate)
                    gate.await()
                },
            )
        }
        composeRule.waitUntil(timeoutMillis = 5_000) { viewModel.uiState.value.entry != null }
        composeRule.waitForIdle()

        composeRule.onNodeWithContentDescription("کپی نام کاربری").performClick()
        composeRule.waitForIdle()
        org.junit.Assert.assertEquals("m.rezaei92", Holder.clipboard?.getText()?.text)
        org.junit.Assert.assertEquals(1, gates.size)

        gates.poll()?.complete(Unit)
        composeRule.waitForIdle()
        org.junit.Assert.assertTrue(Holder.clipboard?.getText()?.text.isNullOrEmpty())
        org.junit.Assert.assertEquals(
            0,
            composeRule.onAllNodesWithText("کپی شد").fetchSemanticsNodes().size,
        )
    }
}


private fun singleEntryRepository() = object : com.pasargad.dezh.domain.VaultEntryRepository {
        private val entry = com.pasargad.dezh.domain.VaultEntry(
            id = "detail-2", title = "بانک پاسارگاد", username = "m.rezaei92", email = "m@dezh.app",
            password = "S3cret!Pass", notes = "", category = "بانکی", favorite = false,
            createdAt = 1_700_000_000_000L, updatedAt = 1_700_000_000_000L,
        )
        override fun observeEntries(query: String, category: String?, favoritesOnly: Boolean, sort: com.pasargad.dezh.domain.VaultSortOption) =
            kotlinx.coroutines.flow.flowOf(listOf(entry))
        override fun observeEntry(id: String) = kotlinx.coroutines.flow.flowOf(entry)
        override suspend fun getEntry(id: String) = entry
        override fun observeCategories() = kotlinx.coroutines.flow.flowOf(listOf(entry.category))
        override suspend fun createEntry(draft: com.pasargad.dezh.domain.VaultEntryDraft) = entry.id
        override suspend fun updateEntry(id: String, draft: com.pasargad.dezh.domain.VaultEntryDraft) = Unit
        override suspend fun deleteEntry(id: String) = Unit
        override suspend fun setFavorite(id: String, favorite: Boolean) = Unit
        override suspend fun upsertEntries(entries: List<com.pasargad.dezh.domain.VaultEntry>) = Unit
        override suspend fun replaceAllEntries(entries: List<com.pasargad.dezh.domain.VaultEntry>) = Unit
}
