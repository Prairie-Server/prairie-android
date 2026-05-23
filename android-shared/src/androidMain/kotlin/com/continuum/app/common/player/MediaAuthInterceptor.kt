package com.continuum.app.common.player

import com.continuum.app.model.auth.RefreshRequest
import com.continuum.app.model.auth.RefreshResponse
import com.continuum.app.network.TokenManager
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
 * OkHttp interceptor that mirrors [com.continuum.app.network.ContinuumAuthPlugin]
 * for the media path. A long HDR film outlives the JWT expiry, so without this
 * a mid-segment 401 would surface as a playback error instead of a transparent
 * retry.
 *
 * Semantics:
 * 1. Inject `Authorization: Bearer <token>` from [TokenManager] on every request.
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

        val authed = if (tokenBeforeRequest.isNullOrBlank()) {
            original
        } else {
            original.newBuilder()
                .header("Authorization", "Bearer $tokenBeforeRequest")
                .build()
        }

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
                    attemptRefresh()
                }
            }
        }

        if (!refreshed) {
            // Build a fresh response since the original has been consumed.
            return chain.proceed(authed)
        }

        val newToken = runBlocking { tokenManager.getAccessToken() }.orEmpty()
        val retried = original.newBuilder()
            .header("Authorization", "Bearer $newToken")
            .build()
        return chain.proceed(retried)
    }

    private suspend fun attemptRefresh(): Boolean {
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
                if (!resp.isSuccessful) {
                    // Refresh itself 401ed or 5xx'd — clear tokens so the
                    // UI gets bounced to login on its next 401.
                    tokenManager.clearTokens()
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
