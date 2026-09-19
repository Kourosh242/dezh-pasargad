package com.pasargad.dezh.presentation.update

import android.content.Intent
import android.net.Uri
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Update screen end-to-end (fake channel + fake gateways): version display,
 * check result states, APK info card, and the official install press. The old
 * browser hand-off remains only as the secondary release-page link.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "fa-rIR-w411dp-h891dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class UpdateScreenUiTest {

    @get:Rule
    val composeRule = createComposeRule()

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

    private class FakeVersion(override val currentVersion: String) : AppVersionProvider

    private class FakeFetcher(private val tag: String) : ReleaseFetcher {
        override suspend fun fetchLatest(): ReleaseInfo =
            ReleaseInfo(tag, "https://github.com/Kourosh242/dezh-pasargad/releases/tag/$tag")
    }

    private class FakeAssetFetcher(private val tag: String) :
        com.pasargad.dezh.domain.update.ApkAssetFetcher {
        override suspend fun fetchLatestWithAsset(): Pair<ReleaseInfo, ReleaseApkAsset?> =
            ReleaseInfo(tag, "https://github.com/Kourosh242/dezh-pasargad/releases/tag/$tag") to
                ReleaseApkAsset(
                    name = OFFICIAL_APK,
                    url = "https://github.com/Kourosh242/dezh-pasargad/releases/download/$tag/$OFFICIAL_APK",
                    sizeBytes = 3_939_667,
                    sha256Hex = null,
                )
    }

    private class FakeDownloader(private val apk: File?) : ApkDownloadGateway {
        override suspend fun download(asset: ReleaseApkAsset, tag: String, onProgress: (Int) -> Unit): File {
            onProgress(100)
            return apk ?: error("no apk")
        }
    }

    private class FakeVerifier : ApkVerificationGateway {
        var verifiedTag: String? = null
            private set

        override suspend fun verify(apk: File, latestTag: String): ApkInspection {
            verifiedTag = latestTag
            return ApkInspection(
                verdict = Verdict.Valid,
                archive = ApkArchiveInfo("com.pasargad.dezh", versionCode = 2, certSha256Hex = listOf("k")),
            )
        }
    }

    private class RecordingInstaller : ApkInstallGateway {
        val installed = mutableListOf<List<File>>()
        override fun canRequestInstall(): Boolean = true
        override fun unknownAppsSettingsIntent(): Intent =
            Intent("android.settings.MANAGE_UNKNOWN_APP_SOURCES").setData(Uri.parse("package:com.pasargad.dezh"))

        override suspend fun install(apkFiles: List<File>) {
            installed.add(apkFiles)
        }

        override fun addInstallListener(listener: (InstallOutcome) -> Unit) = Unit
        override fun removeInstallListener(listener: (InstallOutcome) -> Unit) = Unit
    }

    private fun makeApk(): File {
        val apk = File(tmp.newFolder(), OFFICIAL_APK)
        apk.writeBytes(ByteArray(64) { it.toByte() })
        return apk
    }

    private fun setContent(viewModel: UpdateViewModel): MutableMap<String, String> {
        val releasePage = mutableMapOf<String, String>()
        composeRule.setContent {
            UpdateScreen(
                viewModel = viewModel,
                onOpenReleasePage = { url -> releasePage[url] = url },
                onBack = {},
            )
        }
        return releasePage
    }

    private fun updateViewModel(tag: String, apk: File?, installer: RecordingInstaller): UpdateViewModel =
        UpdateViewModel(
            checker = UpdateChecker(
                versionProvider = FakeVersion("1.0.0"),
                releaseFetcher = FakeFetcher(tag),
                assetFetcher = FakeAssetFetcher(tag),
            ),
            downloader = FakeDownloader(apk),
            verifier = FakeVerifier(),
            installer = installer,
        )

    @Test
    fun update_available_flow_shows_apk_info_then_runs_official_install() {
        val apk = makeApk()
        val installer = RecordingInstaller()
        val viewModel = updateViewModel("1.0.1", apk, installer)
        val releasePage = setContent(viewModel)

        composeRule.onNodeWithText("نسخهٔ فعلی: 1.0.0").assertIsDisplayed()
        composeRule.onNodeWithText("بررسی کن").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            viewModel.uiState.value.status is UpdateStatus.Available
        }
        // Secondary link opens the GitHub release page (never a store).
        composeRule.onNodeWithText("مشاهدهٔ صفحهٔ انتشار").performClick()
        assertTrue(releasePage.keys.single().endsWith("/releases/tag/1.0.1"))

        // In-app CTA replaces the old browser hand-off.
        composeRule.onNodeWithText("دانلود و نصب در همین اپ").assertIsDisplayed().performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            viewModel.uiState.value.installStep is InstallStep.ReadyToInstall
        }
        // APK facts card + admission confirmation are visible before any install.
        composeRule.onNodeWithText("اطلاعات فایل نصبی").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("بسته: com.pasargad.dezh").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("نصب").performScrollTo().assertIsDisplayed().performClick()
        assertEquals(listOf(listOf(apk)), installer.installed)
        composeRule.waitUntil(timeoutMillis = 5_000) {
            viewModel.uiState.value.installStep is InstallStep.Installing
        }
    }

    @Test
    fun same_version_shows_up_to_date_confirmation() {
        val viewModel = updateViewModel("1.0.0", apk = null, installer = RecordingInstaller())
        setContent(viewModel)
        composeRule.onNodeWithText("بررسی کن").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            viewModel.uiState.value.status is UpdateStatus.UpToDate
        }
        composeRule.onNodeWithText("شما جدیدترین نسخه را دارید").assertIsDisplayed()
    }

    @Test
    fun format_bytes_renders_human_units() {
        assertEquals("3.8 MB", formatBytes(3_939_667))
        assertEquals("512 KB", formatBytes(524_288))
        assertEquals("999 B", formatBytes(999))
    }

    companion object {
        const val OFFICIAL_APK = "dezh-pasargad-release.apk"
    }
}
