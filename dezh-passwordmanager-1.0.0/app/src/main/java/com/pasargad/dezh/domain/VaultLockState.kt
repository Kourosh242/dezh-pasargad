package com.pasargad.dezh.domain

/**
 * Lifecycle state of the vault lock. Live only in memory — a fresh process
 * always starts at [Initializing] and resolves to [Locked] or [NotSetUp]
 * (fail-closed: no persistent "unlocked" state exists).
 */
enum class VaultLockState {
    /** App is determining whether the vault is set up (file probing). */
    Initializing,

    /** No vault exists yet → first-run onboarding. */
    NotSetUp,

    /** Vault exists but no key is held in memory. */
    Locked,

    /** DEK is held in memory (session only). */
    Unlocked,
}
