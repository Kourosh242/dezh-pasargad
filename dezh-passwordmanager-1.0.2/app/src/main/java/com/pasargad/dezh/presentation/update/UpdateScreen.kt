package com.pasargad.dezh.presentation.update

import android.content.Intent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pasargad.dezh.R
import com.pasargad.dezh.domain.update.Verdict
import com.pasargad.dezh.presentation.update.UpdateSideEffect.LaunchInstallConfirmation
import com.pasargad.dezh.presentation.update.UpdateSideEffect.RequestInstallPermission
import com.pasargad.dezh.ui.theme.pressScale

/**
 * Manual update-check + in-app self-update screen. The install path is the
 * official PackageInstaller flow: download from the release channel, show the
 * APK facts, at most one "install unknown apps" settings visit, then the
 * system's own confirmation dialog decides — this screen never talks to any
 * store and never tries to influence Play Protect or any security gate.
 */
@Composable
fun UpdateScreen(
    viewModel: UpdateViewModel,
    onOpenReleasePage: (String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // One-shot system launches: settings permission page, official confirm dialog.
    LaunchedEffect(Unit) {
        viewModel.sideEffects.collect { effect ->
            val intent = when (effect) {
                is RequestInstallPermission -> effect.intent
                is LaunchInstallConfirmation -> effect.intent
            }
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }

    // Settings round trip: when we come back, re-check the permission once.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.onHostResumed()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Scaffold(modifier = modifier) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(R.string.update_title),
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.semantics { heading() },
                )
                TextButton(onClick = onBack) { Text(stringResource(R.string.action_back)) }
            }
            Spacer(Modifier.height(24.dp))
            BrandDisc()
            Text(
                text = stringResource(R.string.update_current_version, state.currentVersion),
                style = MaterialTheme.typography.titleMedium,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.padding(top = 20.dp),
            )
            Spacer(Modifier.height(12.dp))
            when (val status = state.status) {
                UpdateStatus.Idle -> {
                    Text(
                        text = stringResource(R.string.update_idle_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(20.dp))
                    PrimaryActionButton(text = stringResource(R.string.update_check_action)) { viewModel.check() }
                }
                UpdateStatus.Checking -> {
                    Spacer(Modifier.height(32.dp))
                    CircularProgressIndicator(
                        modifier = Modifier.size(44.dp),
                        strokeWidth = 4.dp,
                        trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = stringResource(R.string.update_checking),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                UpdateStatus.UpToDate -> {
                    StatusRow(
                        icon = Icons.Filled.CheckCircle,
                        tint = MaterialTheme.colorScheme.primary,
                        text = stringResource(R.string.update_up_to_date),
                    )
                    Spacer(Modifier.height(20.dp))
                    PrimaryActionButton(text = stringResource(R.string.update_retry)) { viewModel.check() }
                }
                is UpdateStatus.Available -> {
                    Spacer(Modifier.height(16.dp))
                    UpdateInstallCard(
                        status = status,
                        installStep = state.installStep,
                        onDownload = viewModel::downloadUpdate,
                        onProceedInstall = viewModel::proceedToInstall,
                        onReopenSettings = viewModel::reopenInstallSettings,
                        onOpenReleasePage = onOpenReleasePage,
                    )
                }
                is UpdateStatus.Failed -> {
                    StatusRow(
                        icon = Icons.Filled.Warning,
                        tint = MaterialTheme.colorScheme.error,
                        text = stringResource(R.string.update_check_failed),
                    )
                    if (status.message?.contains("apk", ignoreCase = true) == true) {
                        Text(
                            text = stringResource(R.string.update_failed_no_asset),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                    }
                    Spacer(Modifier.height(20.dp))
                    PrimaryActionButton(text = stringResource(R.string.update_retry)) { viewModel.check() }
                }
            }
        }
    }
}

@Composable
private fun UpdateInstallCard(
    status: UpdateStatus.Available,
    installStep: InstallStep,
    onDownload: () -> Unit,
    onProceedInstall: () -> Unit,
    onReopenSettings: () -> Unit,
    onOpenReleasePage: (String) -> Unit,
) {
    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.update_available_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = stringResource(R.string.update_available_desc, status.latestVersion),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            InstallStepSection(
                step = installStep,
                onDownload = onDownload,
                onProceedInstall = onProceedInstall,
                onReopenSettings = onReopenSettings,
                onOpenReleasePage = { onOpenReleasePage(status.releaseUrl) },
            )
        }
    }
}

@Composable
internal fun StatusRow(icon: ImageVector, tint: Color, text: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = tint)
        Text(text = text, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun BrandDisc() {
    Box(
        modifier = Modifier
            .size(112.dp)
            .background(
                brush = Brush.linearGradient(
                    listOf(
                        MaterialTheme.colorScheme.primaryContainer,
                        MaterialTheme.colorScheme.secondaryContainer,
                    ),
                ),
                shape = CircleShape,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(R.drawable.ic_fortress_silhouette),
            contentDescription = null, // decorative; the title carries the meaning
            colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.primary),
            modifier = Modifier.size(52.dp),
        )
    }
}

@Composable
internal fun PrimaryActionButton(text: String, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    Button(
        onClick = onClick,
        interactionSource = interaction,
        modifier = Modifier.pressScale(interaction),
    ) {
        Text(text)
    }
}
