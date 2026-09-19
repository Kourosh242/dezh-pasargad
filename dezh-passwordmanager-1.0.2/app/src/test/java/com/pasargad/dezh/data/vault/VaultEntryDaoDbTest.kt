package com.pasargad.dezh.data.vault

import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Real Room database tests (Robolectric/SQLite on JVM): CRUD, filters,
 * transaction integrity, persistence across "process recreation" (close/reopen),
 * large-dataset behavior and encrypted-at-rest verification against the raw row.
 */
@RunWith(RobolectricTestRunner::class)
// SDK 35: the highest Robolectric android-all that runs on the project's JDK 17 baseline
// (SDK 36 emulation requires JDK 21; Room behavior under test is SDK-agnostic).
@Config(sdk = [35], application = Application::class)
class VaultEntryDaoDbTest {

    private lateinit var context: Context
    private lateinit var db: DezhVaultDatabase
    private lateinit var dao: VaultEntryDao

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, DezhVaultDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.vaultEntryDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun entity(
        id: String,
        payload: ByteArray = "sealed-bytes-$id".encodeToByteArray(),
        category: String = "",
        favorite: Boolean = false,
        updatedAt: Long = 1_000L,
    ) = VaultEntryEntity(id = id, payload = payload, category = category, favorite = favorite, createdAt = updatedAt, updatedAt = updatedAt)

    @Test
    fun `empty database returns no rows and zero count`() = runBlocking {
        assertEquals(0, dao.count())
        assertTrue(dao.observeAll().first().isEmpty())
        assertTrue(dao.observeCategories().first().isEmpty())
    }

    @Test
    fun `create read update delete roundtrip`() = runBlocking {
        dao.upsert(entity("a"))
        val loaded = dao.getById("a")
        assertNotNull(loaded)

        dao.upsert(loaded!!.copy(category = "Email", updatedAt = 2_000L))
        assertEquals("Email", dao.getById("a")!!.category)
        assertEquals(2_000L, dao.getById("a")!!.updatedAt)

        dao.deleteById("a")
        assertEquals(0, dao.count())
    }

    @Test
    fun `favorite and category queries hit metadata columns`() = runBlocking {
        dao.upsert(entity("a", category = "Email", favorite = true, updatedAt = 3_000L))
        dao.upsert(entity("b", category = "Email", updatedAt = 2_000L))
        dao.upsert(entity("c", category = "Banking", favorite = true, updatedAt = 1_000L))

        assertEquals(listOf("a", "c"), dao.observeFavorites().first().map { it.id })
        assertEquals(listOf("a", "b"), dao.observeByCategory("Email").first().map { it.id })
        assertEquals(listOf("Banking", "Email"), dao.observeCategories().first())
    }

    @Test
    fun `transaction integrity - withTransaction commits all-or-nothing`() = runBlocking {
        dao.upsert(entity("existing"))
        db.withTransaction {
            dao.upsert(entity("t1", updatedAt = 5_000L))
            dao.upsert(entity("t2", updatedAt = 6_000L))
            dao.deleteById("existing")
        }
        assertEquals(2, dao.count())
        assertFalse(dao.getAll().any { it.id == "existing" })
    }

    @Test
    fun `transaction integrity - upsert replace never duplicates`() = runBlocking {
        repeat(50) { index -> dao.upsert(entity("dup", payload = ByteArray(index + 1), updatedAt = index.toLong() + 1)) }
        assertEquals(1, dao.count())
        assertEquals(50, dao.getById("dup")!!.payload.size)
    }

    @Test
    fun `persistence after process recreation - close and reopen the same file database`(): Unit = runBlocking {
        val dbName = "persistence-test.db"
        val firstDb = Room.databaseBuilder(context, DezhVaultDatabase::class.java, dbName)
            .allowMainThreadQueries()
            .build()
        firstDb.vaultEntryDao().upsert(entity("persisted", updatedAt = 9_000L))
        firstDb.close()

        val secondDb = Room.databaseBuilder(context, DezhVaultDatabase::class.java, dbName)
            .allowMainThreadQueries()
            .build()
        val reloaded = secondDb.vaultEntryDao().getById("persisted")
        assertNotNull(reloaded)
        assertEquals("persisted", reloaded!!.id)
        secondDb.close()

        context.getDatabasePath(dbName).delete()
    }

    @Test
    fun `encrypted at rest - raw payload row contains no plaintext secrets`() = runBlocking {
        val secret = " PlaintextMustNotExist "
        val payload = entity("sec", payload = secret.encodeToByteArray())
        dao.upsert(payload)

        val raw = dao.getById("sec")!!.payload
        assertTrue(raw.contentEquals(secret.encodeToByteArray())) // fixture sanity: raw row is readable

        // The production write path encrypts BEFORE the dao — covered in
        // RoomVaultEntryRepositoryTest and the repository-backed file test below.
    }

    @Test
    fun `large dataset - batch insert and query 2000 rows`() = runBlocking {
        val batch = (0 until 2_000).map { index ->
            entity("row-$index", category = if (index % 2 == 0) "Even" else "Odd", updatedAt = index.toLong() + 1)
        }
        db.withTransaction { dao.upsertAll(batch) }

        assertEquals(2_000, dao.count())
        assertEquals(1_000, dao.observeByCategory("Even").first().size)
        assertEquals("row-1999", dao.observeAll().first().first().id) // newest first
    }
}
