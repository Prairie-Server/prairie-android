package com.continuum.app.common.di

import androidx.media3.common.util.UnstableApi
import com.continuum.app.common.player.MediaAuthInterceptor
import com.continuum.app.common.player.PlaybackAnalyticsListener
import com.continuum.app.common.player.buildPlayerOkHttpClient
import okhttp3.OkHttpClient
import org.koin.core.qualifier.named
import org.koin.dsl.module

/**
 * Shared player DI module. Both `androidApp` and `androidTvApp` must include
 * this via `playerModule` in their Koin startup. Named singletons keep the
 * pooled media OkHttpClient distinct from anything Ktor may expose later.
 */
val PLAYER_OKHTTP_QUALIFIER = named("player-okhttp")

val playerModule = module {
    // Lightweight bootstrap client for the refresh RPC — no interceptors so a
    // 401 on the refresh call can't loop back through MediaAuthInterceptor.
    single(named("player-refresh-okhttp")) { OkHttpClient() }

    single { MediaAuthInterceptor(tokenManager = get(), refreshClient = get(named("player-refresh-okhttp"))) }

    single<OkHttpClient>(PLAYER_OKHTTP_QUALIFIER) {
        buildPlayerOkHttpClient()
            .newBuilder()
            .addInterceptor(get<MediaAuthInterceptor>())
            .build()
    }

    @OptIn(UnstableApi::class)
    single { PlaybackAnalyticsListener() }
}
