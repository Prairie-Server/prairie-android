package com.continuum.app.tv.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Shapes
import androidx.tv.material3.darkColorScheme

private val ContinuumTvDarkColorScheme = darkColorScheme(
    primary = ContinuumPrimary,
    onPrimary = DarkOnPrimary,
    primaryContainer = DarkSurfaceElevated,
    onPrimaryContainer = ContinuumOnSurface,
    secondary = ContinuumOnSurface,
    onSecondary = DarkOnPrimary,
    secondaryContainer = DarkSurfaceVariant,
    onSecondaryContainer = ContinuumOnSurface,
    tertiary = ContinuumSecondaryText,
    onTertiary = DarkOnPrimary,
    tertiaryContainer = DarkSurfaceVariant,
    onTertiaryContainer = ContinuumSecondaryText,
    error = ErrorRed,
    onError = DarkOnSurface,
    errorContainer = ErrorRed,
    onErrorContainer = DarkOnSurface,
    background = DarkBackground,
    onBackground = DarkOnBackground,
    surface = DarkSurface,
    onSurface = DarkOnSurface,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkOnSurfaceVariant,
    border = DarkOutline,
    borderVariant = DarkOutlineVariant,
    scrim = Scrim,
)

// tvOS corner radii: 8 / 12 / 18.
private val ContinuumTvShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(18.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

private const val TvUiDensityScale = 0.86f

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun ContinuumTvTheme(
    content: @Composable () -> Unit,
) {
    val deviceDensity = LocalDensity.current
    val tvDensity = Density(
        density = deviceDensity.density * TvUiDensityScale,
        fontScale = deviceDensity.fontScale,
    )

    MaterialTheme(
        colorScheme = ContinuumTvDarkColorScheme,
        typography = ContinuumTvTypography,
        shapes = ContinuumTvShapes,
    ) {
        CompositionLocalProvider(LocalDensity provides tvDensity) {
            content()
        }
    }
}
