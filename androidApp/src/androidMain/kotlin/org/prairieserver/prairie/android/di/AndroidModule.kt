@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package org.prairieserver.prairie.android.di

import org.prairieserver.prairie.common.downloads.DownloadEnqueuer
import org.prairieserver.prairie.common.downloads.DownloadSubscriptionEvaluatorFactory
import org.prairieserver.prairie.common.downloads.DownloadSubscriptionWorker
import org.prairieserver.prairie.common.downloads.OfflineMediaResolver
import org.prairieserver.prairie.common.downloads.DownloadStorage
import org.prairieserver.prairie.common.downloads.DownloadWorker
import org.prairieserver.prairie.common.cast.PrairieCastNsdBrowser
import org.prairieserver.prairie.common.pairing.PairingDeviceId
import org.prairieserver.prairie.common.pairing.CompanionDeviceLoginApprover
import org.prairieserver.prairie.common.pairing.CompanionPairingCoordinator
import org.prairieserver.prairie.common.pairing.CompanionPairingNsdBrowser
import org.prairieserver.prairie.common.pairing.CompanionPairingServerStore
import org.prairieserver.prairie.common.pairing.CompanionPairingTransportFactory
import org.prairieserver.prairie.common.pairing.RegistryCompanionPairingServerStore
import org.prairieserver.prairie.common.pairing.RepositoryCompanionDeviceLoginApprover
import org.prairieserver.prairie.common.pairing.TlsPskPairingClientTransport
import org.prairieserver.prairie.common.player.AudioCapabilityManager
import org.prairieserver.prairie.common.player.AudioTrackManager
import org.prairieserver.prairie.common.player.PrairiePlayerFactory
import org.prairieserver.prairie.common.player.PlaybackCapabilityDetector
import org.prairieserver.prairie.common.player.PlaybackSessionManager
import org.prairieserver.prairie.common.player.SubtitleManager
import org.prairieserver.prairie.common.player.cast.CastPlaybackPreparer
import org.prairieserver.prairie.common.player.backend.VideoPlaybackBackendFactory
import org.prairieserver.prairie.common.player.video.VideoPlaybackSessionCoordinator
import org.prairieserver.prairie.common.player.video.VideoPlaybackStarter
import org.prairieserver.prairie.common.network.AndroidDeviceMetadataProvider
import org.prairieserver.prairie.common.settings.AndroidServerSettingsCache
import android.content.SharedPreferences
import org.prairieserver.prairie.network.AndroidServerRegistry
import org.prairieserver.prairie.network.EncryptedTokenManagerImpl
import org.prairieserver.prairie.network.ServerRegistry
import org.prairieserver.prairie.network.TokenManager
import org.prairieserver.prairie.network.createSecureSharedPrefs
import org.prairieserver.prairie.android.push.AndroidPushRegistrar
import org.prairieserver.prairie.android.push.AndroidPushTokenProvider
import org.prairieserver.prairie.android.push.FirebaseAndroidPushTokenProvider
import org.prairieserver.prairie.android.push.PushMessageHandler
import org.prairieserver.prairie.android.push.PushNotificationPresenter
import org.prairieserver.prairie.android.ui.screens.admin.AdminEntryViewModel
import org.prairieserver.prairie.android.ui.screens.admin.AdminLogsViewModel
import org.prairieserver.prairie.android.ui.screens.admin.AdminScansViewModel
import org.prairieserver.prairie.android.ui.screens.admin.AdminSessionsViewModel
import org.prairieserver.prairie.android.ui.screens.browse.BrowseViewModel
import org.prairieserver.prairie.android.ui.screens.collections.CollectionDetailViewModel
import org.prairieserver.prairie.android.ui.screens.collections.LibraryCollectionsViewModel
import org.prairieserver.prairie.viewmodel.AdminStatsViewModel
import org.prairieserver.prairie.viewmodel.AdminUserEditViewModel
import org.prairieserver.prairie.viewmodel.AdminUsersViewModel
import org.prairieserver.prairie.viewmodel.CalendarViewModel
import org.prairieserver.prairie.viewmodel.CollectionsViewModel
import org.prairieserver.prairie.android.ui.screens.detail.ItemDetailViewModel
import org.prairieserver.prairie.android.ui.screens.people.PersonDetailViewModel
import org.prairieserver.prairie.android.ui.screens.auth.LoginViewModel
import org.prairieserver.prairie.android.ui.screens.auth.ServerSetupViewModel
import org.prairieserver.prairie.android.ui.screens.auth.SetupViewModel
import org.prairieserver.prairie.android.ui.screens.auth.SignupViewModel
import org.prairieserver.prairie.android.ui.screens.MainHeaderViewModel
import org.prairieserver.prairie.viewmodel.DevicePairingViewModel
import org.prairieserver.prairie.android.ui.screens.profiles.CreateProfileViewModel
import org.prairieserver.prairie.android.ui.screens.profiles.EditProfileViewModel
import org.prairieserver.prairie.android.ui.screens.profiles.ProfileSelectionViewModel
import org.prairieserver.prairie.android.ui.screens.servers.ServerListViewModel
import org.prairieserver.prairie.android.ui.screens.downloads.DownloadsViewModel
import org.prairieserver.prairie.viewmodel.HomeViewModel
import org.prairieserver.prairie.android.ui.screens.libraries.LibrariesViewModel
import org.prairieserver.prairie.viewmodel.FavoritesViewModel
import org.prairieserver.prairie.viewmodel.HistoryViewModel
import org.prairieserver.prairie.viewmodel.MyRequestsViewModel
import org.prairieserver.prairie.viewmodel.LiveTvPlayerViewModel
import org.prairieserver.prairie.viewmodel.LiveTvViewModel
import org.prairieserver.prairie.viewmodel.RecommendationsViewModel
import org.prairieserver.prairie.viewmodel.RequestDetailViewModel
import org.prairieserver.prairie.viewmodel.RequestSearchViewModel
import org.prairieserver.prairie.viewmodel.RequestsViewModel
import org.prairieserver.prairie.viewmodel.WatchlistViewModel
import org.prairieserver.prairie.android.ui.screens.player.MobileVideoPlaybackStarter
import org.prairieserver.prairie.android.ui.screens.player.PlayerViewModel
import org.prairieserver.prairie.android.ui.screens.reading.ReadingHubViewModel
import org.prairieserver.prairie.android.ui.screens.search.SearchViewModel
import org.prairieserver.prairie.android.ui.screens.settings.SettingsViewModel
import org.prairieserver.prairie.android.ui.screens.settings.diagnostics.DiagnosticsViewModel
import org.prairieserver.prairie.android.cast.SharedPrefsPrairieCastLastTargetStore
import org.prairieserver.prairie.android.cast.PrairieCastController
import org.prairieserver.prairie.android.cast.PrairieCastLastTargetStore
import org.prairieserver.prairie.android.cast.PrairieCastSessionManager
import org.koin.android.ext.koin.androidContext
import org.koin.androidx.workmanager.dsl.worker
import org.koin.core.module.dsl.viewModel
import org.koin.core.qualifier.named
import org.koin.dsl.module

/**
 * Android-specific Koin module.
 *
 * Provides player infrastructure (ExoPlayer factory, session manager, managers)
 * and all Android ViewModels. Player components are singletons; ViewModels use
 * the viewModel DSL for proper lifecycle integration.
 */
val androidModule = module {
    // Single encrypted prefs handle shared between the server registry and the
    // token manager — opening it twice means two MasterKey lookups + decryption
    // passes on cold start.
    single<SharedPreferences> { createSecureSharedPrefs(androidContext()) }

    // Multi-server registry. Loaded synchronously in init so MainActivity's
    // `runBlocking { resolveStartDestination() }` reads consistent state.
    single<ServerRegistry> { AndroidServerRegistry(get(), get()) }

    // Persistent (EncryptedSharedPreferences-backed) replacement for the
    // commonMain in-memory TokenManager. Koin 3.1+ replaces same-key bindings
    // when the redefining module is loaded after the original — sharedModules()
    // is registered first in PrairieApplication, so this wins.
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
            // Drain is requested only when a write is left pending (resolve RETRIABLE).
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
    single<org.prairieserver.prairie.repository.DownloadSubscriptionRepository> {
        org.prairieserver.prairie.common.data.repository.RoomDownloadSubscriptionRepository(db = get())
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

    // App-wide services
    single<AndroidServerSettingsCache> { AndroidServerSettingsCache(androidContext()) }
    single<org.prairieserver.prairie.network.DeviceMetadataProvider> {
        AndroidDeviceMetadataProvider(androidContext(), platform = "android")
    }
    single { PrairieCastNsdBrowser(androidContext()) }
    single { CompanionPairingNsdBrowser(androidContext()) }
    single<CompanionPairingServerStore> { RegistryCompanionPairingServerStore(get(), get()) }
    single<CompanionDeviceLoginApprover> { RepositoryCompanionDeviceLoginApprover(get()) }
    single<CompanionPairingTransportFactory> {
        CompanionPairingTransportFactory { target ->
            TlsPskPairingClientTransport.connect(target.host, target.port)
        }
    }
    single { CompanionPairingCoordinator(get(), get(), get()) }
    single<PrairieCastLastTargetStore> { SharedPrefsPrairieCastLastTargetStore(androidContext()) }
    single {
        PrairieCastController(
            browser = get(),
            serverRegistry = get(),
            tokenManager = get(),
            deviceLoginApi = get(),
            lastTargetStore = get(),
            deviceNameProvider = {
                android.os.Build.MODEL?.trim()?.ifBlank { null } ?: "Android Phone"
            },
            deviceIdProvider = { PairingDeviceId.stable(androidContext()) },
        )
    }
    single<AndroidPushTokenProvider> { FirebaseAndroidPushTokenProvider(androidContext()) }
    single {
        AndroidPushRegistrar(
            tokenProvider = get(),
            repository = get(),
            deviceIdProvider = { PairingDeviceId.stable(androidContext()) },
        )
    }
    single {
        PushNotificationPresenter(
            context = androidContext(),
            notificationsRepository = get(),
        )
    }
    single { PushMessageHandler(presenter = get()) }

    // Player infrastructure
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
    // Google Cast (Chromecast) — phone only. The session manager owns the Cast
    // SDK lifecycle; the preparer opens the separate Tier-2 cast-capability
    // playback session so the raw phone stream is never cast.
    single { PrairieCastSessionManager(androidContext()) }
    single {
        CastPlaybackPreparer(
            playbackRepository = get(),
            tokenManager = get(),
            networkEvidenceProvider = get(),
        )
    }
    factory<VideoPlaybackStarter>(named("mobileVideoPlaybackStarter")) {
        MobileVideoPlaybackStarter(
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
            starter = get(named("mobileVideoPlaybackStarter")),
        )
    }

    // Offline downloads — public MediaStore bytes plus private sidecars.
    // Media files keep original names so other Android readers/players can
    // discover them under Downloads/Prairie.
    single { DownloadStorage(androidContext()) }
    // Download metadata now lives in Room (replaces the .record.json sidecars).
    single { org.prairieserver.prairie.common.downloads.DownloadMetadataStore(get()) }
    // One-time import of the legacy .record.json sidecar tree into Room.
    single { org.prairieserver.prairie.common.downloads.LegacyDownloadImporter(androidContext().filesDir, get()) }
    single { OfflineMediaResolver(get(), get(), get()) }
    single { DownloadEnqueuer(androidContext(), get(), get(), get(), get(), get(), get(), get()) }
    single { DownloadSubscriptionEvaluatorFactory(get(), get(), get()) }
    // CoroutineWorker constructed by Koin's WorkerFactory — see
    // PrairieApplication.onCreate `workManagerFactory()` call.
    worker {
        DownloadWorker(
            appContext = androidContext(),
            params = get(),
            repository = get(),
            storage = get(),
            metadataStore = get(),
            httpClient = get(),
        )
    }
    worker {
        DownloadSubscriptionWorker(
            appContext = androidContext(),
            params = get(),
            repository = get(),
            evaluatorFactory = get(),
            serverRegistry = get(),
            profileRepository = get(),
        )
    }
    // Kept for consistency, but DEAD AT RUNTIME: Koin's WorkManager factory
    // returns null on WM 2.10 + Koin 4.1.0, so AppWorkerFactory does the real
    // injection (see AppWorkerFactory). Update both if SyncWorker's deps change.
    worker {
        org.prairieserver.prairie.common.data.sync.SyncWorker(
            appContext = androidContext(),
            params = get(),
            syncEngine = get(),
        )
    }

    // ViewModels
    factory {
        PlayerViewModel(
            videoPlaybackCoordinator = get(),
            catalogRepository = get(),
            playbackSessionManager = get(),
            playbackAnalytics = get(),
            profileRepository = get(),
            personalDataRepository = get(),
            capabilityDetector = get(),
            offlineMediaResolver = get(),
            serverRegistry = get(),
            serverReachabilityMonitor = get(),
            playerSettingsStore = get(),
            introAutoSkipController = get(),
            sessionLifecycle = get(),
            sleepTimer = get(),
            subtitlesRepository = get(),
            userItemStatePort = get(),
            outboxSyncScheduler = get(),
            sectionRepository = get(),
            castPlaybackPreparer = get(),
        )
    }
    viewModel { HomeViewModel(get(), get(), get(), get(), getOrNull()) }
    viewModel { MainHeaderViewModel(get()) }
    viewModel {
        LibrariesViewModel(
            get(), get(), get(),
            getOrNull<org.prairieserver.prairie.repository.port.UserItemStatePort>() ?: org.prairieserver.prairie.repository.port.NoOpUserItemStatePort,
            get(),
            get<org.prairieserver.prairie.android.ui.screens.browse.BrowsePrefsStore>(),
        )
    }
    viewModel { ReadingHubViewModel(get(), get(), get()) }
    viewModel { RecommendationsViewModel(get()) }
    viewModel { SearchViewModel(get()) }
    single {
        org.prairieserver.prairie.android.ui.screens.browse.BrowsePrefsStore(
            context = get(),
            serverRegistry = get(),
        )
    }
    viewModel { params ->
        BrowseViewModel(
            get(),
            params.get(),
            getOrNull<org.prairieserver.prairie.repository.port.UserItemStatePort>() ?: org.prairieserver.prairie.repository.port.NoOpUserItemStatePort,
            get(),
        )
    }
    viewModel { params ->
        ItemDetailViewModel(
            get(), get(), get(), get(), get(), get(), params.get(),
            getOrNull<org.prairieserver.prairie.repository.port.UserItemStatePort>() ?: org.prairieserver.prairie.repository.port.NoOpUserItemStatePort,
        )
    }
    viewModel { params -> PersonDetailViewModel(get(), params.get()) }
    viewModel { params -> LibraryCollectionsViewModel(get(), params.get()) }
    viewModel { FavoritesViewModel(get()) }
    viewModel { WatchlistViewModel(get()) }
    viewModel { HistoryViewModel(get()) }
    viewModel { CollectionsViewModel(get()) }
    viewModel { params -> CollectionDetailViewModel(get(), get(), params.get()) }
    viewModel { RequestsViewModel(get()) }
    viewModel { RequestSearchViewModel(get()) }
    viewModel { MyRequestsViewModel(get()) }
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
    viewModel { params ->
        val args = params.get<Pair<String, Int>>()
        RequestDetailViewModel(
            repository = get(),
            mediaType = args.first,
            tmdbId = args.second,
        )
    }
    viewModel { SettingsViewModel(get(), get(), get(), get(), get(), get(), get(), org.prairieserver.prairie.android.BuildConfig.VERSION_NAME) }
    viewModel { DiagnosticsViewModel(get()) }
    viewModel { AdminEntryViewModel(get(), get()) }
    viewModel { AdminStatsViewModel(get()) }
    viewModel { AdminUsersViewModel(get()) }
    viewModel { AdminUserEditViewModel(get()) }
    viewModel { AdminSessionsViewModel(get()) }
    viewModel { AdminLogsViewModel(get()) }
    viewModel { AdminScansViewModel(get(), get()) }
    viewModel { DownloadsViewModel(get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get()) }
    viewModel { org.prairieserver.prairie.android.ui.screens.pairing.CompanionPairingViewModel(get(), get()) }
    viewModel { ServerSetupViewModel(get()) }
    viewModel { LoginViewModel(get()) }
    viewModel { SetupViewModel(get()) }
    viewModel { SignupViewModel(get()) }
    viewModel { ProfileSelectionViewModel(get()) }
    viewModel { CreateProfileViewModel(get()) }
    viewModel { EditProfileViewModel(get()) }
    viewModel { ServerListViewModel(get(), get(), get(), get()) }
    viewModel { params ->
        val args = params.get<Pair<String?, String?>>()
        DevicePairingViewModel(
            repository = get(),
            initialToken = args.first,
            initialCode = args.second,
        )
    }

    // Audiobook bookmarks store — per-(server, profile, contentId) JSON
    // files under filesDir/audiobook_bookmarks. Local-only for v1.
    single {
        org.prairieserver.prairie.common.audiobook.AudiobookBookmarksStore(androidContext().filesDir)
    }
    // Audiobook position now flows through the Track B outbox (UserItemStatePort)
    // — the old AudiobookPositionStore + AudiobookProgressSyncer were removed.
    // Still owns ebook bookmarks + display settings; reading POSITION now flows
    // through the Track B outbox (EbookProgressSyncer was removed).
    single {
        org.prairieserver.prairie.common.ebook.EbookLocalStateStore(androidContext().filesDir)
    }

    // Audiobook + book readers. SavedStateHandle is auto-injected via Koin's
    // viewModel scope wiring so the contentId nav arg flows through.
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
    viewModel {
        org.prairieserver.prairie.android.ui.screens.reader.ReaderViewModel(
            catalogRepository = get(),
            ebookReaderRepository = get(),
            offlineMediaResolver = get(),
            localStateStore = get(),
            userItemStatePort = get(),
            outboxSyncScheduler = get(),
            serverRegistry = get(),
            profileRepository = get(),
            savedStateHandle = get(),
        )
    }
    viewModel { org.prairieserver.prairie.android.ui.screens.watchtogether.WatchTogetherEntryViewModel(get()) }
    viewModel { params ->
        org.prairieserver.prairie.android.ui.screens.watchtogether.WatchTogetherLobbyViewModel(
            roomId = params.get(),
            repository = get(),
        )
    }
}
