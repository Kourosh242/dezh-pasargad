package com.pasargad.dezh.presentation.categories

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.layout.Box
import com.pasargad.dezh.R
import com.pasargad.dezh.ui.components.EmptyState
import com.pasargad.dezh.ui.theme.LocalMotionEnabled

/** Categories overview; tapping a category opens the vault filtered by it. */
@Composable
fun CategoriesScreen(
    viewModel: CategoriesViewModel,
    onCategoryClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(modifier = modifier) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(R.string.categories_title),
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(top = 8.dp),
            )
            when {
                state.isLoading -> LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                state.categories.isEmpty() -> EmptyState(
                    icon = androidx.compose.ui.graphics.vector.ImageVector.vectorResource(R.drawable.ic_label),
                    title = stringResource(R.string.categories_title),
                    body = stringResource(R.string.categories_empty),
                )

                else -> LazyColumn {
                    items(state.categories, key = { it.name }) { category ->
                        Box(Modifier.animateItem()) {
                        ListItem(
                            headlineContent = { Text(category.name) },
                            trailingContent = {
                                Text(
                                    pluralStringResource(
                                        R.plurals.vault_entry_count,
                                        category.count,
                                        category.count,
                                    ),
                                    style = MaterialTheme.typography.labelMedium,
                                )
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onCategoryClick(category.name) },
                        )
                        }
                    }
                }
            }
        }
    }
}
