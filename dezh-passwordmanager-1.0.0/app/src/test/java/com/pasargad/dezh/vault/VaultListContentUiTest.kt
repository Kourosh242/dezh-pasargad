package com.pasargad.dezh.vault

import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.pasargad.dezh.domain.VaultEntry
import com.pasargad.dezh.domain.VaultSortOption
import com.pasargad.dezh.presentation.vault.VaultListContent
import com.pasargad.dezh.presentation.vault.VaultListUiState
import org.junit.Rule
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Shared list content renders rows and honors the favorites empty state. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "fa-rIR-w411dp-h891dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class VaultListContentUiTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun entry(id: String, title: String) = VaultEntry(
        id = id, title = title, username = "u_$id", email = "$id@mail.example",
        password = "pw", notes = "", category = "Email", favorite = false,
        createdAt = 1L, updatedAt = 1L,
    )

    @Test
    fun rows_render_with_title_and_open_action() {
        composeRule.setContent {
            VaultListContent(
                state = VaultListUiState(
                    entries = listOf(entry("1", "Gmail"), entry("2", "بانک ملت")),
                    isLoading = false,
                    sort = VaultSortOption.NAME,
                ),
                widthSizeClass = WindowWidthSizeClass.Compact,
                onFavoriteToggled = { _, _ -> },
                onEntryClick = {},
            )
        }
        composeRule.onNodeWithText("Gmail").assertExists()
        composeRule.onNodeWithText("بانک ملت").assertExists()
    }

    @Test
    fun favorites_empty_state_shows_when_no_entries() {
        composeRule.setContent {
            VaultListContent(
                state = VaultListUiState(entries = emptyList(), favoritesOnly = true, isLoading = false),
                widthSizeClass = WindowWidthSizeClass.Compact,
                onFavoriteToggled = { _, _ -> },
                onEntryClick = {},
            )
        }
        composeRule.onNodeWithText("هنوز علاقه‌مندی‌ای ندارید").assertExists()
        composeRule.onNodeWithText("با زدن نشان قلب روی هر رکورد، پرمصرف‌ها اینجا جمع می‌شوند.").assertExists()
    }

    @Test
    fun empty_vault_cta_click_invokes_add_callback() {
        var addClicked = false
        composeRule.setContent {
            VaultListContent(
                state = VaultListUiState(entries = emptyList(), isLoading = false),
                widthSizeClass = WindowWidthSizeClass.Compact,
                onFavoriteToggled = { _, _ -> },
                onEntryClick = {},
                onAddEntryClick = { addClicked = true },
            )
        }
        composeRule.onNodeWithText("افزودن نخستین رمز").performClick()
        composeRule.waitForIdle()
        assertTrue(addClicked)
    }
}
