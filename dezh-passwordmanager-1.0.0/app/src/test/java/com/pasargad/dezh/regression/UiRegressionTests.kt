package com.pasargad.dezh.regression

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.onNodeWithText
import com.pasargad.dezh.backup.BackupCodec
import com.pasargad.dezh.backup.BackupManager
import com.pasargad.dezh.domain.CreateEntryUseCase
import com.pasargad.dezh.domain.GetEntryUseCase
import com.pasargad.dezh.domain.UpdateEntryUseCase
import com.pasargad.dezh.domain.VaultEntry
import com.pasargad.dezh.domain.VaultEntryRepository
import com.pasargad.dezh.domain.VaultEntryDraft
import com.pasargad.dezh.domain.VaultSortOption
import com.pasargad.dezh.domain.PasswordStrengthMeter
import com.pasargad.dezh.generator.PasswordGenerator
import com.pasargad.dezh.presentation.backup.BackupScreen
import com.pasargad.dezh.presentation.backup.BackupViewModel
import com.pasargad.dezh.presentation.unlock.UnlockScreen
import com.pasargad.dezh.presentation.unlock.UnlockUiState
import com.pasargad.dezh.presentation.vault.EntryEditViewModel
import com.pasargad.dezh.settings.SettingsRepository
import com.pasargad.dezh.vault.StubSettingsRepository
import java.security.SecureRandom
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * UI regression tests for previously fixed defects: strength band shown when
 * editing an existing entry, backup meter label text, empty-search state and
 * IME "done" clearing the unlock field.
 */
class EditMeterLoadTest {

    private val mainDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(mainDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private class SingleEntryRepository : VaultEntryRepository {
        override fun observeEntries(
            query: String,
            category: String?,
            favoritesOnly: Boolean,
            sort: VaultSortOption,
        ): Flow<List<VaultEntry>> = flowOf(emptyList())

        override fun observeEntry(id: String): Flow<VaultEntry?> = flowOf(null)

        override suspend fun getEntry(id: String): VaultEntry? = VaultEntry(
            id = id, title = "بانک", username = "u", email = "",
            // Strong 16-char mixed password
            password = "xK9#mQ2vL8pWz4!T", notes = "", category = "بانکداری",
            favorite = false, createdAt = 1L, updatedAt = 1L,
        )

        override fun observeCategories(): Flow<List<String>> = flowOf(emptyList())

        override suspend fun createEntry(draft: VaultEntryDraft): String = "new"

        override suspend fun updateEntry(id: String, draft: VaultEntryDraft) = Unit

        override suspend fun deleteEntry(id: String) = Unit

        override suspend fun setFavorite(id: String, favorite: Boolean) = Unit

        override suspend fun upsertEntries(entries: List<VaultEntry>) = Unit

        override suspend fun replaceAllEntries(entries: List<VaultEntry>) = Unit
    }

    @Test
    fun `editing a strong entry does not show a weak meter`() = runBlocking {
        val repository = SingleEntryRepository()
        val viewModel = EntryEditViewModel(
            initialEntryId = "entry-1",
            createEntry = CreateEntryUseCase(repository),
            updateEntry = UpdateEntryUseCase(repository),
            getEntry = GetEntryUseCase(repository),
            passwordGenerator = PasswordGenerator(SecureRandom()),
            strengthMeter = PasswordStrengthMeter(),
        )
        // Wait until the existing entry is loaded into the form.
        org.junit.Assert.assertTrue(viewModel.uiState.value.title.isNotBlank())
        assertEquals(
            "edit mode must evaluate the loaded password's strength",
            com.pasargad.dezh.presentation.vault.MeterBand.STRONG,
            viewModel.uiState.value.meterBand,
        )
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "fa-rIR-w411dp-h891dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class BackupMeterLabelUiTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun backup_meter_shows_band_text_not_a_resource_number() {
        val viewModel = BackupViewModel(
            backupManager = BackupManager(
                BackupCodec(SecureRandom(), kdfIterations = 2_000),
                repository = com.pasargad.dezh.vault.StubVaultEntryRepository(),
            ),
            fileGateway = NoOpFileGateway,
            settingsRepository = StubSettingsRepository(),
        )
        composeRule.setContent { BackupScreen(viewModel = viewModel, onBack = {}) }

        composeRule.onNodeWithText("عبارت عبور پشتیبان").performTextInput("پشتیبان#امن1404!Xq")

        composeRule.waitUntil(timeoutMillis = 5_000) {
            runCatching {
                composeRule.onNodeWithText("قدرت رمز: خیلی خوب").assertIsDisplayed()
            }.isSuccess
        }
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "fa-rIR-w411dp-h891dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SearchNoResultsUiTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun empty_search_results_show_search_empty_not_vault_empty() {
        composeRule.setContent {
            com.pasargad.dezh.presentation.vault.VaultListContent(
                state = com.pasargad.dezh.presentation.vault.VaultListUiState(
                    entries = emptyList(),
                    isLoading = false,
                    query = "چیزی-که-نیست",
                ),
                widthSizeClass = androidx.compose.material3.windowsizeclass.WindowWidthSizeClass.Compact,
                onFavoriteToggled = { _, _ -> },
                onEntryClick = {},
            )
        }
        composeRule.onNodeWithText("گاوصندوق هنوز خالی است").assertDoesNotExist()
        composeRule.onNodeWithText("موردی یافت نشد").assertExists()
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "fa-rIR-w411dp-h891dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class UnlockImeClearTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun ime_done_clears_the_password_field_like_the_button() {
        composeRule.setContent {
            UnlockScreen(
                state = UnlockUiState(),
                onInputChanged = {},
                onSubmit = {},
            )
        }
        composeRule.onNodeWithText("رمز اصلی").performTextInput("Aa1!Bb2@Cc3#")
        composeRule.onNodeWithText("رمز اصلی").performImeAction()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Aa1!Bb2@Cc3#").assertDoesNotExist()
    }
}

/** Test double: never writes anywhere; export only needs the meter UI. */
object NoOpFileGateway : com.pasargad.dezh.data.backup.BackupFileGateway {
    override suspend fun write(uri: android.net.Uri, bytes: ByteArray) = Unit

    override suspend fun read(uri: android.net.Uri): ByteArray = ByteArray(0)

    override suspend fun displayName(uri: android.net.Uri): String? = null
}
