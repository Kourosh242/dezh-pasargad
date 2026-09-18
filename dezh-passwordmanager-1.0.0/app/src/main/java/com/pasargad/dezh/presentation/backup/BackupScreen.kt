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
import androidx.compose.material3.Button
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Encrypted backup export. The file location AND filename are chosen by the
 * user through the Storage Access Framework (CreateDocument); nothing is
 * written without that explicit pick.
 */
@Composable
fun BackupScreen(
    viewModel: BackupViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    val createDocument = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument(MIME_BACKUP),
    ) { uri -> viewModel.onExportLocationPicked(uri) }

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
                Text(stringResource(R.string.settings_backup_nav), style = MaterialTheme.typography.headlineSmall)
                TextButton(onClick = onBack) { Text(stringResource(R.string.action_back)) }
            }

            Text(stringResource(R.string.backup_explanation), style = MaterialTheme.typography.bodyMedium)

            OutlinedTextField(
                value = state.passphrase,
                onValueChange = viewModel::onPassphraseChanged,
                label = { Text(stringResource(R.string.backup_passphrase)) },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = state.confirmPassphrase,
                onValueChange = viewModel::onConfirmPassphraseChanged,
                label = { Text(stringResource(R.string.backup_passphrase_confirm)) },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
            )
            if (state.passphrase.isNotEmpty() && state.passphrase != state.confirmPassphrase) {
                Text(
                    stringResource(R.string.backup_passphrase_mismatch),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            if (state.passphrase.isNotEmpty()) {
                LinearProgressIndicator(
                    progress = { state.meterScore / FLOAT_FULL_SCORE },
                    color = when (state.meterBand) {
                        com.pasargad.dezh.presentation.vault.MeterBand.STRONG -> MaterialTheme.colorScheme.primary
                        com.pasargad.dezh.presentation.vault.MeterBand.FAIR -> MaterialTheme.colorScheme.tertiary
                        com.pasargad.dezh.presentation.vault.MeterBand.WEAK -> MaterialTheme.colorScheme.error
                    },
                    trackColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    // Resolve the band label to a string BEFORE substitution —
                    // passing the Int resource id printed a raw number.
                    stringResource(R.string.entry_meter_label, stringResource(meterLabel(state.meterBand))),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Button(
                enabled = state.canExport && !state.busy,
                onClick = { createDocument.launch(suggestedFilename(state.defaultFilename)) },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.backup_action)) }

            if (state.busy) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
            state.successCount?.let { count ->
                Text(
                    stringResource(R.string.backup_success, count),
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            state.error?.let { error ->
                Text(
                    stringResource(backupErrorLabel(error)),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
                TextButton(onClick = viewModel::dismissError) {
                    Text(stringResource(R.string.action_dismiss))
                }
            }
        }
    }
}

/** Suggested (not enforced) filename: user-choosable in the SAF picker. */
internal fun suggestedFilename(base: String): String {
    val day = SimpleDateFormat(FILENAME_DATE_PATTERN, Locale.US).format(Date())
    return "${base.trim().ifBlank { "dezh-backup" }}-$day${FILENAME_EXTENSION}"
}

internal fun backupErrorLabel(error: BackupUiError): Int = when (error) {
    BackupUiError.INVALID_PATH -> R.string.backup_error_invalid_path
    BackupUiError.INSUFFICIENT_STORAGE -> R.string.backup_error_insufficient_storage
    BackupUiError.IO_ERROR -> R.string.backup_error_io
    BackupUiError.CORRUPTED_OUTPUT -> R.string.backup_error_corrupted_output
    BackupUiError.CANCELED -> R.string.backup_error_canceled
}

@Composable
private fun meterLabel(band: com.pasargad.dezh.presentation.vault.MeterBand): Int = when (band) {
    com.pasargad.dezh.presentation.vault.MeterBand.WEAK -> R.string.strength_weak
    com.pasargad.dezh.presentation.vault.MeterBand.FAIR -> R.string.strength_fair
    com.pasargad.dezh.presentation.vault.MeterBand.STRONG -> R.string.strength_strong
}

private const val MIME_BACKUP = "application/octet-stream"
private const val FILENAME_DATE_PATTERN = "yyyy-MM-dd"
private const val FILENAME_EXTENSION = ".dezhbackup"
private const val FLOAT_FULL_SCORE = 100f
