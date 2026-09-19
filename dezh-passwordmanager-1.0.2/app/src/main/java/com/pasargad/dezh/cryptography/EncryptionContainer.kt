@file:Suppress("MagicNumber")

package com.pasargad.dezh.cryptography

/**
 * Versioned encryption container for vault DATA (encrypted with the session DEK).
 *
 * Layout v1 (big-endian):
 *   magic "DPVG" (4) | formatVersion (1) | algorithmId (1) | keyId (1)
 *   | nonceLen (1) | nonce (12) | payloadLen (4) | payload (ciphertext || tag)
 *
 * The key itself is never part of the container — the holder of the session key
 * is identified by [keyId].
 */
data class EncryptionContainer(
    val formatVersion: Int,
    val algorithmId: Int,
    val keyId: Int,
    val nonce: ByteArray,
    val payload: ByteArray,
) {

    fun encode(): ByteArray {
        val out = ByteArray(HEADER_SIZE + payload.size)
        MAGIC_BYTES.copyInto(out, 0)
        out[4] = formatVersion.toByte()
        out[5] = algorithmId.toByte()
        out[6] = keyId.toByte()
        out[7] = nonce.size.toByte()
        nonce.copyInto(out, 8)
        ByteCodec.putUInt32(out, 8 + nonce.size, payload.size)
        payload.copyInto(out, HEADER_SIZE)
        return out
    }

    companion object {
        const val HEADER_SIZE = 4 + 1 + 1 + 1 + 1 + CryptoConstants.GCM_NONCE_SIZE_BYTES + 4
        private val MAGIC_BYTES = CryptoConstants.DATA_CONTAINER_MAGIC.encodeToByteArray()

        fun create(algorithmId: Int, keyId: Int, nonce: ByteArray, payload: ByteArray): EncryptionContainer =
            EncryptionContainer(
                formatVersion = CryptoConstants.FORMAT_VERSION_V1,
                algorithmId = algorithmId,
                keyId = keyId,
                nonce = nonce,
                payload = payload,
            )

        @Suppress("ThrowsCount")
        fun decode(bytes: ByteArray): EncryptionContainer {
            if (bytes.size < HEADER_SIZE) throw CryptoException.CorruptedContainer()
            if (!MAGIC_BYTES.contentEquals(bytes.copyOfRange(0, 4))) throw CryptoException.CorruptedContainer()
            val version = bytes[4].toInt() and 0xFF
            if (version != CryptoConstants.FORMAT_VERSION_V1) throw CryptoException.UnsupportedVersion(version)
            val algorithm = bytes[5].toInt() and 0xFF
            if (algorithm != CryptoConstants.ALGORITHM_AES_256_GCM) throw CryptoException.UnsupportedVersion(algorithm)
            val keyId = bytes[6].toInt() and 0xFF
            val nonceLen = bytes[7].toInt() and 0xFF
            if (nonceLen != CryptoConstants.GCM_NONCE_SIZE_BYTES) throw CryptoException.CorruptedContainer()
            val nonce = bytes.copyOfRange(8, 8 + nonceLen)
            val payloadOffset = 8 + nonceLen
            val payloadLen = ByteCodec.readUInt32(bytes, payloadOffset)
            if (payloadLen > bytes.size - HEADER_SIZE || payloadLen <= CryptoConstants.GCM_TAG_SIZE_BYTES) {
                throw CryptoException.CorruptedContainer()
            }
            val payload = bytes.copyOfRange(HEADER_SIZE, HEADER_SIZE + payloadLen)
            return EncryptionContainer(version, algorithm, keyId, nonce, payload)
        }
    }
}
