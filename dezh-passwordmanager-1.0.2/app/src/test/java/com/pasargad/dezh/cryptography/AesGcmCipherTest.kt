package com.pasargad.dezh.cryptography

import java.security.SecureRandom
import java.util.Base64
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class AesGcmCipherTest {

    private val secureRandom = SecureRandom()
    private val cipher = AesGcmCipher(secureRandom)
    private val key = ByteArray(CryptoConstants.AES_KEY_SIZE_BYTES).also(secureRandom::nextBytes)
    private val plaintext = "داده‌های محرمانهٔ گاوصندوق پاسارگاد".encodeToByteArray()

    @Test
    fun `encryption and decryption roundtrip succeeds`() {
        val sealed = cipher.encrypt(key, plaintext)

        assertEquals(CryptoConstants.GCM_NONCE_SIZE_BYTES, sealed.nonce.size)
        assertTrue(sealed.payload.size > CryptoConstants.GCM_TAG_SIZE_BYTES)

        val decrypted = cipher.decrypt(key, sealed)
        assertTrue(plaintext.contentEquals(decrypted))
    }

    @Test
    fun `wrong key fails authentication`() {
        val wrongKey = ByteArray(CryptoConstants.AES_KEY_SIZE_BYTES).also(secureRandom::nextBytes)
        val sealed = cipher.encrypt(key, plaintext)
        assertThrows(CryptoException.AuthenticationFailed::class.java) {
            cipher.decrypt(wrongKey, sealed)
        }
    }

    @Test
    fun `tampered authentication tag fails`() {
        val sealed = cipher.encrypt(key, plaintext)
        sealed.payload[sealed.payload.size - 1] = (sealed.payload.last().toInt() xor 0x01).toByte()
        assertThrows(CryptoException.AuthenticationFailed::class.java) {
            cipher.decrypt(key, sealed)
        }
    }

    @Test
    fun `tampered ciphertext fails authentication`() {
        val sealed = cipher.encrypt(key, plaintext)
        sealed.payload[0] = (sealed.payload[0].toInt() xor 0x01).toByte()
        assertThrows(CryptoException.AuthenticationFailed::class.java) {
            cipher.decrypt(key, sealed)
        }
    }

    @Test
    fun `truncated payload is rejected structurally`() {
        val sealed = cipher.encrypt(key, plaintext)
        val truncated = AesGcmCipher.SealedPayload(sealed.nonce, sealed.payload.copyOfRange(0, 8))
        assertThrows(CryptoException.CorruptedContainer::class.java) {
            cipher.decrypt(key, truncated)
        }
    }

    @Test
    fun `nonces are unique across many encryptions`() {
        val encoded = HashSet<String>()
        repeat(500) {
            val sealed = cipher.encrypt(key, plaintext)
            encoded.add(Base64.getEncoder().encodeToString(sealed.nonce))
        }
        assertEquals(500, encoded.size)
    }

    @Test
    fun `same plaintext twice yields different ciphertexts`() {
        val first = cipher.encrypt(key, plaintext)
        val second = cipher.encrypt(key, plaintext)
        assertFalse(first.nonce.contentEquals(second.nonce))
        assertFalse(first.payload.contentEquals(second.payload))
    }

    @Test
    fun `aad mismatch fails authentication`() {
        val sealed = cipher.encrypt(key, plaintext, aad = "context-a".encodeToByteArray())
        assertThrows(CryptoException.AuthenticationFailed::class.java) {
            cipher.decrypt(key, sealed, aad = "context-b".encodeToByteArray())
        }
        val decrypted = cipher.decrypt(key, sealed, aad = "context-a".encodeToByteArray())
        assertTrue(plaintext.contentEquals(decrypted))
    }

    @Test
    fun `invalid key size is rejected`() {
        assertThrows(CryptoException.CorruptedContainer::class.java) {
            cipher.encrypt(ByteArray(16), plaintext)
        }
    }
}
