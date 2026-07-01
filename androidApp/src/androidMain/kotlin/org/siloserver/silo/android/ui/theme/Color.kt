package org.siloserver.silo.android.ui.theme

import androidx.compose.ui.graphics.Color

// Plezy OLED Dark palette — mirrors iosApp/iosApp/Theme/Colors.swift exactly.
// Pure-black backgrounds, EDEDED primary text, white-at-opacity for everything else.

val SiloBackground = Color(0xFF000000)
val SiloSurface = Color(0xFF0A0A0A)
val SiloSurfaceVariant = Color(0xFF0E0F12)
val SiloSurfaceElevated = Color(0xFF15171C)
val SiloPrimary = Color(0xFFEDEDED)
val SiloOnSurface = Color(0xFFEDEDED)
val SiloSecondaryText = Color(0xFFEDEDED).copy(alpha = 0.60f)
val SiloDisabled = Color(0xFF4B5563)

val SiloOutline = Color.White.copy(alpha = 0.12f)
val SiloDivider = Color.White.copy(alpha = 0.12f)
val SiloOverlay = Color.Black.copy(alpha = 0.60f)

val SiloError = Color(0xFFB00020)
val SiloOnError = Color(0xFFFFFFFF)
val SiloSuccess = Color(0xFF34C759) // SwiftUI .green on dark
val SiloWarning = Color(0xFFFFC107)

// Backwards-compatible aliases preserved so downstream code keeps compiling.
// They now resolve to the iOS Plezy values rather than the legacy cream palette.
val SiloWhite = SiloOnSurface
val SiloWhiteSoft = SiloOnSurface
val SiloWhiteMuted = SiloSecondaryText
val SiloBlack = SiloBackground

val DarkBackground = SiloBackground
val DarkSurface = SiloSurface
val DarkSurfaceVariant = SiloSurfaceVariant
val DarkSurfaceHigh = SiloSurfaceElevated

val DarkOnBackground = SiloOnSurface
val DarkOnSurface = SiloOnSurface
val DarkOnSurfaceVariant = SiloSecondaryText
val DarkOnPrimary = SiloBackground

val ErrorRed = SiloError
val ErrorRedDark = Color(0xFF93000A)
val ErrorRedContainer = Color(0xFF8C1D18)
val OnErrorContainer = Color(0xFFFFDAD6)
val SuccessGreen = SiloSuccess
val WarningAmber = SiloWarning

val DarkOutline = SiloOutline
val DarkOutlineVariant = SiloOutline

val DarkInverseSurface = SiloOnSurface
val DarkInverseOnSurface = SiloBackground
val DarkInversePrimary = SiloBackground

val Scrim = SiloOverlay
