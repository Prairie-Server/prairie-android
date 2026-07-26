package org.prairieserver.prairie.network.api

import org.prairieserver.prairie.network.ApiResult
import org.prairieserver.prairie.network.PrairieJson
import org.prairieserver.prairie.network.SkipPrairieAuthAttributeKey
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class HealthApiTest {

    @Test
    fun `checkHealth decodes health payload`() = runTest {
        val api = HealthApi(
            client(
                status = HttpStatusCode.OK,
                body = """{"status":"ok","server_name":"Prairie","server_id":"srv-1"}""",
            ),
        )

        val result = api.checkHealth()

        val success = assertIs<ApiResult.Success<HealthStatus>>(result)
        assertEquals("ok", success.data.status)
        assertEquals("Prairie", success.data.serverName)
        assertEquals("srv-1", success.data.serverId)
    }

    @Test
    fun `checkHealth treats html success body as network error`() = runTest {
        val api = HealthApi(
            client(
                status = HttpStatusCode.OK,
                body = "<html>proxy splash</html>",
                contentType = "text/html",
            ),
        )

        val result = api.checkHealth()

        assertIs<ApiResult.NetworkError>(result)
    }

    @Test
    fun `checkHealth returns error for gateway failure status`() = runTest {
        val api = HealthApi(
            client(
                status = HttpStatusCode.ServiceUnavailable,
                body = """{"error":"unavailable","message":"origin down"}""",
            ),
        )

        val result = api.checkHealth()

        val error = assertIs<ApiResult.Error>(result)
        assertEquals(503, error.code)
    }

    @Test
    fun `checkHealth absolute url skips auth and hits candidate host`() = runTest {
        var sawSkipAuth = false
        var requestUrl = ""
        val api = HealthApi(
            HttpClient(
                MockEngine { request ->
                    requestUrl = request.url.toString()
                    sawSkipAuth = request.attributes.getOrNull(SkipPrairieAuthAttributeKey) == true
                    respond(
                        content = """{"status":"ok","server_name":"LAN","server_id":"lan-1"}""",
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "application/json"),
                    )
                },
            ) {
                install(ContentNegotiation) { json(PrairieJson) }
            },
        )

        val result = api.checkHealth("https://candidate.example/")

        val success = assertIs<ApiResult.Success<HealthStatus>>(result)
        assertEquals("LAN", success.data.serverName)
        assertTrue(sawSkipAuth)
        assertTrue(requestUrl.contains("https://candidate.example/api/v1/health"))
    }

    private fun client(
        status: HttpStatusCode,
        body: String,
        contentType: String = "application/json",
    ): HttpClient =
        HttpClient(
            MockEngine {
                respond(
                    content = body,
                    status = status,
                    headers = headersOf(HttpHeaders.ContentType, contentType),
                )
            },
        ) {
            install(ContentNegotiation) { json(PrairieJson) }
        }
}
