package com.continuum.app.common.player.video

import com.continuum.app.common.player.Playability
import com.continuum.app.model.playback.PlayMethod
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PlaybackStartupStallDetectorTest {

    @Test
    fun directStartupTriggersFallbackAfterGraceWhenPlaybackNeverAdvances() {
        val detector = PlaybackStartupStallDetector(startupGraceMs = 10_000)
        detector.onMounted(
            sessionKey = "session-1",
            playMethod = PlayMethod.DIRECT,
            startPositionMs = 1_767_000,
            nowMs = 0,
        )

        assertNull(
            detector.sample(
                sessionKey = "session-1",
                nowMs = 9_999,
                playWhenReady = true,
                isPlaying = false,
                isBuffering = true,
                currentPositionMs = 1_767_000,
                bufferedPositionMs = 1_767_115,
            ),
        )

        assertEquals(
            Playability.StartupStalled(bufferedAheadMs = 115, stalledForMs = 10_001),
            detector.sample(
                sessionKey = "session-1",
                nowMs = 10_001,
                playWhenReady = true,
                isPlaying = false,
                isBuffering = true,
                currentPositionMs = 1_767_000,
                bufferedPositionMs = 1_767_115,
            ),
        )
    }

    @Test
    fun playingOrPositionProgressDisarmsStartupFallback() {
        val detector = PlaybackStartupStallDetector(startupGraceMs = 10_000)
        detector.onMounted(
            sessionKey = "session-1",
            playMethod = PlayMethod.DIRECT,
            startPositionMs = 90_000,
            nowMs = 0,
        )

        assertNull(
            detector.sample(
                sessionKey = "session-1",
                nowMs = 2_000,
                playWhenReady = true,
                isPlaying = true,
                isBuffering = false,
                currentPositionMs = 91_000,
                bufferedPositionMs = 100_000,
            ),
        )

        assertNull(
            detector.sample(
                sessionKey = "session-1",
                nowMs = 20_000,
                playWhenReady = true,
                isPlaying = false,
                isBuffering = true,
                currentPositionMs = 91_000,
                bufferedPositionMs = 91_500,
            ),
        )
    }

    @Test
    fun nonDirectOrPausedStartupDoesNotTriggerFallback() {
        val detector = PlaybackStartupStallDetector(startupGraceMs = 10_000)
        detector.onMounted(
            sessionKey = "session-1",
            playMethod = PlayMethod.TRANSCODE,
            startPositionMs = 0,
            nowMs = 0,
        )

        assertNull(
            detector.sample(
                sessionKey = "session-1",
                nowMs = 20_000,
                playWhenReady = true,
                isPlaying = false,
                isBuffering = true,
                currentPositionMs = 0,
                bufferedPositionMs = 0,
            ),
        )

        detector.onMounted(
            sessionKey = "session-2",
            playMethod = PlayMethod.DIRECT,
            startPositionMs = 0,
            nowMs = 0,
        )

        assertNull(
            detector.sample(
                sessionKey = "session-2",
                nowMs = 20_000,
                playWhenReady = false,
                isPlaying = false,
                isBuffering = true,
                currentPositionMs = 0,
                bufferedPositionMs = 0,
            ),
        )
    }

    @Test
    fun newMountResetsSignalState() {
        val detector = PlaybackStartupStallDetector(startupGraceMs = 10_000)
        detector.onMounted(
            sessionKey = "session-1",
            playMethod = PlayMethod.DIRECT,
            startPositionMs = 0,
            nowMs = 0,
        )
        detector.sample(
            sessionKey = "session-1",
            nowMs = 20_000,
            playWhenReady = true,
            isPlaying = false,
            isBuffering = true,
            currentPositionMs = 0,
            bufferedPositionMs = 0,
        )

        detector.onMounted(
            sessionKey = "session-2",
            playMethod = PlayMethod.DIRECT,
            startPositionMs = 0,
            nowMs = 30_000,
        )

        assertNull(
            detector.sample(
                sessionKey = "session-1",
                nowMs = 39_999,
                playWhenReady = true,
                isPlaying = false,
                isBuffering = true,
                currentPositionMs = 0,
                bufferedPositionMs = 0,
            ),
        )

        assertEquals(
            Playability.StartupStalled(bufferedAheadMs = 0, stalledForMs = 10_001),
            detector.sample(
                sessionKey = "session-2",
                nowMs = 40_001,
                playWhenReady = true,
                isPlaying = false,
                isBuffering = true,
                currentPositionMs = 0,
                bufferedPositionMs = 0,
            ),
        )
    }
}
