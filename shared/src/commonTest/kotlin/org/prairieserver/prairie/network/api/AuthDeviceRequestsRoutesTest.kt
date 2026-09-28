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
import org.prairieserver.prairie.model.auth.RefreshRequest
import org.prairieserver.prairie.model.auth.SignupRequest
import org.prairieserver.prairie.network.ApiResult
import org.prairieserver.prairie.network.AuthScopeSnapshot
import org.prairieserver.prairie.network.PrairieJson
import org.prairieserver.prairie.network.apiv2.ApiV2Gate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Route and problem-mapping coverage for the v2 auth, device-login and
 * requests transports: every call must hit its v2 path and surface the
 * server's problem code unchanged.
 */
class AuthDeviceRequestsRoutesTest {
    private val problem =
        """{"type":"https://prairieserver.org/docs/api/v2/problems/not_found","title":"Missing","status":404,"instance":"urn:test","code":"not_found","detail":"Missing"}"""

    private val pair =
        """{"access_token":"acc","refresh_token":"ref","expires_in":3600,"user":{"id":"1","username":"user","email":"u@example.test","role":"user","permissions":[],"download_allowed":true}}"""

    private fun client(seen: MutableList<String>, status: HttpStatusCode = HttpStatusCode.NotFound, body: (String) -> String = { problem }) =
        HttpClient(MockEngine { request ->
            seen += "${request.method.value} ${request.url.encodedPath}"
            val type = if (status.value >= 400) "application/problem+json" else "application/json"
            respond(body(request.url.encodedPath), status, headersOf(HttpHeaders.ContentType, type))
        }) { install(ContentNegotiation) { json(PrairieJson) } }

    private fun assertNotFound(result: ApiResult<*>) {
        val error = assertIs<ApiResult.Error>(result)
        assertEquals(404, error.code)
        assertEquals("not_found", error.error)
    }

    private val scope = AuthScopeSnapshot(serverId = "s1", profileId = null, serverUrl = "https://prairie.example", profileToken = null)

    @Test
    fun authRoutesSurfaceProblems() = runTest {
        val seen = mutableListOf<String>()
        val api = AuthApi(client(seen), ApiV2Gate.Unrestricted)
        assertNotFound(api.refresh(RefreshRequest("r")))
        assertNotFound(api.signup(SignupRequest("u", "e", "p", "invite")))
        assertNotFound(api.setup("u", "e", "p"))
        assertNotFound(api.getSetupStatus())
        assertNotFound(api.getSignupStatus())
        assertNotFound(api.lookupInvitation("https://prairie.example/", "tok/en"))
        assertNotFound(api.invitationCapabilities("https://prairie.example"))
        assertNotFound(api.getMe())
        assertNotFound(api.logout())
        assertEquals(
            listOf(
                "POST /api/v2/auth/refresh",
                "POST /api/v2/auth/signup",
                "POST /api/v2/auth/setup",
                "GET /api/v2/system/setup",
                "GET /api/v2/auth/signup",
                "GET /api/v2/invitations/tok%2Fen",
                "GET /api/v2/invitations/capabilities",
                "GET /api/v2/account/me",
                "POST /api/v2/auth/logout",
            ),
            seen,
        )
    }

    @Test
    fun authSuccessShapesMapToDomain() = runTest {
        val created = AuthApi(client(mutableListOf(), HttpStatusCode.Created) { pair }, ApiV2Gate.Unrestricted)
        assertEquals("acc", assertIs<ApiResult.Success<*>>(created.signup(SignupRequest("u", "e", "p", "i"), "https://prairie.example/")).let { (it.data as org.prairieserver.prairie.model.auth.LoginResponse).accessToken })
        assertIs<ApiResult.Success<*>>(created.setup("u", "e", "p", "https://prairie.example"))

        val ok = AuthApi(client(mutableListOf(), HttpStatusCode.OK) { path ->
            when {
                path.endsWith("/system/setup") -> """{"needs_setup":true}"""
                path.endsWith("/auth/signup") -> """{"enabled":true}"""
                path.endsWith("/auth/refresh") -> """{"access_token":"a","refresh_token":"r","expires_in":1}"""
                else -> """{"id":"7","username":"u","email":"e","role":"admin","download_allowed":true,
                    "impersonation":{"active":true,"impersonator_user_id":"1","impersonator_username":"root"}}"""
            }
        }, ApiV2Gate.Unrestricted)
        assertEquals(true, assertIs<ApiResult.Success<*>>(ok.getSetupStatus()).let { (it.data as org.prairieserver.prairie.model.auth.SetupStatusResponse).needsSetup })
        assertEquals(true, assertIs<ApiResult.Success<*>>(ok.getSignupStatus()).let { (it.data as org.prairieserver.prairie.model.auth.SignupStatusResponse).enabled })
        assertIs<ApiResult.Success<*>>(ok.refresh(RefreshRequest("r")))
        val user = assertIs<ApiResult.Success<org.prairieserver.prairie.model.auth.User>>(ok.getMe()).data
        assertEquals("7", user.id)
        assertEquals("admin", user.role)
        assertTrue(user.downloadAllowed)
        assertEquals("root", user.impersonation?.impersonatorUsername)

        val noContent = AuthApi(client(mutableListOf(), HttpStatusCode.NoContent) { "" }, ApiV2Gate.Unrestricted)
        assertIs<ApiResult.Success<Unit>>(noContent.logout())
    }

    @Test
    fun deviceLoginRoutesSurfaceProblems() = runTest {
        val seen = mutableListOf<String>()
        val api = DefaultDeviceLoginApi(client(seen), ApiV2Gate.Unrestricted)
        assertNotFound(api.lookupDeviceLogin(token = "t", code = null))
        assertNotFound(api.approveDeviceLogin(token = null, code = "ABCD"))
        assertNotFound(api.denyDeviceLogin(token = null, code = "ABCD"))
        assertNotFound(api.lookupDeviceLoginForScope(scope, "ABCD"))
        assertNotFound(api.approveDeviceLoginForScope(scope, "ABCD"))
        assertNotFound(api.approveRemotePlaybackForScope(scope, "ABCD"))
        assertNotFound(api.endRemotePlayback(scope))
        assertNotFound(api.denyDeviceLoginForScope(scope, "ABCD"))
        assertEquals(
            listOf(
                "GET /api/v2/auth/device",
                "POST /api/v2/auth/device/approve",
                "POST /api/v2/auth/device/deny",
                "GET /api/v2/auth/device",
                "POST /api/v2/auth/device/approve",
                "POST /api/v2/auth/device/approve-handoff",
                "POST /api/v2/auth/logout",
                "POST /api/v2/auth/device/deny",
            ),
            seen,
        )
    }

    @Test
    fun interfaceDefaultsRejectScopedOperations() = runTest {
        val minimal = object : DeviceLoginApi {
            override suspend fun startDeviceLogin(deviceName: String?, devicePlatform: String?) = throw UnsupportedOperationException()
            override suspend fun pollDeviceLogin(deviceCode: String) = throw UnsupportedOperationException()
            override suspend fun lookupDeviceLogin(token: String?, code: String?) = throw UnsupportedOperationException()
            override suspend fun approveDeviceLogin(token: String?, code: String?) = throw UnsupportedOperationException()
            override suspend fun denyDeviceLogin(token: String?, code: String?) = throw UnsupportedOperationException()
        }
        for (result in listOf(
            minimal.startDeviceLoginAt("https://prairie.example", null, null),
            minimal.pollDeviceLoginAt("https://prairie.example", "dev"),
            minimal.startRemotePlaybackAt("https://prairie.example", null, null),
            minimal.lookupDeviceLoginForScope(scope, "c"),
            minimal.approveDeviceLoginForScope(scope, "c"),
            minimal.approveRemotePlaybackForScope(scope, "c"),
            minimal.endRemotePlayback(scope),
            minimal.denyDeviceLoginForScope(scope, "c"),
        )) {
            assertEquals("scoped_device_login_unsupported", assertIs<ApiResult.Error>(result).error)
        }
        assertEquals("remote_playback_unsupported", assertIs<ApiResult.Error>(minimal.remotePlaybackCapabilityAt("https://prairie.example")).error)
    }

    @Test
    fun requestsRoutesSurfaceProblems() = runTest {
        val seen = mutableListOf<String>()
        val api = DefaultRequestsApi(client(seen), ApiV2Gate.Unrestricted)
        assertNotFound(api.status())
        assertNotFound(api.discover())
        assertNotFound(api.discoverSection("trending", 2))
        assertNotFound(api.search("dune", "movie", 1))
        assertNotFound(api.detail("movie", 438631))
        assertNotFound(api.mine())
        assertNotFound(api.get("r1"))
        assertNotFound(api.cancel("r1"))
        assertEquals(
            listOf(
                "GET /api/v2/requests/status",
                "GET /api/v2/requests/discover",
                "GET /api/v2/requests/discover/trending",
                "GET /api/v2/requests/search",
                "GET /api/v2/requests/detail/movie/438631",
                "GET /api/v2/requests/mine",
                "GET /api/v2/requests/r1",
                "POST /api/v2/requests/r1/cancel",
            ),
            seen,
        )
    }

    @Test
    fun requestsMineRejectsRepeatedCursor() = runTest {
        val api = DefaultRequestsApi(client(mutableListOf(), HttpStatusCode.OK) {
            """{"items":[],"page":{"has_more":true,"next_cursor":"same"}}"""
        }, ApiV2Gate.Unrestricted)
        val result = api.mine()
        assertIs<ApiResult.Error>(result)
    }
}
