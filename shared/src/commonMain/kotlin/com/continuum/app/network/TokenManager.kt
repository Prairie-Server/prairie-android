package com.continuum.app.network

import kotlinx.coroutines.flow.SharedFlow

/**
 * Manages JWT access and refresh tokens.
 * Implementation provided by Agent 2 in TokenManagerImpl.kt.
 */
interface TokenManager {
    suspend fun getAccessToken(): String?
    suspend fun getRefreshToken(): String?
    suspend fun saveTokens(accessToken: String, refreshToken: String, expiresIn: Long)
    suspend fun clearTokens()

    /**
     * Clear tokens AND signal [sessionExpired] so observers (typically the
     * root NavHost) can route the user back to the login screen. Call this
     * from paths where the server has invalidated the session — e.g., a
     * refresh that returned 401 with no way to recover short of user
     * re-auth.
     *
     * For user-initiated sign-out (from Settings), keep calling
     * [clearTokens] directly and drive the navigation explicitly from the
     * caller — [sessionExpired] is reserved for the involuntary case so
     * the two flows don't race to issue navigations.
     */
    suspend fun invalidateSession()

    /**
     * Emits [Unit] each time [invalidateSession] runs. Does NOT fire for
     * plain [clearTokens] calls — manual signout flows own their own nav.
     *
     * Subscribers should use `collect { }` inside a `LaunchedEffect` at
     * the root of the nav graph. The flow is a replayless hot stream,
     * so late subscribers won't see past invalidations — on app startup,
     * `MainTvActivity.resolveStartDestination` already handles the
     * "tokens absent" case by routing to Login.
     */
    val sessionExpired: SharedFlow<Unit>

    suspend fun getProfileId(): String?
    suspend fun setProfileId(profileId: String?)
    suspend fun getProfileToken(): String?
    suspend fun setProfileToken(token: String?)
    suspend fun getServerUrl(): String
    suspend fun setServerUrl(url: String)

    // ----- Multi-server -----

    /**
     * Returns the id of the server whose tokens this manager is currently
     * reading/writing. Null when no server has been configured yet (or when
     * the implementation doesn't track per-server state — the in-memory
     * commonMain impl returns null).
     *
     * Used by the auth interceptor to detect a server switch that happened
     * mid-refresh: if the id changes between request start and response
     * receipt, the response is for a stale server and must not be persisted
     * to the new server's token slot.
     */
    suspend fun getCurrentServerId(): String?

    /**
     * Retarget this manager at [serverId]. Subsequent reads/writes load from
     * and persist to that server's slot. Pass `null` to detach (no active
     * server — used during full sign-out / fresh setup).
     *
     * Implementations should flush any in-memory cache so the next
     * [getAccessToken]/[getRefreshToken] re-reads from the new slot.
     */
    suspend fun switchActiveServer(serverId: String?)

    /** Wipe just the active server's tokens + profile state, keeping the registry entry. */
    suspend fun signOutCurrentServer()
}
