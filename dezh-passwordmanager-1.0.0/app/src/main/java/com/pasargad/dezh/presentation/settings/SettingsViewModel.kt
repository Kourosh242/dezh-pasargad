package com.pasargad.dezh.presentation.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pasargad.dezh.settings.AccentColor
import com.pasargad.dezh.settings.DezhSettings
import com.pasargad.dezh.settings.DisplayMode
import com.pasargad.dezh.settings.SettingsRepository
import com.pasargad.dezh.settings.StartupPage
import com.pasargad.dezh.settings.ThemeMode
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Single settings state holder shared by the hub, theme and security screens.
 * All writes go through [SettingsRepository] (DataStore); nothing sensitive
 * ever reaches this layer.
 */
@Suppress("TooManyFunctions") // one explicit setter per setting keeps call sites typed
class SettingsViewModel(private val settingsRepository: SettingsRepository) : ViewModel() {

    val uiState: StateFlow<DezhSettings> = settingsRepository.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), DezhSettings())

    fun onThemeChanged(value: ThemeMode) = update { it.copy(themeMode = value) }
    fun onAccentChanged(value: AccentColor) = update { it.copy(accentColor = value) }
    fun onFontScaleChanged(value: Float) = update { it.copy(fontScale = value) }
    fun onDisplayModeChanged(value: DisplayMode) = update { it.copy(displayMode = value) }
    fun onStartupPageChanged(value: StartupPage) = update { it.copy(startupPage = value) }
    fun onDefaultCategoryChanged(value: String) = update { it.copy(defaultCategory = value.trim()) }
    fun onBackupFilenameChanged(value: String) = update {
        it.copy(backupFilenameBase = value.trim().ifBlank { DezhSettings.DEFAULT_BACKUP_FILENAME })
    }

    fun onClipboardTimeoutChanged(seconds: Int) = update { it.copy(clipboardTimeoutSeconds = seconds) }
    fun onAutoLockChanged(minutes: Int) = update { it.copy(autoLockTimeoutMinutes = minutes) }
    fun onRememberFiltersChanged(value: Boolean) = update { it.copy(rememberSearchFilters = value) }
    fun onConfirmDeleteChanged(value: Boolean) = update { it.copy(confirmBeforeDelete = value) }
    fun onConfirmDiscardChanged(value: Boolean) = update { it.copy(confirmBeforeDiscard = value) }
    fun onAnimationsChanged(value: Boolean) = update { it.copy(animationsEnabled = value) }
    fun onScreenshotProtectionChanged(value: Boolean) = update { it.copy(screenshotProtectionEnabled = value) }

    fun onResetRequested() = update { reset() }

    private fun update(transform: (DezhSettings) -> DezhSettings) {
        viewModelScope.launch { settingsRepository.update(transform) }
    }

    private fun reset(): DezhSettings = DezhSettings()

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
