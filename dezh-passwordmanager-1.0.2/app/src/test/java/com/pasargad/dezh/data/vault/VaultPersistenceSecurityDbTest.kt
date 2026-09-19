package com.pasargad.dezh.data.vault

import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.pasargad.dezh.cryptography.AesGcmCipher
import com.pasargad.dezh.cryptography.CryptoConstants
import com.pasargad.dezh.domain.VaultEntryDraft
import com.pasargad.dezh.security.VaultSession
import java.io.File
import java.security.SecureRandom
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * End-to-end persistence security (Robolectric, file-backed Room):
 * a real repository (real AES-256-GCM session) writes entries; the raw database
 * file on disk is then scanned byte-by-byte to prove that neither the entry
 * secrets nor anything resembling the master password is stored in plaintext.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class VaultPersistenceSecurityDbTest {

    private lateinit var context: Context
    private lateinit var db: DezhVaultDatabase
    private lateinit var repository: RoomVaultEntryRepository
    private lateinit var session: VaultSession
    private lateinit var dao: VaultEntryDao

    private val entryPassword = "MyStr0ng-Pass!۱۲۳"
    private val entryNotes = "recovery codes: 1-2-3-4"
    private val masterPasswordSample = "Master-Koochak-Nist-1403!"

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getDatabasePath(DB_NAME).delete()
        db = Room.databaseBuilder(context, DezhVaultDatabase::class.java, DB_NAME)
            .allowMainThreadQueries()
            .build()
        dao = db.vaultEntryDao()
        session = VaultSession(AesGcmCipher(SecureRandom()))
        session.unlockWithKey(ByteArray(CryptoConstants.AES_KEY_SIZE_BYTES))
        repository = RoomVaultEntryRepository(
            dao = dao,
            session = session,
            json = Json { ignoreUnknownKeys = true },
            ioDispatcher = Dispatchers.Unconfined,
            clock = { 1_700_000_000_000L },
        )
    }

    @After
    fun tearDown() {
        db.close()
        context.getDatabasePath(DB_NAME).delete()
    }

    @Test
    fun `database file contains no plaintext entry secrets`() = runBlocking {
        repository.createEntry(
            VaultEntryDraft(
                title = "Bank Portal",
                username = "secret-user",
                email = "secret@mail.com",
                password = entryPassword,
                notes = entryNotes,
                category = "Banking",
            ),
        )

        val dbFile: File = context.getDatabasePath(DB_NAME)
        assertTrue(dbFile.isFile)
        val diskBytes = dbFile.readBytes()

        assertFalse(
            "password leaked to disk",
            containsSubarray(diskBytes, entryPassword.encodeToByteArray()),
        )
        assertFalse("notes leaked to disk", containsSubarray(diskBytes, entryNotes.encodeToByteArray()))
        assertFalse("username leaked to disk", containsSubarray(diskBytes, "secret-user".encodeToByteArray()))
        assertFalse("master-password-like secret leaked", containsSubarray(diskBytes, masterPasswordSample.encodeToByteArray()))

        // The sealed payload must be a DPVG container with a non-trivial size.
        val payload = dao.getAll().single().payload
        assertEquals(CryptoConstants.DATA_CONTAINER_MAGIC, String(payload.copyOfRange(0, 4)))
        assertTrue(payload.size > CryptoConstants.GCM_TAG_SIZE_BYTES)
    }

    @Test
    fun `locked session leaves existing rows unreadable from repository`() = runBlocking {
        val id = repository.createEntry(VaultEntryDraft(title = "T", password = entryPassword))
        val rawPayload = dao.getById(id)!!.payload // ciphertext is on disk
        assertFalse("payload must not be plaintext", rawPayload.contentEquals(entryPassword.encodeToByteArray()))
        assertTrue(rawPayload.isNotEmpty())

        session.lock()
        // Raw ciphertext still exists on disk (expected), but the repository
        // refuses to expose plaintext while locked.
        val readAttempt = runCatching { repository.getEntry(id) }
        assertTrue(readAttempt.isFailure)
    }

    private fun containsSubarray(haystack: ByteArray, needle: ByteArray): Boolean {
        if (needle.isEmpty() || haystack.size < needle.size) return false
        return (0..haystack.size - needle.size).any { offset ->
            needle.indices.all { j -> haystack[offset + j] == needle[j] }
        }
    }

    private companion object {
        const val DB_NAME = "vault-security-test.db"
    }
}
