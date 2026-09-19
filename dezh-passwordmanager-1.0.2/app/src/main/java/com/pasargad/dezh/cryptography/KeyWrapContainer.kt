@file:Suppress("MagicNumber")

package com.pasargad.dezh.cryptography

/**
 * Versioned container for the password-wrapped vault key (DEK).
 *
 * Layout v1 (big-endian):
 *   magic "DPKW" (4) | formatVersion (1) | algorithmId (1) | kdfId (1)
 *   | iterations (4) | saltLen (1) | salt | nonceLen (1) | nonce
 *   | payloadLen (4) | payload (ciphertext || tag of the wrapped DEK)
 *
 * This file is the ONLY place where vault key material touches storage, and it
 * is useless without the master password.
 */
data class KeyWrapContainer(
    val formatVersion: Int,
    val algorithmId: Int,
    val kdfId: Int,
    val iterations: Int,
    val salt: ByteArray,
    val nonce: ByteArray,
    val payload: ByteArray,
) {

    fun encode(): ByteArray {
        val out = ByteArray(HEADER_SIZE + payload.size)
        MAGIC_BYTES.copyInto(out, 0)
        out[4] = formatVersion.toByte()
        out[5] = algorithmId.toByte()
        out[6] = kdfId.toByte()
        ByteCodec.putUInt32(out, 7, iterations)
        out[11] = salt.size.toByte()
        salt.copyInto(out, 12)
        val nonceLenOffset = 12 + salt.size
        out[nonceLenOffset] = nonce.size.toByte()
        nonce.copyInto(out, nonceLenOffset + 1)
        val payloadLenOffset = nonceLenOffset + 1 + nonce.size
        ByteCodec.putUInt32(out, payloadLenOffset, payload.size)
        payload.copyInto(out, HEADER_SIZE)
        return out
    }

    companion object {
        const val HEADER_SIZE = 4 + 1 + 1 + 1 + 4 + 1 + CryptoConstants.KDF_SALT_SIZE_BYTES +
            1 + CryptoConstants.GCM_NONCE_SIZE_BYTES + 4
        private val MAGIC_BYTES = CryptoConstants.KEY_WRAP_MAGIC.encodeToByteArray()

        @Suppress("ThrowsCount")
        fun decode(bytes: ByteArray): KeyWrapContainer {
            if (bytes.size < HEADER_SIZE) throw CryptoException.CorruptedContainer()
            if (!MAGIC_BYTES.contentEquals(bytes.copyOfRange(0, 4))) throw CryptoException.CorruptedContainer()
            val version = bytes[4].toInt() and 0xFF
            if (version != CryptoConstants.FORMAT_VERSION_V1) throw CryptoException.UnsupportedVersion(version)
            val algorithm = bytes[5].toInt() and 0xFF
            if (algorithm != CryptoConstants.ALGORITHM_AES_256_GCM) throw CryptoException.UnsupportedVersion(algorithm)
            val kdfId = bytes[6].toInt() and 0xFF
            if (kdfId != CryptoConstants.KDF_PBKDF2_HMAC_SHA256) throw CryptoException.UnsupportedVersion(kdfId)
            val iterations = ByteCodec.readUInt32(bytes, 7)
            if (iterations < MIN_ITERATIONS || iterations > MAX_ITERATIONS) throw CryptoException.CorruptedContainer()
            val saltLen = bytes[11].toInt() and 0xFF
            if (saltLen !in MIN_SALT_LEN..MAX_SALT_LEN) throw CryptoException.CorruptedContainer()
            if (bytes.size < HEADER_SIZE - CryptoConstants.KDF_SALT_SIZE_BYTES + saltLen) {
                throw CryptoException.CorruptedContainer()
            }
            val salt = bytes.copyOfRange(12, 12 + saltLen)
            val nonceLenOffset = 12 + saltLen
            val nonceLen = bytes[nonceLenOffset].toInt() and 0xFF
            if (nonceLen != CryptoConstants.GCM_NONCE_SIZE_BYTES) throw CryptoException.CorruptedContainer()
            val nonce = bytes.copyOfRange(nonceLenOffset + 1, nonceLenOffset + 1 + nonceLen)
            val payloadLenOffset = nonceLenOffset + 1 + nonceLen
            val payloadLen = ByteCodec.readUInt32(bytes, payloadLenOffset)
            val payloadStart = HEADER_SIZE - CryptoConstants.KDF_SALT_SIZE_BYTES + saltLen
            if (payloadLen > bytes.size - payloadStart || payloadLen <= CryptoConstants.GCM_TAG_SIZE_BYTES) {
                throw CryptoException.CorruptedContainer()
            }
            val payload = bytes.copyOfRange(payloadStart, payloadStart + payloadLen)
            return KeyWrapContainer(version, algorithm, kdfId, iterations, salt, nonce, payload)
        }

        private const val MIN_ITERATIONS = 1_000
        private const val MAX_ITERATIONS = 10_000_000
        private const val MIN_SALT_LEN = 8
        private const val MAX_SALT_LEN = 64
    }
}
