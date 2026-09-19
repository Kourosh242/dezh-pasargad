package com.pasargad.dezh.presentation.backup

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pasargad.dezh.R
import com.pasargad.dezh.domain.util.JalaliDate
import com.pasargad.dezh.backup.RestoreMode
import java.util.Date

/**
 * Secure restore flow. Every destructive step is explicit: header checks run
 * before any passphrase is requested, and REPLACE only executes after the
 * user picks it AND confirms a dialog that states the exact consequence.
 */
@Suppress("CyclomaticComplexMethod") // one branch per explicit restore stage
@Composable
fun RestoreScreen(
    viewModel: RestoreViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    val openDocument = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri -> viewModel.onFilePicked(uri) }

    if (state.stage == RestoreStage.CONFIRM_REPLACE) {
        AlertDialog(
            onDismissRequest = viewModel::onReplaceConfirmationDeclined,
            title = { Text(stringResource(R.string.restore_replace_confirm_title)) },
            text = { Text(stringResource(R.string.restore_replace_confirm_text)) },
            confirmButton = {
                TextButton(onClick = viewModel::onReplaceConfirmed) {
                    Text(
                        stringResource(R.string.restore_replace_confirm_action),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::onReplaceConfirmationDeclined) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }

    Scaffold(modifier = modifier) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(stringResource(R.string.settings_restore_nav), style = MaterialTheme.typography.headlineSmall)
                TextButton(onClick = onBack) { Text(stringResource(R.string.action_back)) }
            }

            when (state.stage) {
                RestoreStage.PICK_FILE, RestoreStage.FAILED -> {
                    if (state.stage == RestoreStage.FAILED) {
                        state.error?.let { error ->
                            Card(modifier = Modifier.fillMaxWidth()) {
                                Text(
                                    stringResource(restoreErrorLabel(error)),
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.padding(12.dp),
                                )
                            }
                        }
                    }
                    Text(stringResource(R.string.restore_explanation), style = MaterialTheme.typography.bodyMedium)
                    Button(
                        onClick = { openDocument.launch(arrayOf(MIME_ANY, MIME_ANY_ALT)) },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(stringResource(R.string.restore_pick_file)) }
                    if (state.stage == RestoreStage.FAILED) {
                        OutlinedButton(onClick = viewModel::reset, modifier = Modifier.fillMaxWidth()) {
                            Text(stringResource(R.string.action_cancel))
                        }
                    }
                }

                RestoreStage.PASSPHRASE -> {
                    state.headerVersion?.let { version ->
                        Text(
                            stringResource(
                                R.string.restore_header_info,
                                version,
                                formatDate(state.headerCreatedAtEpochMs),
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    Text(stringResource(R.string.restore_passphrase_prompt), style = MaterialTheme.typography.bodyMedium)
                    OutlinedTextField(
                        value = state.passphrase,
                        onValueChange = viewModel::onPassphraseChanged,
                        label = { Text(stringResource(R.string.backup_passphrase)) },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    state.error?.let { error ->
                        Text(
                            stringResource(restoreErrorLabel(error)),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Button(
                            enabled = state.passphrase.isNotEmpty() && !state.busy,
                            onClick = viewModel::onPassphraseSubmitted,
                        ) { Text(stringResource(R.string.restore_unlock)) }
                        OutlinedButton(onClick = viewModel::reset) { Text(stringResource(R.string.action_cancel)) }
                    }
                }

                RestoreStage.CHOOSE_MODE -> {
                    state.previewCount?.let { count ->
                        Text(
                            stringResource(R.string.restore_preview, count),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                    Text(stringResource(R.string.restore_choose_mode), style = MaterialTheme.typography.bodyMedium)

                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(stringResource(R.string.restore_merge_title), style = MaterialTheme.typography.titleSmall)
                            Text(
                                stringResource(R.string.restore_merge_consequence),
                                style = MaterialTheme.typography.bodySmall,
                            )
                            Button(onClick = viewModel::onMergeChosen, modifier = Modifier.padding(top = 8.dp)) {
                                Text(stringResource(R.string.restore_merge_action))
                            }
                        }
                    }
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                stringResource(R.string.restore_replace_title),
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                            Text(
                                stringResource(R.string.restore_replace_consequence),
                                style = MaterialTheme.typography.bodySmall,
                            )
                            OutlinedButton(onClick = viewModel::onReplaceChosen, modifier = Modifier.padding(top = 8.dp)) {
                                Text(
                                    stringResource(R.string.restore_replace_action),
                                    color = MaterialTheme.colorScheme.error,
                                )
                            }
                        }
                    }
                    OutlinedButton(onClick = viewModel::onCancelAfterPreview, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.action_cancel))
                    }
                }

                RestoreStage.CONFIRM_REPLACE, RestoreStage.RUNNING -> {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }

                RestoreStage.DONE -> {
                    state.summary?.let { summary ->
                        Text(
                            text = when (summary.mode) {
                                RestoreMode.MERGE -> stringResource(
                                    R.string.restore_done_merge,
                                    summary.inserted,
                                    summary.updated,
                                    summary.skipped,
                                )

                                RestoreMode.REPLACE -> stringResource(
                                    R.string.restore_done_replace,
                                    summary.deletedExisting,
                                    summary.inserted,
                                )
                            },
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                    Button(onClick = viewModel::reset, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.action_done))
                    }
                }
            }
        }
    }
}

internal fun restoreErrorLabel(error: RestoreUiError): Int = when (error) {
    RestoreUiError.INVALID_PATH -> R.string.restore_error_invalid_path
    RestoreUiError.EMPTY_FILE -> R.string.restore_error_empty_file
    RestoreUiError.FILE_TOO_LARGE -> R.string.restore_error_too_large
    RestoreUiError.IO_ERROR -> R.string.restore_error_io
    RestoreUiError.INVALID_FORMAT -> R.string.restore_error_invalid_format
    RestoreUiError.UNSUPPORTED_VERSION -> R.string.restore_error_unsupported_version
    RestoreUiError.INTEGRITY_FAILURE -> R.string.restore_error_integrity
    RestoreUiError.CORRUPTED -> R.string.restore_error_corrupted
    RestoreUiError.INCOMPLETE -> R.string.restore_error_incomplete
    RestoreUiError.WRONG_PASSWORD -> R.string.restore_error_wrong_password
    RestoreUiError.IMPORT_FAILURE -> R.string.restore_error_import_failure
}

private fun formatDate(epochMs: Long?): String =
    if (epochMs == null) {
        "-"
    } else {
        JalaliDate.formatDateTime(epochMs)
    }

private const val MIME_ANY = "*/*"
private const val MIME_ANY_ALT = "application/octet-stream"
