package com.continuum.app.tv.ui.theme

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Typography

// tvOS uses system SF in heavy/black weights for hero copy. The closest Android
// analog is the platform default sans (Roboto) with FontWeight.Black on display
// styles. Token names from the legacy Outfit-based scale are kept so consumers
// compile unchanged.

val OutfitFamily: FontFamily = FontFamily.Default

val ContinuumTvTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Black,
        fontSize = 44.sp,
        lineHeight = 48.sp,
        letterSpacing = 0.sp,
    ),
    displayMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Black,
        fontSize = 36.sp,
        lineHeight = 40.sp,
        letterSpacing = 0.sp,
    ),
    displaySmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Black,
        fontSize = 28.sp,
        lineHeight = 32.sp,
        letterSpacing = 0.sp,
    ),
    headlineLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 28.sp,
        lineHeight = 34.sp,
        letterSpacing = 0.sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 24.sp,
        lineHeight = 30.sp,
        letterSpacing = 0.sp,
    ),
    headlineSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = 0.sp,
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = 0.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 26.sp,
        letterSpacing = 0.sp,
    ),
    titleSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 20.sp,
        lineHeight = 28.sp,
        letterSpacing = 0.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 18.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 22.sp,
        letterSpacing = 0.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp,
        lineHeight = 22.sp,
        letterSpacing = 0.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.sp,
    ),
)

// Hero fallback title when no logo image is available — the tvOS detail hero
// caps at 92pt for series titles.
val heroDisplay = TextStyle(
    fontFamily = FontFamily.Default,
    fontWeight = FontWeight.Black,
    fontSize = 58.sp,
    lineHeight = 64.sp,
    letterSpacing = 0.sp,
)

// Hero metadata row — tvOS uses 22pt medium with positive tracking for the
// year • runtime • rating chips above the overview.
val heroMeta = TextStyle(
    fontFamily = FontFamily.Default,
    fontWeight = FontWeight.SemiBold,
    fontSize = 18.sp,
    lineHeight = 22.sp,
    letterSpacing = 0.sp,
)

// Top menu / nav button label.
val navRailLabel = TextStyle(
    fontFamily = FontFamily.Default,
    fontWeight = FontWeight.SemiBold,
    fontSize = 18.sp,
    lineHeight = 22.sp,
    letterSpacing = 0.sp,
)

// Eyebrow pill above hero copy — small caps with tracking, matches tvOS facts row.
val sectionEyebrow = TextStyle(
    fontFamily = FontFamily.Default,
    fontWeight = FontWeight.Black,
    fontSize = 16.sp,
    lineHeight = 20.sp,
    letterSpacing = 0.sp,
)

// Tiny tracked-caps capsule label (e.g. the episode-rail "NOW VIEWING" badge).
// Smallest readable 10-foot caps token — keeps the badge compact while staying
// above the readability floor.
val capsuleCaps = TextStyle(
    fontFamily = FontFamily.Default,
    fontWeight = FontWeight.Black,
    fontSize = 16.sp,
    lineHeight = 18.sp,
    letterSpacing = 1.6.sp,
)

// Mono uppercase group header (e.g. the grouped-collections grid header).
// Wide tracking, monospace; smallest readable 10-foot size.
val monoGroupHeader = TextStyle(
    fontFamily = FontFamily.Monospace,
    fontWeight = FontWeight.Normal,
    fontSize = 16.sp,
    lineHeight = 20.sp,
    letterSpacing = 3.6.sp,
)
