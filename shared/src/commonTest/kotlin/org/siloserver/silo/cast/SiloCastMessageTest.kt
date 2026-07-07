package org.siloserver.silo.cast

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

/**
 * Wire-parity tests against silo-apple's SiloControlMessage encoder
 * (iosApp/Control/SiloControlProtocol.swift). Apple cannot change, so every
 * golden fixture here is the byte-semantics Apple actually produces/expects:
 * camelCase payload fields, the `{type, v, <kind>}` envelope, payloadless
 * ping/pong/close, Int64 track ids, and the Name enum's exact strings.
 * Fixtures are compared as parsed JSON so key order is irrelevant.
 */
class SiloCastMessageTest {
    private val json = Json { encodeDefaults = false; ignoreUnknownKeys = true }

    private fun assertWireEquals(expected: String, message: SiloCastMessage) {
        assertEquals(
            Json.parseToJsonElement(expected),
            Json.parseToJsonElement(json.encodeToString(SiloCastMessage.serializer(), message)),
        )
    }

    @Test
    fun helloMatchesAppleEnvelopeAndFields() {
        assertWireEquals(
            """
            {"type":"hello","v":1,"hello":{
                "role":"phone",
                "deviceName":"Pixel 9",
                "deviceId":"android-abc",
                "serverId":"srv-1",
                "serverName":"Home",
                "supportedVersions":[1]
            }}
            """,
            SiloCastMessage.Hello(
                SiloCastHello(
                    role = SiloCastPeerRole.Phone,
                    deviceName = "Pixel 9",
                    deviceId = "android-abc",
                    serverId = "srv-1",
                    serverName = "Home",
                    supportedVersions = listOf(SiloCastProtocol.version),
                ),
            ),
        )
    }

    @Test
    fun decodesAppleTvHello() {
        val decoded = json.decodeFromString(
            SiloCastMessage.serializer(),
            """{"type":"hello","v":1,"hello":{"role":"tv","deviceName":"Living Room",""" +
                """"deviceId":"atv-1","serverId":"srv-1","serverName":"Home","supportedVersions":[1]}}""",
        )
        val hello = assertIs<SiloCastMessage.Hello>(decoded).hello
        assertEquals(SiloCastPeerRole.Tv, hello.role)
        assertEquals("srv-1", hello.serverId)
    }

    @Test
    fun launchNestsPlaybackRequestLikeApple() {
        assertWireEquals(
            """
            {"type":"launch","v":1,"launch":{
                "serverId":"srv-1",
                "playback":{
                    "contentId":"movie-42",
                    "fileId":7,
                    "audioTrackIndex":1,
                    "startFromBeginning":false,
                    "resumePosition":120.5
                }
            }}
            """,
            SiloCastMessage.Launch(
                SiloCastLaunchRequest(
                    serverId = "srv-1",
                    playback = SiloCastPlaybackRequest(
                        contentId = "movie-42",
                        fileId = 7,
                        audioTrackIndex = 1,
                        subtitleTrackIndex = null,
                        startFromBeginning = false,
                        resumePosition = 120.5,
                    ),
                ),
            ),
        )
    }

    @Test
    fun controlCommandsUseAppleNamesAndFields() {
        assertWireEquals(
            """{"type":"control","v":1,"control":{"name":"play_pause"}}""",
            SiloCastMessage.Control(SiloCastControlCommand.playPause()),
        )
        assertWireEquals(
            """{"type":"control","v":1,"control":{"name":"set_quality","value":"hd-1080"}}""",
            SiloCastMessage.Control(SiloCastControlCommand.setQuality("hd-1080")),
        )
        assertWireEquals(
            """{"type":"control","v":1,"control":{"name":"select_audio_track","trackId":3}}""",
            SiloCastMessage.Control(SiloCastControlCommand.selectAudioTrack(3L)),
        )
        assertWireEquals(
            """{"type":"control","v":1,"control":{"name":"set_subtitle_sync_ms","milliseconds":-250}}""",
            SiloCastMessage.Control(SiloCastControlCommand.setSubtitleSyncMs(-250)),
        )
        assertWireEquals(
            """{"type":"control","v":1,"control":{"name":"play_next"}}""",
            SiloCastMessage.Control(SiloCastControlCommand.playNext()),
        )
    }

    @Test
    fun subtitleOffOmitsTrackIdLikeApple() {
        val message = SiloCastMessage.Control(SiloCastControlCommand.selectSubtitleTrack(null))
        assertWireEquals(
            """{"type":"control","v":1,"control":{"name":"select_subtitle_track"}}""",
            message,
        )
        val decoded = json.decodeFromString(
            SiloCastMessage.serializer(),
            """{"type":"control","v":1,"control":{"name":"select_subtitle_track"}}""",
        )
        assertNull(assertIs<SiloCastMessage.Control>(decoded).control.trackId)
    }

    @Test
    fun pingPongCloseCarryNoPayload() {
        assertWireEquals("""{"type":"ping","v":1}""", SiloCastMessage.Ping())
        assertWireEquals("""{"type":"pong","v":1}""", SiloCastMessage.Pong())
        assertWireEquals("""{"type":"close","v":1}""", SiloCastMessage.Close())
        // Apple's decoder reads only `type` for these kinds.
        assertIs<SiloCastMessage.Ping>(
            json.decodeFromString(SiloCastMessage.serializer(), """{"type":"ping","v":1}"""),
        )
    }

    @Test
    fun decodesApplePlaybackState() {
        val decoded = json.decodeFromString(
            SiloCastMessage.serializer(),
            """
            {"type":"state","v":1,"state":{
                "contentId":"ep-9","title":"S01E09","isPlaying":true,"isLoading":false,
                "isBuffering":false,"currentTime":42.0,"duration":2700.0,
                "audioTracks":[{"kind":"audio","trackId":1,"title":"English","detail":"EAC3 5.1"}],
                "subtitleTracks":[],
                "selectedAudioTrackId":1,
                "qualityOptions":[{"id":"auto","label":"Auto"}],
                "activeQualityId":"auto","isQualitySwitching":false,
                "playbackSpeed":1.0,"videoGravity":"fit","hdrEnabled":true,
                "supportsVideoGravity":true,"supportsHDRToggle":false,
                "subtitlePosition":"standard",
                "volume":1.0,"isMuted":false,"hasNextEpisode":true,
                "nextEpisodeTitle":"S01E10"
            }}
            """,
        )
        val state = assertIs<SiloCastMessage.State>(decoded).state
        assertEquals("ep-9", state.contentId)
        assertEquals(1L, state.audioTracks.single().trackId)
        assertEquals("EAC3 5.1", state.audioTracks.single().detail)
        assertEquals("auto", state.activeQualityId)
        assertEquals("standard", state.subtitlePosition)
        assertNull(state.subtitleSyncMs)
    }

    @Test
    fun errorMatchesAppleShape() {
        assertWireEquals(
            """{"type":"error","v":1,"error":{"code":"server_mismatch","message":"wrong server"}}""",
            SiloCastMessage.Error(SiloCastError(code = "server_mismatch", message = "wrong server")),
        )
    }

    @Test
    fun protocolConstantsMatchApple() {
        assertEquals(1, SiloCastProtocol.version)
        assertEquals("_silocast._tcp", SiloCastProtocol.serviceType)
    }
}
