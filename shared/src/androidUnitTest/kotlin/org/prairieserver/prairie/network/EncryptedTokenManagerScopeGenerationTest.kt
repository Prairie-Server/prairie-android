package org.prairieserver.prairie.network

import android.content.SharedPreferences
import java.lang.reflect.Proxy
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.runTest
import org.prairieserver.prairie.model.server.ServerEntry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class EncryptedTokenManagerScopeGenerationTest {

    @Test
    fun staleSameServerScopeCannotReadOrRestoreReloggedCredentials() = runTest {
        val registry = FakeServerRegistry()
        val manager = EncryptedTokenManagerImpl(
            prefs = inMemoryPreferences(),
            registry = registry,
        )
        manager.saveTokens("old-access", "old-refresh", 3600)
        val staleScope = checkNotNull(manager.snapshotCurrentScope())

        manager.clearTokens()
        manager.saveTokens("new-access", "new-refresh", 3600)

        assertNull(manager.getAccessTokenForScope(staleScope))
        assertNull(manager.getRefreshTokenForScope(staleScope))

        manager.saveTokensForScope(staleScope, "stale-access", "stale-refresh", 3600)

        assertEquals("new-access", manager.getAccessToken())
        assertEquals("new-refresh", manager.getRefreshToken())
    }

    @Test
    fun startupSnapshotOfPreloadedCredentialsCannotReadOrRestoreReloggedCredentials() = runTest {
        val registry = FakeServerRegistry()
        val manager = EncryptedTokenManagerImpl(
            prefs = inMemoryPreferences(
                AndroidServerRegistry.serverScopedKey("server-a", EncryptedTokenManagerImpl.KEY_ACCESS_TOKEN) to
                    "old-access",
                AndroidServerRegistry.serverScopedKey("server-a", EncryptedTokenManagerImpl.KEY_REFRESH_TOKEN) to
                    "old-refresh",
            ),
            registry = registry,
        )
        val staleScope = checkNotNull(manager.snapshotCurrentScope())

        manager.clearTokens()
        manager.saveTokens("new-access", "new-refresh", 3600)

        assertNull(manager.getAccessTokenForScope(staleScope))
        assertNull(manager.getRefreshTokenForScope(staleScope))
        manager.saveTokensForScope(staleScope, "stale-access", "stale-refresh", 3600)
        assertEquals("new-access", manager.getAccessToken())
        assertEquals("new-refresh", manager.getRefreshToken())
    }

    @Test
    fun registryFirstSwitchInvalidatesSnapshotBeforeManagerSwitchCall() = runTest {
        val registry = FakeServerRegistry()
        val manager = EncryptedTokenManagerImpl(
            prefs = inMemoryPreferences(),
            registry = registry,
        )
        manager.saveTokens("server-a-access", "server-a-refresh", 3600)
        val staleScope = checkNotNull(manager.snapshotCurrentScope())

        registry.switchExternally("server-b")
        manager.getAccessToken()
        manager.switchActiveServer("server-b")

        assertNull(manager.getAccessTokenForScope(staleScope))
        manager.saveTokensForScope(staleScope, "stale-access", "stale-refresh", 3600)
        assertNull(manager.getAccessTokenForScope(staleScope))
    }

    private class FakeServerRegistry : ServerRegistry {
        private val serverA = ServerEntry(id = "server-a", url = "https://server-a.example")
        private val serverB = ServerEntry(id = "server-b", url = "https://server-b.example")
        private val entriesFlow = MutableStateFlow(listOf(serverA, serverB))
        private val activeServerIdFlow = MutableStateFlow<String?>(serverA.id)
        private val activeEntryFlow = MutableStateFlow<ServerEntry?>(serverA)
        override val entries: StateFlow<List<ServerEntry>> = entriesFlow
        override val activeServerId: StateFlow<String?> = activeServerIdFlow
        override val activeEntry: StateFlow<ServerEntry?> = activeEntryFlow
        override suspend fun addOrUpdate(url: String, fetchedName: String?): String = serverA.id
        override suspend fun rename(serverId: String, userOverrideName: String?) = Unit
        override suspend fun setFetchedName(serverId: String, fetchedName: String?) = Unit
        override suspend fun setProfileId(serverId: String, profileId: String?) = Unit
        override suspend fun remove(serverId: String) = Unit
        override suspend fun signOut(serverId: String) = Unit
        override suspend fun switchTo(serverId: String) = Unit
        override suspend fun touchActive() = Unit

        fun switchExternally(serverId: String) {
            activeServerIdFlow.value = serverId
            activeEntryFlow.value = entriesFlow.value.first { it.id == serverId }
        }
    }

    private fun inMemoryPreferences(vararg initialValues: Pair<String, Any?>): SharedPreferences {
        val values = mutableMapOf<String, Any?>(*initialValues)
        lateinit var preferences: SharedPreferences
        preferences = Proxy.newProxyInstance(
            SharedPreferences::class.java.classLoader,
            arrayOf(SharedPreferences::class.java),
        ) { _, method, args ->
            when (method.name) {
                "getString" -> values[args!![0]] as? String ?: args[1]
                "getLong" -> values[args!![0]] as? Long ?: args[1]
                "contains" -> values.containsKey(args!![0])
                "getAll" -> values.toMap()
                "edit" -> editor(values)
                "registerOnSharedPreferenceChangeListener",
                "unregisterOnSharedPreferenceChangeListener" -> Unit
                else -> method.defaultValue()
            }
        } as SharedPreferences
        return preferences
    }

    private fun editor(values: MutableMap<String, Any?>): SharedPreferences.Editor {
        lateinit var editor: SharedPreferences.Editor
        editor = Proxy.newProxyInstance(
            SharedPreferences.Editor::class.java.classLoader,
            arrayOf(SharedPreferences.Editor::class.java),
        ) { _, method, args ->
            when (method.name) {
                "putString", "putLong" -> editor.also { values[args!![0] as String] = args[1] }
                "remove" -> editor.also { values.remove(args!![0] as String) }
                "clear" -> editor.also { values.clear() }
                "apply" -> Unit
                "commit" -> true
                else -> editor
            }
        } as SharedPreferences.Editor
        return editor
    }

    private fun java.lang.reflect.Method.defaultValue(): Any? = when (returnType) {
        java.lang.Boolean.TYPE -> false
        java.lang.Integer.TYPE -> 0
        java.lang.Long.TYPE -> 0L
        java.lang.Float.TYPE -> 0f
        else -> null
    }
}
