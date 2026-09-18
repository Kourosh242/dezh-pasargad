package com.pasargad.dezh.backup

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Envelope format identity — independent of any Android database file format. */
const val BACKUP_FORMAT_ID = "dezh-backup"

/** Current envelope version. Only bumped when the payload contract changes. */
const val BACKUP_VERSION_CURRENT = 1

/**
 * Outer, on-disk envelope. Everything the parser needs before decryption is in
 * the header (format/version/kdf/cipher/checksum); [ciphertext] is AES-256-GCM
 * over the strict-JSON [BackupPayload]. Never contains credential plaintext.
 */
@Serializable
data class BackupEnvelope(
    @SerialName("format") val format: String,
    @SerialName("version") val version: Int,
    @SerialName("createdAtEpochMs") val createdAtEpochMs: Long,
    @SerialName("entryCount") val entryCount: Int,
    @SerialName("kdf") val kdf: BackupKdfParams,
    @SerialName("cipher") val cipher: BackupCipherParams,
    /** SHA-256 over the raw ciphertext bytes (base64) — early corruption/tamper check. */
    @SerialName("ciphertextSha256") val ciphertextSha256: String,
    /** Base64 AES-256-GCM ciphertext (includes the 128-bit tag). */
    @SerialName("ciphertext") val ciphertext: String,
)

@Serializable
data class BackupKdfParams(
    @SerialName("algo") val algo: String,
    @SerialName("iterations") val iterations: Int,
    /** Base64 salt. */
    @SerialName("salt") val salt: String,
)

@Serializable
data class BackupCipherParams(
    @SerialName("algo") val algo: String,
    /** Base64 12-byte GCM IV. */
    @SerialName("iv") val iv: String,
)

/** Decrypted, in-memory backup content — never written anywhere in this form. */
@Serializable
data class BackupPayload(
    @SerialName("exportedAtEpochMs") val exportedAtEpochMs: Long,
    @SerialName("entries") val entries: List<BackupEntry>,
)

/** One vault entry inside a backup payload. */
@Serializable
data class BackupEntry(
    @SerialName("id") val id: String,
    @SerialName("title") val title: String,
    @SerialName("username") val username: String,
    @SerialName("email") val email: String,
    @SerialName("password") val password: String,
    @SerialName("notes") val notes: String,
    @SerialName("category") val category: String,
    @SerialName("favorite") val favorite: Boolean,
    @SerialName("createdAtEpochMs") val createdAtEpochMs: Long,
    @SerialName("updatedAtEpochMs") val updatedAtEpochMs: Long,
)

/** Header-only view of a backup file (before the passphrase is involved). */
data class BackupHeader(
    val version: Int,
    val createdAtEpochMs: Long,
    val entryCount: Int,
)

/** Why a backup file was rejected. Mapped 1:1 to user-facing restore errors. */
enum class ParseFailure {
    /** Not JSON / not our envelope / missing mandatory fields. */
    INVALID_FORMAT,

    /** Structurally ours, but the version is not known to this build. */
    UNSUPPORTED_VERSION,

    /** Bytes are damaged (JSON torn, base64 broken, checksum mismatch). */
    CORRUPTED,

    /** Parsed but the content does not add up (count mismatch, missing rows). */
    INCOMPLETE,

    /** Ciphertext fails authentication — GCM tag/AAD mismatch. */
    INTEGRITY_FAILURE,

    /** Ciphertext intact but this passphrase does not open it. */
    WRONG_PASSWORD,
}

/** Typed parse failure so callers can map it to precise, non-generic messages. */
class BackupParseException(val reason: ParseFailure, message: String) : Exception(message)

/** User-chosen restore strategy. Cancel is handled by simply never invoking a mode. */
enum class RestoreMode { MERGE, REPLACE }
