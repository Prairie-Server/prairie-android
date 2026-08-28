package org.prairieserver.prairie.android.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// iOS pins .preferredColorScheme(.dark) in ContentView.swift, so Android matches by
// always emitting the Plezy OLED scheme regardless of system or preference toggles.
private val PrairieDarkColorScheme = darkColorScheme(
    primary = PrairiePrimary,
    onPrimary = PrairieBackground,
    primaryContainer = PrairieSurfaceElevated,
    onPrimaryContainer = PrairieOnSurface,
    secondary = PrairieOnSurface,
    onSecondary = PrairieBackground,
    secondaryContainer = PrairieSurfaceVariant,
    onSecondaryContainer = PrairieOnSurface,
    tertiary = PrairieSecondaryText,
    onTertiary = PrairieBackground,
    tertiaryContainer = PrairieSurfaceVariant,
    onTertiaryContainer = PrairieSecondaryText,
    error = PrairieError,
    onError = PrairieOnError,
    errorContainer = ErrorRedContainer,
    onErrorContainer = OnErrorContainer,
    background = PrairieBackground,
    onBackground = PrairieOnSurface,
    surface = PrairieSurface,
    onSurface = PrairieOnSurface,
    surfaceVariant = PrairieSurfaceVariant,
    onSurfaceVariant = PrairieSecondaryText,
    // `darkColorScheme` leaves the container ladder on M3's purple-tinted
    // baseline, so anything reaching for `surfaceContainer*` used to land off
    // the Prairie palette entirely (the cast bars did, and the settings cards
    // avoided the roles by borrowing `primaryContainer`). Populated so the
    // roles mean what they say.
    surfaceContainerLowest = PrairieSurfaceContainerLowest,
    surfaceContainerLow = PrairieSurfaceContainerLow,
    surfaceContainer = PrairieSurfaceContainer,
    surfaceContainerHigh = PrairieSurfaceContainerHigh,
    surfaceContainerHighest = PrairieSurfaceContainerHighest,
    surfaceDim = PrairieSurfaceDim,
    surfaceBright = PrairieSurfaceBright,
    outline = PrairieOutline,
    outlineVariant = PrairieOutline,
    inverseSurface = PrairieOnSurface,
    inverseOnSurface = PrairieBackground,
    inversePrimary = PrairieBackground,
    scrim = PrairieOverlay,
    surfaceTint = Color.Transparent,
)

@Composable
fun PrairieTheme(
    content: @Composable () -> Unit,
) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? android.app.Activity)?.window ?: return@SideEffect
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
        }
    }

    MaterialTheme(
        colorScheme = PrairieDarkColorScheme,
        typography = PrairieTypography,
        shapes = PrairieShapes,
        content = content,
    )
}
