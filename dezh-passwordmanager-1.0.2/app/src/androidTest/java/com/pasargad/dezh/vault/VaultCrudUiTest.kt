package com.pasargad.dezh.vault

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.pasargad.dezh.MainActivity
import com.pasargad.dezh.data.vault.DezhVaultDatabase
import java.io.File
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented end-to-end vault flow (device/emulator required):
 * fresh install → master password setup → vault → create entry → visible in list.
 *
 * NOTE: execution requires an emulator/device; CI machines without one verify
 * this source via `:app:compileDebugAndroidTestKotlin` (compilation gate).
 */
@RunWith(AndroidJUnit4::class)
class VaultCrudUiTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Before
    fun resetAppState() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        context.deleteDatabase(DezhVaultDatabase.DATABASE_NAME)
        File(context.filesDir, "dezh/security").deleteRecursively()
    }

    @Test
    fun setupVaultThenCreateEntryAppearsInList() {
        // -- first run onboarding: create master password
        composeRule.onNodeWithText("Master password").performTextInput("MasterPass-1403!x")
        composeRule.onNodeWithText("Confirm master password").performTextInput("MasterPass-1403!x")
        composeRule.onNodeWithText("Create vault").performClick()

        // -- vault list
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithText("Vault").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("+").performClick()

        // -- new entry form
        composeRule.onNodeWithText("Title / service name").performTextInput("Gmail")
        composeRule.onNodeWithText("Username").performTextInput("dezh.user")
        composeRule.onNodeWithText("Password").performTextInput("EntryPass-77!z")
        composeRule.onNodeWithText("Save").performClick()

        // -- back on the list, the new entry is visible
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithText("Gmail").fetchSemanticsNodes().isNotEmpty()
        }
    }
}
