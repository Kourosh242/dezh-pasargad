package com.pasargad.dezh.backup

import com.pasargad.dezh.domain.VaultEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RestorePlannerTest {

    private fun local(id: String, updatedAt: Long, title: String = "local-$id") = VaultEntry(
        id = id, title = title, username = "u", email = "e", password = "p",
        notes = "", category = "C", favorite = false, createdAt = 1L, updatedAt = updatedAt,
    )

    private fun incoming(id: String, updatedAt: Long, title: String = "incoming-$id") = BackupEntry(
        id = id, title = title, username = "u", email = "e", password = "p",
        notes = "", category = "C", favorite = false, createdAtEpochMs = 1L, updatedAtEpochMs = updatedAt,
    )

    @Test
    fun `missing entries are planned as inserts`() {
        val plan = RestorePlanner.planMerge(
            existing = listOf(local("a", 10)),
            incoming = listOf(incoming("a", 10), incoming("b", 5), incoming("c", 7)),
        )
        assertEquals(listOf("b", "c"), plan.toInsert.map { it.id })
        assertTrue(plan.toUpdate.isEmpty())
        assertEquals(listOf("a"), plan.skipped.map { it.id })
    }

    @Test
    fun `strictly newer backup copies are updates - equal or older are skipped`() {
        val plan = RestorePlanner.planMerge(
            existing = listOf(local("a", 10), local("b", 10), local("c", 20)),
            incoming = listOf(incoming("a", 11), incoming("b", 10), incoming("c", 19)),
        )
        assertEquals(listOf("a"), plan.toUpdate.map { it.id })
        assertEquals(setOf("b", "c"), plan.skipped.map { it.id }.toSet())
    }

    @Test
    fun `merge never plans deletions`() {
        val plan = RestorePlanner.planMerge(
            existing = listOf(local("a", 1), local("gone-locally", 1)),
            incoming = listOf(incoming("a", 1)),
        )
        assertEquals(0, plan.toInsert.size + plan.toUpdate.size)
    }

    @Test
    fun `replace refuses duplicate ids in the backup`() {
        val duplicates = listOf(incoming("x", 1), incoming("x", 2))
        val failure = runCatching { RestorePlanner.validateReplaceInput(duplicates) }.exceptionOrNull()
        assertTrue(failure is IllegalArgumentException)
        RestorePlanner.validateReplaceInput(listOf(incoming("x", 1), incoming("y", 1)))
    }
}
