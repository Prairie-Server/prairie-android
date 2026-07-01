package org.siloserver.silo.network.api

import org.siloserver.silo.network.SiloJson
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
import kotlinx.serialization.json.double
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class PlaybackApiTest {

    private class Captured {
        var method: HttpMethod? = null
        var path: String = ""
        var body: String = ""
    }

    private fun api(
        status: HttpStatusCode = HttpStatusCode.OK,
        responseBody: String = """{"audio_track_index":2,"play_method":"direct","stream_url":"http://x/s.m3u8"}""",
        captured: Captured = Captured(),
    ): Pair<PlaybackApi, Captured> {
        val client = HttpClient(
            MockEngine { request ->
                captured.method = request.method
                captured.path = request.url.encodedPath
                captured.body = request.body.toByteArray().decodeToString()
                respond(
                    content = responseBody,
                    status = status,
                    headers = headersOf(HttpHeaders.ContentType, "application/json"),
                )
            },
        ) {
            install(ContentNegotiation) { json(SiloJson) }
        }
        return PlaybackApi(client) to captured
    }

    @Test
    fun `changeAudio sends audio_track_index and position to server`() = runTest {
        val captured = Captured()
        val (playbackApi, _) = api(captured = captured)

        playbackApi.changeAudio(
            sessionId = "sess-abc",
            audioTrackIndex = 2,
            position = 123.0,
        )

        assertEquals(HttpMethod.Patch, captured.method)
        assertEquals("/api/v1/playback/sess-abc/audio", captured.path)

        val body = SiloJson.parseToJsonElement(captured.body).jsonObject
        assertEquals(2, body["audio_track_index"]!!.jsonPrimitive.int)
        assertEquals(123.0, body["position"]!!.jsonPrimitive.double)
    }

    @Test
    fun `changeAudio without position omits position field`() = runTest {
        val captured = Captured()
        val (playbackApi, _) = api(captured = captured)

        playbackApi.changeAudio(
            sessionId = "sess-xyz",
            audioTrackIndex = 1,
            position = null,
        )

        val body = SiloJson.parseToJsonElement(captured.body).jsonObject
        assertEquals(1, body["audio_track_index"]!!.jsonPrimitive.int)
        // When position is null it must not be serialized at all (server would default to 0)
        assertEquals(false, body.containsKey("position"))
    }

    @Test
    fun `changeAudio hits PATCH audio endpoint for given session`() = runTest {
        val captured = Captured()
        val (playbackApi, _) = api(captured = captured)

        playbackApi.changeAudio(sessionId = "s1", audioTrackIndex = 0, position = 0.0)

        assertEquals("/api/v1/playback/s1/audio", captured.path)
        assertEquals(HttpMethod.Patch, captured.method)
    }

    @Test
    fun `changeAudio decodes ChangeAudioResponse`() = runTest {
        val (playbackApi, _) = api(
            responseBody = """{"audio_track_index":3,"play_method":"transcode","stream_url":"http://srv/hls.m3u8"}""",
        )

        val result = playbackApi.changeAudio(sessionId = "s", audioTrackIndex = 3, position = 60.0)

        assertIs<org.siloserver.silo.network.ApiResult.Success<*>>(result)
        val data = (result as org.siloserver.silo.network.ApiResult.Success).data
        assertEquals(3, data.audioTrackIndex)
        assertEquals("http://srv/hls.m3u8", data.streamUrl)
    }
}
