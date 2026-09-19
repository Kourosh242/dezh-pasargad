package com.pasargad.dezh.presentation.setup

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pasargad.dezh.R
import com.pasargad.dezh.R as AppR
import com.pasargad.dezh.domain.PasswordStrengthValidator
import com.pasargad.dezh.ui.theme.LocalMotionEnabled

/**
 * First-run setup: master password creation + confirmation + live strength feedback.
 * Stateless UI — all logic lives in [SetupViewModel].
 */
@Composable
fun SetupScreen(viewModel: SetupViewModel, modifier: Modifier = Modifier) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    SetupScreen(
        state = state,
        onPasswordChanged = { raw -> viewModel.onPasswordChanged(raw.toCharArray()) },
        onConfirmationChanged = { password, confirmation ->
            viewModel.onConfirmationChanged(password == confirmation)
        },
        onSubmit = { password, confirmation ->
            viewModel.submit(password.toCharArray(), confirmation.toCharArray())
        },
        modifier = modifier,
    )
}

@Composable
fun SetupScreen(
    state: SetupUiState,
    onPasswordChanged: (String) -> Unit,
    onConfirmationChanged: (password: String, confirmation: String) -> Unit,
    onSubmit: (password: String, confirmation: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var password by rememberSaveable { mutableStateOf("") }
    var confirmation by rememberSaveable { mutableStateOf("") }
    var passwordVisible by rememberSaveable { mutableStateOf(false) }
    var confirmationVisible by rememberSaveable { mutableStateOf(false) }
    val motionEnabled = LocalMotionEnabled.current

    Scaffold(modifier = modifier) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            androidx.compose.foundation.layout.Box(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .size(124.dp)
                    .background(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = androidx.compose.foundation.shape.CircleShape,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    painter = painterResource(AppR.drawable.ic_fortress_silhouette),
                    contentDescription = null, // decorative
                    colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(MaterialTheme.colorScheme.primary),
                    modifier = Modifier.size(96.dp),
                )
            }
            Text(
                text = stringResource(R.string.setup_title),
                style = MaterialTheme.typography.headlineSmall,
            )
            Text(
                text = stringResource(R.string.setup_description),
                style = MaterialTheme.typography.bodyMedium,
            )

            OutlinedTextField(
                value = password,
                onValueChange = { value ->
                    password = value
                    onPasswordChanged(value)
                    onConfirmationChanged(value, confirmation)
                },
                label = { Text(stringResource(R.string.setup_password_label)) },
                singleLine = true,
                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    VisibilityToggle(passwordVisible) { passwordVisible = !passwordVisible }
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Next),
                modifier = Modifier.fillMaxWidth(),
            )

            val targetProgress = strengthProgress(state.rating)
            val strength by animateFloatAsState(
                targetValue = targetProgress,
                animationSpec = tween(durationMillis = if (motionEnabled) 250 else 0),
                label = "strengthMeter",
            )
            LinearProgressIndicator(
                progress = { strength },
                color = strengthColor(state.rating),
                trackColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.fillMaxWidth(),
            )
            if (state.rating != null) {
                Text(
                    text = stringResource(strengthLabel(state.rating)),
                    style = MaterialTheme.typography.labelLarge,
                )
            }
            state.failures.forEach { failure ->
                Text(
                    text = stringResource(failureLabel(failure)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            OutlinedTextField(
                value = confirmation,
                onValueChange = { value ->
                    confirmation = value
                    onConfirmationChanged(password, value)
                },
                label = { Text(stringResource(R.string.setup_confirm_label)) },
                singleLine = true,
                visualTransformation = if (confirmationVisible) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    VisibilityToggle(confirmationVisible) { confirmationVisible = !confirmationVisible }
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(
                    onDone = { if (canSubmit(state, password, confirmation)) onSubmit(password, confirmation) },
                ),
                isError = !state.passwordsMatch,
                supportingText = {
                    if (!state.passwordsMatch) {
                        Text(stringResource(R.string.setup_mismatch))
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )

            if (state.error != null) {
                Text(
                    text = stringResource(R.string.setup_error),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            Button(
                onClick = {
                    onSubmit(password, confirmation)
                    password = ""
                    confirmation = ""
                },
                enabled = canSubmit(state, password, confirmation),
                modifier = Modifier.align(Alignment.CenterHorizontally),
            ) {
                if (state.isSubmitting) {
                    CircularProgressIndicator(modifier = Modifier.padding(end = 8.dp).fillMaxWidth(BUTTON_PROGRESS_FRACTION))
                }
                Text(stringResource(R.string.setup_submit))
            }
        }
    }
}

private fun canSubmit(state: SetupUiState, password: String, confirmation: String): Boolean =
    password.isNotEmpty() && confirmation.isNotEmpty() && state.isValid && state.passwordsMatch && !state.isSubmitting

private const val BUTTON_PROGRESS_FRACTION = 0.12f
private const val STRENGTH_PROGRESS_STRONG = 1f
private const val STRENGTH_PROGRESS_FAIR = 0.6f
private const val STRENGTH_PROGRESS_WEAK = 0.25f

private fun strengthProgress(rating: PasswordStrengthValidator.Rating?): Float = when (rating) {
    PasswordStrengthValidator.Rating.Strong -> STRENGTH_PROGRESS_STRONG
    PasswordStrengthValidator.Rating.Fair -> STRENGTH_PROGRESS_FAIR
    PasswordStrengthValidator.Rating.Weak -> STRENGTH_PROGRESS_WEAK
    null -> 0f
}

private fun strengthLabel(rating: PasswordStrengthValidator.Rating?): Int = when (rating) {
    PasswordStrengthValidator.Rating.Strong -> R.string.strength_strong
    PasswordStrengthValidator.Rating.Fair -> R.string.strength_fair
    PasswordStrengthValidator.Rating.Weak -> R.string.strength_weak
    null -> R.string.strength_weak
}

private fun failureLabel(failure: PasswordStrengthValidator.Failure): Int = when (failure) {
    PasswordStrengthValidator.Failure.TOO_SHORT -> R.string.failure_too_short
    PasswordStrengthValidator.Failure.TOO_LONG -> R.string.failure_too_long
    PasswordStrengthValidator.Failure.NOT_ENOUGH_CHARACTER_CLASSES -> R.string.failure_classes
    PasswordStrengthValidator.Failure.COMMON_PASSWORD -> R.string.failure_common
    PasswordStrengthValidator.Failure.REPEATED_SEQUENCE -> R.string.failure_repeated
}

/** Eye/eye-off toggle with a proper spoken label (accessibility rule). */
@Composable
private fun VisibilityToggle(visible: Boolean, onToggle: () -> Unit) {
    val toggleDescription = stringResource(
        if (visible) R.string.entry_hide_password else R.string.entry_show_password,
    )
    IconButton(onClick = onToggle) {
        Icon(
            painter = painterResource(
                if (visible) AppR.drawable.ic_visibility_off else AppR.drawable.ic_visibility,
            ),
            contentDescription = toggleDescription,
        )
    }
}

@Composable
private fun strengthColor(rating: PasswordStrengthValidator.Rating?) = when (rating) {
    PasswordStrengthValidator.Rating.Weak -> MaterialTheme.colorScheme.error
    PasswordStrengthValidator.Rating.Fair -> MaterialTheme.colorScheme.tertiary
    PasswordStrengthValidator.Rating.Strong -> MaterialTheme.colorScheme.primary
    null -> MaterialTheme.colorScheme.surfaceContainerHighest
}
