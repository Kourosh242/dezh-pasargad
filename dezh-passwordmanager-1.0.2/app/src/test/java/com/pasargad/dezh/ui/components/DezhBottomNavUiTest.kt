package com.pasargad.dezh.ui.components

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The M3 pill bottom-nav contract: three labeled tabs, each clickable, always
 * one selected, labels visible (icon-only navigation is banned by research).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "fa-rIR-w411dp-h891dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DezhBottomNavUiTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `three labeled tabs render and route clicks`() {
        var vault = 0
        var generator = 0
        var settings = 0
        composeRule.setContent {
            DezhBottomNav(
                current = DezhTab.VAULT,
                onVault = { vault++ },
                onGenerator = { generator++ },
                onSettings = { settings++ },
            )
        }
        composeRule.onNodeWithText("صندوقچه").assertIsDisplayed()
        composeRule.onNodeWithText("رمزساز").assertIsDisplayed().performClick()
        composeRule.onNodeWithText("تنظیمات").assertIsDisplayed().performClick()
        composeRule.onNodeWithText("صندوقچه").performClick()
        assertEquals(1, vault)
        assertEquals(1, generator)
        assertEquals(1, settings)
    }
}
