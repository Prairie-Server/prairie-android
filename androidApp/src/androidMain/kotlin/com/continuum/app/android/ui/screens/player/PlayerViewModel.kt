package com.continuum.app.android.ui.screens.player

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.continuum.app.common.player.PlaybackCapabilityDetector
import com.continuum.app.common.player.PlaybackSessionLifecycle
import com.continuum.app.common.player.PlaybackSessionManager
import com.continuum.app.common.player.PlayerNotice
import com.continuum.app.common.player.SessionState
import com.continuum.app.common.player.SleepTimerController
import com.continuum.app.common.player.SleepTimerState
import com.continuum.app.common.player.StartParams
import com.continuum.app.common.settings.PlayerSettingsStore
import com.continuum.app.domain.player.IntroAutoSkipController
import com.continuum.app.domain.player.IntroAutoSkipState
import com.continuum.app.model.catalog.AudioTrack
import com.continuum.app.model.catalog.FileVersion
import com.continuum.app.model.catalog.TimeRange
import com.continuum.app.model.settings.SubtitleAppearance
import com.continuum.app.model.playback.PlayMethod
import com.continuum.app.model.playback.PlaybackSessionResponse
import com.continuum.app.model.playback.PlayerSubtitleInfo
import com.continuum.app.network.ApiResult
import com.continuum.app.repository.CatalogRepository
import com.continuum.app.repository.PersonalDataRepository
import com.continuum.app.repository.ProfileRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * ViewModel for the video player screen.
 *
 * Orchestrates content loading, playback session management, progress reporting,
 * and UI state for controls overlay, subtitle/audio selection, and intro/credits detection.
 *
 * Phase 1: progress reporting + 404/outage recovery is now delegated to
 * [PlaybackSessionLifecycle]. Per-profile playback preferences are read from
 * [PlayerSettingsStore]. Intro auto-skip behavior (countdown ring, cancel,
 * one-shot fire) is owned by [IntroAutoSkipController].
 */
class PlayerViewModel(
    private val catalogRepository: CatalogRepository,
    private val playbackSessionManager: PlaybackSessionManager,
    private val profileRepository: ProfileRepository,
    private val personalDataRepository: PersonalDataRepository,
    private val capabilityDetector: PlaybackCapabilityDetector,
    // Phase 1 Phase 0-infra dependencies:
    private val playerSettingsStore: PlayerSettingsStore,
    private val introAutoSkipController: IntroAutoSkipController,
    private val sessionLifecycle: PlaybackSessionLifecycle,
    // Phase 2 sleep timer:
    private val sleepTimer: SleepTimerController,
) : ViewModel() {

    companion object {
        private const val TAG = "PlayerViewModel"
        private const val CONTROLS_AUTO_HIDE_MS = 3_000L
    }

    data class PlayerUiState(
        val isLoading: Boolean = true,
        val error: String? = null,
        val title: String = "",
        val subtitle: String = "",
        val sessionId: String? = null,
        val playMethod: PlayMethod? = null,
        val streamUrl: String? = null,
        val serverUrl: String = "",
        val accessToken: String = "",
        val startPosition: Double = 0.0,
        val position: Double = 0.0,
        val duration: Double = 0.0,
        val isPlaying: Boolean = false,
        val isPaused: Boolean = false,
        val subtitleTracks: List<PlayerSubtitleInfo> = emptyList(),
        val audioTracks: List<AudioTrack> = emptyList(),
        val selectedAudioIndex: Int = 0,
        val selectedSubtitleIndex: Int = -1,
        val intro: TimeRange? = null,
        val credits: TimeRange? = null,
        val showNextEpisode: Boolean = false,
        val showControls: Boolean = true,
        val isBuffering: Boolean = false,
        val versions: List<FileVersion> = emptyList(),
        val selectedVersionIndex: Int = 0,
        val contentId: String = "",
        val seriesId: String? = null,
        val preferredAudioLanguage: String? = null,
        val preferredTextLanguage: String? = null,
    )

    private val _uiState = MutableStateFlow(PlayerUiState())
    val uiState: StateFlow<PlayerUiState> = _uiState.asStateFlow()

    /** Intro auto-skip banner state. UI consumes this directly. */
    val introSkipState: StateFlow<IntroAutoSkipState> = introAutoSkipController.state

    /**
     * Transient player notice (server reconnecting, suspend warnings, etc.) emitted by
     * [PlaybackSessionLifecycle]. `null` means show nothing. UI consumes this directly.
     */
    val notice: StateFlow<PlayerNotice?> = sessionLifecycle.notice

    /**
     * Lifecycle session state. UI consumes this to drive the buffering spinner during
     * outage Reconnecting (which the underlying ExoPlayer can't observe).
     */
    val sessionState: StateFlow<SessionState> = sessionLifecycle.state

    // ---- Player settings flows (per-profile, DataStore-backed) -----------------
    val playbackSpeed: StateFlow<Double> = playerSettingsStore.playbackSpeedFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, 1.0)
    val videoGravity: StateFlow<String> = playerSettingsStore.videoGravityFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, "fit")
    val autoSkipIntroEnabled: StateFlow<Boolean> = playerSettingsStore.autoSkipIntroFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)
    val autoPlayNextEnabled: StateFlow<Boolean> = playerSettingsStore.autoPlayNextFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)
    val hdrEnabled: StateFlow<Boolean> = playerSettingsStore.hdrEnabledFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)
    val subtitleAppearance: StateFlow<SubtitleAppearance> = playerSettingsStore.subtitleAppearanceFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, SubtitleAppearance.DEFAULT)

    // ---- Sleep timer ------------------------------------------------------------
    /** Live state of the sleep-timer (Idle or Active(remainingSeconds)). */
    val sleepTimerState: StateFlow<SleepTimerState> = sleepTimer.state

    /** Default duration shown in the picker — persists across sessions. */
    val sleepTimerDefaultMinutes: StateFlow<Int> = playerSettingsStore.sleepTimerDefaultMinutesFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, 30)

    private var controlsHideJob: Job? = null
    private var introObserverJob: Job? = null
    private var lifecycleObserverJob: Job? = null

    init {
        // Mirror lifecycle Failed state into the UI error field so the user sees a
        // notice when outage recovery times out or the session fails to start. The
        // notice flow is intentionally *not* surfaced here — that's Phase 3 work.
        lifecycleObserverJob = viewModelScope.launch {
            sessionLifecycle.state.collect { state ->
                if (state is SessionState.Failed) {
                    _uiState.update { current ->
                        if (current.error == null) current.copy(error = state.message) else current
                    }
                }
            }
        }

        // When the sleep timer fires, flip user intent to paused. PlayerScreen
        // mirrors `isPaused` to `mediaController.playWhenReady`, so this is
        // sufficient to halt playback without going through onPlayPause()
        // (which is a *toggle* and would inadvertently resume a paused player).
        sleepTimer.configure {
            _uiState.update { it.copy(isPaused = true) }
        }
    }

    /**
     * Loads content metadata and starts a playback session.
     * This is the main entry point called when the player screen is first displayed.
     */
    fun loadContent(
        contentId: String,
        preferredFileId: Int? = null,
        initialAudioTrackIndex: Int? = null,
        initialSubtitleTrackIndex: Int? = null,
        resumePositionOverride: Double? = null,
    ) {
        // A fresh load resets any in-flight intro countdown / cancellation memory.
        introAutoSkipController.reset()

        _uiState.update { it.copy(isLoading = true, error = null, contentId = contentId) }

        viewModelScope.launch {
            try {
                // Fetch watch detail for versions, user progress, intro/credits markers
                val watchDetailResult = catalogRepository.getWatchDetail(contentId)
                val watchDetail = when (watchDetailResult) {
                    is ApiResult.Success -> watchDetailResult.data
                    is ApiResult.Error -> {
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                error = "Failed to load content: ${watchDetailResult.message}",
                            )
                        }
                        return@launch
                    }
                    is ApiResult.NetworkError -> {
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                error = "Network error: ${watchDetailResult.exception.message}",
                            )
                        }
                        return@launch
                    }
                }

                if (watchDetail.versions.isEmpty()) {
                    _uiState.update {
                        it.copy(isLoading = false, error = "No playable versions available")
                    }
                    return@launch
                }

                // Build display title
                val displayTitle = watchDetail.title
                val displaySubtitle = buildSubtitle(watchDetail)

                // Determine which version to play (prefer last-used or first).
                // Read from the player-settings store so device + user
                // overrides win — `settingsCache` was the pre-store source
                // and is now stale once `refreshFromServer()` has run.
                val serverUrl = playbackSessionManager.getServerUrl()
                val preferredQuality = playerSettingsStore.preferredQualityFlow.first()
                val preferredAudioLanguage = playerSettingsStore.audioLanguageFlow
                    .first().ifBlank { null }
                val versionIndex = findPreferredVersion(watchDetail, preferredFileId, preferredQuality)
                val version = watchDetail.versions[versionIndex]

                // Get active profile — language preferences flow into the
                // track selector so tracks in the preferred audio / subtitle
                // language win over codec-equivalent alternatives.
                val activeProfile = profileRepository.getActiveProfile()
                val profileId = activeProfile?.id ?: profileRepository.getActiveProfileId()
                if (profileId == null) {
                    _uiState.update {
                        it.copy(isLoading = false, error = "No active profile selected")
                    }
                    return@launch
                }
                _uiState.update {
                    it.copy(
                        preferredAudioLanguage = preferredAudioLanguage ?: activeProfile?.language,
                        preferredTextLanguage = activeProfile?.subtitleLanguage,
                    )
                }

                // Get server URL and token for stream auth
                val accessToken = playbackSessionManager.getAccessToken()
                if (accessToken == null) {
                    _uiState.update {
                        it.copy(isLoading = false, error = "Not authenticated")
                    }
                    return@launch
                }

                // Start playback session
                val capabilities = capabilityDetector.detect()
                val sessionResult = playbackSessionManager.startSession(
                    fileId = version.fileId,
                    profileId = profileId,
                    capabilities = capabilities,
                    audioTrackIndex = initialAudioTrackIndex,
                    qualityPreference = preferredQuality,
                    startPosition = resumePositionOverride,
                )

                when (sessionResult) {
                    is ApiResult.Success -> {
                        val session = sessionResult.data
                        handleSessionStarted(
                            session = session,
                            watchDetail = watchDetail,
                            displayTitle = displayTitle,
                            displaySubtitle = displaySubtitle,
                            versionIndex = versionIndex,
                            serverUrl = serverUrl,
                            accessToken = accessToken,
                            initialAudioTrackIndex = initialAudioTrackIndex,
                            initialSubtitleTrackIndex = initialSubtitleTrackIndex,
                            resumePositionOverride = resumePositionOverride,
                            capabilities = capabilities,
                            preferredQuality = preferredQuality,
                        )
                    }
                    is ApiResult.Error -> {
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                error = "Failed to start playback: ${sessionResult.message}",
                            )
                        }
                    }
                    is ApiResult.NetworkError -> {
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                error = "Network error: ${sessionResult.exception.message}",
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading content", e)
                _uiState.update {
                    it.copy(isLoading = false, error = "Unexpected error: ${e.message}")
                }
            }
        }
    }

    private suspend fun handleSessionStarted(
        session: PlaybackSessionResponse,
        watchDetail: com.continuum.app.model.catalog.WatchDetail,
        displayTitle: String,
        displaySubtitle: String,
        versionIndex: Int,
        serverUrl: String,
        accessToken: String,
        initialAudioTrackIndex: Int?,
        initialSubtitleTrackIndex: Int?,
        resumePositionOverride: Double?,
        capabilities: com.continuum.app.model.playback.ClientCodecCapabilities,
        preferredQuality: String?,
    ) {
        // Both remux and transcode need HLS delivery. Only direct play uses
        // the progressive /stream/{id} URL. Server picked the path — we just
        // translate it into the HLS session via startTranscodeFallback.
        if (session.playMethod == PlayMethod.TRANSCODE || session.playMethod == PlayMethod.REMUX) {
            val mode = if (session.playMethod == PlayMethod.REMUX) {
                com.continuum.app.common.player.PlaybackSessionManager.TranscodeMode.REMUX
            } else {
                com.continuum.app.common.player.PlaybackSessionManager.TranscodeMode.FULL
            }
            when (val r = playbackSessionManager.startTranscodeFallback(
                session = session,
                seekSeconds = resumePositionOverride ?: watchDetail.userData?.positionSeconds ?: 0.0,
                resolution = watchDetail.versions[versionIndex].resolution.orEmpty(),
                mode = mode,
            )) {
                is ApiResult.Success -> applySessionToState(
                    session = r.data,
                    watchDetail = watchDetail,
                    displayTitle = displayTitle,
                    displaySubtitle = displaySubtitle,
                    versionIndex = versionIndex,
                    serverUrl = serverUrl,
                    accessToken = accessToken,
                    initialAudioTrackIndex = initialAudioTrackIndex,
                    initialSubtitleTrackIndex = initialSubtitleTrackIndex,
                    resumePositionOverride = resumePositionOverride,
                    capabilities = capabilities,
                    preferredQuality = preferredQuality,
                )
                is ApiResult.Error -> _uiState.update {
                    it.copy(isLoading = false, error = "Failed to start transcode: ${r.message}")
                }
                is ApiResult.NetworkError -> _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = "Network error starting transcode: ${r.exception.message}",
                    )
                }
            }
        } else {
            applySessionToState(
                session = session,
                watchDetail = watchDetail,
                displayTitle = displayTitle,
                displaySubtitle = displaySubtitle,
                versionIndex = versionIndex,
                serverUrl = serverUrl,
                accessToken = accessToken,
                initialAudioTrackIndex = initialAudioTrackIndex,
                initialSubtitleTrackIndex = initialSubtitleTrackIndex,
                resumePositionOverride = resumePositionOverride,
                capabilities = capabilities,
                preferredQuality = preferredQuality,
            )
        }
    }

    private fun applySessionToState(
        session: PlaybackSessionResponse,
        watchDetail: com.continuum.app.model.catalog.WatchDetail,
        displayTitle: String,
        displaySubtitle: String,
        versionIndex: Int,
        serverUrl: String,
        accessToken: String,
        initialAudioTrackIndex: Int?,
        initialSubtitleTrackIndex: Int?,
        resumePositionOverride: Double?,
        capabilities: com.continuum.app.model.playback.ClientCodecCapabilities,
        preferredQuality: String?,
    ) {
        val version = watchDetail.versions[versionIndex]
        val startPos = resumePositionOverride ?: watchDetail.userData?.positionSeconds ?: session.position
        val resolvedSubtitleIndex = initialSubtitleTrackIndex
            ?.takeIf { it == -1 || it in (session.subtitleUrls ?: emptyList()).indices }
            ?: -1

        _uiState.update {
            it.copy(
                isLoading = false,
                error = null,
                title = displayTitle,
                subtitle = displaySubtitle,
                sessionId = session.sessionId,
                playMethod = session.playMethod,
                streamUrl = session.streamUrl,
                serverUrl = serverUrl,
                accessToken = accessToken,
                startPosition = startPos,
                position = startPos,
                duration = session.durationSeconds ?: version.duration,
                isPlaying = true,
                isPaused = false,
                subtitleTracks = session.subtitleUrls ?: emptyList(),
                audioTracks = version.audioTracks ?: emptyList(),
                selectedAudioIndex = session.audioTrackIndex,
                selectedSubtitleIndex = resolvedSubtitleIndex,
                intro = watchDetail.intro,
                credits = watchDetail.credits,
                versions = watchDetail.versions,
                selectedVersionIndex = versionIndex,
                seriesId = watchDetail.seriesId,
            )
        }

        // Hand off progress reporting + 404/outage recovery to the lifecycle.
        // It maintains its own session inside `start()` (a duplicate of the one
        // we already started above) — accept this short-term v1 cost; the VM will
        // fully migrate to the lifecycle's session in Phase 3.
        viewModelScope.launch {
            sessionLifecycle.start(
                StartParams(
                    contentId = _uiState.value.contentId,
                    fileId = version.fileId,
                    capabilities = capabilities,
                    audioTrackIndex = initialAudioTrackIndex ?: session.audioTrackIndex,
                    qualityPreference = preferredQuality,
                    startPosition = startPos,
                ),
            )
        }

        // Begin observing intro auto-skip inputs for this session.
        startIntroAutoSkipObserver()

        // Schedule controls auto-hide
        scheduleControlsHide()
    }

    private fun startIntroAutoSkipObserver() {
        introObserverJob?.cancel()
        introObserverJob = introAutoSkipController.observe(
            position = _uiState
                .map { it.position }
                .distinctUntilChanged(),
            introRange = _uiState
                .map { it.intro }
                .distinctUntilChanged(),
            autoSkipEnabled = playerSettingsStore.autoSkipIntroFlow,
            introKey = _uiState
                .map { state ->
                    state.intro?.let { intro ->
                        val fileId = state.versions.getOrNull(state.selectedVersionIndex)?.fileId
                        "${state.sessionId}:${fileId}:${intro.start}:${intro.end}"
                    }
                }
                .distinctUntilChanged(),
            onAutoSkipFire = { seekToSec -> onSeek(seekToSec) },
        )
    }

    /**
     * Preflight signaled the selected track combo can't be direct-played on
     * this device. Fall back to a transcoded stream at the current position.
     * The user-facing notice explains *why* — "Lossless audio not supported"
     * reads differently than "DV Profile 7 not supported", and a single
     * "not supported" banner would hide both.
     */
    fun onUnsupportedPlayback(reason: com.continuum.app.common.player.Playability) {
        val state = _uiState.value
        val sessionId = state.sessionId ?: return
        val versions = state.versions
        val versionIndex = state.selectedVersionIndex
        val version = versions.getOrNull(versionIndex) ?: return

        val notice = when (reason) {
            is com.continuum.app.common.player.Playability.UnsupportedDvProfile ->
                "This device cannot play Dolby Vision Profile ${reason.profile}. Falling back to transcoded stream."
            is com.continuum.app.common.player.Playability.UnsupportedAudioCodec ->
                "Lossless audio not supported on this output. Falling back to transcoded stream."
            is com.continuum.app.common.player.Playability.UnsupportedChannelCount ->
                "Audio channel count not supported. Falling back to transcoded stream."
            com.continuum.app.common.player.Playability.Supported -> return
        }
        Log.i(TAG, "Preflight fallback: $notice")

        viewModelScope.launch {
            val sessionResponse = PlaybackSessionResponse(
                sessionId = sessionId,
                userId = 0,
                profileId = null,
                mediaFileId = version.fileId,
                playMethod = state.playMethod ?: PlayMethod.DIRECT,
                position = state.position,
                isPaused = state.isPaused,
                streamUrl = state.streamUrl.orEmpty(),
                audioTrackIndex = state.selectedAudioIndex,
                durationSeconds = state.duration,
                subtitleUrls = state.subtitleTracks,
                playbackInfo = null,
            )
            when (val r = playbackSessionManager.startTranscodeFallback(
                session = sessionResponse,
                seekSeconds = state.position,
                resolution = version.resolution.orEmpty(),
                mode = com.continuum.app.common.player.PlaybackSessionManager.TranscodeMode.FULL,
            )) {
                is ApiResult.Success -> _uiState.update {
                    it.copy(
                        sessionId = r.data.sessionId,
                        playMethod = r.data.playMethod,
                        streamUrl = r.data.streamUrl,
                        startPosition = r.data.position,
                    )
                }
                is ApiResult.Error -> _uiState.update {
                    it.copy(error = "$notice (start failed: ${r.message})")
                }
                is ApiResult.NetworkError -> _uiState.update {
                    it.copy(error = "$notice (network error: ${r.exception.message})")
                }
            }
        }
    }

    /** Called by the player when the current position changes. */
    fun onPositionChanged(positionMs: Long, durationMs: Long) {
        val positionSec = positionMs / 1000.0
        val durationSec = durationMs / 1000.0

        _uiState.update { state ->
            state.copy(
                position = positionSec,
                duration = if (durationSec > 0) durationSec else state.duration,
                // synthesize from the credits range — server doesn't tell us when to
                // surface the next-episode prompt, so we infer it from the credits start.
                showNextEpisode = state.credits?.let { positionSec >= it.start && state.seriesId != null } ?: false,
            )
        }

        // Forward to the lifecycle so its 10s reporter has a fresh sample.
        // Recovery (404/outage) is fully owned by the lifecycle.
        sessionLifecycle.reportPosition(
            positionSec = positionSec,
            durationSec = if (durationSec > 0) durationSec else _uiState.value.duration,
            isPaused = _uiState.value.isPaused,
        )
    }

    /**
     * Called when the player's actual playing state changes.
     *
     * `isPlaying` reflects the player — it drops during buffering or stalls even when the
     * user intends to play. `isPaused` is the user's intent and must not be overwritten here,
     * otherwise a buffering glitch flips the pause icon and defeats scheduleControlsHide.
     */
    fun onPlayingChanged(isPlaying: Boolean) {
        _uiState.update { it.copy(isPlaying = isPlaying) }
        // Controls should auto-hide once real playback resumes after a pause.
        if (isPlaying && !_uiState.value.isPaused && _uiState.value.showControls) {
            scheduleControlsHide()
        }
    }

    /** Called when buffering state changes. */
    fun onBufferingChanged(isBuffering: Boolean) {
        _uiState.update { it.copy(isBuffering = isBuffering) }
    }

    /** Toggle play/pause — tracks user intent; PlayerScreen mirrors this to playWhenReady. */
    fun onPlayPause() {
        _uiState.update { it.copy(isPaused = !it.isPaused) }
        // Re-arm the auto-hide timer so controls don't linger after resuming playback.
        if (_uiState.value.showControls) {
            scheduleControlsHide()
        }
    }

    /** Seek to a specific position (in seconds). */
    fun onSeek(position: Double) {
        _uiState.update { it.copy(position = position) }
    }

    /** Select a subtitle track (-1 to disable). */
    fun onSelectSubtitle(index: Int) {
        _uiState.update { it.copy(selectedSubtitleIndex = index) }
    }

    /** Select an audio track (may require server-side switch). */
    fun onSelectAudio(index: Int) {
        val currentState = _uiState.value
        val sessionId = currentState.sessionId ?: return

        _uiState.update { it.copy(selectedAudioIndex = index) }

        viewModelScope.launch {
            val result = playbackSessionManager.changeAudio(sessionId, index)
            when (result) {
                is ApiResult.Success -> {
                    val response = result.data
                    // If the server provided a new stream URL, update the state
                    if (response.streamUrl != currentState.streamUrl) {
                        _uiState.update {
                            it.copy(
                                streamUrl = response.streamUrl,
                                playMethod = response.playMethod,
                                selectedAudioIndex = response.audioTrackIndex,
                            )
                        }
                    }
                }
                is ApiResult.Error -> {
                    Log.e(TAG, "Failed to change audio: ${result.message}")
                }
                is ApiResult.NetworkError -> {
                    Log.e(TAG, "Network error changing audio", result.exception)
                }
            }
        }
    }

    /** Skip the intro (legacy alias used by PlayerOverlay). Same effect as [onSkipIntroNow]. */
    fun onSkipIntro() {
        onSkipIntroNow()
    }

    /** Skip the intro now: seek to the end of the intro range and clear any active countdown. */
    fun onSkipIntroNow() {
        val intro = _uiState.value.intro ?: return
        onSeek(intro.end)
        introAutoSkipController.cancelCountdown()
    }

    /** Cancel an in-flight auto-skip countdown — banner falls back to the manual Skip button. */
    fun onCancelIntroAutoSkip() {
        introAutoSkipController.cancelCountdown()
    }

    /** Navigate to the next episode (delegates to navigation callback). */
    fun onNextEpisode() {
        // This is handled by the screen composable via the onNavigateNext callback
    }

    // ---- Settings setters (forward to per-profile DataStore) -------------------
    fun onSetPlaybackSpeed(value: Double) {
        viewModelScope.launch { playerSettingsStore.setPlaybackSpeed(value) }
    }

    fun onSetVideoGravity(value: String) {
        viewModelScope.launch { playerSettingsStore.setVideoGravity(value) }
    }

    fun onSetAutoSkipIntro(value: Boolean) {
        viewModelScope.launch { playerSettingsStore.setAutoSkipIntro(value) }
    }

    fun onSetAutoPlayNext(value: Boolean) {
        viewModelScope.launch { playerSettingsStore.setAutoPlayNext(value) }
    }

    fun onSetHdrEnabled(value: Boolean) {
        viewModelScope.launch { playerSettingsStore.setHdrEnabled(value) }
    }

    fun onSetSubtitleAppearance(value: SubtitleAppearance) {
        viewModelScope.launch { playerSettingsStore.setSubtitleAppearance(value) }
    }

    // ---- Sleep timer setters ---------------------------------------------------
    /**
     * Start (or restart) the sleep timer for [minutes]. Also persists the
     * choice as the new default duration so the picker remembers it next time.
     */
    fun onStartSleepTimer(minutes: Int) {
        sleepTimer.start(minutes)
        if (minutes > 0) {
            viewModelScope.launch { playerSettingsStore.setSleepTimerDefaultMinutes(minutes) }
        }
    }

    /** Cancel an active sleep timer. No-op when idle. */
    fun onCancelSleepTimer() {
        sleepTimer.cancel()
    }

    /**
     * Select a different file version for playback.
     * Stops the current session and starts a new one with the selected version.
     */
    fun onSelectVersion(index: Int) {
        val currentState = _uiState.value
        val versions = currentState.versions
        if (index < 0 || index >= versions.size) return
        if (index == currentState.selectedVersionIndex) return

        val currentPosition = currentState.position

        viewModelScope.launch {
            // Stop the current session
            currentState.sessionId?.let { playbackSessionManager.stopSession(it) }
            // Cancel any in-flight intro skip countdown — we're loading a new version.
            introAutoSkipController.reset()

            _uiState.update { it.copy(isLoading = true, selectedVersionIndex = index) }

            val version = versions[index]
            val profileId = profileRepository.getActiveProfileId() ?: return@launch
            val capabilities = capabilityDetector.detect()

            val result = playbackSessionManager.startSession(
                fileId = version.fileId,
                profileId = profileId,
                capabilities = capabilities,
                startPosition = currentPosition,
            )

            when (result) {
                is ApiResult.Success -> {
                    val session = result.data
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            sessionId = session.sessionId,
                            playMethod = session.playMethod,
                            streamUrl = session.streamUrl,
                            startPosition = currentPosition,
                            position = currentPosition,
                            duration = session.durationSeconds ?: version.duration,
                            audioTracks = version.audioTracks ?: emptyList(),
                            selectedAudioIndex = session.audioTrackIndex,
                            subtitleTracks = session.subtitleUrls ?: emptyList(),
                        )
                    }
                    // Restart lifecycle reporter against the new session.
                    sessionLifecycle.start(
                        StartParams(
                            contentId = currentState.contentId,
                            fileId = version.fileId,
                            capabilities = capabilities,
                            audioTrackIndex = session.audioTrackIndex,
                            qualityPreference = null,
                            startPosition = currentPosition,
                        ),
                    )
                    // Resume the intro auto-skip observer; the introKey now embeds the new
                    // sessionId/fileId so any prior cancellation does not carry over.
                    startIntroAutoSkipObserver()
                }
                is ApiResult.Error -> {
                    _uiState.update {
                        it.copy(isLoading = false, error = "Failed to switch version: ${result.message}")
                    }
                }
                is ApiResult.NetworkError -> {
                    _uiState.update {
                        it.copy(isLoading = false, error = "Network error: ${result.exception.message}")
                    }
                }
            }
        }
    }

    /** Toggle controls visibility. */
    fun onToggleControls() {
        _uiState.update { it.copy(showControls = !it.showControls) }
        if (_uiState.value.showControls) {
            scheduleControlsHide()
        }
    }

    /** Show controls and reset the auto-hide timer. */
    fun onShowControls() {
        _uiState.update { it.copy(showControls = true) }
        scheduleControlsHide()
    }

    /** Called when the user exits the player. */
    fun onExit() {
        viewModelScope.launch {
            // Lifecycle.stop() handles: final progress report, snapshot via PersonalData,
            // session stop, and reporter cancellation. The single call replaces the
            // duplicated reportProgress/stopSession + syncProgressSnapshot flow.
            sessionLifecycle.stop()
            controlsHideJob?.cancel()
            introObserverJob?.cancel()
            introAutoSkipController.reset()
        }
    }

    private fun scheduleControlsHide() {
        controlsHideJob?.cancel()
        controlsHideJob = viewModelScope.launch {
            delay(CONTROLS_AUTO_HIDE_MS)
            val state = _uiState.value
            // Only auto-hide if playing (not paused and not buffering)
            if (state.isPlaying && !state.isPaused && !state.isBuffering) {
                _uiState.update { it.copy(showControls = false) }
            }
        }
    }

    private fun buildSubtitle(watchDetail: com.continuum.app.model.catalog.WatchDetail): String {
        return if (watchDetail.seriesTitle != null && watchDetail.seasonNumber != null && watchDetail.episodeNumber != null) {
            val seasonEp = "S${watchDetail.seasonNumber.toString().padStart(2, '0')}E${watchDetail.episodeNumber.toString().padStart(2, '0')}"
            "${watchDetail.seriesTitle} - $seasonEp"
        } else {
            watchDetail.year?.toString() ?: ""
        }
    }

    private fun findPreferredVersion(
        watchDetail: com.continuum.app.model.catalog.WatchDetail,
        preferredFileId: Int?,
        preferredQuality: String?,
    ): Int {
        if (preferredFileId != null) {
            val index = watchDetail.versions.indexOfFirst { it.fileId == preferredFileId }
            if (index >= 0) return index
        }
        // If the user has a last-used file ID, prefer that version
        val lastFileId = watchDetail.userData?.lastFileId
        if (lastFileId != null) {
            val index = watchDetail.versions.indexOfFirst { it.fileId == lastFileId }
            if (index >= 0) return index
        }
        val qualityIndex = preferredVersionIndex(watchDetail.versions, preferredQuality)
        if (qualityIndex >= 0) return qualityIndex
        return 0
    }

    private fun preferredVersionIndex(versions: List<FileVersion>, preferredQuality: String?): Int {
        val target = preferredQuality?.lowercase().orEmpty()
        if (target.isBlank() || target == "auto") return -1
        val preferredRank = resolutionRank(target)
        return versions.withIndex()
            .sortedByDescending { (_, version) -> resolutionRank(version.resolution) }
            .firstOrNull { (_, version) ->
                target == "original" || resolutionRank(version.resolution) <= preferredRank
            }
            ?.index ?: -1
    }

    private fun resolutionRank(value: String?): Int {
        val normalized = value?.lowercase().orEmpty()
        return when {
            normalized.contains("2160") || normalized.contains("4k") -> 2160
            normalized.contains("1080") -> 1080
            normalized.contains("720") -> 720
            normalized.contains("480") -> 480
            else -> 0
        }
    }

    override fun onCleared() {
        super.onCleared()
        controlsHideJob?.cancel()
        introObserverJob?.cancel()
        lifecycleObserverJob?.cancel()
        introAutoSkipController.reset()
        // Best-effort session stop. Lifecycle.stop() is suspend-based and may not
        // complete after onCleared (viewModelScope is cancelling) — fire & forget,
        // preferring at least one of the two paths to durably persist progress.
        val sessionId = _uiState.value.sessionId
        if (sessionId != null) {
            viewModelScope.launch {
                playbackSessionManager.stopSession(sessionId)
            }
        }
    }
}
