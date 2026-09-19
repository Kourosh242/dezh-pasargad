package com.pasargad.dezh.domain.update

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Self-update admission policy: the ONLY archive that may reach the installer
 * is one for THIS package, strictly NEWER than the running build, signed with
 * the SAME key. These tests pin each refusal branch.
 */
class SelfUpdatePolicyTest {

    private val current = CurrentAppInfo(
        packageName = "com.pasargad.dezh",
        versionCode = 1,
        certSha256Hex = listOf("aa11", "bb22"),
    )

    private fun archive(
        packageName: String = "com.pasargad.dezh",
        versionCode: Long = 2,
        certs: List<String> = listOf("bb22"),
    ) = ApkArchiveInfo(packageName, versionCode, certs)

    @Test
    fun `matching package newer version same key is valid`() {
        assertEquals(Verdict.Valid, SelfUpdatePolicy.decide(archive(), current))
    }

    @Test
    fun `any of the running certificates matching admits the archive`() {
        assertEquals(Verdict.Valid, SelfUpdatePolicy.decide(archive(certs = listOf("cc33", "aa11")), current))
    }

    @Test
    fun `unreadable archive is refused`() {
        assertEquals(Verdict.Unreadable, SelfUpdatePolicy.decide(archive = null, current = current))
    }

    @Test
    fun `foreign package name is refused with both names reported`() {
        val verdict = SelfUpdatePolicy.decide(archive(packageName = "com.evil.clone"), current)
        assertEquals(Verdict.WrongPackage("com.pasargad.dezh", "com.evil.clone"), verdict)
    }

    @Test
    fun `same versionCode is refused - downgrade guard`() {
        val verdict = SelfUpdatePolicy.decide(archive(versionCode = 1), current)
        assertEquals(Verdict.NotNewer(currentVersionCode = 1, candidateVersionCode = 1), verdict)
    }

    @Test
    fun `older versionCode is refused`() {
        val verdict = SelfUpdatePolicy.decide(archive(versionCode = 0), current)
        assertEquals(Verdict.NotNewer(1, 0), verdict)
    }

    @Test
    fun `different signing key is refused even for a newer version`() {
        val verdict = SelfUpdatePolicy.decide(archive(versionCode = 99, certs = listOf("deadbeef")), current)
        assertEquals(Verdict.SignatureMismatch, verdict)
    }

    @Test
    fun `archive with no certificates is refused`() {
        val verdict = SelfUpdatePolicy.decide(archive(certs = emptyList()), current)
        assertEquals(Verdict.SignatureMismatch, verdict)
    }
}
