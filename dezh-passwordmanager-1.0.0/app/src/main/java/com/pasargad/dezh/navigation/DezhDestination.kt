package com.pasargad.dezh.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/**
 * Full route map of Dezh-e Pasargad.
 *
 * Security rule: no secrets or sensitive payloads ever travel through
 * navigation arguments. Sensitive state stays in the security/vault layers and
 * is re-resolved inside each destination.
 */
@Serializable
sealed interface DezhDestination : NavKey {

    /** First screen: app identity / phase verification (Phase 1). */
    @Serializable
    data object Startup : DezhDestination

    /** First-run onboarding & master setup. */
    @Serializable
    data object Onboarding : DezhDestination

    /** Vault unlock (master credential / biometric). */
    @Serializable
    data object Unlock : DezhDestination

    /**
     * Main vault list. Category/favorites preselection are non-secret UI
     * arguments (category names are stored as plaintext metadata by design).
     */
    @Serializable
    data class Vault(val category: String? = null, val favoritesOnly: Boolean = false) : DezhDestination

    /** Vault search. */
    @Serializable
    data object Search : DezhDestination

    /** Add a new vault entry. */
    @Serializable
    data object AddEntry : DezhDestination

    /** Edit an existing entry (id resolved at destination — an opaque, non-secret identifier). */
    @Serializable
    data class EditEntry(val entryId: String) : DezhDestination

    /** Entry details (opaque id only — no secrets in navigation arguments). */
    @Serializable
    data class EntryDetails(val entryId: String) : DezhDestination

    /** Password/secret generator. */
    @Serializable
    data object Generator : DezhDestination

    /** Categories overview. */
    @Serializable
    data object Categories : DezhDestination

    /** Favorites list. */
    @Serializable
    data object Favorites : DezhDestination

    /** Encrypted backup export. */
    @Serializable
    data object Backup : DezhDestination

    /** Encrypted restore import. */
    @Serializable
    data object Restore : DezhDestination

    /** Settings hub. */
    @Serializable
    data object Settings : DezhDestination

    /** Security center (auto-lock, Keystore status, ...). */
    @Serializable
    data object Security : DezhDestination

    /** Theme preferences. */
    @Serializable
    data object Theme : DezhDestination

    /** About / licenses. */
    @Serializable
    data object About : DezhDestination
}
