package org.prairieserver.prairie.di

import org.prairieserver.prairie.discovery.LanDiscovery
import org.prairieserver.prairie.network.TokenManager
import org.prairieserver.prairie.network.TokenManagerImpl
import org.prairieserver.prairie.network.DefaultIdentityTransitionBarrier
import org.prairieserver.prairie.network.IdentityTransitionBarrier
import org.prairieserver.prairie.network.createPrairieClient
import org.prairieserver.prairie.network.api.*
import org.koin.dsl.module

val networkModule = module {
    single<IdentityTransitionBarrier> { DefaultIdentityTransitionBarrier() }
    single<TokenManager> { TokenManagerImpl(get()) }
    single { createPrairieClient(get(), getOrNull(), getOrNull()) }
    single { AuthApi(get()) }
    single<DeviceLoginApi> { DefaultDeviceLoginApi(get()) }
    single { CatalogApi(get()) }
    single { PlaybackApi(get()) }
    single { PersonalDataApi(get()) }
    single { CollectionApi(get()) }
    single { ProfileApi(get()) }
    single { SectionApi(get()) }
    single { RecommendationApi(get()) }
    single<RequestsApi> { DefaultRequestsApi(get()) }
    single<LiveTvApi> { DefaultLiveTvApi(get()) }
    single<org.prairieserver.prairie.network.api.MetadataAiApi> { org.prairieserver.prairie.network.api.DefaultMetadataAiApi(get()) }
    single<org.prairieserver.prairie.network.HomeRealtimeClient> { org.prairieserver.prairie.network.DefaultHomeRealtimeClient(get(), get()) }
    single<CalendarApi> { DefaultCalendarApi(get()) }
    single { HealthApi(get()) }
    single { LanDiscovery(get()) }
    single { SettingsApi(get()) }
    single { LibraryPlaybackPrefsApi(get()) }
    single { DownloadsApi(get()) }
    single { EbookReaderApi(get()) }
    single<SubtitlesApi> { DefaultSubtitlesApi(get()) }
    single<NotificationsApi> { DefaultNotificationsApi(get()) }
    single<PushRegistrationApi> { DefaultPushRegistrationApi(get()) }
    single<AdminApi> { DefaultAdminApi(get()) }
    single<WatchTogetherApi> { DefaultWatchTogetherApi(get()) }
    single<DiagnosticsApi> { DefaultDiagnosticsApi(get()) }
}
