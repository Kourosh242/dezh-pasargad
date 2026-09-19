package com.pasargad.dezh.data.update

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.IntentSender
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.core.content.ContextCompat
import java.io.File
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Official PackageInstaller gateway. The system's own confirmation dialog (and
 * Play Protect, when it chooses to scan) is the ONLY gate — this class never
 * attempts a silent install and never interferes with any security decision.
 * Blocked/aborted/failed statuses are surfaced verbatim to the UI.
 */
class PackageApkInstaller(private val context: Context) : ApkInstallGateway {

    private val listeners = mutableListOf<(InstallOutcome) -> Unit>()
    private var receiverRegistered = false

    private val statusReceiver = object : BroadcastReceiver() {
        override fun onReceive(receiverContext: Context?, intent: Intent?) {
            val outcome = parseInstallOutcome(intent?.extras)
            listeners.toList().forEach { listener -> listener(outcome) }
        }
    }

    override fun canRequestInstall(): Boolean = context.packageManager.canRequestPackageInstalls()

    override fun unknownAppsSettingsIntent(): Intent =
        Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES)
            .setData(Uri.parse("package:${context.packageName}"))

    override suspend fun install(apkFiles: List<File>): Unit = withContext(Dispatchers.IO) {
        val files = apkFiles.filter { it.isFile && it.length() > 0 }
        if (files.isEmpty()) throw IOException("Update APK file is missing")
        ensureStatusReceiver()
        val packageInstaller = context.packageManager.packageInstaller
        val sessionId = packageInstaller.createSession(
            PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL),
        )
        packageInstaller.openSession(sessionId).use { session ->
            // Each archive becomes its own session entry: single APK now, base
            // + splits later, without changing the call shape.
            files.forEach { apk ->
                apk.inputStream().use { input ->
                    session.openWrite(apk.name, 0, apk.length()).use { output ->
                        input.copyTo(output, COPY_BUFFER_BYTES)
                        session.fsync(output)
                    }
                }
            }
            session.commit(installStatusIntentSender())
        }
    }

    override fun addInstallListener(listener: (InstallOutcome) -> Unit) {
        listeners.add(listener)
    }

    override fun removeInstallListener(listener: (InstallOutcome) -> Unit) {
        listeners.remove(listener)
    }

    /** Targeted, dynamically registered receiver for the official session status broadcast. */
    private fun ensureStatusReceiver() {
        if (receiverRegistered) return
        ContextCompat.registerReceiver(
            context,
            statusReceiver,
            installStatusFilter(),
            ContextCompat.RECEIVER_NOT_EXPORTED, // only this app + the system installer may deliver
        )
        receiverRegistered = true
    }

    private fun installStatusIntentSender(): IntentSender {
        val completion = Intent(ACTION_INSTALL_COMPLETE).setPackage(context.packageName)
        val mutability = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.FLAG_MUTABLE // the system writes status extras into this PendingIntent
        } else {
            0 // pre-S PendingIntents are mutable by default
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            completion,
            PendingIntent.FLAG_UPDATE_CURRENT or mutability,
        )
        return pendingIntent.intentSender
    }

    internal companion object {
        /** Official action of PackageInstaller session-completion broadcasts. */
        const val ACTION_INSTALL_COMPLETE = "com.android.packageinstaller.ACTION_INSTALL_COMPLETE"

        /**
         * Action-only filter: our status broadcast is action+package with NO
         * data Uri. (Regression guard: a "package" data scheme here never
         * matches, so STATUS_PENDING_USER_ACTION was dropped and the official
         * confirmation dialog never launched.)
         */
        fun installStatusFilter(): IntentFilter = IntentFilter(ACTION_INSTALL_COMPLETE)
        const val COPY_BUFFER_BYTES = 64 * 1024
        const val REQUEST_CODE = 4001

        /** Pure mapping of the official status extras — unit-testable. */
        fun parseInstallOutcome(extras: Bundle?): InstallOutcome {
            if (extras == null) return InstallOutcome.Failed(systemMessage = null)
            return when (extras.getInt(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
                PackageInstaller.STATUS_SUCCESS -> InstallOutcome.Success
                PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                    @Suppress("DEPRECATION")
                    val confirmation: Intent? = extras.getParcelable(Intent.EXTRA_INTENT)
                    confirmation
                        ?.let { InstallOutcome.PendingUserAction(activityIntent = it) }
                        ?: InstallOutcome.Failed(systemMessage = null)
                }
                PackageInstaller.STATUS_FAILURE_ABORTED -> InstallOutcome.Aborted
                PackageInstaller.STATUS_FAILURE_BLOCKED ->
                    InstallOutcome.Blocked(systemMessage = extras.getString(PackageInstaller.EXTRA_STATUS_MESSAGE))
                else -> InstallOutcome.Failed(systemMessage = extras.getString(PackageInstaller.EXTRA_STATUS_MESSAGE))
            }
        }
    }
}
