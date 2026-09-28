package org.prairieserver.prairie.di

import org.prairieserver.prairie.domain.MediaActionsCoordinator
import org.prairieserver.prairie.model.feature.LiveTvFeatureStore
import org.prairieserver.prairie.model.feature.RequestsFeatureStore
import org.prairieserver.prairie.repository.LiveTvRepository
import org.prairieserver.prairie.model.profile.ActiveProfileStore
import org.prairieserver.prairie.repository.AuthRepository
import org.prairieserver.prairie.repository.OnboardingRepository
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
import org.prairieserver.prairie.network.TokenManager
import org.prairieserver.prairie.network.api.PlaybackApi
import org.prairieserver.prairie.watchtogether.RoomSession
import org.prairieserver.prairie.watchtogether.WatchTogetherEntryGateway
import org.koin.dsl.module
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

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
    // Repositories — optional multi-server identity dependencies keep these
    // working when the multi-server platform binding isn't installed
    // (commonMain tests, hypothetical iOS reuse). Both repos no-op the
    // multi-server side effects when the registry is null.
    single {
        AuthRepository(
            authApi = get(),
            tokenManager = get(),
            serverRegistry = getOrNull(),
            healthApi = getOrNull(),
            brandingApi = getOrNull(),
            apiV2Probe = getOrNull(),
            // Owns the post-switch display-name refresh so a server-list
            // spinner never waits on branding/health.
            backgroundScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
        )
    }
    single { OnboardingRepository(get()) }
    single { DeviceLoginRepository(get()) }
    single {
        CatalogRepository(
            catalogApi = get(),
            catalogCache = getOrNull<org.prairieserver.prairie.repository.port.CatalogCachePort>()
                ?: org.prairieserver.prairie.repository.port.NoOpCatalogCachePort,
        )
    }
    single { CalendarRepository(get()) }
    single { PlaybackRepository(get(), getOrNull<PlaybackApi>()) }
    // `getOrNull()` picks up the Room-backed ports when the Android platform
    // module binds them (Track B local-first writes + offline read cache); falls
    // back to the network-only no-op ports in commonMain tests / when unbound.
    single {
        PersonalDataRepository(
            personalDataApi = get(),
            membershipPort = get(),
            identityTransitions = get(),
            userItemStatePort = getOrNull<org.prairieserver.prairie.repository.port.UserItemStatePort>()
                ?: org.prairieserver.prairie.repository.port.NoOpUserItemStatePort,
            catalogCache = getOrNull<org.prairieserver.prairie.repository.port.CatalogCachePort>()
                ?: org.prairieserver.prairie.repository.port.NoOpCatalogCachePort,
        )
    }
    single { ProfileRepository(get(), get(), getOrNull(), get(), get(), get()) }
    single { CollectionRepository(get()) }
    single {
        SectionRepository(
            sectionApi = get(),
            catalogCache = getOrNull<org.prairieserver.prairie.repository.port.CatalogCachePort>()
                ?: org.prairieserver.prairie.repository.port.NoOpCatalogCachePort,
        )
    }
    single { RecommendationRepository(get()) }
    single { RequestsRepository(get()) }
    single { RequestsFeatureStore(get()) }
    single { LiveTvRepository(get()) }
    single { LiveTvFeatureStore(get()) }
    single { ActiveProfileStore(get()) }
    single { org.prairieserver.prairie.repository.MetadataAiRepository(get()) }
    single { org.prairieserver.prairie.model.feature.MetadataAiFeatureStore(get()) }
    single { org.prairieserver.prairie.repository.HomeRealtimeCoordinator(get(), get()) }
    single { SettingsRepository(get()) }
    // Profile-scoped canonical settings, shared by the phone and TV screens so
    // one platform cannot grow a behavior the other lacks.
    single { org.prairieserver.prairie.domain.settings.ProfileSettingsController(get()) }
    single { LibraryPlaybackPrefsRepository(get()) }
    single { DownloadsRepository(get(), getOrNull<org.prairieserver.prairie.repository.port.DownloadDeletionPort>() ?: org.prairieserver.prairie.repository.port.NoOpDownloadDeletionPort, get(), get(), get()) }
    single { EbookReaderRepository(get(), get()) }
    single { SubtitlesRepository(get(), get()) }
    single { PushRegistrationRepository(get()) }

    // REST-backed inbox state plus a realtime factory that builds the default
    // websocket client from the shared HttpClient + NotificationsApi. The
    // factory is lazy so a connection is only minted when connectRealtime() runs.
    single {
        NotificationsRepository(
            api = get(),
            tokens = get(), authorities = getOrNull(), checkpoints = getOrNull(), identityTransitions = get(),
            realtimeFactory = {
                org.prairieserver.prairie.network.DefaultNotificationsRealtimeClient(
                    socket = get(),
                )
            },
        )
    }

    // One room's snapshot/suggestions state + WS lifecycle. The realtime factory
    // builds the per-room socket client from the shared HttpClient + TokenManager.
    // Each connect mints a single-use v2 room ticket and upgrades with it in the
    // subprotocol; no credential travels in the URL. Lazy so a socket is only
    // minted when connect() runs.
    single {
        val tokenManager: TokenManager = get()
        WatchTogetherRepository(
            api = get(),
            authScopeProvider = { tokenManager.snapshotCurrentScope() },
            realtimeFactory = {
                org.prairieserver.prairie.network.DefaultWatchTogetherRealtimeClient(
                    client = get(),
                    tokenManager = tokenManager,
                )
            },
        )
    }
    single<WatchTogetherEntryGateway> { get<WatchTogetherRepository>() }
    // Eager so the identity-transition privacy gate is installed before any
    // profile/server/token mutation can occur. This process-lifetime scope,
    // rather than a screen scope, owns connection replacement and teardown.
    single(createdAtStart = true) {
        RoomSession(
            repository = get<WatchTogetherRepository>(),
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
            identityTransitions = get(),
        )
    }

    // Per-session playback control socket (admin remote control). Parallel to
    // the watch-together realtime client; v2 uses a single-use owner-bound ticket.
    // FACTORY, not single: the client holds one mutable socket session, so each
    // player-screen controller must get its own instance (mirrors how the WT
    // repository mints a fresh client per connect) — a shared singleton would
    // let a second player clobber the first's socket.
    factory<org.prairieserver.prairie.network.PlaybackRealtimeClient> {
        org.prairieserver.prairie.network.DefaultPlaybackRealtimeClient(
            client = get(),
            tokenManager = get(),
            gate = get(),
            ownerProvider = get<PlaybackRepository>()::controlOwner,
        )
    }

    // Domain use cases
    single { MediaActionsCoordinator(get()) }
}
