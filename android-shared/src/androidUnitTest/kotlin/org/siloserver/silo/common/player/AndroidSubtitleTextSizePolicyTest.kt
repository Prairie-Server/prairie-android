package org.siloserver.silo.common.player

import org.siloserver.silo.model.settings.SubtitleFontSizePreset
import kotlin.test.Test
import kotlin.test.assertEquals

class AndroidSubtitleTextSizePolicyTest {
    @Test
    fun televisionUsesFixedCouchReadableSpLadder() {
        val expected = mapOf(
            SubtitleFontSizePreset.Small to 18f,
            SubtitleFontSizePreset.Medium to 22f,
            SubtitleFontSizePreset.Large to 26f,
            SubtitleFontSizePreset.XLarge to 32f,
            SubtitleFontSizePreset.XXLarge to 40f,
        )

        expected.forEach { (preset, sp) ->
            assertEquals(
                AndroidSubtitleTextSize.FixedSp(sp),
                androidSubtitleTextSize(AndroidSubtitlePresentation.Television, preset),
            )
        }
    }

    @Test
    fun phonePreservesExistingFractionalLadder() {
        val expected = mapOf(
            SubtitleFontSizePreset.Small to 22.5f / 720f,
            SubtitleFontSizePreset.Medium to 29.25f / 720f,
            SubtitleFontSizePreset.Large to 36f / 720f,
            SubtitleFontSizePreset.XLarge to 45f / 720f,
            SubtitleFontSizePreset.XXLarge to 54f / 720f,
        )

        expected.forEach { (preset, fraction) ->
            assertEquals(
                AndroidSubtitleTextSize.Fractional(fraction),
                androidSubtitleTextSize(AndroidSubtitlePresentation.Phone, preset),
            )
        }
    }
}
