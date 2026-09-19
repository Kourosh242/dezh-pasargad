package com.pasargad.dezh.cryptography

/**
 * Sealed exception hierarchy for the cryptography layer.
 *
 * Security rule: messages are static strings and NEVER include key material,
 * passwords, plaintext, or ciphertext fragments.
 */
sealed class CryptoException(message: String) : Exception(message) {

    /** Structural damage: bad magic, bad sizes, truncation, invalid layout. */
    class CorruptedContainer : CryptoException("Container is corrupted or has an invalid structure")

    /** Known structure but unknown/unsupported version or algorithm identifier. */
    class UnsupportedVersion(version: Int) : CryptoException("Unsupported container format version: $version")

    /** GCM authentication tag verification failed (tampered data or wrong key). */
    class AuthenticationFailed : CryptoException("Ciphertext authentication failed")

    /** Password-based unwrap failed (wrong password or corrupted wrap). */
    class WrongPassphrase : CryptoException("Passphrase is incorrect or data is not authentic")
}
