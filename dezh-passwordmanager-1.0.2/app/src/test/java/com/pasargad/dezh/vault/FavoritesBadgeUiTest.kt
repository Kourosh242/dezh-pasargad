package com.pasargad.dezh.vault

import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.assertCountEquals
import com.pasargad.dezh.domain.VaultEntry
import com.pasargad.dezh.presentation.vault.VaultListContent
import com.pasargad.dezh.presentation.vault.VaultListUiState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** 1.0.2: the favorites chip carries a live count badge — present when >0, absent at 0. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "fa-rIR-w411dp-h891dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class FavoritesBadgeUiTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun entry(id: String, favorite: Boolean) = VaultEntry(
        id = id, title = "عنوان $id", username = "u$id", email = "$id@mail.example",
        password = "pw", notes = "", category = "ایمیل", favorite = favorite,
        createdAt = 1L, updatedAt = 1L,
    )

    private fun setContent(entries: List<VaultEntry>) {
        composeRule.setContent {
            VaultListContent(
                state = VaultListUiState(entries = entries, isLoading = false),
                widthSizeClass = WindowWidthSizeClass.Compact,
                onFavoriteToggled = { _, _ -> },
                onEntryClick = {},
            )
        }
    }

    @Test
    fun favorites_badge_shows_live_count() {
        setContent(listOf(entry("1", true), entry("2", true), entry("3", false)))
        composeRule.onNodeWithText("2").assertExists()
    }

    @Test
    fun favorites_badge_absent_when_zero() {
        setContent(listOf(entry("1", false), entry("2", false)))
        composeRule.onAllNodesWithText("0").assertCountEquals(0)
    }
}
