package com.pasargad.dezh.settings

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** User-facing theme preference (SYSTEM / LIGHT / DARK). Non-sensitive; persisted locally. */
enum class ThemeMode { SYSTEM, LIGHT, DARK }

/**
 * Theme state holder backed by the DataStore settings repository (Phase 5 —
 * the Phase-4 SharedPreferences implementation is gone). Writes go through
 * [SettingsRepository]; the StateFlow mirrors the persisted value.
 */
class ThemeModeController(
    private val settingsRepository: SettingsRepository,
    private val scope: CoroutineScope,
) {

    private val _mode = MutableStateFlow(ThemeMode.SYSTEM)

    /** Hot flow of the effective theme mode; converges on the persisted value. */
    val mode: StateFlow<ThemeMode> = _mode
        .stateIn(scope, SharingStarted.Eagerly, ThemeMode.SYSTEM)

    init {
        settingsRepository.settings
            .map { it.themeMode }
            .onEach { persisted -> if (persisted != _mode.value) _mode.value = persisted }
            .launchIn(scope)
    }

    fun set(value: ThemeMode) {
        if (_mode.value == value) return
        _mode.value = value
        scope.launch { settingsRepository.update { it.copy(themeMode = value) } }
    }

    /** Cycles SYSTEM -> LIGHT -> DARK -> SYSTEM (used by the compact header control). */
    fun cycle() {
        set(
            when (_mode.value) {
                ThemeMode.SYSTEM -> ThemeMode.LIGHT
                ThemeMode.LIGHT -> ThemeMode.DARK
                ThemeMode.DARK -> ThemeMode.SYSTEM
            },
        )
    }
}
