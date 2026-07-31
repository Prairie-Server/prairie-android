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
import org.prairieserver.prairie.model.onboarding.OnboardingProgressRequest
import org.prairieserver.prairie.network.ApiResult
import org.prairieserver.prairie.network.PrairieJson
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class OnboardingApiTest {

    private class Captured {
        var method: HttpMethod? = null
        var path: String = ""
        var query: Map<String, String?> = emptyMap()
        var body: String = ""
    }

    private fun api(
        status: HttpStatusCode = HttpStatusCode.OK,
        responseBody: String = "{}",
        captured: Captured = Captured(),
    ): Pair<OnboardingApi, Captured> {
        val client = HttpClient(
            MockEngine { request ->
                captured.method = request.method
                captured.path = request.url.encodedPath
                captured.query = request.url.parameters.names()
                    .associateWith { request.url.parameters[it] }
                captured.body = request.body.toByteArray().decodeToString()
                respond(
                    content = responseBody,
                    status = status,
                    headers = headersOf(HttpHeaders.ContentType, "application/json"),
                )
            },
        ) {
            install(ContentNegotiation) { json(PrairieJson) }
        }
        return OnboardingApi(client) to captured
    }

    @Test
    fun getFlowPassesSurfaceAndParsesSteps() = runTest {
        val (api, captured) = api(
            responseBody = """
                {"version":1,"tour_id":"phone-welcome","steps":[
                  {"id":"intro","kind":"copy","title":"Hi","body":"Welcome"},
                  {"id":"audio","kind":"setting",
                   "setting":{"target":"setting","key":"playback.audio_language",
                              "control":"picker","options":[{"value":"en","label":"English"}]}}
                ]}
            """.trimIndent(),
        )

        val result = api.getFlow("phone")

        assertEquals(HttpMethod.Get, captured.method)
        assertEquals("/api/v1/onboarding/flow", captured.path)
        assertEquals("phone", captured.query["surface"])
        assertIs<ApiResult.Success<*>>(result)
        val flow = (result as ApiResult.Success).data
        assertEquals("phone-welcome", flow.tourId)
        assertEquals(2, flow.steps.size)
        assertEquals("playback.audio_language", flow.steps[1].setting?.key)
    }

    @Test
    fun getStateParsesProgressFlags() = runTest {
        val (api, captured) = api(
            responseBody = """
                {"tour_id":"tv-welcome","last_step":"intro","done":false}
            """.trimIndent(),
        )

        val result = api.getState()

        assertEquals("/api/v1/onboarding/state", captured.path)
        assertIs<ApiResult.Success<*>>(result)
        val state = (result as ApiResult.Success).data
        assertEquals("tv-welcome", state.tourId)
        assertEquals("intro", state.lastStep)
        assertEquals(false, state.done)
    }

    @Test
    fun postProgressSendsJsonBody() = runTest {
        val (api, captured) = api(status = HttpStatusCode.NoContent, responseBody = "")

        val result = api.postProgress(
            OnboardingProgressRequest(
                tourId = "phone-welcome",
                lastStep = "audio",
                completed = true,
            ),
        )

        assertEquals(HttpMethod.Post, captured.method)
        assertEquals("/api/v1/onboarding/progress", captured.path)
        assertTrue(captured.body.contains("\"tour_id\":\"phone-welcome\""))
        assertTrue(captured.body.contains("\"last_step\":\"audio\""))
        assertTrue(captured.body.contains("\"completed\":true"))
        assertIs<ApiResult.Success<*>>(result)
    }
}
