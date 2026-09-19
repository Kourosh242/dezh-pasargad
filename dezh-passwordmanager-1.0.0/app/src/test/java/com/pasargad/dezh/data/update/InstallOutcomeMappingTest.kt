package com.pasargad.dezh.data.update

import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Bundle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Mapping of the OFFICIAL PackageInstaller session-status extras to UI
 * outcomes. The system's confirmation dialog is the only gate — a blocked or
 * aborted session must surface as-is, never be retried behind the user's back.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class InstallOutcomeMappingTest {

    private fun extras(status: Int, vararg pairs: Pair<String, Any?>): Bundle {
        val bundle = Bundle()
        bundle.putInt(PackageInstaller.EXTRA_STATUS, status)
        pairs.forEach { (key, value) ->
            when (value) {
                is String -> bundle.putString(key, value)
                is Intent -> bundle.putParcelable(key, value)
            }
        }
        return Bundle(bundle)
    }

    @Test
    fun `success maps to Success`() {
        val outcome = PackageApkInstaller.parseInstallOutcome(
            extras(PackageInstaller.STATUS_SUCCESS),
        )
        assertEquals(InstallOutcome.Success, outcome)
    }

    @Test
    fun `pending user action carries the official confirmation intent`() {
        val confirmation = Intent("android.content.pm.action.CONFIRM_INSTALL")
            .setData(Uri.parse("package:com.pasargad.dezh"))
        val outcome = PackageApkInstaller.parseInstallOutcome(
            extras(
                PackageInstaller.STATUS_PENDING_USER_ACTION,
                Intent.EXTRA_INTENT to confirmation,
            ),
        )
        assertTrue(outcome is InstallOutcome.PendingUserAction)
        assertEquals(
            "com.pasargad.dezh",
            (outcome as InstallOutcome.PendingUserAction).activityIntent.data?.schemeSpecificPart,
        )
    }

    @Test
    fun `pending user action without an intent degrades to Failed`() {
        val outcome = PackageApkInstaller.parseInstallOutcome(
            extras(PackageInstaller.STATUS_PENDING_USER_ACTION),
        )
        assertTrue(outcome is InstallOutcome.Failed)
    }

    @Test
    fun `user abort maps to Aborted`() {
        val outcome = PackageApkInstaller.parseInstallOutcome(
            extras(PackageInstaller.STATUS_FAILURE_ABORTED),
        )
        assertEquals(InstallOutcome.Aborted, outcome)
    }

    @Test
    fun `blocked session keeps the system message and is surfaced - not fought`() {
        val outcome = PackageApkInstaller.parseInstallOutcome(
            extras(
                PackageInstaller.STATUS_FAILURE_BLOCKED,
                PackageInstaller.EXTRA_STATUS_MESSAGE to "Blocked by Play Protect",
            ),
        )
        assertEquals(InstallOutcome.Blocked("Blocked by Play Protect"), outcome)
    }

    @Test
    fun `generic failure keeps the system message`() {
        val outcome = PackageApkInstaller.parseInstallOutcome(
            extras(
                PackageInstaller.STATUS_FAILURE,
                PackageInstaller.EXTRA_STATUS_MESSAGE to "INVALID_APK",
            ),
        )
        assertEquals(InstallOutcome.Failed("INVALID_APK"), outcome)
    }

    @Test
    fun `missing extras map to Failed`() {
        assertEquals(InstallOutcome.Failed(null), PackageApkInstaller.parseInstallOutcome(null))
        assertEquals(
            InstallOutcome.Failed(null),
            PackageApkInstaller.parseInstallOutcome(Bundle()),
        )
    }

    @Test
    fun `official installer constants are the real system actions`() {
        // Pin the intent plumbing: our status action is the package-scoped
        // broadcast the system fires into our own receiver — never a store call.
        assertEquals(
            "com.android.packageinstaller.ACTION_INSTALL_COMPLETE",
            PackageApkInstaller.ACTION_INSTALL_COMPLETE,
        )
    }
}
