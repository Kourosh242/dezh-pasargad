package com.pasargad.dezh.backup

import com.pasargad.dezh.domain.VaultEntry

/**
 * Plans a restore against the current vault so the UI can state the exact
 * consequences before anything is written. No silent overwrites: every change
 * the plan contains is the result of an explicit user-chosen [RestoreMode].
 */
object RestorePlanner {

    data class MergePlan(
        val toInsert: List<BackupEntry>,
        val toUpdate: List<BackupEntry>,
        val skipped: List<BackupEntry>,
    ) {
        val changeCount: Int get() = toInsert.size + toUpdate.size
    }

    /**
     * MERGE semantics (shown verbatim in the UI):
     * - entries absent from the vault are added;
     * - entries with the same id are updated only if the backup copy is newer
     *   (updatedAt strictly greater) — the local copy otherwise wins;
     * - everything else in the vault stays untouched.
     */
    fun planMerge(existing: List<VaultEntry>, incoming: List<BackupEntry>): MergePlan {
        val existingById = existing.associateBy { it.id }
        val inserts = ArrayList<BackupEntry>()
        val updates = ArrayList<BackupEntry>()
        val skipped = ArrayList<BackupEntry>()
        incoming.forEach { candidate ->
            val current = existingById[candidate.id]
            when {
                current == null -> inserts += candidate
                candidate.updatedAtEpochMs > current.updatedAt -> updates += candidate
                else -> skipped += candidate
            }
        }
        return MergePlan(toInsert = inserts, toUpdate = updates, skipped = skipped)
    }

    /**
     * REPLACE semantics (shown verbatim in the UI, behind an explicit
     * confirmation dialog): the whole current vault is deleted, then the backup
     * content is imported with its original ids and timestamps.
     */
    fun validateReplaceInput(incoming: List<BackupEntry>) {
        // All-or-nothing guard: refuse to wipe the vault when the backup itself
        // contains duplicate ids (would silently drop rows).
        val distinctIds = incoming.map { it.id }.toSet()
        require(distinctIds.size == incoming.size) { "Backup contains duplicate ids" }
    }
}
