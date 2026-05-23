package com.continuum.app.tv.ui.screens.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.continuum.app.common.settings.AndroidServerSettingsCache
import com.continuum.app.common.settings.LibraryPlaybackPrefsStore
import com.continuum.app.common.settings.PlayerSettingsStore
import com.continuum.app.model.auth.User
import com.continuum.app.model.profile.UpdateProfileRequest
import com.continuum.app.model.settings.PlaybackSettingsKeys
import com.continuum.app.model.settings.SubtitleAppearance
import com.continuum.app.model.settings.SubtitleFontSizePreset
import com.continuum.app.network.ApiResult
import com.continuum.app.network.TokenManager
import com.continuum.app.repository.AuthRepository
import com.continuum.app.repository.ProfileRepository
import com.continuum.app.repository.SettingsRepository
import com.continuum.app.tv.data.preferences.PlaybackQuality
import com.continuum.app.tv.data.preferences.SubtitleMode
import com.continuum.app.tv.data.preferences.SubtitleSize
import com.continuum.app.tv.data.preferences.TvPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * ViewModel for the TV settings screen. Server-managed device settings
 * flow exclusively through [PlayerSettingsStore] (mirror of iOS
 * `PlayerSettings.shared`); profile-level subtitle prefs still go via
 * [profileRepository]. [TvPreferences] is retained only as the source of
 * the one-time legacy → server migration that runs on first boot.
 *
 * Sign-out and switch-profile operations emit a one-shot [NavAction]
 * signal that the screen collects and forwards to the top-level NavHost.
 */
class TvSettingsViewModel(
    private val authRepository: AuthRepository,
    private val profileRepository: ProfileRepository,
    private val tokenManager: TokenManager,
    private val preferences: TvPreferences,
    private val settingsRepository: SettingsRepository,
    private val settingsCache: AndroidServerSettingsCache,
    private val playerSettingsStore: PlayerSettingsStore,
    private val libraryPlaybackPrefsStore: LibraryPlaybackPrefsStore,
) : ViewModel() {

    enum class NavAction { SIGNED_OUT, SWITCH_PROFILE }

    data class UiState(
        val user: User? = null,
        val userLoading: Boolean = true,
        val userError: String? = null,
        val serverUrl: String = "",
        val playbackQuality: PlaybackQuality = PlaybackQuality.Auto,
        val subtitleMode: SubtitleMode = SubtitleMode.Auto,
        val subtitleLanguage: String = "",
        val subtitleSize: SubtitleSize = SubtitleSize.Medium,
        val autoPlayNext: Boolean = true,
        val autoSkipIntro: Boolean = false,
        val autoSkipCredits: Boolean = false,
        val navAction: NavAction? = null,
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    init {
        loadUser()
        loadSettings()
        observePlayerSettings()
    }

    fun loadUser() {
        viewModelScope.launch {
            _uiState.update { it.copy(userLoading = true, userError = null) }
            when (val r = authRepository.getCurrentUser()) {
                is ApiResult.Success -> _uiState.update {
                    it.copy(user = r.data, userLoading = false, userError = null)
                }
                is ApiResult.Error -> _uiState.update {
                    it.copy(
                        userLoading = false,
                        userError = r.message.ifBlank { "Failed to load user" },
                    )
                }
                is ApiResult.NetworkError -> _uiState.update {
                    it.copy(
                        userLoading = false,
                        userError = "Network error: ${r.exception.message ?: "unknown"}",
                    )
                }
            }
        }
    }

    private fun loadSettings() {
        viewModelScope.launch {
            val serverUrl = tokenManager.getServerUrl()
            _uiState.update { it.copy(serverUrl = serverUrl) }

            // One-shot import of pre-server-sync TvPreferences values.
            // Idempotent — gated by the legacy cache's migration sentinel.
            migrateLegacyTvPreferencesIfNeeded(serverUrl)

            // Pull effective device settings (cascade user → device → default).
            // The store writes them to its DataStore; observePlayerSettings()
            // mirrors them into _uiState.
            playerSettingsStore.refreshFromServer()

            when (val profileResult = profileRepository.getActiveProfileResult()) {
                is ApiResult.Success -> {
                    val profile = profileResult.data
                    _uiState.update {
                        it.copy(
                            subtitleMode = SubtitleMode.fromWire(profile.subtitleMode),
                            subtitleLanguage = profile.subtitleLanguage.orEmpty(),
                        )
                    }
                }
                is ApiResult.Error, is ApiResult.NetworkError -> Unit
            }
        }
    }

    /**
     * Mirror device-scoped flows into UI state. The store is the single
     * source of truth — this just projects to the TV-specific UI types
     * (PlaybackQuality, SubtitleSize).
     */
    private fun observePlayerSettings() {
        viewModelScope.launch {
            combine(
                playerSettingsStore.preferredQualityFlow,
                playerSettingsStore.autoPlayNextFlow,
                playerSettingsStore.autoSkipIntroFlow,
                playerSettingsStore.autoSkipCreditsFlow,
                playerSettingsStore.subtitleAppearanceFlow,
            ) { values ->
                @Suppress("UNCHECKED_CAST")
                val quality = values[0] as String
                @Suppress("UNCHECKED_CAST")
                val autoPlay = values[1] as Boolean
                @Suppress("UNCHECKED_CAST")
                val skipIntro = values[2] as Boolean
                @Suppress("UNCHECKED_CAST")
                val skipCredits = values[3] as Boolean
                @Suppress("UNCHECKED_CAST")
                val appearance = values[4] as SubtitleAppearance
                Snapshot(quality, autoPlay, skipIntro, skipCredits, appearance)
            }.collect { snap ->
                _uiState.update {
                    it.copy(
                        playbackQuality = PlaybackQuality.fromWire(snap.quality),
                        autoPlayNext = snap.autoPlay,
                        autoSkipIntro = snap.skipIntro,
                        autoSkipCredits = snap.skipCredits,
                        subtitleSize = snap.appearance.fontSize.toTvSubtitleSize(),
                    )
                }
            }
        }
    }

    fun onPlaybackQualityChanged(value: PlaybackQuality) {
        viewModelScope.launch { playerSettingsStore.setPreferredQuality(value.wireValue) }
    }

    fun onSubtitleModeChanged(value: SubtitleMode) {
        val previousState = _uiState.value
        _uiState.update { it.copy(subtitleMode = value) }
        persistProfileSubtitleSettings(previousState)
    }

    fun onSubtitleLanguageChanged(value: String) {
        val previousState = _uiState.value
        _uiState.update { it.copy(subtitleLanguage = value) }
        persistProfileSubtitleSettings(previousState)
    }

    fun onSubtitleSizeChanged(value: SubtitleSize) {
        viewModelScope.launch {
            val current = playerSettingsStore.subtitleAppearanceFlow.first()
            val updated = current.copy(fontSize = value.toFontSizePreset())
            playerSettingsStore.setSubtitleAppearance(updated)
        }
    }

    fun onAutoPlayNextChanged(value: Boolean) {
        viewModelScope.launch { playerSettingsStore.setAutoPlayNext(value) }
    }

    fun onAutoSkipIntroChanged(value: Boolean) {
        viewModelScope.launch { playerSettingsStore.setAutoSkipIntro(value) }
    }

    fun onAutoSkipCreditsChanged(value: Boolean) {
        viewModelScope.launch { playerSettingsStore.setAutoSkipCredits(value) }
    }

    /**
     * Clear every server-side device override for this device. Mirrors
     * iOS tvOS "Reset Playback Overrides" (TVSettingsView.swift:137).
     */
    fun resetPlaybackOverrides() {
        viewModelScope.launch { playerSettingsStore.resetAllDeviceSettings() }
    }

    /** Lifecycle hook — call from MainTvActivity.onStop. */
    fun flushPendingSettings() {
        viewModelScope.launch { playerSettingsStore.flushPendingDeviceSettings() }
    }

    fun onSignOut(context: Context) {
        viewModelScope.launch {
            playerSettingsStore.flushPendingDeviceSettings()
            authRepository.logout()
            profileRepository.clearProfile()
            tokenManager.clearTokens()
            // Drop per-profile cached prefs so the next user doesn't see
            // them flash before the fresh fetch lands. iOS parity:
            // `PlaybackPrefsStore.clear()` in the sign-out path.
            libraryPlaybackPrefsStore.clear()
            context.getSharedPreferences("continuum_auth", Context.MODE_PRIVATE)
                .edit()
                .clear()
                .apply()
            _uiState.update { it.copy(navAction = NavAction.SIGNED_OUT) }
        }
    }

    fun onSwitchProfile(context: Context) {
        viewModelScope.launch {
            playerSettingsStore.flushPendingDeviceSettings()
            profileRepository.clearProfile()
            // Library prefs are per-profile — drop the cache so the next
            // profile's prefs don't ghost-render the previous user's rows.
            libraryPlaybackPrefsStore.clear()
            context.getSharedPreferences("continuum_auth", Context.MODE_PRIVATE)
                .edit()
                .remove("profileId")
                .apply()
            _uiState.update { it.copy(navAction = NavAction.SWITCH_PROFILE) }
        }
    }

    fun onNavActionConsumed() {
        _uiState.update { it.copy(navAction = null) }
    }

    private fun persistProfileSubtitleSettings(previousState: UiState) {
        val state = _uiState.value
        viewModelScope.launch {
            when (
                profileRepository.updateActiveProfile(
                    UpdateProfileRequest(
                        subtitleLanguage = state.subtitleLanguage.ifBlank { null },
                        subtitleMode = state.subtitleMode.wireValue,
                    )
                )
            ) {
                is ApiResult.Success -> Unit
                is ApiResult.Error, is ApiResult.NetworkError -> {
                    _uiState.update { current ->
                        if (
                            current.subtitleLanguage == state.subtitleLanguage &&
                            current.subtitleMode == state.subtitleMode
                        ) {
                            current.copy(
                                subtitleLanguage = previousState.subtitleLanguage,
                                subtitleMode = previousState.subtitleMode,
                            )
                        } else {
                            current
                        }
                    }
                }
            }
        }
    }

    /**
     * One-shot import of legacy [TvPreferences] values. Pushes each
     * value as a device-scoped override only when the server reports no
     * existing override for the same key. Gated by the legacy cache's
     * migration sentinel so it runs at most once per (server, profile,
     * device) combination.
     */
    private suspend fun migrateLegacyTvPreferencesIfNeeded(serverUrl: String) {
        if (serverUrl.isBlank() || settingsCache.isMigrationComplete(serverUrl, MIGRATION_SCOPE)) {
            return
        }

        val legacyQuality = preferences.playbackQuality.first().wireValue
        val legacySubtitleSize = preferences.subtitleSize.first()
        val legacyAutoPlayNext = preferences.autoPlayNextEpisode.first()
        val legacyAutoSkipIntro = preferences.autoSkipIntro.first()
        val legacyAutoSkipCredits = preferences.autoSkipCredits.first()

        val effective = when (
            val result = settingsRepository.getEffectiveSettings(
                listOf(
                    PlaybackSettingsKeys.PreferredQuality,
                    PlaybackSettingsKeys.AutoPlayNext,
                    PlaybackSettingsKeys.AutoSkipIntro,
                    PlaybackSettingsKeys.AutoSkipCredits,
                    PlaybackSettingsKeys.SubtitleAppearance,
                )
            )
        ) {
            is ApiResult.Success -> result.data
            is ApiResult.Error, is ApiResult.NetworkError -> emptyMap()
        }

        if (effective[PlaybackSettingsKeys.PreferredQuality]?.hasDeviceOverride != true) {
            playerSettingsStore.setPreferredQuality(legacyQuality)
        }
        if (effective[PlaybackSettingsKeys.AutoPlayNext]?.hasDeviceOverride != true) {
            playerSettingsStore.setAutoPlayNext(legacyAutoPlayNext)
        }
        if (effective[PlaybackSettingsKeys.AutoSkipIntro]?.hasDeviceOverride != true) {
            playerSettingsStore.setAutoSkipIntro(legacyAutoSkipIntro)
        }
        if (effective[PlaybackSettingsKeys.AutoSkipCredits]?.hasDeviceOverride != true) {
            playerSettingsStore.setAutoSkipCredits(legacyAutoSkipCredits)
        }
        if (effective[PlaybackSettingsKeys.SubtitleAppearance]?.hasDeviceOverride != true) {
            playerSettingsStore.setSubtitleAppearance(
                SubtitleAppearance.DEFAULT.copy(fontSize = legacySubtitleSize.toFontSizePreset())
            )
        }

        // Make sure the writes hit the server even if the user backs out
        // before the debounce fires.
        playerSettingsStore.flushPendingDeviceSettings()
        settingsCache.markMigrationComplete(serverUrl, MIGRATION_SCOPE)
    }

    private fun SubtitleSize.toFontSizePreset(): SubtitleFontSizePreset = when (this) {
        SubtitleSize.Small -> SubtitleFontSizePreset.Small
        SubtitleSize.Medium -> SubtitleFontSizePreset.Medium
        SubtitleSize.Large -> SubtitleFontSizePreset.Large
    }

    private fun SubtitleFontSizePreset.toTvSubtitleSize(): SubtitleSize = when (this) {
        SubtitleFontSizePreset.Small -> SubtitleSize.Small
        SubtitleFontSizePreset.Medium -> SubtitleSize.Medium
        // Large / XLarge / XXLarge — collapse anything bigger than Medium
        // back onto Large in the TV picker (the TV UI only exposes 3 sizes).
        SubtitleFontSizePreset.Large,
        SubtitleFontSizePreset.XLarge,
        SubtitleFontSizePreset.XXLarge -> SubtitleSize.Large
    }

    private data class Snapshot(
        val quality: String,
        val autoPlay: Boolean,
        val skipIntro: Boolean,
        val skipCredits: Boolean,
        val appearance: SubtitleAppearance,
    )

    private companion object {
        const val MIGRATION_SCOPE = "android-tv-settings"
    }
}
