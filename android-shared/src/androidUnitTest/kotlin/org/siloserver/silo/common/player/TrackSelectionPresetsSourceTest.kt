package org.siloserver.silo.common.player

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TrackSelectionPresetsSourceTest {
    private val source = File(
        "src/androidMain/kotlin/org/siloserver/silo/common/player/TrackSelectionPresets.kt",
    ).readText()

    @Test
    fun presetsDoNotForceDisableSubtitles() {
        assertFalse(
            source.contains("setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)"),
            "presets must not force-disable subtitles — doing so wipes the user's active subtitle override on every caps change",
        )
    }

    @Test
    fun presetsApplyPreferredTextLanguage() {
        val occurrences = source.split("setPreferredTextLanguage(").size - 1
        assertEquals(
            2,
            occurrences,
            "setPreferredTextLanguage( must appear exactly twice — once per builder (TV and Phone)",
        )
    }

    @Test
    fun tvPresetUsesDeviceSpecificMedia3TunnelingPolicy() {
        assertFalse(
            source.contains("setTunnelingEnabled(true)"),
            "TV presets must evaluate the device policy instead of forcing tunneling on.",
        )
        assertTrue(
            source.contains("setTunnelingEnabled(tunnelingEnabled)"),
            "TV presets should use the device-specific tunneling policy.",
        )
    }

    @Test
    fun tvPresetEnablesAudioOffloadWhenSupported() {
        assertTrue(
            source.substringBefore("fun buildPhoneParameters").contains("AUDIO_OFFLOAD_MODE_ENABLED"),
            "TV presets should let Media3 use platform audio offload when the selected format supports it.",
        )
    }
}
