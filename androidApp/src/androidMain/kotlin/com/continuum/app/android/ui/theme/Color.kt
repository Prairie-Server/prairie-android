package com.continuum.app.android.ui.theme

import androidx.compose.ui.graphics.Color

// Plezy OLED Dark palette — mirrors iosApp/iosApp/Theme/Colors.swift exactly.
// Pure-black backgrounds, EDEDED primary text, white-at-opacity for everything else.

val ContinuumBackground = Color(0xFF000000)
val ContinuumSurface = Color(0xFF0A0A0A)
val ContinuumSurfaceVariant = Color(0xFF0E0F12)
val ContinuumSurfaceElevated = Color(0xFF15171C)
val ContinuumPrimary = Color(0xFFEDEDED)
val ContinuumOnSurface = Color(0xFFEDEDED)
val ContinuumSecondaryText = Color(0xFFEDEDED).copy(alpha = 0.60f)
val ContinuumDisabled = Color(0xFF4B5563)

val ContinuumOutline = Color.White.copy(alpha = 0.12f)
val ContinuumDivider = Color.White.copy(alpha = 0.12f)
val ContinuumOverlay = Color.Black.copy(alpha = 0.60f)

val ContinuumError = Color(0xFFB00020)
val ContinuumOnError = Color(0xFFFFFFFF)
val ContinuumSuccess = Color(0xFF34C759) // SwiftUI .green on dark
val ContinuumWarning = Color(0xFFFFC107)

// Backwards-compatible aliases preserved so downstream code keeps compiling.
// They now resolve to the iOS Plezy values rather than the legacy cream palette.
val ContinuumWhite = ContinuumOnSurface
val ContinuumWhiteSoft = ContinuumOnSurface
val ContinuumWhiteMuted = ContinuumSecondaryText
val ContinuumBlack = ContinuumBackground

val DarkBackground = ContinuumBackground
val DarkSurface = ContinuumSurface
val DarkSurfaceVariant = ContinuumSurfaceVariant
val DarkSurfaceHigh = ContinuumSurfaceElevated

val DarkOnBackground = ContinuumOnSurface
val DarkOnSurface = ContinuumOnSurface
val DarkOnSurfaceVariant = ContinuumSecondaryText
val DarkOnPrimary = ContinuumBackground

val ErrorRed = ContinuumError
val ErrorRedDark = Color(0xFF93000A)
val ErrorRedContainer = Color(0xFF8C1D18)
val OnErrorContainer = Color(0xFFFFDAD6)
val SuccessGreen = ContinuumSuccess
val WarningAmber = ContinuumWarning

val DarkOutline = ContinuumOutline
val DarkOutlineVariant = ContinuumOutline

val DarkInverseSurface = ContinuumOnSurface
val DarkInverseOnSurface = ContinuumBackground
val DarkInversePrimary = ContinuumBackground

val Scrim = ContinuumOverlay
