package com.pasargad.dezh.presentation.unlock

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import com.pasargad.dezh.ui.theme.LocalMotionEnabled
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pasargad.dezh.R

/**
 * Vault unlock screen. Shows only generic failure feedback (wrong password,
 * cooldown, corrupted data) — never any cryptographic detail.
 * Password paste is NOT blocked (accessible-authentication rule) and a
 * visibility toggle lets users verify what they typed.
 */
@Composable
fun UnlockScreen(viewModel: UnlockViewModel, modifier: Modifier = Modifier) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    UnlockScreen(
        state = state,
        onInputChanged = viewModel::onInputChanged,
        onSubmit = { raw -> viewModel.submit(raw.toCharArray()) },
        modifier = modifier,
    )
}

@Composable
fun UnlockScreen(
    state: UnlockUiState,
    onInputChanged: () -> Unit,
    onSubmit: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var password by rememberSaveable { mutableStateOf("") }
    var passwordVisible by rememberSaveable { mutableStateOf(false) }
    val backoffActive = state.backoffRemainingSeconds > 0

    // Single submit path: both the IME Done action and the button clear the
    // field afterwards, so the master password never lingers on screen.
    val submitAndClear: () -> Unit = submitAndClear@{
        if (!canSubmit(state, password)) return@submitAndClear
        onSubmit(password)
        password = ""
        passwordVisible = false
    }

    Scaffold(modifier = modifier) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            MaterialTheme.colorScheme.background,
                            MaterialTheme.colorScheme.surfaceContainerLow,
                        ),
                    ),
                )
                .padding(innerPadding),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                // Brand mark — the fortress stands between the user and the vault.
                // Signature motion: the brand disc breathes, gently.
                val motionEnabled = LocalMotionEnabled.current
                val breath: Float = if (motionEnabled) {
                    val transition = rememberInfiniteTransition(label = "brandDisc")
                    transition.animateFloat(
                        initialValue = 1f,
                        targetValue = 1.045f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(durationMillis = 3_200, easing = FastOutSlowInEasing),
                            repeatMode = RepeatMode.Reverse,
                        ),
                        label = "brandDiscBreath",
                    ).value
                } else {
                    1f
                }
                Box(
                    modifier = Modifier
                        .graphicsLayer { scaleX = breath; scaleY = breath }
                        .size(148.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    Image(
                        painter = painterResource(R.drawable.ic_fortress_silhouette),
                        contentDescription = null, // decorative; the title carries the meaning
                        colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(MaterialTheme.colorScheme.primary),
                        modifier = Modifier.size(112.dp),
                    )
                }
                Text(
                    text = stringResource(R.string.unlock_title),
                    style = MaterialTheme.typography.headlineSmall,
                    textAlign = TextAlign.Center,
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { value ->
                        password = value
                        onInputChanged()
                    },
                    label = { Text(stringResource(R.string.unlock_password_label)) },
                    singleLine = true,
                    visualTransformation = if (passwordVisible) {
                        VisualTransformation.None
                    } else {
                        PasswordVisualTransformation()
                    },
                    trailingIcon = {
                        val toggleDescription = stringResource(
                            if (passwordVisible) R.string.entry_hide_password else R.string.entry_show_password,
                        )
                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                            Icon(
                                painter = androidx.compose.ui.res.painterResource(
                                    if (passwordVisible) R.drawable.ic_visibility_off else R.drawable.ic_visibility,
                                ),
                                contentDescription = toggleDescription,
                            )
                        }
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(
                        onDone = { submitAndClear() },
                    ),
                    isError = state.error != null,
                    supportingText = {
                        when {
                            backoffActive -> Text(
                                pluralStringResource(
                                    R.plurals.unlock_backoff_seconds,
                                    state.backoffRemainingSeconds,
                                    state.backoffRemainingSeconds,
                                ),
                            )
                            state.error == UnlockError.WrongPassword -> Text(stringResource(R.string.unlock_error_wrong))
                            state.error == UnlockError.Backoff -> Text(
                                pluralStringResource(
                                    R.plurals.unlock_backoff_seconds,
                                    state.backoffRemainingSeconds,
                                    state.backoffRemainingSeconds,
                                ),
                            )
                            state.error == UnlockError.Corrupted -> Text(stringResource(R.string.unlock_error_corrupted))
                            state.error == UnlockError.NotSetUp -> Text(stringResource(R.string.unlock_error_corrupted))
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                Button(
                    onClick = submitAndClear,
                    enabled = canSubmit(state, password),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (state.isSubmitting) {
                        CircularProgressIndicator(modifier = Modifier.padding(end = 8.dp).fillMaxWidth(BUTTON_PROGRESS_FRACTION))
                    }
                    Text(stringResource(R.string.unlock_submit))
                }
            }
        }
    }
}

private const val BUTTON_PROGRESS_FRACTION = 0.12f

private fun canSubmit(state: UnlockUiState, password: String): Boolean =
    password.isNotEmpty() && !state.isSubmitting && state.backoffRemainingSeconds == 0
