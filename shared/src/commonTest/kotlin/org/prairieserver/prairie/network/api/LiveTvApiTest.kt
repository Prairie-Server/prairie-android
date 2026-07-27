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
        body: String = """{"channels":[]}""",
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
            body = """{"channels":[{"id":"ch-1","number":"7.1","name":"KGO","enabled":true}]}""",
        ).channels(tunerId = "tuner-1")

        assertEquals(HttpMethod.Get, captured.method)
        assertEquals("/api/v1/livetv/channels", captured.path)
        assertTrue(captured.query.contains("tuner_id=tuner-1"))
        val success = assertIs<ApiResult.Success<*>>(result)
        val response = success.data as org.prairieserver.prairie.model.livetv.LiveTvChannelsResponse
        assertEquals(1, response.channels.size)
        assertEquals("ch-1", response.channels[0].id)
    }

    @Test
    fun guideJoinsChannelIdsAndPassesWindow() = runTest {
        val captured = Captured()
        api(captured, body = """{"programs":[],"start":"","end":""}""").guide(
            channelIds = listOf("a", "b"),
            start = "2026-07-25T00:00:00Z",
            end = "2026-07-25T12:00:00Z",
        )

        assertEquals(HttpMethod.Get, captured.method)
        assertEquals("/api/v1/livetv/guide", captured.path)
        assertTrue(captured.query.contains("channels=a%2Cb") || captured.query.contains("channels=a,b"))
        assertTrue(captured.query.contains("start=2026-07-25T00%3A00%3A00Z") || captured.query.contains("start=2026-07-25T00:00:00Z"))
    }

    @Test
    fun startSessionPostsToChannelScopedPath() = runTest {
        val captured = Captured()
        val result = api(
            captured,
            body = """{"session_id":"s1","playback_ticket":"t1","hls_url":"/api/v1/livetv/live-hls/t1/index.m3u8","transport":"hls"}""",
            status = HttpStatusCode.Created,
        ).startSession("ch-9")

        assertEquals(HttpMethod.Post, captured.method)
        assertEquals("/api/v1/livetv/channels/ch-9/session", captured.path)
        val success = assertIs<ApiResult.Success<*>>(result)
        val session = success.data as org.prairieserver.prairie.model.livetv.LiveTvSessionStartResponse
        assertEquals("s1", session.sessionId)
        assertEquals("hls", session.transport)
        assertTrue(session.isHls)
        assertEquals("/api/v1/livetv/live-hls/t1/index.m3u8", session.playableUrl)
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
    fun releaseSessionDeletesSession() = runTest {
        val captured = Captured()
        api(
            captured,
            body = """{"id":"s1","channel_id":"ch-9","status":"released"}""",
        ).releaseSession("s1")

        assertEquals(HttpMethod.Delete, captured.method)
        assertEquals("/api/v1/livetv/sessions/s1", captured.path)
    }

    @Test
    fun programFetchesCanonicalDetailPath() = runTest {
        val captured = Captured()
        api(
            captured,
            body = """{"id":"p1","channel_id":"ch-1","title":"News"}""",
        ).program("p1")

        assertEquals(HttpMethod.Get, captured.method)
        assertEquals("/api/v1/livetv/programs/p1", captured.path)
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
        assertEquals("/api/v1/livetv/recordings", captured.path)
        assertTrue(captured.body.contains("program_id"))
        val success = assertIs<ApiResult.Success<*>>(result)
        val recording = success.data as org.prairieserver.prairie.model.livetv.LiveTvRecording
        assertEquals("r1", recording.id)
    }
}
