package org.prairieserver.prairie.di

import org.prairieserver.prairie.network.TokenManager
import org.prairieserver.prairie.network.TokenManagerImpl
import org.prairieserver.prairie.network.DefaultIdentityTransitionBarrier
import org.prairieserver.prairie.network.IdentityTransitionBarrier
import org.prairieserver.prairie.network.createPrairieClient
import org.prairieserver.prairie.network.HomeRealtimeClient
import org.prairieserver.prairie.network.DefaultHomeRealtimeClient
import org.prairieserver.prairie.network.api.*
import org.prairieserver.prairie.network.apiv2.*
import org.koin.dsl.module

val networkModule = module {
    single<IdentityTransitionBarrier> { DefaultIdentityTransitionBarrier() }
    single<TokenManager> { TokenManagerImpl(get()) }
    single { createPrairieClient(get(), getOrNull(), getOrNull(), getOrNull()) }
    single { ApiV2Gate(getOrNull()) }
    single { MembershipV2Api(get(), get(), get()) }
    single { ApiV2Probe(get()) }
    single { ImageCapabilitiesApi(get(), get(), get()) }
    single { org.prairieserver.prairie.repository.ImageCapabilitiesSession(get(), get(), get()) }
    single { AuthApi(get(), get()) }
    single { OnboardingApi(get(), get(), get()) }
    single<DeviceLoginApi> { DefaultDeviceLoginApi(get(), get()) }
    single { CatalogV2Api(get(), get(), get()) }
    single { PersonRefreshV2Api(get(), get(), get()) }
    single { WatchDetailV2Api(get(), get(), get()) }
    single { CatalogApi(get(), get(), get(), get()) }
    // Prairie-only v1 routes: explicit transcode start + the transcode quality ladder.
    single { PlaybackApi(get()) }
    single {
        org.prairieserver.prairie.playback.QualityLadderClient(
            fetchResponse = { get<PlaybackApi>().getQualityLadder() },
        )
    }
    single { PersonalDataApi(get(), get(), get()) }
    single { CollectionApi(get(), get(), get()) }
    single { ProfileApi(get(), get(), get()) }
    single { LibrarySectionItemsV2Api(get(), get(), get()) }
    single { HomeSectionsV2Api(get(), get(), get()) }
    single { SectionApi(get(), get(), get(), get()) }
    single { SimilarCardsV2Api(get(), get(), get()) }
    single { TasteProfileV2Api(get(), get(), get()) }
    single { DiscoverV2Api(get(), get(), get()) }
    single { RecommendationApi(get(), get(), get(), get()) }
    single<RequestsApi> { DefaultRequestsApi(get(), get(), get()) }
    // Prairie-only: Live TV (Jellyfin-compat endpoints on prairie-server).
    single<LiveTvApi> { DefaultLiveTvApi(get()) }
    single<MetadataAiApi> { DefaultMetadataAiApi(get(), get(), get()) }
    single { EventsSocketV2Api(get(), get(), get()) }
    single<HomeRealtimeClient> { DefaultHomeRealtimeClient(get()) }
    single<CalendarApi> { DefaultCalendarApi(get(), get(), get()) }
    single { HealthApi(get()) }
    single { org.prairieserver.prairie.update.AppUpdateChecker(get()) }
    single { org.prairieserver.prairie.discovery.LanDiscovery(get()) }
    single { BrandingApi(get()) }
    single { SettingsV2Api(get(), get(), get()) }
    single { SettingsApi(get()) }
    single { LibraryPlaybackPrefsApi(get()) }
    single { DownloadRegistryV2Api(get(), get(), get(), get()) }
    single { DownloadCreationV2Api(get(), get(), get(), get(), get()) }
    single { DownloadsApi(get(), get(), get()) }
    single { EbookReaderV2Api(get(), get(), get()) }
    single { EbookReaderApi(get()) }
    single { SubtitleAiReadsV2Api(get(), get(), get()) }
    single { SubtitleDownloadV2Api(get(), get(), get()) }
    single { SubtitleReadsV2Api(get(), get(), get()) }
    single { SubtitleAiCreateV2Api(get(), get(), get()) }
    single<SubtitlesApi> { DefaultSubtitlesApi(get(), get(), get(), get()) }
    single<NotificationsApi> { NotificationsV2Api(get(), get(), get()) }
    single<PushRegistrationApi> { DefaultPushRegistrationApi(get(), get(), get()) }
    single<WatchTogetherApi> { DefaultWatchTogetherApi(get(), get()) }
    single<DiagnosticsApi> { DefaultDiagnosticsApi(get(), gate = get()) }
}
