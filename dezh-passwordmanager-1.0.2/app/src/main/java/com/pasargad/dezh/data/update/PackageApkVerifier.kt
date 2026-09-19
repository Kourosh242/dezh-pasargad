package com.pasargad.dezh.data.update

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import com.pasargad.dezh.domain.update.ApkArchiveInfo
import com.pasargad.dezh.domain.update.ApkInspection
import com.pasargad.dezh.domain.update.CurrentAppInfo
import com.pasargad.dezh.domain.update.SelfUpdatePolicy
import com.pasargad.dezh.domain.update.Verdict
import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Reads archive facts from an APK file. Abstraction keeps the policy testable. */
fun interface ApkArchiveReader {
    fun read(apk: File): ApkArchiveInfo?
}

/**
 * PackageManager-backed archive reader: package name + longVersionCode +
 * SHA-256 of every signing certificate (API 28+ SigningInfo — minSdk 29 makes
 * the deprecated signatures API unreachable by design).
 */
class PackageManagerArchiveReader(private val context: Context) : ApkArchiveReader {

    override fun read(apk: File): ApkArchiveInfo? = when {
        !apk.isFile || apk.length() <= 0 -> null
        else -> readArchive(apk)
    }

    private fun readArchive(apk: File): ApkArchiveInfo? {
        val info = context.packageManager.getPackageArchiveInfo(
            apk.absolutePath,
            PackageManager.GET_SIGNING_CERTIFICATES,
        )?.takeIf { archive -> !archive.packageName.isNullOrEmpty() } ?: return null
        return ApkArchiveInfo(
            packageName = info.packageName,
            versionCode = info.longVersionCode,
            certSha256Hex = info.signingInfo
                ?.apkContentsSigners
                ?.map { signer -> sha256Hex(signer.toByteArray()) }
                .orEmpty(),
        )
    }

    internal companion object {
        fun sha256Hex(bytes: ByteArray): String =
            MessageDigest.getInstance("SHA-256").digest(bytes)
                .joinToString(separator = "") { "%02x".format(it) }
    }
}

/**
 * Runs [com.pasargad.dezh.domain.update.SelfUpdatePolicy] against the running
 * installation. Everything is checked BEFORE any installer session exists.
 */
class PackageApkVerifier(
    context: Context,
    private val archiveReader: ApkArchiveReader = PackageManagerArchiveReader(context),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.Default,
) : ApkVerificationGateway {

    private val currentAppInfo: CurrentAppInfo = readCurrentApp(context.packageManager, context.packageName)

    override suspend fun verify(apk: File, @Suppress("UNUSED_PARAMETER") latestTag: String): ApkInspection =
        withContext(ioDispatcher) {
            val archive = archiveReader.read(apk)
            ApkInspection(
                verdict = SelfUpdatePolicy.decide(archive, currentAppInfo),
                archive = archive,
            )
        }

    internal companion object {
        fun readCurrentApp(packageManager: PackageManager, packageName: String): CurrentAppInfo {
            val info: PackageInfo = packageManager.getPackageInfo(
                packageName,
                PackageManager.GET_SIGNING_CERTIFICATES,
            )
            val certs = info.signingInfo
                ?.apkContentsSigners
                ?.map { signer -> PackageManagerArchiveReader.sha256Hex(signer.toByteArray()) }
                .orEmpty()
            return CurrentAppInfo(
                packageName = packageName,
                versionCode = info.longVersionCode,
                certSha256Hex = certs,
            )
        }
    }
}
