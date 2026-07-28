package org.siloserver.silo.common.player

import android.app.Activity
import android.os.Looper
import android.widget.FrameLayout
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.PlayerView
import org.siloserver.silo.model.settings.SubtitleAppearance
import org.siloserver.silo.model.settings.SubtitleBackgroundStylePreset
import org.siloserver.silo.model.settings.SubtitleFontSizePreset
import org.siloserver.silo.model.settings.SubtitlePositionPreset
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(UnstableApi::class)
@RunWith(RobolectricTestRunner::class)
class SubtitleManagerAppearanceTest {

    @Test
    fun defaultSubtitleStyleIsWhiteTextWithASoftShadow() {
        val style = captionStyleFor(SubtitleAppearance.DEFAULT)

        assertEquals(0xFFFFFFFF.toInt(), style.foregroundColor)
        assertEquals(0x00000000, style.backgroundColor)
        assertEquals(0x00000000, style.windowColor)
        assertEquals(CaptionStyleCompat.EDGE_TYPE_DROP_SHADOW, style.edgeType)
        assertEquals(0xFF000000.toInt(), style.edgeColor)
    }

    @Test
    fun subtitleTextFractionsMatchTheWebScale() {
        val method = SubtitleManager::class.java.getDeclaredMethod(
            "fractionalSizeFor",
            SubtitleFontSizePreset::class.java,
        )
        method.isAccessible = true

        assertEquals(20f / 720f, method.invoke(SubtitleManager(), SubtitleFontSizePreset.Small) as Float)
        assertEquals(26f / 720f, method.invoke(SubtitleManager(), SubtitleFontSizePreset.Medium) as Float)
        assertEquals(32f / 720f, method.invoke(SubtitleManager(), SubtitleFontSizePreset.Large) as Float)
        assertEquals(40f / 720f, method.invoke(SubtitleManager(), SubtitleFontSizePreset.XLarge) as Float)
        assertEquals(48f / 720f, method.invoke(SubtitleManager(), SubtitleFontSizePreset.XXLarge) as Float)
    }

    @Test
    fun bottomSubtitlesUseTheReferenceSafeMargin() {
        val method = SubtitleManager::class.java.getDeclaredMethod(
            "bottomPaddingFor",
            SubtitlePositionPreset::class.java,
        )
        method.isAccessible = true

        assertEquals(
            0.09f,
            method.invoke(
                SubtitleManager(),
                SubtitlePositionPreset.Bottom,
            ) as Float,
        )
    }

    @Test
    fun titleSafeCompensationDoesNotDoubleShiftTopSubtitles() {
        val manager = SubtitleManager().apply {
            titleSafeFraction = 0.05f
        }
        val method = SubtitleManager::class.java.getDeclaredMethod(
            "bottomPaddingFor",
            SubtitlePositionPreset::class.java,
        )
        method.isAccessible = true

        // The padding fraction is evaluated inside a surface scaled to 90% of
        // the original video height. Preserve the original physical presets:
        // f + p(1 - 2f) = base, so p = (base - f) / (1 - 2f).
        assertEquals(
            expected = (0.74f - 0.05f) / 0.90f,
            actual = method.invoke(manager, SubtitlePositionPreset.Top) as Float,
            absoluteTolerance = 0.0001f,
        )
        assertEquals(
            expected = (0.09f - 0.05f) / 0.90f,
            actual = method.invoke(manager, SubtitlePositionPreset.Bottom) as Float,
            absoluteTolerance = 0.0001f,
        )
        assertEquals(
            expected = (0.18f - 0.05f) / 0.90f,
            actual = method.invoke(manager, SubtitlePositionPreset.LowerThird) as Float,
            absoluteTolerance = 0.0001f,
        )
    }

    @Test
    fun boxBackgroundStyleAppliesConfiguredBackgroundAlpha() {
        val style = captionStyleFor(
            SubtitleAppearance.DEFAULT.copy(
                backgroundStyle = SubtitleBackgroundStylePreset.Box,
                backgroundColor = "#000000",
                backgroundOpacity = 75,
                textOutline = false,
            )
        )

        // Box paints through windowColor (Media3 pads the window block around
        // the cue); the glyph-hugging backgroundColor stays transparent.
        assertEquals(0xBF000000.toInt(), style.windowColor)
        assertEquals(0x00000000, style.backgroundColor)
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
        assertEquals(0x00000000, style.windowColor)
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
        assertEquals(0x00000000, style.windowColor)
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
    fun zoomIgnoresStaleFittedContentFrameAndUsesFullViewport() {
        val staleFit = SubtitleVideoRect(left = 0, top = 236, width = 2404, height = 1352)
        val fullViewport = SubtitleVideoRect(left = 0, top = 0, width = 2404, height = 1080)

        assertEquals(
            fullViewport,
            selectSubtitleCanvasRect(
                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM,
                contentFrameRect = staleFit,
                displayedVideoRect = fullViewport,
            ),
        )
    }

    @Test
    fun stretchIgnoresStaleFittedContentFrameAndUsesFullViewport() {
        val staleFit = SubtitleVideoRect(left = 240, top = 0, width = 1920, height = 1080)
        val fullViewport = SubtitleVideoRect(left = 0, top = 0, width = 2400, height = 1080)

        assertEquals(
            fullViewport,
            selectSubtitleCanvasRect(
                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FILL,
                contentFrameRect = staleFit,
                displayedVideoRect = fullViewport,
            ),
        )
    }

    @Test
    fun fitContinuesToUsePostLayoutContentFrame() {
        val fittedFrame = SubtitleVideoRect(left = 0, top = 0, width = 1920, height = 1080)
        val computedFallback = SubtitleVideoRect(left = 240, top = 0, width = 1920, height = 1080)

        assertEquals(
            fittedFrame,
            selectSubtitleCanvasRect(
                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT,
                contentFrameRect = fittedFrame,
                displayedVideoRect = computedFallback,
            ),
        )
    }

    @Test
    fun repeatedModeSelectionDoesNotRetainPreviousCanvas() {
        val fit = SubtitleVideoRect(left = 240, top = 0, width = 1920, height = 1080)
        val full = SubtitleVideoRect(left = 0, top = 0, width = 2400, height = 1080)

        val fill = selectSubtitleCanvasRect(
            AspectRatioFrameLayout.RESIZE_MODE_ZOOM,
            fit,
            full,
        )
        val stretch = selectSubtitleCanvasRect(
            AspectRatioFrameLayout.RESIZE_MODE_FILL,
            fit,
            full,
        )
        val restoredFit = selectSubtitleCanvasRect(
            AspectRatioFrameLayout.RESIZE_MODE_FIT,
            fit,
            fit,
        )

        assertEquals(full, fill)
        assertEquals(full, stretch)
        assertEquals(fit, restoredFit)
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

    @Test
    fun explicitSyncQueuesOnlyOnePostLayoutReconciliation() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val playerView = PlayerView(activity)
        activity.setContentView(playerView)
        playerView.layout(0, 0, 2400, 1080)
        val manager = SubtitleManager()

        manager.syncSubtitleVideoBounds(playerView)
        manager.syncSubtitleVideoBounds(playerView)

        val sync = manager.subtitleRectSyncForTest(playerView)
        assertTrue(sync.postLayoutPendingForTest())
        Shadows.shadowOf(Looper.getMainLooper()).idle()
        assertFalse(sync.postLayoutPendingForTest())
    }

    @Test
    fun detachCancelsPendingPostLayoutReconciliation() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val playerView = PlayerView(activity)
        activity.setContentView(playerView)
        playerView.layout(0, 0, 2400, 1080)
        val manager = SubtitleManager()

        manager.syncSubtitleVideoBounds(playerView)
        val sync = manager.subtitleRectSyncForTest(playerView)
        activity.setContentView(FrameLayout(activity))

        assertTrue(sync.isDisposedForTest())
        assertFalse(sync.postLayoutPendingForTest())
        Shadows.shadowOf(Looper.getMainLooper()).idle()
        assertTrue(sync.isDisposedForTest())
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

private fun SubtitleManager.subtitleRectSyncForTest(playerView: PlayerView): Any {
    val field = SubtitleManager::class.java.getDeclaredField("videoRectSyncs")
    field.isAccessible = true
    val syncs = field.get(this) as Map<*, *>
    return requireNotNull(syncs[playerView])
}

private fun Any.postLayoutPendingForTest(): Boolean {
    val field = javaClass.getDeclaredField("postLayoutPending")
    field.isAccessible = true
    return field.getBoolean(this)
}

private fun Any.isDisposedForTest(): Boolean {
    val field = javaClass.getDeclaredField("isDisposed")
    field.isAccessible = true
    return field.getBoolean(this)
}
