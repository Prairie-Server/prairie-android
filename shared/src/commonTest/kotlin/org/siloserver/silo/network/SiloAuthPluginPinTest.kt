package org.siloserver.silo.network

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
 * Verifies [SiloAuthPlugin] honors an [AuthScopeSnapshot] pin: a request
 * tagged with [authScope] is bound to the snapshot's server URL + profile and
 * the per-server access token, regardless of the globally-active scope. This is
 * the network-layer guarantee the Track B outbox drain relies on.
 */
class SiloAuthPluginPinTest {

    private class Captured {
        var url: String = ""
        var authorization: String? = null
        var profileId: String? = null
        var profileToken: String? = null
        var siloClient: String? = null
        var siloClientVersion: String? = null
    }

    private fun client(
        tokenManager: TokenManager,
        captured: Captured,
        deviceMetadataProvider: DeviceMetadataProvider? = null,
    ): HttpClient =
        HttpClient(
            MockEngine { request ->
                captured.url = request.url.toString()
                captured.authorization = request.headers[HttpHeaders.Authorization]
                captured.profileId = request.headers["X-Profile-Id"]
                captured.profileToken = request.headers["X-Profile-Token"]
                captured.siloClient = request.headers["X-Silo-Client"]
                captured.siloClientVersion = request.headers["X-Silo-Client-Version"]
                respond("{}", HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
            },
        ) {
            install(SiloAuthPlugin) {
                this.tokenManager = tokenManager
                this.deviceMetadataProvider = deviceMetadataProvider
            }
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

    @Test
    fun requestsIncludePlaybackClientHeadersWhenDeviceMetadataProvidesThem() = runTest {
        val tokenManager = TokenManagerImpl().apply {
            setServerUrl("https://silo.example")
            saveTokens(accessToken = "ACCESS-A", refreshToken = "REFRESH-A", expiresIn = 3600)
        }
        val captured = Captured()
        val provider = object : DeviceMetadataProvider {
            override suspend fun current(): SiloDeviceMetadata =
                SiloDeviceMetadata(
                    id = "device-1",
                    name = "Amazon AFTKA",
                    platform = "android-tv",
                    clientName = "Silo Android TV",
                    clientVersion = "0.2.3",
                )
        }

        client(tokenManager, captured, provider).post("/api/v1/playback/start")

        assertEquals("Silo Android TV", captured.siloClient)
        assertEquals("0.2.3", captured.siloClientVersion)
    }
}
