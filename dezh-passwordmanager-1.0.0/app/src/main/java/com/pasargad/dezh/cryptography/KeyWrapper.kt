package com.pasargad.dezh.cryptography

import java.security.SecureRandom

/**
 * Secure key wrapping/unwrapping:
 *   master password --(KDF)--> wrapping key (KEK) --(AES-256-GCM)--> wrapped DEK
 *
 * Rules enforced here:
 *  - DEK is always random (never derived from the password).
 *  - KEK lives only inside a call; it is zeroed in `finally`.
 *  - Every wrap uses a fresh random salt AND a fresh random GCM nonce.
 *  - Wrong password and tampering both surface as [CryptoException.WrongPassphrase]
 *    (GCM makes them indistinguishable — that is by design).
 */
class KeyWrapper(
    private val kdfEngine: PasswordKdfEngine,
    private val cipher: AesGcmCipher,
    private val secureRandom: SecureRandom,
) {

    fun wrap(
        dek: ByteArray,
        masterPassword: CharArray,
        iterations: Int = CryptoConstants.PBKDF2_DEFAULT_ITERATIONS,
    ): KeyWrapContainer {
        require(dek.size == CryptoConstants.AES_KEY_SIZE_BYTES) { "DEK size is invalid" }
        val salt = ByteArray(CryptoConstants.KDF_SALT_SIZE_BYTES).also(secureRandom::nextBytes)
        val kek = kdfEngine.derive(masterPassword, salt, iterations, CryptoConstants.AES_KEY_SIZE_BYTES)
        try {
            val sealed = cipher.encrypt(kek, dek)
            return KeyWrapContainer(
                formatVersion = CryptoConstants.FORMAT_VERSION_V1,
                algorithmId = CryptoConstants.ALGORITHM_AES_256_GCM,
                kdfId = CryptoConstants.KDF_PBKDF2_HMAC_SHA256,
                iterations = iterations,
                salt = salt,
                nonce = sealed.nonce,
                payload = sealed.payload,
            )
        } finally {
            kek.fill(0)
        }
    }

    fun unwrap(container: KeyWrapContainer, masterPassword: CharArray): ByteArray {
        if (container.algorithmId != CryptoConstants.ALGORITHM_AES_256_GCM ||
            container.kdfId != CryptoConstants.KDF_PBKDF2_HMAC_SHA256
        ) {
            throw CryptoException.UnsupportedVersion(container.formatVersion)
        }
        val kek = kdfEngine.derive(masterPassword, container.salt, container.iterations, CryptoConstants.AES_KEY_SIZE_BYTES)
        try {
            return try {
                cipher.decrypt(kek, AesGcmCipher.SealedPayload(container.nonce, container.payload))
            } catch (_: CryptoException.AuthenticationFailed) {
                throw CryptoException.WrongPassphrase()
            }
        } finally {
            kek.fill(0)
        }
    }
}
