package org.siloserver.silo.common.player

import okhttp3.ConnectionPool
import okhttp3.Dispatcher
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import org.siloserver.silo.network.isSameHttpOrigin
import java.util.concurrent.TimeUnit

/**
 * Pooled OkHttp transport used by Media3 and authenticated reader requests.
 *
 * Lives as a Koin singleton so the underlying connection pool, dispatcher,
 * and HTTP/2 session survive across playback sessions. Keep it separate from
 * the app-level Ktor client — this one only serves media; the auth/refresh
 * chain on the Ktor client must not see media traffic. Media3 auth lives above
 * this transport in [RefreshingHttpDataSource].
 */
internal fun buildPlayerOkHttpClient(): OkHttpClient =
    OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .addNetworkInterceptor { chain ->
            val initialTarget = chain.call().request().url.toString()
            val networkRequest = chain.request()
            val safeRequest = if (isSameHttpOrigin(initialTarget, networkRequest.url.toString())) {
                networkRequest
            } else {
                networkRequest.withoutCrossOriginCredentials()
            }
            chain.proceed(safeRequest)
        }
        .protocols(listOf(Protocol.HTTP_2, Protocol.HTTP_1_1))
        .connectionPool(ConnectionPool(maxIdleConnections = 5, keepAliveDuration = 5, timeUnit = TimeUnit.MINUTES))
        .dispatcher(Dispatcher())
        .build()

private fun Request.withoutCrossOriginCredentials(): Request =
    newBuilder()
        .removeHeader("Authorization")
        .removeHeader("X-Profile-Id")
        .removeHeader("X-Profile-Token")
        .apply {
            headers.names()
                .filter { name -> name.startsWith("X-Silo-", ignoreCase = true) }
                .forEach(::removeHeader)
        }
        .build()

/**
 * Bounded bootstrap client for the token-refresh RPC. It intentionally has no
 * auth interceptor so a rejected refresh cannot recurse through the media
 * authentication path.
 */
internal fun buildPlayerRefreshOkHttpClient(): OkHttpClient =
    OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .callTimeout(20, TimeUnit.SECONDS)
        .build()
