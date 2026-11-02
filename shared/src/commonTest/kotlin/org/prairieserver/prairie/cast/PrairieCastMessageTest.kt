package org.prairieserver.prairie.cast

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Wire-parity tests against prairie-apple's PrairieControlMessage encoder
 * (iosApp/Control/PrairieControlProtocol.swift). Apple cannot change, so every
 * golden fixture here is the byte-semantics Apple actually produces/expects:
 * camelCase payload fields, the `{type, v, <kind>}` envelope, payloadless
 * ping/pong/close, Int64 track ids, and the Name enum's exact strings.
 * Fixtures are compared as parsed JSON so key order is irrelevant.
 */
class PrairieCastMessageTest {
    private val json = Json { encodeDefaults = false; ignoreUnknownKeys = true }

    private fun assertWireEquals(expected: String, message: PrairieCastMessage) {
        assertEquals(
            Json.parseToJsonElement(expected),
            Json.parseToJsonElement(json.encodeToString(PrairieCastMessage.serializer(), message)),
        )
    }

    @Test
    fun helloMatchesAppleEnvelopeAndFields() {
        assertWireEquals(
            """
            {"type":"hello","v":2,"hello":{
                "role":"phone",
                "deviceName":"Pixel 9",
                "deviceId":"android-abc",
                "serverId":"srv-1",
                "serverName":"Home",
                "supportedVersions":[2]
            }}
            """,
            PrairieCastMessage.Hello(
                PrairieCastHello(
                    role = PrairieCastPeerRole.Phone,
                    deviceName = "Pixel 9",
                    deviceId = "android-abc",
                    serverId = "srv-1",
                    serverName = "Home",
                    supportedVersions = listOf(PrairieCastProtocol.version),
                ),
            ),
        )
    }

    @Test
    fun decodesAppleTvHello() {
        val decoded = json.decodeFromString(
            PrairieCastMessage.serializer(),
            """{"type":"hello","v":2,"hello":{"role":"tv","deviceName":"Living Room",""" +
                """"deviceId":"atv-1","serverId":"srv-1","serverName":"Home","supportedVersions":[2]}}""",
        )
        val hello = assertIs<PrairieCastMessage.Hello>(decoded).hello
        assertEquals(PrairieCastPeerRole.Tv, hello.role)
        assertEquals("srv-1", hello.serverId)
    }

    @Test
    fun launchNestsPlaybackRequestLikeApple() {
        assertWireEquals(
            """
            {"type":"launch","v":2,"launch":{
                "serverId":"srv-1",
                "playback":{
                    "contentId":"movie-42",
                    "fileId":7,
                    "audioTrackIndex":1,
                    "startFromBeginning":false,
                    "resumePosition":120.5,
                    "pinsVersion":true
                }
            }}
            """,
            PrairieCastMessage.Launch(
                PrairieCastLaunchRequest(
                    serverId = "srv-1",
                    playback = PrairieCastPlaybackRequest(
                        contentId = "movie-42",
                        fileId = 7,
                        audioTrackIndex = 1,
                        subtitleTrackIndex = null,
                        startFromBeginning = false,
                        resumePosition = 120.5,
                        pinsVersion = true,
                    ),
                ),
            ),
        )
    }

    @Test
    fun scopedLaunchPreservesLibraryThroughTheWire() {
        val message = PrairieCastMessage.Launch(
            PrairieCastLaunchRequest(
                serverId = "srv-1",
                playback = PrairieCastPlaybackRequest(
                    contentId = "movie-42",
                    libraryId = 8,
                    startFromBeginning = true,
                ),
            ),
        )
        assertWireEquals(
            """{"type":"launch","v":2,"launch":{"serverId":"srv-1","playback":{"contentId":"movie-42","startFromBeginning":true,"libraryId":8}}}""",
            message,
        )
        val decoded = json.decodeFromString(
            PrairieCastMessage.serializer(), json.encodeToString(PrairieCastMessage.serializer(), message),
        )
        assertEquals(message, decoded)
    }

    @Test
    fun legacyLaunchWithoutLibraryRemainsUnscoped() {
        val decoded = json.decodeFromString(
            PrairieCastMessage.serializer(),
            """{"type":"launch","v":2,"launch":{"serverId":"srv-1","playback":{"contentId":"movie-42","startFromBeginning":true}}}""",
        )
        assertNull(assertIs<PrairieCastMessage.Launch>(decoded).launch.playback.libraryId)
        assertNull(assertIs<PrairieCastMessage.Launch>(decoded).launch.playback.pinsVersion)
    }

    @Test
    fun controlCommandsUseAppleNamesAndFields() {
        assertEquals(PrairieCastControlCommand.Play, PrairieCastControlCommand.play().name)
        assertEquals(PrairieCastControlCommand.Pause, PrairieCastControlCommand.pause().name)
        assertEquals(PrairieCastControlCommand.Stop, PrairieCastControlCommand.stop().name)
        assertEquals(12.5, PrairieCastControlCommand.seek(12.5).seconds)
        assertEquals(1.25, PrairieCastControlCommand.setPlaybackSpeed(1.25).speed)
        assertEquals("fit", PrairieCastControlCommand.setVideoGravity("fit").value)
        assertEquals("bottom", PrairieCastControlCommand.setSubtitlePosition("bottom").value)
        assertEquals(0.4, PrairieCastControlCommand.setVolume(0.4).volume)
        assertEquals(true, PrairieCastControlCommand.setMuted(true).enabled)

        assertWireEquals(
            """{"type":"control","v":2,"control":{"name":"play_pause"}}""",
            PrairieCastMessage.Control(PrairieCastControlCommand.playPause()),
        )
        assertWireEquals(
            """{"type":"control","v":2,"control":{"name":"set_quality","value":"hd-1080"}}""",
            PrairieCastMessage.Control(PrairieCastControlCommand.setQuality("hd-1080")),
        )
        assertWireEquals(
            """{"type":"control","v":2,"control":{"name":"select_audio_track","trackId":3}}""",
            PrairieCastMessage.Control(PrairieCastControlCommand.selectAudioTrack(3L)),
        )
        assertWireEquals(
            """{"type":"control","v":2,"control":{"name":"set_subtitle_sync_ms","milliseconds":-250}}""",
            PrairieCastMessage.Control(PrairieCastControlCommand.setSubtitleSyncMs(-250)),
        )
        assertWireEquals(
            """{"type":"control","v":2,"control":{"name":"play_next"}}""",
            PrairieCastMessage.Control(PrairieCastControlCommand.playNext()),
        )
    }

    @Test
    fun subtitleOffOmitsTrackIdLikeApple() {
        val message = PrairieCastMessage.Control(PrairieCastControlCommand.selectSubtitleTrack(null))
        assertWireEquals(
            """{"type":"control","v":2,"control":{"name":"select_subtitle_track"}}""",
            message,
        )
        val decoded = json.decodeFromString(
            PrairieCastMessage.serializer(),
            """{"type":"control","v":2,"control":{"name":"select_subtitle_track"}}""",
        )
        assertNull(assertIs<PrairieCastMessage.Control>(decoded).control.trackId)
    }

    @Test
    fun pingPongCloseCarryNoPayload() {
        assertWireEquals("""{"type":"ping","v":2}""", PrairieCastMessage.Ping())
        assertWireEquals("""{"type":"pong","v":2}""", PrairieCastMessage.Pong())
        assertWireEquals("""{"type":"close","v":2}""", PrairieCastMessage.Close())
        // Apple's decoder reads only `type` for these kinds.
        assertIs<PrairieCastMessage.Ping>(
            json.decodeFromString(PrairieCastMessage.serializer(), """{"type":"ping","v":2}"""),
        )
    }

    @Test
    fun decodesApplePlaybackState() {
        val decoded = json.decodeFromString(
            PrairieCastMessage.serializer(),
            """
            {"type":"state","v":2,"state":{
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
        val state = assertIs<PrairieCastMessage.State>(decoded).state
        assertEquals("ep-9", state.contentId)
        assertEquals(1L, state.audioTracks.single().trackId)
        assertEquals("EAC3 5.1", state.audioTracks.single().detail)
        assertEquals("auto", state.activeQualityId)
        assertEquals("standard", state.subtitlePosition)
        assertNull(state.subtitleSyncMs)
    }

    @Test
    fun stateCarriesPosterAndOverviewOnlyWhenKnown() {
        val frame = { extra: String ->
            """
            {"type":"state","v":2,"state":{
                "title":"Movie","isPlaying":true,"isLoading":false,"isBuffering":false,
                "currentTime":1.0,"duration":2.0,"audioTracks":[],"subtitleTracks":[],
                "qualityOptions":[],"activeQualityId":"auto","isQualitySwitching":false,
                "playbackSpeed":1.0,"videoGravity":"fit","hdrEnabled":false,
                "supportsVideoGravity":true,"supportsHDRToggle":false,$extra
                "volume":1.0,"isMuted":false,"hasNextEpisode":false
            }}
            """
        }
        // A TV that predates the fields.
        val old = assertIs<PrairieCastMessage.State>(json.decodeFromString(PrairieCastMessage.serializer(), frame(""))).state
        assertNull(old.posterUrl)
        assertNull(old.overview)
        assertFalse("posterUrl" in json.encodeToString(PrairieCastMessage.serializer(), PrairieCastMessage.State(state = old)))

        val withArt = old.copy(posterUrl = "https://prairie.example/p.webp?exp=1&sig=x", overview = "Plot")
        assertWireEquals(
            frame(""""posterUrl":"https://prairie.example/p.webp?exp=1&sig=x","overview":"Plot","""),
            PrairieCastMessage.State(state = withArt),
        )
    }

    @Test
    fun errorMatchesAppleShape() {
        assertWireEquals(
            """{"type":"error","v":2,"error":{"code":"server_mismatch","message":"wrong server"}}""",
            PrairieCastMessage.Error(PrairieCastError(code = "server_mismatch", message = "wrong server")),
        )
    }

    @Test
    fun directConstructorsAndFactoriesExposeExpectedValues() {
        val playback = PrairieCastPlaybackRequest(
            contentId = "movie-1",
            fileId = 9,
            audioTrackIndex = 1,
            subtitleTrackIndex = 2,
            startFromBeginning = true,
            resumePosition = null,
        )
        assertEquals(2, playback.subtitleTrackIndex)

        val state = PrairieCastPlaybackState(
            contentId = "movie-1",
            sessionId = "session-1",
            title = "Movie",
            subtitle = "2026",
            isPlaying = false,
            isLoading = true,
            isBuffering = true,
            currentTime = 1.0,
            duration = 2.0,
            audioTracks = listOf(PrairieCastTrack("audio", 11, "English", "AAC")),
            subtitleTracks = listOf(PrairieCastTrack("subtitle", 22, "English CC")),
            selectedAudioTrackId = 11,
            selectedSubtitleTrackId = 22,
            qualityOptions = listOf(PrairieCastQualityOption("auto", "Auto", "Best")),
            activeQualityId = "auto",
            isQualitySwitching = true,
            playbackSpeed = 1.5,
            videoGravity = "fill",
            hdrEnabled = true,
            supportsVideoGravity = true,
            supportsHDRToggle = true,
            subtitleSyncMs = 125,
            subtitlePosition = "bottom",
            supportsSubtitleDelay = true,
            supportsSubtitlePosition = true,
            volume = 0.75,
            isMuted = true,
            hasNextEpisode = true,
            nextEpisodeTitle = "Next",
            error = null,
        ).copy(error = "buffering")
        assertEquals("buffering", state.error)
        assertEquals("Best", state.qualityOptions.single().detail)

        val messages = listOf(
            PrairieCastMessage.HandoffOffer(
                PrairieCastHandoffOffer("req", "srv", "https://prairie.example", null, "profile", null),
                v = 99,
            ),
            PrairieCastMessage.HandoffChallenge(PrairieCastHandoffChallenge("req", "1234", "99", "soon")),
            PrairieCastMessage.HandoffReady(PrairieCastHandoffReady("req", "srv", "profile", "later", reused = true)),
            PrairieCastMessage.HandoffCancel(PrairieCastHandoffCancel("req", "cancelled", null)),
            PrairieCastMessage.Launch(PrairieCastLaunchRequest("srv", playback)),
            PrairieCastMessage.State(state),
            PrairieCastMessage.Error(PrairieCastError("bad_request", "Bad request")),
            PrairieCastMessage.Pong(v = 99),
            PrairieCastMessage.Close(v = 99),
        )
        assertEquals(99, messages.first().v)
        assertEquals(messages.size, messages.map { it.v }.size)

        assertEquals(7L, PrairieCastControlCommand.selectSubtitleTrack(7).trackId)
        assertEquals(PrairieCastControlCommand.PlayPause, PrairieCastControlCommand.playPause().name)
        assertEquals(PrairieCastControlCommand.Stop, PrairieCastControlCommand.stop().name)
    }

    @Test
    fun protocolConstantsMatchApple() {
        assertEquals(2, PrairieCastProtocol.version)
        assertEquals(listOf(2), PrairieCastProtocol.supportedVersions)
        assertEquals("_prairiecast._tcp", PrairieCastProtocol.serviceType)
    }

    @Test
    fun v2HandoffMessagesMatchAppleShapes() {
        assertWireEquals(
            """{"type":"handoff_offer","v":2,"handoffOffer":{"requestId":"req-1","serverId":"srv-1","serverURL":"https://prairie.example","serverName":"Home","profileId":"profile-1","profileName":"Alex"}}""",
            PrairieCastMessage.HandoffOffer(
                PrairieCastHandoffOffer(
                    requestId = "req-1",
                    serverId = "srv-1",
                    serverURL = "https://prairie.example",
                    serverName = "Home",
                    profileId = "profile-1",
                    profileName = "Alex",
                ),
            ),
        )
        assertWireEquals(
            """{"type":"handoff_challenge","v":2,"handoffChallenge":{"requestId":"req-1","userCode":"ABCD","matchCode":"MATCH","expiresAt":"2099-01-01T00:00:00Z"}}""",
            PrairieCastMessage.HandoffChallenge(
                PrairieCastHandoffChallenge("req-1", "ABCD", "MATCH", "2099-01-01T00:00:00Z"),
            ),
        )
        assertWireEquals(
            """{"type":"handoff_ready","v":2,"handoffReady":{"requestId":"req-1","serverId":"srv-1","profileId":"profile-1","sessionExpiresAt":"2099-01-02T00:00:00Z","reused":false}}""",
            PrairieCastMessage.HandoffReady(
                PrairieCastHandoffReady("req-1", "srv-1", "profile-1", "2099-01-02T00:00:00Z", false),
            ),
        )
        assertWireEquals(
            """{"type":"handoff_cancel","v":2,"handoffCancel":{"requestId":"req-1","reason":"denied","message":"No"}}""",
            PrairieCastMessage.HandoffCancel(PrairieCastHandoffCancel("req-1", "denied", "No")),
        )
    }

    @Test
    fun unknownMessageTypeIsDroppedNotFatal() {
        assertNull(PrairieCastMessage.decodeOrNull(json, """{"type":"queue_update","v":3,"queueUpdate":{"items":[]}}"""))
        assertIs<PrairieCastMessage.Ping>(PrairieCastMessage.decodeOrNull(json, """{"type":"ping","v":2}"""))
        // A known kind with a broken payload is still a protocol error.
        assertFailsWith<SerializationException> {
            PrairieCastMessage.decodeOrNull(json, """{"type":"state","v":2,"state":{"title":"x"}}""")
        }
        assertEquals(
            setOf(
                "hello", "handoff_offer", "handoff_challenge", "handoff_ready", "handoff_cancel",
                "launch", "control", "state", "error", "presence", "ping", "pong", "close",
            ),
            PrairieCastMessage.knownTypes,
        )
    }

    // Several controllers on one TV (prairie-apple PrairieControlMultiControllerTests).

    @Test
    fun phoneHelloCarriesFeaturesPassiveAndIdentity() {
        assertWireEquals(
            """
            {"type":"hello","v":2,"hello":{
                "role":"phone","deviceName":"Pixel 9","deviceId":"android-abc",
                "serverId":"srv-1","serverName":"Home","supportedVersions":[2],
                "serverIdentity":"deploy-1",
                "features":["close_reason","resume_token","replace_confirmation"],
                "passive":true
            }}
            """,
            PrairieCastMessage.Hello(
                PrairieCastHello(
                    role = PrairieCastPeerRole.Phone,
                    deviceName = "Pixel 9",
                    deviceId = "android-abc",
                    serverId = "srv-1",
                    serverName = "Home",
                    supportedVersions = PrairieCastProtocol.supportedVersions,
                    serverIdentity = "deploy-1",
                    features = PrairieCastFeature.phoneFeatures,
                    passive = true,
                ),
            ),
        )
    }

    @Test
    fun decodesAppleMultiControllerTvHello() {
        val hello = assertIs<PrairieCastMessage.Hello>(
            json.decodeFromString(
                PrairieCastMessage.serializer(),
                """{"type":"hello","v":2,"hello":{"role":"tv","deviceName":"Den","deviceId":"atv-1",""" +
                    """"serverId":"srv-1","serverName":"Home","supportedVersions":[1,2],"serverIdentity":"deploy-1",""" +
                    """"features":["multi_controller","close_reason","resume_token","replace_confirmation","presence"]}}""",
            ),
        ).hello
        assertTrue(hello.supports(PrairieCastFeature.MultiController))
        assertTrue(hello.supports(PrairieCastFeature.Presence))
        assertEquals("deploy-1", hello.serverIdentity)
        // An older TV lists nothing.
        assertFalse(PrairieCastHello(PrairieCastPeerRole.Tv, "Old", "atv-0", supportedVersions = listOf(2)).supports(PrairieCastFeature.MultiController))
    }

    @Test
    fun closeCarriesAReasonOnlyWhenGiven() {
        assertWireEquals(
            """{"type":"close","v":2,"close":{"reason":"replaced"}}""",
            PrairieCastMessage.Close.withReason(PrairieCastCloseReason.Replaced),
        )
        assertWireEquals("""{"type":"close","v":2}""", PrairieCastMessage.Close.withReason(null))
        fun reason(frame: String) = assertIs<PrairieCastMessage.Close>(PrairieCastMessage.decodeOrNull(json, frame)).reason
        assertEquals("tv_disconnected", reason("""{"type":"close","v":2,"close":{"reason":"tv_disconnected"}}"""))
        assertNull(reason("""{"type":"close","v":2}"""))
        // A malformed payload is no reason, never a broken frame (Apple decodes it with try?).
        assertNull(reason("""{"type":"close","v":2,"close":{"reason":5}}"""))
        assertNull(reason("""{"type":"close","v":2,"close":"replaced"}"""))
    }

    @Test
    fun presenceMatchesApple() {
        assertWireEquals(
            """{"type":"presence","v":2,"presence":{"passive":false}}""",
            PrairieCastMessage.Presence(PrairieCastPresence(passive = false)),
        )
    }

    @Test
    fun offerCarriesIdentityResumeTokenAndConsent() {
        assertWireEquals(
            """{"type":"handoff_offer","v":2,"handoffOffer":{"requestId":"req-2","serverId":"srv-1",""" +
                """"serverURL":"https://prairie.example","profileId":"p","profileName":"Alex","serverIdentity":"deploy-1",""" +
                """"resumeToken":"tok","replaceConfirmed":true,"replacingContentId":"oak"}}""",
            PrairieCastMessage.HandoffOffer(
                PrairieCastHandoffOffer(
                    requestId = "req-2",
                    serverId = "srv-1",
                    serverURL = "https://prairie.example",
                    profileId = "p",
                    profileName = "Alex",
                    serverIdentity = "deploy-1",
                    resumeToken = "tok",
                    replaceConfirmed = true,
                    replacingContentId = "oak",
                ),
            ),
        )
    }

    @Test
    fun offerCarriesTheServersOtherAddresses() {
        assertWireEquals(
            """{"type":"handoff_offer","v":2,"handoffOffer":{"requestId":"req-3","serverId":"srv-1",""" +
                """"serverURL":"https://prairie.example","profileId":"p","serverIdentity":"deploy-1",""" +
                """"serverEndpoints":[{"url":"https://prairie.example","kind":"public"},""" +
                """{"url":"https://prairie.tail.ts.net","kind":"provider","provider":"tailscale","displayName":"Tailscale"}]}}""",
            PrairieCastMessage.HandoffOffer(
                PrairieCastHandoffOffer(
                    requestId = "req-3",
                    serverId = "srv-1",
                    serverURL = "https://prairie.example",
                    profileId = "p",
                    serverIdentity = "deploy-1",
                    serverEndpoints = listOf(
                        PrairieCastServerEndpoint(url = "https://prairie.example", kind = "public"),
                        PrairieCastServerEndpoint(
                            url = "https://prairie.tail.ts.net",
                            kind = "provider",
                            provider = "tailscale",
                            displayName = "Tailscale",
                        ),
                    ),
                ),
            ),
        )
    }

    @Test
    fun decodesAppleReadyWithResumeTokenAndCancelWithNowPlaying() {
        val ready = assertIs<PrairieCastMessage.HandoffReady>(
            json.decodeFromString(
                PrairieCastMessage.serializer(),
                """{"type":"handoff_ready","v":2,"handoffReady":{"requestId":"r","serverId":"s","profileId":"p",""" +
                    """"sessionExpiresAt":"2099-01-01T00:00:00Z","reused":true,"resumeToken":"tok"}}""",
            ),
        ).handoffReady
        assertEquals("tok", ready.resumeToken)

        val cancel = assertIs<PrairieCastMessage.HandoffCancel>(
            json.decodeFromString(
                PrairieCastMessage.serializer(),
                """{"type":"handoff_cancel","v":2,"handoffCancel":{"requestId":"r","reason":"replace_confirmation_required",""" +
                    """"message":"Alex is watching Oak Street on this TV.","nowPlaying":{"contentId":"oak","title":"Oak Street",""" +
                    """"profileName":"Alex","controllerName":"Sam's iPhone"}}}""",
            ),
        ).handoffCancel
        assertEquals(PrairieCastHandoffCancelReason.ReplaceConfirmationRequired, cancel.reason)
        assertEquals(
            PrairieCastNowPlayingSummary(contentId = "oak", title = "Oak Street", profileName = "Alex", controllerName = "Sam's iPhone"),
            cancel.nowPlaying,
        )
    }

    @Test
    fun featureAndReasonNamesMatchApple() {
        assertEquals(
            listOf("multi_controller", "close_reason", "resume_token", "replace_confirmation", "presence"),
            PrairieCastFeature.receiverFeatures,
        )
        assertEquals(listOf("close_reason", "resume_token", "replace_confirmation"), PrairieCastFeature.phoneFeatures)
        assertEquals(
            listOf("replaced", "tv_disconnected", "too_many_controllers", "timeout", "unauthorized", "launch_timeout"),
            listOf(
                PrairieCastCloseReason.Replaced, PrairieCastCloseReason.TvDisconnected, PrairieCastCloseReason.TooManyControllers,
                PrairieCastCloseReason.Timeout, PrairieCastCloseReason.Unauthorized, PrairieCastCloseReason.LaunchTimeout,
            ),
        )
        assertEquals("superseded", PrairieCastHandoffCancelReason.Superseded)
        assertEquals("replace_confirmation_required", PrairieCastHandoffCancelReason.ReplaceConfirmationRequired)
        assertEquals("integration", PrairieCastClientKind.Integration)
    }
}
