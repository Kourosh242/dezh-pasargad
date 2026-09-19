package com.pasargad.dezh.presentation.vault

import com.pasargad.dezh.domain.PasswordStrengthMeter
import com.pasargad.dezh.domain.VaultEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Password-health aggregation: proportions over the NIST-based meter, counts
 * only (never a numeric score), empty passwords excluded, and the per-entry
 * badge must always agree with the aggregate counts.
 */
class VaultHealthTest {

    private val meter = PasswordStrengthMeter()

    private fun entry(id: String, password: String) = VaultEntry(
        id = id,
        title = "T$id",
        username = "u$id",
        email = "",
        password = password,
        notes = "",
        category = "",
        favorite = false,
        createdAt = 0L,
        updatedAt = 0L,
    )

    @Test
    fun `summary counts only entries that carry a password`() {
        val entries = listOf(
            entry("a", "12345678"),
            entry("b", "Correct!Horse9Battery"),
            entry("c", "another-one-here-42"),
            entry("d", ""), // no password -> excluded
        )
        val summary = VaultHealth.summarize(entries, meter)
        assertEquals(3, summary.total)
        assertEquals(3, summary.weak + summary.fair + summary.strong)
    }

    @Test
    fun `attention is driven by weak presence only`() {
        val weakish = VaultHealth.summarize(listOf(entry("a", "12345678")), meter)
        val summary = VaultHealth.summarize(listOf(entry("b", "Correct!Horse9Battery")), meter)
        assertEquals(weakish.weak > 0, weakish.needsAttention)
        assertEquals(summary.weak > 0, summary.needsAttention)
    }

    @Test
    fun `empty vault yields empty summary`() {
        val summary = VaultHealth.summarize(emptyList(), meter)
        assertEquals(VaultHealthSummary(), summary)
        assertFalse(summary.needsAttention)
    }

    @Test
    fun `per-entry badge agrees with the aggregate`() {
        val entries = listOf(
            entry("a", "12345678"),
            entry("b", "Correct!Horse9Battery"),
            entry("c", ""),
        )
        val summary = VaultHealth.summarize(entries, meter)
        var weak = 0
        var fair = 0
        var strong = 0
        entries.forEach { entry ->
            when (VaultHealth.bandOf(entry, meter)) {
                PasswordHealthBand.WEAK -> weak++
                PasswordHealthBand.FAIR -> fair++
                PasswordHealthBand.STRONG -> strong++
                null -> assertNull(VaultHealth.bandOf(entry, meter))
            }
        }
        assertEquals(summary.weak, weak)
        assertEquals(summary.fair, fair)
        assertEquals(summary.strong, strong)
        assertTrue(summary.total == weak + fair + strong)
    }

    @Test
    fun `empty password has no band`() {
        assertNull(VaultHealth.bandOf(entry("x", ""), meter))
    }
}
