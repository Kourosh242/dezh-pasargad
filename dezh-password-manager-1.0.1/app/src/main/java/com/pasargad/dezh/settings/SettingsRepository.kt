package com.pasargad.dezh.settings

import kotlinx.coroutines.flow.Flow

/**
 * Persistence contract for user settings. Implementations must keep values
 * local-only and must never be given sensitive material (passphrases, secrets).
 */
interface SettingsRepository {

    /** Live settings stream; emits defaults until the first write. */
    val settings: Flow<DezhSettings>

    /** Current snapshot (first emission of [settings]). */
    suspend fun current(): DezhSettings

    /** Functional update — the single write path for every setting. */
    suspend fun update(transform: (DezhSettings) -> DezhSettings)

    /** Resets everything to defaults (user-invoked "reset settings"). */
    suspend fun resetToDefaults()
}
