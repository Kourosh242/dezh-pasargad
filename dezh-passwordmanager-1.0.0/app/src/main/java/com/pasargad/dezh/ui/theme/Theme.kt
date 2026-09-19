package com.pasargad.dezh.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.dp

private val LightColorScheme = lightColorScheme(
    primary = GoldPrimaryLight,
    onPrimary = OnPrimaryLight,
    primaryContainer = PrimaryContainerLight,
    onPrimaryContainer = OnPrimaryContainerLight,
    secondary = BronzeSecondaryLight,
    onSecondary = OnSecondaryLight,
    secondaryContainer = SecondaryContainerLight,
    onSecondaryContainer = OnSecondaryContainerLight,
    tertiary = LapisTertiaryLight,
    onTertiary = OnTertiaryLight,
    tertiaryContainer = TertiaryContainerLight,
    onTertiaryContainer = OnTertiaryContainerLight,
    error = ErrorLight,
    onError = OnErrorLight,
    errorContainer = ErrorContainerLight,
    onErrorContainer = OnErrorContainerLight,
    background = BackgroundLight,
    onBackground = OnBackgroundLight,
    surface = SurfaceLight,
    onSurface = OnSurfaceLight,
    surfaceVariant = SurfaceVariantLight,
    onSurfaceVariant = OnSurfaceVariantLight,
    outline = OutlineLight,
    outlineVariant = OutlineVariantLight,
    surfaceContainerLowest = SurfaceContainerLowestLight,
    surfaceContainerLow = SurfaceContainerLowLight,
    surfaceContainer = SurfaceContainerLight,
    surfaceContainerHigh = SurfaceContainerHighLight,
    surfaceContainerHighest = SurfaceContainerHighestLight,
)

private val DarkColorScheme = darkColorScheme(
    primary = GoldPrimaryDark,
    onPrimary = OnPrimaryDark,
    primaryContainer = PrimaryContainerDark,
    onPrimaryContainer = OnPrimaryContainerDark,
    secondary = BronzeSecondaryDark,
    onSecondary = OnSecondaryDark,
    secondaryContainer = SecondaryContainerDark,
    onSecondaryContainer = OnSecondaryContainerDark,
    tertiary = LapisTertiaryDark,
    onTertiary = OnTertiaryDark,
    tertiaryContainer = TertiaryContainerDark,
    onTertiaryContainer = OnTertiaryContainerDark,
    error = ErrorDark,
    onError = OnErrorDark,
    errorContainer = ErrorContainerDark,
    onErrorContainer = OnErrorContainerDark,
    background = BackgroundDark,
    onBackground = OnBackgroundDark,
    surface = SurfaceDark,
    onSurface = OnSurfaceDark,
    surfaceVariant = SurfaceVariantDark,
    onSurfaceVariant = OnSurfaceVariantDark,
    outline = OutlineDark,
    outlineVariant = OutlineVariantDark,
    surfaceContainerLowest = SurfaceContainerLowestDark,
    surfaceContainerLow = SurfaceContainerLowDark,
    surfaceContainer = SurfaceContainerDark,
    surfaceContainerHigh = SurfaceContainerHighDark,
    surfaceContainerHighest = SurfaceContainerHighestDark,
)

@Composable
fun DezhTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    accentColor: com.pasargad.dezh.settings.AccentColor = com.pasargad.dezh.settings.AccentColor.DEFAULT,
    content: @Composable () -> Unit,
) {
    // Brand-first theming: DEFAULT is the fortress GOLD scheme. Wallpaper-derived
    // dynamic color was removed — it silently produced a blue theme on many
    // devices while the setting still said "gold", which is a naming/trust bug.
    val colorScheme = when (accentColor) {
        com.pasargad.dezh.settings.AccentColor.DEFAULT -> if (darkTheme) DarkColorScheme else LightColorScheme
        else -> {
            val base = if (darkTheme) DarkColorScheme else LightColorScheme
            base.copy(
                primary = if (darkTheme) accentPrimaryDark(accentColor) else accentPrimaryLight(accentColor),
                secondary = if (darkTheme) accentSecondaryDark(accentColor) else accentSecondaryLight(accentColor),
            )
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = AppTypography,
        shapes = DezhShapes,
        content = content,
    )
}

/** Soft-cornered shape scale — friendlier cards without losing the fortress feel. */
private val DezhShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(18.dp),
    extraLarge = RoundedCornerShape(26.dp),
)

/**
 * Motion preference: the user's explicit پویانمایی toggle (and any future
 * system reduced-motion signals) gates every decorative animation. Screens
 * read this instead of threading the setting through every signature.
 */
val LocalMotionEnabled = staticCompositionLocalOf { true }

/** Static accent tokens (design tokens of the theme layer, not UI-code colors). */
private fun accentPrimaryLight(accent: com.pasargad.dezh.settings.AccentColor) = when (accent) {
    com.pasargad.dezh.settings.AccentColor.DEFAULT -> GoldPrimaryLight
    com.pasargad.dezh.settings.AccentColor.EMERALD -> EmeraldPrimaryLight
    com.pasargad.dezh.settings.AccentColor.AMBER -> AmberPrimaryLight
    com.pasargad.dezh.settings.AccentColor.ROSE -> RosePrimaryLight
    com.pasargad.dezh.settings.AccentColor.VIOLET -> VioletPrimaryLight
}

private fun accentPrimaryDark(accent: com.pasargad.dezh.settings.AccentColor) = when (accent) {
    com.pasargad.dezh.settings.AccentColor.DEFAULT -> GoldPrimaryDark
    com.pasargad.dezh.settings.AccentColor.EMERALD -> EmeraldPrimaryDark
    com.pasargad.dezh.settings.AccentColor.AMBER -> AmberPrimaryDark
    com.pasargad.dezh.settings.AccentColor.ROSE -> RosePrimaryDark
    com.pasargad.dezh.settings.AccentColor.VIOLET -> VioletPrimaryDark
}

private fun accentSecondaryLight(accent: com.pasargad.dezh.settings.AccentColor) = when (accent) {
    com.pasargad.dezh.settings.AccentColor.DEFAULT -> BronzeSecondaryLight
    com.pasargad.dezh.settings.AccentColor.EMERALD -> EmeraldSecondaryLight
    com.pasargad.dezh.settings.AccentColor.AMBER -> AmberSecondaryLight
    com.pasargad.dezh.settings.AccentColor.ROSE -> RoseSecondaryLight
    com.pasargad.dezh.settings.AccentColor.VIOLET -> VioletSecondaryLight
}

private fun accentSecondaryDark(accent: com.pasargad.dezh.settings.AccentColor) = when (accent) {
    com.pasargad.dezh.settings.AccentColor.DEFAULT -> BronzeSecondaryDark
    com.pasargad.dezh.settings.AccentColor.EMERALD -> EmeraldSecondaryDark
    com.pasargad.dezh.settings.AccentColor.AMBER -> AmberSecondaryDark
    com.pasargad.dezh.settings.AccentColor.ROSE -> RoseSecondaryDark
    com.pasargad.dezh.settings.AccentColor.VIOLET -> VioletSecondaryDark
}
