package com.pasargad.dezh.data.vault

import com.pasargad.dezh.domain.VaultEntryDraft
import com.pasargad.dezh.security.VaultSession
import com.pasargad.dezh.cryptography.AesGcmCipher
import com.pasargad.dezh.cryptography.CryptoConstants
import java.security.SecureRandom
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Corrupt-payload containment: one unreadable row must NEVER kill the whole
 * list flow (the "empty vault + crash" bug) nor crash single-entry reads —
 * broken rows are skipped, healthy ones keep working.
 */
class CorruptRowContainmentTest {

    private lateinit var dao: FakeVaultEntryDao
    private lateinit var repository: RoomVaultEntryRepository

    @Before
    fun setUp() {
        dao = FakeVaultEntryDao()
        val session = VaultSession(AesGcmCipher(SecureRandom()))
        repository = RoomVaultEntryRepository(
            dao = dao,
            session = session,
            json = Json { ignoreUnknownKeys = true },
            ioDispatcher = Dispatchers.Unconfined,
            clock = { 1_700_000_000_000L },
        )
        session.unlockWithKey(ByteArray(CryptoConstants.AES_KEY_SIZE_BYTES))
    }

    private fun draft(title: String) = VaultEntryDraft(
        title = title,
        username = "user",
        email = "",
        password = "pw-123",
        notes = "",
        category = "عمومی",
        favorite = false,
    )

    @Test
    fun `a corrupt row is skipped and healthy rows survive`() = runBlocking {
        val goodId = repository.createEntry(draft("سالم"))
        val badId = repository.createEntry(draft("خراب"))
        dao.updatePayload(badId, "{\"broken\": true".encodeToByteArray())

        val listed = repository.observeEntries("", null, false, com.pasargad.dezh.domain.VaultSortOption.UPDATED_NEWEST).first()

        assertEquals(listOf(goodId), listed.map { it.id })
        assertTrue(listed.single().title == "سالم")
    }

    @Test
    fun `single read of a corrupt row yields null instead of crashing`() = runBlocking {
        val badId = repository.createEntry(draft("خراب"))
        dao.updatePayload(badId, "not-a-container".encodeToByteArray())
        assertNull(repository.getEntry(badId))
        assertNull(repository.observeEntry(badId).first())
    }
}
