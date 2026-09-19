package com.pasargad.dezh.data.update

/**
 * Pure guards for the update download: URL allowlist, filename sanitisation
 * and digest comparison. Unit-testable without Android.
 */
object UpdateDownloadGuard {

    const val MAX_APK_BYTES: Long = 256L * 1024 * 1024

    private val ALLOWED_HOST_SUFFIXES = listOf("github.com", "githubusercontent.com")

    /** Only HTTPS on GitHub release hosts — the asset URL comes from our own channel payload. */
    fun isAllowedApkUrl(url: String): Boolean {
        if (!url.startsWith("https://")) return false
        val rest = url.removePrefix("https://")
        val host = rest.substringBefore('/').substringBefore(':').lowercase()
        return ALLOWED_HOST_SUFFIXES.any { host == it || host.endsWith(".$it") }
    }

    /** Tags may contain arbitrary text — the filename must stay ASCII-safe. */
    fun sanitizeFileName(raw: String): String =
        raw.map { if (it in ASCII_SAFE) it else '_' }
            .joinToString(separator = "")
            .take(FILE_NAME_MAX)

    fun isDigestMatch(expectedSha256Hex: String?, actualSha256Hex: String): Boolean =
        expectedSha256Hex == null || expectedSha256Hex.equals(actualSha256Hex, ignoreCase = true)

    private const val FILE_NAME_MAX = 64
    private val ASCII_SAFE = ('a'..'z') + ('A'..'Z') + ('0'..'9') + listOf('.', '-', '_')
}
