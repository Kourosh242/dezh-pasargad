package com.pasargad.dezh.security

import com.pasargad.dezh.cryptography.CryptoConstants
import com.pasargad.dezh.cryptography.EncryptionContainer
import com.pasargad.dezh.domain.VaultLockState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * In-memory vault session — the ONLY place where the DEK lives while unlocked.
 *
 * Security properties:
 *  - The DEK never leaves this class (callers use encrypt/decrypt/useDataKey).
 *  - [lock] overwrites the key bytes with zeros and drops the reference.
 *  - Nothing here is persisted; process death ⇒ locked (fail-closed).
 *  - State is exposed as StateFlow for the Presentation layer.
 */
class VaultSession(private val cipher: com.pasargad.dezh.cryptography.AesGcmCipher) {

    private val _lockState = MutableStateFlow(VaultLockState.Initializing)
    val lockState: StateFlow<VaultLockState> = _lockState.asStateFlow()

    val isUnlocked: Boolean get() = _lockState.value == VaultLockState.Unlocked

    private var dataEncryptionKey: ByteArray? = null

    fun markNotSetUp() = synchronized(this) {
        wipeKey()
        _lockState.value = VaultLockState.NotSetUp
    }

    fun markLocked() = synchronized(this) {
        wipeKey()
        if (_lockState.value != VaultLockState.NotSetUp) {
            _lockState.value = VaultLockState.Locked
        }
    }

    /** Adopts a copy of the DEK and switches the session to unlocked. */
    fun unlockWithKey(dek: ByteArray) = synchronized(this) {
        wipeKey()
        dataEncryptionKey = dek.copyOf()
        _lockState.value = VaultLockState.Unlocked
    }

    fun lock() = markLocked()

    /** Runs [block] with the raw DEK — internal use only; never return the key itself. */
    fun <T> useDataKey(block: (ByteArray) -> T): T = synchronized(this) {
        val key = dataEncryptionKey ?: throw VaultSessionLockedException()
        block(key)
    }

    fun encrypt(plaintext: ByteArray): EncryptionContainer = useDataKey { key ->
        val sealed = cipher.encrypt(key, plaintext)
        EncryptionContainer.create(
            algorithmId = CryptoConstants.ALGORITHM_AES_256_GCM,
            keyId = CryptoConstants.KEY_ID_SESSION_DEK,
            nonce = sealed.nonce,
            payload = sealed.payload,
        )
    }

    fun decrypt(container: EncryptionContainer): ByteArray {
        if (container.keyId != CryptoConstants.KEY_ID_SESSION_DEK) {
            throw com.pasargad.dezh.cryptography.CryptoException.UnsupportedVersion(container.keyId)
        }
        return useDataKey { key ->
            cipher.decrypt(key, com.pasargad.dezh.cryptography.AesGcmCipher.SealedPayload(container.nonce, container.payload))
        }
    }

    /** Test/diagnostics only: a COPY of the current key (assert zeroing in tests). */
    internal fun diagnosticKeySnapshot(): ByteArray? = dataEncryptionKey?.copyOf()

    private fun wipeKey() {
        dataEncryptionKey?.fill(0)
        dataEncryptionKey = null
    }
}

/** Thrown when an operation requires an unlocked session. */
class VaultSessionLockedException : IllegalStateException("Vault session is locked")
