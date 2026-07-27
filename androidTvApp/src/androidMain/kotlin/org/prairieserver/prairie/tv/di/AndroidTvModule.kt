@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package org.prairieserver.prairie.tv.di

import org.prairieserver.prairie.network.ApiResult
import org.prairieserver.prairie.repository.SettingsRepository
import org.prairieserver.prairie.tv.data.preferences.LegacyTvPrefsMigration
import org.prairieserver.prairie.tv.data.preferences.TvLibrarySelectionStore
import org.prairieserver.prairie.common.network.AndroidDeviceMetadataProvider
import org.prairieserver.prairie.common.settings.AndroidServerSettingsCache
import android.content.SharedPreferences
import org.prairieserver.prairie.network.AndroidServerRegistry
import org.prairieserver.prairie.network.EncryptedTokenManagerImpl
import org.prairieserver.prairie.network.ServerRegistry
import org.prairieserver.prairie.network.TokenManager
import org.prairieserver.prairie.network.createSecureSharedPrefs
import org.prairieserver.prairie.tv.ui.screens.servers.TvServerListViewModel
import org.prairieserver.prairie.common.player.AudioCapabilityManager
import org.prairieserver.prairie.common.player.AudioTrackManager
import org.prairieserver.prairie.common.player.PlaybackCapabilityDetector
import org.prairieserver.prairie.common.player.backend.VideoPlaybackBackendFactory
import org.prairieserver.prairie.tv.ui.screens.settings.TvSettingsViewModel
import org.prairieserver.prairie.tv.ui.screens.settings.diagnostics.TvDiagnosticsViewModel
import org.prairieserver.prairie.common.player.PrairiePlayerFactory
import org.prairieserver.prairie.common.player.PlaybackSessionManager
import org.prairieserver.prairie.common.player.SubtitleManager
import org.prairieserver.prairie.common.cast.PrairieCastNsdAdvertiser
import org.prairieserver.prairie.common.player.video.VideoPlaybackSessionCoordinator
import org.prairieserver.prairie.common.player.video.VideoPlaybackStarter
import org.prairieserver.prairie.tv.cast.TvPrairieCastReceiver
import org.prairieserver.prairie.tv.cast.RemotePlaybackIdentityManager
import org.prairieserver.prairie.tv.ui.screens.player.TvPlayerLaunchArgs
import org.prairieserver.prairie.tv.ui.screens.auth.TvLoginViewModel
import org.prairieserver.prairie.tv.ui.screens.auth.TvServerSetupViewModel
import org.prairieserver.prairie.tv.ui.screens.collections.TvCollectionDetailViewModel
import org.prairieserver.prairie.viewmodel.AdminStatsViewModel
import org.prairieserver.prairie.viewmodel.CalendarViewModel
import org.prairieserver.prairie.viewmodel.CollectionsViewModel
import org.prairieserver.prairie.tv.ui.screens.detail.TvItemDetailViewModel
import org.prairieserver.prairie.viewmodel.HomeViewModel
import org.prairieserver.prairie.viewmodel.RecommendationsViewModel
import org.prairieserver.prairie.viewmodel.MyRequestsViewModel
import org.prairieserver.prairie.viewmodel.LiveTvPlayerViewModel
import org.prairieserver.prairie.viewmodel.LiveTvViewModel
import org.prairieserver.prairie.viewmodel.RequestSearchViewModel
import org.prairieserver.prairie.viewmodel.RequestsViewModel
import org.prairieserver.prairie.tv.ui.screens.libraries.TvLibrariesViewModel
import org.prairieserver.prairie.tv.ui.screens.library.TvLibraryCollectionDetailViewModel
import org.prairieserver.prairie.tv.ui.screens.library.TvLibraryDetailViewModel
import org.prairieserver.prairie.viewmodel.FavoritesViewModel
import org.prairieserver.prairie.viewmodel.HistoryViewModel
import org.prairieserver.prairie.viewmodel.WatchlistViewModel
import org.prairieserver.prairie.tv.ui.screens.player.TvPlayerViewModel
import org.prairieserver.prairie.tv.ui.screens.player.TvVideoPlaybackStarter
import org.prairieserver.prairie.tv.ui.screens.profiles.TvProfileSelectionViewModel
import org.prairieserver.prairie.tv.ui.screens.search.TvSearchViewModel
import org.prairieserver.prairie.tv.data.preferences.TvLibraryScopeStore
import org.prairieserver.prairie.tv.watchnext.WatchNextRepository
import org.prairieserver.prairie.tv.watchnext.WatchNextSeeder
import android.net.Uri
import kotlinx.coroutines.flow.MutableStateFlow
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModel
import org.koin.core.qualifier.named
import org.koin.dsl.module

/**
 * TV-specific Koin module.
 *
 * Provides the TV flavor of player infrastructure (currently a duplicate of the
 * phone module — see the TODO on [PrairiePlayerFactory]) and the TV ViewModels.
 * The shared [org.prairieserver.prairie.di.sharedModules] is added alongside this one in
 * [org.prairieserver.prairie.tv.PrairieTvApplication].
 */
val androidTvModule = module {
    // Single encrypted prefs handle shared between the server registry and
    // the token manager — see the phone module for rationale.
    single<SharedPreferences> { createSecureSharedPrefs(androidContext()) }

    // Multi-server registry. Loaded synchronously in init so MainTvActivity's
    // runBlocking-resolved start destination sees consistent state.
    single<ServerRegistry> { AndroidServerRegistry(get(), get()) }

    // Persistent (EncryptedSharedPreferences-backed) replacement for the
    // commonMain in-memory TokenManager. Koin 3.1+ replaces same-key bindings
    // when the redefining module is loaded after the original — sharedModules()
    // is registered first in PrairieTvApplication, so this wins.
    single<TokenManager> { EncryptedTokenManagerImpl(get(), get(), get()) }

    // Offline-first Room store (Track B). Bound after sharedModules() so the
    // commonMain PersonalDataRepository's `getOrNull<UserItemStatePort>()` picks
    // up the Room-backed port and writes optimistic projection + outbox rows.
    single { org.prairieserver.prairie.common.data.db.PrairieDatabase.build(androidContext()) }
    single<org.prairieserver.prairie.common.data.sync.OutboxSyncScheduler> {
        val appContext = androidContext().applicationContext
        org.prairieserver.prairie.common.data.sync.OutboxSyncScheduler {
            org.prairieserver.prairie.common.data.sync.SyncWorker.enqueue(appContext)
        }
    }
    single<org.prairieserver.prairie.repository.port.UserItemStatePort> {
        val tokenManager: TokenManager = get()
        org.prairieserver.prairie.common.data.repository.RoomUserItemStateRepository(
            db = get(),
            snapshotProvider = { tokenManager.snapshotCurrentScope() },
            syncScheduler = get(),
        )
    }
    single<org.prairieserver.prairie.repository.port.HomeCachePort> {
        val tokenManager: TokenManager = get()
        org.prairieserver.prairie.common.data.repository.RoomHomeCacheRepository(
            db = get(),
            snapshotProvider = { tokenManager.snapshotCurrentScope() },
        )
    }
    single<org.prairieserver.prairie.repository.port.CatalogCachePort> {
        val tokenManager: TokenManager = get()
        org.prairieserver.prairie.common.data.repository.RoomCatalogCacheRepository(
            db = get(),
            snapshotProvider = { tokenManager.snapshotCurrentScope() },
        )
    }
    single<org.prairieserver.prairie.repository.port.DownloadDeletionPort> {
        org.prairieserver.prairie.common.data.repository.RoomDownloadDeletionStore(db = get())
    }
    single {
        val tokenManager: TokenManager = get()
        org.prairieserver.prairie.common.data.sync.SyncEngine(
            db = get(),
            personalDataApi = get(),
            ebookReaderApi = get(),
            snapshotProvider = { tokenManager.snapshotCurrentScope() },
        )
    }

    single<AndroidServerSettingsCache> { AndroidServerSettingsCache(androidContext()) }
    single<org.prairieserver.prairie.network.DeviceMetadataProvider> {
        AndroidDeviceMetadataProvider(androidContext(), platform = "android-tv")
    }
    // Player infrastructure (duplicate-for-now; extract to :android-player later).
    single { SubtitleManager(get()) }
    single { AudioTrackManager() }
    single {
        VideoPlaybackBackendFactory(
            playerFactory = get(),
            audioTrackManager = get(),
            subtitleManager = get(),
        )
    }
    single { AudioCapabilityManager(androidContext()) }
    single { PlaybackCapabilityDetector(androidContext(), get(), get()) }
    single {
        PrairiePlayerFactory(
            context = androidContext(),
            tokenManager = get(),
            subtitleManager = get(),
            httpDataSourceFactory = get(org.prairieserver.prairie.common.di.PLAYER_HTTP_DATA_SOURCE_FACTORY_QUALIFIER),
            mediaAuthSession = get(),
            bandwidthMeter = get(),
            delayProcessor = get(),
            subtitleOffsetHolder = get(),
            libassBridge = get(),
        )
    }
    single { PlaybackSessionManager(get(), get(), get()) }
    factory<VideoPlaybackStarter>(named("tvVideoPlaybackStarter")) {
        TvVideoPlaybackStarter(
            catalogRepository = get(),
            playbackSessionManager = get(),
            profileRepository = get(),
            capabilityDetector = get(),
            playerSettingsStore = get(),
            sessionLifecycle = get(),
            reachabilityMonitor = get(),
        )
    }
    factory {
        VideoPlaybackSessionCoordinator(
            starter = get(named("tvVideoPlaybackStarter")),
        )
    }

    // Audiobook player deps. TV reuses the shared android-shared VM. The TV
    // graph has no downloads (streaming-only), so register the resolver inline:
    // it just always finds no local media and the VM falls back to the server
    // stream. The stores are local-only JSON under filesDir.
    single {
        org.prairieserver.prairie.common.downloads.OfflineMediaResolver(
            org.prairieserver.prairie.common.downloads.DownloadMetadataStore(get()),
            org.prairieserver.prairie.common.downloads.DownloadStorage(androidContext()),
            get(),
        )
    }
    // One-time import of the legacy .record.json sidecar tree into Room.
    single {
        org.prairieserver.prairie.common.downloads.LegacyDownloadImporter(androidContext().filesDir, get())
    }
    single { org.prairieserver.prairie.common.audiobook.AudiobookBookmarksStore(androidContext().filesDir) }

    // Shared audiobook player VM (android-shared). SavedStateHandle is
    // auto-injected by Koin's viewModel scope so the contentId/fileId nav args
    // (TvRoute.AudiobookPlayer) flow through unchanged — same as the phone.
    viewModel {
        org.prairieserver.prairie.common.player.AudiobookPlayerViewModel(
            catalogRepository = get(),
            playbackSessionManager = get(),
            capabilityDetector = get(),
            bookmarksStore = get(),
            userItemStatePort = get(),
            outboxSyncScheduler = get(),
            serverRegistry = get(),
            profileRepository = get(),
            offlineMediaResolver = get(),
            audiobookSettings = get(),
            savedStateHandle = get(),
        )
    }

    // Preferences (per-profile DataStore for the Libraries tab's selected library).
    single { TvLibrarySelectionStore(androidContext(), get()) }

    // Skyline per-profile·server·type library scope (persisted across launches).
    single {
        org.prairieserver.prairie.tv.data.preferences.TvLibraryScopeStore(androidContext(), get())
    }

    // One-shot legacy `tv_prefs` import (playback settings → server device
    // overrides; selected-library id → active profile's selection store).
    // Sentinel-gated; invoked from TvSettingsViewModel.loadSettings and
    // TvLibrariesViewModel.load. Lambda-injected lookups follow the
    // AndroidPlayerSettingsStore wiring pattern in PlayerInfraModule.
    single {
        LegacyTvPrefsMigration(
            context = androidContext(),
            settingsCache = get(),
            playerSettingsStore = get(),
            librarySelectionStore = get(),
            getServerUrl = { get<TokenManager>().getServerUrl() },
            getProfileId = { get<TokenManager>().getProfileId() },
            getEffectiveSettings = { keys ->
                when (val result = get<SettingsRepository>().getEffectiveSettings(keys)) {
                    is ApiResult.Success -> result.data
                    is ApiResult.Error, is ApiResult.NetworkError -> emptyMap()
                }
            },
        )
    }

    // Watch Next launcher integration (TV-only). Repository wraps the
    // TvProvider ContentResolver; the worker is constructed by
    // TvWorkerFactory, installed via WorkManager.initialize in
    // PrairieTvApplication (Koin's worker DSL is not used — see
    // TvWorkerFactory for why).
    single { WatchNextRepository(androidContext()) }
    single { WatchNextSeeder(androidContext(), get()) }

    // Deep-link bridge between MainTvActivity (producer) and TvAppNavigation
    // (consumer). The Activity writes incoming Prairie app-scheme URIs here on
    // cold-launch (read from launching intent in onCreate) and warm-launch
    // (onNewIntent); the navigation Composable observes the flow and routes
    // to ItemDetail / Player once the user is past the auth chain. Using a
    // shared singleton avoids prop-drilling the URI through every screen
    // that might be the active destination at the moment the link arrives.
    single<MutableStateFlow<Uri?>>(named("pendingDeepLink")) { MutableStateFlow(null) }

    // LAN companion-pairing receiver engine (TV). The advertiser owns the NSD +
    // TLS-PSK socket lifecycle; the receiver is the transport-agnostic state
    // machine. A later step wires the UI to PairingReceiver.status.
    single {
        org.prairieserver.prairie.common.pairing.PairingReceiver(
            authPort = org.prairieserver.prairie.common.pairing.RegistryPairingAuthPort(get(), get()),
            deviceLogin = org.prairieserver.prairie.common.pairing.DeviceLoginRepositoryPort(get()),
            identityProvider = {
                org.prairieserver.prairie.common.pairing.PairingDeviceIdentity(
                    name = tvDeviceName(),
                    deviceId = org.prairieserver.prairie.common.pairing.PairingDeviceId
                        .stable(androidContext()),
                )
            },
            // Always `setup`: advertising only runs while the server-setup
            // screen is showing, and Apple is authoritative for the wire —
            // prairie-apple's TVPairingAdvertiser hardcodes st=setup and its
            // companion card FILTERS to state == .setup, so a registry-based
            // `login` (always true after a sign-out, since the registry keeps
            // entries) made the TV invisible to phones exactly when the user
            // needed set-up-with-phone again.
            receiverStateProvider = { org.prairieserver.prairie.pairing.PairingReceiverState.Setup },
        )
    }
    single {
        org.prairieserver.prairie.common.pairing.TvPairingAdvertiser(
            context = androidContext(),
            receiver = get(),
            // Always `setup`: advertising only runs while the server-setup
            // screen is showing, and Apple is authoritative for the wire —
            // prairie-apple's TVPairingAdvertiser hardcodes st=setup and its
            // companion card FILTERS to state == .setup, so a registry-based
            // `login` (always true after a sign-out, since the registry keeps
            // entries) made the TV invisible to phones exactly when the user
            // needed set-up-with-phone again.
            receiverStateProvider = { org.prairieserver.prairie.pairing.PairingReceiverState.Setup },
        )
    }
    single { PrairieCastNsdAdvertiser(androidContext()) }
    single {
        RemotePlaybackIdentityManager(
            deviceLoginApi = get(),
            tokenManager = get(),
            deviceNameProvider = ::tvDeviceName,
        )
    }
    single {
        TvPrairieCastReceiver(
            advertiser = get(),
            serverRegistry = get(),
            identityManager = get(),
            deviceNameProvider = ::tvDeviceName,
            deviceIdProvider = {
                org.prairieserver.prairie.common.pairing.PairingDeviceId.stable(androidContext())
            },
        )
    }

    // Auth ViewModels
    viewModel { TvServerSetupViewModel(get()) }
    viewModel { org.prairieserver.prairie.tv.ui.screens.auth.TvSetupViewModel(get()) }
    viewModel { org.prairieserver.prairie.tv.ui.screens.auth.TvSignupViewModel(get()) }
    viewModel { TvLoginViewModel(get(), get(), get()) }
    viewModel { TvProfileSelectionViewModel(get()) }
    viewModel { org.prairieserver.prairie.tv.ui.screens.profiles.TvCreateProfileViewModel(get()) }
    viewModel { params ->
        org.prairieserver.prairie.tv.ui.screens.profiles.TvEditProfileViewModel(
            profileRepository = get(),
            profileId = params.get(),
        )
    }
    viewModel { TvServerListViewModel(get(), get(), get(), get()) }

    // Admin ViewModels
    viewModel { AdminStatsViewModel(get()) }
    viewModel { org.prairieserver.prairie.viewmodel.AdminUsersViewModel(get()) }
    viewModel { org.prairieserver.prairie.viewmodel.AdminUserEditViewModel(get()) }
    viewModel { org.prairieserver.prairie.tv.ui.screens.admin.TvAdminSessionsViewModel(get()) }
    viewModel { org.prairieserver.prairie.tv.ui.screens.admin.TvAdminScansViewModel(get(), get()) }
    viewModel { org.prairieserver.prairie.tv.ui.screens.admin.TvAdminLogsViewModel(get()) }
    viewModel { org.prairieserver.prairie.tv.ui.screens.settings.TvManageSessionsViewModel(get()) }
    viewModel { params ->
        org.prairieserver.prairie.viewmodel.RequestDetailViewModel(get(), params.get(), params.get())
    }
    viewModel { params ->
        val args = params.get<Pair<String?, String?>>()
        org.prairieserver.prairie.viewmodel.DevicePairingViewModel(
            repository = get(),
            initialToken = args.first,
            initialCode = args.second,
        )
    }

    // Content ViewModels
    viewModel { HomeViewModel(get(), get(), get(), get(), getOrNull()) }
    viewModel { org.prairieserver.prairie.tv.ui.screens.home.TvUpcomingViewModel(get()) }
    viewModel { RecommendationsViewModel(get()) }
    viewModel { RequestsViewModel(get()) }
    viewModel { RequestSearchViewModel(get()) }
    viewModel { MyRequestsViewModel(get()) }
    viewModel { org.prairieserver.prairie.tv.ui.screens.requests.TvRequestsViewModel(get()) }
    viewModel {
        LiveTvViewModel(
            repository = get(),
            nowMillisProvider = { System.currentTimeMillis() },
        )
    }
    viewModel { LiveTvPlayerViewModel(get()) }
    // Platform supplies "today" and the IANA timezone; the shared ViewModel's
    // week math stays deterministic in commonTest (no Clock.System default).
    viewModel {
        CalendarViewModel(
            repository = get(),
            timezoneId = java.util.TimeZone.getDefault().id,
            todayProvider = { java.time.LocalDate.now().toString() },
        )
    }
    viewModel { org.prairieserver.prairie.tv.ui.screens.browse.TvBrowseViewModel(get()) }
    viewModel { params ->
        org.prairieserver.prairie.tv.ui.screens.people.TvPersonDetailViewModel(
            catalogRepository = get(),
            personId = params.get(),
            personalDataRepository = getOrNull(),
            showAudiobooksProvider = {
                get<TvLibraryScopeStore>().getShowAudiobooksTab()
            },
        )
    }
    viewModel { TvLibrariesViewModel(get(), get(), get()) }
    viewModel { params ->
        TvLibraryDetailViewModel(
            sectionRepository = get(),
            catalogRepository = get(),
            libraryId = params.get(),
            libraryTitle = params.get(),
            libraryType = params.get(),
        )
    }
    viewModel { params ->
        TvLibraryCollectionDetailViewModel(
            sectionRepository = get(),
            libraryId = params.get(),
            collectionId = params.get(),
            title = params.get(),
        )
    }
    viewModel { TvSearchViewModel(get(), get(), get()) }
    viewModel { params ->
        TvItemDetailViewModel(
            catalogRepository = get(),
            personalDataRepository = get(),
            playerSettingsStore = get(),
            profileRepository = get(),
            metadataAiRepository = get(),
            contentId = params.get(),
            userItemState = getOrNull<org.prairieserver.prairie.repository.port.UserItemStatePort>()
                ?: org.prairieserver.prairie.repository.port.NoOpUserItemStatePort,
            recommendationRepository = getOrNull(),
        )
    }
    // Watch Together entry (create/join orchestration) — backs the entry +
    // join-code dialogs on the detail screen.
    viewModel {
        org.prairieserver.prairie.tv.ui.screens.watchtogether.TvWatchTogetherViewModel(get())
    }
    // Watch Together lobby — keyed per roomId (koinViewModel key="wt-lobby-$roomId");
    // roomId is read from the positional parameter.
    viewModel { params ->
        org.prairieserver.prairie.tv.ui.screens.watchtogether.TvWatchTogetherLobbyViewModel(
            roomId = params.get(),
            repository = get(),
        )
    }
    viewModel { params ->
        TvPlayerViewModel(
            videoPlaybackCoordinator = get(),
            playbackSessionManager = get(),
            playbackAnalytics = get(),
            capabilityDetector = get(),
            // Phase 3 TV uplift dependencies (per-profile settings, intro
            // auto-skip controller, lifecycle, sleep timer).
            playerSettingsStore = get(),
            introAutoSkipController = get(),
            sessionLifecycle = get(),
            sleepTimer = get(),
            subtitlesRepository = get(),
            userItemStatePort = get(),
            outboxSyncScheduler = get(),
            catalogRepository = get(),
            serverReachabilityMonitor = get(),
            launchArgs = params.get<TvPlayerLaunchArgs>(),
        )
    }

    // Personal data grids.
    viewModel { FavoritesViewModel(get()) }
    viewModel { WatchlistViewModel(get()) }
    viewModel { HistoryViewModel(get()) }

    // Collections.
    viewModel { CollectionsViewModel(get()) }
    viewModel { params ->
        TvCollectionDetailViewModel(
            collectionRepository = get(),
            collectionId = params.get(),
            initialTitle = params.get(),
        )
    }

    // Settings.
    viewModel {
        TvSettingsViewModel(
            authRepository = get(),
            profileRepository = get(),
            tokenManager = get(),
            serverRegistry = get(),
            playerSettingsStore = get(),
            libraryPlaybackPrefsStore = get(),
            overlayPrefsStore = get(),
            legacyTvPrefsMigration = get(),
            tvLibraryScopeStore = getOrNull(),
            appUpdateChecker = get(),
            appVersionName = org.prairieserver.prairie.tv.BuildConfig.VERSION_NAME,
        )
    }
    viewModel { TvDiagnosticsViewModel(get()) }
}

private fun tvDeviceName(): String =
    android.os.Build.MODEL?.trim()?.ifBlank { null } ?: "Android TV"
