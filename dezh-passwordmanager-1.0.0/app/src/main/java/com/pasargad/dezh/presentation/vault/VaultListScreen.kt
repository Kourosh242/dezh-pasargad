package com.pasargad.dezh.presentation.vault

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pasargad.dezh.R
import com.pasargad.dezh.domain.VaultEntry
import com.pasargad.dezh.domain.VaultSortOption
import com.pasargad.dezh.settings.DisplayMode
import com.pasargad.dezh.settings.ThemeMode
import com.pasargad.dezh.ui.components.EmptyState
import com.pasargad.dezh.ui.components.EntryMonogram
import com.pasargad.dezh.ui.components.DezhBottomNav
import com.pasargad.dezh.ui.components.DezhTab
import com.pasargad.dezh.ui.components.StaggerIn
import com.pasargad.dezh.ui.components.rememberCategoryAvatarStyle
import com.pasargad.dezh.ui.theme.DezhMotion
import com.pasargad.dezh.ui.theme.pressScale
import com.pasargad.dezh.ui.theme.LocalMotionEnabled

/**
 * Shared vault list/search/favorites content.
 * Adaptive: compact = single column, expanded (tablet/window) = two columns.
 * Rows use start/end semantics, so they mirror automatically in RTL.
 */
@Suppress("CyclomaticComplexMethod") // loading/empty/list/grid/adaptive states in one place
@Composable
fun VaultListContent(
    state: VaultListUiState,
    widthSizeClass: WindowWidthSizeClass,
    modifier: Modifier = Modifier,
    displayMode: DisplayMode = DisplayMode.AUTO,
    showHeaderFilters: Boolean = true,
    onQueryChanged: (String) -> Unit = {},
    onCategorySelected: (String?) -> Unit = {},
    onFavoritesFilterChanged: (Boolean) -> Unit = {},
    onSortSelected: (VaultSortOption) -> Unit = {},
    onAddEntryClick: (() -> Unit)? = null,

    /** Kept for API stability; theme switching moved to the vault top bar overflow. */
    @Suppress("unused") onThemeCycled: () -> Unit = {},
    onFavoriteToggled: (String, Boolean) -> Unit,
    onEntryClick: (String) -> Unit,
) {
    var sortMenuOpen by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        MaterialTheme.colorScheme.background,
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.14f),
                    ),
                ),
            )
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val countDescription = stringResource(R.string.vault_entries_semantics)
            Text(
                text = pluralStringResource(R.plurals.vault_entry_count, state.entries.size, state.entries.size),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .semantics { contentDescription = countDescription },
            )
            TextButton(onClick = { sortMenuOpen = true }) {
                Icon(
                    painter = androidx.compose.ui.res.painterResource(R.drawable.ic_sort),
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    text = stringResource(sortLabel(state.sort)),
                    modifier = Modifier.padding(start = 6.dp),
                )
            }
            DropdownMenu(expanded = sortMenuOpen, onDismissRequest = { sortMenuOpen = false }) {
                VaultSortOption.entries.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(stringResource(sortLabel(option))) },
                        onClick = {
                            sortMenuOpen = false
                            onSortSelected(option)
                        },
                    )
                }
            }
        }

        if (showHeaderFilters) {
            TextField(
                value = state.query,
                onValueChange = onQueryChanged,
                placeholder = { Text(stringResource(R.string.vault_search_hint)) },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Filled.Search,
                        contentDescription = null, // the placeholder carries the meaning
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
                singleLine = true,
                shape = MaterialTheme.shapes.large,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
                    unfocusedContainerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
                    disabledContainerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
                    focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                    unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                    disabledIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                ),
                modifier = Modifier.fillMaxWidth(),
            )
            // Horizontal chip lane — favorites + categories scroll, never overflow.
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // "All" leads and is the default; favorites trails with a star.
                item {
                    CategoryChip(
                        label = stringResource(R.string.vault_category_all),
                        selected = state.selectedCategory == null,
                        onClick = { onCategorySelected(null) },
                    )
                }
                items(state.categories.size) { index ->
                    val category = state.categories[index]
                    CategoryChip(
                        label = category,
                        selected = state.selectedCategory == category,
                        onClick = { onCategorySelected(if (state.selectedCategory == category) null else category) },
                    )
                }
                item {
                    FilterChip(
                        selected = state.favoritesOnly,
                        onClick = { onFavoritesFilterChanged(!state.favoritesOnly) },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Filled.Star,
                                contentDescription = null, // the label carries the meaning
                                tint = if (state.favoritesOnly) {
                                    MaterialTheme.colorScheme.onPrimary
                                } else {
                                    MaterialTheme.colorScheme.primary
                                },
                                modifier = Modifier.size(18.dp),
                            )
                        },
                        label = { Text(stringResource(R.string.vault_filter_favorites)) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                        ),
                    )
                }
            }
        }

        when {
            state.isLoading -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                Text(
                    text = stringResource(R.string.vault_loading),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp),
                )
            }

            state.entries.isEmpty() -> when {
                state.favoritesOnly -> EmptyState(
                    icon = Icons.Filled.Star,
                    title = stringResource(R.string.favorites_empty_title),
                    body = stringResource(R.string.favorites_empty_body),
                )

                state.query.isNotBlank() -> EmptyState(
                    icon = Icons.Filled.Search,
                    title = stringResource(R.string.search_empty_title),
                    body = stringResource(R.string.search_empty_body),
                )

                else -> EmptyState(
                    icon = ImageVector.vectorResource(R.drawable.ic_shield),
                    title = stringResource(R.string.vault_empty_title),
                    body = stringResource(R.string.vault_empty_body),
                    actionLabel = onAddEntryClick?.let { stringResource(R.string.vault_empty_cta) },
                    onAction = onAddEntryClick,
                )
            }

            displayMode == DisplayMode.GRID || (displayMode == DisplayMode.AUTO && widthSizeClass >= WindowWidthSizeClass.Medium) ->
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    gridItems(state.entries, key = { it.id }) { entry ->
                        Box(Modifier.animateItem()) {
                            StaggerIn(index = state.entries.indexOf(entry)) {
                                EntryRow(entry, onEntryClick, onFavoriteToggled)
                            }
                        }
                    }
                }

            else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                itemsIndexed(state.entries, key = { _, entry -> entry.id }) { index, entry ->
                    Box(Modifier.animateItem()) {
                        StaggerIn(index = index) {
                            EntryRow(entry, onEntryClick, onFavoriteToggled)
                        }
                    }
                }
            }
        }
    }
}

/**
 * Main vault screen. Navigation actions live in a Material 3 top app bar
 * (search + overflow menu) so the header never overflows on compact screens.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Suppress("LongParameterList") // viewModel + window class + 6 navigation callbacks + slot modifier
@Composable
fun VaultListScreen(
    viewModel: VaultListViewModel,
    widthSizeClass: WindowWidthSizeClass,
    modifier: Modifier = Modifier,
    displayMode: DisplayMode = DisplayMode.AUTO,
    onEntryClick: (String) -> Unit,
    onAddClick: () -> Unit,
    onGeneratorClick: () -> Unit,
    onSearchClick: () -> Unit,
    onCategoriesClick: () -> Unit,
    onFavoritesClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onCheckForUpdatesClick: () -> Unit = {},
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
    val searchDescription = stringResource(R.string.vault_nav_search)
    val moreDescription = stringResource(R.string.vault_more_actions)
    val addDescription = stringResource(R.string.vault_add_entry)
    var overflowOpen by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.vault_title)) },
                actions = {
                    IconButton(onClick = onSearchClick) {
                        Icon(
                            Icons.Filled.Search,
                            contentDescription = searchDescription,
                        )
                    }
                    IconButton(onClick = { overflowOpen = true }) {
                        Icon(
                            Icons.Filled.MoreVert,
                            contentDescription = moreDescription,
                        )
                    }
                    DropdownMenu(expanded = overflowOpen, onDismissRequest = { overflowOpen = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.categories_title)) },
                            onClick = {
                                overflowOpen = false
                                onCategoriesClick()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.favorites_title)) },
                            onClick = {
                                overflowOpen = false
                                onFavoritesClick()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.menu_check_updates)) },
                            onClick = {
                                overflowOpen = false
                                onCheckForUpdatesClick()
                            },
                        )
                        val themeLabel = stringResource(
                            when (themeMode) {
                                ThemeMode.SYSTEM -> R.string.theme_system
                                ThemeMode.LIGHT -> R.string.theme_light
                                ThemeMode.DARK -> R.string.theme_dark
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(themeLabel) },
                            onClick = {
                                overflowOpen = false
                                viewModel.onThemeCycled()
                            },
                        )
                    }
                },
            )
        },
        bottomBar = {
            DezhBottomNav(
                current = DezhTab.VAULT,
                onVault = {},
                onGenerator = onGeneratorClick,
                onSettings = onSettingsClick,
            )
        },
        floatingActionButton = {
            val fabInteraction = remember { MutableInteractionSource() }
            Box(
                modifier = Modifier
                    .padding(10.dp)
                    .size(60.dp)
                    .shadow(6.dp, CircleShape)
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(
                            listOf(
                                MaterialTheme.colorScheme.primary,
                                MaterialTheme.colorScheme.tertiary,
                            ),
                        ),
                    )
                    .clickable(interactionSource = fabInteraction, indication = null) { onAddClick() }
                    .pressScale(fabInteraction)
                    .semantics { contentDescription = addDescription },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.Add,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(26.dp),
                )
            }
        },
    ) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
            VaultListContent(
                state = state,
                widthSizeClass = widthSizeClass,
                displayMode = displayMode,
                onQueryChanged = viewModel::onQueryChanged,
                onCategorySelected = viewModel::onCategorySelected,
                onFavoritesFilterChanged = viewModel::onFavoritesFilterChanged,
                onSortSelected = viewModel::onSortSelected,
                onThemeCycled = viewModel::onThemeCycled,
                onFavoriteToggled = viewModel::onFavoriteToggled,
                onEntryClick = onEntryClick,
                onAddEntryClick = onAddClick,
            )
        }
    }
}

@Composable
private fun CategoryChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.primary,
            selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
        ),
    )
}

/**
 * One vault row = a premium card. Whole card is the touch target (48dp+),
 * a category-tinted avatar gives each record an identity, and the heart
 * bounces on toggle (bouncy spring per M3 Expressive). RTL-safe.
 */
@Composable
private fun EntryRow(
    entry: VaultEntry,
    onEntryClick: (String) -> Unit,
    onFavoriteToggled: (String, Boolean) -> Unit,
) {
    val favoriteDescription = stringResource(R.string.vault_favorite_toggle)
    val motionEnabled = LocalMotionEnabled.current
    val heartScale by animateFloatAsState(
        targetValue = if (entry.favorite) 1f else 0.9f,
        animationSpec = if (motionEnabled) DezhMotion.bouncy() else tween(0),
        label = "favoriteHeart",
    )
    val interaction = remember { MutableInteractionSource() }
    val avatar = rememberCategoryAvatarStyle(entry.category.ifBlank { entry.title })
    Card(
        onClick = { onEntryClick(entry.id) },
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        interactionSource = interaction,
        modifier = Modifier
            .fillMaxWidth()
            .pressScale(interaction),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(avatar.container)
                    .then(
                        if (entry.favorite) {
                            Modifier.border(2.dp, MaterialTheme.colorScheme.primary, CircleShape)
                        } else {
                            Modifier
                        },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                if (avatar.icon != null) {
                    androidx.compose.material3.Icon(
                        imageVector = avatar.icon,
                        contentDescription = null,
                        tint = avatar.onContainer,
                        modifier = Modifier.size(20.dp),
                    )
                } else {
                    Text(
                        text = entry.title.trim().take(1).uppercase(),
                        style = MaterialTheme.typography.titleMedium,
                        color = avatar.onContainer,
                    )
                }
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            ) {
                Text(
                    entry.title,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = listOf(
                        entry.username,
                        com.pasargad.dezh.domain.util.JalaliDate.formatDateTime(entry.updatedAt),
                    )
                        .filter(String::isNotBlank)
                        .joinToString(separator = " · "),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconToggleButton(
                checked = entry.favorite,
                onCheckedChange = { onFavoriteToggled(entry.id, !entry.favorite) },
                modifier = Modifier.semantics { contentDescription = favoriteDescription },
            ) {
                Icon(
                    imageVector = if (entry.favorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                    contentDescription = null,
                    tint = if (entry.favorite) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier
                        .size(22.dp)
                        .alpha(heartScale),
                )
            }
        }
    }
}

private fun sortLabel(option: VaultSortOption): Int = when (option) {
    VaultSortOption.NAME -> R.string.sort_name
    VaultSortOption.CREATED_NEWEST -> R.string.sort_created
    VaultSortOption.UPDATED_NEWEST -> R.string.sort_updated
    VaultSortOption.CATEGORY -> R.string.sort_category
    VaultSortOption.FAVORITES_FIRST -> R.string.sort_favorites
}
