package com.pasargad.dezh.data.update

import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Payload parsing for the GitHub releases endpoint (org.json via Robolectric). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class GithubReleaseFetcherTest {

    @Test
    fun `parses tag_name and html_url`() {
        val body = "{\"tag_name\":\"1.0.1\",\"name\":\"x\"," +
            "\"html_url\":\"https://github.com/Kourosh242/dezh-pasargad/releases/tag/1.0.1\"}"
        val info = GithubReleaseFetcher.parse(body)
        assertEquals("1.0.1", info.latestTag)
        assertEquals("https://github.com/Kourosh242/dezh-pasargad/releases/tag/1.0.1", info.releaseUrl)
    }

    @Test
    fun `html_url absent falls back to the releases page`() {
        val info = GithubReleaseFetcher.parse("{\"tag_name\":\"1.0.2\"}")
        assertEquals("https://github.com/Kourosh242/dezh-pasargad/releases/latest", info.releaseUrl)
    }

    @Test(expected = IOException::class)
    fun `missing tag_name is an error`() {
        GithubReleaseFetcher.parse("{\"html_url\":\"https://example.com\"}")
    }

    @Test(expected = IOException::class)
    fun `malformed json is an error`() {
        GithubReleaseFetcher.parse("not json at all")
    }
}
