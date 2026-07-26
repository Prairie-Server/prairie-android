package org.prairieserver.prairie.network.api

import org.prairieserver.prairie.model.auth.LoginRequest
import org.prairieserver.prairie.model.auth.RefreshRequest
import org.prairieserver.prairie.model.auth.SignupRequest
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

class AuthApiTest {
    private val loginJson = """
        {"access_token":"a","refresh_token":"r","expires_in":1,
         "user":{"id":1,"username":"u","email":"e","role":"user"}}
    """.trimIndent()

    private fun api(body: String = loginJson, status: HttpStatusCode = HttpStatusCode.OK): AuthApi {
        val client = HttpClient(
            MockEngine { respond(body, status, headersOf(HttpHeaders.ContentType, "application/json")) },
        ) { install(ContentNegotiation) { json(PrairieJson) } }
        return AuthApi(client)
    }

    @Test
    fun coversAuthRoutes() = runTest {
        assertIs<ApiResult.Success<*>>(api().login(LoginRequest("u", "p")))
        assertIs<ApiResult.Success<*>>(
            api("""{"access_token":"a","refresh_token":"r","expires_in":1}""")
                .refresh(RefreshRequest("r")),
        )
        assertIs<ApiResult.Success<*>>(
            api().signup(SignupRequest("u", "e", "p", "invite")),
        )
        assertIs<ApiResult.Success<*>>(api().setup("u", "e", "p"))
        assertIs<ApiResult.Success<*>>(api("""{"needs_setup":false}""").getSetupStatus())
        assertIs<ApiResult.Success<*>>(api("""{"needs_setup":true}""").getSetupStatus("https://srv"))
        assertIs<ApiResult.Success<*>>(api("""{"enabled":true}""").getSignupStatus())
        assertIs<ApiResult.Success<*>>(api("""{"enabled":false}""").getSignupStatus("https://srv"))
        assertIs<ApiResult.Success<*>>(
            api("""{"id":1,"username":"u","email":"e","role":"user"}""").getMe(),
        )
        assertIs<ApiResult.Success<*>>(api(status = HttpStatusCode.NoContent, body = "").logout())
        assertIs<ApiResult.Success<*>>(api("""{"sessions":[]}""").getSessions())
        assertIs<ApiResult.Success<*>>(api(status = HttpStatusCode.NoContent, body = "").revokeSession("s1"))
        assertIs<ApiResult.Success<*>>(api(status = HttpStatusCode.NoContent, body = "").deleteSession("s1"))
    }
}
