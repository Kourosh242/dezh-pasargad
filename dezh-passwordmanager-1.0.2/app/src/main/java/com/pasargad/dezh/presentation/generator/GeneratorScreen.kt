package com.pasargad.dezh.presentation.generator

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import kotlinx.coroutines.delay
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pasargad.dezh.R
import com.pasargad.dezh.ui.components.CopiedBadge
import com.pasargad.dezh.generator.PasswordGenerator.Options
import com.pasargad.dezh.ui.components.StrengthRing
import com.pasargad.dezh.ui.theme.pressScale
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.ui.res.painterResource

/** Standalone secure password generator (no generated value is ever logged). */
@Composable
fun GeneratorScreen(
    viewModel: GeneratorViewModel,
    modifier: Modifier = Modifier,
    animationsEnabled: Boolean = true,
    clipboardTimeoutSeconds: Int = DEFAULT_CLIPBOARD_TIMEOUT,
    /** Injectable clock pause for tests; production uses coroutine delay. */
    suspendDelay: suspend (Long) -> Unit = { delay(it) },
    onVaultTab: () -> Unit = {},
    onSettingsTab: () -> Unit = {},
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val clipboard = LocalClipboardManager.current
    var copiedMessage by remember { mutableStateOf(false) }

    // Each copy bumps a generation counter; the auto-clear is keyed on it —
    // NOT on the message flag. Keying both on one boolean used to let the
    // 1.5s message hide CANCEL the pending clipboard wipe, so the copied
    // password never auto-cleared (self-audit bug #5).
    var copyGeneration by remember { mutableStateOf(0) }

    LaunchedEffect(copyGeneration) {
        if (copyGeneration > 0 && clipboardTimeoutSeconds > 0) {
            suspendDelay(clipboardTimeoutSeconds * MILLIS_PER_SECOND)
            clipboard.setText(AnnotatedString(""))
        }
    }

    LaunchedEffect(copiedMessage) {
        if (copiedMessage) {
            suspendDelay(COPIED_VISIBLE_MS)
            copiedMessage = false
        }
    }

    Scaffold(
        modifier = modifier,
        bottomBar = {
            com.pasargad.dezh.ui.components.DezhBottomNav(
                current = com.pasargad.dezh.ui.components.DezhTab.GENERATOR,
                onVault = onVaultTab,
                onGenerator = {},
                onSettings = onSettingsTab,
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.generator_title), style = MaterialTheme.typography.headlineSmall)

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Surface(shape = MaterialTheme.shapes.extraLarge, tonalElevation = 2.dp, modifier = Modifier.weight(1f)) {
                    Text(
                        text = state.password.ifEmpty { stringResource(R.string.generator_empty_hint) },
                        style = MaterialTheme.typography.titleMedium.copy(fontFamily = FontFamily.Monospace),
                        modifier = Modifier.padding(16.dp),
                    )
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    StrengthRing(score = state.meterScore)
                    Text(
                        text = stringResource(meterLabel(state.meterScore)),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                val regenInteraction = remember { MutableInteractionSource() }
                Button(
                    onClick = viewModel::regenerate,
                    interactionSource = regenInteraction,
                    modifier = Modifier
                        .weight(1f)
                        .pressScale(regenInteraction),
                ) { Text(stringResource(R.string.generator_regenerate)) }
                val copyInteraction = remember { MutableInteractionSource() }
                OutlinedButton(
                    enabled = state.password.isNotEmpty(),
                    interactionSource = copyInteraction,
                    onClick = {
                        clipboard.setText(AnnotatedString(state.password))
                        copyGeneration += 1
                        copiedMessage = true
                    },
                    modifier = Modifier
                        .weight(1f)
                        .pressScale(copyInteraction),
                ) { Text(stringResource(R.string.generator_copy)) }
            }
            AnimatedVisibility(visible = copiedMessage && animationsEnabled) {
                CopiedBadge(
                    text = stringResource(
                        if (clipboardTimeoutSeconds > 0) R.string.entry_copied_autoclear else R.string.generator_copied,
                    ),
                )
            }
            if (copiedMessage && !animationsEnabled) {
                Text(
                    text = stringResource(
                        if (clipboardTimeoutSeconds > 0) R.string.entry_copied_autoclear else R.string.generator_copied,
                    ),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            if (state.error) {
                Text(
                    text = stringResource(R.string.generator_empty_hint),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            // Drag locally; ONE persist+regenerate when the finger lifts —
            // per-tick changes wrote DataStore dozens of times per drag.
            var dragLength by remember { mutableStateOf<Int?>(null) }
            val shownLength = dragLength ?: state.length
            Text(stringResource(R.string.generator_length, shownLength), style = MaterialTheme.typography.labelLarge)
            Slider(
                value = shownLength.toFloat(),
                onValueChange = { dragLength = it.toInt() },
                onValueChangeFinished = {
                    dragLength?.let(viewModel::onLengthChanged)
                    dragLength = null
                },
                valueRange = Options.MIN_LENGTH.toFloat()..Options.MAX_LENGTH.toFloat(),
                // Continuous track: per-step tick marks (55 of them) rendered
                // the track as a broken dotted strip in real screenshots.
                steps = 0,
            )

            OptionsCard {
                OptionRow(
                    label = stringResource(R.string.generator_uppercase),
                    minLabel = stringResource(R.string.generator_min_of, stringResource(R.string.generator_min_upper), state.minUppercase),
                    count = state.minUppercase,
                    onDecrease = { viewModel.onMinUppercaseChanged(state.minUppercase - 1) },
                    onIncrease = { viewModel.onMinUppercaseChanged(state.minUppercase + 1) },
                    checked = state.includeUppercase,
                    onChecked = viewModel::onUppercaseChanged,
                )
                OptionRow(
                    label = stringResource(R.string.generator_lowercase),
                    minLabel = stringResource(R.string.generator_min_of, stringResource(R.string.generator_min_lower), state.minLowercase),
                    count = state.minLowercase,
                    onDecrease = { viewModel.onMinLowercaseChanged(state.minLowercase - 1) },
                    onIncrease = { viewModel.onMinLowercaseChanged(state.minLowercase + 1) },
                    checked = state.includeLowercase,
                    onChecked = viewModel::onLowercaseChanged,
                )
                OptionRow(
                    label = stringResource(R.string.generator_digits),
                    minLabel = stringResource(R.string.generator_min_of, stringResource(R.string.generator_min_digits), state.minDigits),
                    count = state.minDigits,
                    onDecrease = { viewModel.onMinDigitsChanged(state.minDigits - 1) },
                    onIncrease = { viewModel.onMinDigitsChanged(state.minDigits + 1) },
                    checked = state.includeDigits,
                    onChecked = viewModel::onDigitsChanged,
                )
                OptionRow(
                    label = stringResource(R.string.generator_symbols),
                    minLabel = stringResource(R.string.generator_min_of, stringResource(R.string.generator_min_symbols), state.minSymbols),
                    count = state.minSymbols,
                    onDecrease = { viewModel.onMinSymbolsChanged(state.minSymbols - 1) },
                    onIncrease = { viewModel.onMinSymbolsChanged(state.minSymbols + 1) },
                    checked = state.includeSymbols,
                    onChecked = viewModel::onSymbolsChanged,
                )
                ToggleRow(
                    stringResource(R.string.generator_exclude_ambiguous),
                    state.excludeAmbiguous,
                    viewModel::onExcludeAmbiguousChanged,
                )
            }
        }
    }
}

@Composable
private fun OptionsCard(content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            content = content,
        )
    }
}

/** One character-class row: label + min stepper + switch, settings-card rhythm. */
@Composable
private fun OptionRow(
    label: String,
    minLabel: String,
    count: Int,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit,
    checked: Boolean,
    onChecked: (Boolean) -> Unit,
) {
    val decreaseDescription = stringResource(R.string.generator_decrease)
    val increaseDescription = stringResource(R.string.generator_increase)
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = minLabel,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        OutlinedIconButton(onClick = onDecrease, modifier = Modifier.size(STEPPER_SIZE)) {
            Text("−", modifier = Modifier.semantics { contentDescription = decreaseDescription })
        }
        Text(
            text = count.toString(),
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.width(20.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        OutlinedIconButton(onClick = onIncrease, modifier = Modifier.size(STEPPER_SIZE)) {
            Text("+", modifier = Modifier.semantics { contentDescription = increaseDescription })
        }
        Switch(checked = checked, onCheckedChange = onChecked)
    }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

private val STEPPER_SIZE = 40.dp

private const val METER_WEAK_MAX = 28
private const val METER_FAIR_MAX = 50

private fun meterLabel(score: Int): Int = when {
    score < METER_WEAK_MAX -> R.string.strength_weak
    score < METER_FAIR_MAX -> R.string.strength_fair
    else -> R.string.strength_strong
}

private const val COPIED_VISIBLE_MS = 1_500L
private const val DEFAULT_CLIPBOARD_TIMEOUT = 60
private const val MILLIS_PER_SECOND = 1_000L
