package com.pasargad.dezh.domain.update

import java.io.IOException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Update decision logic with a fake channel — no network involved. */
class UpdateCheckerTest {

    private class FakeVersion(override val currentVersion: String) : AppVersionProvider

    private class FakeFetcher(initialTag: String) : ReleaseFetcher {
        var tag: String = initialTag
        var fail: Boolean = false
        var calls: Int = 0

        override suspend fun fetchLatest(): ReleaseInfo {
            calls++
            if (fail) throw IOException("offline")
            return ReleaseInfo(tag, "https://example.com/tag/$tag")
        }
    }

    @Test
    fun `newer tag reports update available with release url`() = runBlocking {
        val checker = UpdateChecker(FakeVersion("1.0.0"), FakeFetcher("1.0.1"))
        val result = checker.check()
        assertTrue(result is UpdateCheckResult.UpdateAvailable)
        val available = result as UpdateCheckResult.UpdateAvailable
        assertEquals("1.0.1", available.latestVersion)
        assertEquals("https://example.com/tag/1.0.1", available.releaseUrl)
    }

    @Test
    fun `same tag reports up to date`() = runBlocking {
        val result = UpdateChecker(FakeVersion("1.0.0"), FakeFetcher("1.0.0")).check()
        assertTrue(result is UpdateCheckResult.UpToDate)
        assertEquals("1.0.0", (result as UpdateCheckResult.UpToDate).currentVersion)
    }

    @Test
    fun `older tag reports up to date`() = runBlocking {
        val result = UpdateChecker(FakeVersion("1.0.1"), FakeFetcher("1.0.0")).check()
        assertTrue(result is UpdateCheckResult.UpToDate)
    }

    @Test
    fun `network failure maps to failed result`() = runBlocking {
        val fetcher = FakeFetcher("1.0.1").apply { fail = true }
        val result = UpdateChecker(FakeVersion("1.0.0"), fetcher).check()
        assertTrue(result is UpdateCheckResult.Failed)
    }

    @Test
    fun `every check hits the channel fresh - no caching`() = runBlocking {
        val fetcher = FakeFetcher("1.0.0")
        val checker = UpdateChecker(FakeVersion("1.0.0"), fetcher)
        checker.check()
        fetcher.tag = "1.0.1"
        val second = checker.check()
        assertEquals(2, fetcher.calls)
        assertTrue(second is UpdateCheckResult.UpdateAvailable)
    }
}
