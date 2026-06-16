package com.continuum.app.network

import android.content.SharedPreferences
import com.continuum.app.network.AndroidServerRegistry.Companion.serverScopedKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Persistent [TokenManager] backed by EncryptedSharedPreferences with per-server
 * isolation.
 *
 * All token + profile values are stored under keys of the form
 * "<serverId>.<baseKey>" (see [AndroidServerRegistry.serverScopedKey]) so each
 * saved server keeps its own credentials. The active server is whatever the
 * [ServerRegistry] currently has selected — this manager mirrors that
 * selection into its in-memory cache and listens for switches so subsequent
 * reads target the right slot.
 *
 * Mirrors the iOS multi-server `TokenStore` semantics: switching server flushes
 * the in-memory cache, and the auth interceptor's refresh path checks
 * [getCurrentServerId] before saving the response so a stale 401-refresh that
 * lands after a switch can't leak tokens into the new server's slot.
 */
class EncryptedTokenManagerImpl(
    private val prefs: SharedPreferences,
    private val registry: ServerRegistry,
) : TokenManager {

    private val mutex = Mutex()
    private val scope = CoroutineScope(Dispatchers.Default)

    private var activeServerId: String? = registry.activeServerId.value

    // Cached values for the active server. Refilled on every server switch.
    private var accessToken: String? = null
    private var refreshToken: String? = null
    private var tokenExpiryEpochMs: Long? = null
    private var profileId: String? = null
    private var profileToken: String? = null

    private val _sessionExpired = MutableSharedFlow<Unit>(
        replay = 0,
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    override val sessionExpired: SharedFlow<Unit> = _sessionExpired.asSharedFlow()

    init {
        // Load initial cache for whichever server the registry made active
        // (post-migration / from disk).
        runBlocking { reloadCacheLocked() }

        // Observe future registry switches — UI flows that call
        // ServerRegistry.switchTo() outside switchActiveServer() are still
        // picked up here. The cache is flushed under the same mutex used by
        // every read/write so requests in flight see consistent state.
        scope.launch {
            registry.activeServerId.collectLatest { id ->
                mutex.withLock {
                    if (id != activeServerId) {
                        activeServerId = id
                        reloadCacheUnsynchronized()
                    }
                }
            }
        }
    }

    // ---- Token reads/writes (always against the active server) ----

    override suspend fun getAccessToken(): String? = mutex.withLock { accessToken }
    override suspend fun getRefreshToken(): String? = mutex.withLock { refreshToken }

    override suspend fun saveTokens(accessToken: String, refreshToken: String, expiresIn: Long) {
        mutex.withLock {
            val serverId = activeServerId ?: return  // No active server — drop on the floor.
            this.accessToken = accessToken
            this.refreshToken = refreshToken
            val expiryEpochMs = System.currentTimeMillis() + expiresIn * 1000L
            this.tokenExpiryEpochMs = expiryEpochMs
            prefs.edit()
                .putString(serverScopedKey(serverId, KEY_ACCESS_TOKEN), accessToken)
                .putString(serverScopedKey(serverId, KEY_REFRESH_TOKEN), refreshToken)
                .putLong(serverScopedKey(serverId, KEY_TOKEN_EXPIRY), expiryEpochMs)
                .apply()
        }
    }

    override suspend fun clearTokens() {
        mutex.withLock {
            val serverId = activeServerId
            accessToken = null
            refreshToken = null
            tokenExpiryEpochMs = null
            profileId = null
            profileToken = null
            if (serverId != null) {
                prefs.edit()
                    .remove(serverScopedKey(serverId, KEY_ACCESS_TOKEN))
                    .remove(serverScopedKey(serverId, KEY_REFRESH_TOKEN))
                    .remove(serverScopedKey(serverId, KEY_TOKEN_EXPIRY))
                    .remove(serverScopedKey(serverId, KEY_PROFILE_ID))
                    .remove(serverScopedKey(serverId, KEY_PROFILE_TOKEN))
                    .apply()
            }
        }
    }

    override suspend fun invalidateSession() {
        clearTokens()
        _sessionExpired.tryEmit(Unit)
    }

    override suspend fun getProfileId(): String? = mutex.withLock { profileId }

    override suspend fun setProfileId(profileId: String?) {
        mutex.withLock {
            val serverId = activeServerId ?: return
            if (this.profileId == profileId) return
            this.profileId = profileId
            val key = serverScopedKey(serverId, KEY_PROFILE_ID)
            prefs.edit().apply {
                if (profileId == null) remove(key) else putString(key, profileId)
            }.apply()
        }
    }

    override suspend fun getProfileToken(): String? = mutex.withLock { profileToken }

    override suspend fun setProfileToken(token: String?) {
        mutex.withLock {
            val serverId = activeServerId ?: return
            if (this.profileToken == token) return
            this.profileToken = token
            val key = serverScopedKey(serverId, KEY_PROFILE_TOKEN)
            prefs.edit().apply {
                if (token == null) remove(key) else putString(key, token)
            }.apply()
        }
    }

    override suspend fun getServerUrl(): String =
        registry.activeEntry.value?.url.orEmpty()

    /**
     * Adds-or-updates [url] in the registry and switches to it. Used by the
     * server-setup flow (one-server-at-a-time entry) — multi-server-aware
     * call sites should go through [ServerRegistry.addOrUpdate] /
     * [ServerRegistry.switchTo] directly.
     */
    override suspend fun setServerUrl(url: String) {
        val newId = registry.addOrUpdate(url)
        registry.switchTo(newId)
        // Defer cache reload to switchActiveServer so the registry observer
        // and this call can't race into two reloads — switchActiveServer's
        // existing no-op guard makes whichever runs second a no-op.
        switchActiveServer(newId)
    }

    // ---- Multi-server controls ----

    override suspend fun getCurrentServerId(): String? = mutex.withLock { activeServerId }

    override suspend fun switchActiveServer(serverId: String?) {
        mutex.withLock {
            if (activeServerId == serverId) return
            activeServerId = serverId
            reloadCacheUnsynchronized()
        }
    }

    override suspend fun signOutCurrentServer() {
        clearTokens()
    }

    // ---- Scoped auth (pinned background requests) ----

    override suspend fun snapshotCurrentScope(): AuthScopeSnapshot? = mutex.withLock {
        val serverId = activeServerId ?: return@withLock null
        // Resolve the URL for *this* serverId from the registry entries so the
        // snapshot is internally consistent. Do NOT fall back to activeEntry —
        // if the captured serverId isn't a known, non-blank-URL entry, a pinned
        // request could be sent to the wrong server (or skip the rewrite and hit
        // localhost). Return null instead so the drain simply no-ops this pass.
        val url = registry.entries.value.firstOrNull { it.id == serverId }?.url
        if (url.isNullOrBlank()) return@withLock null
        AuthScopeSnapshot(
            serverId = serverId,
            profileId = profileId,
            serverUrl = url,
            profileToken = profileToken,
        )
    }

    override suspend fun getAccessTokenForScope(serverId: String): String? = mutex.withLock {
        if (serverId == activeServerId) accessToken
        else prefs.getString(serverScopedKey(serverId, KEY_ACCESS_TOKEN), null)
    }

    override suspend fun getRefreshTokenForScope(serverId: String): String? = mutex.withLock {
        if (serverId == activeServerId) refreshToken
        else prefs.getString(serverScopedKey(serverId, KEY_REFRESH_TOKEN), null)
    }

    override suspend fun saveTokensForScope(
        serverId: String,
        accessToken: String,
        refreshToken: String,
        expiresIn: Long,
    ) {
        mutex.withLock {
            val expiryEpochMs = System.currentTimeMillis() + expiresIn * 1000L
            prefs.edit()
                .putString(serverScopedKey(serverId, KEY_ACCESS_TOKEN), accessToken)
                .putString(serverScopedKey(serverId, KEY_REFRESH_TOKEN), refreshToken)
                .putLong(serverScopedKey(serverId, KEY_TOKEN_EXPIRY), expiryEpochMs)
                .apply()
            // Keep the in-memory cache coherent if we just refreshed the active server.
            if (serverId == activeServerId) {
                this.accessToken = accessToken
                this.refreshToken = refreshToken
                this.tokenExpiryEpochMs = expiryEpochMs
            }
        }
    }

    // ---- Internal cache management ----

    private suspend fun reloadCacheLocked() {
        mutex.withLock { reloadCacheUnsynchronized() }
    }

    private fun reloadCacheUnsynchronized() {
        val serverId = activeServerId
        if (serverId == null) {
            accessToken = null
            refreshToken = null
            tokenExpiryEpochMs = null
            profileId = null
            profileToken = null
            return
        }
        accessToken = prefs.getString(serverScopedKey(serverId, KEY_ACCESS_TOKEN), null)
        refreshToken = prefs.getString(serverScopedKey(serverId, KEY_REFRESH_TOKEN), null)
        val expiryKey = serverScopedKey(serverId, KEY_TOKEN_EXPIRY)
        tokenExpiryEpochMs = if (prefs.contains(expiryKey)) prefs.getLong(expiryKey, 0L) else null
        profileId = prefs.getString(serverScopedKey(serverId, KEY_PROFILE_ID), null)
        profileToken = prefs.getString(serverScopedKey(serverId, KEY_PROFILE_TOKEN), null)
    }

    internal companion object {
        const val KEY_ACCESS_TOKEN = "access_token"
        const val KEY_REFRESH_TOKEN = "refresh_token"
        const val KEY_TOKEN_EXPIRY = "token_expiry_epoch_ms"
        const val KEY_PROFILE_ID = "profile_id"
        const val KEY_PROFILE_TOKEN = "profile_token"
        // Retained only so [AndroidServerRegistry.migrateLegacyIfNeeded] can
        // strip the pre-multi-server unprefixed key off disk.
        const val KEY_SERVER_URL = "server_url"
    }
}
