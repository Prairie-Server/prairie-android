package org.siloserver.silo.tv.ui.screens.player

import org.siloserver.silo.model.playback.PlayerSubtitleInfo
import kotlin.test.Test
import kotlin.test.assertEquals

class TvSubtitleChoiceLabelsTest {
    private fun row(
        index: Int = 0,
        language: String? = null,
        codec: String? = null,
        label: String? = null,
        source: String? = null,
        forced: Boolean? = null,
        url: String = "",
    ) = PlayerSubtitleInfo(
        index = index, language = language, codec = codec,
        label = label, source = source, forced = forced, url = url,
    )

    @Test
    fun sidecarFilenameLabelsCollapseToLanguageFormat() {
        // The exact shape from the report: external sidecars whose server
        // label is the release filename rendered N identical truncated rows.
        val label = subtitleChoiceLabel(
            row(
                language = "da",
                codec = "srt",
                label = "Reasonable Doubt (2014) [Bluray-1080p 8-bit x264 DTS 5.1]-PANDEMONiUM.da.srt",
                source = "external",
            ),
            position = 0,
        )
        assertEquals("Danish SRT (External)", label)
    }

    @Test
    fun embeddedCodecJunkTitleCollapses() {
        assertEquals(
            "Danish SRT",
            subtitleChoiceLabel(row(language = "da", codec = "subrip", label = "SUBRIP", source = "embedded"), 0),
        )
        assertEquals(
            "Danish PGS",
            subtitleChoiceLabel(row(language = "da", codec = "hdmv_pgs_subtitle", label = "HDMV_PGS_SUBTITLE"), 0),
        )
    }

    @Test
    fun languageRedundantTitleCollapses() {
        assertEquals(
            "English SRT",
            subtitleChoiceLabel(row(language = "en", codec = "subrip", label = "English", source = "embedded"), 0),
        )
    }

    @Test
    fun meaningfulTitleAndQualifiersRideAlong() {
        assertEquals(
            "English PGS (SDH)",
            subtitleChoiceLabel(row(language = "en", codec = "pgs", label = "SDH"), 0),
        )
        assertEquals(
            "Japanese ASS (Signs, Forced)",
            subtitleChoiceLabel(row(language = "ja", codec = "ass", label = "Signs", forced = true), 0),
        )
        assertEquals(
            "Danish SRT (Downloaded)",
            subtitleChoiceLabel(row(language = "da", codec = "srt", label = "Some Release (provider) [x264] blah blah", source = "downloaded"), 0),
        )
    }

    @Test
    fun formatFallsBackToUrlExtensionAndPositionCoversUnknowns() {
        assertEquals(
            "Danish VTT",
            subtitleChoiceLabel(row(language = "da", url = "/stream/s/subtitles/0.vtt?x=1"), 0),
        )
        assertEquals("Track 3", subtitleChoiceLabel(row(), 2))
    }
}
