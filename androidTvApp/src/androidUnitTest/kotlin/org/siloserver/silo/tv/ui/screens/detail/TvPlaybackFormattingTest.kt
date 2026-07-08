package org.siloserver.silo.tv.ui.screens.detail

import org.siloserver.silo.model.catalog.AudioTrack
import org.siloserver.silo.model.catalog.FileVersion
import org.siloserver.silo.model.catalog.SubtitleTrack
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TvPlaybackFormattingTest {

    // --- versionShortLabel ---

    @Test fun versionShortLabel_4kHdr() {
        val v = fileVersion(resolution = "2160p", hdr = true)
        assertEquals("4K · HDR", TvPlaybackFormatting.versionShortLabel(v))
    }

    @Test fun versionShortLabel_1080() {
        assertEquals(
            "1080P",
            TvPlaybackFormatting.versionShortLabel(fileVersion(resolution = "1080p", hdr = false)),
        )
    }

    @Test fun versionShortLabel_nullIsAuto() {
        assertEquals("Auto", TvPlaybackFormatting.versionShortLabel(null))
    }

    @Test fun versionShortLabel_blankResolutionNoHdrIsAuto() {
        assertEquals("Auto", TvPlaybackFormatting.versionShortLabel(fileVersion(resolution = null, hdr = false)))
    }

    // --- versionDetailLabel ---

    @Test fun versionDetailLabel_codecContainerSize() {
        val v = fileVersion(
            resolution = "2160p",
            codecVideo = "hevc",
            container = "mkv",
            fileSize = 8L * 1024 * 1024 * 1024,
        )
        // 8 GiB → decimal/adaptive like Apple's ByteCountFormatter (.file): 8.59 GB.
        assertEquals("HEVC · MKV · 8.59 GB", TvPlaybackFormatting.versionDetailLabel(v))
    }

    @Test fun versionDetailLabel_megabytesDecimal() {
        val v = fileVersion(codecVideo = "h264", container = "mp4", fileSize = 500L * 1024 * 1024)
        // 500 MiB = 524_288_000 bytes → 524.3 MB (decimal, 1 dp), matching ByteCountFormatter.
        assertEquals("H.264 · MP4 · 524.3 MB", TvPlaybackFormatting.versionDetailLabel(v))
    }

    // --- audioValueLabel / audioOptions ---

    @Test fun audioValueLabel_codecLayoutLanguage() {
        val v = fileVersion(
            audio = listOf(audioTrack(codec = "EAC3", layout = "5.1", lang = "English", default = true)),
        )
        // Auto previews its resolution (QA 2026-07-08 / Apple parity).
        assertEquals("Auto - EAC3 5.1 - English", TvPlaybackFormatting.audioValueLabel(v, selectedAudioTrackIndex = null))
        assertEquals("EAC3 5.1 - English", TvPlaybackFormatting.audioValueLabel(v, selectedAudioTrackIndex = 0))
    }

    @Test fun audioValueLabel_eng3LetterCode() {
        val v = fileVersion(audio = listOf(audioTrack(codec = "aac", layout = "stereo", lang = "eng")))
        assertEquals("Auto - AAC Stereo - English", TvPlaybackFormatting.audioValueLabel(v, selectedAudioTrackIndex = null))
    }

    @Test fun audioValueLabel_unknownWhenNoTracks() {
        assertEquals("Unknown", TvPlaybackFormatting.audioValueLabel(fileVersion(), selectedAudioTrackIndex = null))
    }

    @Test fun audioOptions_selectionFollowsSelectedIndex() {
        val v = fileVersion(
            audio = listOf(
                audioTrack(codec = "aac", layout = "stereo", lang = "eng"),
                audioTrack(codec = "eac3", layout = "5.1", lang = "fre"),
            ),
        )
        val opts = TvPlaybackFormatting.audioOptions(v, selectedAudioTrackIndex = 1)
        assertEquals(2, opts.size)
        assertEquals(0, opts[0].ordinal)
        assertFalse(opts[0].isSelected)
        assertTrue(opts[1].isSelected)
    }

    @Test fun audioOptions_defaultSelectedWhenNoSelection() {
        val v = fileVersion(
            audio = listOf(
                audioTrack(codec = "aac", lang = "eng"),
                audioTrack(codec = "eac3", lang = "fre", default = true),
            ),
        )
        val opts = TvPlaybackFormatting.audioOptions(v, selectedAudioTrackIndex = null)
        assertFalse(opts[0].isSelected)
        assertTrue(opts[1].isSelected)
    }

    // --- subtitleValueLabel / subtitleOptions ---

    @Test fun subtitleValueLabel_offForMinusOne() {
        assertEquals("Off", TvPlaybackFormatting.subtitleValueLabel(fileVersion(), selectedSubtitleTrackIndex = -1))
    }

    @Test fun subtitleValueLabel_autoForNull() {
        // No default-flagged track: Auto resolves to none (QA 2026-07-08).
        assertEquals("Auto - None", TvPlaybackFormatting.subtitleValueLabel(fileVersion(), selectedSubtitleTrackIndex = null))
    }

    @Test fun subtitleValueLabel_languageForSelected() {
        // Selection is by ORDINAL: the single track sits at ordinal 0 even though
        // its stream index is 3.
        val v = fileVersion(subtitles = listOf(subtitleTrack(index = 3, lang = "eng")))
        assertEquals("English", TvPlaybackFormatting.subtitleValueLabel(v, selectedSubtitleTrackIndex = 0))
    }

    @Test fun subtitleValueLabel_forcedBadge() {
        val v = fileVersion(subtitles = listOf(subtitleTrack(index = 2, lang = "fre", forced = true)))
        assertEquals("Forced - French", TvPlaybackFormatting.subtitleValueLabel(v, selectedSubtitleTrackIndex = 0))
    }

    @Test fun subtitleValueLabel_hearingImpairedBadge() {
        val v = fileVersion(subtitles = listOf(subtitleTrack(index = 1, lang = "eng", title = "English SDH")))
        assertEquals("SDH - English", TvPlaybackFormatting.subtitleValueLabel(v, selectedSubtitleTrackIndex = 0))
    }

    @Test fun subtitleOptions_useFlatOrdinalNotStreamIndex() {
        // Stream indexes are non-ordinal and collide (external tracks decode 0).
        // selectionIndex must be the 0-based ordinal, with distinct stable ids,
        // and selection matches by ordinal.
        val v = fileVersion(
            subtitles = listOf(
                subtitleTrack(index = 0, lang = "eng", external = true),
                subtitleTrack(index = 0, lang = "fre", external = true),
                subtitleTrack(index = 5, lang = "spa"),
            ),
        )
        val opts = TvPlaybackFormatting.subtitleOptions(v, selectedSubtitleTrackIndex = 1)
        assertEquals(listOf(0, 1, 2), opts.map { it.selectionIndex })
        assertEquals(3, opts.map { it.stableId }.toSet().size)
        assertFalse(opts[0].isSelected)
        assertTrue(opts[1].isSelected)
        assertFalse(opts[2].isSelected)
    }

    @Test fun subtitleOptions_carryForcedAndDefaultDetail() {
        val v = fileVersion(
            subtitles = listOf(
                subtitleTrack(index = 0, lang = "eng", codec = "srt", forced = true, default = true),
            ),
        )
        val opts = TvPlaybackFormatting.subtitleOptions(v, selectedSubtitleTrackIndex = 0)
        assertEquals(1, opts.size)
        assertEquals(0, opts[0].selectionIndex)
        assertTrue(opts[0].isSelected)
        assertTrue(opts[0].detail.contains("Forced"))
        assertTrue(opts[0].detail.contains("Default"))
    }

    // --- editions (Android model has no edition data → single group) ---

    @Test fun editions_collapseToSingleGroup() {
        val versions = listOf(fileVersion(fileId = 1), fileVersion(fileId = 2))
        val editions = TvPlaybackFormatting.editions(versions)
        assertEquals(1, editions.size)
        assertEquals(2, editions[0].versions.size)
    }

    @Test fun editions_emptyWhenNoVersions() {
        assertTrue(TvPlaybackFormatting.editions(emptyList()).isEmpty())
    }

    // --- builders matching the real Android model constructors ---

    private fun fileVersion(
        fileId: Int = 1,
        resolution: String? = null,
        codecVideo: String? = null,
        hdr: Boolean = false,
        container: String? = null,
        fileSize: Long = 0,
        audio: List<AudioTrack>? = null,
        subtitles: List<SubtitleTrack>? = null,
    ): FileVersion = FileVersion(
        fileId = fileId,
        resolution = resolution,
        codecVideo = codecVideo,
        hdr = hdr,
        container = container,
        fileSize = fileSize,
        audioTracks = audio,
        subtitleTracks = subtitles,
    )

    private fun audioTrack(
        index: Int = 0,
        codec: String? = null,
        layout: String? = null,
        channels: Int? = null,
        lang: String? = null,
        title: String? = null,
        default: Boolean = false,
    ): AudioTrack = AudioTrack(
        index = index,
        codec = codec,
        channelLayout = layout,
        channels = channels,
        language = lang,
        title = title,
        isDefault = default,
    )

    private fun subtitleTrack(
        index: Int = 0,
        codec: String? = null,
        lang: String? = null,
        title: String? = null,
        forced: Boolean = false,
        default: Boolean = false,
        external: Boolean = false,
    ): SubtitleTrack = SubtitleTrack(
        index = index,
        codec = codec,
        language = lang,
        title = title,
        forced = forced,
        isDefault = default,
        external = external,
    )
}
