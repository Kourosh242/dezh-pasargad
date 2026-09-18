package com.pasargad.dezh.vault

import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.pasargad.dezh.presentation.vault.VaultListScreen
import com.pasargad.dezh.presentation.vault.VaultListViewModel
import com.pasargad.dezh.domain.DeleteEntryUseCase
import com.pasargad.dezh.domain.ObserveCategoriesUseCase
import com.pasargad.dezh.domain.ObserveEntriesUseCase
import com.pasargad.dezh.domain.ToggleFavoriteUseCase
import com.pasargad.dezh.settings.ThemeModeController
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Material 3 top app bar of the vault: search + overflow menu carry all
 * navigation actions; the compact header is no longer overcrowded.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "fa-rIR-w411dp-h891dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class VaultTopBarUiTest {

    @get:Rule
    val composeRule = createComposeRule()

    private class FakeContainer {
        var settingsOpened = false
        var favoritesOpened = false
        var generatorOpened = false
        var searchOpened = false
    }

    private fun viewModel(): VaultListViewModel = VaultListViewModel(
        observeEntries = ObserveEntriesUseCase(StubVaultEntryRepository()),
        observeCategories = ObserveCategoriesUseCase(StubVaultEntryRepository()),
        toggleFavorite = ToggleFavoriteUseCase(StubVaultEntryRepository()),
        deleteEntry = DeleteEntryUseCase(StubVaultEntryRepository()),
        themeModeController = ThemeModeController(StubSettingsRepository(), kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Unconfined)),
    )

    @Test
    fun search_icon_and_overflow_menu_navigate() {
        val box = FakeContainer()
        composeRule.setContent {
            VaultListScreen(
                viewModel = viewModel(),
                widthSizeClass = WindowWidthSizeClass.Compact,
                onEntryClick = {},
                onAddClick = {},
                onGeneratorClick = { box.generatorOpened = true },
                onSearchClick = { box.searchOpened = true },
                onCategoriesClick = {},
                onFavoritesClick = { box.favoritesOpened = true },
                onSettingsClick = { box.settingsOpened = true },
            )
        }
        composeRule.onNodeWithContentDescription("جستجو").assertIsDisplayed().performClick()
        assertTrue(box.searchOpened)

        composeRule.onNodeWithContentDescription("اقدامات بیشتر").performClick()
        composeRule.onNodeWithText("تنظیمات").assertIsDisplayed().performClick()
        assertTrue(box.settingsOpened)

        composeRule.onNodeWithContentDescription("اقدامات بیشتر").performClick()
        composeRule.onNodeWithText("دسته‌ها").assertIsDisplayed()
        composeRule.onNodeWithText("تولید رمز").assertIsDisplayed().performClick()
        assertTrue(box.generatorOpened)
    }

    @Test
    fun overflow_menu_lists_favorites_and_theme_entries() {
        composeRule.setContent {
            VaultListScreen(
                viewModel = viewModel(),
                widthSizeClass = WindowWidthSizeClass.Compact,
                onEntryClick = {}, onAddClick = {}, onGeneratorClick = {}, onSearchClick = {},
                onCategoriesClick = {}, onFavoritesClick = {}, onSettingsClick = {},
            )
        }
        composeRule.onNodeWithContentDescription("اقدامات بیشتر").performClick()
        // Both labels exist inside the open menu (menu item + list filter chip in the
        // underlying tree), so presence is asserted via the count-aware API.
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText("علاقه‌مندی‌ها").fetchSemanticsNodes().size >= 2
        }
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText("تم: سیستم").fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun fab_exposes_an_accessible_description() {
        composeRule.setContent {
            VaultListScreen(
                viewModel = viewModel(),
                widthSizeClass = WindowWidthSizeClass.Compact,
                onEntryClick = {}, onAddClick = {}, onGeneratorClick = {}, onSearchClick = {},
                onCategoriesClick = {}, onFavoritesClick = {}, onSettingsClick = {},
            )
        }
        composeRule.onNodeWithContentDescription("افزودن رکورد جدید").assertIsDisplayed()
    }
}
