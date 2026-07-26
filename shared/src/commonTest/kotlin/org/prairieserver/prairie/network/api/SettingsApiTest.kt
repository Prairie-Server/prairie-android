package org.prairieserver.prairie.network.api

import org.prairieserver.prairie.model.settings.SubtitleAppearance
import org.prairieserver.prairie.network.ApiResult
import org.prairieserver.prairie.network.PrairieJson
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
import kotlin.test.assertTrue

class SettingsApiTest {
    private class Cap {
        var path = ""
        var profileHeader: String? = null
    }

    private fun api(
        body: String = "{}",
        status: HttpStatusCode = HttpStatusCode.OK,
        cap: Cap = Cap(),
    ): Pair<SettingsApi, Cap> {
        val client = HttpClient(
            MockEngine { req ->
                cap.path = req.url.encodedPath
                cap.profileHeader = req.headers["X-Profile-Id"]
                respond(body, status, headersOf(HttpHeaders.ContentType, "application/json"))
            },
        ) { install(ContentNegotiation) { json(PrairieJson) } }
        return SettingsApi(client) to cap
    }

    @Test
    fun coversSettingsRoutes() = runTest {
        assertIs<ApiResult.Success<*>>(api(body = """{"settings":[]}""").first.getSettings())
        assertIs<ApiResult.Success<*>>(api(body = """{"enabled":true}""").first.overlayConfig())
        assertIs<ApiResult.Success<*>>(api(body = """{"key":"k","value":"v"}""").first.getSetting("k"))
        assertIs<ApiResult.Success<*>>(api(status = HttpStatusCode.NoContent, body = "").first.setSetting("k", "v"))
        assertIs<ApiResult.Success<*>>(api(status = HttpStatusCode.NoContent, body = "").first.deleteSetting("k"))
        assertIs<ApiResult.Success<*>>(api(body = """{"key":"k","value":"v"}""").first.getDeviceSetting("k"))
        val (_, cap) = api(status = HttpStatusCode.NoContent, body = "")
        assertIs<ApiResult.Success<*>>(
            api(status = HttpStatusCode.NoContent, body = "", cap = cap)
                .first.setDeviceSetting("k", "v", profileId = "p1"),
        )
        assertTrue(cap.profileHeader == "p1")
        assertIs<ApiResult.Success<*>>(api(status = HttpStatusCode.NoContent, body = "").first.deleteDeviceSetting("k"))
        assertIs<ApiResult.Success<*>>(
            api(body = """{"settings":[{"key":"a","effective_value":"1","source":"default"}]}""")
                .first.getEffectiveSettings(listOf("a", "b")),
        )
        assertIs<ApiResult.Success<*>>(
            api(body = """{"key":"subtitle_appearance","global_value":"{}","effective_value":"{}"}""")
                .first.getEffectiveSubtitleAppearance(),
        )
        assertIs<ApiResult.Success<*>>(
            api(status = HttpStatusCode.NoContent, body = "").first.setDeviceSubtitleAppearanceOverride(
                SubtitleAppearance(),
                profileId = "p1",
            ),
        )
        assertIs<ApiResult.Success<*>>(
            api(status = HttpStatusCode.NoContent, body = "").first.deleteDeviceSubtitleAppearanceOverride(),
        )
    }
}
