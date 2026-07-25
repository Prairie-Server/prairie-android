package org.prairieserver.prairie.di

import org.prairieserver.prairie.domain.GetHomeDataUseCase
import org.prairieserver.prairie.domain.ManagePlaybackUseCase
import org.prairieserver.prairie.domain.MediaActionsCoordinator
import org.prairieserver.prairie.model.feature.LiveTvFeatureStore
import org.prairieserver.prairie.model.feature.RequestsFeatureStore
import org.prairieserver.prairie.repository.AdminRepository
import org.prairieserver.prairie.repository.LiveTvRepository
import org.prairieserver.prairie.repository.AuthRepository
import org.prairieserver.prairie.repository.CalendarRepository
import org.prairieserver.prairie.repository.DeviceLoginRepository
import org.prairieserver.prairie.repository.CatalogRepository
import org.prairieserver.prairie.repository.CollectionRepository
import org.prairieserver.prairie.repository.DownloadsRepository
import org.prairieserver.prairie.repository.EbookReaderRepository
import org.prairieserver.prairie.repository.SubtitlesRepository
import org.prairieserver.prairie.repository.LibraryPlaybackPrefsRepository
import org.prairieserver.prairie.repository.NotificationsRepository
import org.prairieserver.prairie.repository.PersonalDataRepository
import org.prairieserver.prairie.repository.PlaybackRepository
import org.prairieserver.prairie.repository.ProfileRepository
import org.prairieserver.prairie.repository.PushRegistrationRepository
import org.prairieserver.prairie.repository.RecommendationRepository
import org.prairieserver.prairie.repository.RequestsRepository
import org.prairieserver.prairie.repository.SectionRepository
import org.prairieserver.prairie.repository.SettingsRepository
import org.prairieserver.prairie.repository.WatchTogetherRepository
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
    single { CatalogRepository(get(), getOrNull<org.prairieserver.prairie.repository.port.CatalogCachePort>() ?: org.prairieserver.prairie.repository.port.NoOpCatalogCachePort) }
    single { CalendarRepository(get()) }
    single { PlaybackRepository(get()) }
    // `getOrNull()` picks up the Room-backed ports when the Android platform
    // module binds them (Track B local-first writes + offline read cache); falls
    // back to the network-only no-op ports in commonMain tests / when unbound.
    single {
        PersonalDataRepository(
            get(),
            getOrNull<org.prairieserver.prairie.repository.port.UserItemStatePort>() ?: org.prairieserver.prairie.repository.port.NoOpUserItemStatePort,
            getOrNull<org.prairieserver.prairie.repository.port.CatalogCachePort>() ?: org.prairieserver.prairie.repository.port.NoOpCatalogCachePort,
        )
    }
    single { ProfileRepository(get(), get(), getOrNull(), get(), get(), get()) }
    single { CollectionRepository(get()) }
    single { SectionRepository(get(), getOrNull<org.prairieserver.prairie.repository.port.CatalogCachePort>() ?: org.prairieserver.prairie.repository.port.NoOpCatalogCachePort) }
    single { RecommendationRepository(get()) }
    single { RequestsRepository(get()) }
    single { RequestsFeatureStore(get()) }
    single { LiveTvRepository(get()) }
    single { LiveTvFeatureStore(get()) }
    single { org.prairieserver.prairie.repository.MetadataAiRepository(get()) }
    single { org.prairieserver.prairie.model.feature.MetadataAiFeatureStore(get()) }
    single { org.prairieserver.prairie.repository.HomeRealtimeCoordinator(get(), get()) }
    single { SettingsRepository(get()) }
    single { LibraryPlaybackPrefsRepository(get()) }
    single { DownloadsRepository(get(), getOrNull<org.prairieserver.prairie.repository.port.DownloadDeletionPort>() ?: org.prairieserver.prairie.repository.port.NoOpDownloadDeletionPort) }
    single { EbookReaderRepository(get()) }
    single { SubtitlesRepository(get()) }
    single { AdminRepository(get()) }
    single { PushRegistrationRepository(get()) }

    // REST-backed inbox state plus a realtime factory that builds the default
    // websocket client from the shared HttpClient + NotificationsApi. The
    // factory is lazy so a connection is only minted when connectRealtime() runs.
    single {
        NotificationsRepository(
            api = get(),
            realtimeFactory = {
                org.prairieserver.prairie.network.DefaultNotificationsRealtimeClient(
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
                org.prairieserver.prairie.network.DefaultWatchTogetherRealtimeClient(
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
    factory<org.prairieserver.prairie.network.PlaybackRealtimeClient> {
        org.prairieserver.prairie.network.DefaultPlaybackRealtimeClient(
            client = get(),
            tokenManager = get(),
        )
    }

    // Domain use cases
    single { GetHomeDataUseCase(get(), get()) }
    single { ManagePlaybackUseCase(get(), get()) }
    single { MediaActionsCoordinator(get()) }
}
