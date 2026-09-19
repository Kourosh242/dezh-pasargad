package com.pasargad.dezh.search

import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTextInput
import com.pasargad.dezh.domain.ObserveCategoriesUseCase
import com.pasargad.dezh.domain.ObserveEntriesUseCase
import com.pasargad.dezh.domain.ToggleFavoriteUseCase
import com.pasargad.dezh.domain.VaultEntry
import com.pasargad.dezh.domain.VaultEntryDraft
import com.pasargad.dezh.domain.VaultEntryRepository
import com.pasargad.dezh.domain.VaultSortOption
import com.pasargad.dezh.presentation.search.SearchScreen
import com.pasargad.dezh.presentation.search.SearchViewModel
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
 * 1.0.2 bug hunt: the search surface must be ESCAPABLE — a system back press
 * (and the search bar's own collapse) has to invoke [SearchScreen.onBack].
 * With the old always-active SearchBar and an empty onActiveChange, back was
 * swallowed and the user was stuck on the search screen.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "fa-rIR-w411dp-h891dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SearchBackEscapeTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<androidx.activity.ComponentActivity>()

    private val mainDispatcher = UnconfinedTestDispatcher()

    private class Repo : VaultEntryRepository {
        private val entry = VaultEntry(
            id = "s1", title = "گیت‌هاب", username = "k", email = "",
            password = "pw", notes = "", category = "توسعه", favorite = false,
            createdAt = 1L, updatedAt = 1L,
        )
        override fun observeEntries(
            query: String,
            category: String?,
            favoritesOnly: Boolean,
            sort: VaultSortOption,
        ): Flow<List<VaultEntry>> = flowOf(
            if (query.isBlank() || entry.title.contains(query, ignoreCase = true)) {
                listOf(entry)
            } else {
                emptyList()
            },
        )
        override fun observeEntry(id: String): Flow<VaultEntry?> = flowOf(entry)
        override suspend fun getEntry(id: String): VaultEntry? = entry
        override fun observeCategories(): Flow<List<String>> = flowOf(listOf("توسعه"))
        override suspend fun createEntry(draft: VaultEntryDraft): String = "s1"
        override suspend fun updateEntry(id: String, draft: VaultEntryDraft) = Unit
        override suspend fun deleteEntry(id: String) = Unit
        override suspend fun setFavorite(id: String, favorite: Boolean) = Unit
        override suspend fun upsertEntries(entries: List<VaultEntry>) = Unit
        override suspend fun replaceAllEntries(entries: List<VaultEntry>) = Unit
    }

    private var backCalled = false

    @Before
    fun setUp() {
        Dispatchers.setMain(mainDispatcher)
        backCalled = false
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun system_back_invokes_onBack() {
        val viewModel = SearchViewModel(
            observeEntries = ObserveEntriesUseCase(Repo()),
            observeCategories = ObserveCategoriesUseCase(Repo()),
            toggleFavorite = ToggleFavoriteUseCase(Repo()),
        )
        composeRule.setContent {
            SearchScreen(
                viewModel = viewModel,
                widthSizeClass = WindowWidthSizeClass.Compact,
                onEntryClick = {},
                onBack = { backCalled = true },
            )
        }
        composeRule.waitForIdle()
        // The search field is focused/active: dispatch a system back.
        composeRule.activity.onBackPressedDispatcher.onBackPressed()
        composeRule.waitForIdle()
        assertTrue("system back must leave the search screen", backCalled)
    }
}
