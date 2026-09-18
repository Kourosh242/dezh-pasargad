package com.pasargad.dezh.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.WindowManager
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalView
import com.pasargad.dezh.ui.requiresSecureFlag
import com.pasargad.dezh.di.AppContainer
import com.pasargad.dezh.domain.VaultLockState
import com.pasargad.dezh.presentation.backup.BackupScreen
import com.pasargad.dezh.presentation.backup.BackupViewModel
import com.pasargad.dezh.presentation.backup.RestoreScreen
import com.pasargad.dezh.presentation.backup.RestoreViewModel
import com.pasargad.dezh.presentation.settings.AboutScreen
import com.pasargad.dezh.presentation.settings.SecuritySettingsScreen
import com.pasargad.dezh.presentation.settings.SettingsScreen
import com.pasargad.dezh.presentation.settings.SettingsViewModel
import com.pasargad.dezh.presentation.settings.ThemeSettingsScreen
import com.pasargad.dezh.settings.DezhSettings
import com.pasargad.dezh.ui.theme.LocalMotionEnabled
import com.pasargad.dezh.settings.StartupPage
import com.pasargad.dezh.presentation.setup.SetupScreen
import com.pasargad.dezh.presentation.setup.SetupViewModel
import com.pasargad.dezh.presentation.categories.CategoriesScreen
import com.pasargad.dezh.presentation.categories.CategoriesViewModel
import com.pasargad.dezh.presentation.generator.GeneratorScreen
import com.pasargad.dezh.presentation.generator.GeneratorViewModel
import com.pasargad.dezh.presentation.search.SearchScreen
import com.pasargad.dezh.presentation.search.SearchViewModel
import com.pasargad.dezh.presentation.startup.StartupScreen
import com.pasargad.dezh.presentation.vault.FavoritesScreen
import com.pasargad.dezh.presentation.unlock.UnlockScreen
import com.pasargad.dezh.presentation.unlock.UnlockViewModel
import com.pasargad.dezh.presentation.vault.EntryDetailViewModel
import com.pasargad.dezh.presentation.vault.EntryDetailScreen
import com.pasargad.dezh.presentation.vault.EntryEditScreen
import com.pasargad.dezh.presentation.vault.EntryEditViewModel
import com.pasargad.dezh.presentation.vault.VaultListScreen
import com.pasargad.dezh.presentation.vault.VaultListViewModel

/**
 * Root Navigation 3 graph. The lock lifecycle IS the navigation authority:
 * the back stack root is rebuilt from [VaultLockState] — Locked/NotSetUp/Unlocked
 * users can never observe a screen that does not match their security state.
 *
 * Security rule: no secrets travel through navigation arguments — only opaque,
 * non-sensitive ids (entry UUIDs).
 */
@Composable
fun DezhApp(container: AppContainer, windowWidthSizeClass: WindowWidthSizeClass, modifier: Modifier = Modifier) {
    val backStack = rememberNavBackStack(DezhDestination.Startup)
    val lockState by container.securityRepository.lockState.collectAsStateWithLifecycle()
    val settings by container.settingsRepository.settings
        .collectAsStateWithLifecycle(initialValue = DezhSettings())

    // Screenshot / app-switcher protection: FLAG_SECURE whenever any screen that
    // holds or can reveal credentials is showing (policy: SecureScreenPolicy).
    val view = LocalView.current
    DisposableEffect(lockState, settings.screenshotProtectionEnabled, view) {
        val window = view.context.findActivity()?.window
        if (window != null) {
            if (requiresSecureFlag(lockState, settings.screenshotProtectionEnabled)) {
                window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
            } else {
                window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
            }
        }
        onDispose { window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE) }
    }

    CompositionLocalProvider(LocalMotionEnabled provides settings.animationsEnabled) {
    LaunchedEffect(lockState, settings.startupPage) {
        val target: DezhDestination = when (lockState) {
            VaultLockState.Initializing -> DezhDestination.Startup
            VaultLockState.NotSetUp -> DezhDestination.Onboarding
            VaultLockState.Locked -> DezhDestination.Unlock
            VaultLockState.Unlocked -> when (settings.startupPage) {
                StartupPage.VAULT -> DezhDestination.Vault()
                StartupPage.SEARCH -> DezhDestination.Search
                StartupPage.FAVORITES -> DezhDestination.Favorites
                StartupPage.GENERATOR -> DezhDestination.Generator
                StartupPage.CATEGORIES -> DezhDestination.Categories
            }
        }
        if (backStack.lastOrNull() != target) {
            backStack.clear()
            backStack.add(target)
        }
    }

    NavDisplay(
        backStack = backStack,
        onBack = { backStack.removeLastOrNull() },
        modifier = modifier,
        entryProvider = entryProvider {
            entry<DezhDestination.Startup> {
                StartupScreen()
            }
            entry<DezhDestination.Onboarding> {
                SetupScreen(
                    viewModel = viewModel {
                        SetupViewModel(container.setupMasterPassword, container.passwordStrengthValidator)
                    },
                )
            }
            entry<DezhDestination.Unlock> {
                UnlockScreen(
                    viewModel = viewModel { UnlockViewModel(container.unlockVault) },
                )
            }
            entry<DezhDestination.Vault> { key ->
                VaultListScreen(
                    viewModel = viewModel {
                        VaultListViewModel(
                            initialCategory = key.category,
                            initialFavoritesOnly = key.favoritesOnly,
                            observeEntries = container.observeEntries,
                            observeCategories = container.observeCategories,
                            toggleFavorite = container.toggleVaultFavorite,
                            deleteEntry = container.deleteVaultEntry,
                            themeModeController = container.themeModeController,
                            settingsRepository = container.settingsRepository,
                        )
                    },
                    widthSizeClass = windowWidthSizeClass,
                    displayMode = settings.displayMode,
                    onEntryClick = { id -> backStack.add(DezhDestination.EntryDetails(id)) },
                    onAddClick = { backStack.add(DezhDestination.AddEntry) },
                    onGeneratorClick = { backStack.add(DezhDestination.Generator) },
                    onSearchClick = { backStack.add(DezhDestination.Search) },
                    onCategoriesClick = { backStack.add(DezhDestination.Categories) },
                    onFavoritesClick = { backStack.add(DezhDestination.Favorites) },
                    onSettingsClick = { backStack.add(DezhDestination.Settings) },
                )
            }
            entry<DezhDestination.Search> {
                SearchScreen(
                    viewModel = viewModel {
                        SearchViewModel(
                            observeEntries = container.observeEntries,
                            observeCategories = container.observeCategories,
                            toggleFavorite = container.toggleVaultFavorite,
                        )
                    },
                    widthSizeClass = windowWidthSizeClass,
                    onEntryClick = { id -> backStack.add(DezhDestination.EntryDetails(id)) },
                )
            }
            entry<DezhDestination.Categories> {
                CategoriesScreen(
                    viewModel = viewModel { CategoriesViewModel(container.observeEntries) },
                    onCategoryClick = { name -> backStack.add(DezhDestination.Vault(category = name)) },
                )
            }
            entry<DezhDestination.Favorites> {
                FavoritesScreen(
                    viewModel = viewModel {
                        VaultListViewModel(
                            initialFavoritesOnly = true,
                            observeEntries = container.observeEntries,
                            observeCategories = container.observeCategories,
                            toggleFavorite = container.toggleVaultFavorite,
                            deleteEntry = container.deleteVaultEntry,
                            themeModeController = container.themeModeController,
                            settingsRepository = container.settingsRepository,
                        )
                    },
                    widthSizeClass = windowWidthSizeClass,
                    displayMode = settings.displayMode,
                    onEntryClick = { id -> backStack.add(DezhDestination.EntryDetails(id)) },
                )
            }
            entry<DezhDestination.Generator> {
                GeneratorScreen(
                    viewModel = viewModel {
                        GeneratorViewModel(
                            generator = container.passwordGenerator,
                            settingsRepository = container.settingsRepository,
                        )
                    },
                    animationsEnabled = settings.animationsEnabled,
                    clipboardTimeoutSeconds = settings.clipboardTimeoutSeconds,
                )
            }
            entry<DezhDestination.AddEntry> {
                EntryEditScreen(
                    viewModel = viewModel {
                        EntryEditViewModel(
                            initialEntryId = null,
                            createEntry = container.createVaultEntry,
                            updateEntry = container.updateVaultEntry,
                            getEntry = container.getVaultEntry,
                            passwordGenerator = container.passwordGenerator,
                            strengthMeter = container.passwordStrengthMeter,
                            defaultCategoryProvider = { container.settingsRepository.current().defaultCategory },
                        )
                    },
                    confirmBeforeDiscard = settings.confirmBeforeDiscard,
                    onSaved = { backStack.removeLastOrNull() },
                    onCancelled = { backStack.removeLastOrNull() },
                )
            }
            entry<DezhDestination.EntryDetails> { key ->
                EntryDetailScreen(
                    viewModel = viewModel {
                        EntryDetailViewModel(
                            entryId = key.entryId,
                            observeEntry = container.observeEntry,
                            toggleFavorite = container.toggleVaultFavorite,
                            deleteEntry = container.deleteVaultEntry,
                        )
                    },
                    clipboardTimeoutSeconds = settings.clipboardTimeoutSeconds,
                    confirmBeforeDelete = settings.confirmBeforeDelete,
                    onEdit = { id -> backStack.add(DezhDestination.EditEntry(id)) },
                    onDeleted = { backStack.removeLastOrNull() },
                )
            }
            entry<DezhDestination.Settings> {
                SettingsScreen(
                    viewModel = viewModel { SettingsViewModel(container.settingsRepository) },
                    onBack = { backStack.removeLastOrNull() },
                    onThemeClick = { backStack.add(DezhDestination.Theme) },
                    onSecurityClick = { backStack.add(DezhDestination.Security) },
                    onBackupClick = { backStack.add(DezhDestination.Backup) },
                    onRestoreClick = { backStack.add(DezhDestination.Restore) },
                    onAboutClick = { backStack.add(DezhDestination.About) },
                )
            }
            entry<DezhDestination.Theme> {
                ThemeSettingsScreen(
                    viewModel = viewModel { SettingsViewModel(container.settingsRepository) },
                    onBack = { backStack.removeLastOrNull() },
                )
            }
            entry<DezhDestination.Security> {
                SecuritySettingsScreen(
                    viewModel = viewModel { SettingsViewModel(container.settingsRepository) },
                    onBack = { backStack.removeLastOrNull() },
                )
            }
            entry<DezhDestination.Backup> {
                BackupScreen(
                    viewModel = viewModel {
                        BackupViewModel(
                            backupManager = container.backupManager,
                            fileGateway = container.backupFileGateway,
                            settingsRepository = container.settingsRepository,
                        )
                    },
                    onBack = { backStack.removeLastOrNull() },
                )
            }
            entry<DezhDestination.Restore> {
                RestoreScreen(
                    viewModel = viewModel {
                        RestoreViewModel(
                            backupManager = container.backupManager,
                            fileGateway = container.backupFileGateway,
                        )
                    },
                    onBack = { backStack.removeLastOrNull() },
                )
            }
            entry<DezhDestination.About> {
                AboutScreen(onBack = { backStack.removeLastOrNull() })
            }
            entry<DezhDestination.EditEntry> { key ->
                EntryEditScreen(
                    viewModel = viewModel {
                        EntryEditViewModel(
                            initialEntryId = key.entryId,
                            createEntry = container.createVaultEntry,
                            updateEntry = container.updateVaultEntry,
                            getEntry = container.getVaultEntry,
                            passwordGenerator = container.passwordGenerator,
                            strengthMeter = container.passwordStrengthMeter,
                            defaultCategoryProvider = { container.settingsRepository.current().defaultCategory },
                        )
                    },
                    confirmBeforeDiscard = settings.confirmBeforeDiscard,
                    onSaved = { backStack.removeLastOrNull() },
                    onCancelled = { backStack.removeLastOrNull() },
                )
            }
        },
    )
    }
}

/** Unwraps a Compose view context to the hosting Activity, if any. */
private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
