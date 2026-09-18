package com.pasargad.dezh.domain

/**
 * Vault list sort options. NAME and CATEGORY sort in-memory (titles are
 * encrypted at rest); the others use plain metadata fields.
 */
enum class VaultSortOption {
    /** Persian-aware lowercase title comparison (in-memory). */
    NAME,

    /** Newest first. */
    CREATED_NEWEST,

    /** Newest first (default). */
    UPDATED_NEWEST,

    /** Category then updated (in-memory for the category label). */
    CATEGORY,

    /** Favorites first, then most recently updated. */
    FAVORITES_FIRST,
}
