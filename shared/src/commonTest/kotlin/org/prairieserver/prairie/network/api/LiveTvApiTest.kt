package org.prairieserver.prairie.network.api

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import org.prairieserver.prairie.network.ApiResult
import org.prairieserver.prairie.network.PrairieJson
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class LiveTvApiTest {
    private class Captured {
        var method: HttpMethod? = null
        var path: String = ""
        var query: String = ""
        var body: String = ""
    }

    private fun api(
        captured: Captured,
        body: String = """{"items":[]}""",
        status: HttpStatusCode = HttpStatusCode.OK,
    ): LiveTvApi {
        val client = HttpClient(
            MockEngine { request ->
                captured.method = request.method
                captured.path = request.url.encodedPath
                captured.query = request.url.encodedQuery
                captured.body = request.body.toByteArray().decodeToString()
                respond(
                    content = body,
                    status = status,
                    headers = headersOf(HttpHeaders.ContentType, "application/json"),
                )
            },
        ) {
            install(ContentNegotiation) { json(PrairieJson) }
        }
        return DefaultLiveTvApi(client)
    }

    @Test
    fun channelsUsesCanonicalEndpointAndOptionalTunerFilter() = runTest {
        val captured = Captured()
        val result = api(
            captured,
            body = """{"items":[{"id":"ch-1","number":"7.1","name":"KGO","enabled":true}]}""",
        ).channels(tunerId = "tuner-1")

        assertEquals(HttpMethod.Get, captured.method)
        assertEquals("/api/v2/livetv/channels", captured.path)
        assertTrue(captured.query.contains("tuner_id=tuner-1"))
        val success = assertIs<ApiResult.Success<*>>(result)
        val response = success.data as org.prairieserver.prairie.model.livetv.LiveTvChannelsResponse
        assertEquals(1, response.channels.size)
        assertEquals("ch-1", response.channels[0].id)
    }

    @Test
    fun guideRepeatsChannelIdAndParsesItems() = runTest {
        val captured = Captured()
        val result = api(captured, body = """{"items":[{"id":"p1","channel_id":"a","title":"News"}],"start":"","end":""}""").guide(
            channelIds = listOf("a", "b"),
            start = "2026-07-25T00:00:00Z",
            end = "2026-07-25T12:00:00Z",
        )

        assertEquals(HttpMethod.Get, captured.method)
        assertEquals("/api/v2/livetv/guide", captured.path)
        assertTrue(captured.query.contains("channel_id=a&channel_id=b"))
        assertTrue(!captured.query.contains("channels="))
        val guide = assertIs<ApiResult.Success<*>>(result).data as org.prairieserver.prairie.model.livetv.LiveTvGuideResponse
        assertEquals("p1", guide.programs.single().id)
        assertTrue(captured.query.contains("start=2026-07-25T00%3A00%3A00Z") || captured.query.contains("start=2026-07-25T00:00:00Z"))
    }

    @Test
    fun startSessionPostsToChannelScopedPath() = runTest {
        val captured = Captured()
        val result = api(
            captured,
            body = """{"session_id":"s1","channel_id":"ch-9","playback_ticket":"t1","stream_url":"/api/v1/livetv/live-hls/t1/index.m3u8?st=x","transport":"hls","status":"active","created_at":"2026-10-12T00:00:00Z","stale_after_seconds":90}""",
            status = HttpStatusCode.Created,
        ).startSession("ch-9")

        assertEquals(HttpMethod.Post, captured.method)
        assertEquals("/api/v2/livetv/channels/ch-9/sessions", captured.path)
        val success = assertIs<ApiResult.Success<*>>(result)
        val session = success.data as org.prairieserver.prairie.model.livetv.LiveTvSessionStartResponse
        assertEquals("s1", session.sessionId)
        assertEquals("hls", session.transport)
        assertTrue(session.isHls)
        assertEquals("/api/v1/livetv/live-hls/t1/index.m3u8?st=x", session.playableUrl)
        assertEquals(90, session.staleAfterSeconds)
    }

    @Test
    fun startSessionMpegtsPrefersStreamUrl() = runTest {
        val captured = Captured()
        val result = api(
            captured,
            body = """{"session_id":"s2","playback_ticket":"s2","stream_url":"/api/v1/livetv/sessions/s2/stream","transport":"mpegts"}""",
            status = HttpStatusCode.Created,
        ).startSession("ch-1")

        val success = assertIs<ApiResult.Success<*>>(result)
        val session = success.data as org.prairieserver.prairie.model.livetv.LiveTvSessionStartResponse
        assertEquals("mpegts", session.transport)
        assertTrue(!session.isHls)
        assertEquals("/api/v1/livetv/sessions/s2/stream", session.playableUrl)
    }

    @Test
    fun heartbeatSessionPostsHeartbeat() = runTest {
        val captured = Captured()
        val result = api(captured, body = "", status = HttpStatusCode.NoContent).heartbeatSession("s1")

        assertEquals(HttpMethod.Post, captured.method)
        assertEquals("/api/v2/livetv/sessions/s1/heartbeat", captured.path)
        assertIs<ApiResult.Success<Unit>>(result)
    }

    @Test
    fun releaseSessionDeletesSession() = runTest {
        val captured = Captured()
        val result = api(captured, body = "", status = HttpStatusCode.NoContent).releaseSession("s1")

        assertEquals(HttpMethod.Delete, captured.method)
        assertEquals("/api/v2/livetv/sessions/s1", captured.path)
        assertIs<ApiResult.Success<Unit>>(result)
    }

    @Test
    fun programFetchesCanonicalDetailPath() = runTest {
        val captured = Captured()
        api(
            captured,
            body = """{"id":"p1","channel_id":"ch-1","title":"News"}""",
        ).program("p1")

        assertEquals(HttpMethod.Get, captured.method)
        assertEquals("/api/v2/livetv/programs/p1", captured.path)
    }

    @Test
    fun scheduleRecordingPostsProgramId() = runTest {
        val captured = Captured()
        val result = api(
            captured,
            body = """{"id":"r1","channel_id":"ch-1","status":"scheduled","start":"","stop":"","title":"News"}""",
            status = HttpStatusCode.Created,
        ).scheduleRecording(
            org.prairieserver.prairie.model.livetv.LiveTvScheduleRecordingRequest(programId = "p1"),
        )

        assertEquals(HttpMethod.Post, captured.method)
        assertEquals("/api/v2/livetv/recordings", captured.path)
        assertTrue(captured.body.contains("program_id"))
        val success = assertIs<ApiResult.Success<*>>(result)
        val recording = success.data as org.prairieserver.prairie.model.livetv.LiveTvRecording
        assertEquals("r1", recording.id)
    }

    @Test
    fun recordingsParseItemsEnvelope() = runTest {
        val captured = Captured()
        val result = api(
            captured,
            body = """{"items":[{"id":"r1","channel_id":"ch-1","profile_id":"pr-1","status":"scheduled","title":"News","start":null}]}""",
        ).recordings(status = "scheduled")

        assertEquals("/api/v2/livetv/recordings", captured.path)
        assertTrue(captured.query.contains("status=scheduled"))
        val response = assertIs<ApiResult.Success<*>>(result).data as org.prairieserver.prairie.model.livetv.LiveTvRecordingsResponse
        assertEquals("r1", response.recordings.single().id)
        assertEquals("pr-1", response.recordings.single().profileId)
    }

    @Test
    fun capabilityNotConfiguredSurfacesAsProblemCode() = runTest {
        val result = api(
            Captured(),
            body = """{"type":"https://prairieserver.org/docs/api/v2/problems/capability_not_configured","title":"Conflict","status":409,"detail":"Live TV is not configured."}""",
            status = HttpStatusCode.Conflict,
        ).channels()

        val error = assertIs<ApiResult.Error>(result)
        assertEquals(409, error.code)
        assertEquals("capability_not_configured", error.error)
    }
}
