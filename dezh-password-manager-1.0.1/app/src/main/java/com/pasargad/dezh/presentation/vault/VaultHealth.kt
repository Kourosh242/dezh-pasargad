package com.pasargad.dezh.presentation.vault

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.pasargad.dezh.R
import com.pasargad.dezh.domain.PasswordStrengthMeter
import com.pasargad.dezh.domain.VaultEntry
import com.pasargad.dezh.ui.theme.LocalMotionEnabled

/** Health band of one entry password (mirrors [PasswordStrengthMeter.Band]). */
enum class PasswordHealthBand { WEAK, FAIR, STRONG }

/** Aggregate password health of the visible vault — counts only, never scores. */
data class VaultHealthSummary(
    val weak: Int = 0,
    val fair: Int = 0,
    val strong: Int = 0,
) {
    val total: Int get() = weak + fair + strong
    val needsAttention: Boolean get() = weak > 0
}

/**
 * Password health derived from the NIST-based meter already shipped in the
 * app. Qualitative bands only — per product rule the meter never shows a
 * number, so the aggregate ring shows PROPORTIONS, not a score.
 */
object VaultHealth {

    fun summarize(entries: List<VaultEntry>, meter: PasswordStrengthMeter): VaultHealthSummary {
        var weak = 0
        var fair = 0
        var strong = 0
        entries.forEach { entry ->
            if (entry.password.isEmpty()) return@forEach
            when (meter.evaluate(entry.password.toCharArray()).band) {
                PasswordStrengthMeter.Band.WEAK -> weak++
                PasswordStrengthMeter.Band.FAIR -> fair++
                PasswordStrengthMeter.Band.STRONG -> strong++
            }
        }
        return VaultHealthSummary(weak = weak, fair = fair, strong = strong)
    }

    fun bandOf(entry: VaultEntry, meter: PasswordStrengthMeter): PasswordHealthBand? {
        if (entry.password.isEmpty()) return null
        return when (meter.evaluate(entry.password.toCharArray()).band) {
            PasswordStrengthMeter.Band.WEAK -> PasswordHealthBand.WEAK
            PasswordStrengthMeter.Band.FAIR -> PasswordHealthBand.FAIR
            PasswordStrengthMeter.Band.STRONG -> PasswordHealthBand.STRONG
        }
    }
}

/**
 * Compact health card above the vault list (mobile reference pattern: a
 * security-score disc in the header). The ring shows the PROPORTION of
 * strong/fair/weak passwords; the caption is qualitative — no numeric score.
 */
@Composable
fun SecurityScoreCard(summary: VaultHealthSummary, modifier: Modifier = Modifier) {
    if (summary.total == 0) return
    val motionEnabled = LocalMotionEnabled.current
    var played by remember { mutableStateOf(false) }
    LaunchedEffect(summary) { played = true }
    val progress by animateFloatAsState(
        targetValue = if (played) 1f else 0f,
        animationSpec = if (motionEnabled) tween(durationMillis = RING_SWEEP_MS) else snap(),
        label = "healthRingProgress",
    )
    val statusText = stringResource(
        if (summary.needsAttention) R.string.vault_health_attention else R.string.vault_health_ok,
    )
    val description = stringResource(R.string.vault_health_title) + "، " + statusText
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = modifier
            .fillMaxWidth()
            .semantics { contentDescription = description },
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            HealthRing(
                summary = summary,
                progress = progress,
                description = description,
                modifier = Modifier.size(52.dp),
            )
            Column {
                Text(
                    text = stringResource(R.string.vault_health_title),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = statusText,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                    color = if (summary.needsAttention) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
                )
            }
        }
    }
}

@Composable
private fun HealthRing(
    summary: VaultHealthSummary,
    progress: Float,
    description: String,
    modifier: Modifier = Modifier,
) {
    val strongColor = MaterialTheme.colorScheme.primary
    val fairColor = MaterialTheme.colorScheme.tertiary
    val weakColor = MaterialTheme.colorScheme.error
    val trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
    Canvas(modifier = modifier.semantics { contentDescription = description }) {
        val stroke = Stroke(width = 6.5.dp.toPx(), cap = StrokeCap.Round)
        val diameter = size.minDimension - stroke.width
        val topLeft = Offset(stroke.width / 2f, stroke.width / 2f)
        val arcSize = Size(diameter, diameter)
        drawArc(
            color = trackColor,
            startAngle = 0f,
            sweepAngle = 360f,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = stroke,
        )
        val gap = 8f
        val shares = listOf(
            strongColor to summary.strong,
            fairColor to summary.fair,
            weakColor to summary.weak,
        ).filter { it.second > 0 }
        if (shares.isEmpty()) return@Canvas
        val usable = FULL_CIRCLE_DEGREES - gap * shares.size
        var start = TOP_START_ANGLE
        shares.forEach { (color, count) ->
            val sweep = progress * (usable * count / summary.total)
            drawArc(
                color = color,
                startAngle = start,
                sweepAngle = sweep,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = stroke,
            )
            start += sweep + gap
        }
    }
}

/** Row-level health cue (1Password Watchtower pattern): icon + honest tint. */
@Composable
fun PasswordHealthBadge(band: PasswordHealthBand, modifier: Modifier = Modifier) {
    val description = stringResource(
        when (band) {
            PasswordHealthBand.STRONG -> R.string.health_strong
            PasswordHealthBand.FAIR -> R.string.health_fair
            PasswordHealthBand.WEAK -> R.string.health_weak
        },
    )
    val (image, tint) = when (band) {
        PasswordHealthBand.STRONG ->
            Icons.Filled.CheckCircle to MaterialTheme.colorScheme.primary
        PasswordHealthBand.FAIR ->
            Icons.Outlined.Warning to MaterialTheme.colorScheme.tertiary
        PasswordHealthBand.WEAK ->
            Icons.Filled.Warning to MaterialTheme.colorScheme.error
    }
    Icon(
        imageVector = image,
        contentDescription = description,
        tint = tint,
        modifier = modifier.size(18.dp),
    )
}

private const val RING_SWEEP_MS = 650

private const val FULL_CIRCLE_DEGREES = 360f
private const val TOP_START_ANGLE = -90f
