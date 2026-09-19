package com.pasargad.dezh.cryptography

import java.security.SecureRandom
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class EncryptionContainerTest {

    private val secureRandom = SecureRandom()

    private fun sampleContainer(): EncryptionContainer {
        val nonce = ByteArray(CryptoConstants.GCM_NONCE_SIZE_BYTES).also(secureRandom::nextBytes)
        val payload = ByteArray(64).also(secureRandom::nextBytes)
        return EncryptionContainer.create(
            algorithmId = CryptoConstants.ALGORITHM_AES_256_GCM,
            keyId = CryptoConstants.KEY_ID_SESSION_DEK,
            nonce = nonce,
            payload = payload,
        )
    }

    @Test
    fun `encode decode roundtrip preserves all metadata`() {
        val container = sampleContainer()
        val decoded = EncryptionContainer.decode(container.encode())
        assertEquals(container.formatVersion, decoded.formatVersion)
        assertEquals(container.algorithmId, decoded.algorithmId)
        assertEquals(container.keyId, decoded.keyId)
        assertTrue(container.nonce.contentEquals(decoded.nonce))
        assertTrue(container.payload.contentEquals(decoded.payload))
    }

    @Test
    fun `wrong magic is rejected`() {
        val bytes = sampleContainer().encode()
        bytes[0] = 'X'.code.toByte()
        assertThrows(CryptoException.CorruptedContainer::class.java) { EncryptionContainer.decode(bytes) }
    }

    @Test
    fun `unsupported version is rejected`() {
        val bytes = sampleContainer().encode()
        bytes[4] = 9
        assertThrows(CryptoException.UnsupportedVersion::class.java) { EncryptionContainer.decode(bytes) }
    }

    @Test
    fun `unsupported algorithm is rejected`() {
        val bytes = sampleContainer().encode()
        bytes[5] = 7
        assertThrows(CryptoException.UnsupportedVersion::class.java) { EncryptionContainer.decode(bytes) }
    }

    @Test
    fun `truncated container is rejected`() {
        val bytes = sampleContainer().encode()
        assertThrows(CryptoException.CorruptedContainer::class.java) {
            EncryptionContainer.decode(bytes.copyOfRange(0, 10))
        }
    }

    @Test
    fun `payload shorter than the gcm tag is rejected`() {
        val container = sampleContainer().copy(payload = ByteArray(8))
        val bytes = container.encode()
        // rewrite payload length to 8 so the length check triggers
        ByteCodec.putUInt32(bytes, EncryptionContainer.HEADER_SIZE - 4, 8)
        assertThrows(CryptoException.CorruptedContainer::class.java) { EncryptionContainer.decode(bytes) }
    }
}
