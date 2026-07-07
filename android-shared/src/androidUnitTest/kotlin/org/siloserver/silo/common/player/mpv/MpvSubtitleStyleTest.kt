package org.siloserver.silo.common.player.mpv

import kotlin.test.Test
import kotlin.test.assertEquals
import org.siloserver.silo.model.settings.SubtitleAppearance
import org.siloserver.silo.model.settings.SubtitleBackgroundStylePreset
import org.siloserver.silo.model.settings.SubtitleFontSizePreset
import org.siloserver.silo.model.settings.SubtitlePositionPreset

class MpvSubtitleStyleTest {

    @Test
    fun defaultAppearanceMapsToBoxStyle() {
        val props = subtitleAppearanceToMpvProperties(SubtitleAppearance.DEFAULT, nativeAssTrack = false)

        assertEquals("#ffffff", props["sub-color"])
        // Box style: background color carries the 75% opacity alpha (0xBF).
        assertEquals("#BF000000", props["sub-back-color"])
        assertEquals("4", props["sub-border-style"]) // ASS BorderStyle 4 = per-event box
        // Outline off → no border ring.
        assertEquals("0", props["sub-border-size"])
        // Bottom position → legacy sub-pos 100.
        assertEquals("100", props["sub-pos"])
        // User styling must override authored styles on non-ASS tracks.
        assertEquals("force", props["sub-ass-override"])
    }

    @Test
    fun nativeAssTrackDisablesOverrides() {
        val props = subtitleAppearanceToMpvProperties(SubtitleAppearance.DEFAULT, nativeAssTrack = true)
        // Authored ASS renders as-authored — only the override switch is set.
        assertEquals("no", props["sub-ass-override"])
        assertEquals(setOf("sub-ass-override"), props.keys)
    }

    @Test
    fun outlineAndShadowAndPositionMap() {
        val appearance = SubtitleAppearance(
            fontSize = SubtitleFontSizePreset.XXLarge,
            fontColor = "#ffff00",
            backgroundStyle = SubtitleBackgroundStylePreset.Shadow,
            backgroundColor = "#1e3a5f",
            textOutline = true,
            textOutlineColor = "#14532d",
            position = SubtitlePositionPreset.Top,
        )
        val props = subtitleAppearanceToMpvProperties(appearance, nativeAssTrack = false)

        assertEquals("#ffff00", props["sub-color"])
        // Shadow: half-alpha back color + a positive shadow offset (Apple 0x80).
        assertEquals("#801E3A5F", props["sub-back-color"])
        assertEquals("2", props["sub-shadow-offset"])
        assertEquals("1", props["sub-border-style"]) // outline+shadow, no box
        // Outline: clamp(pointSize * 0.08, 2, 4) — xxlarge is >= 50pt → 4.
        assertEquals("4", props["sub-border-size"])
        assertEquals("#14532d", props["sub-border-color"])
        assertEquals("0", props["sub-pos"]) // top
    }

    @Test
    fun backgroundNoneClearsBox() {
        val appearance = SubtitleAppearance(backgroundStyle = SubtitleBackgroundStylePreset.None)
        val props = subtitleAppearanceToMpvProperties(appearance, nativeAssTrack = false)
        assertEquals("#00000000", props["sub-back-color"])
        assertEquals("1", props["sub-border-style"])
        assertEquals("0", props["sub-shadow-offset"])
    }

    @Test
    fun fontFamilyAndSizeMap() {
        val appearance = SubtitleAppearance(
            fontFamily = SubtitleAppearance.MONOSPACE,
            fontSize = SubtitleFontSizePreset.Small,
        )
        val props = subtitleAppearanceToMpvProperties(appearance, nativeAssTrack = false)
        assertEquals("monospace", props["sub-font"])
        assertEquals("36", props["sub-font-size"])
    }
}
