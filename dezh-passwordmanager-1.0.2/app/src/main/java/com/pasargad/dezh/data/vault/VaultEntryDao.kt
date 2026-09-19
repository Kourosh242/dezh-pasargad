package com.pasargad.dezh.data.vault

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/**
 * Vault DAO. Deterministic ordering (updatedAt DESC, id ASC) keeps pagination
 * stable for large datasets; filtered reads hit the indexed metadata columns.
 */
/** Data access surface includes the backup restore primitives (clear/replaceAll). */
@Suppress("TooManyFunctions")
@Dao
interface VaultEntryDao {

    @Query("SELECT * FROM vault_entries ORDER BY updatedAt DESC, id ASC")
    fun observeAll(): Flow<List<VaultEntryEntity>>

    @Query("SELECT * FROM vault_entries WHERE id = :id")
    fun observeById(id: String): Flow<VaultEntryEntity?>

    @Query("SELECT * FROM vault_entries WHERE id = :id")
    suspend fun getById(id: String): VaultEntryEntity?

    @Query("SELECT * FROM vault_entries WHERE favorite = 1 ORDER BY updatedAt DESC, id ASC")
    fun observeFavorites(): Flow<List<VaultEntryEntity>>

    @Query("SELECT * FROM vault_entries WHERE category = :category ORDER BY updatedAt DESC, id ASC")
    fun observeByCategory(category: String): Flow<List<VaultEntryEntity>>

    @Query("SELECT DISTINCT category FROM vault_entries WHERE category != '' ORDER BY category COLLATE NOCASE ASC")
    fun observeCategories(): Flow<List<String>>

    @Upsert
    suspend fun upsert(entry: VaultEntryEntity)

    @Upsert
    suspend fun upsertAll(entries: List<VaultEntryEntity>)

    @Query("DELETE FROM vault_entries WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("UPDATE vault_entries SET favorite = :favorite, updatedAt = :updatedAt WHERE id = :id")
    suspend fun setFavorite(id: String, favorite: Boolean, updatedAt: Long)

    @Query("SELECT COUNT(*) FROM vault_entries")
    suspend fun count(): Int

    @Query("SELECT * FROM vault_entries")
    suspend fun getAll(): List<VaultEntryEntity>

    @Query("DELETE FROM vault_entries")
    suspend fun clearAll()

    /**
     * Atomic whole-vault replacement used by the destructive restore path:
     * clear + import must never leave a half-wiped vault behind.
     */
    @Transaction
    suspend fun replaceAll(entries: List<VaultEntryEntity>) {
        clearAll()
        upsertAll(entries)
    }
}
