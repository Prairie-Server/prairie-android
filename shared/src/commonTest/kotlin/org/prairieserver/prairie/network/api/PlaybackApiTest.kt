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
import org.prairieserver.prairie.playback.QualityLadderResponse
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class PlaybackApiTest {
    private class Captured {
        var method: HttpMethod? = null
        var path: String = ""
        var query: Map<String, String?> = emptyMap()
        var body: String = ""
    }

    private fun api(
        captured: Captured,
        responseBody: String = "{}",
    ): PlaybackApi {
        val client = HttpClient(
            MockEngine { request ->
                captured.method = request.method
                captured.path = request.url.encodedPath
                captured.query = request.url.parameters.names()
                    .associateWith { request.url.parameters[it] }
                captured.body = request.body.toByteArray().decodeToString()
                respond(
                    content = responseBody,
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json"),
                )
            },
        ) {
            install(ContentNegotiation) { json(PrairieJson) }
        }
        return PlaybackApi(client)
    }

    @Test
    fun `quality ladder omits source_height when unset or non-positive`() = runTest {
        val captured = Captured()
        val result = api(
            captured,
            responseBody = """
                {"rungs":[{"id":"1080p","label":"1080p","resolution":"1080p","height":1080,"bitrate_kbps":6000}],
                 "modes":["auto","original"],"source_height":1080}
            """.trimIndent(),
        ).getQualityLadder()

        assertEquals(HttpMethod.Get, captured.method)
        assertEquals("/api/v1/playback/quality-ladder", captured.path)
        assertTrue(captured.query.isEmpty())
        val success = assertIs<ApiResult.Success<QualityLadderResponse>>(result)
        assertEquals(1, success.data.rungs.size)
        assertEquals("1080p", success.data.rungs.single().id)
        assertEquals(1080, success.data.sourceHeight)

        val again = Captured()
        api(again).getQualityLadder(sourceHeight = 0)
        assertTrue(again.query.isEmpty())
    }

    @Test
    fun `quality ladder includes source_height query when positive`() = runTest {
        val captured = Captured()
        api(captured).getQualityLadder(sourceHeight = 2160)

        assertEquals(HttpMethod.Get, captured.method)
        assertEquals("/api/v1/playback/quality-ladder", captured.path)
        assertEquals("2160", captured.query["source_height"])
    }
}
