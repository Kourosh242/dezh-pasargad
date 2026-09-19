package com.pasargad.dezh.domain

/**
 * Plaintext vault entry — exists ONLY in memory of an unlocked session.
 * Persistence stores this as an encrypted payload (never as plaintext columns).
 */
data class VaultEntry(
    val id: String,
    val title: String,
    val username: String,
    val email: String,
    val password: String,
    val notes: String,
    val category: String,
    val favorite: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
)

/** User-supplied input for create/update (no id, no timestamps). */
data class VaultEntryDraft(
    val title: String,
    val username: String = "",
    val email: String = "",
    val password: String = "",
    val notes: String = "",
    val category: String = "",
    val favorite: Boolean = false,
)

/** Round-trip helper: an existing entry as user-input draft (timestamps excluded). */
fun VaultEntry.toDraft() = VaultEntryDraft(
    title = title,
    username = username,
    email = email,
    password = password,
    notes = notes,
    category = category,
    favorite = favorite,
)
