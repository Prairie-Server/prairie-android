package com.continuum.app.tv.ui.theme

import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TvThemeScalingTest {
    private val theme = File(
        "src/androidMain/kotlin/com/continuum/app/tv/ui/theme/Theme.kt",
    ).readText()

    private val spacing = File(
        "src/androidMain/kotlin/com/continuum/app/tv/ui/theme/Spacing.kt",
    ).readText()

    private val detailHero = File(
        "src/androidMain/kotlin/com/continuum/app/tv/ui/screens/detail/TvDetailHero.kt",
    ).readText()

    @Test
    fun tvThemeScalesTextWithoutShrinkingMappedDpGeometry() {
        assertTrue(theme.contains("private const val TvUiFontScale = 0.86f"))
        assertTrue(theme.contains("density = deviceDensity.density,"))
        assertTrue(theme.contains("fontScale = deviceDensity.fontScale * TvUiFontScale"))

        assertFalse(theme.contains("TvUiDensityScale"))
        assertFalse(theme.contains("density = deviceDensity.density *"))
        assertFalse(spacing.contains("under the theme's\n * 0.86 density scale"))
    }

    @Test
    fun detailHeroDoesNotCompensateForOldDensityShrink() {
        assertTrue(detailHero.contains("LocalConfiguration.current.screenHeightDp.dp"))

        assertFalse(detailHero.contains("resources.displayMetrics.heightPixels"))
        assertFalse(detailHero.contains("LocalDensity.current"))
        assertFalse(detailHero.contains("density\n    // override"))
    }
}
