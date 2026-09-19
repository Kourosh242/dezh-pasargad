package com.pasargad.dezh.presentation.vault

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.Surface
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.pasargad.dezh.domain.util.JalaliDate
import com.pasargad.dezh.ui.theme.pressScale
import androidx.compose.foundation.interaction.MutableInteractionSource
import com.pasargad.dezh.ui.components.EntryMonogram
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import com.pasargad.dezh.R

/**
 * Entry details. Reveals the password only after an explicit user action and
 * routes edit/delete through the ViewModel. No business logic here.
 */
@Composable
fun EntryDetailScreen(
    viewModel: EntryDetailViewModel,
    onEdit: (String) -> Unit,
    onDeleted: () -> Unit,
    modifier: Modifier = Modifier,
    clipboardTimeoutSeconds: Int = DEFAULT_CLIPBOARD_TIMEOUT,
    confirmBeforeDelete: Boolean = true,
    /** Injectable clock pause for tests; production uses coroutine delay. */
    suspendDelay: suspend (Long) -> Unit = { delay(it) },
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var showDeleteDialog by remember { mutableStateOf(false) }
    val clipboard = LocalClipboardManager.current
    var passwordCopied by remember { mutableStateOf(false) }
    // Monotonic copy counter: EVERY copy re-arms the wipe (a re-copy inside the
    // window cancels the stale timer and starts a fresh full window). Keying on
    // a boolean here was the same class of bug fixed in the generator screen.
    var passwordCopyGeneration by remember { mutableStateOf(0) }

    // Clipboard hygiene: auto-clear after the configured timeout (0 = never).
    LaunchedEffect(passwordCopyGeneration) {
        if (passwordCopyGeneration > 0 && clipboardTimeoutSeconds > 0) {
            suspendDelay(clipboardTimeoutSeconds * MILLIS_PER_SECOND)
            clipboard.setText(AnnotatedString(""))
            passwordCopied = false
        }
    }

    LaunchedEffect(state.deleted) {
        if (state.deleted) onDeleted()
    }

    val entry = state.entry
    Scaffold(modifier = modifier) { innerPadding ->
        if (entry == null) {
            if (!state.deleted) {
                Text(
                    text = stringResource(R.string.vault_loading),
                    modifier = Modifier
                        .padding(innerPadding)
                        .padding(16.dp),
                )
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            DetailCard {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    EntryMonogram(entry.title, size = 52.dp)
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = 12.dp),
                    ) {
                        Text(entry.title, style = MaterialTheme.typography.titleLarge)
                        if (entry.category.isNotBlank()) {
                            Surface(
                                shape = MaterialTheme.shapes.small,
                                color = MaterialTheme.colorScheme.secondaryContainer,
                                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.padding(top = 4.dp),
                            ) {
                                Text(
                                    text = entry.category,
                                    style = MaterialTheme.typography.labelMedium,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                )
                            }
                        }
                    }
                    IconToggleButton(
                        checked = entry.favorite,
                        onCheckedChange = viewModel::onFavoriteToggled,
                    ) {
                        val favoriteDescription = stringResource(R.string.vault_favorite_toggle)
                        Icon(
                            imageVector = if (entry.favorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                            contentDescription = favoriteDescription,
                            tint = if (entry.favorite) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                    }
                }
            }

            DetailCard {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    DetailField(stringResource(R.string.entry_field_username), entry.username)
                    DetailField(stringResource(R.string.entry_field_email), entry.email)
                }
            }

            PasswordSection(
                password = entry.password,
                revealed = state.passwordRevealed,
                onToggleRevealed = viewModel::togglePasswordRevealed,
                onCopy = {
                    clipboard.setText(AnnotatedString(entry.password))
                    passwordCopied = true
                    passwordCopyGeneration += 1
                },
                copied = passwordCopied,
                clipboardTimeoutSeconds = clipboardTimeoutSeconds,
            )

            if (entry.notes.isNotBlank()) {
                DetailCard {
                    Column(modifier = Modifier.fillMaxWidth().padding(14.dp)) {
                        DetailField(stringResource(R.string.entry_field_notes), entry.notes)
                    }
                }
            }

            Text(
                text = stringResource(
                    R.string.entry_timestamps,
                    JalaliDate.formatDateTime(entry.createdAt),
                    JalaliDate.formatDateTime(entry.updatedAt),
                ),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Button(onClick = { onEdit(entry.id) }, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.entry_edit))
                }
                OutlinedButton(
                    onClick = {
                        if (confirmBeforeDelete) showDeleteDialog = true else viewModel.onDeleteConfirmed()
                    },
                    colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error,
                    ),
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(R.string.entry_delete))
                }
            }
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text(stringResource(R.string.entry_delete_confirm_title)) },
            text = { Text(stringResource(R.string.entry_delete_confirm_text)) },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteDialog = false
                    viewModel.onDeleteConfirmed()
                }) {
                    Text(stringResource(R.string.entry_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text(stringResource(R.string.entry_cancel))
                }
            },
        )
    }
}

/** Shared card container for the detail sections (settings-card rhythm). */
@Composable
private fun DetailCard(content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Card(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(14.dp), content = content)
    }
}

/**
 * Password field block: masked/revealed value plus explicit reveal + copy
 * actions with spoken labels (48dp icon targets). Extraction keeps the screen
 * composable complexity low.
 */
@Composable
private fun PasswordSection(
    password: String,
    revealed: Boolean,
    onToggleRevealed: () -> Unit,
    onCopy: () -> Unit,
    copied: Boolean,
    clipboardTimeoutSeconds: Int,
) {
    DetailCard {
        Column {
            Text(
                text = stringResource(R.string.entry_field_password),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = 6.dp),
            ) {
            AnimatedContent(
                targetState = revealed,
                transitionSpec = {
                    (slideInVertically { height -> height / 4 } + fadeIn())
                        .togetherWith(slideOutVertically { height -> -height / 4 } + fadeOut())
                },
                label = "passwordReveal",
            ) { isRevealed ->
                Text(
                    text = if (isRevealed) password else REVEAL_MASK,
                    style = MaterialTheme.typography.titleMedium.copy(fontFamily = FontFamily.Monospace),
                    modifier = Modifier.weight(1f),
                )
            }
            val revealInteraction = remember { MutableInteractionSource() }
            IconButton(
                onClick = onToggleRevealed,
                interactionSource = revealInteraction,
                modifier = Modifier.pressScale(revealInteraction),
            ) {
                val revealDescription = stringResource(
                    if (revealed) R.string.entry_hide_password else R.string.entry_show_password,
                )
                Icon(
                    painter = painterResource(
                        if (revealed) R.drawable.ic_visibility_off else R.drawable.ic_visibility,
                    ),
                    contentDescription = revealDescription,
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
            val copyInteraction = remember { MutableInteractionSource() }
            IconButton(
                onClick = onCopy,
                interactionSource = copyInteraction,
                modifier = Modifier.pressScale(copyInteraction),
            ) {
                val copyDescription = stringResource(R.string.entry_copy_password)
                Icon(
                    painter = painterResource(R.drawable.ic_copy),
                    contentDescription = copyDescription,
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
            }
        }
    }
    if (copied) {
        Text(
            stringResource(
                if (clipboardTimeoutSeconds > 0) {
                    R.string.entry_copied_autoclear
                } else {
                    R.string.generator_copied
                },
            ),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

private const val REVEAL_MASK = "••••••••"

@Composable
private fun DetailField(label: String, value: String) {
    if (value.isBlank()) return
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(text = value, style = MaterialTheme.typography.bodyLarge)
    }
}

private const val DEFAULT_CLIPBOARD_TIMEOUT = 60
private const val MILLIS_PER_SECOND = 1_000L
