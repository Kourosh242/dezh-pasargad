package com.pasargad.dezh.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.pasargad.dezh.R

/**
 * Brand type system — Vazirmatn variable (OFL), the de-facto standard Persian
 * UI face. One variable font file covers 100–900 weights; API 29+ applies the
 * wght axis, so no multi-file family is bundled.
 *
 * Line heights are tuned generously for Persian ascenders/descenders and the
 * slightly taller Vazirmatn x-height: body line-height ratio ≥ 1.5
 * (base body 16sp).
 */
val VazirmatnFamily = FontFamily(
    Font(
        resId = R.font.vazirmatn,
        weight = FontWeight.Normal,
        variationSettings = FontVariation.Settings(FontVariation.weight(400)),
    ),
    Font(
        resId = R.font.vazirmatn,
        weight = FontWeight.Medium,
        variationSettings = FontVariation.Settings(FontVariation.weight(500)),
    ),
    Font(
        resId = R.font.vazirmatn,
        weight = FontWeight.SemiBold,
        variationSettings = FontVariation.Settings(FontVariation.weight(600)),
    ),
    Font(
        resId = R.font.vazirmatn,
        weight = FontWeight.Bold,
        variationSettings = FontVariation.Settings(FontVariation.weight(700)),
    ),
)

private val VazirDefault = TextStyle(
    fontFamily = VazirmatnFamily,
    letterSpacing = 0.sp, // connected Persian script must not be tracked out
)

val AppTypography = Typography(
    displayLarge = VazirDefault.copy(fontSize = 57.sp, lineHeight = 64.sp, fontWeight = FontWeight.Bold),
    displayMedium = VazirDefault.copy(fontSize = 45.sp, lineHeight = 52.sp, fontWeight = FontWeight.Bold),
    displaySmall = VazirDefault.copy(fontSize = 36.sp, lineHeight = 44.sp, fontWeight = FontWeight.SemiBold),
    headlineLarge = VazirDefault.copy(fontSize = 32.sp, lineHeight = 40.sp, fontWeight = FontWeight.SemiBold),
    headlineMedium = VazirDefault.copy(fontSize = 28.sp, lineHeight = 36.sp, fontWeight = FontWeight.SemiBold),
    headlineSmall = VazirDefault.copy(fontSize = 24.sp, lineHeight = 34.sp, fontWeight = FontWeight.SemiBold),
    titleLarge = VazirDefault.copy(fontSize = 22.sp, lineHeight = 30.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = VazirDefault.copy(fontSize = 16.sp, lineHeight = 26.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.1.sp),
    titleSmall = VazirDefault.copy(fontSize = 14.sp, lineHeight = 22.sp, fontWeight = FontWeight.Medium),
    bodyLarge = VazirDefault.copy(fontSize = 16.sp, lineHeight = 27.sp, fontWeight = FontWeight.Normal),
    bodyMedium = VazirDefault.copy(fontSize = 14.sp, lineHeight = 23.sp, fontWeight = FontWeight.Normal),
    bodySmall = VazirDefault.copy(fontSize = 12.sp, lineHeight = 19.sp, fontWeight = FontWeight.Normal),
    labelLarge = VazirDefault.copy(fontSize = 14.sp, lineHeight = 22.sp, fontWeight = FontWeight.Medium),
    labelMedium = VazirDefault.copy(fontSize = 12.sp, lineHeight = 18.sp, fontWeight = FontWeight.Medium),
    labelSmall = VazirDefault.copy(fontSize = 11.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium),
)
