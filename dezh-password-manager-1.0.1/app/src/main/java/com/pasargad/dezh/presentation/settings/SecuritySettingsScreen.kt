package com.pasargad.dezh.presentation.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pasargad.dezh.R
import com.pasargad.dezh.settings.DezhSettings

/** Security settings: auto-lock timeout, clipboard timeout, confirmation prompts. */
@Composable
fun SecuritySettingsScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val settings by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(modifier = modifier) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(stringResource(R.string.settings_security_nav), style = MaterialTheme.typography.headlineSmall)
                TextButton(onClick = onBack) { Text(stringResource(R.string.action_back)) }
            }

            Text(
                stringResource(R.string.settings_screenshot_protection),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .padding(top = 12.dp, bottom = 4.dp)
                    .semantics { heading() },
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_screenshot_protection)) },
                supportingContent = { Text(stringResource(R.string.settings_screenshot_protection_desc)) },
                trailingContent = {
                    Switch(
                        checked = settings.screenshotProtectionEnabled,
                        onCheckedChange = viewModel::onScreenshotProtectionChanged,
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        viewModel.onScreenshotProtectionChanged(!settings.screenshotProtectionEnabled)
                    },
            )

            Text(
                stringResource(R.string.settings_auto_lock),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .padding(top = 12.dp, bottom = 4.dp)
                    .semantics { heading() },
            )
            DezhSettings.AUTO_LOCK_MINUTE_CHOICES.forEach { minutes ->
                ListItem(
                    headlineContent = {
                        Text(
                            if (minutes == DezhSettings.AUTO_LOCK_IMMEDIATELY) {
                                stringResource(R.string.settings_auto_lock_immediately)
                            } else {
                                stringResource(R.string.settings_auto_lock_value, minutes)
                            },
                        )
                    },
                    leadingContent = {
                        RadioButton(
                            selected = settings.autoLockTimeoutMinutes == minutes,
                            onClick = { viewModel.onAutoLockChanged(minutes) },
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Text(
                stringResource(R.string.settings_clipboard_timeout),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .padding(top = 16.dp, bottom = 4.dp)
                    .semantics { heading() },
            )
            DezhSettings.CLIPBOARD_TIMEOUT_CHOICES.forEach { seconds ->
                ListItem(
                    headlineContent = {
                        Text(
                            if (seconds == DezhSettings.CLIPBOARD_TIMEOUT_NEVER) {
                                stringResource(R.string.settings_clipboard_never)
                            } else {
                                stringResource(R.string.settings_clipboard_value, seconds)
                            },
                        )
                    },
                    leadingContent = {
                        RadioButton(
                            selected = settings.clipboardTimeoutSeconds == seconds,
                            onClick = { viewModel.onClipboardTimeoutChanged(seconds) },
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Text(
                stringResource(R.string.settings_confirmations),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 16.dp, bottom = 4.dp),
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_confirm_delete)) },
                trailingContent = {
                    Switch(
                        checked = settings.confirmBeforeDelete,
                        onCheckedChange = viewModel::onConfirmDeleteChanged,
                    )
                },
                modifier = Modifier.fillMaxWidth(),
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_confirm_discard)) },
                trailingContent = {
                    Switch(
                        checked = settings.confirmBeforeDiscard,
                        onCheckedChange = viewModel::onConfirmDiscardChanged,
                    )
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
