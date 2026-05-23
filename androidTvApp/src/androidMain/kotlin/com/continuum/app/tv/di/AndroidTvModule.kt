package com.continuum.app.tv.di

import com.continuum.app.tv.data.preferences.TvPreferences
import com.continuum.app.common.network.AndroidDeviceMetadataProvider
import com.continuum.app.common.settings.AndroidServerSettingsCache
import android.content.SharedPreferences
import com.continuum.app.network.AndroidServerRegistry
import com.continuum.app.network.EncryptedTokenManagerImpl
import com.continuum.app.network.ServerRegistry
import com.continuum.app.network.TokenManager
import com.continuum.app.network.createSecureSharedPrefs
import com.continuum.app.tv.ui.screens.servers.TvServerListViewModel
import com.continuum.app.common.player.AudioCapabilityManager
import com.continuum.app.common.player.AudioTrackManager
import com.continuum.app.common.player.PlaybackCapabilityDetector
import com.continuum.app.viewmodel.AdminViewModel
import com.continuum.app.tv.ui.screens.settings.TvSettingsViewModel
import com.continuum.app.common.player.ContinuumPlayerFactory
import com.continuum.app.common.player.PlaybackSessionManager
import com.continuum.app.common.player.SubtitleManager
import com.continuum.app.tv.ui.screens.auth.TvLoginViewModel
import com.continuum.app.tv.ui.screens.auth.TvServerSetupViewModel
import com.continuum.app.tv.ui.screens.collections.TvCollectionDetailViewModel
import com.continuum.app.viewmodel.CollectionsViewModel
import com.continuum.app.tv.ui.screens.detail.TvItemDetailViewModel
import com.continuum.app.viewmodel.HomeViewModel
import com.continuum.app.viewmodel.RecommendationsViewModel
import com.continuum.app.tv.ui.screens.libraries.TvLibrariesViewModel
import com.continuum.app.tv.ui.screens.library.TvLibraryCollectionDetailViewModel
import com.continuum.app.tv.ui.screens.library.TvLibraryDetailViewModel
import com.continuum.app.viewmodel.FavoritesViewModel
import com.continuum.app.viewmodel.HistoryViewModel
import com.continuum.app.viewmodel.WatchlistViewModel
import com.continuum.app.tv.ui.screens.player.TvPlayerViewModel
import com.continuum.app.tv.ui.screens.profiles.TvProfileSelectionViewModel
import com.continuum.app.tv.ui.screens.search.TvSearchViewModel
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/**
 * TV-specific Koin module.
 *
 * Provides the TV flavor of player infrastructure (currently a duplicate of the
 * phone module — see the TODO on [ContinuumPlayerFactory]) and the TV ViewModels.
 * The shared [com.continuum.app.di.sharedModules] is added alongside this one in
 * [com.continuum.app.tv.ContinuumTvApplication].
 */
val androidTvModule = module {
    // Single encrypted prefs handle shared between the server registry and
    // the token manager — see the phone module for rationale.
    single<SharedPreferences> { createSecureSharedPrefs(androidContext()) }

    // Multi-server registry. Loaded synchronously in init so MainTvActivity's
    // runBlocking-resolved start destination sees consistent state.
    single<ServerRegistry> { AndroidServerRegistry(androidContext(), get()) }

    // Persistent (EncryptedSharedPreferences-backed) replacement for the
    // commonMain in-memory TokenManager. Koin 3.1+ replaces same-key bindings
    // when the redefining module is loaded after the original — sharedModules()
    // is registered first in ContinuumTvApplication, so this wins.
    single<TokenManager> { EncryptedTokenManagerImpl(get(), get()) }

    single<AndroidServerSettingsCache> { AndroidServerSettingsCache(androidContext()) }
    single<com.continuum.app.network.DeviceMetadataProvider> {
        AndroidDeviceMetadataProvider(androidContext(), platform = "android-tv")
    }
    // Player infrastructure (duplicate-for-now; extract to :android-player later).
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

    // Preferences (DataStore-backed playback quality, subtitle defaults, etc.).
    single { TvPreferences(androidContext()) }

    // Auth ViewModels
    viewModel { TvServerSetupViewModel(get()) }
    viewModel { TvLoginViewModel(get(), get()) }
    viewModel { TvProfileSelectionViewModel(get(), get()) }
    viewModel { TvServerListViewModel(get(), get()) }

    // Content ViewModels
    viewModel { HomeViewModel(get(), get()) }
    viewModel { RecommendationsViewModel(get()) }
    viewModel { TvLibrariesViewModel(get(), get()) }
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
    viewModel { TvSearchViewModel(get()) }
    viewModel { params ->
        TvItemDetailViewModel(
            catalogRepository = get(),
            personalDataRepository = get(),
            contentId = params.get(),
        )
    }
    viewModel { params ->
        TvPlayerViewModel(
            catalogRepository = get(),
            playbackSessionManager = get(),
            profileRepository = get(),
            personalDataRepository = get(),
            capabilityDetector = get(),
            // Phase 3 TV uplift dependencies (per-profile settings, intro
            // auto-skip controller, lifecycle, sleep timer).
            playerSettingsStore = get(),
            introAutoSkipController = get(),
            sessionLifecycle = get(),
            sleepTimer = get(),
            contentId = params.get(),
            // Positional `getOrNull<Int>()` reads the 2nd parametersOf slot —
            // absent when callers opt for the "auto" version (episodes, rows).
            preferredFileId = params.getOrNull<Int>(),
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
            title = params.get(),
        )
    }

    // Settings.
    viewModel {
        TvSettingsViewModel(
            authRepository = get(),
            profileRepository = get(),
            tokenManager = get(),
            preferences = get(),
            settingsRepository = get(),
            settingsCache = get(),
            playerSettingsStore = get(),
            libraryPlaybackPrefsStore = get(),
        )
    }

    // Admin dashboard.
    viewModel { AdminViewModel(get()) }
}
