package org.prairieserver.prairie.network.api

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import org.prairieserver.prairie.network.ApiResult
import org.prairieserver.prairie.network.PrairieJson
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class BrandingApiTest {
    @Test
    fun `getBranding decodes native server name`() = runTest {
        val api = BrandingApi(
            HttpClient(
                MockEngine {
                    assertEquals("/api/v2/theme/branding", it.url.encodedPath)
                    respond(
                        content = """{"server_name":"Home Silo","login_subtitle":"Welcome"}""",
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "application/json"),
                    )
                },
            ) {
                install(ContentNegotiation) { json(PrairieJson) }
            },
        )

        val result = assertIs<ApiResult.Success<BrandingStatus>>(api.getBranding())

        assertEquals("Home Silo", result.data.serverName)
    }
    @Test
    fun `missing and unavailable v2 branding never fall back`() = runTest {
        for (status in listOf(HttpStatusCode.NotFound, HttpStatusCode.ServiceUnavailable)) {
            val paths = mutableListOf<String>()
            val client = HttpClient(MockEngine {
                paths += it.url.encodedPath
                if (paths.size == 1) respond("{}", status, headersOf(HttpHeaders.ContentType, "application/problem+json"))
                else respond("""{"server_name":"Legacy"}""", HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
            }) { install(ContentNegotiation) { json(PrairieJson) } }
            val result = BrandingApi(client).getBranding()
            assertIs<ApiResult.Error>(result)
            assertEquals(listOf("/api/v2/theme/branding"), paths)
            client.close()
        }
    }

}
