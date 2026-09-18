package com.pasargad.dezh.presentation.settings

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import com.pasargad.dezh.settings.AccentColor
import com.pasargad.dezh.settings.DezhSettings
import com.pasargad.dezh.settings.DataStoreSettingsRepository
import com.pasargad.dezh.settings.StartupPage
import com.pasargad.dezh.settings.ThemeMode
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Settings hub + nested dialogs (Robolectric Compose, Persian locale) writing
 * through a real isolated DataStore file.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "fa-rIR-w411dp-h891dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SettingsScreenUiTest {

    @get:Rule
    val composeRule = createComposeRule()

    @get:Rule
    val tmp = TemporaryFolder()

    private fun newViewModel(): SettingsViewModel = SettingsViewModel(
        DataStoreSettingsRepository.forFile(
            file = File(tmp.root, "ui-${System.nanoTime()}.preferences_pb"),
            scope = CoroutineScope(Job() + UnconfinedTestDispatcher()),
        ),
    )

    @Test
    fun hub_renders_all_sections_and_navigation_targets() {
        composeRule.setContent {
            SettingsScreen(
                viewModel = newViewModel(),
                onBack = {}, onThemeClick = {}, onSecurityClick = {},
                onBackupClick = {}, onRestoreClick = {}, onAboutClick = {},
            )
        }
        composeRule.onNodeWithText("تنظیمات").assertExists()
        composeRule.onNodeWithText("تم و نمایش").assertExists()
        composeRule.onNodeWithText("تنظیمات امنیتی").assertExists()
        composeRule.onNodeWithText("پشتیبان‌گیری رمزگذاری‌شده").assertExists()
        composeRule.onNodeWithText("بازیابی از پشتیبان").assertExists()
        composeRule.onNodeWithText("دربارهٔ دژ پاسارگاد").assertExists()
        composeRule.onNodeWithText("بازنشانی تنظیمات").assertExists()
    }

    @Test
    fun startup_page_dialog_persists_the_choice() {
        val viewModel = newViewModel()
        composeRule.setContent {
            SettingsScreen(
                viewModel = viewModel,
                onBack = {}, onThemeClick = {}, onSecurityClick = {},
                onBackupClick = {}, onRestoreClick = {}, onAboutClick = {},
            )
        }
        composeRule.onNodeWithText("صفحهٔ شروع پس از باز کردن قفل").performClick()
        composeRule.onNodeWithText("تولیدکنندهٔ رمز").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            viewModel.uiState.value.startupPage == StartupPage.GENERATOR
        }
        assertEquals(StartupPage.GENERATOR, viewModel.uiState.value.startupPage)
    }

    @Test
    fun reset_restores_defaults_after_a_change() {
        val viewModel = newViewModel()
        runBlocking { viewModel.onThemeChanged(ThemeMode.DARK) }
        composeRule.setContent {
            SettingsScreen(
                viewModel = viewModel,
                onBack = {}, onThemeClick = {}, onSecurityClick = {},
                onBackupClick = {}, onRestoreClick = {}, onAboutClick = {},
            )
        }
        composeRule.waitUntil(timeoutMillis = 5_000) {
            viewModel.uiState.value.themeMode == ThemeMode.DARK
        }
        // Reset flows through SettingsRepository -> DataStore clear; the AlertDialog
        // itself is Robolectric-window-bound, so the behavioral check drives the VM.
        viewModel.onResetRequested()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            viewModel.uiState.value.themeMode == ThemeMode.SYSTEM
        }
        assertEquals(DezhSettings(), viewModel.uiState.value)
    }

    @Test
    fun theme_screen_updates_accent() {
        val viewModel = newViewModel()
        composeRule.setContent { ThemeSettingsScreen(viewModel = viewModel, onBack = {}) }
        composeRule.onNodeWithText("سبز").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            viewModel.uiState.value.accentColor == AccentColor.EMERALD
        }
        assertEquals(AccentColor.EMERALD, viewModel.uiState.value.accentColor)
    }

    @Test
    fun screenshot_protection_toggle_flips_setting() {
        val viewModel = newViewModel()
        composeRule.setContent { SecuritySettingsScreen(viewModel = viewModel, onBack = {}) }
        assertTrue(viewModel.uiState.value.screenshotProtectionEnabled)
        composeRule.onAllNodesWithText("حفاظت از عکاسی").onLast().performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            !viewModel.uiState.value.screenshotProtectionEnabled
        }
        composeRule.onAllNodesWithText("حفاظت از عکاسی").onLast().performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            viewModel.uiState.value.screenshotProtectionEnabled
        }
        assertTrue(viewModel.uiState.value.screenshotProtectionEnabled)
    }
}
