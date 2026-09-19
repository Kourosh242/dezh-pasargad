package com.pasargad.dezh.settings

import com.pasargad.dezh.domain.VaultSortOption

/** Accent presets — static design tokens resolved in the theme layer only. */
enum class AccentColor { DEFAULT, EMERALD, AMBER, ROSE, VIOLET }

/** How lists are rendered. AUTO decides from the window width class. */
enum class DisplayMode { AUTO, LIST, GRID }

/** First page after a successful unlock. */
enum class StartupPage { VAULT, SEARCH, FAVORITES, GENERATOR, CATEGORIES }

/** Non-sensitive generator defaults persisted for the next session. */
data class GeneratorSettings(
    val length: Int = 16,
    val includeUppercase: Boolean = true,
    val includeLowercase: Boolean = true,
    val includeDigits: Boolean = true,
    val includeSymbols: Boolean = true,
    val excludeAmbiguous: Boolean = false,
    val minUppercase: Int = 1,
    val minLowercase: Int = 1,
    val minDigits: Int = 1,
    val minSymbols: Int = 1,
)

/**
 * All user settings in one immutable snapshot.
 *
 * Contract: local-only, non-sensitive. The master password, backup passphrases
 * and vault secrets are NEVER stored here (or anywhere persistent in plaintext).
 */
data class DezhSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val accentColor: AccentColor = AccentColor.DEFAULT,
    val fontScale: Float = FONT_SCALE_NORMAL,
    val displayMode: DisplayMode = DisplayMode.AUTO,
    val rememberSearchFilters: Boolean = true,
    val lastSelectedCategory: String? = null,
    val lastFavoritesOnly: Boolean = false,
    val sortOrder: VaultSortOption = VaultSortOption.UPDATED_NEWEST,
    val startupPage: StartupPage = StartupPage.VAULT,
    val defaultCategory: String = "",
    val backupFilenameBase: String = DEFAULT_BACKUP_FILENAME,
    val clipboardTimeoutSeconds: Int = CLIPBOARD_TIMEOUT_DEFAULT,
    val autoLockTimeoutMinutes: Int = AUTO_LOCK_DEFAULT_MINUTES,
    val generator: GeneratorSettings = GeneratorSettings(),
    val confirmBeforeDelete: Boolean = true,
    val confirmBeforeDiscard: Boolean = true,
    val animationsEnabled: Boolean = true,
    val screenshotProtectionEnabled: Boolean = true,
) {
    companion object {
        const val FONT_SCALE_SMALL = 0.85f
        const val FONT_SCALE_NORMAL = 1.0f
        const val FONT_SCALE_LARGE = 1.15f
        const val FONT_SCALE_XLARGE = 1.3f
        const val DEFAULT_BACKUP_FILENAME = "dezh-backup"
        const val CLIPBOARD_TIMEOUT_NEVER = 0
        const val CLIPBOARD_TIMEOUT_DEFAULT = 60
        const val AUTO_LOCK_IMMEDIATELY = 0

        /** Security policy of Dezh: "never" is NOT an allowed auto-lock value. */
        const val AUTO_LOCK_DEFAULT_MINUTES = 5
        val FONT_SCALES = floatArrayOf(FONT_SCALE_SMALL, FONT_SCALE_NORMAL, FONT_SCALE_LARGE, FONT_SCALE_XLARGE)
        val CLIPBOARD_TIMEOUT_CHOICES = intArrayOf(0, 15, 30, 60, 120)
        val AUTO_LOCK_MINUTE_CHOICES = intArrayOf(AUTO_LOCK_IMMEDIATELY, 1, 5, 15, 30)
    }
}
