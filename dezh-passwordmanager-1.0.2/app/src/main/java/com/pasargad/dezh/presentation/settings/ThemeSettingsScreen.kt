package com.pasargad.dezh.presentation.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp


import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pasargad.dezh.R
import com.pasargad.dezh.settings.AccentColor
import com.pasargad.dezh.settings.DezhSettings
import com.pasargad.dezh.settings.ThemeMode

/** Theme preferences: mode (system/light/dark), accent color, font scale. */
@Composable
fun ThemeSettingsScreen(
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
                Text(stringResource(R.string.settings_theme_nav), style = MaterialTheme.typography.headlineSmall)
                TextButton(onClick = onBack) { Text(stringResource(R.string.action_back)) }
            }

            SectionHeader(stringResource(R.string.theme_mode))
            ThemeMode.entries.forEach { mode ->
                ListItem(
                    headlineContent = { Text(stringResource(themeModeLabel(mode))) },
                    leadingContent = {
                        RadioButton(selected = settings.themeMode == mode, onClick = { viewModel.onThemeChanged(mode) })
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            SectionHeader(stringResource(R.string.theme_accent))
            AccentColor.entries.forEach { accent ->
                ListItem(
                    headlineContent = { Text(stringResource(accentLabel(accent))) },
                    leadingContent = {
                        Box(
                            modifier = Modifier
                                .size(ACCENT_SWATCH_SIZE)
                                .background(color = accentPreview(accent), shape = CircleShape),
                        )
                    },
                    trailingContent = {
                        if (settings.accentColor == accent) {
                            Text(stringResource(R.string.settings_selected), style = MaterialTheme.typography.labelSmall)
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { viewModel.onAccentChanged(accent) },
                )
            }

            SectionHeader(stringResource(R.string.theme_font_scale))
            DezhSettings.FONT_SCALES.forEach { scale ->
                ListItem(
                    headlineContent = { Text(stringResource(fontScaleLabel(scale))) },
                    leadingContent = {
                        RadioButton(
                            selected = settings.fontScale == scale,
                            onClick = { viewModel.onFontScaleChanged(scale) },
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

/** Accent swatch diameter. */
private val ACCENT_SWATCH_SIZE = 24.dp

// ARGB channels for the preview swatches (composed as Color(argb) below).
private const val ARG_GOLD = 0xFFC9A227
private const val ARG_EMERALD = 0xFF2E9E6B
private const val ARG_AMBER = 0xFFD98E23
private const val ARG_ROSE = 0xFFC25B6E
private const val ARG_VIOLET = 0xFF8A6FD1

/** Accent preview swatch — a theme token preview; the real override lives in Theme.kt. */
private fun accentPreview(accent: AccentColor): Color = when (accent) {
    AccentColor.DEFAULT -> Color(ARG_GOLD)
    AccentColor.EMERALD -> Color(ARG_EMERALD)
    AccentColor.AMBER -> Color(ARG_AMBER)
    AccentColor.ROSE -> Color(ARG_ROSE)
    AccentColor.VIOLET -> Color(ARG_VIOLET)
}

@Composable
private fun themeModeLabel(mode: ThemeMode): Int = when (mode) {
    ThemeMode.SYSTEM -> R.string.theme_system
    ThemeMode.LIGHT -> R.string.theme_light
    ThemeMode.DARK -> R.string.theme_dark
}

@Composable
private fun accentLabel(accent: AccentColor): Int = when (accent) {
    AccentColor.DEFAULT -> R.string.accent_default
    AccentColor.EMERALD -> R.string.accent_emerald
    AccentColor.AMBER -> R.string.accent_amber
    AccentColor.ROSE -> R.string.accent_rose
    AccentColor.VIOLET -> R.string.accent_violet
}

@Composable
private fun fontScaleLabel(scale: Float): Int = when (scale) {
    DezhSettings.FONT_SCALE_SMALL -> R.string.font_scale_small
    DezhSettings.FONT_SCALE_NORMAL -> R.string.font_scale_normal
    DezhSettings.FONT_SCALE_LARGE -> R.string.font_scale_large
    else -> R.string.font_scale_xlarge
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .padding(top = 16.dp, bottom = 4.dp)
            .semantics { heading() },
    )
}
