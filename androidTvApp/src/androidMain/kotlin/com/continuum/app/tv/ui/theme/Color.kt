package com.continuum.app.tv.ui.theme

import androidx.compose.ui.graphics.Color

// tvOS monochrome palette — mirrors iOS Plezy OLED Dark with white-at-opacity for
// every accent. Token names from the legacy lavender palette are kept so existing
// consumers compile unchanged; values now resolve to tvOS-faithful tones.

val ContinuumPrimary = Color(0xFFEDEDED)
val ContinuumOnSurface = Color(0xFFEDEDED)
val ContinuumSecondaryText = Color(0xFFEDEDED).copy(alpha = 0.60f)

val DarkBackground = Color(0xFF000000)
val DarkSurface = Color(0xFF0A0A0A)
val DarkSurfaceVariant = Color(0xFF0E0F12)
val DarkSurfaceElevated = Color(0xFF15171C)

val DarkOnBackground = ContinuumOnSurface
val DarkOnSurface = ContinuumOnSurface
val DarkOnSurfaceVariant = ContinuumSecondaryText
val DarkOnPrimary = DarkBackground

val ErrorRed = Color(0xFFB00020)
val SuccessGreen = Color(0xFF34C759)

val DarkOutline = Color.White.copy(alpha = 0.12f)
val DarkOutlineVariant = Color.White.copy(alpha = 0.08f)
val Scrim = Color(0xFF000000)

// Focus signature — tvOS uses a white-on-white card style with a soft shadow.
val FocusedContainer = ContinuumOnSurface
val FocusedContent = DarkBackground
val SelectedContainer = Color.White.copy(alpha = 0.12f)
val SubtleSurface = Color.White.copy(alpha = 0.08f)
val ElevatedSurface = DarkSurfaceElevated.copy(alpha = 0.80f)

val HeroIndicatorInactive = Color.White.copy(alpha = 0.32f)

// Legacy lavender accent collapses to monochrome white-at-opacity. tvOS does not
// use a chromatic accent — focus state is the lift+glow, progress is white-on-white.
val AccentLavender = ContinuumOnSurface
val AccentLavenderSoft = Color.White.copy(alpha = 0.40f)
val AccentLavenderMuted = Color.White.copy(alpha = 0.15f)

val ContinuumBlueGlow = Color.White.copy(alpha = 0.08f)
val ContinuumBlueBorderIdle = Color.White.copy(alpha = 0.16f)

// Hero scrim — tvOS detail hero uses a 4-stop horizontal-then-vertical fade.
val HeroScrimTop = Color(0x00000000)
val HeroScrimMid = Color.Black.copy(alpha = 0.40f)
val HeroScrimLower = Color.Black.copy(alpha = 0.75f)
val HeroScrimBottom = Color.Black.copy(alpha = 0.95f)

val RailGradientStart = Color.Black.copy(alpha = 0.90f)
val RailGradientEnd = Color(0x00000000)

val CardShadowColor = Color.Black.copy(alpha = 0.55f)

// Resume bar uses pure white over a dim track on tvOS — no chroma.
val ProgressTrack = Color.White.copy(alpha = 0.20f)
val ProgressFill = ContinuumOnSurface

val StageLightStrong = Color.White.copy(alpha = 0.15f)
val StageLightSoft = Color.White.copy(alpha = 0.08f)

// Backwards-compatible aliases — name retained, value remapped to tvOS tones.
val ContinuumBlue = ContinuumOnSurface
val ContinuumBlueLight = ContinuumOnSurface
val ContinuumBlueDark = ContinuumSecondaryText
