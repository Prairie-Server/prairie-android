package org.siloserver.silo.common.player

import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.CaptionStyleCompat
import org.siloserver.silo.model.settings.SubtitleAppearance
import org.siloserver.silo.model.settings.SubtitleBackgroundStylePreset
import org.siloserver.silo.model.settings.SubtitleFontSizePreset
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(UnstableApi::class)
@RunWith(RobolectricTestRunner::class)
class SubtitleManagerAppearanceTest {

    @Test
    fun subtitleTextFractionsAreCalibratedOneStepSmaller() {
        val method = SubtitleManager::class.java.getDeclaredMethod(
            "fractionalSizeFor",
            SubtitleFontSizePreset::class.java,
        )
        method.isAccessible = true

        assertEquals(0.032f, method.invoke(SubtitleManager(), SubtitleFontSizePreset.Small) as Float)
        assertEquals(0.040f, method.invoke(SubtitleManager(), SubtitleFontSizePreset.Medium) as Float)
        assertEquals(0.050f, method.invoke(SubtitleManager(), SubtitleFontSizePreset.Large) as Float)
        assertEquals(0.060f, method.invoke(SubtitleManager(), SubtitleFontSizePreset.XLarge) as Float)
        assertEquals(0.072f, method.invoke(SubtitleManager(), SubtitleFontSizePreset.XXLarge) as Float)
    }

    @Test
    fun boxBackgroundStyleAppliesConfiguredBackgroundAlpha() {
        val style = captionStyleFor(
            SubtitleAppearance.DEFAULT.copy(
                backgroundStyle = SubtitleBackgroundStylePreset.Box,
                backgroundColor = "#000000",
                backgroundOpacity = 75,
            )
        )

        assertEquals(0xBF000000.toInt(), style.backgroundColor)
        assertEquals(CaptionStyleCompat.EDGE_TYPE_NONE, style.edgeType)
    }

    @Test
    fun shadowBackgroundStyleKeepsBackgroundTransparent() {
        val style = captionStyleFor(
            SubtitleAppearance.DEFAULT.copy(
                backgroundStyle = SubtitleBackgroundStylePreset.Shadow,
                backgroundColor = "#000000",
                backgroundOpacity = 75,
            )
        )

        assertEquals(0x00000000, style.backgroundColor)
        assertEquals(CaptionStyleCompat.EDGE_TYPE_DROP_SHADOW, style.edgeType)
    }

    @Test
    fun outlineBackgroundStyleKeepsBackgroundTransparent() {
        val style = captionStyleFor(
            SubtitleAppearance.DEFAULT.copy(
                backgroundStyle = SubtitleBackgroundStylePreset.Outline,
                backgroundColor = "#000000",
                backgroundOpacity = 75,
            )
        )

        assertEquals(0x00000000, style.backgroundColor)
        assertEquals(CaptionStyleCompat.EDGE_TYPE_OUTLINE, style.edgeType)
    }

    @Test
    fun fitModeComputesPortraitVideoRectInsideLetterbox() {
        val rect = displayedSubtitleVideoRect(
            viewWidth = 1080,
            viewHeight = 2400,
            videoWidth = 1920,
            videoHeight = 1080,
            resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT,
        )

        assertEquals(SubtitleVideoRect(left = 0, top = 896, width = 1080, height = 608), rect)
    }

    @Test
    fun fitModeComputesLandscapeVideoRectInsidePillarbox() {
        val rect = displayedSubtitleVideoRect(
            viewWidth = 2400,
            viewHeight = 1080,
            videoWidth = 1920,
            videoHeight = 1080,
            resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT,
        )

        assertEquals(SubtitleVideoRect(left = 240, top = 0, width = 1920, height = 1080), rect)
    }

    @Test
    fun fitModeUsesVideoPixelAspectRatioForAnamorphicContent() {
        val rect = displayedSubtitleVideoRect(
            viewWidth = 1920,
            viewHeight = 1080,
            videoWidth = 720,
            videoHeight = 576,
            videoPixelWidthHeightRatio = 16f / 15f,
            resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT,
        )

        assertEquals(SubtitleVideoRect(left = 240, top = 0, width = 1440, height = 1080), rect)
    }

    @Test
    fun zoomAndFillModesUseFullViewRect() {
        val zoom = displayedSubtitleVideoRect(
            viewWidth = 1080,
            viewHeight = 2400,
            videoWidth = 1920,
            videoHeight = 1080,
            resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM,
        )
        val fill = displayedSubtitleVideoRect(
            viewWidth = 1080,
            viewHeight = 2400,
            videoWidth = 1920,
            videoHeight = 1080,
            resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FILL,
        )

        assertEquals(SubtitleVideoRect(left = 0, top = 0, width = 1080, height = 2400), zoom)
        assertEquals(SubtitleVideoRect(left = 0, top = 0, width = 1080, height = 2400), fill)
    }

    @Test
    fun invalidVideoSizeUsesFullViewRect() {
        val rect = displayedSubtitleVideoRect(
            viewWidth = 1080,
            viewHeight = 2400,
            videoWidth = 0,
            videoHeight = 0,
            resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT,
        )

        assertEquals(SubtitleVideoRect(left = 0, top = 0, width = 1080, height = 2400), rect)
    }

    @Test
    fun contentFrameRectUsesSubtitleParentLocalBounds() {
        val rect = displayedSubtitleContentFrameRect(
            viewWidth = 1920,
            viewHeight = 1080,
            frameLeft = 0,
            frameTop = 140,
            frameWidth = 1920,
            frameHeight = 800,
        )

        assertEquals(SubtitleVideoRect(left = 0, top = 0, width = 1920, height = 800), rect)
    }

    @Test
    fun clippedContentFrameRectUsesSubtitleParentLocalIntersection() {
        val rect = displayedSubtitleContentFrameRect(
            viewWidth = 1920,
            viewHeight = 1080,
            frameLeft = -100,
            frameTop = -50,
            frameWidth = 2120,
            frameHeight = 1180,
        )

        assertEquals(SubtitleVideoRect(left = 100, top = 50, width = 1920, height = 1080), rect)
    }

    @Test
    fun invalidContentFrameRectFallsBackToComputedBounds() {
        assertEquals(
            null,
            displayedSubtitleContentFrameRect(
                viewWidth = 1920,
                viewHeight = 1080,
                frameLeft = 0,
                frameTop = 0,
                frameWidth = 0,
                frameHeight = 0,
            ),
        )
    }

    private fun captionStyleFor(appearance: SubtitleAppearance): CaptionStyleCompat {
        val method = SubtitleManager::class.java.getDeclaredMethod(
            "buildCaptionStyle",
            SubtitleAppearance::class.java,
        )
        method.isAccessible = true
        return method.invoke(SubtitleManager(), appearance) as CaptionStyleCompat
    }
}
