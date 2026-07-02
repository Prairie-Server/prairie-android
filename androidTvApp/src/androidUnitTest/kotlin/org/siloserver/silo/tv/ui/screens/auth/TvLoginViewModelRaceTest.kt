package org.siloserver.silo.tv.ui.screens.auth

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.siloserver.silo.model.auth.DeviceLoginDecisionResponse
import org.siloserver.silo.model.auth.DeviceLoginLookupResponse
import org.siloserver.silo.model.auth.DeviceLoginPollResponse
import org.siloserver.silo.model.auth.DeviceLoginStartResponse
import org.siloserver.silo.network.ApiResult
import org.siloserver.silo.network.SiloAuthPlugin
import org.siloserver.silo.network.SiloJson
import org.siloserver.silo.network.TokenManager
import org.siloserver.silo.network.api.AuthApi
import org.siloserver.silo.network.api.DeviceLoginApi
import org.siloserver.silo.repository.AuthRepository
import org.siloserver.silo.repository.DeviceLoginRepository
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class TvLoginViewModelRaceTest {
    private val dispatcher = UnconfinedTestDispatcher()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun credentialCompletionAfterQrApprovalDoesNotOverwriteQrTokens() = runTest(dispatcher) {
        val tokenManager = RecordingTokenStore()
        val releaseCredentialLogin = CompletableDeferred<Unit>()
        val credentialLoginStarted = CompletableDeferred<Unit>()
        val deviceApi = ControlledDeviceLoginApi()
        val viewModel = TvLoginViewModel(
            authRepository = AuthRepository(
                authApi = AuthApi(loginClient(tokenManager, releaseCredentialLogin, credentialLoginStarted)),
                tokenManager = tokenManager,
            ),
            tokenManager = tokenManager,
            deviceLogin = DeviceLoginRepository(deviceApi),
        )
        advanceUntilIdle()

        viewModel.onUsernameChanged("jim")
        viewModel.onPasswordChanged("Amsterdam123!")
        viewModel.onLoginClick()
        advanceUntilIdle()
        withTimeout(1_000) { credentialLoginStarted.await() }

        deviceApi.completePoll(
            DeviceLoginPollResponse(
                status = "approved",
                accessToken = "qr-access",
                refreshToken = "qr-refresh",
                expiresIn = 3600,
            ),
        )
        advanceUntilIdle()
        assertEquals("qr-access", tokenManager.accessToken)

        releaseCredentialLogin.complete(Unit)
        advanceUntilIdle()

        assertEquals("qr-access", tokenManager.accessToken)
        assertEquals(listOf("qr-access"), tokenManager.savedAccessTokens)
    }

    private fun loginClient(
        tokenManager: TokenManager,
        releaseCredentialLogin: CompletableDeferred<Unit>,
        credentialLoginStarted: CompletableDeferred<Unit>,
    ) = HttpClient(MockEngine) {
        engine {
            addHandler { request ->
                credentialLoginStarted.complete(Unit)
                releaseCredentialLogin.await()
                respond(
                    content = credentialLoginJson("credential-access", "credential-refresh"),
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json"),
                )
            }
        }
        install(ContentNegotiation) { json(SiloJson) }
        install(SiloAuthPlugin) { this.tokenManager = tokenManager }
    }
}

private class ControlledDeviceLoginApi : DeviceLoginApi {
    private val pollResult = CompletableDeferred<ApiResult<DeviceLoginPollResponse>>()

    override suspend fun startDeviceLogin(
        deviceName: String?,
        devicePlatform: String?,
    ): ApiResult<DeviceLoginStartResponse> = ApiResult.Success(
        DeviceLoginStartResponse(
            deviceCode = "device-code",
            userCode = "USER-CODE",
            matchCode = "1234",
            verificationUri = "https://silo.test/activate",
            verificationUriComplete = "https://silo.test/activate?code=USER-CODE",
            expiresAt = "2030-01-01T00:00:00Z",
            expiresIn = 600,
            interval = 1,
            deviceName = deviceName.orEmpty(),
            devicePlatform = devicePlatform.orEmpty(),
        ),
    )

    override suspend fun pollDeviceLogin(deviceCode: String): ApiResult<DeviceLoginPollResponse> =
        pollResult.await()

    fun completePoll(response: DeviceLoginPollResponse) {
        pollResult.complete(ApiResult.Success(response))
    }

    override suspend fun lookupDeviceLogin(token: String?, code: String?): ApiResult<DeviceLoginLookupResponse> =
        error("Not used")

    override suspend fun approveDeviceLogin(token: String?, code: String?): ApiResult<DeviceLoginDecisionResponse> =
        error("Not used")

    override suspend fun denyDeviceLogin(token: String?, code: String?): ApiResult<DeviceLoginDecisionResponse> =
        error("Not used")
}

private class RecordingTokenStore : TokenManager {
    val savedAccessTokens = mutableListOf<String>()
    var accessToken: String? = null
    private var refreshToken: String? = null
    private var serverUrl: String = "https://silo.test"
    override val sessionExpired = MutableSharedFlow<Unit>()
    override suspend fun getAccessToken(): String? = accessToken
    override suspend fun getRefreshToken(): String? = refreshToken
    override suspend fun saveTokens(accessToken: String, refreshToken: String, expiresIn: Long) {
        this.accessToken = accessToken
        this.refreshToken = refreshToken
        savedAccessTokens += accessToken
    }
    override suspend fun clearTokens() {
        accessToken = null
        refreshToken = null
    }
    override suspend fun invalidateSession() = Unit
    override suspend fun getProfileId(): String? = null
    override suspend fun setProfileId(profileId: String?) = Unit
    override suspend fun getProfileToken(): String? = null
    override suspend fun setProfileToken(token: String?) = Unit
    override suspend fun getServerUrl(): String = serverUrl
    override suspend fun setServerUrl(url: String) {
        serverUrl = url
    }
    override suspend fun getCurrentServerId(): String? = null
    override suspend fun switchActiveServer(serverId: String?) = Unit
    override suspend fun signOutCurrentServer() = Unit
}

private fun credentialLoginJson(accessToken: String, refreshToken: String): String = """
    {
      "access_token": "$accessToken",
      "refresh_token": "$refreshToken",
      "expires_in": 3600,
      "user": {
        "id": 1,
        "username": "jim",
        "email": "jim@example.com",
        "role": "user",
        "download_allowed": true
      }
    }
""".trimIndent()
