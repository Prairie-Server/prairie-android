package org.prairieserver.prairie.tv.ui.screens.player

import androidx.compose.ui.Alignment
import androidx.compose.ui.text.font.FontFamily
import org.prairieserver.prairie.model.settings.SubtitleAppearance
import org.prairieserver.prairie.model.settings.SubtitleBackgroundStylePreset
import org.prairieserver.prairie.model.settings.SubtitleFontSizePreset
import org.prairieserver.prairie.model.settings.SubtitlePositionPreset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TvSubtitleAppearanceOptionsTest {
    @Test
    fun backgroundStylePickerLeadsWithNoBackground() {
        assertEquals(
            SubtitleBackgroundStylePreset.None to "No background",
            TvSubtitleAppearanceOptions.BACKGROUND_STYLES.first(),
        )
        assertEquals(
            "No background",
            TvSubtitleAppearanceOptions.backgroundStyleLabel(SubtitleBackgroundStylePreset.None),
        )
    }

    @Test
    fun previewSizesAreDistinctAndOrdered() {
        val sizes = SubtitleFontSizePreset.entries.map(TvSubtitleAppearanceOptions::previewFontSizeSp)
        assertEquals(sizes.sorted(), sizes)
        assertEquals(sizes.size, sizes.distinct().size)
    }

    @Test
    fun previewFontsMapToComposeFamilies() {
        assertEquals(FontFamily.SansSerif, TvSubtitleAppearanceOptions.previewFontFamily(SubtitleAppearance.SANS_SERIF))
        assertEquals(FontFamily.Serif, TvSubtitleAppearanceOptions.previewFontFamily(SubtitleAppearance.SERIF))
        assertEquals(FontFamily.Monospace, TvSubtitleAppearanceOptions.previewFontFamily(SubtitleAppearance.MONOSPACE))
    }

    @Test
    fun previewPositionsUseDistinctStageAlignments() {
        assertEquals(Alignment.TopCenter, TvSubtitleAppearanceOptions.previewAlignment(SubtitlePositionPreset.Top))
        assertEquals(Alignment.Center, TvSubtitleAppearanceOptions.previewAlignment(SubtitlePositionPreset.LowerThird))
        assertEquals(Alignment.BottomCenter, TvSubtitleAppearanceOptions.previewAlignment(SubtitlePositionPreset.Bottom))
    }

    @Test
    fun previewDecorationDistinguishesOutlineAndShadow() {
        val outline = TvSubtitleAppearanceOptions.previewDecoration(
            SubtitleAppearance(backgroundStyle = SubtitleBackgroundStylePreset.Outline),
        )
        assertTrue(outline.outline)
        assertFalse(outline.shadow)

        val shadow = TvSubtitleAppearanceOptions.previewDecoration(
            SubtitleAppearance(backgroundStyle = SubtitleBackgroundStylePreset.Shadow),
        )
        assertFalse(shadow.outline)
        assertTrue(shadow.shadow)
    }
}
