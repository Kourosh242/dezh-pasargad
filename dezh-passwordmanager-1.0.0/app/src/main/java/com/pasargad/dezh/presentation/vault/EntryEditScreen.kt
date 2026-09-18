package com.pasargad.dezh.presentation.vault

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.TextButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pasargad.dezh.R

/**
 * Add/Edit entry form. All writes go through [EntryEditViewModel] — the screen
 * holds no business logic and never touches the database.
 */
@Composable
fun EntryEditScreen(
    viewModel: EntryEditViewModel,
    onSaved: () -> Unit,
    onCancelled: () -> Unit,
    modifier: Modifier = Modifier,
    confirmBeforeDiscard: Boolean = true,
) {
    var passwordVisible by remember { mutableStateOf(false) }
    var showDiscardDialog by remember { mutableStateOf(false) }
    if (showDiscardDialog) {
        AlertDialog(
            onDismissRequest = { showDiscardDialog = false },
            title = { Text(stringResource(R.string.entry_discard_confirm_title)) },
            text = { Text(stringResource(R.string.entry_discard_confirm_text)) },
            confirmButton = {
                TextButton(onClick = { showDiscardDialog = false; onCancelled() }) {
                    Text(stringResource(R.string.entry_discard_confirm_action))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDiscardDialog = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(state.saved) {
        if (state.saved) onSaved()
    }

    Scaffold(modifier = modifier) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(if (state.isEdit) R.string.entry_edit_title else R.string.entry_add_title),
                style = MaterialTheme.typography.headlineSmall,
            )

            OutlinedTextField(
                value = state.title,
                onValueChange = viewModel::onTitleChanged,
                label = { Text(stringResource(R.string.entry_field_title)) },
                singleLine = true,
                isError = state.isTitleInvalid,
                supportingText = {
                    if (state.isTitleInvalid) Text(stringResource(R.string.entry_title_required))
                },
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = state.username,
                onValueChange = viewModel::onUsernameChanged,
                label = { Text(stringResource(R.string.entry_field_username)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = state.email,
                onValueChange = viewModel::onEmailChanged,
                label = { Text(stringResource(R.string.entry_field_email)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = state.password,
                onValueChange = viewModel::onPasswordChanged,
                label = { Text(stringResource(R.string.entry_field_password)) },
                singleLine = true,
                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                trailingIcon = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val toggleDescription = stringResource(
                            if (passwordVisible) R.string.entry_hide_password else R.string.entry_show_password,
                        )
                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                            Icon(
                                painter = painterResource(
                                    if (passwordVisible) R.drawable.ic_visibility_off else R.drawable.ic_visibility,
                                ),
                                contentDescription = toggleDescription,
                            )
                        }
                        TextButton(onClick = viewModel::onGeneratePassword) {
                            Text(stringResource(R.string.entry_generate))
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )
            if (state.password.isNotEmpty()) {
                Column {
                    LinearProgressIndicator(
                        progress = { state.meterScore / 100f },
                        color = when (state.meterBand) {
                            MeterBand.STRONG -> MaterialTheme.colorScheme.primary
                            MeterBand.FAIR -> MaterialTheme.colorScheme.tertiary
                            MeterBand.WEAK -> MaterialTheme.colorScheme.error
                        },
                        trackColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        // NOTE: the band label must be resolved to a string
                        // BEFORE substitution — passing the Int resource id as
                        // %1$s printed a raw resource number (reported bug).
                        text = stringResource(R.string.entry_meter_label, stringResource(meterLabel(state.meterBand))),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            OutlinedTextField(
                value = state.notes,
                onValueChange = viewModel::onNotesChanged,
                label = { Text(stringResource(R.string.entry_field_notes)) },
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = state.category,
                onValueChange = viewModel::onCategoryChanged,
                label = { Text(stringResource(R.string.entry_field_category)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = state.favorite, onCheckedChange = viewModel::onFavoriteChanged)
                Text(stringResource(R.string.entry_field_favorite))
            }

            if (state.error != null) {
                Text(
                    text = stringResource(R.string.entry_save_failed),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Button(
                    onClick = viewModel::save,
                    enabled = !state.isSaving,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(R.string.entry_save))
                }
                OutlinedButton(
                    onClick = { if (confirmBeforeDiscard) showDiscardDialog = true else onCancelled() },
                    enabled = !state.isSaving,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(R.string.entry_cancel))
                }
            }
        }
    }
}

private fun meterLabel(band: MeterBand): Int = when (band) {
    MeterBand.WEAK -> R.string.strength_weak
    MeterBand.FAIR -> R.string.strength_fair
    MeterBand.STRONG -> R.string.strength_strong
}
