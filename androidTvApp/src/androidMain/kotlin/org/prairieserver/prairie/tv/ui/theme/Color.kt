package org.prairieserver.prairie.tv.ui.theme

import androidx.compose.ui.graphics.Color

// Prairie Dusk for Android TV — keep in sync with phone Color.kt / iOS / smarttv.

val PrairiePrimary = Color(0xFFE0A84A)
val PrairieOnSurface = Color(0xFFF2EEE6)
// 0.75: 10-ft legibility floor for supporting text.
val PrairieSecondaryText = Color(0xFF9AA3B2).copy(alpha = 0.95f)

val DarkBackground = Color(0xFF141820)
val DarkSurface = Color(0xFF1C222C)
val DarkSurfaceVariant = Color(0xFF0E1116)
val DarkSurfaceElevated = Color(0xFF222B38)

val DarkOnBackground = PrairieOnSurface
val DarkOnSurface = PrairieOnSurface
val DarkOnSurfaceVariant = PrairieSecondaryText
val DarkOnPrimary = DarkBackground

val ErrorRed = Color(0xFFB00020)
val SuccessGreen = Color(0xFF34C759)

val DarkOutline = PrairieOnSurface.copy(alpha = 0.12f)
val DarkOutlineVariant = PrairieOnSurface.copy(alpha = 0.08f)
val Scrim = Color(0xFF141820)

// Focus signature — amber rim reads at 10 ft on dusk slate.
val FocusedContainer = PrairiePrimary
val FocusedContent = DarkBackground
val SelectedContainer = PrairiePrimary.copy(alpha = 0.18f)
val SubtleSurface = PrairieOnSurface.copy(alpha = 0.08f)
val ElevatedSurface = DarkSurfaceElevated.copy(alpha = 0.90f)

val HeroIndicatorInactive = PrairieOnSurface.copy(alpha = 0.32f)

val ChromeSelectedFill = PrairieOnSurface.copy(alpha = 0.14f)
val ChromeSelectedBorder = PrairieOnSurface.copy(alpha = 0.10f)

val AccentLavender = PrairiePrimary
val AccentLavenderSoft = PrairiePrimary.copy(alpha = 0.55f)
val AccentLavenderMuted = PrairiePrimary.copy(alpha = 0.22f)

val PrairieBlueGlow = PrairiePrimary.copy(alpha = 0.32f)
val PrairieBlueBorderIdle = PrairieOnSurface.copy(alpha = 0.16f)

val HeroScrimTop = Color(0x00141820)
val HeroScrimMid = DarkBackground.copy(alpha = 0.40f)
val HeroScrimLower = DarkBackground.copy(alpha = 0.75f)
val HeroScrimBottom = DarkBackground.copy(alpha = 0.95f)

val RailGradientStart = DarkBackground.copy(alpha = 0.90f)
val RailGradientEnd = Color(0x00141820)

val CardShadowColor = DarkBackground.copy(alpha = 0.55f)

val ProgressTrack = PrairieOnSurface.copy(alpha = 0.20f)
val ProgressFill = PrairiePrimary

val StageLightStrong = PrairiePrimary.copy(alpha = 0.18f)
val StageLightSoft = PrairiePrimary.copy(alpha = 0.10f)

val PrairieBlue = PrairiePrimary
val PrairieBlueLight = PrairiePrimary
val PrairieBlueDark = PrairieSecondaryText
