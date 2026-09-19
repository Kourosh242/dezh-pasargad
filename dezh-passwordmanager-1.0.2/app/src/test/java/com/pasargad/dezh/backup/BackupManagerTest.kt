package com.pasargad.dezh.backup

import com.pasargad.dezh.cryptography.AesGcmCipher
import com.pasargad.dezh.cryptography.CryptoConstants
import com.pasargad.dezh.data.vault.FakeVaultEntryDao
import com.pasargad.dezh.data.vault.RoomVaultEntryRepository
import com.pasargad.dezh.domain.VaultEntryDraft
import com.pasargad.dezh.security.VaultSession
import java.security.SecureRandom
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Real end-to-end backup/restore scenarios over the actual encrypted-payload
 * repository (session-sealed rows, Fake in-memory DAO):
 * export → wipe → restore(REPLACE), export → restore(MERGE), duplicates,
 * wrong passphrase mid-flow and the no-plaintext-on-disk guarantee.
 */
class BackupManagerTest {

    private lateinit var dao: FakeVaultEntryDao
    private lateinit var session: VaultSession
    private lateinit var repository: RoomVaultEntryRepository
    private lateinit var manager: BackupManager
    private val passphrase = "رمزِ پشتیبان ۲۰۲۶!".toCharArray()

    @Before
    fun setUp() {
        dao = FakeVaultEntryDao()
        val cipher = AesGcmCipher(SecureRandom())
        session = VaultSession(cipher)
        repository = RoomVaultEntryRepository(
            dao = dao,
            session = session,
            json = Json { ignoreUnknownKeys = true },
            ioDispatcher = Dispatchers.Unconfined,
            clock = { FIXED_NOW },
        )
        session.unlockWithKey(ByteArray(CryptoConstants.AES_KEY_SIZE_BYTES))
        var tick = 0L
        manager = BackupManager(
            codec = BackupCodec(SecureRandom(), kdfIterations = TEST_ITERATIONS),
            repository = repository,
            clock = { FIXED_NOW + tick++ },
        )
    }

    private fun draft(title: String, password: String, category: String = "Email") = VaultEntryDraft(
        title = title, username = "u-$title", email = "$title@mail.example",
        password = password, notes = "note-$title", category = category,
    )

    @Test
    fun `export-restore REPLACE round-trips ids, timestamps and secrets`() = runBlocking {
        repository.createEntry(draft("Gmail", "pw-A"))
        repository.createEntry(draft("بانک ملت", "pw-B", category = "Banking"))
        val before = repository.observeEntries().first().associateBy { it.id }

        val bytes = manager.export(passphrase)
        // Destructive restore runs over the populated vault (that's the real scenario).
        assertEquals(2, repository.observeEntries().first().size)

        val payload = manager.decrypt(bytes, passphrase)
        val summary = manager.restore(payload, RestoreMode.REPLACE)

        assertEquals(2, summary.deletedExisting)
        assertEquals(2, summary.inserted)
        val after = repository.observeEntries().first().associateBy { it.id }
        assertEquals(before.keys, after.keys)
        assertEquals(before, after) // ids, timestamps, titles AND passwords identical
    }

    @Test
    fun `MERGE updates when the backup copy is strictly newer`() = runBlocking {
        repository.createEntry(draft("Shared", "pw-shared"))
        val backupBytes = manager.export(passphrase)
        val payload = manager.decrypt(backupBytes, passphrase)

        // Local edit made BEFORE the backup's timestamp → the backup copy is newer.
        val local = repository.observeEntries().first().single()
        repository.upsertEntries(listOf(local.copy(title = "Shared-edited", updatedAt = FIXED_NOW - 1_000)))

        val summary = manager.restore(payload, RestoreMode.MERGE)
        assertEquals(0, summary.deletedExisting)
        assertEquals(0, summary.inserted)
        assertEquals(1, summary.updated)
        assertEquals(0, summary.skipped)
        assertEquals("Shared", repository.observeEntries().first().single().title)
    }

    @Test
    fun `MERGE skips older backups, inserts missing and leaves local-only rows alone`() = runBlocking {
        repository.createEntry(draft("Shared", "pw-shared"))
        val backupBytes = manager.export(passphrase)
        val originalPayload = manager.decrypt(backupBytes, passphrase)

        // Local drift: newer local edit of Shared + a local-only entry.
        val shared = repository.observeEntries().first().single()
        repository.upsertEntries(listOf(shared.copy(title = "Shared-newer", updatedAt = FIXED_NOW + 5_000)))
        repository.createEntry(draft("LocalOnly", "pw-local"))

        // Simulate an entry that exists only in the (older) backup file.
        val payload = originalPayload.copy(
            entries = originalPayload.entries + testEntry("backup-only", updatedAt = FIXED_NOW - 2_000),
        )

        val summary = manager.restore(payload, RestoreMode.MERGE)
        assertEquals(1, summary.inserted) // backup-only entry added
        assertEquals(0, summary.updated) // local Shared is strictly newer
        assertEquals(1, summary.skipped) // Shared untouched
        assertEquals(0, summary.deletedExisting)

        val byTitle = repository.observeEntries().first().associateBy { it.title }
        assertEquals(setOf("Shared-newer", "LocalOnly", "t"), byTitle.keys)
        assertEquals("pw-local", byTitle.getValue("LocalOnly").password)
    }

    @Test
    fun `MERGE inserts and updates from an older backup taken before local growth`() = runBlocking {
        repository.createEntry(draft("Shared", "pw-shared-1"))
        val backupBytes = manager.export(passphrase)

        // After the backup: edit the shared entry (newer) and add a local-only one.
        val shared = repository.observeEntries().first().single()
        repository.upsertEntries(listOf(shared.copy(title = "Shared-newer", updatedAt = FIXED_NOW + 5_000)))
        val localOnlyId = repository.createEntry(draft("LocalOnly", "pw-local"))

        // Also delete a row that exists in the backup → merge must re-insert it.
        repository.deleteEntry(shared.id)

        val payload = manager.decrypt(backupBytes, passphrase)
        val summary = manager.restore(payload, RestoreMode.MERGE)

        assertEquals(1, summary.inserted) // Shared re-inserted
        assertEquals(0, summary.updated)
        assertEquals(0, summary.deletedExisting)
        val titles = repository.observeEntries().first().map { it.title }.toSet()
        assertEquals(setOf("Shared", "LocalOnly"), titles) // backup title won for the re-insert
        assertTrue(repository.observeEntries().first().any { it.id == localOnlyId })
    }

    @Test
    fun `REPLACE refuses a payload with duplicate ids`() = runBlocking {
        repository.createEntry(draft("A", "pw"))
        val payload = BackupPayload(
            exportedAtEpochMs = FIXED_NOW,
            entries = listOf(
                testEntry("dup", 1L),
                testEntry("dup", 2L),
            ),
        )
        val failure = runCatching { manager.restore(payload, RestoreMode.REPLACE) }.exceptionOrNull()
        assertTrue(failure is IllegalArgumentException)
        // Vault untouched by the rejected restore.
        assertEquals(1, repository.observeEntries().first().size)
    }

    @Test
    fun `wrong passphrase during restore is WRONG_PASSWORD and changes nothing`() = runBlocking {
        repository.createEntry(draft("A", "pw"))
        val bytes = manager.export(passphrase)
        val failure = runCatching { manager.decrypt(bytes, "باطل".toCharArray()) }.exceptionOrNull()
        assertTrue(failure is BackupParseException)
        assertEquals(ParseFailure.WRONG_PASSWORD, (failure as BackupParseException).reason)
        assertEquals(1, repository.observeEntries().first().size)
    }

    @Test
    fun `backup bytes never contain plaintext credentials`() = runBlocking {
        repository.createEntry(draft("Gmail", "SuperSecret123!"))
        val bytes = manager.export(passphrase)
        val text = bytes.decodeToString()
        assertFalse(text.contains("SuperSecret123!"))
        assertFalse(text.contains("Gmail"))
        assertFalse(text.contains("\"password\""))
        // …while the restored vault still contains them.
        assertTrue(
            repository.observeEntries().first().single().password == "SuperSecret123!",
        )
    }

    @Test
    fun `empty vault backup restores to an empty vault`() = runBlocking {
        val bytes = manager.export(passphrase)
        repository.createEntry(draft("temp", "pw"))
        val payload = manager.decrypt(bytes, passphrase)
        manager.restore(payload, RestoreMode.REPLACE)
        assertTrue(repository.observeEntries().first().isEmpty())
    }

    private fun testEntry(id: String, updatedAt: Long) = BackupEntry(
        id = id, title = "t", username = "u", email = "e", password = "p",
        notes = "", category = "C", favorite = false,
        createdAtEpochMs = 1L, updatedAtEpochMs = updatedAt,
    )

    private companion object {
        const val FIXED_NOW = 1_760_000_000_000L
        const val TEST_ITERATIONS = 20_000
    }
}
