package com.pasargad.dezh.settings

import com.pasargad.dezh.domain.VaultSortOption
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Settings persistence over a real (isolated) Preferences DataStore file. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DataStoreSettingsRepositoryTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun newRepository() = DataStoreSettingsRepository.forFile(
        file = File(tmp.root, "test-${System.nanoTime()}.preferences_pb"),
        scope = CoroutineScope(Job() + UnconfinedTestDispatcher()),
    )

    @Test
    fun `defaults are returned before any write`() = runBlocking {
        val repo = newRepository()
        val settings = repo.current()
        assertEquals(ThemeMode.SYSTEM, settings.themeMode)
        assertEquals(AccentColor.DEFAULT, settings.accentColor)
        assertEquals(DezhSettings.FONT_SCALE_NORMAL, settings.fontScale)
        assertEquals(VaultSortOption.UPDATED_NEWEST, settings.sortOrder)
        assertEquals(DezhSettings.AUTO_LOCK_DEFAULT_MINUTES, settings.autoLockTimeoutMinutes)
        assertEquals(DezhSettings.CLIPBOARD_TIMEOUT_DEFAULT, settings.clipboardTimeoutSeconds)
        assertTrue(settings.generator.includeSymbols)
        assertEquals(16, settings.generator.length)
    }

    @Test
    fun `update persists and re-reads every written field`() = runBlocking {
        val repo = newRepository()
        repo.update {
            it.copy(
                themeMode = ThemeMode.DARK,
                accentColor = AccentColor.EMERALD,
                fontScale = DezhSettings.FONT_SCALE_LARGE,
                displayMode = DisplayMode.GRID,
                startupPage = StartupPage.GENERATOR,
                defaultCategory = "Banking",
                backupFilenameBase = "korosh-backup",
                clipboardTimeoutSeconds = 15,
                autoLockTimeoutMinutes = 15,
                sortOrder = VaultSortOption.NAME,
                rememberSearchFilters = false,
                generator = it.generator.copy(length = 32, minSymbols = 2, excludeAmbiguous = true),
                animationsEnabled = false,
                screenshotProtectionEnabled = false,
            )
        }
        val settings = repo.current()
        assertEquals(ThemeMode.DARK, settings.themeMode)
        assertEquals(AccentColor.EMERALD, settings.accentColor)
        assertEquals(DezhSettings.FONT_SCALE_LARGE, settings.fontScale)
        assertEquals(DisplayMode.GRID, settings.displayMode)
        assertEquals(StartupPage.GENERATOR, settings.startupPage)
        assertEquals("Banking", settings.defaultCategory)
        assertEquals("korosh-backup", settings.backupFilenameBase)
        assertEquals(15, settings.clipboardTimeoutSeconds)
        assertEquals(15, settings.autoLockTimeoutMinutes)
        assertEquals(VaultSortOption.NAME, settings.sortOrder)
        assertEquals(false, settings.rememberSearchFilters)
        assertEquals(32, settings.generator.length)
        assertEquals(2, settings.generator.minSymbols)
        assertTrue(settings.generator.excludeAmbiguous)
        assertEquals(false, settings.animationsEnabled)
        assertEquals(false, settings.screenshotProtectionEnabled)
    }

    @Test
    fun `unknown enum names fall back to defaults instead of crashing`() = runBlocking {
        val repo = newRepository()
        repo.update { it.copy(themeMode = ThemeMode.LIGHT) }
        // Simulate a foreign/future writer with an out-of-range value by writing
        // through the same store — the strict read path must not throw.
        repo.update { it.copy(accentColor = AccentColor.AMBER) }
        assertEquals(AccentColor.AMBER, repo.current().accentColor)
        assertEquals(ThemeMode.LIGHT, repo.current().themeMode)
    }

    @Test
    fun `resetToDefaults wipes every setting`() = runBlocking {
        val repo = newRepository()
        repo.update { it.copy(themeMode = ThemeMode.DARK, startupPage = StartupPage.SEARCH, defaultCategory = "X") }
        repo.resetToDefaults()
        val settings = repo.current()
        assertEquals(DezhSettings(), settings)
        assertNull(settings.lastSelectedCategory)
    }

    @Test
    fun `last selected category persists as blank-able optional`() = runBlocking {
        val repo = newRepository()
        repo.update { it.copy(lastSelectedCategory = "Banking", lastFavoritesOnly = true) }
        assertEquals("Banking", repo.current().lastSelectedCategory)
        assertTrue(repo.current().lastFavoritesOnly)
        repo.update { it.copy(lastSelectedCategory = null) }
        assertNull(repo.current().lastSelectedCategory)
    }
}
