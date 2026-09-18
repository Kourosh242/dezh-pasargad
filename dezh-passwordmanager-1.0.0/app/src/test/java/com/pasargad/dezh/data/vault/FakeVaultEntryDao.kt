package com.pasargad.dezh.data.vault

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/**
 * In-memory [VaultEntryDao] double for JVM repository tests. Mirrors Room
 * semantics: REPLACE on id conflict, deterministic ordering, reactive flows.
 */
class FakeVaultEntryDao : VaultEntryDao {

    private val store = LinkedHashMap<String, VaultEntryEntity>()
    private val rows = MutableStateFlow(0L)

    private fun publish() {
        rows.value += 1
    }

    private fun snapshot(): List<VaultEntryEntity> =
        store.values.sortedWith(compareByDescending<VaultEntryEntity> { it.updatedAt }.thenBy { it.id })

    override fun observeAll(): Flow<List<VaultEntryEntity>> =
        rows.map { snapshot() }

    override fun observeById(id: String): Flow<VaultEntryEntity?> =
        rows.map { store[id] }

    override suspend fun getById(id: String): VaultEntryEntity? = store[id]

    override fun observeFavorites(): Flow<List<VaultEntryEntity>> =
        rows.map { snapshot().filter { entry -> entry.favorite } }

    override fun observeByCategory(category: String): Flow<List<VaultEntryEntity>> =
        rows.map { snapshot().filter { entry -> entry.category == category } }

    override fun observeCategories(): Flow<List<String>> =
        rows.map { snapshot().map { entry -> entry.category }.filter { it.isNotEmpty() }.distinct().sorted() }

    override suspend fun upsert(entry: VaultEntryEntity) {
        store[entry.id] = entry
        publish()
    }

    override suspend fun upsertAll(entries: List<VaultEntryEntity>) {
        entries.forEach { store[it.id] = it }
        publish()
    }

    override suspend fun deleteById(id: String) {
        store.remove(id)
        publish()
    }

    override suspend fun setFavorite(id: String, favorite: Boolean, updatedAt: Long) {
        val current = store[id] ?: return
        store[id] = current.copy(favorite = favorite, updatedAt = updatedAt)
        publish()
    }

    override suspend fun count(): Int = store.size

    override suspend fun getAll(): List<VaultEntryEntity> = snapshot()

    override suspend fun clearAll() {
        store.clear()
        publish()
    }
}
