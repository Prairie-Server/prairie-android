package com.continuum.app.tv.ui.screens.player

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class SubtitleTrackDisplayLabelTest {

    @Test
    fun fileLikeLabelsPreferLanguageForcedAndFormat() {
        val label = formatSubtitleTrackDisplayLabel(
            rawLabel = "Michael.2025.1080p.WEB-DL.en.forced.srt",
            language = "en",
            codecOrMime = "application/x-subrip",
            isForced = true,
            index = 0,
        )

        assertEquals("English • Forced • SRT", label)
        assertFalse(label.contains("WEB-DL"))
        assertFalse(label.contains(".srt"))
    }

    @Test
    fun downloadedProviderSurvivesWithoutTheReleaseFilename() {
        val label = formatSubtitleTrackDisplayLabel(
            rawLabel = "Dune.Part.Two.2024.1080p.BluRay.x265 (opensubtitles)",
            language = "en",
            codecOrMime = "srt",
            isForced = false,
            index = 2,
        )

        assertEquals("English • SRT • OpenSubtitles", label)
    }

    @Test
    fun aiGeneratedSubtitlesAreObviousInTrackPicker() {
        val label = formatSubtitleTrackDisplayLabel(
            rawLabel = "English → Dutch (AI) (translated)",
            language = "nl",
            codecOrMime = "srt",
            isForced = false,
            index = 1,
        )

        assertEquals("Dutch • AI Translation • SRT", label)
    }

    @Test
    fun meaningfulTrackNamesAreKeptAsDescriptions() {
        val label = formatSubtitleTrackDisplayLabel(
            rawLabel = "Director Commentary",
            language = "en",
            codecOrMime = "text/x-ssa",
            isForced = false,
            index = 1,
        )

        assertEquals("English • Director Commentary • ASS", label)
    }

    @Test
    fun redundantLanguageAndForcedNamesAreNotRepeated() {
        val label = formatSubtitleTrackDisplayLabel(
            rawLabel = "English - Forced",
            language = "en",
            codecOrMime = "srt",
            isForced = false,
            index = 0,
        )

        assertEquals("English • Forced • SRT", label)
    }

    @Test
    fun missingMetadataFallsBackToNumberedSubtitle() {
        val label = formatSubtitleTrackDisplayLabel(
            rawLabel = null,
            language = null,
            codecOrMime = null,
            isForced = false,
            index = 4,
        )

        assertEquals("Subtitle 5", label)
    }
}
