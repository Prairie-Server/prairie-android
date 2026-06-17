package com.continuum.app.di

import com.continuum.app.domain.GetHomeDataUseCase
import com.continuum.app.domain.ManagePlaybackUseCase
import com.continuum.app.domain.MediaActionsCoordinator
import com.continuum.app.repository.AdminRepository
import com.continuum.app.repository.AuthRepository
import com.continuum.app.repository.CalendarRepository
import com.continuum.app.repository.DeviceLoginRepository
import com.continuum.app.repository.CatalogRepository
import com.continuum.app.repository.CollectionRepository
import com.continuum.app.repository.DownloadsRepository
import com.continuum.app.repository.EbookReaderRepository
import com.continuum.app.repository.SubtitlesRepository
import com.continuum.app.repository.LibraryPlaybackPrefsRepository
import com.continuum.app.repository.NotificationsRepository
import com.continuum.app.repository.PersonalDataRepository
import com.continuum.app.repository.PlaybackRepository
import com.continuum.app.repository.ProfileRepository
import com.continuum.app.repository.RecommendationRepository
import com.continuum.app.repository.RequestsRepository
import com.continuum.app.repository.SectionRepository
import com.continuum.app.repository.SettingsRepository
import com.continuum.app.repository.WatchTogetherRepository
import org.koin.dsl.module

/**
 * Koin module providing all repository and domain use case instances.
 *
 * Dependencies:
 * - API classes (AuthApi, CatalogApi, etc.) from networkModule (Agent 2)
 * - TokenManager from networkModule (Agent 2)
 *
 * All repositories are singletons; they are stateless wrappers around API classes
 * and TokenManager, so sharing instances is safe and efficient.
 */
val repositoryModule = module {
    // Repositories — `getOrNull()` for ServerRegistry / HealthApi keeps these
    // working when the multi-server platform binding isn't installed
    // (commonMain tests, hypothetical iOS reuse). Both repos no-op the
    // multi-server side effects when the registry is null.
    single { AuthRepository(get(), get(), getOrNull(), getOrNull()) }
    single { DeviceLoginRepository(get()) }
    single { CatalogRepository(get(), getOrNull<com.continuum.app.repository.port.CatalogCachePort>() ?: com.continuum.app.repository.port.NoOpCatalogCachePort) }
    single { CalendarRepository(get()) }
    single { PlaybackRepository(get()) }
    // `getOrNull()` picks up the Room-backed ports when the Android platform
    // module binds them (Track B local-first writes + offline read cache); falls
    // back to the network-only no-op ports in commonMain tests / when unbound.
    single {
        PersonalDataRepository(
            get(),
            getOrNull<com.continuum.app.repository.port.UserItemStatePort>() ?: com.continuum.app.repository.port.NoOpUserItemStatePort,
            getOrNull<com.continuum.app.repository.port.CatalogCachePort>() ?: com.continuum.app.repository.port.NoOpCatalogCachePort,
        )
    }
    single { ProfileRepository(get(), get(), getOrNull(), get(), get()) }
    single { CollectionRepository(get()) }
    single { SectionRepository(get(), getOrNull<com.continuum.app.repository.port.CatalogCachePort>() ?: com.continuum.app.repository.port.NoOpCatalogCachePort) }
    single { RecommendationRepository(get()) }
    single { RequestsRepository(get()) }
    single { SettingsRepository(get()) }
    single { LibraryPlaybackPrefsRepository(get()) }
    single { DownloadsRepository(get(), getOrNull<com.continuum.app.repository.port.DownloadDeletionPort>() ?: com.continuum.app.repository.port.NoOpDownloadDeletionPort) }
    single { EbookReaderRepository(get()) }
    single { SubtitlesRepository(get()) }
    single { AdminRepository(get()) }

    // REST-backed inbox state plus a realtime factory that builds the default
    // websocket client from the shared HttpClient + NotificationsApi. The
    // factory is lazy so a connection is only minted when connectRealtime() runs.
    single {
        NotificationsRepository(
            api = get(),
            realtimeFactory = {
                com.continuum.app.network.DefaultNotificationsRealtimeClient(
                    client = get(),
                    api = get(),
                )
            },
        )
    }

    // One room's snapshot/suggestions state + WS lifecycle. The realtime factory
    // builds the per-room socket client from the shared HttpClient + TokenManager
    // (query-param auth). Lazy so a socket is only minted when connect() runs.
    single {
        WatchTogetherRepository(
            api = get(),
            realtimeFactory = {
                com.continuum.app.network.DefaultWatchTogetherRealtimeClient(
                    client = get(),
                    tokenManager = get(),
                )
            },
        )
    }

    // Per-session playback control socket (admin remote control). Parallel to
    // the watch-together realtime client — same HttpClient + query-param auth.
    // FACTORY, not single: the client holds one mutable socket session, so each
    // player-screen controller must get its own instance (mirrors how the WT
    // repository mints a fresh client per connect) — a shared singleton would
    // let a second player clobber the first's socket.
    factory<com.continuum.app.network.PlaybackRealtimeClient> {
        com.continuum.app.network.DefaultPlaybackRealtimeClient(
            client = get(),
            tokenManager = get(),
        )
    }

    // Domain use cases
    single { GetHomeDataUseCase(get(), get()) }
    single { ManagePlaybackUseCase(get(), get()) }
    single { MediaActionsCoordinator(get()) }
}
