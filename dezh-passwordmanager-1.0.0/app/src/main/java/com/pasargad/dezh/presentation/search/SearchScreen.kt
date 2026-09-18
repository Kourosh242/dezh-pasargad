package com.pasargad.dezh.presentation.search

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SearchBar
import androidx.compose.material3.Text
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pasargad.dezh.R
import com.pasargad.dezh.presentation.vault.VaultListContent

/** Offline search over the decrypted-in-memory vault content (M3 SearchBar). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    viewModel: SearchViewModel,
    widthSizeClass: WindowWidthSizeClass,
    onEntryClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(modifier = modifier) { innerPadding ->
        SearchBar(
            query = state.query,
            onQueryChange = viewModel::onQueryChanged,
            onSearch = {},
            active = true,
            onActiveChange = {},
            placeholder = { Text(stringResource(R.string.vault_search_hint)) },
            modifier = Modifier.padding(innerPadding),
        ) {
            VaultListContent(
                state = state,
                widthSizeClass = widthSizeClass,
                showHeaderFilters = false,
                onFavoriteToggled = viewModel::onFavoriteToggled,
                onEntryClick = onEntryClick,
            )
        }
    }
}
