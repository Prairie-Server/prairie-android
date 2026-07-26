package org.prairieserver.prairie.repository

import org.prairieserver.prairie.model.playback.ClientCodecCapabilities
import org.prairieserver.prairie.model.playback.ClientPlaybackContext
import org.prairieserver.prairie.model.playback.PlayMethod
import org.prairieserver.prairie.model.playback.PlaybackFailureV3
import org.prairieserver.prairie.model.playback.PlaybackReplanRequestV3
import org.prairieserver.prairie.model.playback.PlaybackRouteEventV3
import org.prairieserver.prairie.model.playback.PlaybackStartRequestV3
import org.prairieserver.prairie.model.playback.SelectedPlaybackTracksV3
import org.prairieserver.prairie.model.playback.SubtitleFidelityPreference
import org.prairieserver.prairie.model.playback.TranscodeStartRequest
import org.prairieserver.prairie.network.ApiResult
import org.prairieserver.prairie.network.PrairieJson
import org.prairieserver.prairie.network.api.PlaybackApi
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.assertTrue

class PlaybackRepositoryTest {
    private fun context() = ClientPlaybackContext(formFactor = "phone", appVersion = "test")

    private fun repo(body: String, status: HttpStatusCode = HttpStatusCode.OK): PlaybackRepository {
        val client = HttpClient(
            MockEngine { respond(body, status, headersOf(HttpHeaders.ContentType, "application/json")) },
        ) { install(ContentNegotiation) { json(PrairieJson) } }
        return PlaybackRepository(PlaybackApi(client))
    }

    private val sessionJson = """
        {"session_id":"s1","user_id":1,"media_file_id":9,"play_method":"direct","stream_url":"https://x/s"}
    """.trimIndent()

    @Test
    fun startPlaybackMapsCapabilitiesAndPlayMethod() = runTest {
        var body = ""
        val client = HttpClient(
            MockEngine { request ->
                body = request.body.toByteArray().decodeToString()
                respond(sessionJson, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
            },
        ) { install(ContentNegotiation) { json(PrairieJson) } }
        val result = PlaybackRepository(PlaybackApi(client)).startPlayback(
            fileId = 9,
            profileId = "p1",
            qualityPreference = "1080p",
            audioTrackIndex = 1,
            subtitleTrackIndex = 2,
            startPosition = 12.5,
            capabilities = ClientCodecCapabilities(codecsVideo = listOf("hevc"), hdr = true),
            clientPlaybackContext = context(),
            preserveDirectAudioSelection = true,
            playMethod = PlayMethod.TRANSCODE,
            disableProgressPersistence = true,
            seekableStreamsOnly = true,
        )
        assertIs<ApiResult.Success<*>>(result)
        assertTrue("transcode" in body)
        assertTrue("hevc" in body)
        assertTrue("1080p" in body)

        // Exercise remux + direct wire values too.
        PlaybackRepository(PlaybackApi(client)).startPlayback(
            fileId = 9,
            profileId = "p1",
            capabilities = ClientCodecCapabilities(),
            playMethod = PlayMethod.REMUX,
        )
        assertTrue("remux" in body)
        PlaybackRepository(PlaybackApi(client)).startPlayback(
            fileId = 9,
            profileId = "p1",
            capabilities = ClientCodecCapabilities(),
            playMethod = PlayMethod.DIRECT,
        )
        assertTrue("direct" in body)
    }

    @Test
    fun coversV3ProgressStopAndTranscode() = runTest {
        val start = PlaybackStartRequestV3(
            fileId = 1,
            profileId = "p",
            playbackAttemptId = "a",
            subtitleFidelityPreference = SubtitleFidelityPreference.PRESERVE,
            outputRouteGeneration = 1,
            capabilities = ClientCodecCapabilities(),
            clientPlaybackContext = context(),
        )
        // Empty/minimal bodies exercise the call path; result type is still Success/Error.
        assertIs<ApiResult.Success<*>>(repo("{}").startPlaybackV3(start))
        assertIs<ApiResult.Success<*>>(
            repo("{}").replanPlaybackV3(
                "s1",
                PlaybackReplanRequestV3(
                    playbackAttemptId = "a",
                    replanRequestId = "r",
                    failedPlanId = "fp",
                    planAttemptId = "pa",
                    planAttemptKey = "pak",
                    attemptedPlanKeys = listOf("pak"),
                    attemptCount = 1,
                    positionSeconds = 1.0,
                    outputRouteGeneration = 1,
                    selectedTracks = SelectedPlaybackTracksV3(),
                    failure = PlaybackFailureV3("decode", message = "boom"),
                    capabilities = ClientCodecCapabilities(),
                    clientPlaybackContext = context(),
                ),
            ),
        )
        assertIs<ApiResult.Success<*>>(
            repo("", HttpStatusCode.NoContent).reportRouteEventV3(
                PlaybackRouteEventV3(
                    playbackAttemptId = "a",
                    event = "started",
                    outputRouteGeneration = 1,
                ),
            ),
        )
        assertIs<ApiResult.Success<*>>(repo("", HttpStatusCode.NoContent).updateProgress("s1", 1.0, true))
        assertIs<ApiResult.Success<*>>(repo("", HttpStatusCode.NoContent).stopPlayback("s1"))
        assertIs<ApiResult.Success<*>>(
            repo(
                """{"session_id":"t1","status":"ok","manifest_url":"https://x/t"}""",
            ).startTranscode(
                TranscodeStartRequest(
                    sessionId = "s1",
                    seekSeconds = 10.0,
                    targetBitrateKbps = 4000,
                    segmentDuration = 4,
                    subtitleBurnIn = false,
                ),
            ),
        )
    }
}
