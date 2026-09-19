package com.pasargad.dezh.backup

import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import kotlinx.serialization.json.Json

/**
 * Encrypts/decrypts the versioned backup envelope.
 *
 * Security properties:
 * - **Encrypted + authenticated:** AES-256-GCM (128-bit tag) under a key derived
 *   from the user's backup passphrase with PBKDF2-HMAC-SHA256.
 * - **Integrity protected:** SHA-256 checksum over the ciphertext in the header,
 *   plus the GCM tag; the AAD binds format/version/entryCount/createdAt so a
 *   tampered header can no longer decrypt.
 * - **Versioned & migration-friendly:** the header carries format id + version +
 *   all crypto parameters; future versions add fields, never implicit assumptions.
 * - **DB-format independent:** the on-disk form is pure JSON — no SQLite file.
 * - **No plaintext export:** credentials exist only inside the encrypted payload.
 */
class BackupCodec(
    private val random: SecureRandom = SecureRandom(),
    private val kdfIterations: Int = DEFAULT_KDF_ITERATIONS,
) {

    private val json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = false
        isLenient = false
        coerceInputValues = false
    }

    /** Builds the full encrypted backup file bytes for [payload]. */
    fun seal(payload: BackupPayload, passphrase: CharArray): ByteArray =
        sealWithDeclaredCount(payload, payload.entries.size, passphrase)

    /**
     * Same as [seal] but the header/AAD declare [declaredEntryCount] instead of
     * the payload's real size — the seam used to produce and then detect
     * truncated (incomplete) backups in tests and in migration tooling.
     */
    fun sealWithDeclaredCount(
        payload: BackupPayload,
        declaredEntryCount: Int,
        passphrase: CharArray,
    ): ByteArray {
        val salt = ByteArray(SALT_BYTES).also(random::nextBytes)
        val iv = ByteArray(IV_BYTES).also(random::nextBytes)
        val plaintext = json.encodeToString(BackupPayload.serializer(), payload).encodeToByteArray()
        val entryCount = declaredEntryCount
        val key = deriveKey(passphrase, salt, kdfIterations)
        val aad = aadFor(BACKUP_FORMAT_ID, BACKUP_VERSION_CURRENT, entryCount, payload.exportedAtEpochMs)
        val ciphertext = aesGcm(key, iv, aad, plaintext, encrypt = true)
        val envelope = BackupEnvelope(
            format = BACKUP_FORMAT_ID,
            version = BACKUP_VERSION_CURRENT,
            createdAtEpochMs = payload.exportedAtEpochMs,
            entryCount = entryCount,
            kdf = BackupKdfParams(
                algo = KDF_ALGO,
                iterations = kdfIterations,
                salt = Base64.getEncoder().encodeToString(salt),
            ),
            cipher = BackupCipherParams(
                algo = CIPHER_ALGO,
                iv = Base64.getEncoder().encodeToString(iv),
            ),
            ciphertextSha256 = sha256Base64(ciphertext),
            ciphertext = Base64.getEncoder().encodeToString(ciphertext),
        )
        return json.encodeToString(BackupEnvelope.serializer(), envelope).encodeToByteArray()
    }

    /** Verifies structure + checksum + version without the passphrase. */
    fun readHeader(bytes: ByteArray): BackupHeader {
        val envelope = parseEnvelope(bytes)
        requireSupportedVersion(envelope)
        verifyChecksum(envelope)
        return BackupHeader(
            version = envelope.version,
            createdAtEpochMs = envelope.createdAtEpochMs,
            entryCount = envelope.entryCount,
        )
    }

    /**
     * Opens a backup file. Throws [BackupParseException] with a precise
     * [ParseFailure] so the UI can tell wrong password apart from corruption.
     */
    @Suppress("ThrowsCount") // each failure mode carries a distinct ParseFailure
    fun open(bytes: ByteArray, passphrase: CharArray): BackupPayload {
        val envelope = parseEnvelope(bytes)
        requireSupportedVersion(envelope)
        verifyChecksum(envelope)

        val salt = decodeBase64(envelope.kdf.salt, ParseFailure.CORRUPTED)
        val iv = decodeBase64(envelope.cipher.iv, ParseFailure.CORRUPTED)
        val ciphertext = decodeBase64(envelope.ciphertext, ParseFailure.CORRUPTED)
        if (salt.isEmpty() || iv.size != IV_BYTES) throw BackupParseException(
            ParseFailure.CORRUPTED,
            "Invalid crypto parameters",
        )

        val key = deriveKey(passphrase, salt, envelope.kdf.iterations)
        val aad = aadFor(envelope.format, envelope.version, envelope.entryCount, envelope.createdAtEpochMs)
        val plaintext = try {
            aesGcm(key, iv, aad, ciphertext, encrypt = false)
        } catch (_: Exception) {
            // Ciphertext+checksum are intact, so the overwhelmingly likely cause
            // of an authentication failure is a wrong passphrase.
            throw BackupParseException(ParseFailure.WRONG_PASSWORD, "Authentication failed")
        }

        val payload = try {
            json.decodeFromString(BackupPayload.serializer(), plaintext.decodeToString())
        } catch (_: Exception) {
            throw BackupParseException(ParseFailure.CORRUPTED, "Decrypted payload is not valid")
        }
        if (payload.entries.size != envelope.entryCount) {
            throw BackupParseException(ParseFailure.INCOMPLETE, "Entry count mismatch")
        }
        if (payload.entries.any { it.id.isBlank() }) {
            throw BackupParseException(ParseFailure.INCOMPLETE, "Entry without an id")
        }
        return payload
    }

    // --- internals ---------------------------------------------------------------

    @Suppress("ThrowsCount")
    private fun parseEnvelope(bytes: ByteArray): BackupEnvelope {
        if (bytes.isEmpty()) throw BackupParseException(ParseFailure.INVALID_FORMAT, "Empty file")
        val text = try {
            Charsets.UTF_8.newDecoder()
                .onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
                .onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT)
                .decode(java.nio.ByteBuffer.wrap(bytes)).toString()
        } catch (_: Exception) {
            throw BackupParseException(ParseFailure.INVALID_FORMAT, "Not UTF-8 text")
        }
        return try {
            json.decodeFromString(BackupEnvelope.serializer(), text)
        } catch (_: Exception) {
            throw BackupParseException(ParseFailure.INVALID_FORMAT, "Not a Dezh backup envelope")
        }
    }

    @Suppress("ThrowsCount")
    private fun requireSupportedVersion(envelope: BackupEnvelope) {
        if (envelope.format != BACKUP_FORMAT_ID) {
            throw BackupParseException(ParseFailure.INVALID_FORMAT, "Unknown format id")
        }
        if (envelope.version < 1 || envelope.version > BACKUP_VERSION_CURRENT) {
            throw BackupParseException(ParseFailure.UNSUPPORTED_VERSION, "Version ${envelope.version}")
        }
        if (envelope.kdf.iterations !in MIN_KDF_ITERATIONS..MAX_KDF_ITERATIONS) {
            throw BackupParseException(ParseFailure.CORRUPTED, "Unreasonable KDF iteration count")
        }
    }

    private fun verifyChecksum(envelope: BackupEnvelope) {
        val ciphertext = decodeBase64(envelope.ciphertext, ParseFailure.CORRUPTED)
        if (sha256Base64(ciphertext) != envelope.ciphertextSha256) {
            throw BackupParseException(ParseFailure.INTEGRITY_FAILURE, "Ciphertext checksum mismatch")
        }
    }

    private fun deriveKey(passphrase: CharArray, salt: ByteArray, iterations: Int): SecretKeySpec {
        val factory = SecretKeyFactory.getInstance(KDF_ALGO)
        val spec = PBEKeySpec(passphrase, salt, iterations, KEY_BITS)
        val key = factory.generateSecret(spec).encoded
        spec.clearPassword()
        return SecretKeySpec(key, "AES")
    }

    private fun aesGcm(
        key: SecretKeySpec,
        iv: ByteArray,
        aad: ByteArray,
        input: ByteArray,
        encrypt: Boolean,
    ): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(
            if (encrypt) Cipher.ENCRYPT_MODE else Cipher.DECRYPT_MODE,
            key,
            GCMParameterSpec(TAG_BITS, iv),
        )
        cipher.updateAAD(aad)
        return cipher.doFinal(input)
    }

    private fun aadFor(
        format: String,
        version: Int,
        entryCount: Int,
        createdAt: Long,
    ): ByteArray = "$format|$version|$entryCount|$createdAt".encodeToByteArray()

    private fun decodeBase64(value: String, failure: ParseFailure): ByteArray = try {
        Base64.getDecoder().decode(value)
    } catch (_: Exception) {
        throw BackupParseException(failure, "Invalid base64")
    }

    private fun sha256Base64(bytes: ByteArray): String =
        Base64.getEncoder().encodeToString(MessageDigest.getInstance("SHA-256").digest(bytes))

    companion object {
        const val DEFAULT_KDF_ITERATIONS = 600_000
        const val MIN_KDF_ITERATIONS = 1_000
        const val MAX_KDF_ITERATIONS = 10_000_000
        const val SALT_BYTES = 16
        const val IV_BYTES = 12
        const val KEY_BITS = 256
        const val TAG_BITS = 128

        private const val KDF_ALGO = "PBKDF2WithHmacSHA256"
        private const val CIPHER_ALGO = "AES-256-GCM"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"

        /** Test hook: deterministic export time keeps golden checks stable. */
        fun payloadAt(exportedAt: Long, entries: List<BackupEntry>) =
            BackupPayload(exportedAtEpochMs = exportedAt, entries = entries)
    }
}
