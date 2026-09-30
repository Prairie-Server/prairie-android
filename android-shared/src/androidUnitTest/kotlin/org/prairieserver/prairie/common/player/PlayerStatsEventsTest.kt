package org.prairieserver.prairie.common.player

import androidx.annotation.OptIn
import androidx.media3.common.Format
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import org.prairieserver.prairie.playback.stats.PlayerEventSeverity
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame

@OptIn(UnstableApi::class)
class PlayerStatsEventsTest {

    @Test
    fun `errors are recorded with the URL query stripped`() {
        val snapshot = PlayerStatsSnapshot().recordPlayerEvent(
            PlaybackAnalyticsListener.Event.LoadError(
                IOException("403 for https://srv/api/v2/stream/a/master.m3u8?stream_token=secret"),
            ),
            nowEpochMs = 1_000L,
        )
        val entry = snapshot.events.entries.single()
        assertEquals(PlayerEventSeverity.ERROR, entry.severity)
        assertEquals("Load error: IOException: 403 for https://srv/api/v2/stream/a/master.m3u8?…", entry.message)
        assertEquals(1_000L, entry.atEpochMs)
    }

    @Test
    fun `player errors name the error code`() {
        val error = PlaybackException("boom", null, PlaybackException.ERROR_CODE_DECODING_FAILED)
        val (severity, message) = describePlayerEvent(PlaybackAnalyticsListener.Event.PlayerError(error))!!
        assertEquals(PlayerEventSeverity.ERROR, severity)
        assertEquals("Player error ERROR_CODE_DECODING_FAILED: boom", message)
    }

    @Test
    fun `state, format and decoder events read as short lines`() {
        fun line(e: PlaybackAnalyticsListener.Event) = describePlayerEvent(e)?.second
        assertEquals("Buffering", line(PlaybackAnalyticsListener.Event.PlaybackStateChanged(Player.STATE_BUFFERING, 0, 0, true)))
        assertEquals("Ready", line(PlaybackAnalyticsListener.Event.PlaybackStateChanged(Player.STATE_READY, 0, 0, true)))
        assertEquals("Ended", line(PlaybackAnalyticsListener.Event.PlaybackStateChanged(Player.STATE_ENDED, 0, 0, true)))
        assertNull(line(PlaybackAnalyticsListener.Event.PlaybackStateChanged(Player.STATE_IDLE, 0, 0, true)))
        assertEquals(
            "Video format hvc1.2.4.L153 3840x1600",
            line(
                PlaybackAnalyticsListener.Event.VideoFormatChanged(
                    Format.Builder().setCodecs("hvc1.2.4.L153").setWidth(3840).setHeight(1600).build(),
                ),
            ),
        )
        assertEquals(
            "Audio format audio/eac3 5.1",
            line(
                PlaybackAnalyticsListener.Event.AudioFormatChanged(
                    Format.Builder().setSampleMimeType("audio/eac3").setChannelCount(6).build(),
                ),
            ),
        )
        assertEquals(
            "Video decoder c2.qti.hevc.decoder (42 ms)",
            line(PlaybackAnalyticsListener.Event.VideoDecoderInitialized("c2.qti.hevc.decoder", 42)),
        )
        assertEquals("Audio decoder c2.android.aac", line(PlaybackAnalyticsListener.Event.AudioDecoderInitialized("c2.android.aac")))
        assertEquals("Dropped 5 frames", line(PlaybackAnalyticsListener.Event.DroppedFrames(5, 1000)))
        assertEquals("Audio underrun", line(PlaybackAnalyticsListener.Event.AudioUnderrun))
        assertEquals("First frame", line(PlaybackAnalyticsListener.Event.FirstFrameRendered(1)))
        assertEquals("Seek", line(PlaybackAnalyticsListener.Event.SeekStarted(1)))
    }

    @Test
    fun `chatty events are not recorded`() {
        val start = PlayerStatsSnapshot()
        assertSame(start, start.recordPlayerEvent(PlaybackAnalyticsListener.Event.BandwidthEstimate(1), 1))
        assertSame(start, start.recordPlayerEvent(PlaybackAnalyticsListener.Event.TrackSnapshot("x"), 1))
    }

    @Test
    fun `reducer tracks declared bitrate and channels`() {
        val video = reducePlayerStats(
            PlayerStatsSnapshot(),
            PlaybackAnalyticsListener.Event.VideoFormatChanged(Format.Builder().setAverageBitrate(18_000_000).build()),
        )
        assertEquals(18_000_000L, video.videoBitrateBps)
        val audio = reducePlayerStats(
            video,
            PlaybackAnalyticsListener.Event.AudioFormatChanged(Format.Builder().setChannelCount(2).build()),
        )
        assertEquals(2, audio.audioChannels)
        assertEquals(18_000_000L, audio.videoBitrateBps)
    }

    @Test
    fun `health rows list bitrate, channels, buffer, rebuffers and startup`() {
        val rows = PlayerStatsSnapshot(
            videoBitrateBps = 18_000_000,
            audioChannels = 6,
            bufferedDurationMs = 12_340,
            rebufferCount = 2,
            rebufferTotalMs = 900,
            rebufferMaxMs = 600,
            startupReadyMs = 850,
        ).healthRows()
        assertEquals(
            listOf(
                "Video bitrate" to "18.0 Mbps",
                "Audio channels" to "5.1",
                "Buffer ahead" to "12.3 s",
                "Rebuffers" to "2 (900 ms total, max 600 ms)",
                "Startup" to "850 ms",
            ),
            rows.map { it.label to it.value },
        )
        assertEquals(emptyList<org.prairieserver.prairie.playback.stats.StatsRow>(), PlayerStatsSnapshot().healthRows())
    }

    @Test
    fun `finishing a session keeps the event log`() {
        val withEvent = PlayerStatsSnapshot().recordPlayerEvent(PlaybackAnalyticsListener.Event.AudioUnderrun, 5)
        assertEquals(withEvent.events, finishPlayerStats(withEvent, detailedCapture = false).next.events)
    }
}
