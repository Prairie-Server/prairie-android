package org.siloserver.silo.common.player

import org.siloserver.silo.model.playback.PlayMethod
import org.siloserver.silo.model.playback.PlaybackSessionResponse
import org.siloserver.silo.network.ApiResult
import org.siloserver.silo.network.AuthScopeSnapshot
import org.siloserver.silo.network.SiloJson
import org.siloserver.silo.network.TokenManager
import org.siloserver.silo.network.api.PlaybackApi
import org.siloserver.silo.repository.PlaybackRepository
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PlaybackSessionManagerTranscodeFallbackTest {
    @Test
    fun remuxFallbackPreservesRemuxPlayMethodAndRequestsCopyCodecs() = runTest {
        val captured = CapturedRequest()
        val manager = manager(
            captured = captured,
            responseBody = """{"session_id":"remux-session","status":"ready","manifest_url":"/stream/remux/master","duration_seconds":120.0,"player_start_seconds":42.5}""",
        )

        val result = manager.startTranscodeFallback(
            session = session(playMethod = PlayMethod.REMUX),
            seekSeconds = 42.5,
            resolution = "1080p",
            mode = PlaybackSessionManager.TranscodeMode.REMUX,
        )

        assertTrue(result is ApiResult.Success)
        assertEquals(PlayMethod.REMUX, result.data.playMethod)
        assertEquals("/stream/remux/master", result.data.streamUrl)
        assertEquals(42.5, result.data.position)

        val body = SiloJson.parseToJsonElement(captured.body).jsonObject
        assertEquals("copy", body["target_codec_video"]!!.jsonPrimitive.content)
        assertEquals("copy", body["target_codec_audio"]!!.jsonPrimitive.content)
        assertEquals(0, body["target_bitrate_kbps"]!!.jsonPrimitive.int)
    }

    @Test
    fun fullFallbackStillReportsTranscodePlayMethod() = runTest {
        val manager = manager(
            captured = CapturedRequest(),
            responseBody = """{"session_id":"transcode-session","status":"ready","manifest_url":"/stream/transcode/master","duration_seconds":120.0,"player_start_seconds":12.0}""",
        )

        val result = manager.startTranscodeFallback(
            session = session(playMethod = PlayMethod.DIRECT),
            seekSeconds = 12.0,
            resolution = "1080p",
            mode = PlaybackSessionManager.TranscodeMode.FULL,
        )

        assertTrue(result is ApiResult.Success)
        assertEquals(PlayMethod.TRANSCODE, result.data.playMethod)
    }

    @Test
    fun fallbackRenewsPlaybackSessionWhenServerReportsSessionMissing() = runTest {
        val captured = CapturedRequest()
        val manager = manager(
            captured = captured,
            responses = ArrayDeque(
                listOf(
                    MockHttpResponse(
                        status = HttpStatusCode.NotFound,
                        body = """{"error":"playback_session_not_found","message":"Playback session not found"}""",
                    ),
                    MockHttpResponse(
                        status = HttpStatusCode.OK,
                        body = """{"session_id":"fresh-session","status":"ready","manifest_url":"/stream/transcode/fresh","duration_seconds":120.0,"player_start_seconds":33.0}""",
                    ),
                ),
            ),
        )

        val result = manager.startTranscodeFallbackRecoveringMissingSession(
            session = session(sessionId = "stale-session", playMethod = PlayMethod.DIRECT),
            seekSeconds = 33.0,
            resolution = "2160p",
            mode = PlaybackSessionManager.TranscodeMode.FULL,
            renewSession = {
                ApiResult.Success<PlaybackSessionResponse>(
                    session(sessionId = "fresh-session", playMethod = PlayMethod.DIRECT),
                )
            },
        )

        assertTrue(result is ApiResult.Success)
        assertEquals("fresh-session", result.data.sessionId)
        assertEquals("/stream/transcode/fresh", result.data.streamUrl)
        assertEquals(
            listOf("stale-session", "fresh-session"),
            captured.bodies.map { body ->
                SiloJson.parseToJsonElement(body).jsonObject["session_id"]!!.jsonPrimitive.content
            },
        )
    }

    private fun manager(
        captured: CapturedRequest,
        responseBody: String,
    ): PlaybackSessionManager =
        manager(
            captured = captured,
            responses = ArrayDeque(listOf(MockHttpResponse(HttpStatusCode.OK, responseBody))),
        )

    private fun manager(
        captured: CapturedRequest,
        responses: ArrayDeque<MockHttpResponse>,
    ): PlaybackSessionManager {
        val client = HttpClient(
            MockEngine { request ->
                val body = request.body.toByteArray().decodeToString()
                captured.body = body
                captured.bodies += body
                val response = responses.removeFirst()
                respond(
                    content = response.body,
                    status = response.status,
                    headers = headersOf(HttpHeaders.ContentType, "application/json"),
                )
            },
        ) {
            install(ContentNegotiation) { json(SiloJson) }
        }
        return PlaybackSessionManager(
            playbackRepository = PlaybackRepository(PlaybackApi(client)),
            tokenManager = NoOpTokenManager,
        )
    }

    private fun session(
        sessionId: String = "session-1",
        playMethod: PlayMethod,
    ): PlaybackSessionResponse =
        PlaybackSessionResponse(
            sessionId = sessionId,
            userId = 1,
            profileId = "profile-1",
            mediaFileId = 42,
            playMethod = playMethod,
            streamUrl = "/stream/session-1",
            durationSeconds = 120.0,
        )

    private class CapturedRequest {
        var body: String = ""
        val bodies = mutableListOf<String>()
    }

    private data class MockHttpResponse(
        val status: HttpStatusCode,
        val body: String,
    )
}

private object NoOpTokenManager : TokenManager {
    override val sessionExpired: SharedFlow<Unit> = MutableSharedFlow()
    override suspend fun getAccessToken(): String? = null
    override suspend fun getRefreshToken(): String? = null
    override suspend fun saveTokens(accessToken: String, refreshToken: String, expiresIn: Long) {}
    override suspend fun clearTokens() {}
    override suspend fun invalidateSession() {}
    override suspend fun getProfileId(): String? = null
    override suspend fun setProfileId(profileId: String?) {}
    override suspend fun getProfileToken(): String? = null
    override suspend fun setProfileToken(token: String?) {}
    override suspend fun getServerUrl(): String = ""
    override suspend fun setServerUrl(url: String) {}
    override suspend fun getCurrentServerId(): String? = null
    override suspend fun switchActiveServer(serverId: String?) {}
    override suspend fun signOutCurrentServer() {}
    override suspend fun snapshotCurrentScope(): AuthScopeSnapshot? = null
}
