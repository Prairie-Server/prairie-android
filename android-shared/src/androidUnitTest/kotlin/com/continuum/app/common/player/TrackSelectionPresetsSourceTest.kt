package com.continuum.app.common.player

import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TrackSelectionPresetsSourceTest {
    private val source = File(
        "src/androidMain/kotlin/com/continuum/app/common/player/TrackSelectionPresets.kt",
    ).readText()

    @Test
    fun presetsDoNotAutoSelectTextTracksFromProfileLanguage() {
        assertTrue(
            source.contains("setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)"),
            "startup presets must leave text disabled until the user picks a subtitle",
        )
        assertFalse(
            source.contains("setPreferredTextLanguage("),
            "profile subtitle language must not auto-select embedded image subtitles during startup",
        )
    }
}
