package com.continuum.app.android.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.continuum.app.common.settings.LibraryPlaybackPrefsStore
import com.continuum.app.common.settings.PlayerSettingsStore
import com.continuum.app.model.auth.AuthSession
import com.continuum.app.model.auth.User
import com.continuum.app.model.profile.UpdateProfileRequest
import com.continuum.app.network.ApiResult
import com.continuum.app.android.ui.theme.ThemeManager
import com.continuum.app.repository.AuthRepository
import com.continuum.app.repository.ProfileRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Theme preference for the app appearance.
 */
enum class ThemePreference(val label: String) {
    SYSTEM("System"),
    DARK("Dark"),
    LIGHT("Light"),
}

/**
 * Subtitle display mode.
 */
enum class SubtitleMode(val label: String) {
    OFF("Off"),
    AUTO("Auto"),
    ALWAYS("Always"),
}

data class SettingsUiState(
    // Account
    val user: User? = null,
    val serverUrl: String = "",
    val isLoadingUser: Boolean = false,
    val sessions: List<AuthSession> = emptyList(),
    val isLoadingSessions: Boolean = false,
    val showSessions: Boolean = false,
    val loggedOut: Boolean = false,

    // Appearance
    val theme: ThemePreference = ThemePreference.SYSTEM,

    // Playback
    val defaultQuality: String = "Auto",
    val audioLanguage: String = "Default",
    val autoSkipIntro: Boolean = false,
    val autoSkipCredits: Boolean = false,

    // Subtitles
    val subtitleLanguage: String = "Off",
    val subtitleMode: SubtitleMode = SubtitleMode.AUTO,
    val showForcedSubtitles: Boolean = true,
)

class SettingsViewModel(
    private val authRepository: AuthRepository,
    private val themeManager: ThemeManager,
    private val playerSettingsStore: PlayerSettingsStore,
    private val profileRepository: ProfileRepository,
    private val libraryPlaybackPrefsStore: LibraryPlaybackPrefsStore,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        loadUserInfo()
        observePlayerSettings()
        _uiState.update { it.copy(theme = themeManager.themePreference.value) }
    }

    private fun loadUserInfo() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingUser = true) }
            when (val result = authRepository.getCurrentUser()) {
                is ApiResult.Success -> {
                    _uiState.update { it.copy(user = result.data, isLoadingUser = false) }
                }
                is ApiResult.Error, is ApiResult.NetworkError -> {
                    _uiState.update { it.copy(isLoadingUser = false) }
                }
            }

            val serverUrl = authRepository.getServerUrl()
            _uiState.update { it.copy(serverUrl = serverUrl) }

            playerSettingsStore.refreshFromServer()

            when (val profileResult = profileRepository.getActiveProfileResult()) {
                is ApiResult.Success -> {
                    val profile = profileResult.data
                    _uiState.update {
                        it.copy(
                            subtitleLanguage = profile.subtitleLanguage?.ifBlank { "Off" } ?: "Off",
                            subtitleMode = subtitleModeFromServer(profile.subtitleMode),
                            showForcedSubtitles = profile.showForcedSubtitles ?: true,
                        )
                    }
                }
                is ApiResult.Error, is ApiResult.NetworkError -> Unit
            }
        }
    }

    private data class PlayerSettingsSnapshot(
        val quality: String,
        val audioLanguage: String,
        val autoSkipIntro: Boolean,
        val autoSkipCredits: Boolean,
    )

    private fun observePlayerSettings() {
        combine(
            playerSettingsStore.preferredQualityFlow,
            playerSettingsStore.audioLanguageFlow,
            playerSettingsStore.autoSkipIntroFlow,
            playerSettingsStore.autoSkipCreditsFlow,
            ::PlayerSettingsSnapshot,
        ).onEach { snap ->
            _uiState.update {
                it.copy(
                    defaultQuality = qualityLabel(snap.quality),
                    audioLanguage = audioLanguageLabel(snap.audioLanguage),
                    autoSkipIntro = snap.autoSkipIntro,
                    autoSkipCredits = snap.autoSkipCredits,
                )
            }
        }.launchIn(viewModelScope)
    }

    fun loadSessions() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingSessions = true, showSessions = true) }
            when (val result = authRepository.getSessions()) {
                is ApiResult.Success -> {
                    _uiState.update {
                        it.copy(sessions = result.data, isLoadingSessions = false)
                    }
                }
                is ApiResult.Error, is ApiResult.NetworkError -> {
                    _uiState.update { it.copy(isLoadingSessions = false) }
                }
            }
        }
    }

    fun hideSessions() {
        _uiState.update { it.copy(showSessions = false) }
    }

    fun revokeSession(id: String) {
        viewModelScope.launch {
            when (authRepository.deleteSession(id)) {
                is ApiResult.Success -> {
                    _uiState.update { state ->
                        state.copy(sessions = state.sessions.filter { it.id != id })
                    }
                }
                is ApiResult.Error, is ApiResult.NetworkError -> Unit
            }
        }
    }

    fun logout() {
        viewModelScope.launch {
            // Push any in-flight settings before tearing down the session.
            playerSettingsStore.flushPendingDeviceSettings()
            authRepository.logout()
            // Drop per-profile cached prefs so the next user doesn't see
            // stale rows flash before the fresh fetch lands.
            libraryPlaybackPrefsStore.clear()
            _uiState.update { it.copy(loggedOut = true) }
        }
    }

    fun onLogoutConsumed() {
        _uiState.update { it.copy(loggedOut = false) }
    }

    // -- Appearance --

    fun setTheme(theme: ThemePreference) {
        themeManager.setTheme(theme)
        _uiState.update { it.copy(theme = theme) }
    }

    // -- Playback --

    fun setDefaultQuality(quality: String) {
        viewModelScope.launch {
            playerSettingsStore.setPreferredQuality(qualityWireValue(quality))
        }
    }

    fun setAudioLanguage(language: String) {
        viewModelScope.launch {
            playerSettingsStore.setAudioLanguage(audioLanguageWireValue(language))
        }
    }

    fun setAutoSkipIntro(enabled: Boolean) {
        viewModelScope.launch { playerSettingsStore.setAutoSkipIntro(enabled) }
    }

    fun setAutoSkipCredits(enabled: Boolean) {
        viewModelScope.launch { playerSettingsStore.setAutoSkipCredits(enabled) }
    }

    fun resetPlaybackOverrides() {
        viewModelScope.launch { playerSettingsStore.resetAllDeviceSettings() }
    }

    /** Lifecycle hook — call from ON_STOP so debounced writes survive. */
    fun flushPendingSettings() {
        viewModelScope.launch { playerSettingsStore.flushPendingDeviceSettings() }
    }

    // -- Subtitles --

    fun setSubtitleLanguage(language: String) {
        _uiState.update { it.copy(subtitleLanguage = language) }
        persistProfileSubtitleSettings()
    }

    fun setSubtitleMode(mode: SubtitleMode) {
        _uiState.update { it.copy(subtitleMode = mode) }
        persistProfileSubtitleSettings()
    }

    fun setShowForcedSubtitles(enabled: Boolean) {
        _uiState.update { it.copy(showForcedSubtitles = enabled) }
        persistProfileSubtitleSettings()
    }

    private fun persistProfileSubtitleSettings() {
        val state = _uiState.value
        viewModelScope.launch {
            profileRepository.updateActiveProfile(
                UpdateProfileRequest(
                    subtitleLanguage = state.subtitleLanguage.takeUnless { it == "Off" },
                    subtitleMode = state.subtitleMode.toServerValue(),
                    showForcedSubtitles = state.showForcedSubtitles,
                )
            )
        }
    }

    private fun qualityLabel(value: String): String =
        when (value.lowercase()) {
            "auto" -> "Auto"
            "original" -> "Original"
            else -> value.uppercase()
        }

    private fun qualityWireValue(value: String): String =
        when (value) {
            "Auto" -> "auto"
            "Original" -> "original"
            else -> value.lowercase()
        }

    private fun audioLanguageLabel(value: String): String =
        value.ifBlank { "Default" }

    private fun audioLanguageWireValue(value: String): String =
        value.takeUnless { it == "Default" }.orEmpty()

    private fun subtitleModeFromServer(value: String?): SubtitleMode =
        when (value?.lowercase()) {
            "off" -> SubtitleMode.OFF
            "always" -> SubtitleMode.ALWAYS
            else -> SubtitleMode.AUTO
        }

    private fun SubtitleMode.toServerValue(): String =
        when (this) {
            SubtitleMode.OFF -> "off"
            SubtitleMode.AUTO -> "auto"
            SubtitleMode.ALWAYS -> "always"
        }
}
