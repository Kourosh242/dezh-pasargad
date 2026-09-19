package com.pasargad.dezh.data.update

import android.content.Context
import com.pasargad.dezh.domain.update.ReleaseApkAsset
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Classified download failure for the update APK. */
class ApkDownloadException(message: String, cause: Throwable? = null) : IOException(message, cause)

/**
 * Streams the release APK into app-private cache (never user-supplied paths).
 * Guards: HTTPS + GitHub host allowlist, hard size cap, optional SHA-256
 * digest verification, partial-file cleanup on every failure path.
 */
class HttpApkDownloader(
    context: Context,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ApkDownloadGateway {

    private val updatesDir = File(context.applicationContext.cacheDir, UPDATES_DIR).apply { mkdirs() }

    @Suppress("TooGenericExceptionCaught") // cleanup-and-rethrow: partial file must never survive
    override suspend fun download(
        asset: ReleaseApkAsset,
        tag: String,
        onProgress: (Int) -> Unit,
    ): File = withContext(ioDispatcher) {
        if (!UpdateDownloadGuard.isAllowedApkUrl(asset.url)) {
            throw ApkDownloadException("Update asset URL is outside the allowed channel hosts")
        }
        if (asset.sizeBytes > UpdateDownloadGuard.MAX_APK_BYTES) {
            throw ApkDownloadException("Update asset exceeds the size cap")
        }
        // One update file at a time: drop leftovers from previous attempts.
        updatesDir.listFiles()?.forEach { it.delete() }
        val target = File(updatesDir, "dezh-update-${UpdateDownloadGuard.sanitizeFileName(tag)}.apk")
        try {
            downloadTo(asset, target, onProgress)
            verifyDigest(asset, target)
            target
        } catch (failure: Throwable) {
            // Deliberately broad: EVERY failure path must remove the partial
            // file before propagating (cleanup, not handling).
            target.delete()
            throw failure
        }
    }

    private fun downloadTo(asset: ReleaseApkAsset, target: File, onProgress: (Int) -> Unit) {
        val connection = openConnection(asset.url)
        try {
            streamTo(connection, asset, target, onProgress)
        } finally {
            connection.disconnect()
        }
    }

    private fun openConnection(url: String): HttpURLConnection {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = CONNECT_TIMEOUT_MS
        connection.readTimeout = READ_TIMEOUT_MS
        connection.instanceFollowRedirects = true
        connection.setRequestProperty("User-Agent", USER_AGENT)
        if (connection.responseCode !in HTTP_OK..HTTP_OK_MAX) {
            connection.disconnect()
            throw ApkDownloadException("Update download HTTP ${connection.responseCode}")
        }
        return connection
    }

    private fun streamTo(
        connection: HttpURLConnection,
        asset: ReleaseApkAsset,
        target: File,
        onProgress: (Int) -> Unit,
    ) {
        val total = asset.sizeBytes.takeIf { it > 0 } ?: connection.contentLengthLong
        if (total > UpdateDownloadGuard.MAX_APK_BYTES) {
            throw ApkDownloadException("Update payload exceeds the size cap")
        }
        connection.inputStream.use { input ->
            target.outputStream().use { output ->
                copyWithProgress(input, output, total, onProgress)
                output.flush()
            }
        }
    }

    private fun copyWithProgress(
        input: java.io.InputStream,
        output: java.io.OutputStream,
        total: Long,
        onProgress: (Int) -> Unit,
    ) {
        val buffer = ByteArray(COPY_BUFFER_BYTES)
        var written = 0L
        var lastPercent = -1
        while (true) {
            val read = input.read(buffer)
            if (read == END_OF_STREAM) break
            written += read
            if (written > UpdateDownloadGuard.MAX_APK_BYTES) {
                throw ApkDownloadException("Update payload exceeds the size cap")
            }
            output.write(buffer, 0, read)
            val percent = (written * PERCENT_SCALE / total).toInt()
            if (total > 0 && percent != lastPercent) {
                lastPercent = percent
                onProgress(percent)
            }
        }
    }

    private fun verifyDigest(asset: ReleaseApkAsset, file: File) {
        val expected = asset.sha256Hex ?: return // channel published no digest — size cap + GCM-signed channel suffice
        val actual = file.inputStream().use { input ->
            val digest = MessageDigest.getInstance(DIGEST_ALGORITHM)
            val buffer = ByteArray(COPY_BUFFER_BYTES)
            while (true) {
                val read = input.read(buffer)
                if (read == END_OF_STREAM) break
                digest.update(buffer, 0, read)
            }
            digest.digest().joinToString(separator = "") { "%02x".format(it) }
        }
        if (!UpdateDownloadGuard.isDigestMatch(expected, actual)) {
            throw ApkDownloadException("Update APK digest mismatch")
        }
    }

    private companion object {
        const val UPDATES_DIR = "updates"
        const val CONNECT_TIMEOUT_MS = 15_000
        const val READ_TIMEOUT_MS = 30_000
        const val HTTP_OK = 200
        const val HTTP_OK_MAX = 299
        const val COPY_BUFFER_BYTES = 64 * 1024
        const val END_OF_STREAM = -1
        const val PERCENT_SCALE = 100L
        const val DIGEST_ALGORITHM = "SHA-256"
        const val USER_AGENT = "dezh-pasargad-self-update"
    }
}
