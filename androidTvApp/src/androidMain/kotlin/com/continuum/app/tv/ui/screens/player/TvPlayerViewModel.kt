package com.continuum.app.tv.ui.screens.player

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
import com.continuum.app.model.catalog.FileVersion
import com.continuum.app.model.catalog.TimeRange
import com.continuum.app.model.personal.SyncProgressItem
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
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Renderable audio or subtitle track pulled out of ExoPlayer's current
 * `Tracks` object. `id` is just the ordinal position among groups of the
 * same type — it's used as the index argument when calling
 * [com.continuum.app.common.player.AudioTrackManager.selectAudioTrack] or
 * [com.continuum.app.common.player.SubtitleManager.selectSubtitle].
 */
data class PlayerTrackEntry(
    val index: Int,
    val label: String,
    val language: String?,
    val isSelected: Boolean,
)

/**
 * TV player ViewModel. Phase E adds state for track selection menus, skip
 * buttons, and a 5-second auto-hide timer for the Compose overlay.
 *
 * Phase 3 TV uplift mirrors the phone PlayerViewModel: injects
 * [PlayerSettingsStore], [IntroAutoSkipController], [PlaybackSessionLifecycle],
 * and [SleepTimerController]. The lifecycle is wired alongside the existing
 * recovery flow (dual-call workaround documented in phone VM) — full migration
 * is a later pass. Intro auto-skip and player notices are exposed as separate
 * flows for the screen to consume.
 *
 * Playback itself still goes through [com.continuum.app.common.player.ContinuumPlayerFactory] +
 * [PlaybackSessionManager]. The ViewModel receives track info from the
 * screen (via [onTracksChanged]) because ExoPlayer is owned by the
 * composable.
 */
class TvPlayerViewModel(
    private val catalogRepository: CatalogRepository,
    private val playbackSessionManager: PlaybackSessionManager,
    private val profileRepository: ProfileRepository,
    private val personalDataRepository: PersonalDataRepository,
    private val capabilityDetector: PlaybackCapabilityDetector,
    // Phase 3 TV uplift dependencies.
    private val playerSettingsStore: PlayerSettingsStore,
    private val introAutoSkipController: IntroAutoSkipController,
    private val sessionLifecycle: PlaybackSessionLifecycle,
    private val sleepTimer: SleepTimerController,
    private val contentId: String,
    /**
     * Preferred file version to play (chosen by the user in
     * [com.continuum.app.tv.ui.screens.detail.TvVersionPicker]). When the
     * item has multiple versions (e.g. 4K + 1080p), this pins the session
     * to that version's `fileId`. `null` means "auto" — fall back to the
     * first version the server returns.
     *
     * Without this, the detail screen's version picker was visually
     * effective but functionally dead: the Play action always defaulted
     * to `versions.first()`, which for many titles is the lower-
     * resolution file because of the server's version sort order.
     */
    private val preferredFileId: Int? = null,
) : ViewModel() {

    companion object {
        private const val TAG = "TvPlayerViewModel"
        private const val PROGRESS_REPORT_INTERVAL_MS = 10_000L
    }

    data class UiState(
        val isLoading: Boolean = true,
        val error: String? = null,
        val title: String = "",
        val sessionId: String? = null,
        val playMethod: PlayMethod? = null,
        val streamUrl: String? = null,
        val serverUrl: String = "",
        val accessToken: String = "",
        val selectedFileId: Int? = null,
        val startPosition: Double = 0.0,
        val position: Double = 0.0,
        val duration: Double = 0.0,
        // User intent (only flipped by onPlayPause / explicit actions).
        val isPaused: Boolean = false,
        // Actual player state — transient dips during buffering must not
        // overwrite isPaused, otherwise the icon flickers to Play and the
        // auto-hide timer cancels mid-stall.
        val isPlaying: Boolean = false,
        // Buffering — driven by the player's onIsLoadingChanged listener
        // (set in the screen). Used together with sessionState.Reconnecting
        // to render the centered spinner during outage recovery.
        val isBuffering: Boolean = false,
        // Track selection — populated by the screen from ExoPlayer's
        // `currentTracks` once playback starts.
        val audioTracks: List<PlayerTrackEntry> = emptyList(),
        val subtitleTracks: List<PlayerTrackEntry> = emptyList(),
        val videoTracks: List<PlayerTrackEntry> = emptyList(),
        // Scrubber preview state — `isScrubbing` flips on the first arrow
        // press from the focused scrubber, `scrubPreviewSec` shadows the
        // intended seek target so the overlay can render a preview puck
        // without committing to MediaController.seekTo until the user
        // releases or presses Select.
        val isScrubbing: Boolean = false,
        val scrubPreviewSec: Double = 0.0,
        // Sidecar subtitle URLs from the playback session — passed into
        // [ContinuumPlayerFactory.createMediaSource] so the player loads them
        // as text tracks (the stream manifest doesn't reference these).
        val subtitleUrls: List<PlayerSubtitleInfo> = emptyList(),
        // Overlay visibility (Phase E — driven by the screen but stored here
        // so the overlay can react to play/pause state changes).
        val showControls: Boolean = true,
        val subtitleMenuOpen: Boolean = false,
        val audioMenuOpen: Boolean = false,
        val hudOpen: Boolean = false,
        val preferredAudioLanguage: String? = null,
        val preferredTextLanguage: String? = null,
        // Intro / credits ranges — populated from `WatchDetail`. Used by the
        // intro auto-skip observer and (eventually) the next-up promote.
        val intro: TimeRange? = null,
        val credits: TimeRange? = null,
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    /** Intro auto-skip banner state. The screen consumes this directly. */
    val introSkipState: StateFlow<IntroAutoSkipState> = introAutoSkipController.state

    private val _seekRequests = MutableSharedFlow<Double>(extraBufferCapacity = 1)
    val seekRequests: SharedFlow<Double> = _seekRequests

    /**
     * Transient player notice (server reconnecting, suspend warnings, etc.) emitted by
     * [PlaybackSessionLifecycle]. `null` means show nothing.
     */
    val notice: StateFlow<PlayerNotice?> = sessionLifecycle.notice

    /**
     * Lifecycle session state. The screen uses this to drive the buffering
     * spinner during outage Reconnecting (which the underlying ExoPlayer can't
     * observe).
     */
    val sessionState: StateFlow<SessionState> = sessionLifecycle.state

    // ---- Player settings flows (per-profile, DataStore-backed) -----------------
    val playbackSpeed: StateFlow<Double> = playerSettingsStore.playbackSpeedFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, 1.0)
    val autoSkipIntroEnabled: StateFlow<Boolean> = playerSettingsStore.autoSkipIntroFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)
    val autoPlayNextEnabled: StateFlow<Boolean> = playerSettingsStore.autoPlayNextFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)
    val hdrEnabled: StateFlow<Boolean> = playerSettingsStore.hdrEnabledFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)
    val subtitleAppearance: StateFlow<SubtitleAppearance> = playerSettingsStore.subtitleAppearanceFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, SubtitleAppearance.DEFAULT)

    // ---- Sleep timer ------------------------------------------------------------
    val sleepTimerState: StateFlow<SleepTimerState> = sleepTimer.state
    val sleepTimerDefaultMinutes: StateFlow<Int> = playerSettingsStore.sleepTimerDefaultMinutesFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, 30)

    private var progressJob: Job? = null
    private var recoveryJob: Job? = null
    private var introObserveJob: Job? = null
    private var lifecycleObserveJob: Job? = null
    private var recoveringSessionId: String? = null

    init {
        // Mirror lifecycle Failed state into the UI error field so the user
        // sees a notice if outage recovery times out or the lifecycle's
        // session fails to start. The phone VM does the same.
        lifecycleObserveJob = viewModelScope.launch {
            sessionLifecycle.state.collect { state ->
                if (state is SessionState.Failed) {
                    _uiState.update { current ->
                        if (current.error == null) current.copy(error = state.message) else current
                    }
                }
            }
        }

        // When the sleep timer fires, flip user intent to paused. The screen
        // mirrors `isPaused` to `mediaController.playWhenReady`.
        sleepTimer.configure {
            _uiState.update { it.copy(isPaused = true) }
        }

        if (contentId.isNotBlank()) loadContent()
    }

    private fun loadContent(
        startPositionOverride: Double? = null,
        preferredFileIdOverride: Int? = null,
    ) {
        // A fresh load resets any in-flight intro countdown / cancellation
        // memory so a new content session starts clean.
        introAutoSkipController.reset()

        _uiState.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            try {
                val watchDetail = when (val r = catalogRepository.getWatchDetail(contentId)) {
                    is ApiResult.Success -> r.data
                    is ApiResult.Error -> return@launch fail("Failed to load content: ${r.message}")
                    is ApiResult.NetworkError -> return@launch fail(
                        "Network error: ${r.exception.message}",
                    )
                }
                if (watchDetail.versions.isEmpty()) {
                    return@launch fail("No playable versions available")
                }

                // Honor the user's explicit version choice from the detail
                // screen (e.g. 4K over 1080p). If the requested fileId is no
                // longer present (unlikely, but can happen if the library
                // was rescanned between detail open and Play), silently fall
                // back to the server's default rather than error — picking a
                // version is a best-effort UX nicety.
                val requestedFileId = preferredFileIdOverride ?: preferredFileId
                // Read once from the player-settings store: device + user
                // overrides resolve through the same cascade the server
                // returns from `effective`.
                val serverUrl = playbackSessionManager.getServerUrl()
                val preferredQuality = playerSettingsStore.preferredQualityFlow.first()
                val version = requestedFileId
                    ?.let { id -> watchDetail.versions.firstOrNull { it.fileId == id } }
                    ?: pickPreferredVersion(
                        watchDetail.versions,
                        watchDetail.userData?.lastFileId,
                        preferredQuality,
                    )
                val activeProfile = profileRepository.getActiveProfile()
                val profileId = activeProfile?.id ?: profileRepository.getActiveProfileId()
                    ?: return@launch fail("No active profile selected")
                val preferredAudioLanguage = playerSettingsStore.audioLanguageFlow
                    .first().ifBlank { null }
                val accessToken = playbackSessionManager.getAccessToken()
                    ?: return@launch fail("Not authenticated")
                _uiState.update {
                    it.copy(
                        preferredAudioLanguage = preferredAudioLanguage ?: activeProfile?.language,
                        preferredTextLanguage = activeProfile?.subtitleLanguage,
                    )
                }

                val capabilities = capabilityDetector.detect()

                val session = when (
                    val r = playbackSessionManager.startSession(
                        fileId = version.fileId,
                        profileId = profileId,
                        capabilities = capabilities,
                        qualityPreference = preferredQuality,
                        startPosition = startPositionOverride,
                    )
                ) {
                    is ApiResult.Success -> r.data
                    is ApiResult.Error -> return@launch fail("Failed to start playback: ${r.message}")
                    is ApiResult.NetworkError -> return@launch fail(
                        "Network error: ${r.exception.message}",
                    )
                }

                // DIRECT play: the session's stream URL is ready. REMUX and TRANSCODE
                // both need a transcode-start call to produce an HLS manifest —
                // delegated to startTranscodeFallback so phone + TV share one
                // code path.
                val resolved: PlaybackSessionResponse = when (session.playMethod) {
                    PlayMethod.DIRECT -> session
                    PlayMethod.REMUX, PlayMethod.TRANSCODE -> {
                        val mode = if (session.playMethod == PlayMethod.REMUX) {
                            PlaybackSessionManager.TranscodeMode.REMUX
                        } else {
                            PlaybackSessionManager.TranscodeMode.FULL
                        }
                        when (val r = playbackSessionManager.startTranscodeFallback(
                            session = session,
                            seekSeconds = startPositionOverride ?: watchDetail.userData?.positionSeconds ?: 0.0,
                            resolution = version.resolution.orEmpty(),
                            mode = mode,
                        )) {
                            is ApiResult.Success -> r.data
                            is ApiResult.Error -> return@launch fail(
                                "Failed to start transcode: ${r.message}",
                            )
                            is ApiResult.NetworkError -> return@launch fail(
                                "Network error starting transcode: ${r.exception.message}",
                            )
                        }
                    }
                }

                val startPos = startPositionOverride ?: watchDetail.userData?.positionSeconds ?: resolved.position
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = null,
                        title = watchDetail.title,
                        sessionId = resolved.sessionId,
                        playMethod = resolved.playMethod,
                        streamUrl = resolved.streamUrl,
                        serverUrl = serverUrl,
                        accessToken = accessToken,
                        selectedFileId = version.fileId,
                        startPosition = startPos,
                        position = startPos,
                        duration = resolved.durationSeconds ?: version.duration,
                        isPaused = false,
                        subtitleUrls = resolved.subtitleUrls ?: emptyList(),
                        intro = watchDetail.intro,
                        credits = watchDetail.credits,
                    )
                }
                recoveringSessionId = null
                startProgressReporting(resolved.sessionId)

                // Hand off progress reporting + 404/outage recovery to the
                // lifecycle in parallel. The lifecycle starts its own session
                // (a dup of the one we just created) — accept this short-term
                // v1 cost; full migration off the legacy progressJob is a
                // later pass. This dual-call mirrors the phone VM.
                sessionLifecycle.start(
                    StartParams(
                        contentId = contentId,
                        fileId = version.fileId,
                        capabilities = capabilities,
                        audioTrackIndex = resolved.audioTrackIndex,
                        qualityPreference = preferredQuality,
                        startPosition = startPos,
                    ),
                )

                // Begin observing intro auto-skip inputs for this session.
                startIntroAutoSkipObserver()
            } catch (e: Exception) {
                Log.e(TAG, "Error loading content", e)
                fail("Unexpected error: ${e.message}")
            }
        }
    }

    private fun startIntroAutoSkipObserver() {
        introObserveJob?.cancel()
        introObserveJob = introAutoSkipController.observe(
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
                        "${state.sessionId}:${state.selectedFileId}:${intro.start}:${intro.end}"
                    }
                }
                .distinctUntilChanged(),
            onAutoSkipFire = { seekToSec ->
                _uiState.update { it.copy(position = seekToSec) }
                _seekRequests.emit(seekToSec)
            },
        )
    }

    private fun fail(message: String) {
        _uiState.update { it.copy(isLoading = false, error = message) }
    }

    /**
     * Preflight signaled the selected track combo can't be direct-played.
     * Fall back to a transcoded stream at the current position and show the
     * user the reason.
     */
    fun onUnsupportedPlayback(reason: com.continuum.app.common.player.Playability) {
        val state = _uiState.value
        val sessionId = state.sessionId ?: return

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
                mediaFileId = 0,
                playMethod = state.playMethod ?: PlayMethod.DIRECT,
                position = state.position,
                isPaused = state.isPaused,
                streamUrl = state.streamUrl.orEmpty(),
                audioTrackIndex = 0,
                durationSeconds = state.duration,
                subtitleUrls = state.subtitleUrls,
                playbackInfo = null,
            )
            when (val r = playbackSessionManager.startTranscodeFallback(
                session = sessionResponse,
                seekSeconds = state.position,
                resolution = "",
                mode = PlaybackSessionManager.TranscodeMode.FULL,
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

    fun onPositionChanged(positionMs: Long, durationMs: Long) {
        val positionSec = positionMs / 1000.0
        val durationSec = durationMs / 1000.0
        _uiState.update {
            it.copy(
                position = positionSec,
                duration = if (durationSec > 0) durationSec else it.duration,
            )
        }
        // Forward to the lifecycle so its 10s reporter has a fresh sample.
        // The legacy progressJob stays in place for now — see the dual-call
        // note in loadContent.
        sessionLifecycle.reportPosition(
            positionSec = positionSec,
            durationSec = if (durationSec > 0) durationSec else _uiState.value.duration,
            isPaused = _uiState.value.isPaused,
        )
    }

    fun onPlayingChanged(isPlaying: Boolean) {
        _uiState.update { it.copy(isPlaying = isPlaying) }
    }

    fun onBufferingChanged(isBuffering: Boolean) {
        _uiState.update { it.copy(isBuffering = isBuffering) }
    }

    /** Toggle user-intent pause state. Screen mirrors this to player.play/pause. */
    fun onPlayPause() {
        _uiState.update { it.copy(isPaused = !it.isPaused) }
    }

    /**
     * Push the fresh list of audio / subtitle tracks up from the screen. Called
     * from a `Player.Listener.onTracksChanged` callback — we keep the list in
     * ViewModel state so the menu composables can read it directly.
     */
    fun onTracksChanged(audio: List<PlayerTrackEntry>, subtitle: List<PlayerTrackEntry>) {
        _uiState.update { it.copy(audioTracks = audio, subtitleTracks = subtitle) }
    }

    fun onTracksChanged(
        audio: List<PlayerTrackEntry>,
        subtitle: List<PlayerTrackEntry>,
        video: List<PlayerTrackEntry>,
    ) {
        _uiState.update { it.copy(audioTracks = audio, subtitleTracks = subtitle, videoTracks = video) }
    }

    fun beginScrub() {
        _uiState.update { it.copy(isScrubbing = true, scrubPreviewSec = it.position, showControls = true) }
    }

    fun updateScrubPreview(sec: Double) {
        _uiState.update {
            val clamped = sec.coerceIn(0.0, it.duration.coerceAtLeast(0.0))
            it.copy(scrubPreviewSec = clamped)
        }
    }

    fun commitScrub(): Double {
        val target = _uiState.value.scrubPreviewSec
        _uiState.update { it.copy(isScrubbing = false) }
        return target
    }

    fun cancelScrub() {
        _uiState.update { it.copy(isScrubbing = false, scrubPreviewSec = 0.0) }
    }

    fun setControlsVisible(visible: Boolean) {
        _uiState.update { it.copy(showControls = visible) }
    }

    fun openSubtitleMenu() {
        _uiState.update { it.copy(subtitleMenuOpen = true) }
    }

    fun closeSubtitleMenu() {
        _uiState.update { it.copy(subtitleMenuOpen = false) }
    }

    fun openAudioMenu() {
        _uiState.update { it.copy(audioMenuOpen = true) }
    }

    fun closeAudioMenu() {
        _uiState.update { it.copy(audioMenuOpen = false) }
    }

    fun openHUD() {
        _uiState.update { it.copy(hudOpen = true, showControls = true) }
    }

    fun closeHUD() {
        _uiState.update { it.copy(hudOpen = false) }
    }

    /**
     * Skip the intro now: returns the seek target in seconds so the screen
     * can call MediaController.seekTo. Returns null if there is no active
     * intro range.
     *
     * Returning the value (instead of seeking internally) keeps the VM free
     * of MediaController references — the screen owns the controller.
     */
    fun onSkipIntroNow(): Double? {
        val intro = _uiState.value.intro ?: return null
        introAutoSkipController.cancelCountdown()
        return intro.end
    }

    /** Cancel an in-flight auto-skip countdown — banner falls back to manual Skip. */
    fun onCancelIntroAutoSkip() {
        introAutoSkipController.cancelCountdown()
    }

    // ---- Settings setters (forward to per-profile DataStore) -------------------
    fun onSetPlaybackSpeed(value: Double) {
        viewModelScope.launch { playerSettingsStore.setPlaybackSpeed(value) }
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
    fun onStartSleepTimer(minutes: Int) {
        sleepTimer.start(minutes)
        if (minutes > 0) {
            viewModelScope.launch { playerSettingsStore.setSleepTimerDefaultMinutes(minutes) }
        }
    }

    fun onCancelSleepTimer() {
        sleepTimer.cancel()
    }

    suspend fun stopSessionForExit() {
        val state = _uiState.value
        state.sessionId?.let { id ->
            val progressResult = playbackSessionManager.reportProgress(
                sessionId = id,
                position = state.position,
                isPaused = true,
            )
            val stopResult = playbackSessionManager.stopSession(id)
            if (isPlaybackSessionMissing(progressResult) || isPlaybackSessionMissing(stopResult)) {
                syncProgressSnapshot(state)
            }
        }
        // Also stop the lifecycle so its reporter shuts down cleanly and a
        // final snapshot lands via the shared progress flush.
        sessionLifecycle.stop()
        progressJob?.cancel()
        recoveryJob?.cancel()
        introObserveJob?.cancel()
        introAutoSkipController.reset()
        _uiState.update { it.copy(sessionId = null) }
    }

    fun onExit() {
        viewModelScope.launch { stopSessionForExit() }
    }

    override fun onCleared() {
        super.onCleared()
        progressJob?.cancel()
        recoveryJob?.cancel()
        introObserveJob?.cancel()
        lifecycleObserveJob?.cancel()
        introAutoSkipController.reset()
        val sessionId = _uiState.value.sessionId
        if (sessionId != null) {
            // Fire-and-forget stop; VM is already being cleared so we can't await.
            viewModelScope.launch { playbackSessionManager.stopSession(sessionId) }
        }
    }

    private fun startProgressReporting(sessionId: String) {
        progressJob?.cancel()
        progressJob = viewModelScope.launch {
            while (isActive) {
                delay(PROGRESS_REPORT_INTERVAL_MS)
                val state = _uiState.value
                if (state.sessionId == sessionId) {
                    val result = playbackSessionManager.reportProgress(
                        sessionId = sessionId,
                        position = state.position,
                        isPaused = state.isPaused,
                    )
                    if (isPlaybackSessionMissing(result)) {
                        recoverMissingPlaybackSession(sessionId)
                    }
                }
            }
        }
    }

    private fun recoverMissingPlaybackSession(staleSessionId: String) {
        if (recoveringSessionId == staleSessionId) return

        val snapshot = _uiState.value
        Log.w(
            TAG,
            "Playback session $staleSessionId missing; renewing at ${snapshot.position}s",
        )
        recoveringSessionId = staleSessionId
        recoveryJob?.cancel()
        recoveryJob = viewModelScope.launch {
            syncProgressSnapshot(snapshot)
            progressJob?.cancel()
            loadContent(
                startPositionOverride = snapshot.position,
                preferredFileIdOverride = snapshot.selectedFileId,
            )
        }
    }

    private suspend fun syncProgressSnapshot(state: UiState) {
        if (contentId.isBlank() || !state.position.isFinite() || state.position < 0) {
            return
        }

        val result = personalDataRepository.syncProgress(
            listOf(
                SyncProgressItem(
                    mediaItemId = contentId,
                    position = state.position,
                    duration = if (state.duration.isFinite() && state.duration > 0) state.duration else 0.0,
                    forceOverwrite = true,
                ),
            ),
        )
        when (result) {
            is ApiResult.Success -> Unit
            is ApiResult.Error -> Log.w(TAG, "syncProgress failed: ${result.code} ${result.message}")
            is ApiResult.NetworkError -> Log.w(TAG, "syncProgress network error", result.exception)
        }
    }

    private fun isPlaybackSessionMissing(result: ApiResult<*>): Boolean {
        val error = result as? ApiResult.Error ?: return false
        return error.code == 404 &&
            (error.error == "playback_session_not_found" || error.message == "Playback session not found")
    }

    private fun pickPreferredVersion(
        versions: List<FileVersion>,
        lastFileId: Int?,
        preferredQuality: String?,
    ): FileVersion {
        if (lastFileId != null) {
            versions.firstOrNull { it.fileId == lastFileId }?.let { return it }
        }
        val target = preferredQuality?.lowercase().orEmpty()
        if (target.isBlank() || target == "auto") {
            return versions.first()
        }
        val preferredRank = resolutionRank(target)
        return versions
            .sortedByDescending { resolutionRank(it.resolution) }
            .firstOrNull { version ->
                target == "original" || resolutionRank(version.resolution) <= preferredRank
            }
            ?: versions.first()
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

}
