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
        isDefault: Boolean? = null,
        url: String = "",
    ) = PlayerSubtitleInfo(
        index = index, language = language, codec = codec,
        label = label, source = source, forced = forced, url = url,
        isDefault = isDefault,
    )

    @Test
    fun sidecarFilenameLabelsNeverExposeTheShowOrReleaseName() {
        val label = subtitleChoiceLabel(
            row(
                language = "da",
                codec = "srt",
                label = "Reasonable Doubt (2014) [Bluray-1080p 8-bit x264 DTS 5.1]-PANDEMONiUM.da.srt",
                source = "external",
            ),
            position = 0,
        )
        assertEquals("Danish — SRT · External", label)
    }

    @Test
    fun embeddedCodecJunkTitleCollapses() {
        assertEquals(
            "Danish — SRT · Default",
            subtitleChoiceLabel(
                row(
                    language = "da",
                    codec = "subrip",
                    label = "SUBRIP",
                    source = "embedded",
                    isDefault = true,
                ),
                0,
            ),
        )
        assertEquals(
            "Danish — PGS",
            subtitleChoiceLabel(row(language = "da", codec = "hdmv_pgs_subtitle", label = "HDMV_PGS_SUBTITLE"), 0),
        )
    }

    @Test
    fun languageRedundantTitleCollapses() {
        assertEquals(
            "English — SRT",
            subtitleChoiceLabel(row(language = "en", codec = "subrip", label = "English", source = "embedded"), 0),
        )
    }

    @Test
    fun serverArtifactPlaceholderDoesNotReplaceCatalogName() {
        assertEquals(
            "English — SRT",
            subtitleChoiceLabel(
                row(language = "en", codec = "srt", label = "Server subtitle", source = "server_artifact"),
                0,
            ),
        )
    }

    @Test
    fun meaningfulTitleAndQualifiersRideAlong() {
        assertEquals(
            "English — SDH · PGS",
            subtitleChoiceLabel(row(language = "en", codec = "pgs", label = "SDH"), 0),
        )
        assertEquals(
            "Japanese — Signs · ASS · Forced",
            subtitleChoiceLabel(row(language = "ja", codec = "ass", label = "Signs", forced = true), 0),
        )
        assertEquals(
            "Danish — SRT · Downloaded",
            subtitleChoiceLabel(row(language = "da", codec = "srt", label = "Some Release (provider) [x264] blah blah", source = "downloaded"), 0),
        )
    }

    @Test
    fun formatFallsBackToUrlExtensionAndPositionCoversUnknowns() {
        assertEquals(
            "Danish — VTT",
            subtitleChoiceLabel(row(language = "da", url = "/stream/s/subtitles/0.vtt?x=1"), 0),
        )
        assertEquals("Track 3", subtitleChoiceLabel(row(), 2))
    }
}

class TvAudioChoiceLabelsTest {
    private fun audio(
        language: String? = null,
        codecOrMime: String? = null,
        channels: Int = 0,
        label: String = "",
    ) = PlayerTrackEntry(
        index = 0, label = label, language = language, isSelected = false,
        displayLabel = label, codecOrMime = codecOrMime, channelCount = channels,
    )

    @Test
    fun buildsLanguageCodecLayout() {
        assertEquals(
            "English DTS 5.1",
            audioChoiceLabel(audio(language = "en", codecOrMime = "audio/vnd.dts", channels = 6), 0),
        )
        assertEquals(
            "Japanese AAC Stereo",
            audioChoiceLabel(audio(language = "ja", codecOrMime = "audio/mp4a-latm", channels = 2), 0),
        )
        assertEquals(
            "English Atmos 7.1",
            audioChoiceLabel(audio(language = "en", codecOrMime = "audio/eac3-joc", channels = 8), 0),
        )
    }

    @Test
    fun iso639TwoBibliographicCodesResolve() {
        assertEquals(
            "German E-AC3 5.1",
            audioChoiceLabel(audio(language = "ger", codecOrMime = "audio/eac3", channels = 6), 0),
        )
        // Subtitle side of the same fix.
        assertEquals(
            "German — SRT · External",
            subtitleChoiceLabel(
                PlayerSubtitleInfo(index = 0, language = "ger", codec = "srt", source = "external", url = ""),
                0,
            ),
        )
    }

    @Test
    fun fallsBackToDisplayLabelThenPosition() {
        assertEquals("Commentary", audioChoiceLabel(audio(label = "Commentary"), 2))
        assertEquals("Track 3", audioChoiceLabel(audio(), 2))
    }

    @Test
    fun meaningfulTitleDistinguishesStructuredTracks() {
        assertEquals(
            "English AC3 5.1 (Commentary)",
            audioChoiceLabel(
                audio(language = "en", codecOrMime = "audio/ac3", channels = 6, label = "Commentary"),
                0,
            ),
        )
        assertEquals(
            "English AC3 5.1 (Descriptive Audio)",
            audioChoiceLabel(
                audio(language = "en", codecOrMime = "audio/ac3", channels = 6, label = "Descriptive Audio"),
                1,
            ),
        )
    }

    @Test
    fun redundantStructuredTitleIsNotRepeated() {
        assertEquals(
            "English AC3 5.1",
            audioChoiceLabel(
                audio(language = "en", codecOrMime = "audio/ac3", channels = 6, label = "English AC3 5.1"),
                0,
            ),
        )
    }

    @Test
    fun bareLanguageAliasesAndServerIdentifiersAreNotQualifiers() {
        assertEquals(
            "English AC3 5.1",
            audioChoiceLabel(
                audio(language = "en", codecOrMime = "audio/ac3", channels = 6, label = "eng"),
                0,
            ),
        )
        assertEquals(
            "English AC3 5.1",
            audioChoiceLabel(
                audio(language = "en", codecOrMime = "audio/ac3", channels = 6, label = "9073"),
                0,
            ),
        )
        assertEquals(
            "English AC3 5.1",
            audioChoiceLabel(
                audio(language = "en", codecOrMime = "audio/ac3", channels = 6, label = "release.eng.ac3"),
                0,
            ),
        )
    }

    @Test
    fun codecDeduplicationUsesTokenBoundaries() {
        assertEquals(
            "English AAC Stereo (Isaac Commentary)",
            audioChoiceLabel(
                audio(
                    language = "en",
                    codecOrMime = "audio/aac",
                    channels = 2,
                    label = "Isaac Commentary",
                ),
                0,
            ),
        )
    }
}
