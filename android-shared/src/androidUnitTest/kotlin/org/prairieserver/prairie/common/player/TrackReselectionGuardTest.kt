package org.prairieserver.prairie.common.player

import androidx.media3.common.Player
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Regression cover for a playback-killing crash seen on a Google TV Streamer:
 *
 * ```
 * ExoPlaybackException: Unexpected runtime error
 *   Caused by: NullPointerException: MediaPeriodHolder.info
 *     at ExoPlayerImplInternal.seekToCurrentPosition
 *     at ExoPlayerImplInternal.reselectTracksInternalAndSeek
 * ```
 *
 * An HDMI audio-route change ("Audio output capabilities updated: codecs=[]
 * maxChannels=2") re-fired the track-selection effect while the player was
 * being torn down. ExoPlayer applies a reselection by seeking the current media
 * period, and there was none left to seek, so playback ended in ERROR(7).
 *
 * The rule: never hand a reselection to a player that has nothing to apply it
 * to. Presets are re-applied when the next player is built, so withholding them
 * here loses nothing.
 */
class TrackReselectionGuardTest {

    @Test
    fun `withholds reselection from an idle player`() {
        assertTrue(shouldSkipTrackReselection(Player.STATE_IDLE, timelineEmpty = false))
    }

    @Test
    fun `withholds reselection when the timeline is empty`() {
        assertTrue(shouldSkipTrackReselection(Player.STATE_READY, timelineEmpty = true))
    }

    @Test
    fun `withholds reselection from an idle player with an empty timeline`() {
        assertTrue(shouldSkipTrackReselection(Player.STATE_IDLE, timelineEmpty = true))
    }

    @Test
    fun `applies reselection while buffering, ready or ended`() {
        // Ended still holds a media period, so a reselection resolves normally;
        // only IDLE and an empty timeline are unsafe.
        for (state in listOf(Player.STATE_BUFFERING, Player.STATE_READY, Player.STATE_ENDED)) {
            assertFalse(
                shouldSkipTrackReselection(state, timelineEmpty = false),
                "state $state with a populated timeline should accept a reselection",
            )
        }
    }
}
