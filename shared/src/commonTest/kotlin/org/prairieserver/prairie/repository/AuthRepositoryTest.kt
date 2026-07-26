package org.prairieserver.prairie.repository

import org.prairieserver.prairie.model.auth.LoginResponse
import org.prairieserver.prairie.model.auth.User
import org.prairieserver.prairie.model.server.ServerEntry
import org.prairieserver.prairie.network.ApiResult
import org.prairieserver.prairie.network.PrairieJson
import org.prairieserver.prairie.network.ServerRegistry
import org.prairieserver.prairie.network.TemporaryAuthScope
import org.prairieserver.prairie.network.TokenManager
import org.prairieserver.prairie.network.TokenManagerImpl
import org.prairieserver.prairie.network.api.AuthApi
import org.prairieserver.prairie.network.api.HealthApi
import org.prairieserver.prairie.network.api.HealthStatus
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AuthRepositoryTest {

    private val loginJson = """
        {"access_token":"a","refresh_token":"r","expires_in":3600,
         "user":{"id":1,"username":"ada","email":"ada@ex.com","role":"admin"}}
    """.trimIndent()

    private fun client(
        handler: (io.ktor.client.request.HttpRequestData) -> Pair<HttpStatusCode, String>,
    ): HttpClient = HttpClient(
        MockEngine { request ->
            val (status, body) = handler(request)
            respond(body, status, headersOf(HttpHeaders.ContentType, "application/json"))
        },
    ) {
        install(ContentNegotiation) { json(PrairieJson) }
    }

    @Test
    fun loginPersistsTokensAndReturnsUser() = runTest {
        val tokens = TokenManagerImpl()
        val result = AuthRepository(AuthApi(client { HttpStatusCode.OK to loginJson }), tokens)
            .login("ada", "pw")
        assertIs<ApiResult.Success<User>>(result)
        assertEquals("ada", result.data.username)
        assertEquals("a", tokens.getAccessToken())
        assertEquals("r", tokens.getRefreshToken())
    }

    @Test
    fun loginForTokensDoesNotPersist() = runTest {
        val tokens = TokenManagerImpl()
        val result = AuthRepository(AuthApi(client { HttpStatusCode.OK to loginJson }), tokens)
            .loginForTokens("ada", "pw")
        assertIs<ApiResult.Success<LoginResponse>>(result)
        assertNull(tokens.getAccessToken())
    }

    @Test
    fun signupSetupStatusSessionsAndLogout() = runTest {
        val tokens = RecordingTokenManager()
        tokens.refreshToken = "r"
        tokens.currentServerId = "srv-1"
        val api = AuthApi(client { req ->
            val path = req.url.encodedPath
            when {
                path.endsWith("/signup") && req.method == HttpMethod.Post -> HttpStatusCode.OK to loginJson
                path.endsWith("/setup") && req.method == HttpMethod.Post -> HttpStatusCode.OK to loginJson
                path.endsWith("/setup") -> HttpStatusCode.OK to """{"needs_setup":true}"""
                path.endsWith("/signup") -> HttpStatusCode.OK to """{"enabled":false}"""
                path.endsWith("/me") ->
                    HttpStatusCode.OK to """{"id":1,"username":"ada","email":"ada@ex.com","role":"admin"}"""
                path.endsWith("/sessions") && req.method == HttpMethod.Get ->
                    HttpStatusCode.OK to
                        """{"sessions":[{"id":"s1","device_name":"tv","ip_address":"1.1.1.1","created_at":"t","expires_at":"t"}]}"""
                path.contains("/sessions/") -> HttpStatusCode.NoContent to ""
                path.endsWith("/logout") -> HttpStatusCode.NoContent to ""
                else -> HttpStatusCode.NotFound to "{}"
            }
        })
        val registry = FakeServerRegistry()
        val repo = AuthRepository(api, tokens, serverRegistry = registry)
        assertIs<ApiResult.Success<*>>(repo.signup("ada", "a@e.com", "pw", "invite"))
        assertEquals("a", tokens.accessToken)
        assertIs<ApiResult.Success<*>>(repo.setup("ada", "a@e.com", "pw"))
        assertEquals(true, (repo.getSetupStatus() as ApiResult.Success).data.needsSetup)
        assertEquals(false, (repo.getSignupStatus() as ApiResult.Success).data.enabled)
        assertEquals(true, (repo.getSetupStatus("https://srv") as ApiResult.Success).data.needsSetup)
        assertEquals(false, (repo.getSignupStatus("https://srv") as ApiResult.Success).data.enabled)
        assertEquals("ada", (repo.getCurrentUser() as ApiResult.Success).data.username)
        assertEquals(1, (repo.getSessions() as ApiResult.Success).data.size)
        assertIs<ApiResult.Success<*>>(repo.deleteSession("s1"))
        assertTrue(repo.isLoggedIn())
        repo.logout()
        assertTrue(tokens.signedOut)
        assertEquals(listOf("srv-1"), registry.signOutCalls)
    }

    @Test
    fun setServerUrlUsesRegistryWhenPresent() = runTest {
        val tokens = RecordingTokenManager()
        val registry = FakeServerRegistry()
        AuthRepository(AuthApi(client { HttpStatusCode.OK to "{}" }), tokens, serverRegistry = registry)
            .setServerUrl("https://Example.COM/prairie/")
        assertEquals(listOf("https://Example.COM/prairie/"), registry.addCalls)
        assertEquals(listOf("id-0"), registry.switchCalls)
        assertEquals("id-0", tokens.switchedTo)
    }

    @Test
    fun setServerUrlFallsBackWithoutRegistry() = runTest {
        val tokens = RecordingTokenManager()
        AuthRepository(AuthApi(client { HttpStatusCode.OK to "{}" }), tokens)
            .setServerUrl("https://solo.example")
        assertEquals("https://solo.example", tokens.serverUrl)
    }

    @Test
    fun refreshActiveServerNameUpdatesRegistry() = runTest {
        val registry = FakeServerRegistry()
        registry.addOrUpdate("https://srv.example")
        registry.switchTo("id-0")
        val health = object : HealthApi(HttpClient(MockEngine { error("unused") })) {
            override suspend fun checkHealth() = ApiResult.Success(HealthStatus("ok", serverName = " Prairie "))
        }
        AuthRepository(
            AuthApi(client { HttpStatusCode.OK to "{}" }),
            RecordingTokenManager(),
            serverRegistry = registry,
            healthApi = health,
        ).refreshActiveServerName()
        assertEquals("Prairie", registry.entries.value.first().fetchedName)
    }

    @Test
    fun loginPropagatesErrors() = runTest {
        val result = AuthRepository(
            AuthApi(client { HttpStatusCode.Unauthorized to """{"error":"bad","message":"nope"}""" }),
            TokenManagerImpl(),
        ).login("x", "y")
        assertIs<ApiResult.Error>(result)
    }
}

private class RecordingTokenManager : TokenManager {
    var accessToken: String? = null
    var refreshToken: String? = null
    var serverUrl: String = "http://localhost:8090"
    var currentServerId: String? = null
    var switchedTo: String? = null
    var signedOut = false
    private val _sessionExpired = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    override val sessionExpired: SharedFlow<Unit> = _sessionExpired
    override suspend fun getAccessToken() = accessToken
    override suspend fun getRefreshToken() = refreshToken
    override suspend fun saveTokens(accessToken: String, refreshToken: String, expiresIn: Long) {
        this.accessToken = accessToken
        this.refreshToken = refreshToken
    }
    override suspend fun clearTokens() { accessToken = null; refreshToken = null }
    override suspend fun invalidateSession() { clearTokens(); _sessionExpired.tryEmit(Unit) }
    override suspend fun getProfileId(): String? = null
    override suspend fun setProfileId(profileId: String?) = Unit
    override suspend fun getProfileToken(): String? = null
    override suspend fun setProfileToken(token: String?) = Unit
    override suspend fun getServerUrl() = serverUrl
    override suspend fun setServerUrl(url: String) { serverUrl = url.trimEnd('/') }
    override suspend fun getCurrentServerId() = currentServerId
    override suspend fun switchActiveServer(serverId: String?) { switchedTo = serverId; currentServerId = serverId }
    override suspend fun signOutCurrentServer() { signedOut = true; accessToken = null; refreshToken = null }
    override suspend fun beginTemporaryScope(scope: TemporaryAuthScope) = Unit
}

private class FakeServerRegistry : ServerRegistry {
    val addCalls = mutableListOf<String>()
    val switchCalls = mutableListOf<String>()
    val signOutCalls = mutableListOf<String>()
    private val _entries = MutableStateFlow<List<ServerEntry>>(emptyList())
    private val _active = MutableStateFlow<String?>(null)
    private val _activeEntry = MutableStateFlow<ServerEntry?>(null)
    override val entries: StateFlow<List<ServerEntry>> = _entries
    override val activeServerId: StateFlow<String?> = _active
    override val activeEntry: StateFlow<ServerEntry?> = _activeEntry
    override suspend fun addOrUpdate(url: String, fetchedName: String?): String {
        addCalls += url
        val id = "id-${addCalls.size - 1}"
        val entry = ServerEntry(id = id, url = url, fetchedName = fetchedName)
        _entries.value = _entries.value.filter { it.id != id } + entry
        return id
    }
    override suspend fun rename(serverId: String, userOverrideName: String?) = Unit
    override suspend fun setFetchedName(serverId: String, fetchedName: String?) {
        _entries.value = _entries.value.map {
            if (it.id == serverId) it.copy(fetchedName = fetchedName) else it
        }
        if (_active.value == serverId) {
            _activeEntry.value = _entries.value.firstOrNull { it.id == serverId }
        }
    }
    override suspend fun setProfileId(serverId: String, profileId: String?) = Unit
    override suspend fun remove(serverId: String) = Unit
    override suspend fun signOut(serverId: String) { signOutCalls += serverId }
    override suspend fun switchTo(serverId: String) {
        switchCalls += serverId
        _active.value = serverId
        _activeEntry.value = _entries.value.firstOrNull { it.id == serverId }
    }
    override suspend fun touchActive() = Unit
}
