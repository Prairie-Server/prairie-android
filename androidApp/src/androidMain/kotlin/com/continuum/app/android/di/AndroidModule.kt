package com.continuum.app.android.di

import com.continuum.app.common.downloads.DownloadEnqueuer
import com.continuum.app.common.downloads.OfflineMediaResolver
import com.continuum.app.common.downloads.DownloadStorage
import com.continuum.app.common.downloads.DownloadWorker
import com.continuum.app.common.player.AudioCapabilityManager
import com.continuum.app.common.player.AudioTrackManager
import com.continuum.app.common.player.ContinuumPlayerFactory
import com.continuum.app.common.player.PlaybackCapabilityDetector
import com.continuum.app.common.player.PlaybackSessionManager
import com.continuum.app.common.player.SubtitleManager
import com.continuum.app.common.network.AndroidDeviceMetadataProvider
import com.continuum.app.common.settings.AndroidServerSettingsCache
import android.content.SharedPreferences
import com.continuum.app.network.AndroidServerRegistry
import com.continuum.app.network.EncryptedTokenManagerImpl
import com.continuum.app.network.ServerRegistry
import com.continuum.app.network.TokenManager
import com.continuum.app.network.createSecureSharedPrefs
import com.continuum.app.android.ui.screens.admin.AdminEntryViewModel
import com.continuum.app.android.ui.screens.admin.AdminLogsViewModel
import com.continuum.app.android.ui.screens.admin.AdminScansViewModel
import com.continuum.app.android.ui.screens.admin.AdminSessionsViewModel
import com.continuum.app.android.ui.screens.browse.BrowseViewModel
import com.continuum.app.android.ui.screens.collections.CollectionDetailViewModel
import com.continuum.app.android.ui.screens.collections.LibraryCollectionsViewModel
import com.continuum.app.viewmodel.AdminStatsViewModel
import com.continuum.app.viewmodel.AdminUserEditViewModel
import com.continuum.app.viewmodel.AdminUsersViewModel
import com.continuum.app.viewmodel.CalendarViewModel
import com.continuum.app.viewmodel.CollectionsViewModel
import com.continuum.app.android.ui.screens.detail.ItemDetailViewModel
import com.continuum.app.android.ui.screens.people.PersonDetailViewModel
import com.continuum.app.android.ui.screens.auth.LoginViewModel
import com.continuum.app.android.ui.screens.auth.ServerSetupViewModel
import com.continuum.app.android.ui.screens.auth.SetupViewModel
import com.continuum.app.android.ui.screens.auth.SignupViewModel
import com.continuum.app.android.ui.screens.MainHeaderViewModel
import com.continuum.app.viewmodel.DevicePairingViewModel
import com.continuum.app.android.ui.screens.profiles.CreateProfileViewModel
import com.continuum.app.android.ui.screens.profiles.EditProfileViewModel
import com.continuum.app.android.ui.screens.profiles.ProfileSelectionViewModel
import com.continuum.app.android.ui.screens.servers.ServerListViewModel
import com.continuum.app.android.ui.screens.downloads.DownloadsViewModel
import com.continuum.app.viewmodel.HomeViewModel
import com.continuum.app.android.ui.screens.libraries.LibrariesViewModel
import com.continuum.app.viewmodel.FavoritesViewModel
import com.continuum.app.viewmodel.HistoryViewModel
import com.continuum.app.viewmodel.MyRequestsViewModel
import com.continuum.app.viewmodel.RecommendationsViewModel
import com.continuum.app.viewmodel.RequestDetailViewModel
import com.continuum.app.viewmodel.RequestSearchViewModel
import com.continuum.app.viewmodel.RequestsViewModel
import com.continuum.app.viewmodel.WatchlistViewModel
import com.continuum.app.android.ui.screens.player.PlayerViewModel
import com.continuum.app.android.ui.screens.reading.ReadingHubViewModel
import com.continuum.app.android.ui.screens.search.SearchViewModel
import com.continuum.app.android.ui.screens.settings.SettingsViewModel
import com.continuum.app.android.ui.theme.ThemeManager
import org.koin.android.ext.koin.androidContext
import org.koin.androidx.workmanager.dsl.worker
import org.koin.core.module.dsl.viewModel
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
    single<ServerRegistry> { AndroidServerRegistry(androidContext(), get()) }

    // Persistent (EncryptedSharedPreferences-backed) replacement for the
    // commonMain in-memory TokenManager. Koin 3.1+ replaces same-key bindings
    // when the redefining module is loaded after the original — sharedModules()
    // is registered first in ContinuumApplication, so this wins.
    single<TokenManager> { EncryptedTokenManagerImpl(get(), get()) }

    // App-wide services
    single { ThemeManager(androidContext()) }
    single<AndroidServerSettingsCache> { AndroidServerSettingsCache(androidContext()) }
    single<com.continuum.app.network.DeviceMetadataProvider> {
        AndroidDeviceMetadataProvider(androidContext(), platform = "android")
    }

    // Player infrastructure
    single { SubtitleManager() }
    single { AudioTrackManager() }
    single { AudioCapabilityManager(androidContext()) }
    single { PlaybackCapabilityDetector(androidContext(), get()) }
    single {
        ContinuumPlayerFactory(
            context = androidContext(),
            tokenManager = get(),
            subtitleManager = get(),
            okHttpClient = get(com.continuum.app.common.di.PLAYER_OKHTTP_QUALIFIER),
            delayProcessor = get(),
            subtitleOffsetHolder = get(),
        )
    }
    single { PlaybackSessionManager(get(), get()) }

    // Offline downloads — public MediaStore bytes plus private sidecars.
    // Media files keep original names so other Android readers/players can
    // discover them under Downloads/Silo.
    single { DownloadStorage(androidContext()) }
    single { OfflineMediaResolver(get()) }
    single { DownloadEnqueuer(androidContext(), get(), get(), get(), get(), get(), get()) }
    // CoroutineWorker constructed by Koin's WorkerFactory — see
    // ContinuumApplication.onCreate `workManagerFactory()` call.
    worker {
        DownloadWorker(
            appContext = androidContext(),
            params = get(),
            repository = get(),
            storage = get(),
            httpClient = get(),
        )
    }

    // ViewModels
    factory { PlayerViewModel(get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get()) }
    viewModel { HomeViewModel(get(), get()) }
    viewModel { MainHeaderViewModel(get()) }
    viewModel { LibrariesViewModel(get(), get(), get()) }
    viewModel { ReadingHubViewModel(get(), get(), get()) }
    viewModel { RecommendationsViewModel(get()) }
    viewModel { SearchViewModel(get()) }
    viewModel { params -> BrowseViewModel(get(), params.get()) }
    viewModel { params -> ItemDetailViewModel(get(), get(), get(), get(), params.get()) }
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
    viewModel { SettingsViewModel(get(), get(), get(), get(), get(), get()) }
    viewModel { AdminEntryViewModel(get(), get()) }
    viewModel { AdminStatsViewModel(get()) }
    viewModel { AdminUsersViewModel(get()) }
    viewModel { AdminUserEditViewModel(get()) }
    viewModel { AdminSessionsViewModel(get()) }
    viewModel { AdminLogsViewModel(get()) }
    viewModel { AdminScansViewModel(get(), get()) }
    viewModel { DownloadsViewModel(get(), get(), get(), get(), get()) }
    viewModel { ServerSetupViewModel(get()) }
    viewModel { LoginViewModel(get()) }
    viewModel { SetupViewModel(get()) }
    viewModel { SignupViewModel(get()) }
    viewModel { ProfileSelectionViewModel(get()) }
    viewModel { CreateProfileViewModel(get()) }
    viewModel { EditProfileViewModel(get()) }
    viewModel { ServerListViewModel(get(), get()) }
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
        com.continuum.app.common.audiobook.AudiobookBookmarksStore(androidContext().filesDir)
    }
    // Audiobook position store — per-(server, profile, contentId) JSON
    // files under filesDir/audiobook_positions.
    single {
        com.continuum.app.common.audiobook.AudiobookPositionStore(androidContext().filesDir)
    }
    single {
        com.continuum.app.common.ebook.EbookLocalStateStore(androidContext().filesDir)
    }

    // Audiobook + book readers. SavedStateHandle is auto-injected via Koin's
    // viewModel scope wiring so the contentId nav arg flows through.
    viewModel {
        com.continuum.app.common.player.AudiobookPlayerViewModel(
            catalogRepository = get(),
            playbackSessionManager = get(),
            capabilityDetector = get(),
            bookmarksStore = get(),
            positionStore = get(),
            serverRegistry = get(),
            profileRepository = get(),
            offlineMediaResolver = get(),
            savedStateHandle = get(),
        )
    }
    viewModel {
        com.continuum.app.android.ui.screens.reader.ReaderViewModel(
            catalogRepository = get(),
            ebookReaderRepository = get(),
            offlineMediaResolver = get(),
            localStateStore = get(),
            serverRegistry = get(),
            profileRepository = get(),
            savedStateHandle = get(),
        )
    }
    viewModel { com.continuum.app.android.ui.screens.watchtogether.WatchTogetherEntryViewModel(get()) }
    viewModel { params ->
        com.continuum.app.android.ui.screens.watchtogether.WatchTogetherLobbyViewModel(
            roomId = params.get(),
            repository = get(),
        )
    }
}
