package com.continuum.app.network

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

/**
 * In-memory implementation of [TokenManager] with coroutine-safe access.
 *
 * Stores JWT tokens, profile state, and server URL. Uses [Mutex] to ensure
 * thread-safe reads and writes across coroutines.
 *
 * Note: Tokens are not persisted across app restarts in this implementation.
 * A platform-specific persistent implementation (DataStore on Android,
 * Keychain on iOS) can be substituted via Koin.
 */
class TokenManagerImpl : TokenManager {

    private val mutex = Mutex()
    private val timeSource = TimeSource.Monotonic

    private var accessToken: String? = null
    private var refreshToken: String? = null
    private var tokenExpiry: TimeSource.Monotonic.ValueTimeMark? = null

    private var profileId: String? = null
    private var profileToken: String? = null

    private var serverUrl: String = "http://localhost:8090"

    // extraBufferCapacity=1 + DROP_OLDEST makes `tryEmit` from a non-suspend
    // caller always succeed without backpressure — we never care about
    // dropping a duplicate invalidation event, since the observer's
    // effect (route to Login, clear persisted tokens) is idempotent.
    private val _sessionExpired = MutableSharedFlow<Unit>(
        replay = 0,
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    override val sessionExpired: SharedFlow<Unit> = _sessionExpired.asSharedFlow()

    override suspend fun getAccessToken(): String? = mutex.withLock {
        accessToken
    }

    override suspend fun getRefreshToken(): String? = mutex.withLock {
        refreshToken
    }

    override suspend fun saveTokens(accessToken: String, refreshToken: String, expiresIn: Long) {
        mutex.withLock {
            this.accessToken = accessToken
            this.refreshToken = refreshToken
            this.tokenExpiry = timeSource.markNow() + expiresIn.seconds
        }
    }

    override suspend fun clearTokens() {
        mutex.withLock {
            accessToken = null
            refreshToken = null
            tokenExpiry = null
            profileId = null
            profileToken = null
        }
    }

    override suspend fun invalidateSession() {
        clearTokens()
        // Non-suspending emit so this method can be called from anywhere
        // without caller cooperation. DROP_OLDEST buffer means a rapid
        // succession of invalidations collapses into a single observer
        // tick — fine since the observer's nav is idempotent.
        _sessionExpired.tryEmit(Unit)
    }

    override suspend fun getProfileId(): String? = mutex.withLock {
        profileId
    }

    override suspend fun setProfileId(profileId: String?) {
        mutex.withLock {
            this.profileId = profileId
        }
    }

    override suspend fun getProfileToken(): String? = mutex.withLock {
        profileToken
    }

    override suspend fun setProfileToken(token: String?) {
        mutex.withLock {
            this.profileToken = token
        }
    }

    override suspend fun getServerUrl(): String = mutex.withLock {
        serverUrl
    }

    override suspend fun setServerUrl(url: String) {
        mutex.withLock {
            this.serverUrl = url.trimEnd('/')
        }
    }

    // The in-memory impl is single-server; multi-server methods are no-ops.
    // The Android impl ([com.continuum.app.network.EncryptedTokenManagerImpl])
    // is what the apps actually run with.
    override suspend fun getCurrentServerId(): String? = null
    override suspend fun switchActiveServer(serverId: String?) { /* no-op */ }
    override suspend fun signOutCurrentServer() {
        clearTokens()
    }
}
