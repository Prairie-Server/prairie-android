package org.siloserver.silo.android.ui.screens.auth

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import java.io.IOException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.siloserver.silo.network.SiloAuthPlugin
import org.siloserver.silo.network.SiloJson
import org.siloserver.silo.network.TokenManager
import org.siloserver.silo.network.api.AuthApi
import org.siloserver.silo.repository.AuthRepository
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class ServerSetupPersistenceTest {
    @Test
    fun failedProbeDoesNotReplacePreviouslyActiveServerUrl() = runTest(UnconfinedTestDispatcher()) {
        val tokenManager = RecordingTokenManager(serverUrl = "https://old.silo")
        val repository = AuthRepository(
            authApi = AuthApi(
                HttpClient(MockEngine) {
                    engine {
                        addHandler {
                            throw IOException("offline")
                        }
                    }
                    install(ContentNegotiation) { json(SiloJson) }
                    install(SiloAuthPlugin) { this.tokenManager = tokenManager }
                },
            ),
            tokenManager = tokenManager,
        )
        val viewModel = ServerSetupViewModel(repository)

        viewModel.onServerUrlChanged("bad.silo")
        viewModel.onConnectClick()

        assertEquals("https://old.silo", tokenManager.getServerUrl())
        assertEquals(
            emptyList(),
            tokenManager.serverUrlWrites,
            "Failed probes must not persist candidate URLs.",
        )
    }
}

private class RecordingTokenManager(
    private var serverUrl: String,
) : TokenManager {
    val serverUrlWrites = mutableListOf<String>()
    override val sessionExpired = MutableSharedFlow<Unit>()
    override suspend fun getAccessToken(): String? = null
    override suspend fun getRefreshToken(): String? = null
    override suspend fun saveTokens(accessToken: String, refreshToken: String, expiresIn: Long) = Unit
    override suspend fun clearTokens() = Unit
    override suspend fun invalidateSession() = Unit
    override suspend fun getProfileId(): String? = null
    override suspend fun setProfileId(profileId: String?) = Unit
    override suspend fun getProfileToken(): String? = null
    override suspend fun setProfileToken(token: String?) = Unit
    override suspend fun getServerUrl(): String = serverUrl
    override suspend fun setServerUrl(url: String) {
        serverUrl = url
        serverUrlWrites += url
    }
    override suspend fun getCurrentServerId(): String? = null
    override suspend fun switchActiveServer(serverId: String?) = Unit
    override suspend fun signOutCurrentServer() = Unit
}
