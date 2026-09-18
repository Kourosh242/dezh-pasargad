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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pasargad.dezh.R
import com.pasargad.dezh.settings.DezhSettings
import com.pasargad.dezh.settings.DisplayMode
import com.pasargad.dezh.settings.StartupPage

/**
 * Settings hub: navigation to theme/security/backup/about sections plus the
 * vault-level preferences (list mode, startup page, default category,
 * backup filename base) and the reset action.
 */
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
    onThemeClick: () -> Unit,
    onSecurityClick: () -> Unit,
    onBackupClick: () -> Unit,
    onRestoreClick: () -> Unit,
    onAboutClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val settings by viewModel.uiState.collectAsStateWithLifecycle()
    var displayDialog by remember { mutableStateOf(false) }
    var startupDialog by remember { mutableStateOf(false) }
    var categoryDialog by remember { mutableStateOf(false) }
    var filenameDialog by remember { mutableStateOf(false) }
    var resetDialog by remember { mutableStateOf(false) }

    if (displayDialog) {
        RadioDialog(
            title = stringResource(R.string.settings_display_mode),
            options = DisplayMode.entries.map { it.name to displayModeLabel(it) },
            selected = settings.displayMode.name,
            onSelect = { viewModel.onDisplayModeChanged(DisplayMode.valueOf(it)) },
            onDismiss = { displayDialog = false },
        )
    }
    if (startupDialog) {
        RadioDialog(
            title = stringResource(R.string.settings_startup_page),
            options = StartupPage.entries.map { it.name to startupPageLabel(it) },
            selected = settings.startupPage.name,
            onSelect = { viewModel.onStartupPageChanged(StartupPage.valueOf(it)) },
            onDismiss = { startupDialog = false },
        )
    }
    if (categoryDialog) {
        TextDialog(
            title = stringResource(R.string.settings_default_category),
            initialValue = settings.defaultCategory,
            onConfirm = { viewModel.onDefaultCategoryChanged(it); categoryDialog = false },
            onDismiss = { categoryDialog = false },
        )
    }
    if (filenameDialog) {
        TextDialog(
            title = stringResource(R.string.settings_backup_filename),
            initialValue = settings.backupFilenameBase,
            onConfirm = { viewModel.onBackupFilenameChanged(it); filenameDialog = false },
            onDismiss = { filenameDialog = false },
        )
    }
    if (resetDialog) {
        AlertDialog(
            onDismissRequest = { resetDialog = false },
            title = { Text(stringResource(R.string.settings_reset)) },
            text = { Text(stringResource(R.string.settings_reset_confirm_text)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.onResetRequested()
                    resetDialog = false
                }) { Text(stringResource(R.string.settings_reset_action)) }
            },
            dismissButton = {
                TextButton(onClick = { resetDialog = false }) { Text(stringResource(R.string.action_cancel)) }
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
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(R.string.settings_title),
                    style = MaterialTheme.typography.headlineSmall,
                )
                TextButton(onClick = onBack) { Text(stringResource(R.string.action_back)) }
            }

            SectionLabel(stringResource(R.string.settings_section_appearance))
            NavRow(stringResource(R.string.settings_theme_nav), onThemeClick)
            NavRow(stringResource(R.string.settings_display_mode), onClick = { displayDialog = true }) {
                Text(stringResource(displayModeLabel(settings.displayMode)))
            }

            SectionLabel(stringResource(R.string.settings_section_vault))
            NavRow(stringResource(R.string.settings_startup_page), onClick = { startupDialog = true }) {
                Text(stringResource(startupPageLabel(settings.startupPage)))
            }
            NavRow(stringResource(R.string.settings_default_category), onClick = { categoryDialog = true }) {
                Text(settings.defaultCategory.ifBlank { stringResource(R.string.settings_value_none) })
            }
            SwitchRow(
                label = stringResource(R.string.settings_remember_filters),
                checked = settings.rememberSearchFilters,
                onCheckedChange = viewModel::onRememberFiltersChanged,
            )
            SwitchRow(
                label = stringResource(R.string.settings_animations),
                checked = settings.animationsEnabled,
                onCheckedChange = viewModel::onAnimationsChanged,
            )

            SectionLabel(stringResource(R.string.settings_section_backup))
            NavRow(stringResource(R.string.settings_backup_nav), onBackupClick)
            NavRow(stringResource(R.string.settings_restore_nav), onRestoreClick)
            NavRow(stringResource(R.string.settings_backup_filename), onClick = { filenameDialog = true }) {
                Text(settings.backupFilenameBase, maxLines = 1)
            }

            SectionLabel(stringResource(R.string.settings_section_security))
            NavRow(stringResource(R.string.settings_security_nav), onSecurityClick)

            SectionLabel(stringResource(R.string.settings_section_about))
            NavRow(stringResource(R.string.settings_about_nav), onAboutClick)

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            TextButton(onClick = { resetDialog = true }) {
                Text(stringResource(R.string.settings_reset), color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun displayModeLabel(mode: DisplayMode): Int = when (mode) {
    DisplayMode.AUTO -> R.string.display_mode_auto
    DisplayMode.LIST -> R.string.display_mode_list
    DisplayMode.GRID -> R.string.display_mode_grid
}

@Composable
private fun startupPageLabel(page: StartupPage): Int = when (page) {
    StartupPage.VAULT -> R.string.startup_page_vault
    StartupPage.SEARCH -> R.string.startup_page_search
    StartupPage.FAVORITES -> R.string.startup_page_favorites
    StartupPage.GENERATOR -> R.string.startup_page_generator
    StartupPage.CATEGORIES -> R.string.startup_page_categories
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .padding(top = 16.dp, bottom = 4.dp)
            .semantics { heading() },
    )
}

@Composable
private fun NavRow(label: String, onClick: () -> Unit, trailing: @Composable (() -> Unit)? = null) {
    ListItem(
        headlineContent = { Text(label) },
        trailingContent = trailing,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    )
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    ListItem(
        headlineContent = { Text(label) },
        trailingContent = { Switch(checked = checked, onCheckedChange = onCheckedChange) },
        modifier = Modifier.fillMaxWidth(),
    )
}

/** Reusable single-choice dialog used by the hub preference rows. */
@Composable
internal fun RadioDialog(
    title: String,
    options: List<Pair<String, Int>>,
    selected: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                options.forEach { (value, labelRes) ->
                    ListItem(
                        headlineContent = { Text(stringResource(labelRes)) },
                        trailingContent = {
                            if (value == selected) {
                                Text(stringResource(R.string.settings_selected), style = MaterialTheme.typography.labelSmall)
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onSelect(value)
                                onDismiss()
                            },
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

@Composable
private fun TextDialog(
    title: String,
    initialValue: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var value by remember(title) { mutableStateOf(initialValue) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(value) }) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}
