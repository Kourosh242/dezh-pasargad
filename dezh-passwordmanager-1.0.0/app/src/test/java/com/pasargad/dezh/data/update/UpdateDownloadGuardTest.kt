package com.pasargad.dezh.data.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Download-side guards: channel host allowlist, filename sanitisation, digests. */
class UpdateDownloadGuardTest {

    @Test
    fun `github release asset urls are allowed`() {
        assertTrue(UpdateDownloadGuard.isAllowedApkUrl("https://github.com/Kourosh242/dezh-pasargad/releases/download/1.0.1/dezh-pasargad-release.apk"))
        assertTrue(UpdateDownloadGuard.isAllowedApkUrl("https://objects.githubusercontent.com/x/dezh.apk"))
    }

    @Test
    fun `github subdomains are allowed`() {
        assertTrue(UpdateDownloadGuard.isAllowedApkUrl("https://raw.githubusercontent.com/Kourosh242/x/master/a.apk"))
    }

    @Test
    fun `plain http is rejected`() {
        assertFalse(UpdateDownloadGuard.isAllowedApkUrl("http://github.com/x/y.apk"))
    }

    @Test
    fun `look-alike hosts are rejected`() {
        assertFalse(UpdateDownloadGuard.isAllowedApkUrl("https://github.com.evil.example/x.apk"))
        assertFalse(UpdateDownloadGuard.isAllowedApkUrl("https://notgithub.com/x.apk"))
        assertFalse(UpdateDownloadGuard.isAllowedApkUrl("https://example.com/x.apk"))
    }

    @Test
    fun `non-https schemes are rejected`() {
        assertFalse(UpdateDownloadGuard.isAllowedApkUrl("file:///sdcard/x.apk"))
        assertFalse(UpdateDownloadGuard.isAllowedApkUrl("content://downloads/x.apk"))
        assertFalse(UpdateDownloadGuard.isAllowedApkUrl("ftp://github.com/x.apk"))
    }

    @Test
    fun `tag with path separators and unicode collapses to safe filename`() {
        val safe = UpdateDownloadGuard.sanitizeFileName("../../1.0.1/دژ release")
        assertFalse(safe.contains('/'))
        assertFalse(safe.contains("..$"))
        assertTrue(safe.matches(Regex("[A-Za-z0-9._\\-]{1,64}")))
    }

    @Test
    fun `unsafe tag collapses to underscores - caller supplies prefix`() {
        assertEquals("___", UpdateDownloadGuard.sanitizeFileName("###"))
    }

    @Test
    fun `digest comparison is case-insensitive and optional`() {
        assertTrue(UpdateDownloadGuard.isDigestMatch("ABC", "abc"))
        assertTrue(UpdateDownloadGuard.isDigestMatch(null, "anything"))
        assertFalse(UpdateDownloadGuard.isDigestMatch("aa11", "bb22"))
    }
}
