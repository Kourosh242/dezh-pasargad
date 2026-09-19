package com.pasargad.dezh.data.vault

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room record of a vault entry.
 *
 * Privacy by design:
 *  - ALL sensitive text (title, username, email, password, notes) lives ONLY
 *    inside [payload] — an AES-256-GCM container (DPVG) encrypted with the
 *    in-memory session DEK. There are no plaintext sensitive columns.
 *  - Non-sensitive metadata only: category (needed for indexed DB filtering),
 *    favorite flag, timestamps.
 *  - No plaintext search index is persisted for password/notes (search decrypts
 *    in memory after unlock).
 */
@Entity(
    tableName = "vault_entries",
    indices = [Index("favorite"), Index("category"), Index("updatedAt")],
)
data class VaultEntryEntity(
    @PrimaryKey val id: String,
    val payload: ByteArray,
    val category: String,
    val favorite: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
)
