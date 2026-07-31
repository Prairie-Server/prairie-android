package org.prairieserver.prairie.network.api

import org.prairieserver.prairie.model.auth.LoginRequest
import org.prairieserver.prairie.model.auth.RefreshRequest
import org.prairieserver.prairie.model.auth.SignupRequest
import org.prairieserver.prairie.network.ApiResult
import org.prairieserver.prairie.network.PrairieJson
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
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class AuthApiTest {
    private val loginJson = """
        {"access_token":"a","refresh_token":"r","expires_in":1,
         "user":{"id":1,"username":"u","email":"e","role":"user"}}
    """.trimIndent()

    private class Captured {
        var method: HttpMethod? = null
        var url: String = ""
        var body: String = ""
    }

    private fun api(
        body: String = loginJson,
        status: HttpStatusCode = HttpStatusCode.OK,
        captured: Captured? = null,
    ): AuthApi {
        val client = HttpClient(
            MockEngine { request ->
                captured?.method = request.method
                captured?.url = request.url.toString()
                captured?.body = request.body.toByteArray().decodeToString()
                respond(body, status, headersOf(HttpHeaders.ContentType, "application/json"))
            },
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

    @Test
    fun lookupInvitationPathEncodesTokenAndParsesClaimPreview() = runTest {
        val captured = Captured()
        val result = api(
            body = """
                {"email":"invitee@example.com","inviter_name":"Host",
                 "server_name":"Prairie","expires_at":"2026-08-01T00:00:00Z"}
            """.trimIndent(),
            captured = captured,
        ).lookupInvitation("https://srv.example/", "tok/en?raw")

        assertEquals(HttpMethod.Get, captured.method)
        assertTrue(captured.url.contains("/api/v1/invitations/"))
        assertTrue(captured.url.contains("tok"), "token should remain in the path")
        assertIs<ApiResult.Success<*>>(result)
        val lookup = (result as ApiResult.Success).data
        assertEquals("invitee@example.com", lookup.email)
        assertEquals("Host", lookup.inviterName)
        assertEquals("Prairie", lookup.serverName)
        assertEquals("2026-08-01T00:00:00Z", lookup.expiresAt)
    }

    @Test
    fun acceptInvitationPostsPasswordAndReturnsLogin() = runTest {
        val captured = Captured()
        val result = api(captured = captured).acceptInvitation(
            serverUrl = "https://srv.example",
            token = "claim-token",
            password = "secret-pass",
        )

        assertEquals(HttpMethod.Post, captured.method)
        assertTrue(captured.url.endsWith("/api/v1/invitations/claim-token/accept"))
        assertTrue(captured.body.contains("\"password\":\"secret-pass\""))
        assertIs<ApiResult.Success<*>>(result)
    }
}
