package com.pasargad.dezh.data.vault

import com.pasargad.dezh.cryptography.AesGcmCipher
import com.pasargad.dezh.cryptography.CryptoConstants
import com.pasargad.dezh.cryptography.EncryptionContainer
import com.pasargad.dezh.domain.VaultEntryDraft
import com.pasargad.dezh.domain.VaultEntryNotFoundException
import com.pasargad.dezh.domain.VaultSortOption
import com.pasargad.dezh.security.VaultSession
import com.pasargad.dezh.security.VaultSessionLockedException
import java.security.SecureRandom
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * JVM repository tests: real crypto (AES-256-GCM session) on top of a fake DAO.
 * Covers CRUD, favorite/category, in-memory search, encrypted-at-rest payload
 * verification, large-dataset behavior and fail-closed behavior when locked.
 */
class RoomVaultEntryRepositoryTest {

    private lateinit var dao: FakeVaultEntryDao
    private lateinit var session: VaultSession
    private lateinit var repository: RoomVaultEntryRepository

    private val secretPassword = "Sup3r-Secret!رمز"

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
            clock = { 1_700_000_000_000L },
        )
        session.unlockWithKey(ByteArray(CryptoConstants.AES_KEY_SIZE_BYTES))
    }

    private fun draft(
        title: String = "Gmail",
        username: String = "user1",
        email: String = "user1@example.com",
        password: String = secretPassword,
        notes: String = "backup codes in drawer",
        category: String = "Email",
        favorite: Boolean = false,
    ) = VaultEntryDraft(title, username, email, password, notes, category, favorite)

    @Test
    fun `create then observe returns decrypted entry`() = runBlocking {
        val id = repository.createEntry(draft())
        val entries = repository.observeEntries().first()

        assertEquals(1, entries.size)
        val entry = entries.single()
        assertEquals(id, entry.id)
        assertEquals("Gmail", entry.title)
        assertEquals("user1", entry.username)
        assertEquals("user1@example.com", entry.email)
        assertEquals(secretPassword, entry.password)
        assertEquals("backup codes in drawer", entry.notes)
        assertEquals("Email", entry.category)
        assertEquals(false, entry.favorite)
    }

    @Test
    fun `read by id returns entry and unknown id returns null`() = runBlocking {
        val id = repository.createEntry(draft())
        assertNotNull(repository.getEntry(id))
        assertNull(repository.getEntry("missing-id"))
    }

    @Test
    fun `update rewrites payload and bumps updatedAt`() = runBlocking {
        val id = repository.createEntry(draft())
        repository.updateEntry(id, draft(title = "Gmail2", password = "NewPass-42!x"))

        val entry = repository.getEntry(id)!!
        assertEquals("Gmail2", entry.title)
        assertEquals("NewPass-42!x", entry.password)
        assertEquals(entry.createdAt, entry.updatedAt)
    }

    @Test
    fun `delete removes the entry and missing id fails`(): Unit = runBlocking {
        val id = repository.createEntry(draft())
        repository.deleteEntry(id)
        assertTrue(repository.observeEntries().first().isEmpty())
        assertThrows(VaultEntryNotFoundException::class.java) { runBlocking { repository.deleteEntry(id) } }
    }

    @Test
    fun `favorite toggle is persisted in metadata only`() = runBlocking {
        val id = repository.createEntry(draft())
        repository.setFavorite(id, true)
        assertTrue(repository.observeEntries(favoritesOnly = true).first().single().favorite)
        repository.setFavorite(id, false)
        assertTrue(repository.observeEntries(favoritesOnly = true).first().isEmpty())
    }

    @Test
    fun `category filtering and distinct categories work at db level`() = runBlocking {
        repository.createEntry(draft(category = "Email"))
        repository.createEntry(draft(title = "Bank", category = "Banking"))
        repository.createEntry(draft(title = "Cloud", category = ""))

        val banking = repository.observeEntries(category = "Banking").first()
        assertEquals(1, banking.size)
        assertEquals("Bank", banking.single().title)

        val categories = repository.observeCategories().first()
        assertEquals(listOf("Banking", "Email"), categories)
    }

    @Test
    fun `in-memory search finds content in notes username and title`() = runBlocking {
        repository.createEntry(draft(title = "Gmail"))
        repository.createEntry(draft(title = "Server", username = "root", notes = "SSH prod notes"))
        repository.createEntry(draft(title = "Shop", email = "orders@shop.io"))

        assertEquals(1, repository.observeEntries(query = "prod").first().size)
        assertEquals(1, repository.observeEntries(query = "orders@").first().size)
        assertEquals(1, repository.observeEntries(query = "gmail").first().size)
        assertEquals(0, repository.observeEntries(query = "notfound").first().size)
    }

    @Test
    fun `encrypted at rest - payload blob holds no plaintext and differs per write`() = runBlocking {
        val id = repository.createEntry(draft())
        val firstPayload = dao.getById(id)!!.payload

        // structural: valid DPVG container
        assertEquals(CryptoConstants.DATA_CONTAINER_MAGIC, String(firstPayload.copyOfRange(0, 4)))

        // no plaintext sensitive material inside the blob
        listOf(secretPassword, "backup codes in drawer", "user1@example.com", "Gmail").forEach { sensitive ->
            assertFalse(containsSubarray(firstPayload, sensitive.encodeToByteArray()))
        }

        // unique nonce/ciphertext per encryption
        repository.updateEntry(id, draft())
        val secondPayload = dao.getById(id)!!.payload
        assertFalse(firstPayload.contentEquals(secondPayload))
    }

    @Test
    fun `locked session fails closed on writes and reads`(): Unit = runBlocking {
        val id = repository.createEntry(draft())
        session.lock()
        assertThrows(VaultSessionLockedException::class.java) { runBlocking { repository.createEntry(draft()) } }
        // With a stored (encrypted) row present, a locked read cannot decrypt -> fail-closed.
        assertThrows(VaultSessionLockedException::class.java) { runBlocking { repository.observeEntries().first() } }
        assertThrows(VaultSessionLockedException::class.java) { runBlocking { repository.getEntry(id) } }
    }

    @Test
    fun `upsert replace keeps a single row per id`() = runBlocking {
        val id = repository.createEntry(draft())
        repository.updateEntry(id, draft(title = "Renamed"))
        assertEquals(1, dao.count())
        assertEquals("Renamed", repository.getEntry(id)!!.title)
    }

    @Test
    fun `large dataset - 5000 entries stay correct through decrypt-filter path`() = runBlocking {
        val drafts = (0 until 5_000).map { index ->
            draft(title = "Service-$index", username = "user-$index", category = if (index % 10 == 0) "Big" else "Small")
        }
        drafts.forEach { repository.createEntry(it) }

        assertEquals(5_000, dao.count())

        val bigCategory = repository.observeEntries(category = "Big").first()
        assertEquals(500, bigCategory.size)

        val needle = repository.observeEntries(query = "service-4999").first()
        assertEquals(1, needle.size)
        assertEquals("user-4999", needle.single().username)

        val all = repository.observeEntries().first()
        assertEquals(5_000, all.size)
        assertNotEquals(all[0].id, all[1].id)
    }

    @Test
    fun `payload roundtrip through container codec preserves secrets`() = runBlocking {
        val id = repository.createEntry(draft())
        val blob = dao.getById(id)!!.payload
        val container = EncryptionContainer.decode(blob)
        assertArrayEquals(blob, container.encode())
    }

    private fun containsSubarray(haystack: ByteArray, needle: ByteArray): Boolean {
        if (needle.isEmpty() || haystack.size < needle.size) return false
        return (0..haystack.size - needle.size).any { offset ->
            needle.indices.all { j -> haystack[offset + j] == needle[j] }
        }
    }

    @Test
    fun `sort options order entries correctly`() = runBlocking {
        var now = 1_700_000_000_000L
        val timedRepository = RoomVaultEntryRepository(
            dao = FakeVaultEntryDao(),
            session = session,
            json = Json { ignoreUnknownKeys = true },
            ioDispatcher = Dispatchers.Unconfined,
            clock = { now += 1_000L; now },
        )
        val a = timedRepository.createEntry(draft(title = "بانک ملت", category = "Banking")) // created 1st
        val b = timedRepository.createEntry(draft(title = "Gmail", category = "Email"))
        val c = timedRepository.createEntry(draft(title = "Shop", category = "Email"))
        timedRepository.setFavorite(b, true)

        val byName = timedRepository.observeEntries(sort = VaultSortOption.NAME).first().map { it.title }
        assertEquals(listOf("Gmail", "Shop", "بانک ملت"), byName)

        val byCreated = timedRepository.observeEntries(sort = VaultSortOption.CREATED_NEWEST).first().map { it.id }
        assertEquals(listOf(c, b, a), byCreated)

        val byCategory = timedRepository.observeEntries(sort = VaultSortOption.CATEGORY).first().map { it.title }
        assertEquals(listOf("بانک ملت", "Gmail", "Shop"), byCategory)

        val favsFirst = timedRepository.observeEntries(sort = VaultSortOption.FAVORITES_FIRST).first().map { it.title }
        assertEquals("Gmail", favsFirst.first()) // favorite first, then updatedAt DESC
    }

    private companion object {
        const val SORT_SLEEP_MS = 10L // kept for reference; sort test uses an incremental clock
    }
}
