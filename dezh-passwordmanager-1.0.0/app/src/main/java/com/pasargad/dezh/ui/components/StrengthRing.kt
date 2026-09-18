package com.pasargad.dezh.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.pasargad.dezh.ui.theme.DezhMotion
import com.pasargad.dezh.ui.theme.LocalMotionEnabled

private const val SCORE_MIN = 0
private const val SCORE_MAX = 100
private const val RING_STROKE_FRACTION = 0.10f
private const val RING_START_ANGLE = 135f
private const val RING_SWEEP_ANGLE = 270f

/**
 * Animated strength ring (M3 Expressive "data as decoration"): a 270° track
 * with a primary→tertiary gradient sweep proportional to the 0..100 score.
 * Purely decorative — the textual band label lives next to it.
 */
@Composable
fun StrengthRing(
    score: Int,
    modifier: Modifier = Modifier,
    size: Dp = 52.dp,
    trackColor: Color = MaterialTheme.colorScheme.surfaceContainerHighest,
) {
    val motionEnabled = LocalMotionEnabled.current
    val animated by animateFloatAsState(
        targetValue = score.coerceIn(SCORE_MIN, SCORE_MAX) / SCORE_MAX.toFloat(),
        animationSpec = if (motionEnabled) DezhMotion.springCritical() else androidx.compose.animation.core.snap(),
        label = "strengthRing",
    )
    val sweepGradient = Brush.sweepGradient(
        listOf(
            MaterialTheme.colorScheme.primary,
            MaterialTheme.colorScheme.tertiary,
            MaterialTheme.colorScheme.primary,
        ),
    )
    Canvas(modifier = modifier.size(size)) {
        val stroke = this.size.minDimension * RING_STROKE_FRACTION
        val inset = stroke / 2
        val arcSize = Size(this.size.minDimension - stroke, this.size.minDimension - stroke)
        drawArc(
            color = trackColor,
            startAngle = RING_START_ANGLE,
            sweepAngle = RING_SWEEP_ANGLE,
            useCenter = false,
            topLeft = Offset(inset, inset),
            size = arcSize,
            style = Stroke(width = stroke, cap = StrokeCap.Round),
        )
        drawArc(
            brush = sweepGradient,
            startAngle = RING_START_ANGLE,
            sweepAngle = RING_SWEEP_ANGLE * animated,
            useCenter = false,
            topLeft = Offset(inset, inset),
            size = arcSize,
            style = Stroke(width = stroke, cap = StrokeCap.Round),
        )
    }
}
