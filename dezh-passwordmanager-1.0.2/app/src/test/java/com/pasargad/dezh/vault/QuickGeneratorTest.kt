package com.pasargad.dezh.vault

import com.pasargad.dezh.domain.CreateEntryUseCase
import com.pasargad.dezh.domain.GetEntryUseCase
import com.pasargad.dezh.generator.PasswordGenerator
import com.pasargad.dezh.domain.PasswordStrengthMeter
import com.pasargad.dezh.domain.UpdateEntryUseCase
import com.pasargad.dezh.domain.VaultEntry
import com.pasargad.dezh.domain.VaultEntryDraft
import com.pasargad.dezh.domain.VaultEntryRepository
import com.pasargad.dezh.domain.VaultSortOption
import com.pasargad.dezh.presentation.vault.EntryEditViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.security.SecureRandom

/** 1.0.2: quick generator sheet — generate, tune, adopt (Proton-Pass flow). */
@OptIn(ExperimentalCoroutinesApi::class)
class QuickGeneratorTest {

    private val dispatcher = UnconfinedTestDispatcher()

    private class EmptyRepository : VaultEntryRepository {
        override fun observeEntries(
            query: String,
            category: String?,
            favoritesOnly: Boolean,
            sort: VaultSortOption,
        ): Flow<List<VaultEntry>> = flowOf(emptyList())

        override fun observeEntry(id: String): Flow<VaultEntry?> = flowOf(null)
        override suspend fun getEntry(id: String): VaultEntry? = null
        override fun observeCategories(): Flow<List<String>> = flowOf(emptyList())
        override suspend fun createEntry(draft: VaultEntryDraft): String = "new"
        override suspend fun updateEntry(id: String, draft: VaultEntryDraft) = Unit
        override suspend fun deleteEntry(id: String) = Unit
        override suspend fun setFavorite(id: String, favorite: Boolean) = Unit
        override suspend fun upsertEntries(entries: List<VaultEntry>) = Unit
        override suspend fun replaceAllEntries(entries: List<VaultEntry>) = Unit
    }

    private fun viewModel() = EntryEditViewModel(
        initialEntryId = null,
        createEntry = CreateEntryUseCase(EmptyRepository()),
        updateEntry = UpdateEntryUseCase(EmptyRepository()),
        getEntry = GetEntryUseCase(EmptyRepository()),
        passwordGenerator = PasswordGenerator(SecureRandom()),
        strengthMeter = PasswordStrengthMeter(),
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun opening_sheet_generates_password() {
        val vm = viewModel()
        vm.onQuickSheetOpened()
        val quick = vm.quickGenerator.value
        assertTrue(quick.password.isNotEmpty())
        assertEquals(PasswordGenerator.Options.DEFAULT_LENGTH, quick.length)
    }

    @Test
    fun length_change_regenerates_with_new_length() {
        val vm = viewModel()
        vm.onQuickSheetOpened()
        vm.onQuickOptionsChanged(length = 24)
        assertEquals(24, vm.quickGenerator.value.length)
        assertEquals(24, vm.quickGenerator.value.password.length)
    }

    @Test
    fun adopt_fills_form_field_only_on_use() {
        val vm = viewModel()
        val before = vm.uiState.value.password
        vm.onQuickSheetOpened()
        assertNotEquals(before, vm.quickGenerator.value.password)
        // before adopt, the form field is untouched
        assertEquals(before, vm.uiState.value.password)
        vm.onQuickUse()
        assertEquals(vm.quickGenerator.value.password, vm.uiState.value.password)
    }

    @Test
    fun disabling_all_classes_sets_error_state() {
        val vm = viewModel()
        vm.onQuickSheetOpened()
        vm.onQuickOptionsChanged(uppercase = false, lowercase = false, digits = false, symbols = false)
        assertTrue(vm.quickGenerator.value.error)
        assertTrue(vm.quickGenerator.value.password.isEmpty())
    }
}
