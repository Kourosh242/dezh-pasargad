package com.pasargad.dezh.cryptography

import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Authenticated-only AES-256-GCM primitive.
 * - Key: 32 bytes (AES-256).
 * - Nonce: 12 bytes, always freshly generated from [SecureRandom] per encryption (never caller-supplied).
 * - Tag: 128 bits, appended to the ciphertext payload.
 * - ECB / CBC / unauthenticated modes are intentionally unreachable.
 */
class AesGcmCipher(private val secureRandom: SecureRandom) {

    /** Payload layout: ciphertext || tag (standard GCM doFinal output). */
    class SealedPayload(val nonce: ByteArray, val payload: ByteArray)

    fun encrypt(key: ByteArray, plaintext: ByteArray, aad: ByteArray? = null): SealedPayload {
        validateKey(key)
        val nonce = ByteArray(CryptoConstants.GCM_NONCE_SIZE_BYTES).also(secureRandom::nextBytes)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(CryptoConstants.GCM_TAG_BITS, nonce))
        if (aad != null) cipher.updateAAD(aad)
        val payload = cipher.doFinal(plaintext)
        return SealedPayload(nonce, payload)
    }

    @Suppress("ThrowsCount")
    fun decrypt(key: ByteArray, sealed: SealedPayload, aad: ByteArray? = null): ByteArray {
        validateKey(key)
        if (sealed.nonce.size != CryptoConstants.GCM_NONCE_SIZE_BYTES) throw CryptoException.CorruptedContainer()
        if (sealed.payload.size <= CryptoConstants.GCM_TAG_SIZE_BYTES) throw CryptoException.CorruptedContainer()
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(CryptoConstants.GCM_TAG_BITS, sealed.nonce))
        if (aad != null) cipher.updateAAD(aad)
        return try {
            cipher.doFinal(sealed.payload)
        } catch (_: Exception) {
            // Wrong key, flipped bit, truncated tag — everything is "authentication failed" (fail-closed).
            throw CryptoException.AuthenticationFailed()
        }
    }

    private fun validateKey(key: ByteArray) {
        if (key.size != CryptoConstants.AES_KEY_SIZE_BYTES) throw CryptoException.CorruptedContainer()
    }

    private companion object {
        const val TRANSFORMATION = "AES/GCM/NoPadding"
    }
}
