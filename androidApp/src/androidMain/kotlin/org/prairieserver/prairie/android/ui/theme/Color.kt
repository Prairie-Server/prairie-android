package org.prairieserver.prairie.android.ui.theme

import androidx.compose.ui.graphics.Color

// Plezy OLED Dark palette — mirrors iosApp/iosApp/Theme/Colors.swift exactly.
// Pure-black backgrounds, EDEDED primary text, white-at-opacity for everything else.

val PrairieBackground = Color(0xFF000000)
val PrairieSurface = Color(0xFF0A0A0A)
val PrairieSurfaceVariant = Color(0xFF0E0F12)
val PrairieSurfaceElevated = Color(0xFF15171C)
val PrairiePrimary = Color(0xFFEDEDED)
val PrairieOnSurface = Color(0xFFEDEDED)
val PrairieSecondaryText = Color(0xFFEDEDED).copy(alpha = 0.60f)
val PrairieDisabled = Color(0xFF4B5563)

val PrairieOutline = Color.White.copy(alpha = 0.12f)
val PrairieDivider = Color.White.copy(alpha = 0.12f)
val PrairieOverlay = Color.Black.copy(alpha = 0.60f)

val PrairieError = Color(0xFFB00020)
val PrairieOnError = Color(0xFFFFFFFF)
val PrairieSuccess = Color(0xFF34C759) // SwiftUI .green on dark
val PrairieWarning = Color(0xFFFFC107)

// Backwards-compatible aliases preserved so downstream code keeps compiling.
// They now resolve to the iOS Plezy values rather than the legacy cream palette.
val PrairieWhite = PrairieOnSurface
val PrairieWhiteSoft = PrairieOnSurface
val PrairieWhiteMuted = PrairieSecondaryText
val PrairieBlack = PrairieBackground

val DarkBackground = PrairieBackground
val DarkSurface = PrairieSurface
val DarkSurfaceVariant = PrairieSurfaceVariant
val DarkSurfaceHigh = PrairieSurfaceElevated

val DarkOnBackground = PrairieOnSurface
val DarkOnSurface = PrairieOnSurface
val DarkOnSurfaceVariant = PrairieSecondaryText
val DarkOnPrimary = PrairieBackground

val ErrorRed = PrairieError
val ErrorRedDark = Color(0xFF93000A)
val ErrorRedContainer = Color(0xFF8C1D18)
val OnErrorContainer = Color(0xFFFFDAD6)
val SuccessGreen = PrairieSuccess
val WarningAmber = PrairieWarning

val DarkOutline = PrairieOutline
val DarkOutlineVariant = PrairieOutline

val DarkInverseSurface = PrairieOnSurface
val DarkInverseOnSurface = PrairieBackground
val DarkInversePrimary = PrairieBackground

val Scrim = PrairieOverlay
