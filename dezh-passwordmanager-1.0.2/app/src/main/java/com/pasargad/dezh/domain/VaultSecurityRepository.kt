package com.pasargad.dezh.domain

import kotlinx.coroutines.flow.StateFlow

/**
 * Domain contract for everything around the master password & vault lock.
 * Implemented in the data layer (data/security).
 */
interface VaultSecurityRepository {

    /** Single source of truth for the lock lifecycle (StateFlow). */
    val lockState: StateFlow<VaultLockState>

    /** Resolves [VaultLockState.Initializing] into [VaultLockState.NotSetUp] / [VaultLockState.Locked]. */
    suspend fun initialize()

    /**
     * Creates the vault: generate DEK → KDF(password) → wrap → persist.
     * On success the session becomes [VaultLockState.Unlocked].
     */
    suspend fun setup(masterPassword: CharArray)

    /**
     * Verifies the master password by unwrapping the DEK (GCM auth tag acts as
     * the verifier — the password itself is never stored or compared).
     *
     * @throws VaultSecurityException.WrongMasterPassword wrong password (carries next-attempt backoff)
     * @throws VaultSecurityException.BackoffRequired too many failed attempts, retry later
     * @throws VaultSecurityException.VaultNotSetUp no vault on disk
     * @throws VaultSecurityException.VaultDataCorrupted security files are damaged/tampered
     */
    suspend fun unlock(masterPassword: CharArray)

    /** Locks the session and zeroes the in-memory DEK. */
    fun lock()
}

/** Domain-level security failures. Messages are static — no secrets, ever. */
sealed class VaultSecurityException(message: String) : Exception(message) {
    class VaultNotSetUp : VaultSecurityException("Vault is not set up")
    class VaultAlreadySetUp : VaultSecurityException("Vault is already set up")
    class WrongMasterPassword(val nextAttemptDelayMillis: Long) : VaultSecurityException("Master password is incorrect")
    class BackoffRequired(val remainingMillis: Long) : VaultSecurityException("Too many failed attempts; wait before retrying")
    class VaultDataCorrupted : VaultSecurityException("Vault security data is corrupted or tampered")
}
