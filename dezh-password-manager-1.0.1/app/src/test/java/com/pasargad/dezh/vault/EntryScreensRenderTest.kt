package com.pasargad.dezh.vault

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.pasargad.dezh.domain.CreateEntryUseCase
import com.pasargad.dezh.domain.DeleteEntryUseCase
import com.pasargad.dezh.domain.GetEntryUseCase
import com.pasargad.dezh.domain.ObserveEntryUseCase
import com.pasargad.dezh.generator.PasswordGenerator
import com.pasargad.dezh.domain.PasswordStrengthMeter
import com.pasargad.dezh.domain.ToggleFavoriteUseCase
import com.pasargad.dezh.domain.UpdateEntryUseCase
import com.pasargad.dezh.presentation.vault.EntryDetailScreen
import com.pasargad.dezh.presentation.vault.EntryDetailViewModel
import com.pasargad.dezh.presentation.vault.EntryEditScreen
import com.pasargad.dezh.presentation.vault.EntryEditViewModel
import java.security.SecureRandom
import kotlinx.coroutines.Dispatchers
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Render coverage for the two screens that had none (device crash risk):
 * the add/edit form and the detail view must compose, show their fields and
 * keep basic interactions working.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "fa-rIR-w411dp-h891dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class EntryScreensRenderTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun add_entry_screen_renders_fields_and_validates_blank_title() {
        val viewModel = EntryEditViewModel(
            initialEntryId = null,
            createEntry = CreateEntryUseCase(StubVaultEntryRepository()),
            updateEntry = UpdateEntryUseCase(StubVaultEntryRepository()),
            getEntry = GetEntryUseCase(StubVaultEntryRepository()),
            passwordGenerator = PasswordGenerator(SecureRandom()),
            strengthMeter = PasswordStrengthMeter(),
        )
        composeRule.setContent {
            EntryEditScreen(viewModel = viewModel, onSaved = {}, onCancelled = {})
        }
        composeRule.onNodeWithText("آیتم جدید").assertIsDisplayed()
        composeRule.onNodeWithText("ذخیره").assertIsDisplayed().performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) { viewModel.uiState.value.isTitleInvalid }
    }

    @Test
    fun add_entry_generate_password_fills_the_field() {
        val viewModel = EntryEditViewModel(
            initialEntryId = null,
            createEntry = CreateEntryUseCase(StubVaultEntryRepository()),
            updateEntry = UpdateEntryUseCase(StubVaultEntryRepository()),
            getEntry = GetEntryUseCase(StubVaultEntryRepository()),
            passwordGenerator = PasswordGenerator(SecureRandom()),
            strengthMeter = PasswordStrengthMeter(),
        )
        composeRule.setContent {
            EntryEditScreen(viewModel = viewModel, onSaved = {}, onCancelled = {})
        }
        composeRule.onNodeWithText("تولید").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            viewModel.uiState.value.password.isNotEmpty()
        }
    }

    /** Serves exactly one entry on every read. */
    private class SingleEntryRepository : com.pasargad.dezh.domain.VaultEntryRepository {
        val entry = detailEntry()

        override fun observeEntries(
            query: String,
            category: String?,
            favoritesOnly: Boolean,
            sort: com.pasargad.dezh.domain.VaultSortOption,
        ) = kotlinx.coroutines.flow.flowOf(listOf(entry))

        override fun observeEntry(id: String) = kotlinx.coroutines.flow.flowOf(entry)

        override suspend fun getEntry(id: String) = entry

        override fun observeCategories() = kotlinx.coroutines.flow.flowOf(listOf(entry.category))

        override suspend fun createEntry(draft: com.pasargad.dezh.domain.VaultEntryDraft) = entry.id

        override suspend fun updateEntry(id: String, draft: com.pasargad.dezh.domain.VaultEntryDraft) = Unit

        override suspend fun deleteEntry(id: String) = Unit

        override suspend fun setFavorite(id: String, favorite: Boolean) = Unit

        override suspend fun upsertEntries(entries: List<com.pasargad.dezh.domain.VaultEntry>) = Unit

        override suspend fun replaceAllEntries(entries: List<com.pasargad.dezh.domain.VaultEntry>) = Unit
    }

    @Test
    fun detail_screen_renders_entry_fields() {
        val repository = SingleEntryRepository()
        val viewModel = EntryDetailViewModel(
            entryId = "detail-1",
            observeEntry = ObserveEntryUseCase(repository),
            toggleFavorite = ToggleFavoriteUseCase(repository),
            deleteEntry = DeleteEntryUseCase(repository),
        )
        composeRule.setContent {
            EntryDetailScreen(viewModel = viewModel, onEdit = {}, onDeleted = {})
        }
        composeRule.waitUntil(timeoutMillis = 5_000) {
            viewModel.uiState.value.entry != null
        }
        composeRule.onNodeWithText("بانک ملت").assertIsDisplayed()
    }

    private companion object {
        fun detailEntry(): com.pasargad.dezh.domain.VaultEntry = com.pasargad.dezh.domain.VaultEntry(
        id = "detail-1",
        title = "بانک ملت",
        username = "kourosh",
        email = "",
        password = "pw",
        notes = "",
        category = "بانکداری",
        favorite = false,
        createdAt = 1_700_000_000_000L,
        updatedAt = 1_700_000_000_000L,
        )
    }
}
