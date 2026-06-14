package com.continuum.app.network

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.api.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import com.continuum.app.model.auth.RefreshRequest
import com.continuum.app.model.auth.RefreshResponse

/**
 * Configuration for the [ContinuumAuthPlugin].
 */
class ContinuumAuthConfig {
    /**
     * The [TokenManager] used to read/write tokens and profile state.
     */
    var tokenManager: TokenManager? = null
    var deviceMetadataProvider: DeviceMetadataProvider? = null
}

/**
 * Ktor client plugin that handles Continuum authentication.
 *
 * Before each request:
 * - Attaches `Authorization: Bearer <token>` if an access token is available
 * - Attaches `X-Profile-Id` header if a profile is selected
 * - Attaches `X-Profile-Token` header if present
 *
 * On 401 response:
 * - Attempts a token refresh using the stored refresh token
 * - Saves new tokens on success
 * - Retries the original request once with the new access token
 * - Uses a [Mutex] to prevent concurrent refresh attempts, and a double-check
 *   inside the mutex so that N parallel 401s only trigger ONE refresh.
 */
val ContinuumAuthPlugin = createClientPlugin("ContinuumAuthPlugin", ::ContinuumAuthConfig) {
    val tokenManager = pluginConfig.tokenManager
        ?: error("TokenManager must be provided to ContinuumAuthPlugin")
    val deviceMetadataProvider = pluginConfig.deviceMetadataProvider

    val refreshMutex = Mutex()

    onRequest { request, _ ->
        // Shared API calls use relative paths; bind them to the configured server URL
        // before the request is sent so Ktor doesn't fall back to localhost on iOS.
        if (request.url.encodedPath.startsWith("/api/") && request.url.host == "localhost") {
            val serverUrl = tokenManager.getServerUrl()
            if (serverUrl.isNotBlank()) {
                val originalPath = request.url.encodedPath
                val originalParameters = request.url.parameters.build()
                val originalFragment = request.url.fragment

                request.url.takeFrom(serverUrl)
                request.url.encodedPath = originalPath
                request.url.parameters.clear()
                request.url.parameters.appendAll(originalParameters)
                request.url.fragment = originalFragment
            }
        }

        // Skip auth headers for the refresh endpoint itself to avoid recursion
        val isRefreshRequest = request.url.encodedPath.endsWith("/auth/refresh")
        if (isRefreshRequest) return@onRequest

        tokenManager.getAccessToken()?.let { token ->
            request.header(HttpHeaders.Authorization, "Bearer $token")
        }

        if (!request.headers.contains("X-Profile-Id")) {
            tokenManager.getProfileId()?.let { profileId ->
                request.header("X-Profile-Id", profileId)
            }
        }

        tokenManager.getProfileToken()?.let { profileToken ->
            request.header("X-Profile-Token", profileToken)
        }

        deviceMetadataProvider?.current()?.let { device ->
            request.header("X-Silo-Device-Id", device.id)
            request.header("X-Silo-Device-Name", device.name)
            request.header("X-Silo-Device-Platform", device.platform)
        }
    }

    on(Send) { request ->
        // Capture the access token we will actually SEND with this request.
        // If the response comes back 401, we compare against this snapshot
        // inside the refresh mutex to detect a concurrent refresh that
        // already happened — so N parallel 401s collapse into ONE refresh.
        val tokenBeforeRequest = tokenManager.getAccessToken()

        val originalCall = proceed(request)

        // Only attempt refresh on 401 for non-auth endpoints
        if (originalCall.response.status != HttpStatusCode.Unauthorized) {
            return@on originalCall
        }

        val requestPath = originalCall.request.url.encodedPath
        if (requestPath.endsWith("/auth/refresh") || requestPath.endsWith("/auth/login")) {
            return@on originalCall
        }

        // Capture the server id as well so we can detect a mid-refresh server
        // switch — without this, a 401-refresh kicked off against server A
        // could land after the user has switched to server B and write A's
        // freshly-issued tokens into B's slot.
        val serverIdBeforeRequest = tokenManager.getCurrentServerId()

        // Attempt token refresh with mutex to prevent concurrent refreshes.
        // The mutex guarantees that only one coroutine refreshes at a time;
        // the double-check guarantees that only one coroutine HITS the network
        // for the refresh — subsequent waiters observe the already-refreshed
        // token and skip straight to retry.
        val refreshed = refreshMutex.withLock {
            // If the user switched servers between request-send and 401-retry,
            // we are now operating against a different server. Don't try to
            // "refresh" — the refresh token wouldn't be valid for the new
            // server anyway, and we'd risk persisting cross-server tokens.
            val serverIdNow = tokenManager.getCurrentServerId()
            if (serverIdNow != serverIdBeforeRequest) {
                return@withLock false
            }

            val tokenNow = tokenManager.getAccessToken()
            if (tokenNow != null && tokenNow != tokenBeforeRequest) {
                // Another coroutine already refreshed while we were waiting —
                // just retry the original request with the new token.
                return@withLock true
            }

            val refreshToken = tokenManager.getRefreshToken()
            if (refreshToken.isNullOrBlank()) {
                return@withLock false
            }

            try {
                val serverUrl = tokenManager.getServerUrl()
                if (serverUrl.isBlank()) {
                    return@withLock false
                }

                val refreshResponse = client.post("$serverUrl/api/v1/auth/refresh") {
                    contentType(ContentType.Application.Json)
                    setBody(RefreshRequest(refreshToken))
                }

                // Re-check serverId AFTER the network call as well — the user
                // could have switched while we were waiting on the network.
                // The token write below targets whichever server is active at
                // save time, so a mismatch here means we'd write to the wrong
                // slot.
                val serverIdAfterCall = tokenManager.getCurrentServerId()
                if (serverIdAfterCall != serverIdBeforeRequest) {
                    return@withLock false
                }

                if (refreshResponse.status.isSuccess()) {
                    val tokens = refreshResponse.body<RefreshResponse>()
                    tokenManager.saveTokens(
                        accessToken = tokens.accessToken,
                        refreshToken = tokens.refreshToken,
                        expiresIn = tokens.expiresIn
                    )
                    true
                } else {
                    // The [TokenManager.sessionExpired] event emitted by this
                    // call is what the root NavHost observer uses to route the
                    // user back to the login screen; without it, the UI would
                    // stay on Home and keep rendering "Failed to load..." for
                    // every subsequent API call that now has no credentials.
                    tokenManager.invalidateSession()
                    false
                }
            } catch (e: Throwable) {
                false
            }
        }

        if (refreshed) {
            // Explicitly replace the Authorization header on the request builder
            // before retrying. `proceed(request)` re-enters the pipeline at Send —
            // the `onRequest` hook (which originally attached the old token) does
            // NOT run a second time, so if we don't update the header here the
            // retry gets sent with the expired Bearer token and the server
            // returns another 401.
            val newAccessToken = tokenManager.getAccessToken()
            if (newAccessToken != null) {
                request.headers.remove(HttpHeaders.Authorization)
                request.header(HttpHeaders.Authorization, "Bearer $newAccessToken")
            }
            proceed(request)
        } else {
            originalCall
        }
    }
}
