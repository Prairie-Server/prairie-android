package org.prairieserver.prairie.android.ui.theme

import androidx.compose.ui.graphics.Color

// Prairie Dusk palette — mirrors prairie-server / prairie-smarttv / the TV
// app's Color.kt. Deep slate surfaces + amber wheat accent (#E0A84A).
// Prairie-only (99f1014c); an upstream sync reverted these tokens to the
// Silo OLED values once. Anchored in scripts/prairie-invariants.txt.

val PrairieBackground = Color(0xFF141820)

/**
 * The signed-in page canvas. iOS paints `ContinuumPageBackdrop` (#111111) under
 * every signed-in phone surface instead of pure black (silo-apple PR #222);
 * `PrairieBackground` stays black for the ink and player roles that mirror
 * `continuumBackground`. Settings keeps `PrairieSettingsBackground`.
 */
val PrairiePageBackground = Color(0xFF111111)
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

// Restrained Android substitute for iOS Liquid Glass. These shared washes keep
// every migrated circle/capsule in one light material family without a live
// blur. They remain inexpensive on
// Android (no live backdrop blur), while allowing the title-derived surface
// underneath to tint them instead of reading as flat white plastic.
val PrairieOpaqueControl = Color.White.copy(alpha = 0.58f)
val PrairieOpaqueControlSelected = Color.White.copy(alpha = 0.70f)
val PrairieOpaqueControlSelectionOverlay = Color.White.copy(alpha = 0.18f)
// PR #212 PhoneLabeledAction values. These read as the same adaptive material
// family as the Home tab bar over a dark detail surface without becoming the
// pale, nearly solid circles shown in the Android comparison screenshot.
val PrairieDetailActionControl = Color.White.copy(alpha = 0.10f)
val PrairieDetailActionControlActive = Color.White.copy(alpha = 0.30f)
val PrairieOnOpaqueControl = Color(0xFF17171A)
val PrairieOnOpaqueControlMuted = Color(0xFF4F4F55)
val PrairieOpaqueControlBorder = Color.White.copy(alpha = 0.46f)

// --- Grouped-surface palette (Silo web client parity) ---
//
// The OLED values above are the app's chrome: pure black grounds with
// white-at-opacity on top. That reads well over artwork and badly over a long
// form, where a card has to separate from its page without a border and a
// hairline has to be visible without glowing. These are the web client's
// settings values, and they fill M3's `surfaceContainer*` ladder — which
// `darkColorScheme` otherwise leaves on its purple-tinted baseline.

/** Lifted page ground for form-shaped screens. Web `--background`. */
val PrairieSettingsBackground = Color(0xFF141417)

/** Grouped card surface. Web `--card`. */
val PrairieSurfaceContainer = Color(0xFF1C1C20)

val PrairieSurfaceContainerLowest = Color(0xFF060608)
val PrairieSurfaceContainerLow = Color(0xFF141417)
val PrairieSurfaceContainerHigh = Color(0xFF24242A)
val PrairieSurfaceContainerHighest = Color(0xFF2C2C33)
val PrairieSurfaceDim = Color(0xFF000000)
val PrairieSurfaceBright = Color(0xFF2C2C33)

/** Hairline between rows and around inset controls. Web `--border`. */
val PrairieBorder = Color(0xFF28282E)

/** Secondary copy on a grouped surface. Web `--muted-foreground`. */
val PrairieMutedText = Color(0xFF9696A0)

/** Primary copy on a grouped surface. Web `--foreground`. */
val PrairieForeground = Color(0xFFE8E8EC)

/**
 * The single destructive tint.
 *
 * Settings previously carried two: `colorScheme.error` (0xFFB00020, an M3
 * *light*-theme red that fails contrast on a dark card) for Sign Out and Reset
 * Playback Overrides, and an iOS system red (0xFFFF453A) for Remove All
 * Downloads. Web `--destructive`.
 */
val PrairieDestructive = Color(0xFFEF4444)

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

val DarkBackground = PrairiePageBackground
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

// Bottom navigation capsule. Deliberately opaque rather than a translucent
// wash: Compose has no live backdrop blur, so iOS's glass tab bar cannot be
// reproduced by tinting alone. The previous 30%-white fill just lightened
// whatever poster art scrolled underneath and swallowed the labels over bright
// covers. The selected tab inverts to a light chip instead of a brighter wash.
val PrairieNavPillSurface = Color(0xFF15191F)
val PrairieNavPillBorder = Color.White.copy(alpha = 0.10f)
val PrairieNavPillSelected = Color(0xFFEDEDED)
val PrairieNavPillSelectedContent = Color(0xFF0B0B0C)
val PrairieNavPillContent = Color.White.copy(alpha = 0.58f)

/**
 * Detail-page overlay buttons (close, remote). The bottom-nav pill made
 * translucent: hero artwork still reads through, but a dark disc holds a white
 * glyph over a pale poster, which the old white-on-white wash did not.
 */
val PrairieOverlayPillSurface = PrairieNavPillSurface.copy(alpha = 0.62f)
