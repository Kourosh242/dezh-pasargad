package com.pasargad.dezh.presentation.update

import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.pasargad.dezh.R
import com.pasargad.dezh.domain.update.Verdict

/**
 * Rendering of every in-app install step below the "update available" header:
 * download progress, APK facts, permission gate, official installer wait and
 * every outcome (success / abort / block / failure / policy refusal).
 */
@Composable
internal fun InstallStepSection(
    step: InstallStep,
    onDownload: () -> Unit,
    onProceedInstall: () -> Unit,
    onReopenSettings: () -> Unit,
    onOpenReleasePage: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        when (step) {
            InstallStep.NotStarted -> {
                PrimaryActionButton(
                    text = stringResource(R.string.update_install_in_app_action),
                    onClick = onDownload,
                )
                TextButton(onClick = onOpenReleasePage) {
                    Text(stringResource(R.string.update_release_page_link))
                }
            }
            is InstallStep.DownloadingApk -> {
                LinearProgressIndicator(
                    progress = { step.percent / PERCENT_DENOMINATOR.toFloat() },
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    text = stringResource(R.string.update_download_progress, step.percent),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            InstallStep.InspectingApk -> {
                CircularProgressIndicator(
                    modifier = Modifier.size(36.dp),
                    strokeWidth = 4.dp,
                    trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                )
                Text(
                    text = stringResource(R.string.update_inspecting_apk),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            is InstallStep.ReadyToInstall -> ApkInfoSection(
                summary = step.summary,
                onInstall = onProceedInstall,
            )
            is InstallStep.NeedInstallPermission -> PermissionGateSection(
                onReopenSettings = onReopenSettings,
            )
            InstallStep.Installing, InstallStep.WaitingSystemConfirmation ->
                ProgressingInstall(waiting = step is InstallStep.WaitingSystemConfirmation)
            InstallStep.InstallSucceeded -> StatusRow(
                icon = Icons.Filled.CheckCircle,
                tint = MaterialTheme.colorScheme.primary,
                text = stringResource(R.string.update_install_success),
            )
            is InstallStep.InstallAborted -> AbortedInstall(onProceedInstall)
            is InstallStep.InstallBlocked ->
                OutcomeFailure(R.string.update_install_blocked, step.systemMessage)
            is InstallStep.InstallFailed ->
                OutcomeFailure(R.string.update_install_failed, step.systemMessage)
            is InstallStep.InstallRefused -> RefusedSection(step.reason)
            InstallStep.AssetMissing ->
                OutcomeFailure(R.string.update_download_failed, systemMessage = null)
        }
    }
}

@Composable
private fun OutcomeFailure(baseMessage: Int, systemMessage: String?) {
    StatusRow(
        icon = Icons.Filled.Warning,
        tint = MaterialTheme.colorScheme.error,
        text = stringResource(baseMessage, "") + (systemMessage?.let { ": $it" } ?: "."),
    )
}

@Composable
private fun ProgressingInstall(waiting: Boolean) {
    CircularProgressIndicator()
    Text(
        text = stringResource(
            if (waiting) R.string.update_waiting_confirmation else R.string.update_installing,
        ),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )
}

@Composable
private fun AbortedInstall(onRetryInstall: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        StatusRow(
            icon = Icons.Filled.Warning,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            text = stringResource(R.string.update_install_aborted),
        )
        PrimaryActionButton(
            text = stringResource(R.string.update_install_now_action),
            onClick = onRetryInstall,
        )
    }
}

@Composable
private fun ApkInfoSection(summary: ApkSummary, onInstall: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = stringResource(R.string.update_apk_info_title),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )
        InfoRow(stringResource(R.string.update_apk_info_row_file, summary.fileName))
        InfoRow(stringResource(R.string.update_apk_info_row_size, formatBytes(summary.sizeBytes)))
        InfoRow(stringResource(R.string.update_apk_info_row_version, summary.versionCode))
        InfoRow(stringResource(R.string.update_apk_info_row_package, summary.packageName))
        Text(
            text = stringResource(R.string.update_apk_info_verified),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center,
        )
        PrimaryActionButton(text = stringResource(R.string.update_install_now_action), onClick = onInstall)
    }
}

@Composable
private fun PermissionGateSection(onReopenSettings: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = stringResource(R.string.update_need_permission_title),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = stringResource(R.string.update_need_permission_desc),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        PrimaryActionButton(text = stringResource(R.string.update_open_settings_action), onClick = onReopenSettings)
    }
}

@Composable
private fun RefusedSection(reason: Verdict) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = stringResource(R.string.update_refused_title),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )
        val message = when (reason) {
            is Verdict.WrongPackage -> stringResource(R.string.update_refused_wrong_package, reason.actual)
            is Verdict.NotNewer -> stringResource(R.string.update_refused_not_newer, reason.candidateVersionCode)
            Verdict.SignatureMismatch -> stringResource(R.string.update_refused_signature)
            Verdict.Unreadable -> stringResource(R.string.update_refused_unreadable)
            Verdict.Valid -> "" // unreachable: Valid never reaches the refusal UI
        }
        StatusRow(
            icon = Icons.Filled.Warning,
            tint = MaterialTheme.colorScheme.error,
            text = message,
        )
    }
}

@Composable
private fun InfoRow(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        fontFamily = FontFamily.Monospace,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

private const val PERCENT_DENOMINATOR = 100
