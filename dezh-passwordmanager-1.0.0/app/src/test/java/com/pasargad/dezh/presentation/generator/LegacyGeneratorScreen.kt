package com.pasargad.dezh.presentation.generator

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
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
import com.pasargad.dezh.generator.PasswordGenerator.Options
import kotlinx.coroutines.delay

/**
 * Pre-fix copy of the generator screen, kept solely as the negative control for
 * [com.pasargad.dezh.regression.GeneratorClipboardAutoClearTest]: the test must
 * fail against this variant and pass against the real screen.
 */
@Composable
fun LegacyGeneratorScreen(
    viewModel: GeneratorViewModel,
    modifier: Modifier = Modifier,
    animationsEnabled: Boolean = true,
    clipboardTimeoutSeconds: Int = DEFAULT_CLIPBOARD_TIMEOUT,
    suspendDelay: suspend (Long) -> Unit = { delay(it) },
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val clipboard = LocalClipboardManager.current
    var copied by remember { mutableStateOf(false) }

    // Clipboard hygiene: the copied value auto-clears after the user-configured
    // timeout (0 = never). The generated value is never logged or persisted.
    LaunchedEffect(copied) {
        if (copied && clipboardTimeoutSeconds > 0) {
            suspendDelay(clipboardTimeoutSeconds * MILLIS_PER_SECOND)
            clipboard.setText(AnnotatedString(""))
            copied = false
        }
    }

    LaunchedEffect(copied) {
        if (copied) {
            suspendDelay(COPIED_VISIBLE_MS)
            copied = false
        }
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
            Text(stringResource(R.string.generator_title), style = MaterialTheme.typography.headlineSmall)

            Surface(shape = MaterialTheme.shapes.medium, tonalElevation = 2.dp, modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = state.password.ifEmpty { stringResource(R.string.generator_empty_hint) },
                    style = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace),
                    modifier = Modifier.padding(16.dp),
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = viewModel::regenerate) { Text(stringResource(R.string.generator_regenerate)) }
                OutlinedButton(
                    enabled = state.password.isNotEmpty(),
                    onClick = {
                        clipboard.setText(AnnotatedString(state.password))
                        copied = true
                    },
                ) { Text(stringResource(R.string.generator_copy)) }
            }
            AnimatedVisibility(visible = copied && animationsEnabled) {
                Text(
                    text = stringResource(
                        if (clipboardTimeoutSeconds > 0) R.string.entry_copied_autoclear else R.string.generator_copied,
                    ),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            if (copied && !animationsEnabled) {
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

            Text(stringResource(R.string.generator_length, state.length), style = MaterialTheme.typography.labelLarge)
            Slider(
                value = state.length.toFloat(),
                onValueChange = { viewModel.onLengthChanged(it.toInt()) },
                valueRange = Options.MIN_LENGTH.toFloat()..Options.MAX_LENGTH.toFloat(),
                steps = Options.MAX_LENGTH - Options.MIN_LENGTH - 1,
            )

            ToggleRow(stringResource(R.string.generator_uppercase), state.includeUppercase, viewModel::onUppercaseChanged)
            MinRow(stringResource(R.string.generator_min_upper), state.minUppercase, viewModel::onMinUppercaseChanged)
            ToggleRow(stringResource(R.string.generator_lowercase), state.includeLowercase, viewModel::onLowercaseChanged)
            MinRow(stringResource(R.string.generator_min_lower), state.minLowercase, viewModel::onMinLowercaseChanged)
            ToggleRow(stringResource(R.string.generator_digits), state.includeDigits, viewModel::onDigitsChanged)
            MinRow(stringResource(R.string.generator_min_digits), state.minDigits, viewModel::onMinDigitsChanged)
            ToggleRow(stringResource(R.string.generator_symbols), state.includeSymbols, viewModel::onSymbolsChanged)
            MinRow(stringResource(R.string.generator_min_symbols), state.minSymbols, viewModel::onMinSymbolsChanged)
            ToggleRow(
                stringResource(R.string.generator_exclude_ambiguous),
                state.excludeAmbiguous,
                viewModel::onExcludeAmbiguousChanged,
            )
        }
    }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun MinRow(label: String, value: Int, onChange: (Int) -> Unit) {
    val decreaseDescription = stringResource(R.string.generator_decrease)
    val increaseDescription = stringResource(R.string.generator_increase)
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(stringResource(R.string.generator_min_of, label, value), modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
        OutlinedButton(onClick = { onChange((value - 1).coerceAtLeast(0)) }) {
            Text("−", modifier = Modifier.semantics { contentDescription = decreaseDescription })
        }
        OutlinedButton(onClick = { onChange((value + 1).coerceAtMost(8)) }) {
            Text("+", modifier = Modifier.semantics { contentDescription = increaseDescription })
        }
    }
}

private const val COPIED_VISIBLE_MS = 1_500L
private const val DEFAULT_CLIPBOARD_TIMEOUT = 60
private const val MILLIS_PER_SECOND = 1_000L
