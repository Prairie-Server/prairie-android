package org.prairieserver.prairie.network

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TokenManagerContractTest {

    /** Implements only the abstract surface so the interface defaults run. */
    private class MinimalTokenManager : TokenManager {
        var access: String? = null
        var refresh: String? = null
        var profile: String? = null
        var profileTok: String? = null
        var url: String = "http://localhost"
        var server: String? = null
        override suspend fun getAccessToken() = access
        override suspend fun getRefreshToken() = refresh
        override suspend fun saveTokens(accessToken: String, refreshToken: String, expiresIn: Long) {
            access = accessToken; refresh = refreshToken
        }
        override suspend fun clearTokens() { access = null; refresh = null }
        override suspend fun invalidateSession() = clearTokens()
        override val sessionExpired: SharedFlow<Unit> = MutableSharedFlow()
        override suspend fun getProfileId() = profile
        override suspend fun setProfileId(profileId: String?) { profile = profileId }
        override suspend fun getProfileToken() = profileTok
        override suspend fun setProfileToken(token: String?) { profileTok = token }
        override suspend fun getServerUrl() = url
        override suspend fun setServerUrl(url: String) { this.url = url }
        override suspend fun getCurrentServerId() = server
        override suspend fun switchActiveServer(serverId: String?) { server = serverId }
        override suspend fun signOutCurrentServer() = clearTokens()
    }

    private val temporary = TemporaryAuthScope(
        generationId = "g1",
        serverId = "s1",
        serverUrl = "https://prairie.example",
        accessToken = "tmp-access",
        refreshToken = "tmp-refresh",
        profileId = "p1",
        profileToken = "pt1",
        expiresAtEpochMs = 1_000L,
    )

    @Test
    fun interfaceDefaultsInstallAccountSessionThroughAbstractSetters() = runTest {
        val tokens = MinimalTokenManager()
        assertNull(tokens.captureAccountSessionExpectation())
        tokens.replaceAccountSession(
            serverId = "s1",
            serverUrl = "https://prairie.example",
            accessToken = "a",
            refreshToken = "r",
            expiresIn = 60,
            profileId = "p",
            profileToken = "t",
        )
        assertEquals("https://prairie.example", tokens.getServerUrl())
        assertEquals("s1", tokens.getCurrentServerId())
        assertEquals(ProfileIdentity("p", "t"), tokens.getProfileIdentity())
        assertEquals("a", tokens.getAccessToken())
        assertFalse(tokens.accessTokenExpiresWithin(1_000))
        tokens.beginTemporaryScope(temporary)
        assertFalse(tokens.hasTemporaryScope())
        assertFalse(tokens.endTemporaryScope())

        val expectation = AccountSessionExpectation(1, null, "https://prairie.example")
        assertFailsWith<IllegalStateException> {
            tokens.replaceAccountSession(accessToken = "a", refreshToken = "r", expiresIn = 1, expectedIdentity = expectation)
        }
    }

    @Test
    fun sessionExpectationComparesIdentityNotLambdas() {
        val a = AccountSessionExpectation(3, "s1", "https://prairie.example")
        val b = AccountSessionExpectation(3, "s1", "https://prairie.example", installationAllowed = { false })
        assertTrue(a.isSameSession(b))
        assertFalse(a.isSameSession(null))
        assertFalse(a.isSameSession(b.copy(generation = 4)))
        assertFalse(a.isSameSession(b.copy(serverId = "s2")))
        assertFalse(a.isSameSession(b.copy(serverUrl = "https://other.example")))
        assertTrue(AccountSessionChangedException().message!!.contains("changed"))
    }

    @Test
    fun temporaryScopeToStringRedactsSecrets() {
        val text = temporary.toString()
        assertTrue(text.contains("serverId=s1"))
        assertFalse(text.contains("tmp-access"))
        assertFalse(text.contains("tmp-refresh"))
        assertFalse(text.contains("pt1"))
    }

    @Test
    fun inMemoryReplaceAccountSessionInstallsEverythingAtomically() = runTest {
        val tokens = TokenManagerImpl()
        val expectation = assertNotNull(tokens.captureAccountSessionExpectation())
        tokens.replaceAccountSession(
            serverId = null,
            serverUrl = "https://prairie.example/",
            accessToken = "a",
            refreshToken = "r",
            expiresIn = 3600,
            profileId = "p",
            profileToken = "t",
            expectedIdentity = expectation,
        )
        assertEquals("https://prairie.example", tokens.getServerUrl())
        assertEquals("a", tokens.getAccessToken())
        assertEquals("r", tokens.getRefreshToken())
        assertEquals("p", tokens.getProfileId())
        assertEquals("t", tokens.getProfileToken())

        val refused = AccountSessionExpectation(expectation.generation, null, "https://prairie.example", installationAllowed = { false })
        assertFailsWith<AccountSessionChangedException> {
            tokens.replaceAccountSession(accessToken = "b", refreshToken = "c", expiresIn = 1, expectedIdentity = refused)
        }
        assertEquals("a", tokens.getAccessToken())
    }

    @Test
    fun inMemoryTemporaryScopeShadowsPersistentCredentials() = runTest {
        val tokens = TokenManagerImpl()
        tokens.switchActiveServer(null)
        tokens.saveTokens("persist-a", "persist-r", 3600)
        tokens.setProfileId("persist-p")
        tokens.beginTemporaryScope(temporary)
        assertTrue(tokens.hasTemporaryScope())
        assertEquals("s1", tokens.getCurrentServerId())
        assertEquals("tmp-access", tokens.getAccessToken())

        tokens.saveTokens("rotated-a", "rotated-r", 60)
        assertEquals("rotated-a", tokens.getAccessToken())
        tokens.setProfileId("p2")
        tokens.setProfileToken("pt2")
        assertEquals(ProfileIdentity("p2", "pt2"), tokens.getProfileIdentity())
        assertFailsWith<IllegalStateException> { tokens.captureAccountSessionExpectation() }

        assertTrue(tokens.endTemporaryScope())
        assertFalse(tokens.endTemporaryScope())
        assertEquals("persist-a", tokens.getAccessToken())
        assertEquals("persist-p", tokens.getProfileId())

        tokens.beginTemporaryScope(temporary)
        tokens.signOutCurrentServer()
        assertFalse(tokens.hasTemporaryScope())
        assertEquals("persist-a", tokens.getAccessToken())
    }
}
