package com.pasargad.dezh.data.vault

import kotlinx.serialization.Serializable

/**
 * Encrypted-payload schema (serialized to canonical JSON bytes, then sealed into
 * a DPVG AES-256-GCM container with the session DEK before persistence).
 * This class never touches storage unencrypted.
 */
@Serializable
data class EntrySecrets(
    val title: String,
    val username: String = "",
    val email: String = "",
    val password: String = "",
    val notes: String = "",
)
