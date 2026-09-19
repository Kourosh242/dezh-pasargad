package com.pasargad.dezh.ui.theme

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer

/**
 * Brand motion — Material 3 Expressive spring philosophy distilled to the
 * four canonical specs (skill: m3-expressive, §Spring Specifications):
 *  bouncy  → playful emphasis (favorite heart, hero reveals)
 *  snappy  → responsive micro-interactions (press scale)
 *  gentle  → large calm transitions
 *  critical→ precise, no overshoot (meter progress)
 * All decorative motion is gated by [LocalMotionEnabled].
 */
object DezhMotion {
    fun bouncy() = spring<Float>(dampingRatio = 0.4f, stiffness = 400f)
    fun snappy() = spring<Float>(dampingRatio = 0.75f, stiffness = 1_000f)
    fun gentle() = spring<Float>(dampingRatio = 0.6f, stiffness = 200f)
    fun critical() = spring<Float>(dampingRatio = 1f, stiffness = 800f)

    fun <T> springBouncy() = spring<T>(dampingRatio = 0.4f, stiffness = 400f)
    fun <T> springSnappy() = spring<T>(dampingRatio = 0.75f, stiffness = 1_000f)
    fun <T> springCritical() = spring<T>(dampingRatio = 1f, stiffness = 800f)
}

/**
 * Press feedback per pro-rules ("stable interaction states"): a subtle scale
 * with a snappy spring — no layout shift, no elevation jump.
 */
fun Modifier.pressScale(
    interactionSource: MutableInteractionSource,
    pressedScale: Float = 0.97f,
): Modifier = composed {
    val motionEnabled = LocalMotionEnabled.current
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) pressedScale else 1f,
        animationSpec = if (motionEnabled) DezhMotion.springSnappy() else spring(stiffness = Spring.StiffnessHigh),
        label = "pressScale",
    )
    graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}
