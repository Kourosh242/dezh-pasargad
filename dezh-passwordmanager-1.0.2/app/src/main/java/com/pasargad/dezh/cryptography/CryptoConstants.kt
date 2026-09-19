package com.pasargad.dezh.cryptography

/**
 * Central cryptographic parameters for Dezh-e Pasargad.
 * Values here define the versioned on-disk formats (DPKW / DPVG).
 */
object CryptoConstants {

    // Format magics & versions
    const val KEY_WRAP_MAGIC = "DPKW"
    const val DATA_CONTAINER_MAGIC = "DPVG"
    const val FORMAT_VERSION_V1: Int = 1

    // Algorithm identifiers
    const val ALGORITHM_AES_256_GCM: Int = 1
    const val KDF_NONE: Int = 0
    const val KDF_PBKDF2_HMAC_SHA256: Int = 1

    // Key identity (EncryptionContainer.keyId)
    const val KEY_ID_SESSION_DEK: Int = 0

    // Sizes
    const val AES_KEY_SIZE_BYTES = 32
    const val GCM_NONCE_SIZE_BYTES = 12
    const val GCM_TAG_SIZE_BYTES = 16
    const val GCM_TAG_BITS = 128
    const val KDF_SALT_SIZE_BYTES = 16

    // KDF effort — OWASP-aligned default for PBKDF2-HMAC-SHA256.
    // Test-only code paths override this via constructor parameters.
    const val PBKDF2_DEFAULT_ITERATIONS = 600_000

    // Master password bounds (DoS guard for the KDF)
    const val MAX_PASSWORD_LENGTH = 128
}
