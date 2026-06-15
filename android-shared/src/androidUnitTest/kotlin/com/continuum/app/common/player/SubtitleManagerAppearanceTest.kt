package com.continuum.app.common.player

import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import com.continuum.app.model.settings.SubtitleFontSizePreset
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(UnstableApi::class)
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
}
