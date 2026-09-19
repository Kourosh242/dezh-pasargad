package com.pasargad.dezh.data.backup

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import java.io.FileNotFoundException
import java.io.IOException
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Why a file operation failed — maps 1:1 to the user-facing backup errors. */
enum class FileFailure { INVALID_PATH, IO_ERROR, INSUFFICIENT_STORAGE, FILE_TOO_LARGE, EMPTY_FILE }

/** Typed I/O failure carrying the user-facing [FileFailure] classification. */
class BackupFileException(val failure: FileFailure, message: String, cause: Throwable? = null) :
    Exception(message, cause)

/**
 * Reads/writes backup bytes through an opaque URI (SAF). The implementation
 * never trusts the filename or MIME type — it only streams bytes, and every
 * failure is classified into a [FileFailure] instead of leaking raw exceptions.
 */
interface BackupFileGateway {
    /** @throws BackupFileException on any classified failure. */
    suspend fun write(uri: Uri, bytes: ByteArray)

    /** @throws BackupFileException on any classified failure. */
    suspend fun read(uri: Uri): ByteArray

    /** Best-effort display name (never trusted for parsing decisions). */
    suspend fun displayName(uri: Uri): String?

    companion object {
        /** Upper bound for restore input; protects the app from absurd files. */
        const val MAX_BACKUP_BYTES = 64L * 1024 * 1024
    }
}

/** SAF/[ContentResolver]-backed gateway. */
class SafBackupFileGateway(
    private val contentResolver: ContentResolver,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : BackupFileGateway {

    override suspend fun write(uri: Uri, bytes: ByteArray): Unit = withContext(ioDispatcher) {
        requireSupportedScheme(uri)
        val stream = try {
            contentResolver.openOutputStream(uri, "w") ?: throw invalidPath("Resolver returned no stream")
        } catch (e: BackupFileException) {
            throw e
        } catch (e: FileNotFoundException) {
            throw invalidPath(e.message ?: "File not found", e)
        } catch (e: IOException) {
            throw classify(e)
        }
        stream.use { output ->
            try {
                output.write(bytes)
                output.flush()
            } catch (e: IOException) {
                throw classify(e)
            }
        }
    }

    @Suppress("CyclomaticComplexMethod") // every failure is classified explicitly
    override suspend fun read(uri: Uri): ByteArray = withContext(ioDispatcher) {
        requireSupportedScheme(uri)
        val declaredSize = querySize(uri)
        if (declaredSize != null && declaredSize > BackupFileGateway.MAX_BACKUP_BYTES) {
            throw BackupFileException(FileFailure.FILE_TOO_LARGE, "Declared size exceeds cap")
        }
        val stream = try {
            contentResolver.openInputStream(uri) ?: throw invalidPath("Resolver returned no stream")
        } catch (e: BackupFileException) {
            throw e
        } catch (e: FileNotFoundException) {
            throw invalidPath(e.message ?: "File not found", e)
        } catch (e: IOException) {
            throw classify(e)
        }
        stream.use { input ->
            val buffer = ByteArrayOutputStream()
            val chunk = ByteArray(CHUNK_BYTES)
            try {
                while (true) {
                    val read = input.read(chunk)
                    if (read == END_OF_STREAM) break
                    buffer.write(chunk, 0, read)
                    if (buffer.size() > BackupFileGateway.MAX_BACKUP_BYTES) {
                        throw BackupFileException(FileFailure.FILE_TOO_LARGE, "Read exceeded cap")
                    }
                }
            } catch (e: BackupFileException) {
                throw e
            } catch (e: IOException) {
                throw classify(e)
            }
            val bytes = buffer.toByteArray()
            if (bytes.isEmpty()) throw BackupFileException(FileFailure.EMPTY_FILE, "File is empty")
            bytes
        }
    }

    override suspend fun displayName(uri: Uri): String? = withContext(ioDispatcher) {
        runCatching {
            contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                val column = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (column >= 0 && cursor.moveToFirst()) cursor.getString(column) else null
            }
        }.getOrNull()
    }

    private fun requireSupportedScheme(uri: Uri) {
        val scheme = uri.scheme
        if (scheme != ContentResolver.SCHEME_CONTENT && scheme != ContentResolver.SCHEME_FILE) {
            throw BackupFileException(FileFailure.INVALID_PATH, "Unsupported uri scheme: $scheme")
        }
    }

    private fun querySize(uri: Uri): Long? = runCatching {
        contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { cursor ->
            val column = cursor.getColumnIndex(OpenableColumns.SIZE)
            if (column >= 0 && cursor.moveToFirst() && !cursor.isNull(column)) cursor.getLong(column) else null
        }
    }.getOrNull()

    private fun invalidPath(message: String, cause: Throwable? = null) =
        BackupFileException(FileFailure.INVALID_PATH, message, cause)

    /** Space-related IOExceptions are reported as storage exhaustion, not generic I/O. */
    private fun classify(e: IOException): BackupFileException = classifyIoException(e)

    internal companion object {
        const val CHUNK_BYTES = 64 * 1024
        const val END_OF_STREAM = -1
        const val NO_SPACE_HINT = "space"

        /** Pure classification of stream failures (unit-testable without Android). */
        fun classifyIoException(e: IOException): BackupFileException = when {
            e.message?.contains(NO_SPACE_HINT, ignoreCase = true) == true ||
                e.suppressed.any { suppressed -> suppressed.message?.contains(NO_SPACE_HINT, ignoreCase = true) == true } ->
                BackupFileException(FileFailure.INSUFFICIENT_STORAGE, e.message ?: "No space", e)

            else -> BackupFileException(FileFailure.IO_ERROR, e.message ?: "I/O failure", e)
        }
    }
}
