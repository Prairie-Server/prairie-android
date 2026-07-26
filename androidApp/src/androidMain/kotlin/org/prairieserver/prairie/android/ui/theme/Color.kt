package org.prairieserver.prairie.android.ui.theme

import androidx.compose.ui.graphics.Color

// Prairie Dusk palette — mirrors prairie-server Phase 3 / prairie-smarttv / iOS Theme/Colors.swift.
// Deep slate surfaces + amber wheat accent (#E0A84A).

val PrairieBackground = Color(0xFF141820)
val PrairieSurface = Color(0xFF1C222C)
val PrairieSurfaceVariant = Color(0xFF0E1116)
val PrairieSurfaceElevated = Color(0xFF222B38)
/** Brand / Material primary — amber wheat */
val PrairiePrimary = Color(0xFFE0A84A)
val PrairieOnSurface = Color(0xFFF2EEE6)
val PrairieSecondaryText = Color(0xFF9AA3B2)
val PrairieDisabled = Color(0xFF4B5563)

val PrairieOutline = PrairieOnSurface.copy(alpha = 0.12f)
val PrairieDivider = PrairieOnSurface.copy(alpha = 0.12f)
val PrairieOverlay = PrairieBackground.copy(alpha = 0.72f)

val PrairieError = Color(0xFFB00020)
val PrairieOnError = Color(0xFFFFFFFF)
val PrairieSuccess = Color(0xFF34C759)
val PrairieWarning = Color(0xFFFFC107)

// Backwards-compatible aliases.
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
