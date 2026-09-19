package com.pasargad.dezh.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import com.pasargad.dezh.domain.VaultSortOption
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private const val DATASTORE_FILE = "dezh_settings.preferences_pb"

private val Context.dataStore: DataStore<Preferences> by androidx.datastore.preferences.preferencesDataStore(
    name = DATASTORE_FILE,
)

/**
 * Preferences DataStore-backed settings. Strict on read: unknown enum names
 * fall back to defaults instead
 * of crashing, and I/O hiccups emit defaults rather than tearing the flow down.
 */
class DataStoreSettingsRepository private constructor(private val store: DataStore<Preferences>) : SettingsRepository {

    /** Production constructor: the per-process DataStore singleton for this app. */
    constructor(context: Context) : this(context.dataStore)

    override val settings: Flow<DezhSettings> = store.data
        .catch { error -> if (error is IOException) emit(emptyPreferences()) else throw error }
        .map { prefs -> prefs.toSettings() }

    private object KEYS {
        val THEME = stringPreferencesKey("theme_mode")
        val ACCENT = stringPreferencesKey("accent_color")
        val FONT_SCALE = floatPreferencesKey("font_scale")
        val DISPLAY_MODE = stringPreferencesKey("display_mode")
        val REMEMBER_FILTERS = booleanPreferencesKey("remember_search_filters")
        val LAST_CATEGORY = stringPreferencesKey("last_selected_category")
        val LAST_FAVORITES = booleanPreferencesKey("last_favorites_only")
        val SORT_ORDER = stringPreferencesKey("sort_order")
        val STARTUP_PAGE = stringPreferencesKey("startup_page")
        val DEFAULT_CATEGORY = stringPreferencesKey("default_category")
        val BACKUP_FILENAME = stringPreferencesKey("backup_filename_base")
        val CLIPBOARD_TIMEOUT = intPreferencesKey("clipboard_timeout_seconds")
        val AUTO_LOCK = intPreferencesKey("auto_lock_timeout_minutes")
        val GEN_LENGTH = intPreferencesKey("generator_length")
        val GEN_UPPER = booleanPreferencesKey("generator_upper")
        val GEN_LOWER = booleanPreferencesKey("generator_lower")
        val GEN_DIGITS = booleanPreferencesKey("generator_digits")
        val GEN_SYMBOLS = booleanPreferencesKey("generator_symbols")
        val GEN_EXCLUDE = booleanPreferencesKey("generator_exclude_ambiguous")
        val GEN_MIN_UPPER = intPreferencesKey("generator_min_upper")
        val GEN_MIN_LOWER = intPreferencesKey("generator_min_lower")
        val GEN_MIN_DIGITS = intPreferencesKey("generator_min_digits")
        val GEN_MIN_SYMBOLS = intPreferencesKey("generator_min_symbols")
        val CONFIRM_DELETE = booleanPreferencesKey("confirm_before_delete")
        val CONFIRM_DISCARD = booleanPreferencesKey("confirm_before_discard")
        val ANIMATIONS = booleanPreferencesKey("animations_enabled")
        val SCREENSHOT_PROTECTION = booleanPreferencesKey("screenshot_protection_enabled")
    }

    override suspend fun current(): DezhSettings = settings.first()

    override suspend fun update(transform: (DezhSettings) -> DezhSettings) {
        store.edit { prefs ->
            val updated = transform(prefs.toSettings())
            prefs.applySettings(updated)
        }
    }

    override suspend fun resetToDefaults() {
        store.edit { it.clear() }
    }

    companion object {
        /** Test factory: isolated DataStore file per test to avoid cross-test state. */
        fun forFile(file: java.io.File, scope: kotlinx.coroutines.CoroutineScope): DataStoreSettingsRepository =
            DataStoreSettingsRepository(
                androidx.datastore.preferences.core.PreferenceDataStoreFactory.create(
                    scope = scope,
                    produceFile = { file },
                ),
            )
    }

    // --- mapping ------------------------------------------------------------------

    @Suppress("CyclomaticComplexMethod") // every key read is validated/clamped inline
    private fun Preferences.toSettings(): DezhSettings = DezhSettings(
        themeMode = enumValue(KEYS.THEME, ThemeMode.SYSTEM),
        accentColor = enumValue(KEYS.ACCENT, AccentColor.DEFAULT),
        fontScale = clampFloat(this[KEYS.FONT_SCALE] ?: DezhSettings.FONT_SCALE_NORMAL),
        displayMode = enumValue(KEYS.DISPLAY_MODE, DisplayMode.AUTO),
        rememberSearchFilters = this[KEYS.REMEMBER_FILTERS] ?: true,
        lastSelectedCategory = this[KEYS.LAST_CATEGORY]?.takeIf { it.isNotBlank() },
        lastFavoritesOnly = this[KEYS.LAST_FAVORITES] ?: false,
        sortOrder = enumValue(KEYS.SORT_ORDER, VaultSortOption.UPDATED_NEWEST),
        startupPage = enumValue(KEYS.STARTUP_PAGE, StartupPage.VAULT),
        defaultCategory = this[KEYS.DEFAULT_CATEGORY].orEmpty(),
        backupFilenameBase = this[KEYS.BACKUP_FILENAME] ?: DezhSettings.DEFAULT_BACKUP_FILENAME,
        clipboardTimeoutSeconds = clampChoice(
            this[KEYS.CLIPBOARD_TIMEOUT] ?: DezhSettings.CLIPBOARD_TIMEOUT_DEFAULT,
            DezhSettings.CLIPBOARD_TIMEOUT_CHOICES,
        ),
        autoLockTimeoutMinutes = clampChoice(
            this[KEYS.AUTO_LOCK] ?: DezhSettings.AUTO_LOCK_DEFAULT_MINUTES,
            DezhSettings.AUTO_LOCK_MINUTE_CHOICES,
        ),
        generator = GeneratorSettings(
            length = clampInt(this[KEYS.GEN_LENGTH] ?: 16, 8, 64),
            includeUppercase = this[KEYS.GEN_UPPER] ?: true,
            includeLowercase = this[KEYS.GEN_LOWER] ?: true,
            includeDigits = this[KEYS.GEN_DIGITS] ?: true,
            includeSymbols = this[KEYS.GEN_SYMBOLS] ?: true,
            excludeAmbiguous = this[KEYS.GEN_EXCLUDE] ?: false,
            minUppercase = clampInt(this[KEYS.GEN_MIN_UPPER] ?: 1, 0, 8),
            minLowercase = clampInt(this[KEYS.GEN_MIN_LOWER] ?: 1, 0, 8),
            minDigits = clampInt(this[KEYS.GEN_MIN_DIGITS] ?: 1, 0, 8),
            minSymbols = clampInt(this[KEYS.GEN_MIN_SYMBOLS] ?: 1, 0, 8),
        ),
        confirmBeforeDelete = this[KEYS.CONFIRM_DELETE] ?: true,
        confirmBeforeDiscard = this[KEYS.CONFIRM_DISCARD] ?: true,
        animationsEnabled = this[KEYS.ANIMATIONS] ?: true,
        screenshotProtectionEnabled = this[KEYS.SCREENSHOT_PROTECTION] ?: true,
    )

    private fun androidx.datastore.preferences.core.MutablePreferences.applySettings(settings: DezhSettings) {
        this[KEYS.THEME] = settings.themeMode.name
        this[KEYS.ACCENT] = settings.accentColor.name
        this[KEYS.FONT_SCALE] = clampFloat(settings.fontScale)
        this[KEYS.DISPLAY_MODE] = settings.displayMode.name
        this[KEYS.REMEMBER_FILTERS] = settings.rememberSearchFilters
        this[KEYS.LAST_CATEGORY] = settings.lastSelectedCategory.orEmpty()
        this[KEYS.LAST_FAVORITES] = settings.lastFavoritesOnly
        this[KEYS.SORT_ORDER] = settings.sortOrder.name
        this[KEYS.STARTUP_PAGE] = settings.startupPage.name
        this[KEYS.DEFAULT_CATEGORY] = settings.defaultCategory
        this[KEYS.BACKUP_FILENAME] = settings.backupFilenameBase.ifBlank { DezhSettings.DEFAULT_BACKUP_FILENAME }
        this[KEYS.CLIPBOARD_TIMEOUT] = clampChoice(
            settings.clipboardTimeoutSeconds,
            DezhSettings.CLIPBOARD_TIMEOUT_CHOICES,
        )
        this[KEYS.AUTO_LOCK] = clampChoice(settings.autoLockTimeoutMinutes, DezhSettings.AUTO_LOCK_MINUTE_CHOICES)
        this[KEYS.GEN_LENGTH] = clampInt(settings.generator.length, 8, 64)
        this[KEYS.GEN_UPPER] = settings.generator.includeUppercase
        this[KEYS.GEN_LOWER] = settings.generator.includeLowercase
        this[KEYS.GEN_DIGITS] = settings.generator.includeDigits
        this[KEYS.GEN_SYMBOLS] = settings.generator.includeSymbols
        this[KEYS.GEN_EXCLUDE] = settings.generator.excludeAmbiguous
        this[KEYS.GEN_MIN_UPPER] = clampInt(settings.generator.minUppercase, 0, 8)
        this[KEYS.GEN_MIN_LOWER] = clampInt(settings.generator.minLowercase, 0, 8)
        this[KEYS.GEN_MIN_DIGITS] = clampInt(settings.generator.minDigits, 0, 8)
        this[KEYS.GEN_MIN_SYMBOLS] = clampInt(settings.generator.minSymbols, 0, 8)
        this[KEYS.CONFIRM_DELETE] = settings.confirmBeforeDelete
        this[KEYS.CONFIRM_DISCARD] = settings.confirmBeforeDiscard
        this[KEYS.ANIMATIONS] = settings.animationsEnabled
        this[KEYS.SCREENSHOT_PROTECTION] = settings.screenshotProtectionEnabled
    }

    private inline fun <reified T : Enum<T>> Preferences.enumValue(key: Preferences.Key<String>, default: T): T =
        this[key]?.let { raw -> runCatching { enumValueOf<T>(raw) }.getOrNull() } ?: default

    private fun clampFloat(value: Float): Float =
        DezhSettings.FONT_SCALES.firstOrNull { it == value } ?: DezhSettings.FONT_SCALE_NORMAL

    private fun clampChoice(value: Int, choices: IntArray): Int =
        choices.firstOrNull { it == value } ?: choices.first()

    private fun clampInt(value: Int, min: Int, max: Int): Int = value.coerceIn(min, max)

}
