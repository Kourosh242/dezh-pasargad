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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.text.font.FontFamily
import com.pasargad.dezh.ui.theme.LocalMotionEnabled
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
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EntryEditScreen(
    viewModel: EntryEditViewModel,
    onSaved: () -> Unit,
    onCancelled: () -> Unit,
    modifier: Modifier = Modifier,
    confirmBeforeDiscard: Boolean = true,
) {
    var showDiscardDialog by remember { mutableStateOf(false) }
    var showQuickGenerator by remember { mutableStateOf(false) }
    val quickState by viewModel.quickGenerator.collectAsStateWithLifecycle()
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
            PasswordEditor(
                state = state,
                onPasswordChanged = viewModel::onPasswordChanged,
                onOpenQuickGenerator = {
                    viewModel.onQuickSheetOpened()
                    showQuickGenerator = true
                },
            )
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

    if (showQuickGenerator) {
        ModalBottomSheet(onDismissRequest = { showQuickGenerator = false }) {
            QuickGeneratorSheet(
                state = quickState,
                onOptionsChanged = viewModel::onQuickOptionsChanged,
                onRegenerate = viewModel::onQuickSheetOpened,
                onUse = {
                    viewModel.onQuickUse()
                    showQuickGenerator = false
                },
            )
        }
    }
}

/**
 * Proton-Pass-style inline generator: tune, regenerate and adopt without
 * leaving the edit form. All controls are 48dp+; the adopt action is the
 * only path that touches the form field.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QuickGeneratorSheet(
    state: QuickGeneratorState,
    onOptionsChanged: (Int, Boolean, Boolean, Boolean, Boolean, Boolean) -> Unit,
    onRegenerate: () -> Unit,
    onUse: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = stringResource(R.string.generator_sheet_title),
            style = MaterialTheme.typography.titleMedium,
        )
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = state.password.ifEmpty { stringResource(R.string.generator_empty_hint) },
                style = if (state.password.isEmpty()) {
                    MaterialTheme.typography.bodyMedium
                } else {
                    MaterialTheme.typography.titleMedium.copy(fontFamily = FontFamily.Monospace)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
            )
        }
        TextButton(onClick = onRegenerate) {
            Text(stringResource(R.string.generator_regenerate))
        }
        // Drag locally; ONE regeneration when the finger lifts (per-tick
        // regeneration made the preview flicker and burn cycles).
        var dragLength by remember { mutableStateOf<Int?>(null) }
        val shownLength = dragLength ?: state.length
        Text(
            text = stringResource(R.string.generator_length, shownLength),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Slider(
            value = shownLength.toFloat(),
            onValueChange = { dragLength = it.toInt() },
            onValueChangeFinished = {
                dragLength?.let {
                    onOptionsChanged(
                        it,
                        state.includeUppercase,
                        state.includeLowercase,
                        state.includeDigits,
                        state.includeSymbols,
                        state.excludeAmbiguous,
                    )
                }
                dragLength = null
            },
            valueRange = 8f..64f,
        )
        SwitchRow(stringResource(R.string.generator_uppercase), state.includeUppercase) {
            onOptionsChanged(state.length, !state.includeUppercase, state.includeLowercase, state.includeDigits, state.includeSymbols, state.excludeAmbiguous)
        }
        SwitchRow(stringResource(R.string.generator_lowercase), state.includeLowercase) {
            onOptionsChanged(state.length, state.includeUppercase, !state.includeLowercase, state.includeDigits, state.includeSymbols, state.excludeAmbiguous)
        }
        SwitchRow(stringResource(R.string.generator_digits), state.includeDigits) {
            onOptionsChanged(state.length, state.includeUppercase, state.includeLowercase, !state.includeDigits, state.includeSymbols, state.excludeAmbiguous)
        }
        SwitchRow(stringResource(R.string.generator_symbols), state.includeSymbols) {
            onOptionsChanged(state.length, state.includeUppercase, state.includeLowercase, state.includeDigits, !state.includeSymbols, state.excludeAmbiguous)
        }
        SwitchRow(stringResource(R.string.generator_exclude_ambiguous), state.excludeAmbiguous) {
            onOptionsChanged(state.length, state.includeUppercase, state.includeLowercase, state.includeDigits, state.includeSymbols, !state.excludeAmbiguous)
        }
        Button(
            onClick = onUse,
            enabled = state.password.isNotEmpty(),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.generator_sheet_use))
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onToggle: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
        )
        Switch(checked = checked, onCheckedChange = { onToggle() })
    }
}

private fun meterLabel(band: MeterBand): Int = when (band) {
    MeterBand.WEAK -> R.string.strength_weak
    MeterBand.FAIR -> R.string.strength_fair
    MeterBand.STRONG -> R.string.strength_strong
}

/**
 * Password field + strength meter of the edit form, extracted to keep the
 * screen composable simple. The key glyph opens the quick generator sheet.
 */
@Composable
private fun PasswordEditor(
    state: EntryEditUiState,
    onPasswordChanged: (String) -> Unit,
    onOpenQuickGenerator: () -> Unit,
) {
    var passwordVisible by remember { mutableStateOf(false) }
    OutlinedTextField(
        value = state.password,
        onValueChange = onPasswordChanged,
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
                IconButton(onClick = onOpenQuickGenerator) {
                    val generateDescription = stringResource(R.string.entry_generate)
                    Icon(
                        painter = painterResource(R.drawable.ic_nav_key),
                        contentDescription = generateDescription,
                        tint = MaterialTheme.colorScheme.primary,
                    )
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
}
