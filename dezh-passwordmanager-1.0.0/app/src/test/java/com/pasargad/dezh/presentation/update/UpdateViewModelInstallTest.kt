package com.pasargad.dezh.presentation.update

import android.content.Intent
import android.net.Uri
import com.pasargad.dezh.data.update.ApkDownloadException
import com.pasargad.dezh.data.update.ApkDownloadGateway
import com.pasargad.dezh.data.update.ApkInstallGateway
import com.pasargad.dezh.data.update.ApkVerificationGateway
import com.pasargad.dezh.data.update.InstallOutcome
import com.pasargad.dezh.domain.update.ApkArchiveInfo
import com.pasargad.dezh.domain.update.ApkInspection
import com.pasargad.dezh.domain.update.AppVersionProvider
import com.pasargad.dezh.domain.update.ReleaseApkAsset
import com.pasargad.dezh.domain.update.ReleaseFetcher
import com.pasargad.dezh.domain.update.ReleaseInfo
import com.pasargad.dezh.domain.update.UpdateChecker
import com.pasargad.dezh.domain.update.Verdict
import java.io.File
import com.pasargad.dezh.domain.update.ApkAssetFetcher
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * In-app self-update pipeline (official PackageInstaller, user-approved):
 * checks EVERY branch of download → admission policy → single permission gate
 * → installer session → outcome surfacing. Pins the product bans: no store
 * call anywhere, no silent install, blocked/aborted outcomes surfaced as-is.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class UpdateViewModelInstallTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val mainDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(mainDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // --- fakes -----------------------------------------------------------------

    private class FakeDownloader(
        private val apk: File?,
        private val failure: Exception? = null,
        private val pauseAtPercent: Int? = null,
    ) : ApkDownloadGateway {
        var receivedAsset: ReleaseApkAsset? = null
        val pauseGate = CompletableDeferred<Unit>()

        override suspend fun download(
            asset: ReleaseApkAsset,
            tag: String,
            onProgress: (Int) -> Unit,
        ): File {
            receivedAsset = asset
            failure?.let { throw it }
            if (pauseAtPercent != null) {
                onProgress(pauseAtPercent)
                pauseGate.await()
            }
            onProgress(DONE_PERCENT)
            return apk ?: error("no apk configured for this test")
        }
    }

    private class FakeVerifier(private val inspection: ApkInspection) : ApkVerificationGateway {
        var verifiedFile: File? = null
        override suspend fun verify(apk: File, latestTag: String): ApkInspection {
            verifiedFile = apk
            return inspection
        }
    }

    private class FakeInstaller(
        private val outcomeToEmit: InstallOutcome? = null,
    ) : ApkInstallGateway {
        var canInstall = true
        val installedFiles = mutableListOf<List<File>>()
        val effects = mutableListOf<InstallOutcome>()
        private val listeners = mutableListOf<(InstallOutcome) -> Unit>()

        override fun canRequestInstall(): Boolean = canInstall
        override fun unknownAppsSettingsIntent(): Intent =
            Intent(ACTION_MANAGE_UNKNOWN_APP_SOURCES).setData(Uri.parse("package:com.pasargad.dezh"))

        override suspend fun install(apkFiles: List<File>) {
            installedFiles.add(apkFiles)
            outcomeToEmit?.let { outcome ->
                listeners.toList().forEach { listener -> listener(outcome) }
            }
        }

        override fun addInstallListener(listener: (InstallOutcome) -> Unit) {
            listeners.add(listener)
        }

        override fun removeInstallListener(listener: (InstallOutcome) -> Unit) {
            listeners.remove(listener)
        }

        /** Simulates the async system broadcast after commit returned. */
        fun emit(outcome: InstallOutcome) {
            effects.add(outcome)
            listeners.toList().forEach { listener -> listener(outcome) }
        }

        companion object {
            const val ACTION_MANAGE_UNKNOWN_APP_SOURCES = "android.settings.MANAGE_UNKNOWN_APP_SOURCES"
        }
    }

    private class FakeChannel(private val tag: String, private val withAsset: Boolean = true) {
        val asset = ReleaseApkAsset(
            name = OFFICIAL_APK_NAME,
            url = "https://github.com/Kourosh242/dezh-pasargad/releases/download/$tag/$OFFICIAL_APK_NAME",
            sizeBytes = 3_939_667,
            sha256Hex = null,
        )

        val checker = UpdateChecker(
            versionProvider = object : AppVersionProvider {
                override val currentVersion: String = "1.0.0"
            },
            releaseFetcher = ReleaseFetcher {
                ReleaseInfo(tag, "https://github.com/Kourosh242/dezh-pasargad/releases/tag/$tag")
            },
            assetFetcher = ApkAssetFetcherFake(),
        )

        private inner class ApkAssetFetcherFake : ApkAssetFetcher {
            override suspend fun fetchLatestWithAsset(): Pair<ReleaseInfo, ReleaseApkAsset?> =
                ReleaseInfo(tag, "https://github.com/Kourosh242/dezh-pasargad/releases/tag/$tag") to
                    asset.takeIf { withAsset }
        }
    }

    private fun validInspection() = ApkInspection(
        verdict = Verdict.Valid,
        archive = ApkArchiveInfo(
            packageName = "com.pasargad.dezh",
            versionCode = 2,
            certSha256Hex = listOf("sha-of-release-key"),
        ),
    )

    private fun makeApk(): File {
        val apk = File(tmp.newFolder(), "dezh-pasargad-release.apk")
        apk.writeBytes(ByteArray(128) { it.toByte() })
        return apk
    }

    private fun viewModelFor(
        channel: FakeChannel,
        downloader: FakeDownloader,
        verifier: FakeVerifier,
        installer: FakeInstaller,
    ): UpdateViewModel = UpdateViewModel(
        checker = channel.checker,
        downloader = downloader,
        verifier = verifier,
        installer = installer,
    )

    /** Drives the pipeline up to ReadyToInstall and returns the VM. */
    private suspend fun reachReady(
        downloader: FakeDownloader,
        verifier: FakeVerifier,
        installer: FakeInstaller = FakeInstaller(),
        channelTag: String = "1.0.1",
    ): UpdateViewModel {
        val channel = FakeChannel(channelTag)
        val viewModel = viewModelFor(channel, downloader, verifier, installer)
        viewModel.check()
        assertEquals(UpdateStatus.Available::class, viewModel.uiState.value.status::class)
        viewModel.downloadUpdate()
        return viewModel
    }

    // --- (a) happy path: check → download → info → official install ------------

    @Test
    fun `available update downloads and shows apk facts then installs via session`() = runTest {
        val apk = makeApk()
        val verifier = FakeVerifier(validInspection())
        val installer = FakeInstaller()
        val effects = mutableListOf<UpdateSideEffect>()
        val downloader = FakeDownloader(apk)

        val viewModel = reachReady(downloader, verifier, installer)
        val ready = viewModel.uiState.value.installStep as InstallStep.ReadyToInstall
        assertEquals("dezh-pasargad-release.apk", ready.summary.fileName)
        assertEquals("com.pasargad.dezh", ready.summary.packageName)
        assertEquals(2L, ready.summary.versionCode)
        assertEquals(apk.length(), ready.summary.sizeBytes)
        assertEquals("1.0.1", ready.summary.latestTag)

        backgroundScope.launch(mainDispatcher) {
            viewModel.sideEffects.collect { effects.add(it) }
        }

        viewModel.proceedToInstall()
        assertEquals(listOf(listOf(apk)), installer.installedFiles)
        assertEquals(InstallStep.Installing, viewModel.uiState.value.installStep)

        // The system answers with the official confirmation dialog intent.
        installer.emit(
            InstallOutcome.PendingUserAction(
                Intent("android.content.pm.action.CONFIRM_INSTALL").setData(Uri.parse("package:com.pasargad.dezh")),
            ),
        )
        assertEquals(InstallStep.WaitingSystemConfirmation, viewModel.uiState.value.installStep)
        val effect = effects.filterIsInstance<UpdateSideEffect.LaunchInstallConfirmation>().single()
        assertEquals("com.pasargad.dezh", effect.intent.data?.schemeSpecificPart)

        installer.emit(InstallOutcome.Success)
        assertEquals(InstallStep.InstallSucceeded, viewModel.uiState.value.installStep)
    }

    @Test
    fun `download reports live progress before finishing`() = runTest {
        val apk = makeApk()
        val downloader = FakeDownloader(apk, pauseAtPercent = PERCENT_MIDWAY)
        val verifier = FakeVerifier(validInspection())
        val channel = FakeChannel("1.0.1")
        val viewModel = viewModelFor(channel, downloader, verifier, FakeInstaller())

        viewModel.check()
        viewModel.downloadUpdate()
        // download is parked inside the fake at 37% — the UI state must show it.
        val step = viewModel.uiState.value.installStep as InstallStep.DownloadingApk
        assertEquals(PERCENT_MIDWAY, step.percent)

        downloader.pauseGate.complete(Unit)
        assertTrue(viewModel.uiState.value.installStep is InstallStep.ReadyToInstall)
    }

    // --- (b) admission refusals BEFORE any installer session --------------------

    @Test
    fun `foreign package is refused before the installer runs`() = runTest {
        val apk = makeApk()
        val verifier = FakeVerifier(
            ApkInspection(Verdict.WrongPackage("com.pasargad.dezh", "com.evil.clone")),
        )
        val installer = FakeInstaller()
        val viewModel = reachReady(FakeDownloader(apk), verifier, installer)
        val refused = viewModel.uiState.value.installStep as InstallStep.InstallRefused
        assertEquals(Verdict.WrongPackage("com.pasargad.dezh", "com.evil.clone"), refused.reason)
        assertTrue(installer.installedFiles.isEmpty())
        assertTrue(!apk.exists()) // refused archive is deleted, never kept around
    }

    @Test
    fun `not-newer archive is refused`() = runTest {
        val apk = makeApk()
        val verifier = FakeVerifier(
            ApkInspection(Verdict.NotNewer(currentVersionCode = 1, candidateVersionCode = 1)),
        )
        val viewModel = reachReady(FakeDownloader(apk), verifier, FakeInstaller())
        val refused = viewModel.uiState.value.installStep as InstallStep.InstallRefused
        assertTrue(refused.reason is Verdict.NotNewer)
    }

    @Test
    fun `signature mismatch is refused`() = runTest {
        val apk = makeApk()
        val verifier = FakeVerifier(ApkInspection(Verdict.SignatureMismatch))
        val viewModel = reachReady(FakeDownloader(apk), verifier, FakeInstaller())
        val refused = viewModel.uiState.value.installStep as InstallStep.InstallRefused
        assertEquals(Verdict.SignatureMismatch, refused.reason)
    }

    // --- (c) invalid APK: unreadable archive and broken downloads ---------------

    @Test
    fun `unreadable apk is refused as unreadable`() = runTest {
        val apk = makeApk()
        val verifier = FakeVerifier(ApkInspection(Verdict.Unreadable, archive = null))
        val viewModel = reachReady(FakeDownloader(apk), verifier, FakeInstaller())
        assertEquals(InstallStep.InstallRefused(Verdict.Unreadable), viewModel.uiState.value.installStep)
    }

    @Test
    fun `failed download surfaces the reason and keeps no state`() = runTest {
        val verifier = FakeVerifier(validInspection())
        val downloader = FakeDownloader(apk = null, failure = ApkDownloadException("digest mismatch"))
        val viewModel = reachReady(downloader, verifier, FakeInstaller())
        val failed = viewModel.uiState.value.installStep as InstallStep.InstallFailed
        assertEquals("digest mismatch", failed.systemMessage)
    }

    // --- (d) install-permission gate: exactly one settings round trip -----------

    @Test
    fun `missing permission opens THIS app's settings once and resumes install after grant`() = runTest {
        val apk = makeApk()
        val installer = FakeInstaller()
        val effects = mutableListOf<UpdateSideEffect>()
        val viewModel = reachReady(FakeDownloader(apk), FakeVerifier(validInspection()), installer)

        backgroundScope.launch(mainDispatcher) {
            viewModel.sideEffects.collect { effects.add(it) }
        }

        installer.canInstall = false
        viewModel.proceedToInstall()
        assertTrue(viewModel.uiState.value.installStep is InstallStep.NeedInstallPermission)
        val requested = effects.filterIsInstance<UpdateSideEffect.RequestInstallPermission>().single()
        assertEquals("android.settings.MANAGE_UNKNOWN_APP_SOURCES", requested.intent.action)
        assertEquals("com.pasargad.dezh", requested.intent.data?.schemeSpecificPart)

        // User flips the toggle in the system page and returns.
        installer.canInstall = true
        viewModel.onHostResumed()
        assertEquals(InstallStep.Installing, viewModel.uiState.value.installStep)
        assertEquals(1, installer.installedFiles.size)
    }

    @Test
    fun `returning without the grant fails - no second settings ask`() = runTest {
        val apk = makeApk()
        val installer = FakeInstaller()
        val effects = mutableListOf<UpdateSideEffect>()
        val viewModel = reachReady(FakeDownloader(apk), FakeVerifier(validInspection()), installer)

        backgroundScope.launch(mainDispatcher) {
            viewModel.sideEffects.collect { effects.add(it) }
        }

        installer.canInstall = false
        viewModel.proceedToInstall()
        viewModel.onHostResumed() // still not granted
        val failed = viewModel.uiState.value.installStep as InstallStep.InstallFailed
        assertNotNull(failed)
        assertEquals(1, effects.filterIsInstance<UpdateSideEffect.RequestInstallPermission>().size)

        // Pressing install again must NOT open settings a second time.
        viewModel.proceedToInstall()
        assertEquals(1, effects.filterIsInstance<UpdateSideEffect.RequestInstallPermission>().size)
        assertTrue(installer.installedFiles.isEmpty())
    }

    // --- (e) no store involvement anywhere in the flow ---------------------------

    @Test
    fun `every launched intent targets the system - never a store`() = runTest {
        val apk = makeApk()
        val installer = FakeInstaller()
        val effects = mutableListOf<UpdateSideEffect>()
        val viewModel = reachReady(FakeDownloader(apk), FakeVerifier(validInspection()), installer)

        backgroundScope.launch(mainDispatcher) {
            viewModel.sideEffects.collect { effects.add(it) }
        }

        installer.canInstall = false
        viewModel.proceedToInstall()
        installer.canInstall = true
        viewModel.onHostResumed()
        installer.emit(
            InstallOutcome.PendingUserAction(
                Intent("android.content.pm.action.CONFIRM_INSTALL").setData(Uri.parse("package:com.pasargad.dezh")),
            ),
        )

        val intents = effects.mapNotNull { effect ->
            when (effect) {
                is UpdateSideEffect.RequestInstallPermission -> effect.intent
                is UpdateSideEffect.LaunchInstallConfirmation -> effect.intent
            }
        }
        assertEquals(2, intents.size)
        intents.forEach { intent ->
            assertEquals("package:com.pasargad.dezh", intent.data.toString())
            val target = (intent.action ?: "") + " " + (intent.`package` ?: "")
            assertTrue(!target.contains("market"))
            assertTrue(!target.contains("play.google"))
            assertTrue(!target.contains("android.intent.action.VIEW"))
        }
    }

    // --- (f) Play Protect block: surfaced verbatim, never fought or retried ------

    @Test
    fun `blocked session surfaces the system message and performs no retry`() = runTest {
        val apk = makeApk()
        val installer = FakeInstaller()
        val effects = mutableListOf<UpdateSideEffect>()
        val viewModel = reachReady(FakeDownloader(apk), FakeVerifier(validInspection()), installer)

        backgroundScope.launch(mainDispatcher) {
            viewModel.sideEffects.collect { effects.add(it) }
        }

        viewModel.proceedToInstall()
        installer.emit(
            InstallOutcome.Blocked(systemMessage = "Blocked by Play Protect"),
        )
        val blocked = viewModel.uiState.value.installStep as InstallStep.InstallBlocked
        assertEquals("Blocked by Play Protect", blocked.systemMessage)
        assertEquals(1, installer.installedFiles.size) // no silent second attempt
        assertTrue(effects.filterIsInstance<UpdateSideEffect.LaunchInstallConfirmation>().isEmpty())
    }

    @Test
    fun `user abort keeps the apk retryable without re-downloading`() = runTest {
        val apk = makeApk()
        val installer = FakeInstaller()
        val viewModel = reachReady(FakeDownloader(apk), FakeVerifier(validInspection()), installer)

        viewModel.proceedToInstall()
        assertEquals(1, installer.installedFiles.size)
        installer.emit(InstallOutcome.Aborted)

        val aborted = viewModel.uiState.value.installStep as InstallStep.InstallAborted
        assertEquals("dezh-pasargad-release.apk", aborted.summary.fileName)
        assertTrue(apk.exists())

        // Retry uses the SAME file — no second download happened.
        viewModel.proceedToInstall()
        assertEquals(2, installer.installedFiles.size)
    }

    @Test
    fun `update without any apk asset on the channel maps to a failed status`() = runTest {
        val channel = FakeChannel("1.0.1", withAsset = false)
        val viewModel = viewModelFor(
            channel,
            FakeDownloader(apk = null),
            FakeVerifier(validInspection()),
            FakeInstaller(),
        )
        viewModel.check()
        val failed = viewModel.uiState.value.status as UpdateStatus.Failed
        assertEquals("release has no installable apk asset", failed.message)
    }

    private companion object {
        const val DONE_PERCENT = 100
        const val PERCENT_MIDWAY = 37
        const val OFFICIAL_APK_NAME = "dezh-pasargad-release.apk"
    }
}
