package com.pasargad.dezh.data.vault

import com.pasargad.dezh.cryptography.EncryptionContainer
import com.pasargad.dezh.domain.VaultEntry
import com.pasargad.dezh.domain.toDraft
import com.pasargad.dezh.domain.VaultEntryDraft
import com.pasargad.dezh.domain.VaultEntryNotFoundException
import com.pasargad.dezh.domain.VaultSortOption
import com.pasargad.dezh.domain.VaultEntryRepository
import com.pasargad.dezh.security.VaultSession
import java.util.UUID
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

/**
 * Room-backed vault repository. Encryption happens HERE (data layer), so no
 * Composable or ViewModel ever sees the database or ciphertext handling:
 *
 *   Presentation → UseCase → [RoomVaultEntryRepository] → VaultEntryDao → Room
 *
 * Sensitive fields are sealed into an encrypted payload per record; only
 * non-sensitive metadata (category/favorite/timestamps) is stored as columns.
 * Text search decrypts in memory (post-unlock) — no persistent plaintext index.
 */
@Suppress("TooManyFunctions") // full vault contract incl. backup import paths
class RoomVaultEntryRepository(
    private val dao: VaultEntryDao,
    private val session: VaultSession,
    private val json: Json,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val clock: () -> Long = System::currentTimeMillis,
    private val idGenerator: () -> String = { UUID.randomUUID().toString() },
) : VaultEntryRepository {

    override fun observeEntries(
        query: String,
        category: String?,
        favoritesOnly: Boolean,
        sort: VaultSortOption,
    ): Flow<List<VaultEntry>> = when {
        category != null -> dao.observeByCategory(category)
        favoritesOnly -> dao.observeFavorites()
        else -> dao.observeAll()
    }
        .map { rows -> sortEntries(filterDecrypted(rows, query), sort) }
        .flowOn(ioDispatcher)

    override fun observeEntry(id: String): Flow<VaultEntry?> =
        dao.observeById(id).map { row -> row?.let { decryptRow(it) } }.flowOn(ioDispatcher)

    override suspend fun getEntry(id: String): VaultEntry? = withContext(ioDispatcher) {
        dao.getById(id)?.let { decryptRow(it) }
    }

    override fun observeCategories(): Flow<List<String>> = dao.observeCategories().flowOn(ioDispatcher)

    override suspend fun createEntry(draft: VaultEntryDraft): String = withContext(ioDispatcher) {
        val now = clock()
        val id = idGenerator()
        dao.upsert(
            VaultEntryEntity(
                id = id,
                payload = sealSecrets(draft),
                category = draft.category.trim(),
                favorite = draft.favorite,
                createdAt = now,
                updatedAt = now,
            ),
        )
        id
    }

    override suspend fun updateEntry(id: String, draft: VaultEntryDraft) = withContext(ioDispatcher) {
        val existing = dao.getById(id) ?: throw VaultEntryNotFoundException()
        dao.upsert(
            existing.copy(
                payload = sealSecrets(draft),
                category = draft.category.trim(),
                favorite = draft.favorite,
                updatedAt = clock(),
            ),
        )
    }

    override suspend fun deleteEntry(id: String) = withContext(ioDispatcher) {
        if (dao.getById(id) == null) throw VaultEntryNotFoundException()
        dao.deleteById(id)
    }

    override suspend fun upsertEntries(entries: List<VaultEntry>) = withContext(ioDispatcher) {
        entries.forEach { entry -> dao.upsert(sealedEntity(entry)) }
    }

    override suspend fun replaceAllEntries(entries: List<VaultEntry>) = withContext(ioDispatcher) {
        dao.replaceAll(entries.map { entry -> sealedEntity(entry) })
    }

    override suspend fun setFavorite(id: String, favorite: Boolean) = withContext(ioDispatcher) {
        if (dao.getById(id) == null) throw VaultEntryNotFoundException()
        dao.setFavorite(id, favorite, clock())
    }

    /** Re-seals an imported entry with the CURRENT session key, preserving id/timestamps. */
    private fun sealedEntity(entry: VaultEntry): VaultEntryEntity = VaultEntryEntity(
        id = entry.id,
        payload = sealSecrets(entry.toDraft()),
        category = entry.category,
        favorite = entry.favorite,
        createdAt = entry.createdAt,
        updatedAt = entry.updatedAt,
    )

    // --- payload sealing ----------------------------------------------------------

    /** Plaintext → JSON bytes → DPVG encrypted container (session DEK). */
    private fun sealSecrets(draft: VaultEntryDraft): ByteArray {
        val secrets = EntrySecrets(
            title = draft.title.trim(),
            username = draft.username.trim(),
            email = draft.email.trim(),
            password = draft.password,
            notes = draft.notes,
        )
        val plaintext = json.encodeToString(EntrySecrets.serializer(), secrets).encodeToByteArray()
        return session.encrypt(plaintext).encode()
    }

    /** DPVG container → plaintext → [VaultEntry]; plaintext lives only in memory. */
    private fun decryptRow(row: VaultEntryEntity): VaultEntry {
        val container = EncryptionContainer.decode(row.payload)
        val plaintext = session.decrypt(container)
        val secrets = json.decodeFromString(EntrySecrets.serializer(), plaintext.decodeToString())
        return VaultEntry(
            id = row.id,
            title = secrets.title,
            username = secrets.username,
            email = secrets.email,
            password = secrets.password,
            notes = secrets.notes,
            category = row.category,
            favorite = row.favorite,
            createdAt = row.createdAt,
            updatedAt = row.updatedAt,
        )
    }

    /** In-memory sort over decrypted content (titles are encrypted at rest). */
    private fun sortEntries(entries: List<VaultEntry>, sort: VaultSortOption): List<VaultEntry> = when (sort) {
        VaultSortOption.NAME -> entries.sortedBy { it.title.lowercase() }
        VaultSortOption.CREATED_NEWEST -> entries.sortedByDescending { it.createdAt }
        VaultSortOption.UPDATED_NEWEST -> entries.sortedByDescending { it.updatedAt }
        VaultSortOption.CATEGORY -> entries.sortedWith(
            compareBy<VaultEntry> { it.category.lowercase() }.thenByDescending { it.updatedAt },
        )
        VaultSortOption.FAVORITES_FIRST -> entries.sortedWith(
            compareByDescending<VaultEntry> { it.favorite }.thenByDescending { it.updatedAt },
        )
    }

    /** In-memory search over decrypted content (title/username/email/notes). */
    private fun filterDecrypted(rows: List<VaultEntryEntity>, query: String): List<VaultEntry> {
        val needle = query.trim().lowercase()
        val entries = rows.map { decryptRow(it) }
        if (needle.isEmpty()) return entries
        return entries.filter { entry ->
            entry.title.lowercase().contains(needle) ||
                entry.username.lowercase().contains(needle) ||
                entry.email.lowercase().contains(needle) ||
                entry.notes.lowercase().contains(needle)
        }
    }
}
