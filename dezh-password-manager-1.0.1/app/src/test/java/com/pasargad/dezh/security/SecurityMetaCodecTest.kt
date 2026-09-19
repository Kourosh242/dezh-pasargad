package com.pasargad.dezh.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class SecurityMetaCodecTest {

    @Test
    fun `encode decode roundtrip preserves values`() {
        val meta = SecurityMeta(
            kdfId = 1,
            iterations = 600_000,
            failedAttemptCount = 3,
            lastFailedAtMs = 1_724_000_000_123L,
        )
        val decoded = SecurityMetaCodec.decode(SecurityMetaCodec.encode(meta))
        assertEquals(meta, decoded)
    }

    @Test
    fun `truncated meta is rejected`() {
        val bytes = SecurityMetaCodec.encode(SecurityMeta(1, 600_000, 0, 0L))
        assertThrows(SecurityMetaCorruptedException::class.java) {
            SecurityMetaCodec.decode(bytes.copyOfRange(0, bytes.size - 1))
        }
    }

    @Test
    fun `encoded size is stable for the versioned format`() {
        assertEquals(17, SecurityMetaCodec.ENCODED_SIZE)
        assertEquals(SecurityMetaCodec.ENCODED_SIZE, SecurityMetaCodec.encode(SecurityMeta(1, 1, 0, 0L)).size)
        assertTrue(SecurityMetaCodec.decode(ByteArray(SecurityMetaCodec.ENCODED_SIZE)).failedAttemptCount == 0)
    }
}
