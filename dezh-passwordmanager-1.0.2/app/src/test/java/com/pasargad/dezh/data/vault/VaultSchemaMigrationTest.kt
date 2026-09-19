package com.pasargad.dezh.data.vault

import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Schema/migration strategy validation (Robolectric).
 *
 * Room detects schema drift via the identity hash stored in `room_master_table`;
 * this test proves that the runtime schema of [DezhVaultDatabase] v1 is exactly
 * the committed export (app/schemas/.../1.json) and that the exported column set
 * accepts the documented insert path. Future versions must:
 *  1. bump SCHEMA_VERSION,
 *  2. commit the newly exported schema JSON,
 *  3. add a Migration to DatabaseMigrations.ALL,
 *  4. extend this test with a vN -> vN+1 migration step.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class VaultSchemaMigrationTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun `runtime schema identity hash matches the committed schema export`() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, DezhVaultDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        db.openHelper.writableDatabase // force schema creation
        try {
            val runtimeHash = db.openHelper.writableDatabase
                .query("SELECT identity_hash FROM room_master_table")
                .use { cursor ->
                    cursor.moveToFirst()
                    cursor.getString(0)
                }
            assertEquals(exportedIdentityHash(), runtimeHash)
        } finally {
            db.close()
        }
    }

    @Test
    fun `schema v1 provides the documented columns and indexes`() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, DezhVaultDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            val database = db.openHelper.writableDatabase

            val columns = database.query("PRAGMA table_info(vault_entries)").use { cursor ->
                buildList {
                    while (cursor.moveToNext()) add(cursor.getString(cursor.getColumnIndexOrThrow("name")))
                }
            }
            assertEquals(
                listOf("id", "payload", "category", "favorite", "createdAt", "updatedAt"),
                columns,
            )

            val indexes = database.query("PRAGMA index_list(vault_entries)").use { cursor ->
                buildList {
                    while (cursor.moveToNext()) add(cursor.getString(cursor.getColumnIndexOrThrow("name")))
                }
            }
            assertTrue("index_vault_entries_favorite" in indexes)
            assertTrue("index_vault_entries_category" in indexes)
            assertTrue("index_vault_entries_updatedAt" in indexes)
        } finally {
            db.close()
        }
    }

    /** Reads the identityHash recorded in the committed schema export. */
    private fun exportedIdentityHash(): String {
        val schemaFile = File("schemas/com.pasargad.dezh.data.vault.DezhVaultDatabase/1.json")
        check(schemaFile.isFile) { "Committed schema export is missing: ${schemaFile.path}" }
        val match = Regex("\"identityHash\"\\s*:\\s*\"([^\"]+)\"").find(schemaFile.readText())
        return requireNotNull(match?.groupValues?.get(1)) { "identityHash not found in schema export" }
    }
}
