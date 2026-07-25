package org.prairieserver.prairie.tv.ui.theme

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

private val PrairieTvDarkColorScheme = darkColorScheme(
    primary = PrairiePrimary,
    onPrimary = DarkOnPrimary,
    primaryContainer = DarkSurfaceElevated,
    onPrimaryContainer = PrairieOnSurface,
    secondary = PrairieOnSurface,
    onSecondary = DarkOnPrimary,
    secondaryContainer = DarkSurfaceVariant,
    onSecondaryContainer = PrairieOnSurface,
    tertiary = PrairieSecondaryText,
    onTertiary = DarkOnPrimary,
    tertiaryContainer = DarkSurfaceVariant,
    onTertiaryContainer = PrairieSecondaryText,
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

/**
 * Dedicated radius for the squared control kit (pills + toggles). Mirrors the
 * Apple tvOS `PrairieTheme.smallCornerRadius` (8pt) mapped to the 960x540 DP
 * canvas (× 0.5 = 4dp). Intentionally distinct from the [Shapes.small] (12.dp)
 * token — these controls render tighter corners.
 */
val TvControlCorner = 4.dp

// tvOS corner radii: 8 / 12 / 18.
private val PrairieTvShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(18.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

private const val TvUiFontScale = 0.86f

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun PrairieTvTheme(
    content: @Composable () -> Unit,
) {
    val deviceDensity = LocalDensity.current
    // Skyline geometry tokens are already mapped from the 1920x1080 tvOS
    // canvas to Android TV's 960x540dp layout. Keep dp geometry at the device
    // density and apply the tvOS calibration only to text.
    val tvDensity = Density(
        density = deviceDensity.density,
        fontScale = deviceDensity.fontScale * TvUiFontScale,
    )

    MaterialTheme(
        colorScheme = PrairieTvDarkColorScheme,
        typography = PrairieTvTypography,
        shapes = PrairieTvShapes,
    ) {
        CompositionLocalProvider(LocalDensity provides tvDensity) {
            content()
        }
    }
}
