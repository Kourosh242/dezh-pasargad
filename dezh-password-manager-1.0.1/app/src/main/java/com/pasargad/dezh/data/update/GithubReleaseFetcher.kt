package com.pasargad.dezh.data.update

import com.pasargad.dezh.domain.update.ReleaseFetcher
import com.pasargad.dezh.domain.update.ReleaseInfo
import com.pasargad.dezh.domain.update.ApkAssetFetcher
import com.pasargad.dezh.domain.update.ReleaseApkAsset
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONException
import org.json.JSONObject

/**
 * Reads the newest release tag from the public GitHub Releases REST endpoint
 * of the project. HTTPS only, bounded timeouts, no caching — each call is live.
 * Uses the platform HttpURLConnection + org.json so the app gains no new
 * third-party dependency for the update check.
 */
class GithubReleaseFetcher(
    private val endpoint: String = DEFAULT_ENDPOINT,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ReleaseFetcher,
    ApkAssetFetcher {

    override suspend fun fetchLatest(): ReleaseInfo = withContext(ioDispatcher) {
        val connection = URL(endpoint).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = CONNECT_TIMEOUT_MS
            connection.readTimeout = READ_TIMEOUT_MS
            connection.requestMethod = "GET"
            connection.setRequestProperty("Accept", ACCEPT_GITHUB_JSON)
            connection.setRequestProperty("User-Agent", USER_AGENT)
            parse(readBody(connection))
        } finally {
            connection.disconnect()
        }
    }

    override suspend fun fetchLatestWithAsset(): Pair<ReleaseInfo, ReleaseApkAsset?> =
        withContext(ioDispatcher) {
            val connection = URL(endpoint).openConnection() as HttpURLConnection
            try {
                connection.connectTimeout = CONNECT_TIMEOUT_MS
                connection.readTimeout = READ_TIMEOUT_MS
                connection.requestMethod = "GET"
                connection.setRequestProperty("Accept", ACCEPT_GITHUB_JSON)
                connection.setRequestProperty("User-Agent", USER_AGENT)
                val body = readBody(connection)
                parse(body) to parseApkAsset(body)
            } finally {
                connection.disconnect()
            }
        }

    private fun readBody(connection: HttpURLConnection): String {
        val successful = connection.responseCode in HTTP_OK..HTTP_OK_MAX
        val source = if (successful) {
            connection.inputStream
        } else {
            connection.errorStream ?: throw IOException("HTTP ${connection.responseCode}")
        }
        return source.bufferedReader().use { reader -> reader.readText() }
    }

    internal companion object {
        const val DEFAULT_ENDPOINT = "https://api.github.com/repos/Kourosh242/dezh-pasargad/releases/latest"
        private const val FALLBACK_RELEASE_PAGE = "https://github.com/Kourosh242/dezh-pasargad/releases/latest"
        private const val CONNECT_TIMEOUT_MS = 10_000
        private const val READ_TIMEOUT_MS = 10_000
        private const val ACCEPT_GITHUB_JSON = "application/vnd.github+json"
        private const val USER_AGENT = "dezh-pasargad-update-check"
        private const val HTTP_OK = 200
        private const val HTTP_OK_MAX = 299
        internal const val RELEASE_APK_NAME = "dezh-pasargad-release.apk"
        private const val APK_SUFFIX = ".apk"
        private const val DIGEST_PREFIX = "sha256:"

        /** Tolerant reader: tag_name is mandatory, html_url falls back to the releases page. */
        fun parse(body: String): ReleaseInfo {
            val json = try {
                JSONObject(body)
            } catch (broken: JSONException) {
                throw IOException("Malformed release payload", broken)
            }
            val tag = json.optString("tag_name").trim()
            if (tag.isEmpty()) throw IOException("Release payload without tag_name")
            val url = json.optString("html_url").trim().ifEmpty { FALLBACK_RELEASE_PAGE }
            return ReleaseInfo(tag, url)
        }

        /**
         * Finds the installable .apk asset of the release. Prefers the official
         * release filename; falls back to the first asset ending in .apk.
         * digest is optional ("sha256:<hex>" when the channel publishes one).
         */
        fun parseApkAsset(body: String): ReleaseApkAsset? {
            val json = try {
                JSONObject(body)
            } catch (broken: JSONException) {
                throw IOException("Malformed release payload", broken)
            }
            val assets = json.optJSONArray("assets") ?: return null
            val apks = (0 until assets.length())
                .mapNotNull { index -> toApkAsset(assets.optJSONObject(index)) }
            return apks.firstOrNull { asset -> asset.name.equals(RELEASE_APK_NAME, ignoreCase = true) }
                ?: apks.firstOrNull()
        }

        private fun toApkAsset(asset: JSONObject?): ReleaseApkAsset? {
            val name = asset?.optString("name")?.trim().orEmpty()
            val url = asset?.optString("browser_download_url")?.trim().orEmpty()
            if (!name.endsWith(APK_SUFFIX, ignoreCase = true) || url.isEmpty()) return null
            return ReleaseApkAsset(
                name = name,
                url = url,
                sizeBytes = asset?.optLong("size") ?: 0,
                sha256Hex = asset?.optString("digest")?.trim()
                    ?.takeIf { it.startsWith(DIGEST_PREFIX) }
                    ?.substringAfter(DIGEST_PREFIX)
                    ?.lowercase(),
            )
        }

    }
}
