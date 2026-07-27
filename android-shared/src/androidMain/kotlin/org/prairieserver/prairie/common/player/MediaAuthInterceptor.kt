package org.prairieserver.prairie.common.player

import org.prairieserver.prairie.network.TokenManager
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.prairieserver.prairie.network.isSameHttpOrigin

/**
 * OkHttp interceptor that mirrors [org.prairieserver.prairie.network.PrairieAuthPlugin]
 * for the media path. A long HDR film outlives the JWT expiry, so without this
 * a mid-segment 401 would surface as a playback error instead of a transparent
 * retry.
 *
 * Semantics:
 * 1. Inject `Authorization: Bearer <token>` plus active profile headers from
 *    [MediaAuthSession] on every request.
 * 2. On a 401, ask the shared session to single-flight a refresh so N parallel
 *    401s from readers and Media3 collapse into ONE refresh.
 * 3. Retry the original request once with the refreshed token.
 *
 * Media3 uses the same session through [RefreshingHttpDataSource], keeping
 * these semantics intact if its underlying transport changes.
 */
class MediaAuthInterceptor(
    private val authSession: MediaAuthSession,
) : Interceptor {
    constructor(
        tokenManager: TokenManager,
        refreshClient: OkHttpClient,
        json: Json = Json { ignoreUnknownKeys = true },
    ) : this(MediaAuthSession(tokenManager, refreshClient, json))

    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        val failedSnapshot = runBlocking { authSession.snapshot() }
        if (!isSameHttpOrigin(failedSnapshot.serverUrl, original.url.toString())) {
            return chain.proceed(original.withoutSiloCredentials())
        }

        val authed = original.newBuilder()
            .applyAuthHeaders(failedSnapshot)
            .build()

        val response = chain.proceed(authed)
        if (response.code != 401) return response

        // Single-flight refresh — inside the mutex, double-check the token
        // snapshot. If another thread already refreshed, we skip straight to
        // retry with the fresh token.
        response.close()
        val refreshed = runBlocking { authSession.refreshIfStale(failedSnapshot) }

        if (!refreshed) {
            // Build a fresh response since the original has been consumed.
            return chain.proceed(authed)
        }

        val retrySnapshot = runBlocking { authSession.snapshot() }
        val retried = if (isSameHttpOrigin(retrySnapshot.serverUrl, original.url.toString())) {
            original.newBuilder()
                .applyAuthHeaders(retrySnapshot)
                .build()
        } else {
            original.withoutSiloCredentials()
        }
        return chain.proceed(retried)
    }

    private fun Request.Builder.applyAuthHeaders(snapshot: MediaAuthSnapshot): Request.Builder {
        snapshot.asRequestHeaders().forEach { (name, value) -> header(name, value) }
        return this
    }
}

private fun Request.withoutSiloCredentials(): Request =
    newBuilder()
        .removeHeader("Authorization")
        .removeHeader("X-Profile-Id")
        .removeHeader("X-Profile-Token")
        .apply {
            headers.names()
                .filter { name -> name.startsWith("X-Prairie-", ignoreCase = true) }
                .forEach(::removeHeader)
        }
        .build()
