package com.pasargad.dezh.domain.update

/**
 * The installable APK asset advertised by the release channel.
 * [sha256Hex] is optional (present only when the channel publishes a digest).
 */
data class ReleaseApkAsset(
    val name: String,
    val url: String,
    val sizeBytes: Long,
    val sha256Hex: String?,
)

/** Archive facts extracted from a downloaded APK file. */
data class ApkArchiveInfo(
    val packageName: String,
    val versionCode: Long,
    /** SHA-256 of every signing certificate carried by the archive (hex, lowercase). */
    val certSha256Hex: List<String>,
)

/** Channel that exposes the latest release together with its installable APK. */
interface ApkAssetFetcher {
    /** Asset is null when the release carries no installable APK. @throws IOException on channel failure. */
    suspend fun fetchLatestWithAsset(): Pair<ReleaseInfo, ReleaseApkAsset?>
}

/** Result of inspecting one downloaded APK: admission verdict plus parsed facts. */
data class ApkInspection(
    val verdict: Verdict,
    val archive: ApkArchiveInfo? = null,
)

/** Facts about the running app, used as the baseline for a self-update. */
data class CurrentAppInfo(
    val packageName: String,
    val versionCode: Long,
    /** SHA-256 of the certificates the running app is signed with (hex, lowercase). */
    val certSha256Hex: List<String>,
)

/**
 * Self-update admission decision: an update APK may only proceed when it is a
 * readable archive of THIS package, strictly newer than the running build, and
 * signed with the SAME key. Anything else is refused before the installer runs.
 */
object SelfUpdatePolicy {

    fun decide(archive: ApkArchiveInfo?, current: CurrentAppInfo): Verdict = when {
        archive == null -> Verdict.Unreadable
        archive.packageName != current.packageName ->
            Verdict.WrongPackage(expected = current.packageName, actual = archive.packageName)
        archive.versionCode <= current.versionCode ->
            Verdict.NotNewer(currentVersionCode = current.versionCode, candidateVersionCode = archive.versionCode)
        archive.certSha256Hex.none { it in current.certSha256Hex } -> Verdict.SignatureMismatch
        else -> Verdict.Valid
    }
}

/** Why a downloaded update APK was refused. */
sealed interface Verdict {
    data object Valid : Verdict
    data object Unreadable : Verdict
    data class WrongPackage(val expected: String, val actual: String) : Verdict
    data class NotNewer(val currentVersionCode: Long, val candidateVersionCode: Long) : Verdict
    data object SignatureMismatch : Verdict
}
