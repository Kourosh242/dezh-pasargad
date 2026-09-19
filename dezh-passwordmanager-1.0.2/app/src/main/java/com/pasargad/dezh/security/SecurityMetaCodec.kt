package com.pasargad.dezh.security

import com.pasargad.dezh.cryptography.ByteCodec

/**
 * Integrity-protected security metadata (itself encrypted by [KeystoreGateway]):
 * KDF parameters + failed-attempt counters for interactive brute-force throttling.
 */
data class SecurityMeta(
    val kdfId: Int,
    val iterations: Int,
    val failedAttemptCount: Int,
    val lastFailedAtMs: Long,
)

@Suppress("MagicNumber")
object SecurityMetaCodec {

    /** Layout: kdfId(u8) | iterations(u32) | failedAttemptCount(u32) | lastFailedAtMs(s64) = 17 bytes. */
    const val ENCODED_SIZE = 1 + 4 + 4 + 8

    fun encode(meta: SecurityMeta): ByteArray = ByteArray(ENCODED_SIZE).also { out ->
        out[0] = meta.kdfId.toByte()
        ByteCodec.putUInt32(out, 1, meta.iterations)
        ByteCodec.putUInt32(out, 5, meta.failedAttemptCount)
        ByteCodec.putInt64(out, 9, meta.lastFailedAtMs)
    }

    fun decode(bytes: ByteArray): SecurityMeta {
        if (bytes.size < ENCODED_SIZE) throw SecurityMetaCorruptedException()
        return SecurityMeta(
            kdfId = bytes[0].toInt() and 0xFF,
            iterations = ByteCodec.readUInt32(bytes, 1),
            failedAttemptCount = ByteCodec.readUInt32(bytes, 5),
            lastFailedAtMs = ByteCodec.readInt64(bytes, 9),
        )
    }
}
