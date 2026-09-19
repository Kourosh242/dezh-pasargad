package com.pasargad.dezh.data.update

import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Release-channel asset parsing: finds the installable APK of a release. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ReleaseAssetParsingTest {

    private fun assetJson(
        name: String,
        url: String,
        size: Long = 4_000_000,
        digest: String? = null,
    ): String {
        val digestField = digest?.let { ",\"digest\":\"$it\"" } ?: ""
        return "{\"name\":\"$name\",\"browser_download_url\":\"$url\",\"size\":$size$digestField}"
    }

    private fun releaseJson(vararg assets: String): String =
        "{\"tag_name\":\"1.0.1\",\"name\":\"x\",\"html_url\":\"https://github.com/Kourosh242/dezh-pasargad/releases/tag/1.0.1\"," +
            "\"assets\":[${assets.joinToString(separator = ",")}]}"

    @Test
    fun `official release filename is preferred`() {
        val apk = GithubReleaseFetcher.parseApkAsset(
            releaseJson(
                assetJson("sources.zip", "https://github.com/x/sources.zip"),
                assetJson("dezh-pasargad-release.apk", "https://github.com/x/dezh-pasargad-release.apk"),
                assetJson("other.apk", "https://github.com/x/other.apk"),
            ),
        )
        assertEquals("dezh-pasargad-release.apk", apk?.name)
        assertEquals("https://github.com/x/dezh-pasargad-release.apk", apk?.url)
    }

    @Test
    fun `first apk asset is the fallback without the official name`() {
        val apk = GithubReleaseFetcher.parseApkAsset(
            releaseJson(
                assetJson("notes.md", "https://github.com/x/notes.md"),
                assetJson("app-build-101.apk", "https://github.com/x/app-build-101.apk", size = 3_939_667),
            ),
        )
        assertEquals("app-build-101.apk", apk?.name)
        assertEquals(3_939_667L, apk?.sizeBytes)
    }

    @Test
    fun `sha256 digest prefix is parsed lowercased`() {
        val apk = GithubReleaseFetcher.parseApkAsset(
            releaseJson(assetJson("dezh-pasargad-release.apk", "https://github.com/x/a.apk", digest = "sha256:ABCDEF01")),
        )
        assertEquals("abcdef01", apk?.sha256Hex)
    }

    @Test
    fun `release without apk asset yields null`() {
        val apk = GithubReleaseFetcher.parseApkAsset(
            releaseJson(assetJson("sources.zip", "https://github.com/x/sources.zip")),
        )
        assertNull(apk)
    }

    @Test
    fun `release without assets array yields null`() {
        val apk = GithubReleaseFetcher.parseApkAsset("{\"tag_name\":\"1.0.1\"}")
        assertNull(apk)
    }

    @Test(expected = IOException::class)
    fun `malformed payload is an error`() {
        GithubReleaseFetcher.parseApkAsset("broken")
    }

    @Test
    fun `asset without download url is skipped`() {
        val body = "{\"tag_name\":\"1.0.1\",\"assets\":[" +
            "{\"name\":\"a.apk\",\"size\":10}," +
            "{\"name\":\"dezh-pasargad-release.apk\",\"browser_download_url\":\"https://github.com/x/ok.apk\",\"size\":10}]}"
        val apk = GithubReleaseFetcher.parseApkAsset(body)
        assertEquals("https://github.com/x/ok.apk", apk?.url)
        assertTrue(apk!!.sizeBytes > 0)
    }
}
