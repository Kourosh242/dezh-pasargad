package com.pasargad.dezh.security

import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * JVM-test double for [KeystoreGateway]: a software AES-256-GCM key standing in
 * for the non-exportable AndroidKeyStore key. Same wire format (iv || ct || tag)
 * and the same fail-closed behavior on tag mismatch.
 */
class FakeKeystoreGateway : KeystoreGateway {

    private val secureRandom = SecureRandom()
    private val key: SecretKey = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()

    override fun encrypt(plaintext: ByteArray): ByteArray {
        val iv = ByteArray(IV_SIZE).also(secureRandom::nextBytes)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, iv))
        return iv + cipher.doFinal(plaintext)
    }

    override fun decrypt(sealed: ByteArray): ByteArray {
        if (sealed.size <= IV_SIZE) throw SecurityMetaCorruptedException()
        val iv = sealed.copyOfRange(0, IV_SIZE)
        val ciphertext = sealed.copyOfRange(IV_SIZE, sealed.size)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv))
        return try {
            cipher.doFinal(ciphertext)
        } catch (_: Exception) {
            throw SecurityMetaCorruptedException()
        }
    }

    private companion object {
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_SIZE = 12
    }
}
