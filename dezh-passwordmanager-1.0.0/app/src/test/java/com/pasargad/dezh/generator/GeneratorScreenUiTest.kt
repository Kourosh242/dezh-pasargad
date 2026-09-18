package com.pasargad.dezh.generator

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.pasargad.dezh.presentation.generator.GeneratorScreen
import com.pasargad.dezh.presentation.generator.GeneratorViewModel
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Compose UI test for the generator screen (Robolectric, headless).
 * Asserts only UI semantics — never the generated value itself.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "fa-rIR-w411dp-h891dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class GeneratorScreenUiTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun generator_screen_shows_controls_and_regenerates() {
        val viewModel = GeneratorViewModel(PasswordGenerator())
        composeRule.setContent {
            GeneratorScreen(viewModel = viewModel)
        }
        composeRule.onNodeWithText("تولیدکنندهٔ رمز").assertExists()
        composeRule.onNodeWithText("طول رمز: ۱۶").assertExists()
        composeRule.onNodeWithText("حذف نویسه‌های گیج‌کننده").assertExists()

        composeRule.onNodeWithText("تولید مجدد").performClick()
        composeRule.onNodeWithText("کپی").assertExists()
    }
}
