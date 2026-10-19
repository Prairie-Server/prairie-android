package org.prairieserver.prairie.repository

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import org.prairieserver.prairie.network.AuthScopeAttributeKey
import org.prairieserver.prairie.network.AuthScopeSnapshot
import org.prairieserver.prairie.network.TokenManager
import org.prairieserver.prairie.network.TokenManagerImpl
import org.prairieserver.prairie.network.api.AuthApi
import org.prairieserver.prairie.network.apiv2.ApiV2Gate
import kotlin.test.Test
import kotlin.test.assertEquals

class AuthRepositorySignOutTest {
    @Test
    fun signOutEndsTheServerSessionItBeganOnEvenAfterASwitch() = runTest {
        val home = AuthScopeSnapshot("home", "profile", "https://home.example", "pin", identityGeneration = 1)
        var active = home
        val tokens = object : TokenManager by TokenManagerImpl() {
            override suspend fun snapshotCurrentScope() = active
        }
        var endedFor: AuthScopeSnapshot? = null
        val client = HttpClient(MockEngine { request ->
            endedFor = request.attributes.getOrNull(AuthScopeAttributeKey)
            respond("", HttpStatusCode.NoContent)
        })
        try {
            val repository = AuthRepository(AuthApi(client, ApiV2Gate.Unrestricted), tokens)
            val target = repository.captureSignOutTarget()
            // The user switches to the cabin while the sign-out waits on the network.
            active = home.copy(serverId = "cabin", serverUrl = "https://cabin.example")

            repository.logout(target)

            assertEquals(home, endedFor, "the cabin's session is never the one ended")
        } finally {
            client.close()
        }
    }
}
