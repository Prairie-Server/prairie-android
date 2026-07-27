package org.prairieserver.prairie.network.api

import org.prairieserver.prairie.network.ApiResult
import org.prairieserver.prairie.network.AuthScopeSnapshot
import org.prairieserver.prairie.network.PrairieJson
import org.prairieserver.prairie.model.calendar.CalendarFilter
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
import kotlin.test.assertIs

class DeviceLoginCalendarMetadataApiCoverageTest {

    private fun client(body: String = "{}", status: HttpStatusCode = HttpStatusCode.OK): HttpClient =
        HttpClient(
            MockEngine {
                respond(body, status, headersOf(HttpHeaders.ContentType, "application/json"))
            },
        ) { install(ContentNegotiation) { json(PrairieJson) } }

    private val startBody = """
        {
          "device_code":"dc",
          "user_code":"UC",
          "match_code":"M",
          "verification_uri":"https://x/device",
          "verification_uri_complete":"https://x/device?c=UC",
          "expires_at":"2099-01-01T00:00:00Z",
          "expires_in":600,
          "interval":5,
          "device_name":"Shield",
          "device_platform":"androidtv"
        }
    """.trimIndent()

    private val scope = AuthScopeSnapshot(
        serverId = "s1",
        serverUrl = "https://active.example",
        profileId = "p1",
        profileToken = "pt",
    )

    @Test
    fun coversDefaultDeviceLoginActiveRoutes() = runTest {
        val api = DefaultDeviceLoginApi(client(body = startBody))
        assertIs<ApiResult.Success<*>>(api.startDeviceLogin("Shield", "androidtv"))
        assertIs<ApiResult.Success<*>>(
            DefaultDeviceLoginApi(client(body = """{"status":"pending"}""")).pollDeviceLogin("dc"),
        )
        assertIs<ApiResult.Success<*>>(
            DefaultDeviceLoginApi(
                client(
                    body = """{
                      "status":"pending",
                      "user_code":"UC",
                      "match_code":"M",
                      "device_name":"Shield",
                      "device_platform":"androidtv"
                    }""",
                ),
            ).lookupDeviceLogin(token = "t", code = "UC"),
        )
        assertIs<ApiResult.Success<*>>(
            DefaultDeviceLoginApi(client(body = """{"status":"approved"}"""))
                .approveDeviceLogin(token = "t", code = "UC"),
        )
        assertIs<ApiResult.Success<*>>(
            DefaultDeviceLoginApi(client(body = """{"status":"denied"}"""))
                .denyDeviceLogin(token = "t", code = "UC"),
        )
        assertIs<ApiResult.Success<*>>(
            DefaultDeviceLoginApi(
                client(
                    body = """{
                      "status":"pending",
                      "user_code":"UC",
                      "match_code":"M",
                      "device_name":"Shield",
                      "device_platform":"androidtv"
                    }""",
                ),
            ).lookupDeviceLoginForScope(scope, "UC"),
        )
        assertIs<ApiResult.Success<*>>(
            DefaultDeviceLoginApi(client(body = """{"status":"approved"}"""))
                .approveDeviceLoginForScope(scope, "UC"),
        )
        assertIs<ApiResult.Success<*>>(
            DefaultDeviceLoginApi(client(body = """{"status":"denied"}"""))
                .denyDeviceLoginForScope(scope, "UC"),
        )
        assertIs<ApiResult.Success<*>>(
            DefaultDeviceLoginApi(client(status = HttpStatusCode.NoContent, body = ""))
                .endRemotePlayback(scope),
        )
    }

    @Test
    fun coversCalendarAndMetadataAiRoutes() = runTest {
        assertIs<ApiResult.Success<*>>(
            DefaultCalendarApi(client(body = """{"days":[]}"""))
                .getCalendar(
                    start = "2026-01-01",
                    end = "2026-01-07",
                    filter = CalendarFilter.All,
                    libraryId = 1,
                    timezone = "UTC",
                ),
        )
        assertIs<ApiResult.Success<*>>(
            DefaultCalendarApi(client(body = """{"days":[]}"""))
                .getCalendar(start = "2026-01-01", end = "2026-01-07"),
        )
        assertIs<ApiResult.Success<*>>(
            DefaultMetadataAiApi(client(body = """{"enabled":false,"on_view":"off"}""")).status(),
        )
        assertIs<ApiResult.Success<*>>(
            DefaultMetadataAiApi(client(status = HttpStatusCode.Accepted, body = ""))
                .translateDescription("c1", "es"),
        )
    }
}
