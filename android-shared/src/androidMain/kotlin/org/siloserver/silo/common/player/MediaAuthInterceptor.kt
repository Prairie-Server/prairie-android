package org.siloserver.silo.common.player

import org.siloserver.silo.model.auth.RefreshRequest
import org.siloserver.silo.model.auth.RefreshResponse
import org.siloserver.silo.network.TokenManager
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException

/**
 * OkHttp interceptor that mirrors [org.siloserver.silo.network.SiloAuthPlugin]
 * for the media path. A long HDR film outlives the JWT expiry, so without this
 * a mid-segment 401 would surface as a playback error instead of a transparent
 * retry.
 *
 * Semantics:
 * 1. Inject `Authorization: Bearer <token>` plus active profile headers from
 *    [TokenManager] on every request.
 * 2. On a 401, single-flight a refresh via [refreshMutex]; double-check inside
 *    the mutex so N parallel 401s collapse into ONE refresh.
 * 3. Retry the original request once with the refreshed token.
 *
 * The refresh RPC uses a lightweight bootstrap [OkHttpClient] (no interceptors)
 * to avoid recursion — if the refresh itself 401ed, feeding that response back
 * through this interceptor would loop until tokens were cleared.
 */
class MediaAuthInterceptor(
    private val tokenManager: TokenManager,
    private val refreshClient: OkHttpClient = OkHttpClient(),
    private val json: Json = Json { ignoreUnknownKeys = true },
) : Interceptor {

    private val refreshMutex = Mutex()

    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        val tokenBeforeRequest = runBlocking { tokenManager.getAccessToken() }
        val serverIdBeforeRequest = runBlocking { tokenManager.getCurrentServerId() }

        val authed = original.newBuilder()
            .applyAuthHeaders(tokenBeforeRequest)
            .build()

        val response = chain.proceed(authed)
        if (response.code != 401) return response

        // Single-flight refresh — inside the mutex, double-check the token
        // snapshot. If another thread already refreshed, we skip straight to
        // retry with the fresh token.
        response.close()
        val refreshed = runBlocking {
            refreshMutex.withLock {
                val tokenNow = tokenManager.getAccessToken()
                if (tokenNow != null && tokenNow != tokenBeforeRequest) {
                    true
                } else {
                    val serverIdNow = tokenManager.getCurrentServerId()
                    if (serverIdNow != serverIdBeforeRequest) {
                        false
                    } else {
                        attemptRefresh(serverIdBeforeRequest)
                    }
                }
            }
        }

        if (!refreshed) {
            // Build a fresh response since the original has been consumed.
            return chain.proceed(authed)
        }

        val retried = original.newBuilder()
            .applyAuthHeaders(runBlocking { tokenManager.getAccessToken() })
            .build()
        return chain.proceed(retried)
    }

    private fun Request.Builder.applyAuthHeaders(accessToken: String?): Request.Builder {
        if (!accessToken.isNullOrBlank()) {
            header("Authorization", "Bearer $accessToken")
        }
        runBlocking { tokenManager.getProfileId() }?.takeIf { it.isNotBlank() }?.let { profileId ->
            header("X-Profile-Id", profileId)
        }
        runBlocking { tokenManager.getProfileToken() }?.takeIf { it.isNotBlank() }?.let { profileToken ->
            header("X-Profile-Token", profileToken)
        }
        return this
    }

    private suspend fun attemptRefresh(serverIdBeforeRequest: String?): Boolean {
        val refreshToken = tokenManager.getRefreshToken() ?: return false
        val serverUrl = tokenManager.getServerUrl()
        if (refreshToken.isBlank() || serverUrl.isBlank()) return false

        val url = serverUrl.trimEnd('/') + "/api/v1/auth/refresh"
        val body = json.encodeToString(RefreshRequest(refreshToken))
            .toRequestBody("application/json; charset=utf-8".toMediaType())

        val request = Request.Builder()
            .url(url)
            .post(body)
            .build()

        return try {
            refreshClient.newCall(request).execute().use { resp ->
                val serverIdAfterCall = tokenManager.getCurrentServerId()
                if (serverIdAfterCall != serverIdBeforeRequest) {
                    return@use false
                }
                // Signed out while the refresh was in flight (logout revokes
                // the access token server-side before clearTokens() runs, so
                // 401-refreshes race sign-out): drop the response instead of
                // re-persisting a fresh session.
                if (tokenManager.getRefreshToken().isNullOrBlank()) {
                    return@use false
                }
                if (!resp.isSuccessful) {
                    // Only auth rejection proves the refresh token is bad.
                    // Gateway/proxy/server failures should keep the session so
                    // a temporary outage does not sign the user out.
                    if (resp.code.shouldInvalidateSessionAfterRefreshFailure()) {
                        tokenManager.invalidateSession()
                    }
                    return@use false
                }
                val payload = resp.body?.string().orEmpty()
                val tokens = runCatching { json.decodeFromString<RefreshResponse>(payload) }
                    .getOrNull() ?: return@use false
                tokenManager.saveTokens(
                    accessToken = tokens.accessToken,
                    refreshToken = tokens.refreshToken,
                    expiresIn = tokens.expiresIn,
                )
                true
            }
        } catch (_: IOException) {
            false
        }
    }
}

private fun Int.shouldInvalidateSessionAfterRefreshFailure(): Boolean =
    this == 400 || this == 401 || this == 403
