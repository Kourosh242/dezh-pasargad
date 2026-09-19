package com.pasargad.dezh.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import kotlinx.coroutines.delay

/**
 * Staggered entrance: fades/slides list items in with a small per-index delay
 * (M3 Expressive choreography feel). Respects the motion toggle — without
 * motion, content appears immediately. Runs only on first composition.
 */
@Composable
fun StaggerIn(
    index: Int,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val motionEnabled = com.pasargad.dezh.ui.theme.LocalMotionEnabled.current
    var visible by remember { mutableStateOf(!motionEnabled) }
    LaunchedEffect(motionEnabled) {
        if (motionEnabled && !visible) {
            delay(((index * STAGGER_STEP_MS).coerceAtMost(STAGGER_MAX_MS)).toLong())
            visible = true
        }
    }
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = fadeIn(animationSpec = tween(STAGGER_FADE_MS)) +
            slideInVertically(
                animationSpec = spring<IntOffset>(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMediumLow),
                initialOffsetY = { it / 6 },
            ),
    ) { content() }
}

private const val STAGGER_FADE_MS = 220
private const val STAGGER_STEP_MS = 40
private const val STAGGER_MAX_MS = 320

/**
 * Category avatar styling: a stable tonal pair (container/onContainer) plus an
 * icon for well-known category families — the password-manager pattern of
 * giving every item an instant visual identity.
 */
data class CategoryAvatarStyle(
    val container: Color,
    val onContainer: Color,
    val icon: ImageVector?,
)

private enum class AvatarFamily { FINANCE, MAIL, WORK, SOCIAL, BLANK, HASHED }

private fun familyFor(lower: String): AvatarFamily? = when {
    lower.contains("بانک") || lower.contains("bank") || lower.contains("مالی") -> AvatarFamily.FINANCE
    lower.contains("ایمیل") || lower.contains("mail") -> AvatarFamily.MAIL
    lower.contains("کار") || lower.contains("سرور") || lower.contains("work") -> AvatarFamily.WORK
    lower.contains("شخصی") || lower.contains("سوشال") || lower.contains("social") -> AvatarFamily.SOCIAL
    lower.isBlank() -> AvatarFamily.BLANK
    else -> null
}

private const val HASH_ROTATION = 3

@Composable
private fun styleFor(family: AvatarFamily, category: String): CategoryAvatarStyle = when (family) {
    AvatarFamily.FINANCE -> CategoryAvatarStyle(
        MaterialTheme.colorScheme.primaryContainer,
        MaterialTheme.colorScheme.onPrimaryContainer,
        Icons.Filled.Star,
    )
    AvatarFamily.MAIL -> CategoryAvatarStyle(
        MaterialTheme.colorScheme.secondaryContainer,
        MaterialTheme.colorScheme.onSecondaryContainer,
        null,
    )
    AvatarFamily.WORK -> CategoryAvatarStyle(
        MaterialTheme.colorScheme.tertiaryContainer,
        MaterialTheme.colorScheme.onTertiaryContainer,
        Icons.Filled.Build,
    )
    AvatarFamily.SOCIAL -> CategoryAvatarStyle(
        MaterialTheme.colorScheme.primaryContainer,
        MaterialTheme.colorScheme.onPrimaryContainer,
        Icons.Filled.Favorite,
    )
    AvatarFamily.BLANK -> CategoryAvatarStyle(
        MaterialTheme.colorScheme.surfaceContainerHigh,
        MaterialTheme.colorScheme.onSurfaceVariant,
        null,
    )
    AvatarFamily.HASHED -> when (kotlin.math.abs(category.hashCode()) % HASH_ROTATION) {
        0 -> CategoryAvatarStyle(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer, null)
        1 -> CategoryAvatarStyle(MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.onSecondaryContainer, null)
        else -> CategoryAvatarStyle(MaterialTheme.colorScheme.tertiaryContainer, MaterialTheme.colorScheme.onTertiaryContainer, null)
    }
}

@Composable
fun rememberCategoryAvatarStyle(category: String): CategoryAvatarStyle {
    val family = familyFor(category.trim().lowercase()) ?: AvatarFamily.HASHED
    return styleFor(family, category)
}
