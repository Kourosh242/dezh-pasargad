package com.pasargad.dezh.domain

import kotlinx.coroutines.flow.Flow

/**
 * Vault CRUD contract. All read paths return decrypted [VaultEntry] values that
 * exist only in memory; the repository performs encryption/decryption on top of
 * the encrypted record payloads. There is deliberately no persistent plaintext
 * search index for passwords or notes — text search happens in memory after unlock.
 */
interface VaultEntryRepository {

    /**
     * Observes entries (newest first), optionally filtered.
     * [query] is matched in-memory against title/username/email/notes (case-insensitive).
     * [category] and [favoritesOnly] are applied at the database level (metadata columns).
     */
    fun observeEntries(
        query: String = "",
        category: String? = null,
        favoritesOnly: Boolean = false,
        sort: VaultSortOption = VaultSortOption.UPDATED_NEWEST,
    ): Flow<List<VaultEntry>>

    fun observeEntry(id: String): Flow<VaultEntry?>

    suspend fun getEntry(id: String): VaultEntry?

    fun observeCategories(): Flow<List<String>>

    /** @return the generated id of the new entry. */
    suspend fun createEntry(draft: VaultEntryDraft): String

    /** @throws VaultEntryNotFoundException if the entry does not exist. */
    suspend fun updateEntry(id: String, draft: VaultEntryDraft)

    /** @throws VaultEntryNotFoundException if the entry does not exist. */
    suspend fun deleteEntry(id: String)

    suspend fun setFavorite(id: String, favorite: Boolean)

    /**
     * Import support (encrypted backup restore): inserts or updates entries with
     * their ORIGINAL ids and timestamps, re-sealing the secrets with the current
     * session key. Used by merge (subset) and replace (whole vault) restores.
     */
    suspend fun upsertEntries(entries: List<VaultEntry>)

    /** Destructive restore: atomically replaces the whole vault with [entries]. */
    suspend fun replaceAllEntries(entries: List<VaultEntry>)
}

/** Static message — no entry content is ever included. */
class VaultEntryNotFoundException : Exception("Vault entry does not exist")
