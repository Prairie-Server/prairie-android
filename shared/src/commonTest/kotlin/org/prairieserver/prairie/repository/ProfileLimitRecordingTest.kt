package org.prairieserver.prairie.repository

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.runTest
import org.prairieserver.prairie.model.server.AccountEntry
import org.prairieserver.prairie.model.server.ServerEntry
import org.prairieserver.prairie.network.ApiResult
import org.prairieserver.prairie.network.AuthScopeSnapshot
import org.prairieserver.prairie.network.ServerRegistry
import org.prairieserver.prairie.network.PrairieJson
import org.prairieserver.prairie.network.TokenManager
import org.prairieserver.prairie.network.TokenManagerImpl
import org.prairieserver.prairie.network.api.ProfileApi
import org.prairieserver.prairie.network.apiv2.ApiV2Gate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/** The open account's profile limit is kept as its profile list loads. */
class ProfileLimitRecordingTest {
    private val profile = """{"id":"42","name":"Reader","created_at":"2026-01-01T00:00:00Z","updated_at":"2026-01-01T00:00:00Z"}"""
    private var handoff = false
    private val tokens = object : TokenManager by TokenManagerImpl() {
        override suspend fun snapshotCurrentScope() =
            AuthScopeSnapshot("server", null, "https://example.invalid", null, identityGeneration = 1)
        override suspend fun hasTemporaryScope() = handoff
    }

    private fun repository(body: String, registry: ServerRegistry): ProfileRepository {
        val client = HttpClient(MockEngine { respond(body, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json")) }) {
            install(ContentNegotiation) { json(PrairieJson) }
        }
        return ProfileRepository(ProfileApi(client, ApiV2Gate.Unrestricted, tokens), tokens, registry)
    }

    @Test fun theLimitIsKeptWithTheOpenAccount() = runTest {
        val registry = OneAccountRegistry()
        assertIs<ApiResult.Success<*>>(repository("""{"items":[$profile],"max_profiles":1}""", registry).listProfiles())
        assertEquals(1, registry.activeAccount.value?.maxProfiles)
        // A server that does not say clears a limit it said before.
        assertIs<ApiResult.Success<*>>(repository("""{"items":[$profile]}""", registry).listProfiles())
        assertEquals(null, registry.activeAccount.value?.maxProfiles)
    }

    @Test fun aHandoffSessionsListLeavesTheOpenAccountsLimitAlone() = runTest {
        val registry = OneAccountRegistry(maxProfiles = 1)
        handoff = true
        assertIs<ApiResult.Success<*>>(repository("""{"items":[$profile]}""", registry).listProfiles())
        assertEquals(1, registry.activeAccount.value?.maxProfiles)
    }
}

private class OneAccountRegistry(maxProfiles: Int? = null) : ServerRegistry {
    private val server = ServerEntry(id = "server", url = "https://example.invalid")
    private val account = MutableStateFlow<AccountEntry?>(
        AccountEntry(localAccountId = "account", serverId = "server", maxProfiles = maxProfiles),
    )

    override val entries: StateFlow<List<ServerEntry>> = MutableStateFlow(listOf(server))
    override val activeServerId: StateFlow<String?> = MutableStateFlow("server")
    override val activeEntry: StateFlow<ServerEntry?> = MutableStateFlow(server)
    override val activeAccountId: StateFlow<String?> = MutableStateFlow("account")
    override val activeAccount: StateFlow<AccountEntry?> = account

    override suspend fun setMaxProfiles(accountId: String, maxProfiles: Int?) {
        account.value = account.value?.copy(maxProfiles = maxProfiles)
    }

    override suspend fun addOrUpdate(url: String, fetchedName: String?): String = "server"
    override suspend fun rename(serverId: String, userOverrideName: String?) = Unit
    override suspend fun setFetchedName(serverId: String, fetchedName: String?) = Unit
    override suspend fun setProfileId(serverId: String, profileId: String?) = Unit
    override suspend fun remove(serverId: String) = Unit
    override suspend fun signOut(serverId: String) = Unit
    override suspend fun switchTo(serverId: String) = Unit
    override suspend fun touchActive() = Unit
}
