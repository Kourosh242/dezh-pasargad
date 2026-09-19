package com.pasargad.dezh.domain.update

import java.io.IOException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Update-check with the asset-aware channel: an available update must also
 * carry the installable APK asset (the whole point of the in-app installer),
 * and channel failures must degrade to Failed — never crash the vault.
 */
class UpdateCheckerAssetTest {

    private class FakeVersion(override val currentVersion: String) : AppVersionProvider

    private class FakeFetcher(private val tag: String) : ReleaseFetcher {
        override suspend fun fetchLatest(): ReleaseInfo = ReleaseInfo(tag, "https://github.com/x/releases/tag/$tag")
    }

    private class FakeAssetFetcher(
        private val tag: String,
        private val asset: ReleaseApkAsset? =
            ReleaseApkAsset("dezh-pasargad-release.apk", "https://github.com/x/dezh.apk", 4_000_000, null),
        private val failure: IOException? = null,
    ) : ApkAssetFetcher {
        override suspend fun fetchLatestWithAsset(): Pair<ReleaseInfo, ReleaseApkAsset?> {
            failure?.let { throw it }
            return ReleaseInfo(tag, "https://github.com/x/releases/tag/$tag") to asset
        }
    }

    @Test
    fun `available update carries the apk asset`() = runTest {
        val result = UpdateChecker(FakeVersion("1.0.0"), FakeFetcher("1.0.1"), FakeAssetFetcher("1.0.1")).check()
        val available = result as UpdateCheckResult.UpdateAvailable
        assertEquals("1.0.1", available.latestVersion)
        assertEquals("dezh-pasargad-release.apk", available.asset?.name)
    }

    @Test
    fun `asset-less available update still reports the version - vm decides the message`() = runTest {
        val result = UpdateChecker(FakeVersion("1.0.0"), FakeFetcher("1.0.1"), FakeAssetFetcher("1.0.1", asset = null)).check()
        val available = result as UpdateCheckResult.UpdateAvailable
        assertNull(available.asset)
    }

    @Test
    fun `same version with asset channel stays up to date`() = runTest {
        val result = UpdateChecker(FakeVersion("1.0.1"), FakeFetcher("1.0.1"), FakeAssetFetcher("1.0.1")).check()
        assertTrue(result is UpdateCheckResult.UpToDate)
    }

    @Test
    fun `asset channel failure maps to Failed`() = runTest {
        val result = UpdateChecker(
            FakeVersion("1.0.0"),
            FakeFetcher("1.0.1"),
            FakeAssetFetcher("1.0.1", failure = IOException("offline")),
        ).check()
        assertTrue(result is UpdateCheckResult.Failed)
    }

    @Test
    fun `legacy single-arg channel without asset fetcher still works`() = runTest {
        val result = UpdateChecker(FakeVersion("1.0.0"), FakeFetcher("1.0.1")).check()
        val available = result as UpdateCheckResult.UpdateAvailable
        assertNull(available.asset)
    }
}
