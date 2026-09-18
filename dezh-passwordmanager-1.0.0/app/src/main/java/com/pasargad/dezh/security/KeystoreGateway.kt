package com.pasargad.dezh.security

/**
 * Abstraction over Android Keystore so the security pipeline is testable on the JVM.
 * The gateway encrypts/decrypts small blobs (security metadata) with a
 * non-exportable AES-256-GCM key. Output layout: iv(12) || ciphertext || tag.
 */
interface KeystoreGateway {
    fun encrypt(plaintext: ByteArray): ByteArray
    fun decrypt(sealed: ByteArray): ByteArray
}
