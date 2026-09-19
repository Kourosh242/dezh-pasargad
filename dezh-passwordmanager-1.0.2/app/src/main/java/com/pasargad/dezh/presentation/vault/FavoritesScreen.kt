package com.pasargad.dezh.presentation.vault

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/** Favorites — the shared vault content pre-filtered to favorites (no header filters). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FavoritesScreen(
    viewModel: VaultListViewModel,
    widthSizeClass: WindowWidthSizeClass,
    onEntryClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    displayMode: com.pasargad.dezh.settings.DisplayMode = com.pasargad.dezh.settings.DisplayMode.AUTO,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(modifier = modifier) { innerPadding ->
        VaultListContent(
            state = state.copy(favoritesOnly = true),
            widthSizeClass = widthSizeClass,
            displayMode = displayMode,
            showHeaderFilters = false,
            onFavoriteToggled = viewModel::onFavoriteToggled,
            onEntryClick = onEntryClick,
            modifier = Modifier.padding(innerPadding),
        )
    }
}
