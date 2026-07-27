package org.siloserver.silo.network

import android.content.SharedPreferences
import java.lang.reflect.Proxy
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.runTest
import org.siloserver.silo.model.server.ServerEntry
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

    private class FakeServerRegistry : ServerRegistry {
        private val entry = ServerEntry(id = "server-a", url = "https://server-a.example")
        override val entries: StateFlow<List<ServerEntry>> = MutableStateFlow(listOf(entry))
        override val activeServerId: StateFlow<String?> = MutableStateFlow(entry.id)
        override val activeEntry: StateFlow<ServerEntry?> = MutableStateFlow(entry)
        override suspend fun addOrUpdate(url: String, fetchedName: String?): String = entry.id
        override suspend fun rename(serverId: String, userOverrideName: String?) = Unit
        override suspend fun setFetchedName(serverId: String, fetchedName: String?) = Unit
        override suspend fun setProfileId(serverId: String, profileId: String?) = Unit
        override suspend fun remove(serverId: String) = Unit
        override suspend fun signOut(serverId: String) = Unit
        override suspend fun switchTo(serverId: String) = Unit
        override suspend fun touchActive() = Unit
    }

    private fun inMemoryPreferences(): SharedPreferences {
        val values = mutableMapOf<String, Any?>()
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
