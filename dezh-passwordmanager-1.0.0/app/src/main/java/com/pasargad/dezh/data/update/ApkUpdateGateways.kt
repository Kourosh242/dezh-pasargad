package com.pasargad.dezh.data.update

import android.content.Intent
import com.pasargad.dezh.domain.update.ReleaseApkAsset
import com.pasargad.dezh.domain.update.Verdict
import java.io.File

/**
 * Gateways for the in-app self-update download/install flow. They live beside
 * the data implementations (project convention: interfaces beside their data
 * layer, like SettingsRepository) so ViewModels stay testable with fakes.
 * (ApkAssetFetcher itself belongs to the domain — UpdateChecker consumes it.)
 */


/** Streams the release APK into app-private storage with progress reporting. */
fun interface ApkDownloadGateway {
    /**
     * @param onProgress 0..100 percent, called from a worker thread.
     * @throws ApkDownloadException on any failure (URL guard, network, digest).
     */
    suspend fun download(asset: ReleaseApkAsset, tag: String, onProgress: (Int) -> Unit): File
}

/** Runs the admission policy against a downloaded APK file. */
fun interface ApkVerificationGateway {
    suspend fun verify(apk: File, latestTag: String): com.pasargad.dezh.domain.update.ApkInspection
}

/**
 * PackageInstaller gateway. Implementation is ALWAYS user-confirmed: it never
 * performs a silent install and never touches Play Protect or any system
 * security decision — blocked/aborted outcomes are surfaced, not fought.
 */
interface ApkInstallGateway {

    /** True when the user has already granted "install unknown apps" for us. */
    fun canRequestInstall(): Boolean

    /** System settings page for THIS app's unknown-sources permission. */
    fun unknownAppsSettingsIntent(): Intent

    /**
     * Streams every archive in [apkFiles] into one fresh PackageInstaller
     * session and commits it. One file = classic self-update; several files =
     * base + split APKs, all written into the SAME session (future-ready).
     * The system confirmation dialog (and any Play Protect screen) is the
     * official Android UI; its outcome arrives through [addInstallListener].
     */
    suspend fun install(apkFiles: List<File>)

    /** Outcome stream: every session status lands here (main thread). */
    fun addInstallListener(listener: (InstallOutcome) -> Unit)
    fun removeInstallListener(listener: (InstallOutcome) -> Unit)
}

/** Result of one PackageInstaller session, mapped from the official extras. */
sealed interface InstallOutcome {
    /** System needs the official confirmation dialog shown — launch [activityIntent]. */
    data class PendingUserAction(val activityIntent: Intent) : InstallOutcome
    data object Success : InstallOutcome
    /** Play Protect or the admin blocked the install — reported, never fought. */
    data class Blocked(val systemMessage: String?) : InstallOutcome
    /** User cancelled the official confirmation dialog. */
    data object Aborted : InstallOutcome
    data class Failed(val systemMessage: String?) : InstallOutcome
}
