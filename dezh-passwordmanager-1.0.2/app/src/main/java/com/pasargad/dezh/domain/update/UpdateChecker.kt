package com.pasargad.dezh.domain.update

import kotlinx.coroutines.CancellationException

/** Latest published release as advertised by the release channel. */
data class ReleaseInfo(
    val latestTag: String,
    val releaseUrl: String,
)

/** Transport for reading the newest published release. Network-free in tests. */
fun interface ReleaseFetcher {
    suspend fun fetchLatest(): ReleaseInfo
}

/** Answers "what version is running right now?" (implementation: PackageManager). */
interface AppVersionProvider {
    val currentVersion: String
}

/** Pure dotted-version ordering: "1.0.1" > "1.0.0"; equal or older -> false. */
object SemanticVersion {

    fun isNewer(current: String, candidate: String): Boolean {
        val mine = parse(current)
        val theirs = parse(candidate)
        val size = maxOf(mine.size, theirs.size)
        for (index in 0 until size) {
            val a = mine.getOrElse(index) { 0 }
            val b = theirs.getOrElse(index) { 0 }
            if (a != b) return b > a
        }
        return false
    }

    private fun parse(version: String): List<Int> = version
        .trim()
        .removePrefix("v")
        .removePrefix("V")
        .split('.')
        .map { part -> part.filter(Char::isDigit).ifEmpty { "0" }.toInt() }
}

/** Outcome of one update check. */
sealed interface UpdateCheckResult {
    data class UpToDate(val currentVersion: String) : UpdateCheckResult

    /** [asset] is present only when the channel also exposes an [ApkAssetFetcher]. */
    data class UpdateAvailable(
        val latestVersion: String,
        val releaseUrl: String,
        val asset: ReleaseApkAsset? = null,
    ) : UpdateCheckResult
    data class Failed(val message: String) : UpdateCheckResult
}

/**
 * Compares the running version against the newest published release tag.
 * Every [check] performs a fresh fetch — no caching, per product decision:
 * the user's press must always reflect the channel's current state.
 */
class UpdateChecker(
    private val versionProvider: AppVersionProvider,
    private val releaseFetcher: ReleaseFetcher,
    private val assetFetcher: ApkAssetFetcher? = null,
) {
    val currentVersion: String get() = versionProvider.currentVersion

    // A deliberately broad catch: the check is best-effort and must map ANY
    // channel failure (I/O, parsing, unexpected runtime) to a user-facing
    // "failed" state instead of crashing the vault.
    @Suppress("TooGenericExceptionCaught")
    suspend fun check(): UpdateCheckResult {
        val current = versionProvider.currentVersion
        return try {
            val release: ReleaseInfo
            val asset: ReleaseApkAsset?
            if (assetFetcher != null) {
                val (fetchedRelease, fetchedAsset) = assetFetcher.fetchLatestWithAsset()
                release = fetchedRelease
                asset = fetchedAsset
            } else {
                release = releaseFetcher.fetchLatest()
                asset = null
            }
            if (SemanticVersion.isNewer(current, release.latestTag)) {
                UpdateCheckResult.UpdateAvailable(release.latestTag, release.releaseUrl, asset)
            } else {
                UpdateCheckResult.UpToDate(current)
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            UpdateCheckResult.Failed(failure.message ?: FALLBACK_MESSAGE)
        }
    }

    private companion object {
        const val FALLBACK_MESSAGE = "update check failed"
    }
}
