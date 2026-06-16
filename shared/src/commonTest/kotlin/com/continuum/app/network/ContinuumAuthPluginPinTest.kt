package com.continuum.app.network

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.post
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Verifies [ContinuumAuthPlugin] honors an [AuthScopeSnapshot] pin: a request
 * tagged with [authScope] is bound to the snapshot's server URL + profile and
 * the per-server access token, regardless of the globally-active scope. This is
 * the network-layer guarantee the Track B outbox drain relies on.
 */
class ContinuumAuthPluginPinTest {

    private class Captured {
        var url: String = ""
        var authorization: String? = null
        var profileId: String? = null
        var profileToken: String? = null
    }

    private fun client(tokenManager: TokenManager, captured: Captured): HttpClient =
        HttpClient(
            MockEngine { request ->
                captured.url = request.url.toString()
                captured.authorization = request.headers[HttpHeaders.Authorization]
                captured.profileId = request.headers["X-Profile-Id"]
                captured.profileToken = request.headers["X-Profile-Token"]
                respond("{}", HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
            },
        ) {
            install(ContinuumAuthPlugin) { this.tokenManager = tokenManager }
        }

    @Test
    fun pinnedRequestUsesSnapshotUrlProfileAndServerToken() = runTest {
        val tokenManager = TokenManagerImpl().apply {
            saveTokens(accessToken = "ACCESS-A", refreshToken = "REFRESH-A", expiresIn = 3600)
        }
        val captured = Captured()
        val snapshot = AuthScopeSnapshot(
            serverId = "server-a",
            profileId = "profile-a",
            serverUrl = "https://a.example",
            profileToken = "ptoken-a",
        )

        client(tokenManager, captured).post("/api/v1/watched/item-1") { authScope(snapshot) }

        assertEquals("https://a.example/api/v1/watched/item-1", captured.url)
        assertEquals("Bearer ACCESS-A", captured.authorization)
        assertEquals("profile-a", captured.profileId)
        assertEquals("ptoken-a", captured.profileToken)
    }

    @Test
    fun pinnedRequestOmitsProfileTokenHeaderWhenSnapshotHasNone() = runTest {
        val tokenManager = TokenManagerImpl().apply {
            saveTokens(accessToken = "ACCESS-A", refreshToken = "REFRESH-A", expiresIn = 3600)
        }
        val captured = Captured()
        val snapshot = AuthScopeSnapshot(
            serverId = "server-a",
            profileId = null,
            serverUrl = "https://a.example",
            profileToken = null,
        )

        client(tokenManager, captured).post("/api/v1/watched/item-1") { authScope(snapshot) }

        assertEquals(null, captured.profileToken)
        assertEquals(null, captured.profileId)
        assertEquals("Bearer ACCESS-A", captured.authorization)
    }
}
