package com.continuum.app.common.di

import com.continuum.app.common.player.PlaybackSessionLifecycle
import com.continuum.app.common.player.SleepTimerController
import com.continuum.app.common.settings.AndroidPlayerSettingsStore
import com.continuum.app.common.settings.DefaultLibraryPlaybackPrefsStore
import com.continuum.app.common.settings.DefaultServerSettingsFlusher
import com.continuum.app.common.settings.LibraryPlaybackPrefsStore
import com.continuum.app.common.settings.PlayerSettingsStore
import com.continuum.app.common.settings.ServerSettingsFlusher
import com.continuum.app.domain.player.IntroAutoSkipController
import com.continuum.app.network.DeviceMetadataProvider
import com.continuum.app.network.ServerRegistry
import com.continuum.app.network.TokenManager
import com.continuum.app.repository.LibraryPlaybackPrefsRepository
import com.continuum.app.repository.ProfileRepository
import com.continuum.app.repository.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

/**
 * Phase 0 player infrastructure DI module.
 *
 * Sibling of [playerModule] (which owns OkHttp + interceptors). This module
 * registers the new per-profile settings store, server-settings flusher, and
 * playback session lifecycle. ViewModels in Phase 0 still consume the legacy
 * [com.continuum.app.common.player.PlaybackSessionManager] directly; the
 * lifecycle here is wired but unused until Phase 1+ migrations.
 */
val playerInfraModule = module {
    // Long-lived application-scope flusher: debounced server writes survive
    // ViewModel teardown. Uses Dispatchers.IO since flushOne does network work.
    single<ServerSettingsFlusher> {
        DefaultServerSettingsFlusher(
            settingsApi = get(),
            scope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
        )
    }

    // Per-profile DataStore-backed settings store with legacy SharedPreferences
    // migration. We resolve the active profile on demand via ProfileRepository,
    // and the current server URL via TokenManager — both of which the auth
    // flow keeps up to date (TokenManager.setServerUrl on connect, profile id
    // saved on profile selection).
    single<PlayerSettingsStore> {
        val registry = get<ServerRegistry>()
        val profileChangeSignal = registry.activeEntry
            .map { it?.profileId }
            .distinctUntilChanged()
            .map { Unit }
        val serverChangeSignal = registry.activeEntry
            .map { it?.url }
            .distinctUntilChanged()
            .map { Unit }
        AndroidPlayerSettingsStore(
            context = androidContext(),
            legacyCache = get(),
            getActiveProfileId = { get<ProfileRepository>().getActiveProfileId() },
            getServerUrl = { get<TokenManager>().getServerUrl() },
            serverSettingsFlusher = get(),
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate),
            profileChangeSignal = profileChangeSignal,
            serverChangeSignal = serverChangeSignal,
            settingsRepository = get<SettingsRepository>(),
            // Reuse the same device id the rest of the app sends to the
            // server so settings scope stays in lockstep with what the
            // server records as `device_id` for each override.
            getDeviceId = { get<DeviceMetadataProvider>().current()?.id },
        )
    }

    single<LibraryPlaybackPrefsStore> {
        DefaultLibraryPlaybackPrefsStore(
            repository = get<LibraryPlaybackPrefsRepository>(),
        )
    }

    // Singleton lifecycle. v1 trade-off: there is only ever one active player
    // session at a time, and start()/stop() handle cleanup between sessions.
    // ViewModels can `get<PlaybackSessionLifecycle>()` once Phase 1+ migrates
    // them; for now this binding is registered but unobserved.
    single<PlaybackSessionLifecycle> {
        PlaybackSessionLifecycle(
            sessionManager = get(),
            profileRepository = get(),
            healthApi = get(),
            personalDataRepository = get(),
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate),
        )
    }

    // Phase 2 sleep timer. Pinned to Main.immediate so the on-fire callback
    // (typically `_uiState.update { isPaused = true }`) lands on the same
    // thread the player VM and MediaController already use, avoiding an extra
    // dispatch hop.
    single<SleepTimerController> {
        SleepTimerController(
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate),
        )
    }

    factory<IntroAutoSkipController> {
        IntroAutoSkipController(
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate),
        )
    }
}
