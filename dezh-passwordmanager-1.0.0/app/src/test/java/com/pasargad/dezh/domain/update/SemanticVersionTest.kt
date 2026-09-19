package com.pasargad.dezh.domain.update

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Dotted-version ordering used by the update check. */
class SemanticVersionTest {

    @Test
    fun `candidate one patch ahead is newer`() {
        assertTrue(SemanticVersion.isNewer("1.0.0", "1.0.1"))
    }

    @Test
    fun `equal versions are not newer`() {
        assertFalse(SemanticVersion.isNewer("1.0.0", "1.0.0"))
    }

    @Test
    fun `older candidate is not newer`() {
        assertFalse(SemanticVersion.isNewer("1.0.1", "1.0.0"))
    }

    @Test
    fun `numeric comparison not lexicographic`() {
        assertTrue(SemanticVersion.isNewer("1.9.0", "1.10.0"))
    }

    @Test
    fun `major bump wins`() {
        assertTrue(SemanticVersion.isNewer("1.0.0", "2.0.0"))
    }

    @Test
    fun `v prefix is tolerated`() {
        assertTrue(SemanticVersion.isNewer("1.0.0", "v1.0.1"))
    }

    @Test
    fun `missing parts count as zero`() {
        assertTrue(SemanticVersion.isNewer("1.0", "1.0.1"))
        assertFalse(SemanticVersion.isNewer("1.0.0", "1"))
    }

    @Test
    fun `non numeric suffix is ignored`() {
        assertTrue(SemanticVersion.isNewer("1.0.0", "1.0.1-beta"))
    }
}
