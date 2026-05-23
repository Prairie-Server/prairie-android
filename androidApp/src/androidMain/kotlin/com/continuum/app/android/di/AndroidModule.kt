package com.continuum.app.android.di

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
import com.continuum.app.android.ui.screens.browse.BrowseViewModel
import com.continuum.app.android.ui.screens.collections.CollectionDetailViewModel
import com.continuum.app.android.ui.screens.collections.LibraryCollectionsViewModel
import com.continuum.app.viewmodel.CollectionsViewModel
import com.continuum.app.android.ui.screens.detail.ItemDetailViewModel
import com.continuum.app.android.ui.screens.people.PersonDetailViewModel
import com.continuum.app.android.ui.screens.auth.LoginViewModel
import com.continuum.app.android.ui.screens.auth.ServerSetupViewModel
import com.continuum.app.android.ui.screens.auth.SetupViewModel
import com.continuum.app.android.ui.screens.auth.SignupViewModel
import com.continuum.app.android.ui.screens.MainHeaderViewModel
import com.continuum.app.viewmodel.AdminViewModel
import com.continuum.app.android.ui.screens.profiles.ProfileSelectionViewModel
import com.continuum.app.android.ui.screens.servers.ServerListViewModel
import com.continuum.app.android.ui.screens.downloads.DownloadsViewModel
import com.continuum.app.viewmodel.HomeViewModel
import com.continuum.app.android.ui.screens.libraries.LibrariesViewModel
import com.continuum.app.viewmodel.FavoritesViewModel
import com.continuum.app.viewmodel.HistoryViewModel
import com.continuum.app.viewmodel.RecommendationsViewModel
import com.continuum.app.viewmodel.WatchlistViewModel
import com.continuum.app.android.ui.screens.player.PlayerViewModel
import com.continuum.app.android.ui.screens.search.SearchViewModel
import com.continuum.app.android.ui.screens.settings.SettingsViewModel
import com.continuum.app.android.ui.theme.ThemeManager
import org.koin.android.ext.koin.androidContext
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
        )
    }
    single { PlaybackSessionManager(get(), get()) }

    // ViewModels
    factory { PlayerViewModel(get(), get(), get(), get(), get(), get(), get(), get(), get()) }
    viewModel { HomeViewModel(get(), get()) }
    viewModel { MainHeaderViewModel(get()) }
    viewModel { LibrariesViewModel(get(), get(), get()) }
    viewModel { RecommendationsViewModel(get()) }
    viewModel { SearchViewModel(get()) }
    viewModel { params -> BrowseViewModel(get(), params.get()) }
    viewModel { params -> ItemDetailViewModel(get(), get(), params.get()) }
    viewModel { params -> PersonDetailViewModel(get(), params.get()) }
    viewModel { params -> LibraryCollectionsViewModel(get(), params.get()) }
    viewModel { FavoritesViewModel(get()) }
    viewModel { WatchlistViewModel(get()) }
    viewModel { HistoryViewModel(get()) }
    viewModel { CollectionsViewModel(get()) }
    viewModel { params -> CollectionDetailViewModel(get(), get(), params.get()) }
    viewModel { SettingsViewModel(get(), get(), get(), get(), get()) }
    viewModel { DownloadsViewModel() }
    viewModel { AdminViewModel(get()) }
    viewModel { ServerSetupViewModel(get()) }
    viewModel { LoginViewModel(get()) }
    viewModel { SetupViewModel(get()) }
    viewModel { SignupViewModel(get()) }
    viewModel { ProfileSelectionViewModel(get()) }
    viewModel { ServerListViewModel(get(), get()) }
}
