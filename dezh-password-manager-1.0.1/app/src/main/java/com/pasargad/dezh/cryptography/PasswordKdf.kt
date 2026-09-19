package com.pasargad.dezh.cryptography

import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * Abstraction over the password-based KDF. Implementations MUST:
 *  - consume the password as [CharArray] (zeroable),
 *  - never persist or log anything,
 *  - return exactly [outputSizeBytes] bytes.
 */
interface PasswordKdfEngine {
    fun derive(password: CharArray, salt: ByteArray, iterations: Int, outputSizeBytes: Int): ByteArray
}

/**
 * PBKDF2-HMAC-SHA256 (KDF identifier 1 in the versioned formats).
 * Available on JVM and Android API 29+ without extra dependencies.
 */
class Pbkdf2HmacSha256Engine : PasswordKdfEngine {

    override fun derive(password: CharArray, salt: ByteArray, iterations: Int, outputSizeBytes: Int): ByteArray {
        require(iterations > 0) { "KDF iterations must be positive" }
        require(salt.isNotEmpty()) { "KDF salt must not be empty" }
        require(outputSizeBytes > 0) { "KDF output size must be positive" }
        val spec = PBEKeySpec(password, salt, iterations, outputSizeBytes * 8)
        return try {
            SecretKeyFactory.getInstance(ALGORITHM).generateSecret(spec).encoded
        } finally {
            // Best-effort: clear the password copy held by the spec.
            spec.clearPassword()
        }
    }

    private companion object {
        const val ALGORITHM = "PBKDF2WithHmacSHA256"
    }
}
