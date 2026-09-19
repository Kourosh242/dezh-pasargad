package com.pasargad.dezh.data.security

import com.pasargad.dezh.cryptography.CryptoConstants
import com.pasargad.dezh.cryptography.CryptoException
import com.pasargad.dezh.cryptography.KeyWrapContainer
import com.pasargad.dezh.cryptography.KeyWrapper
import com.pasargad.dezh.domain.VaultLockState
import com.pasargad.dezh.domain.VaultSecurityException
import com.pasargad.dezh.domain.VaultSecurityRepository
import com.pasargad.dezh.security.KeystoreGateway
import com.pasargad.dezh.security.LoginAttemptPolicy
import com.pasargad.dezh.security.SecurityMeta
import com.pasargad.dezh.security.SecurityMetaCodec
import com.pasargad.dezh.security.VaultSecurityStorage
import com.pasargad.dezh.security.VaultSession
import java.security.SecureRandom
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext

/**
 * File-backed implementation of [VaultSecurityRepository].
 *
 * Storage layout (app-private `filesDir/dezh/security/`):
 *  - keywrap.v1 : KeyWrapContainer (salt + iterations + GCM-wrapped DEK)
 *  - meta.v1    : Keystore-GCM(security meta) — tamper-evident KDF params & attempt counter
 *
 * The master password only exists as char[] during KDF; the DEK only exists in
 * VaultSession memory while unlocked. No secrets are logged or persisted.
 */
class FileVaultSecurityRepository(
    private val storage: VaultSecurityStorage,
    private val keyWrapper: KeyWrapper,
    private val keystoreGateway: KeystoreGateway,
    private val session: VaultSession,
    private val secureRandom: SecureRandom,
    private val defaultIterations: Int = CryptoConstants.PBKDF2_DEFAULT_ITERATIONS,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val timeSource: () -> Long = System::currentTimeMillis,
) : VaultSecurityRepository {

    override val lockState: StateFlow<VaultLockState> = session.lockState

    override suspend fun initialize() = withContext(ioDispatcher) {
        if (session.lockState.value == VaultLockState.Unlocked) return@withContext
        if (storage.hasKeyWrap()) session.markLocked() else session.markNotSetUp()
    }

    override suspend fun setup(masterPassword: CharArray) = withContext(ioDispatcher) {
        if (storage.hasKeyWrap()) throw VaultSecurityException.VaultAlreadySetUp()
        requireBounds(masterPassword)

        val dek = ByteArray(CryptoConstants.AES_KEY_SIZE_BYTES).also(secureRandom::nextBytes)
        try {
            // Meta first, then keywrap: an interrupted setup must never leave a
            // keywrap without its integrity-protected meta (or vice versa —
            // wipeAll on failure guarantees a clean re-setup).
            persistMeta(
                SecurityMeta(
                    kdfId = CryptoConstants.KDF_PBKDF2_HMAC_SHA256,
                    iterations = defaultIterations,
                    failedAttemptCount = 0,
                    lastFailedAtMs = 0L,
                ),
            )
            val wrapContainer = keyWrapper.wrap(dek, masterPassword, defaultIterations)
            storage.writeKeyWrap(wrapContainer.encode())
            session.unlockWithKey(dek)
        } catch (t: Throwable) {
            storage.wipeAll()
            session.markLocked()
            throw t
        } finally {
            dek.fill(0)
        }
    }

    override suspend fun unlock(masterPassword: CharArray) = withContext(ioDispatcher) {
        requireBounds(masterPassword)
        if (!storage.hasKeyWrap()) throw VaultSecurityException.VaultNotSetUp()

        val meta = loadMeta()
        val remainingBackoff = LoginAttemptPolicy.remainingBackoffMillis(
            meta.failedAttemptCount,
            meta.lastFailedAtMs,
            timeSource(),
        )
        if (remainingBackoff > 0) throw VaultSecurityException.BackoffRequired(remainingBackoff)

        val container = try {
            KeyWrapContainer.decode(storage.readKeyWrap())
        } catch (_: CryptoException) {
            throw VaultSecurityException.VaultDataCorrupted()
        }

        // Tamper check: wrap parameters must match the Keystore-protected meta.
        if (container.kdfId != meta.kdfId || container.iterations != meta.iterations) {
            throw VaultSecurityException.VaultDataCorrupted()
        }

        val dek = try {
            keyWrapper.unwrap(container, masterPassword)
        } catch (_: CryptoException.WrongPassphrase) {
            throw registerFailedAttempt(meta)
        } catch (_: CryptoException) {
            throw VaultSecurityException.VaultDataCorrupted()
        }

        try {
            persistMeta(meta.copy(failedAttemptCount = 0, lastFailedAtMs = 0L))
        } catch (_: Exception) {
            // Unlock stays valid; meta rewrite is retried on the next event.
        }
        session.unlockWithKey(dek)
        dek.fill(0)
    }

    override fun lock() = session.lock()

    @Suppress("ThrowsCount")
    private fun loadMeta(): SecurityMeta {
        val sealed = storage.readMeta() ?: throw VaultSecurityException.VaultDataCorrupted()
        val plain = try {
            keystoreGateway.decrypt(sealed)
        } catch (_: Exception) {
            throw VaultSecurityException.VaultDataCorrupted()
        }
        return try {
            SecurityMetaCodec.decode(plain)
        } catch (_: Exception) {
            throw VaultSecurityException.VaultDataCorrupted()
        }
    }

    private fun persistMeta(meta: SecurityMeta) {
        storage.writeMeta(keystoreGateway.encrypt(SecurityMetaCodec.encode(meta)))
    }

    private fun registerFailedAttempt(meta: SecurityMeta): VaultSecurityException.WrongMasterPassword {
        val updated = meta.copy(
            failedAttemptCount = (meta.failedAttemptCount + 1).coerceAtMost(Int.MAX_VALUE),
            lastFailedAtMs = timeSource(),
        )
        try {
            persistMeta(updated)
        } catch (_: Exception) {
            // Counter persistence failure must never leak information; the
            // in-memory policy still applies for this session.
        }
        return VaultSecurityException.WrongMasterPassword(
            nextAttemptDelayMillis = LoginAttemptPolicy.backoffDelayMillis(updated.failedAttemptCount),
        )
    }

    private fun requireBounds(password: CharArray) {
        require(password.isNotEmpty() && password.size <= CryptoConstants.MAX_PASSWORD_LENGTH) {
            "Master password length is out of the allowed range"
        }
    }
}
