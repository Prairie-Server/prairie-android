package org.siloserver.silo.model.settings

import kotlin.test.Test
import kotlin.test.assertEquals

class SubtitleAppearanceTest {

    @Test
    fun subtitleFontSizePresetsUseTheStandardScale() {
        assertEquals(36.0, SubtitleFontSizePreset.Small.pointSize)
        assertEquals(44.0, SubtitleFontSizePreset.Medium.pointSize)
        assertEquals(56.0, SubtitleFontSizePreset.Large.pointSize)
        assertEquals(68.0, SubtitleFontSizePreset.XLarge.pointSize)
        assertEquals(82.0, SubtitleFontSizePreset.XXLarge.pointSize)
    }

    @Test
    fun defaultSubtitleAppearanceKeepsTheStandardLargePreset() {
        assertEquals(SubtitleFontSizePreset.Large, SubtitleAppearance.DEFAULT.fontSize)
        assertEquals(56.0, SubtitleAppearance.DEFAULT.fontSize.pointSize)
    }

    @Test
    fun defaultSubtitleAppearanceUsesBoxBackground() {
        assertEquals(SubtitleBackgroundStylePreset.Box, SubtitleAppearance.DEFAULT.backgroundStyle)
    }

    @Test
    fun decodingMissingBackgroundStyleUsesBoxDefault() {
        val decoded = SubtitleAppearance.decode(
            """
            {
              "fontSize": "large",
              "fontFamily": "sans-serif",
              "fontColor": "#ffffff",
              "backgroundColor": "#000000",
              "backgroundOpacity": 75,
              "textOutline": false,
              "textOutlineColor": "#000000",
              "position": "bottom"
            }
            """.trimIndent(),
        )

        assertEquals(SubtitleBackgroundStylePreset.Box, decoded.backgroundStyle)
    }
}
