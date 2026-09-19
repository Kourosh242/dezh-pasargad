package com.pasargad.dezh.cryptography

import java.security.SecureRandom
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class KeyWrapperTest {

    private val secureRandom = SecureRandom()
    private val kdfEngine = Pbkdf2HmacSha256Engine()
    private val cipher = AesGcmCipher(secureRandom)
    private val keyWrapper = KeyWrapper(kdfEngine, cipher, secureRandom)

    private val testIterations = 2_000
    private val dek = ByteArray(CryptoConstants.AES_KEY_SIZE_BYTES).also(secureRandom::nextBytes)
    private val password = "Master-Pass-1403!".toCharArray()

    @Test
    fun `key wrap and unwrap roundtrip succeeds`() {
        val container = keyWrapper.wrap(dek, password, testIterations)
        val unwrapped = keyWrapper.unwrap(container, password)
        assertTrue(dek.contentEquals(unwrapped))
    }

    @Test
    fun `wrong password fails with wrong passphrase`() {
        val container = keyWrapper.wrap(dek, password, testIterations)
        assertThrows(CryptoException.WrongPassphrase::class.java) {
            keyWrapper.unwrap(container, "WrongPass-9876".toCharArray())
        }
    }

    @Test
    fun `every wrap uses a unique salt and nonce`() {
        val first = keyWrapper.wrap(dek, password, testIterations)
        val second = keyWrapper.wrap(dek, password, testIterations)
        assertFalse(first.salt.contentEquals(second.salt))
        assertFalse(first.nonce.contentEquals(second.nonce))
        assertFalse(first.payload.contentEquals(second.payload))
    }

    @Test
    fun `tampered wrapped key fails authentication`() {
        val container = keyWrapper.wrap(dek, password, testIterations)
        val tampered = container.copy(
            payload = container.payload.copyOf().also {
                it[it.size / 2] = (it[it.size / 2].toInt() xor 0x02).toByte()
            },
        )
        assertThrows(CryptoException.WrongPassphrase::class.java) {
            keyWrapper.unwrap(tampered, password)
        }
    }

    @Test
    fun `corrupted wrap structure is rejected on decode`() {
        val bytes = keyWrapper.wrap(dek, password, testIterations).encode()
        assertThrows(CryptoException.CorruptedContainer::class.java) {
            KeyWrapContainer.decode(bytes.copyOfRange(0, 20))
        }
        val wrongMagic = bytes.clone().also { it[0] = 'X'.code.toByte() }
        assertThrows(CryptoException.CorruptedContainer::class.java) { KeyWrapContainer.decode(wrongMagic) }
    }

    @Test
    fun `kdf is deterministic for the same inputs and differs across salts`() {
        val saltA = ByteArray(16).also(secureRandom::nextBytes)
        val saltB = ByteArray(16).also(secureRandom::nextBytes)
        val outA1 = kdfEngine.derive(password, saltA, testIterations, 32)
        val outA2 = kdfEngine.derive(password, saltA, testIterations, 32)
        val outB = kdfEngine.derive(password, saltB, testIterations, 32)
        assertTrue(outA1.contentEquals(outA2))
        assertFalse(outA1.contentEquals(outB))
        assertEquals(32, outA1.size)
    }

    @Test
    fun `wrapped container carries the kdf metadata needed for decryption`() {
        val container = keyWrapper.wrap(dek, password, testIterations)
        assertEquals(CryptoConstants.FORMAT_VERSION_V1, container.formatVersion)
        assertEquals(CryptoConstants.ALGORITHM_AES_256_GCM, container.algorithmId)
        assertEquals(CryptoConstants.KDF_PBKDF2_HMAC_SHA256, container.kdfId)
        assertEquals(testIterations, container.iterations)
        assertEquals(CryptoConstants.KDF_SALT_SIZE_BYTES, container.salt.size)
        assertEquals(CryptoConstants.GCM_NONCE_SIZE_BYTES, container.nonce.size)
    }
}
