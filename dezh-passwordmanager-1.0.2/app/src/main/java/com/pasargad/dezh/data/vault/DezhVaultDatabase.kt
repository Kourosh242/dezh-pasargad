package com.pasargad.dezh.data.vault

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration

/**
 * Vault database (Room, schema versioned, schemas exported to app/schemas and
 * committed). Future schema changes MUST ship a [Migration] registered in
 * `DatabaseMigrations.ALL` — destructive fallback is intentionally NOT enabled.
 */
@Database(
    entities = [VaultEntryEntity::class],
    version = DezhVaultDatabase.SCHEMA_VERSION,
    exportSchema = true,
)
abstract class DezhVaultDatabase : RoomDatabase() {

    abstract fun vaultEntryDao(): VaultEntryDao

    companion object {
        const val DATABASE_NAME = "dezh_vault.db"
        const val SCHEMA_VERSION = 1
    }
}

/**
 * Ordered migration history. Strategy:
 *  1. Bump [DezhVaultDatabase.SCHEMA_VERSION].
 *  2. Commit the newly exported schema JSON.
 *  3. Append a `Migration(from, to)` here; Room validates against exported schemas in tests.
 * When Room 3 stable is released the 2.8.x -> 3.x runtime upgrade follows the same
 * versioned-schema path (no user-visible data migration needed unless the engine changes).
 */
object DatabaseMigrations {
    val ALL: Array<Migration> = emptyArray()
}
