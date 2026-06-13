package com.continuum.app.tv.ui.screens.player

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.continuum.app.common.player.PlaybackAnalyticsListener
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
import com.continuum.app.model.catalog.VersionChapter
import com.continuum.app.model.personal.SyncProgressItem
import com.continuum.app.model.settings.SubtitleAppearance
import com.continuum.app.model.playback.PlayMethod
import com.continuum.app.model.playback.PlaybackSessionResponse
import com.continuum.app.model.playback.PlayerSubtitleInfo
import com.continuum.app.model.playback.mergeDownloadedSubtitles
import com.continuum.app.model.subtitles.SubtitleAiQuota
import com.continuum.app.model.subtitles.SubtitleAiStatus
import com.continuum.app.model.subtitles.SubtitleDownloadRequest
import com.continuum.app.model.subtitles.SubtitleResult
import com.continuum.app.model.subtitles.SubtitleSearchRequest
import com.continuum.app.model.subtitles.SubtitleTranslateRequest
import com.continuum.app.network.ApiResult
import com.continuum.app.network.errorMessage
import com.continuum.app.repository.CatalogRepository
import com.continuum.app.repository.PersonalDataRepository
import com.continuum.app.repository.ProfileRepository
import com.continuum.app.repository.SubtitlesRepository
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
 * How the video surface scales to fill the player area. Session-scoped
 * (resets to [Fit] on each new playback) — matches tvOS behavior.
 */
enum class VideoFillMode {
    /** Letterbox: preserve aspect ratio, may show bars. Default. */
    Fit,
    /** Zoom: preserve aspect ratio, fill screen, may crop edges. */
    Zoom,
}

/**
 * Snapshot of player statistics surfaced in the HUD's Stats pane.
 * Built by [reducePlayerStats] from a stream of [PlaybackAnalyticsListener.Event]s.
 *
 * All fields nullable — fields populate as events arrive; rendering should
 * tolerate any subset being null. `droppedFrames` and `audioUnderruns` are
 * cumulative counters since the snapshot was created.
 */
data class PlayerStatsSnapshot(
    val videoDecoderName: String? = null,
    val audioDecoderName: String? = null,
    val videoCodec: String? = null,
    val audioCodec: String? = null,
    val resolution: String? = null,            // e.g. "1920x1080"
    val frameRate: Float? = null,
    val hdrMode: String? = null,               // e.g. "Dolby Vision", "HDR10", "SDR"
    val bitrateBps: Long? = null,
    val droppedFrames: Int = 0,                // cumulative since session start
    val audioUnderruns: Int = 0,               // cumulative
)

/**
 * Pure event-to-snapshot reducer. Used by the ViewModel; tested in isolation.
 * Does NOT clear state on unrelated events (e.g. a DroppedFrames event leaves
 * format/decoder fields untouched).
 */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
internal fun reducePlayerStats(
    current: PlayerStatsSnapshot,
    event: PlaybackAnalyticsListener.Event,
): PlayerStatsSnapshot = when (event) {
    is PlaybackAnalyticsListener.Event.VideoDecoderInitialized ->
        current.copy(videoDecoderName = event.decoderName)
    is PlaybackAnalyticsListener.Event.AudioDecoderInitialized ->
        current.copy(audioDecoderName = event.decoderName)
    is PlaybackAnalyticsListener.Event.VideoFormatChanged -> current.copy(
        videoCodec = event.format.codecs ?: event.format.sampleMimeType,
        resolution = if (event.format.width > 0 && event.format.height > 0) {
            "${event.format.width}x${event.format.height}"
        } else current.resolution,
        frameRate = if (event.format.frameRate > 0f) event.format.frameRate else current.frameRate,
        hdrMode = describeHdrMode(event.format) ?: current.hdrMode,
    )
    is PlaybackAnalyticsListener.Event.AudioFormatChanged ->
        current.copy(audioCodec = event.format.codecs ?: event.format.sampleMimeType)
    is PlaybackAnalyticsListener.Event.DroppedFrames ->
        current.copy(droppedFrames = current.droppedFrames + event.count)
    is PlaybackAnalyticsListener.Event.AudioUnderrun ->
        current.copy(audioUnderruns = current.audioUnderruns + 1)
    is PlaybackAnalyticsListener.Event.BandwidthEstimate ->
        current.copy(bitrateBps = event.bitrateBps)
    is PlaybackAnalyticsListener.Event.LoadError ->
        current // load errors don't mutate the stats snapshot
}

/**
 * Describe the HDR mode of a video [androidx.media3.common.Format].
 *
 * Dolby Vision detection is by codec string (`dvh1`, `dvhe`) and runs BEFORE
 * the `colorTransfer` switch because DV bitstreams can carry varying color
 * transfers and Apple's reference treats DV as its own mode. Returns `null`
 * if no HDR signal is present (caller keeps the prior value).
 */
private fun describeHdrMode(format: androidx.media3.common.Format): String? {
    val codecs = format.codecs.orEmpty()
    if (codecs.contains("dvh", ignoreCase = true) || codecs.contains("dvhe", ignoreCase = true)) {
        return "Dolby Vision"
    }
    val colorInfo = format.colorInfo ?: return null
    return when (colorInfo.colorTransfer) {
        androidx.media3.common.C.COLOR_TRANSFER_ST2084 -> "HDR10"
        androidx.media3.common.C.COLOR_TRANSFER_HLG -> "HLG"
        androidx.media3.common.C.COLOR_TRANSFER_SDR -> "SDR"
        else -> null
    }
}

/**
 * Subtitle provider search/download state backing the TV subtitle search
 * dialog. `completedNonce` increments when a download lands and the track
 * list has been refreshed — the dialog observes it and dismisses itself.
 */
data class SubtitleSearchUiState(
    val language: String = "en",
    val isSearching: Boolean = false,
    val hasSearched: Boolean = false,
    val results: List<SubtitleResult> = emptyList(),
    val error: String? = null,
    /** [SubtitleResult.id] currently downloading (inline row spinner), or null. */
    val downloadingResultId: String? = null,
    val completedNonce: Int = 0,
)

/** Lifecycle of the in-dialog AI job for the TV AI translate dialog. */
sealed interface AiJobPhase {
    data object Idle : AiJobPhase
    data object Submitting : AiJobPhase
    data class Running(val progress: Double, val message: String?) : AiJobPhase
    data class Failed(val message: String) : AiJobPhase
}

/**
 * AI translate/transcribe state. `status` defaults to both-flags-false so the
 * HUD row stays hidden until the lazy probe succeeds (matching the web: a
 * failed probe also leaves both flags false and surfaces no error).
 */
data class AiTranslateUiState(
    val statusLoaded: Boolean = false,
    val status: SubtitleAiStatus = SubtitleAiStatus(enabled = false, transcribeEnabled = false),
    val quota: SubtitleAiQuota? = null,
    val phase: AiJobPhase = AiJobPhase.Idle,
    val completedNonce: Int = 0,
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
    private val playbackAnalytics: PlaybackAnalyticsListener,
    // Phase 3 TV uplift dependencies.
    private val playerSettingsStore: PlayerSettingsStore,
    private val introAutoSkipController: IntroAutoSkipController,
    private val sessionLifecycle: PlaybackSessionLifecycle,
    private val sleepTimer: SleepTimerController,
    // Subtitle suite (provider search/download + AI translate).
    private val subtitlesRepository: SubtitlesRepository,
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
    private val resumePositionOverride: Double? = null,
) : ViewModel() {

    companion object {
        private const val TAG = "TvPlayerViewModel"
        private const val PROGRESS_REPORT_INTERVAL_MS = 10_000L
    }

    data class UiState(
        val isLoading: Boolean = true,
        val error: String? = null,
        val title: String = "",
        /**
         * Artwork URL for Now Playing lock-screen / Bluetooth / Wear surfaces.
         * Sourced from `WatchDetail.posterUrl` with `backdropUrl` fallback.
         * Threaded into MediaItem.MediaMetadata via [TvPlayerScreen]'s call
         * to `playerFactory.buildMediaItem`. Mirrors phone player parity.
         */
        val artworkUrl: String? = null,
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
        // Server media file id for the active version — required by the
        // subtitle search/download and AI translate endpoints. Sourced from
        // PlaybackSessionResponse.mediaFileId in loadContent; null until the
        // session starts (the HUD hides the Search row while null).
        val mediaFileId: Int? = null,
        // Bumped by refreshSubtitles after merging downloaded subtitles into
        // subtitleUrls. The screen rebuilds the MediaItem (same stream URL,
        // enlarged sidecar list) on each bump — keyed on the nonce, NOT on
        // subtitleUrls, so the initial prepare effect stays the only path
        // for session start / stream-URL changes.
        val subtitleRefreshNonce: Int = 0,
        // Dialog visibility — owned here so HUD rows can request them and
        // the screen renders the Popups above the open HUD.
        val showSubtitleSearchDialog: Boolean = false,
        val showAiTranslateDialog: Boolean = false,
        // Overlay visibility (Phase E — driven by the screen but stored here
        // so the overlay can react to play/pause state changes).
        val showControls: Boolean = true,
        val hudOpen: Boolean = false,
        val preferredAudioLanguage: String? = null,
        val preferredTextLanguage: String? = null,
        // Intro / credits ranges — populated from `WatchDetail`. Used by the
        // intro auto-skip observer and (eventually) the next-up promote.
        val intro: TimeRange? = null,
        val credits: TimeRange? = null,
        // Chapters from the selected FileVersion (server-extracted via FFprobe
        // at ingest, mirrors Apple's `VersionChapter` consumption). Empty list
        // when the file has no embedded chapters. The HUD Chapters pane
        // renders this directly; the scrubber maps the same list to its
        // lightweight ChapterInfo for tick rendering.
        val chapters: List<VersionChapter> = emptyList(),
        // Live player statistics — reduced from [PlaybackAnalyticsListener.Event]s
        // by [reducePlayerStats]. Always non-null so the HUD Stats pane has a
        // snapshot to read; populates field-by-field as events arrive.
        val stats: PlayerStatsSnapshot = PlayerStatsSnapshot(),
        // Video surface fill mode (letterbox vs zoom). Session-scoped — resets
        // to Fit on each new playback to match tvOS video-gravity behavior.
        val videoFillMode: VideoFillMode = VideoFillMode.Fit,
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

    // ---- Subtitle suite flows ----------------------------------------------------
    private val _subtitleSearch = MutableStateFlow(SubtitleSearchUiState())
    val subtitleSearch: StateFlow<SubtitleSearchUiState> = _subtitleSearch.asStateFlow()

    private val _aiTranslate = MutableStateFlow(AiTranslateUiState())
    val aiTranslate: StateFlow<AiTranslateUiState> = _aiTranslate.asStateFlow()

    /**
     * Ordinal text-group index to select after a subtitle refresh lands.
     * Mirrors the seekRequests idiom: the screen collects and calls
     * SubtitleManager.selectSubtitle — the VM never touches the controller.
     */
    private val _subtitleSelectRequests = MutableSharedFlow<Int>(extraBufferCapacity = 1)
    val subtitleSelectRequests: SharedFlow<Int> = _subtitleSelectRequests

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
    /**
     * Per-profile audio delay in ms, ±500 clamp. Sourced from
     * [PlayerSettingsStore.audioSyncMsFlow]; mirrored into the active
     * [com.continuum.app.common.player.audio.DelayAudioProcessor] by
     * [com.continuum.app.common.player.ContinuumPlaybackService] (E T3).
     * The HUD Audio pane reads this for its delay stepper.
     */
    val audioDelayMs: StateFlow<Int> = playerSettingsStore.audioSyncMsFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0)
    /**
     * Per-profile subtitle delay in ms, ±500 clamp. Sourced from
     * [PlayerSettingsStore.subtitleSyncMsFlow]; mirrored into the active
     * [com.continuum.app.common.player.subtitle.SubtitleOffsetHolder] by
     * [com.continuum.app.common.player.ContinuumPlaybackService] (A.3f T2).
     * The HUD Subtitles pane reads this for its delay stepper.
     */
    val subtitleDelayMs: StateFlow<Int> = playerSettingsStore.subtitleSyncMsFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    // ---- Sleep timer ------------------------------------------------------------
    val sleepTimerState: StateFlow<SleepTimerState> = sleepTimer.state
    val sleepTimerDefaultMinutes: StateFlow<Int> = playerSettingsStore.sleepTimerDefaultMinutesFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, 30)

    private var progressJob: Job? = null
    private var recoveryJob: Job? = null
    private var introObserveJob: Job? = null
    private var lifecycleObserveJob: Job? = null
    private var recoveringSessionId: String? = null

    // Subtitle suite bookkeeping.
    private var aiStatusRequested = false
    private var aiJobPollJob: Job? = null
    private var activeAiJobId: Long? = null
    private var pendingSubtitleSelectLabel: String? = null

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

        // Reduce the analytics listener's event stream into the HUD's Stats
        // snapshot. The listener is a process-wide singleton shared with
        // ContinuumPlaybackService; we just subscribe — no extra registration.
        viewModelScope.launch {
            playbackAnalytics.events.collect { event ->
                _uiState.update { it.copy(stats = reducePlayerStats(it.stats, event)) }
            }
        }

        if (contentId.isNotBlank()) loadContent(startPositionOverride = resumePositionOverride)
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
                        artworkUrl = watchDetail.posterUrl?.takeIf { it.isNotBlank() }
                            ?: watchDetail.backdropUrl?.takeIf { it.isNotBlank() },
                        sessionId = resolved.sessionId,
                        playMethod = resolved.playMethod,
                        streamUrl = resolved.streamUrl,
                        serverUrl = serverUrl,
                        accessToken = accessToken,
                        selectedFileId = version.fileId,
                        // onUnsupportedPlayback's synthetic PlaybackSessionResponse
                        // (mediaFileId = 0) never flows back into UiState, so the
                        // id captured here survives transcode fallback.
                        mediaFileId = resolved.mediaFileId.takeIf { id -> id > 0 }
                            ?: session.mediaFileId.takeIf { id -> id > 0 },
                        startPosition = startPos,
                        position = startPos,
                        duration = resolved.durationSeconds ?: version.duration,
                        isPaused = false,
                        subtitleUrls = resolved.subtitleUrls ?: emptyList(),
                        intro = watchDetail.intro,
                        credits = watchDetail.credits,
                        chapters = version.chapters.orEmpty(),
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
     * Idempotent pause setter for Watch Together sync-applied commands. Unlike
     * [onPlayPause] (a toggle), this sets the absolute desired state, so a
     * duplicate room command can't flip the player the wrong way. The screen's
     * `state.isPaused` mirror drives `mediaController.playWhenReady`.
     */
    fun setPaused(paused: Boolean) {
        _uiState.update { if (it.isPaused == paused) it else it.copy(isPaused = paused) }
    }

    /**
     * Deadband-free seek for Watch Together corrective seeks
     * ([TvRoomSyncController.applyDecision]). Updates `uiState.position` AND
     * emits on [seekRequests], which the screen collects and applies to the
     * MediaController unconditionally (TV has no position-mirror deadband, so
     * `seekRequests` already reaches the player on every emission — sub-second
     * sync corrections are never swallowed). Named to mirror the mobile
     * `PlayerViewModel.seekImmediate` contract.
     */
    fun seekImmediate(positionSec: Double) {
        _uiState.update { it.copy(position = positionSec) }
        _seekRequests.tryEmit(positionSec)
    }

    /**
     * Push the fresh list of audio / subtitle tracks up from the screen. Called
     * from a `Player.Listener.onTracksChanged` callback — we keep the list in
     * ViewModel state so the menu composables can read it directly.
     */
    fun onTracksChanged(audio: List<PlayerTrackEntry>, subtitle: List<PlayerTrackEntry>) {
        _uiState.update { it.copy(audioTracks = audio, subtitleTracks = subtitle) }
        resolvePendingSubtitleSelection(subtitle)
    }

    fun onTracksChanged(
        audio: List<PlayerTrackEntry>,
        subtitle: List<PlayerTrackEntry>,
        video: List<PlayerTrackEntry>,
    ) {
        _uiState.update { it.copy(audioTracks = audio, subtitleTracks = subtitle, videoTracks = video) }
        resolvePendingSubtitleSelection(subtitle)
    }

    /**
     * After refreshSubtitles bumps the nonce, the screen re-prepares the item
     * and a fresh onTracksChanged arrives. Sidecar tracks expose their
     * SubtitleConfiguration label as Format.label, which extractTrackEntries
     * copies into PlayerTrackEntry.label — so matching by label is exact.
     * Emits the ordinal text-group index for SubtitleManager.selectSubtitle.
     */
    private fun resolvePendingSubtitleSelection(subtitle: List<PlayerTrackEntry>) {
        val label = pendingSubtitleSelectLabel ?: return
        val match = subtitle.firstOrNull { it.label == label } ?: return
        pendingSubtitleSelectLabel = null
        _subtitleSelectRequests.tryEmit(match.index)
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

    fun openHUD() {
        _uiState.update { it.copy(hudOpen = true, showControls = true) }
    }

    fun closeHUD() {
        _uiState.update { it.copy(hudOpen = false) }
    }

    fun onVideoFillModeChanged(mode: VideoFillMode) {
        _uiState.update { it.copy(videoFillMode = mode) }
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

    /**
     * HUD Chapters pane picked a row. Returns the seek target in seconds;
     * the screen owns the MediaController and performs the actual seek.
     * Returns null when the supplied index is out of range (shouldn't
     * happen — the row list is built from the same `chapters` field — but
     * guarded for safety).
     */
    fun onSeekToChapter(chapterIndex: Int): Double? =
        _uiState.value.chapters.getOrNull(chapterIndex)?.startSeconds

    // ---- Subtitle suite: AI status probe + dialog visibility --------------------

    /**
     * Lazy once-per-player-session AI status probe, fired by the HUD the
     * first time the Subtitles pane is shown. On any failure both flags stay
     * false → the "Translate with AI" row is simply hidden (web parity; no
     * error surfaced).
     */
    fun onSubtitlesPaneShown() {
        if (aiStatusRequested) return
        aiStatusRequested = true
        viewModelScope.launch {
            val status = when (val r = subtitlesRepository.aiStatus()) {
                is ApiResult.Success -> r.data
                else -> SubtitleAiStatus(enabled = false, transcribeEnabled = false)
            }
            _aiTranslate.update { it.copy(statusLoaded = true, status = status) }
        }
    }

    fun openSubtitleSearchDialog() {
        val defaultLang = _uiState.value.preferredTextLanguage
            ?.takeIf { it.isNotBlank() }?.take(2)?.lowercase() ?: "en"
        _subtitleSearch.update {
            // Keep prior results/language when reopening mid-session.
            if (it.hasSearched) it else it.copy(language = defaultLang)
        }
        _uiState.update { it.copy(showSubtitleSearchDialog = true) }
    }

    fun closeSubtitleSearchDialog() {
        _uiState.update { it.copy(showSubtitleSearchDialog = false) }
    }

    fun openAiTranslateDialog() {
        refreshAiQuota() // spec: quota refreshed on open
        _aiTranslate.update { it.copy(phase = AiJobPhase.Idle) }
        _uiState.update { it.copy(showAiTranslateDialog = true) }
    }

    /** Dismiss the dialog. A running job keeps polling — reopening shows live progress. */
    fun closeAiTranslateDialog() {
        _uiState.update { it.copy(showAiTranslateDialog = false) }
    }

    // ---- Subtitle suite: provider search / download ------------------------------

    fun setSubtitleSearchLanguage(code: String) {
        _subtitleSearch.update { it.copy(language = code) }
    }

    fun searchSubtitles() {
        val mediaFileId = _uiState.value.mediaFileId ?: return
        if (_subtitleSearch.value.isSearching) return
        val language = _subtitleSearch.value.language
        _subtitleSearch.update {
            it.copy(isSearching = true, hasSearched = true, error = null, results = emptyList())
        }
        viewModelScope.launch {
            val request = SubtitleSearchRequest(mediaFileId = mediaFileId, languages = listOf(language))
            when (val r = subtitlesRepository.search(request)) {
                is ApiResult.Success -> _subtitleSearch.update {
                    it.copy(isSearching = false, results = r.data.results)
                }
                // No capability probe exists — "no providers configured" arrives
                // here as a plain server error; surface its text verbatim.
                is ApiResult.Error, is ApiResult.NetworkError -> _subtitleSearch.update {
                    it.copy(isSearching = false, error = r.errorMessage("Subtitle search failed"))
                }
            }
        }
    }

    fun downloadSubtitle(result: SubtitleResult) {
        val mediaFileId = _uiState.value.mediaFileId ?: return
        if (_subtitleSearch.value.downloadingResultId != null) return
        _subtitleSearch.update { it.copy(downloadingResultId = result.id, error = null) }
        viewModelScope.launch {
            val request = SubtitleDownloadRequest(
                mediaFileId = mediaFileId,
                provider = result.provider,
                subtitleId = result.id,
                language = result.language,
                releaseName = result.releaseName,
                format = result.format,
                score = result.score,
                hearingImpaired = result.hearingImpaired,
            )
            when (val r = subtitlesRepository.download(request)) {
                is ApiResult.Success -> {
                    refreshSubtitles(autoSelectSubtitleId = r.data.subtitle.id)
                    _subtitleSearch.update {
                        it.copy(downloadingResultId = null, completedNonce = it.completedNonce + 1)
                    }
                }
                is ApiResult.Error, is ApiResult.NetworkError -> _subtitleSearch.update {
                    it.copy(downloadingResultId = null, error = r.errorMessage("Subtitle download failed"))
                }
            }
        }
    }

    // ---- Subtitle suite: track refresh (web-parity, no session restart) ---------

    /**
     * Refetch the downloaded-subtitle list, merge it into
     * [UiState.subtitleUrls] via the shared pure merge, and bump
     * [UiState.subtitleRefreshNonce] so the screen re-prepares the MediaItem
     * (same stream URL + session — only the sidecar list changes). Selection
     * is label-driven: the freshly downloaded track's label when
     * [autoSelectSubtitleId] matches, otherwise the currently selected track's
     * label so the rebuild preserves the user's choice (Media3 track-group
     * overrides don't survive a re-prepare — groups are new instances).
     */
    suspend fun refreshSubtitles(autoSelectSubtitleId: Int?) {
        val state = _uiState.value
        val mediaFileId = state.mediaFileId ?: return
        // Inert without a remote session — merged track URLs are session-scoped.
        val sessionId = state.sessionId ?: return
        val downloaded = when (val r = subtitlesRepository.list(mediaFileId)) {
            is ApiResult.Success -> r.data.subtitles
            is ApiResult.Error -> {
                Log.w(TAG, "refreshSubtitles failed: ${r.code} ${r.message}")
                return
            }
            is ApiResult.NetworkError -> {
                Log.w(TAG, "refreshSubtitles network error", r.exception)
                return
            }
        }
        if (downloaded.isEmpty()) return
        val merged = mergeDownloadedSubtitles(
            existing = state.subtitleUrls,
            downloaded = downloaded,
            sessionId = sessionId,
            serverUrl = state.serverUrl,
        )
        // Label of the track to auto-select, located via the merge contract:
        // downloaded entries occupy the merged list's tail in listing order
        // (same positional contract mobile's downloadedTrackIndex relies on).
        val autoSelectLabel = autoSelectSubtitleId?.let { id ->
            val pos = downloaded.indexOfFirst { it.id == id }
            if (pos < 0) null else merged.getOrNull(merged.size - downloaded.size + pos)?.label
        }
        if (merged == state.subtitleUrls) {
            // Nothing new to mount (e.g. re-download of an existing entry) —
            // honor auto-select against the already-mounted tracks and skip
            // the rebuild entirely.
            autoSelectLabel?.let { label ->
                state.subtitleTracks.firstOrNull { it.label == label }
                    ?.let { _subtitleSelectRequests.tryEmit(it.index) }
            }
            return
        }
        pendingSubtitleSelectLabel = autoSelectLabel
            ?: state.subtitleTracks.firstOrNull { it.isSelected }?.label
        _uiState.update {
            it.copy(subtitleUrls = merged, subtitleRefreshNonce = it.subtitleRefreshNonce + 1)
        }
    }

    // ---- Subtitle suite: AI translate / transcribe -------------------------------

    fun refreshAiQuota() {
        viewModelScope.launch {
            when (val r = subtitlesRepository.aiQuota()) {
                is ApiResult.Success -> _aiTranslate.update { it.copy(quota = r.data) }
                else -> Unit // quota line is simply absent on failure
            }
        }
    }

    /**
     * Submit an AI job and poll to completion. `start_position` = current
     * playhead (web parity); no `session_id` — Android polls instead of
     * streaming live cues. Runs in viewModelScope so player exit cancels the
     * poll via structured concurrency (the server job itself keeps running).
     */
    fun submitAiTranslate(
        kind: String,
        sourceIndex: Int,
        sourceLanguage: String?,
        targetLanguage: String,
    ) {
        val mediaFileId = _uiState.value.mediaFileId ?: return
        val phase = _aiTranslate.value.phase
        if (phase is AiJobPhase.Submitting || phase is AiJobPhase.Running) return
        _aiTranslate.update { it.copy(phase = AiJobPhase.Submitting) }
        aiJobPollJob?.cancel()
        aiJobPollJob = viewModelScope.launch {
            val request = SubtitleTranslateRequest(
                mediaFileId = mediaFileId,
                kind = kind,
                sourceIndex = sourceIndex,
                sourceLanguage = sourceLanguage?.ifBlank { null },
                targetLanguage = targetLanguage.ifBlank { null },
                startPosition = _uiState.value.position,
            )
            val job = when (val r = subtitlesRepository.translate(request)) {
                is ApiResult.Success -> r.data.job
                is ApiResult.Error -> {
                    // 429 = quota exhausted → refresh quota so the dialog
                    // flips to the exhausted state; 503 = engine unconfigured.
                    if (r.code == 429) refreshAiQuota()
                    _aiTranslate.update {
                        it.copy(phase = AiJobPhase.Failed(r.errorMessage("Translation failed")))
                    }
                    return@launch
                }
                is ApiResult.NetworkError -> {
                    _aiTranslate.update {
                        it.copy(phase = AiJobPhase.Failed(r.errorMessage("Translation failed")))
                    }
                    return@launch
                }
            }
            activeAiJobId = job.id
            _aiTranslate.update {
                it.copy(phase = AiJobPhase.Running(job.progress, job.progressMessage.ifBlank { null }))
            }
            val outcome = subtitlesRepository.pollJob(
                jobId = job.id,
                onUpdate = { update ->
                    _aiTranslate.update {
                        it.copy(
                            phase = AiJobPhase.Running(
                                update.progress,
                                update.progressMessage.ifBlank { null },
                            ),
                        )
                    }
                },
            )
            activeAiJobId = null
            when (outcome) {
                is SubtitlesRepository.SubtitleJobOutcome.Completed -> {
                    refreshSubtitles(autoSelectSubtitleId = outcome.resultSubtitleId)
                    _aiTranslate.update {
                        it.copy(phase = AiJobPhase.Idle, completedNonce = it.completedNonce + 1)
                    }
                }
                is SubtitlesRepository.SubtitleJobOutcome.Failed -> _aiTranslate.update {
                    it.copy(phase = AiJobPhase.Failed(outcome.message ?: "Translation failed"))
                }
                SubtitlesRepository.SubtitleJobOutcome.Cancelled -> _aiTranslate.update {
                    it.copy(phase = AiJobPhase.Idle)
                }
            }
        }
    }

    /** Dialog Cancel row: stop polling, ask the server to cancel, return to the form. */
    fun cancelAiTranslateJob() {
        val jobId = activeAiJobId
        aiJobPollJob?.cancel()
        aiJobPollJob = null
        activeAiJobId = null
        _aiTranslate.update { it.copy(phase = AiJobPhase.Idle) }
        if (jobId != null) {
            viewModelScope.launch { subtitlesRepository.cancelJob(jobId) }
        }
    }

    /** Failed phase → back to the form after the user acknowledges the error. */
    fun clearAiTranslateError() {
        _aiTranslate.update { it.copy(phase = AiJobPhase.Idle) }
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

    /**
     * HUD Audio pane stepper handler. Coerced to ±500ms in the store; the
     * service binding (E T3) picks up the new value and pushes it into the
     * shared [com.continuum.app.common.player.audio.DelayAudioProcessor]
     * (forcing a flush via `seekTo(currentPosition)` so the change applies
     * mid-playback).
     */
    fun onAudioDelayChanged(delayMs: Int) {
        viewModelScope.launch { playerSettingsStore.setAudioSyncMs(delayMs) }
    }

    /**
     * HUD Subtitles pane stepper handler. Coerced to ±500ms in the store; the
     * service binding (A.3f T2) picks up the new value and pushes it into the
     * shared [com.continuum.app.common.player.subtitle.SubtitleOffsetHolder]
     * (forcing a flush via `seekTo(currentPosition)` so the change applies
     * mid-playback by dropping already-buffered cues).
     */
    fun onSubtitleDelayChanged(delayMs: Int) {
        viewModelScope.launch { playerSettingsStore.setSubtitleSyncMs(delayMs) }
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
