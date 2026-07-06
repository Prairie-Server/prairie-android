package org.siloserver.silo.playback

import org.siloserver.silo.model.catalog.AudioTrack
import org.siloserver.silo.model.catalog.SubtitleTrack
import org.siloserver.silo.model.playback.PlayerSubtitleInfo
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TrackSelectionFingerprintTest {

    @Test
    fun resolvesAudioFingerprintBackToTrackOrdinal() {
        val tracks = listOf(
            AudioTrack(index = 0, codec = "aac", language = "eng", title = "Stereo"),
            AudioTrack(index = 1, codec = "eac3", language = "eng", title = "5.1"),
        )
        val saved = audioTrackFingerprint(tracks[1])

        assertEquals(1, resolveAudioTrackOrdinal(tracks, saved))
    }

    @Test
    fun resolvesSubtitleOffAndTrackFingerprints() {
        val tracks = listOf(
            SubtitleTrack(index = 0, codec = "srt", language = "eng", title = "English"),
            SubtitleTrack(index = 3, codec = "ass", language = "eng", title = "Signs", forced = true),
        )

        assertEquals(-1, resolveSubtitleTrackOrdinal(tracks, SUBTITLE_OFF_FINGERPRINT))
        assertEquals(1, resolveSubtitleTrackOrdinal(tracks, subtitleTrackFingerprint(tracks[1])))
    }

    @Test
    fun playerSubtitleInfoUsesSameSubtitleFingerprintShape() {
        val catalog = SubtitleTrack(index = 2, codec = "srt", language = "eng", title = "English CC", forced = true)
        val mounted = PlayerSubtitleInfo(
            index = 2,
            codec = "srt",
            language = "eng",
            label = "English CC",
            forced = true,
            url = "/stream/subtitles/2.vtt",
        )

        assertEquals(subtitleTrackFingerprint(catalog), subtitleTrackFingerprint(mounted))
    }

    @Test
    fun blankOrUnknownFingerprintDoesNotResolve() {
        val tracks = listOf(AudioTrack(index = 0, codec = "aac", language = "eng", title = "Stereo"))

        assertNull(resolveAudioTrackOrdinal(tracks, null))
        assertNull(resolveAudioTrackOrdinal(tracks, ""))
        assertNull(resolveAudioTrackOrdinal(tracks, "missing"))
    }
}
