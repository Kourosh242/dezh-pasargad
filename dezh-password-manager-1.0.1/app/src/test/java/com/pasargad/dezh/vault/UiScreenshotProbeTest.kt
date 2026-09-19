package com.pasargad.dezh.vault

import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.pasargad.dezh.domain.DeleteEntryUseCase
import com.pasargad.dezh.domain.ObserveCategoriesUseCase
import com.pasargad.dezh.domain.ObserveEntriesUseCase
import com.pasargad.dezh.domain.ToggleFavoriteUseCase
import com.pasargad.dezh.domain.VaultEntry
import com.pasargad.dezh.domain.VaultEntryDraft
import com.pasargad.dezh.domain.VaultEntryRepository
import com.pasargad.dezh.domain.VaultSortOption
import com.pasargad.dezh.domain.CreateEntryUseCase
import com.pasargad.dezh.domain.GetEntryUseCase
import com.pasargad.dezh.domain.PasswordStrengthMeter
import com.pasargad.dezh.domain.UpdateEntryUseCase
import com.pasargad.dezh.generator.PasswordGenerator
import com.pasargad.dezh.presentation.generator.GeneratorScreen
import com.pasargad.dezh.presentation.generator.GeneratorViewModel
import com.pasargad.dezh.presentation.settings.SettingsScreen
import com.pasargad.dezh.presentation.settings.SettingsViewModel
import com.pasargad.dezh.presentation.vault.EntryDetailScreen
import com.pasargad.dezh.presentation.vault.EntryDetailViewModel
import com.pasargad.dezh.presentation.vault.EntryEditScreen
import com.pasargad.dezh.presentation.vault.EntryEditViewModel
import com.pasargad.dezh.domain.ObserveEntryUseCase
import com.pasargad.dezh.presentation.vault.VaultListScreen
import com.pasargad.dezh.presentation.unlock.UnlockScreen
import com.pasargad.dezh.presentation.unlock.UnlockUiState
import com.pasargad.dezh.settings.DataStoreSettingsRepository
import com.pasargad.dezh.settings.SettingsRepository
import com.pasargad.dezh.settings.ThemeModeController
import com.pasargad.dezh.ui.theme.DezhTheme
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Assume
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * SELF-VIEW PROBE: renders the real screens at real pixels (Robolectric
 * native graphics) and writes PNGs to $DEZH_SHOT_DIR so the developer can
 * LOOK at the shipped UI before releasing. Skipped unless DEZH_SHOT_DIR is
 * set — it is a design tool, not a CI assertion.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "fa-rIR-w411dp-h891dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class UiScreenshotProbeTest {

    @get:Rule
    val composeRule = createComposeRule()

    @get:Rule
    val tmp = TemporaryFolder()

    private fun shotDir(): File? = System.getenv("DEZH_SHOT_DIR")?.let { File(it) }

    private fun seededRepository(): VaultEntryRepository {
        val now = 1_760_000_000_000L
        val entries = listOf(
            entry("1", "بانک پاسارگاد", "m.rezaei92", "بانکی", "vT7#qLm2!xWp9zR4", true, now),
            entry("2", "جیمیل", "kourosh242", "ایمیل", "12345678", true, now - 86_400_000),
            entry("3", "اینستاگرام", "dezh.fan", "شبکهٔ اجتماعی", "Kourosh2024fan", false, now - 2 * 86_400_000),
            entry("4", "سرور شرکت", "admin", "کاری", "Srv!9mQ2#vX8wL5z", false, now - 3 * 86_400_000),
            entry("5", "تلگرام", "kourosh_t", "شبکهٔ اجتماعی", "password1", false, now - 4 * 86_400_000),
            entry("6", "گیت‌هاب", "Kourosh242", "کاری", "Gh$8sW3!pQ6zR2vN", true, now - 5 * 86_400_000),
            entry("7", "وای‌فای دفتر", "", "کاری", "OfficeNet2026", false, now - 6 * 86_400_000),
        )
        val categories = listOf("بانکی", "ایمیل", "شبکهٔ اجتماعی", "کاری")
        return object : VaultEntryRepository {
            override fun observeEntries(query: String, category: String?, favoritesOnly: Boolean, sort: VaultSortOption): Flow<List<VaultEntry>> {
                var result = entries
                if (category != null) result = result.filter { it.category == category }
                if (favoritesOnly) result = result.filter { it.favorite }
                if (query.isNotBlank()) {
                    result = result.filter {
                        it.title.contains(query) || it.username.contains(query, ignoreCase = true)
                    }
                }
                return flowOf(result.sortedByDescending { it.updatedAt })
            }

            override fun observeEntry(id: String): Flow<VaultEntry?> = flowOf(entries.firstOrNull { it.id == id })
            override suspend fun getEntry(id: String): VaultEntry? = entries.firstOrNull { it.id == id }
            override fun observeCategories(): Flow<List<String>> = flowOf(categories)
            override suspend fun createEntry(draft: VaultEntryDraft): String = "new"
            override suspend fun updateEntry(id: String, draft: VaultEntryDraft) = Unit
            override suspend fun deleteEntry(id: String) = Unit
            override suspend fun setFavorite(id: String, favorite: Boolean) = Unit
            override suspend fun upsertEntries(newEntries: List<VaultEntry>) = Unit
            override suspend fun replaceAllEntries(newEntries: List<VaultEntry>) = Unit
        }
    }

    private fun entry(
        id: String,
        title: String,
        username: String,
        category: String,
        password: String,
        favorite: Boolean,
        updatedAt: Long,
    ) = VaultEntry(
        id = id,
        title = title,
        username = username,
        email = "",
        password = password,
        notes = "",
        category = category,
        favorite = favorite,
        createdAt = updatedAt,
        updatedAt = updatedAt,
    )

    private fun vaultViewModel(): com.pasargad.dezh.presentation.vault.VaultListViewModel {
        val repository = seededRepository()
        return com.pasargad.dezh.presentation.vault.VaultListViewModel(
            observeEntries = ObserveEntriesUseCase(repository),
            observeCategories = ObserveCategoriesUseCase(repository),
            toggleFavorite = ToggleFavoriteUseCase(repository),
            deleteEntry = DeleteEntryUseCase(repository),
            themeModeController = ThemeModeController(
                StubSettingsRepository(),
                CoroutineScope(Dispatchers.Unconfined),
            ),
        )
    }

    private fun settingsViewModel(): SettingsViewModel = SettingsViewModel(
        DataStoreSettingsRepository.forFile(
            file = File(tmp.root, "shot-${System.nanoTime()}.preferences_pb"),
            scope = CoroutineScope(Job() + UnconfinedTestDispatcher()),
        ) as SettingsRepository,
    )

    private fun saveShot(name: String) {
        val dir = shotDir() ?: return
        dir.mkdirs()
        val bitmap = composeRule.onRoot().captureToImage().asAndroidBitmap()
        FileOutputStream(File(dir, "$name.png")).use { out ->
            bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out)
        }
    }

    @Test
    fun shot_vault_light() {
        Assume.assumeNotNull(shotDir())
        composeRule.setContent {
            DezhTheme(darkTheme = false) {
                VaultListScreen(
                    viewModel = vaultViewModel(),
                    widthSizeClass = WindowWidthSizeClass.Compact,
                    onEntryClick = {}, onAddClick = {}, onGeneratorClick = {}, onSearchClick = {},
                    onCategoriesClick = {}, onFavoritesClick = {}, onSettingsClick = {},
                )
            }
        }
        composeRule.waitForIdle()
        saveShot("vault-light")
    }

    @Test
    fun shot_vault_dark() {
        Assume.assumeNotNull(shotDir())
        composeRule.setContent {
            DezhTheme(darkTheme = true) {
                VaultListScreen(
                    viewModel = vaultViewModel(),
                    widthSizeClass = WindowWidthSizeClass.Compact,
                    onEntryClick = {}, onAddClick = {}, onGeneratorClick = {}, onSearchClick = {},
                    onCategoriesClick = {}, onFavoritesClick = {}, onSettingsClick = {},
                )
            }
        }
        composeRule.waitForIdle()
        saveShot("vault-dark")
    }

    @Test
    fun shot_settings_light() {
        Assume.assumeNotNull(shotDir())
        composeRule.setContent {
            DezhTheme(darkTheme = false) {
                SettingsScreen(
                    viewModel = settingsViewModel(),
                    onBack = {}, onThemeClick = {}, onSecurityClick = {}, onBackupClick = {},
                    onRestoreClick = {}, onAboutClick = {}, onVaultTab = {}, onGeneratorTab = {},
                )
            }
        }
        composeRule.waitForIdle()
        saveShot("settings-light")
    }

    @Test
    fun shot_settings_dark() {
        Assume.assumeNotNull(shotDir())
        composeRule.setContent {
            DezhTheme(darkTheme = true) {
                SettingsScreen(
                    viewModel = settingsViewModel(),
                    onBack = {}, onThemeClick = {}, onSecurityClick = {}, onBackupClick = {},
                    onRestoreClick = {}, onAboutClick = {}, onVaultTab = {}, onGeneratorTab = {},
                )
            }
        }
        composeRule.waitForIdle()
        saveShot("settings-dark")
    }

    private fun detailViewModel(): EntryDetailViewModel {
        val repository = object : VaultEntryRepository {
            val entry = entry(
                id = "d1",
                title = "بانک پاسارگاد",
                username = "m.rezaei92",
                category = "بانکی",
                password = "vT7#qLm2!xWp9zR4",
                favorite = true,
                updatedAt = 1_760_000_000_000L,
            )

            override fun observeEntries(query: String, category: String?, favoritesOnly: Boolean, sort: VaultSortOption) =
                flowOf(listOf(entry))
            override fun observeEntry(id: String) = flowOf(entry)
            override suspend fun getEntry(id: String) = entry
            override fun observeCategories() = flowOf(listOf(entry.category))
            override suspend fun createEntry(draft: VaultEntryDraft) = entry.id
            override suspend fun updateEntry(id: String, draft: VaultEntryDraft) = Unit
            override suspend fun deleteEntry(id: String) = Unit
            override suspend fun setFavorite(id: String, favorite: Boolean) = Unit
            override suspend fun upsertEntries(newEntries: List<VaultEntry>) = Unit
            override suspend fun replaceAllEntries(newEntries: List<VaultEntry>) = Unit
        }
        return EntryDetailViewModel(
            entryId = "d1",
            observeEntry = ObserveEntryUseCase(repository),
            toggleFavorite = ToggleFavoriteUseCase(repository),
            deleteEntry = DeleteEntryUseCase(repository),
        )
    }

    private fun editViewModel(initialId: String?): EntryEditViewModel {
        val repository = seededRepository()
        return EntryEditViewModel(
            initialEntryId = initialId,
            createEntry = CreateEntryUseCase(repository),
            updateEntry = UpdateEntryUseCase(repository),
            getEntry = GetEntryUseCase(repository),
            passwordGenerator = PasswordGenerator(),
            strengthMeter = PasswordStrengthMeter(),
        )
    }

    @Test
    fun shot_generator_light() {
        Assume.assumeNotNull(shotDir())
        val viewModel = GeneratorViewModel(PasswordGenerator())
        composeRule.setContent {
            DezhTheme(darkTheme = false) { GeneratorScreen(viewModel = viewModel) }
        }
        composeRule.waitForIdle()
        saveShot("generator-light")
    }

    @Test
    fun shot_generator_dark() {
        Assume.assumeNotNull(shotDir())
        val viewModel = GeneratorViewModel(PasswordGenerator())
        composeRule.setContent {
            DezhTheme(darkTheme = true) { GeneratorScreen(viewModel = viewModel) }
        }
        composeRule.waitForIdle()
        saveShot("generator-dark")
    }

    @Test
    fun shot_entry_detail_light() {
        Assume.assumeNotNull(shotDir())
        val viewModel = detailViewModel()
        composeRule.setContent {
            DezhTheme(darkTheme = false) { EntryDetailScreen(viewModel = viewModel, onEdit = {}, onDeleted = {}) }
        }
        composeRule.waitUntil(timeoutMillis = 5_000) { viewModel.uiState.value.entry != null }
        composeRule.waitForIdle()
        saveShot("entry-detail-light")
    }

    @Test
    fun shot_entry_edit_light() {
        Assume.assumeNotNull(shotDir())
        composeRule.setContent {
            DezhTheme(darkTheme = false) { EntryEditScreen(viewModel = editViewModel(initialId = null), onSaved = {}, onCancelled = {}) }
        }
        composeRule.waitForIdle()
        saveShot("entry-edit-light")
    }

    @Test
    fun shot_unlock_light() {
        Assume.assumeNotNull(shotDir())
        composeRule.setContent {
            DezhTheme(darkTheme = false) {
                UnlockScreen(state = UnlockUiState(), onInputChanged = {}, onSubmit = {})
            }
        }
        composeRule.waitForIdle()
        saveShot("unlock-light")
    }

    @Test
    fun shot_unlock_dark() {
        Assume.assumeNotNull(shotDir())
        composeRule.setContent {
            DezhTheme(darkTheme = true) {
                UnlockScreen(state = UnlockUiState(), onInputChanged = {}, onSubmit = {})
            }
        }
        composeRule.waitForIdle()
        saveShot("unlock-dark")
    }
}
