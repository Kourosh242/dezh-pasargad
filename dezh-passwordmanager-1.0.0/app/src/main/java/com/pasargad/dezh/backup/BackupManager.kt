package com.pasargad.dezh.backup

import com.pasargad.dezh.domain.VaultEntry
import com.pasargad.dezh.domain.VaultEntryRepository
import kotlinx.coroutines.flow.firstOrNull

/** Result summary shown after a restore completes. */
data class RestoreSummary(
    val mode: RestoreMode,
    val deletedExisting: Int,
    val inserted: Int,
    val updated: Int,
    val skipped: Int,
)

/**
 * Backup/restore orchestration. Pure orchestration only — file I/O stays behind
 * the BackupFileGateway and crypto stays in [BackupCodec], so every path is
 * unit-testable on the JVM.
 */
class BackupManager(
    private val codec: BackupCodec,
    private val repository: VaultEntryRepository,
    private val clock: () -> Long = System::currentTimeMillis,
) {

    /** Snapshot of the current vault → encrypted backup bytes (no plaintext on disk). */
    suspend fun export(passphrase: CharArray): ByteArray {
        val entries = repository.observeEntries().firstOrNull().orEmpty()
        return codec.seal(entries.toPayload(clock()), passphrase)
    }

    /** Header check without the passphrase: format, version, checksum. */
    fun readHeader(bytes: ByteArray): BackupHeader = codec.readHeader(bytes)

    /** Decrypts the payload (wrong passphrase/corruption surface as [BackupParseException]). */
    fun decrypt(bytes: ByteArray, passphrase: CharArray): BackupPayload = codec.open(bytes, passphrase)

    /**
     * Applies the chosen restore mode. Cancel never reaches this method.
     * MERGE: add missing + update strictly-newer, keep everything else.
     * REPLACE: wipe the vault, then import the backup content verbatim.
     */
    suspend fun restore(payload: BackupPayload, mode: RestoreMode): RestoreSummary = when (mode) {
        RestoreMode.MERGE -> {
            val existing = repository.observeEntries().firstOrNull().orEmpty()
            val plan = RestorePlanner.planMerge(existing, payload.entries)
            repository.upsertEntries(plan.toInsert.map { it.toVaultEntry() })
            repository.upsertEntries(plan.toUpdate.map { it.toVaultEntry() })
            RestoreSummary(
                mode = mode,
                deletedExisting = 0,
                inserted = plan.toInsert.size,
                updated = plan.toUpdate.size,
                skipped = plan.skipped.size,
            )
        }

        RestoreMode.REPLACE -> {
            RestorePlanner.validateReplaceInput(payload.entries)
            val existing = repository.observeEntries().firstOrNull().orEmpty()
            repository.replaceAllEntries(payload.entries.map { it.toVaultEntry() })
            RestoreSummary(
                mode = mode,
                deletedExisting = existing.size,
                inserted = payload.entries.size,
                updated = 0,
                skipped = 0,
            )
        }
    }

    private fun List<VaultEntry>.toPayload(exportedAt: Long) = BackupPayload(
        exportedAtEpochMs = exportedAt,
        entries = map { entry ->
            BackupEntry(
                id = entry.id,
                title = entry.title,
                username = entry.username,
                email = entry.email,
                password = entry.password,
                notes = entry.notes,
                category = entry.category,
                favorite = entry.favorite,
                createdAtEpochMs = entry.createdAt,
                updatedAtEpochMs = entry.updatedAt,
            )
        },
    )

    private fun BackupEntry.toVaultEntry() = VaultEntry(
        id = id,
        title = title,
        username = username,
        email = email,
        password = password,
        notes = notes,
        category = category,
        favorite = favorite,
        createdAt = createdAtEpochMs,
        updatedAt = updatedAtEpochMs,
    )
}
