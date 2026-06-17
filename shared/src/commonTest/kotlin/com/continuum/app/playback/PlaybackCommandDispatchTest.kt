package com.continuum.app.playback

import com.continuum.app.network.PlaybackRealtimeEvent
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals

class PlaybackCommandDispatchTest {
    private val json = Json
    private fun cmd(name: String, payload: String = "{}") =
        PlaybackRealtimeEvent.Command("c1", "s1", name, json.parseToJsonElement(payload).jsonObject)

    @Test fun pauseMapsToPause() {
        assertEquals(PlaybackAction.Pause, decidePlaybackAction(cmd("pause")))
    }

    @Test fun seekReadsPositionSeconds() {
        assertEquals(PlaybackAction.SeekTo(42.5), decidePlaybackAction(cmd("seek", """{"position_seconds":42.5}""")))
    }

    @Test fun seekReadsPositionAlias() {
        assertEquals(PlaybackAction.SeekTo(10.0), decidePlaybackAction(cmd("seek", """{"position":10.0}""")))
    }

    @Test fun seekReadsSecondsAlias() {
        assertEquals(PlaybackAction.SeekTo(5.0), decidePlaybackAction(cmd("seek", """{"seconds":5.0}""")))
    }

    @Test fun displayMessageReadsMessage() {
        assertEquals(PlaybackAction.ShowMessage("hi"), decidePlaybackAction(cmd("display_message", """{"message":"hi"}""")))
    }

    @Test fun terminateMapsToStop() {
        assertEquals(PlaybackAction.Stop, decidePlaybackAction(cmd("terminate")))
    }

    @Test fun serverRestartingIsIgnored() {
        assertEquals(PlaybackAction.Ignore, decidePlaybackAction(cmd("server_restarting")))
    }

    @Test fun setVolumeIsRejected(/* not advertised, so not actioned */) {
        assertEquals(PlaybackAction.Reject, decidePlaybackAction(cmd("set_volume", """{"volume":0.5}""")))
    }

    @Test fun unsupportedCommandIsRejected() {
        assertEquals(PlaybackAction.Reject, decidePlaybackAction(cmd("play_media")))
    }

    @Test fun malformedSeekIsRejected() {
        assertEquals(PlaybackAction.Reject, decidePlaybackAction(cmd("seek", """{}""")))
    }

    @Test fun quotedNumberSeekIsRejected() {
        assertEquals(PlaybackAction.Reject, decidePlaybackAction(cmd("seek", """{"position_seconds":"42"}""")))
    }

    @Test fun nonFiniteSeekIsRejected() {
        // JSON has no NaN/Infinity literal; a quoted "Infinity" must not seek.
        assertEquals(PlaybackAction.Reject, decidePlaybackAction(cmd("seek", """{"position_seconds":"Infinity"}""")))
    }
}
