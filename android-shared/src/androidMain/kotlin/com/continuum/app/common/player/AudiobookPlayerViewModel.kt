package com.continuum.app.common.player

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.continuum.app.audiobook.AudiobookChapter
import com.continuum.app.audiobook.AudiobookChapters
import com.continuum.app.common.audiobook.AudiobookBookmarksStore
import com.continuum.app.common.audiobook.AudiobookPositionStore
import com.continuum.app.common.downloads.DownloadEnqueuer
import com.continuum.app.common.downloads.OfflineMediaResolver
import com.continuum.app.model.audiobook.AudiobookBookmark
import com.continuum.app.model.catalog.VersionChapter
import com.continuum.app.model.playback.PlayMethod
import com.continuum.app.model.playback.PlaybackSessionResponse
import com.continuum.app.network.ApiResult
import com.continuum.app.network.ServerRegistry
import com.continuum.app.repository.CatalogRepository
import com.continuum.app.repository.ProfileRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlin.random.Random

/**
 * UI state for the audiobook player.
 *
 * Distinct from the video PlayerViewModel because the audiobook player
 * cares about chapter navigation, speed, and sleep-timer state rather
 * than tracks / subtitles / aspect ratios.
 */
data class AudiobookPlayerUiState(
    val isLoading: Boolean = true,
    val title: String = "",
    val author: String? = null,
    val narrator: String? = null,
    val overview: String? = null,
    val coverUrl: String? = null,
    val coverThumbhash: String? = null,
    val chapters: List<VersionChapter> = emptyList(),
    val durationSeconds: Double = 0.0,
    val positionSeconds: Double = 0.0,
    val isPlaying: Boolean = false,
    // Default to "not paused" so the player auto-plays on open: the Compose
    // layer mirrors isPaused into Media3's playWhenReady, and a `true` default
    // raced the resume/auto-play wiring and left the book paused at 0:00 until
    // the user hit play.
    val isPaused: Boolean = false,
    val playbackSpeed: Float = 1.0f,
    val skipBackSeconds: Int = 30,
    val skipForwardSeconds: Int = 30,
    val sleepTimerMinutesLeft: Int? = null,
    val streamUrl: String? = null,
    val sessionId: String? = null,
    val selectedFileId: Int? = null,
    val error: String? = null,
)

/**
 * Drives the [AudiobookPlayerScreen]. Wraps Media3 for actual decoding
 * (same engine as the video player) but exposes audiobook-shaped
 * commands: [seekBy30], [setSpeed], [jumpToChapter], [startSleepTimer].
 */
class AudiobookPlayerViewModel(
    private val catalogRepository: CatalogRepository,
    private val playbackSessionManager: PlaybackSessionManager,
    private val capabilityDetector: PlaybackCapabilityDetector,
    private val bookmarksStore: AudiobookBookmarksStore,
    private val positionStore: AudiobookPositionStore,
    private val serverRegistry: ServerRegistry,
    private val profileRepository: ProfileRepository,
    private val offlineMediaResolver: OfflineMediaResolver,
    private val audiobookSettings: AudiobookSettingsStore,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val contentId: String = savedStateHandle.get<String>("contentId") ?: ""
    private val requestedFileIdRaw: String? = savedStateHandle.get<String>("fileId")
    private val hasRequestedFileId: Boolean = !requestedFileIdRaw.isNullOrBlank()
    private val requestedFileId: Int? = requestedFileIdRaw?.toIntOrNull()

    /** When true ("Play from beginning"), ignore saved progress and start at 0. */
    private val startFromBeginning: Boolean = savedStateHandle.get<Boolean>("fromStart") ?: false

    private val _uiState = MutableStateFlow(AudiobookPlayerUiState())
    val uiState: StateFlow<AudiobookPlayerUiState> = _uiState.asStateFlow()

    // Chapter-derived views over ui-state, computed via the pure
    // [AudiobookChapters] math so the phone and TV players share one source of
    // truth. Eagerly started so the UI has a value the moment it subscribes.

    /** Index of the chapter the current position falls in. */
    val currentChapterIndex: StateFlow<Int> = uiState
        .map { AudiobookChapters.currentIndex(it.chapters.toAudiobookChapters(), it.positionSeconds) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    /** Progress within the current chapter, 0..1. */
    val chapterProgress: StateFlow<Float> = uiState
        .map {
            AudiobookChapters.chapterProgress(
                it.chapters.toAudiobookChapters(),
                it.positionSeconds,
            ).toFloat()
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0f)

    /** "Chapter N of M" label, or empty when the book has 0/1 chapters. */
    val chapterCountLabel: StateFlow<String> = uiState
        .map { AudiobookChapters.countLabel(it.chapters.toAudiobookChapters(), it.positionSeconds).orEmpty() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, "")

    private val _bookmarks = MutableStateFlow<List<AudiobookBookmark>>(emptyList())
    val bookmarks: StateFlow<List<AudiobookBookmark>> = _bookmarks.asStateFlow()

    /** Position the player should seek to on first prepare. Resolved from
     *  the local snapshot at init; the Compose layer reads it via
     *  [resumePositionSeconds] and consumes [consumeResumePosition] once
     *  it's applied. Server-side resume can replace this later. */
    private val _resumePosition = MutableStateFlow<Double?>(null)
    val resumePositionSeconds: StateFlow<Double?> = _resumePosition.asStateFlow()

    /** Guards the one-time seed of [AudiobookPlayerUiState.playbackSpeed] from
     *  the persisted default; once seeded, in-session speed changes win. */
    private var speedSeeded = false

    init {
        observeAudiobookSettings()
        if (contentId.isNotBlank()) {
            loadDetail()
            loadBookmarks()
            startPeriodicPositionSave()
        }
    }

    /** Mirror the persisted skip interval into ui-state, and seed playback
     *  speed from the saved default exactly once. */
    private fun observeAudiobookSettings() {
        viewModelScope.launch {
            audiobookSettings.skipBackSecondsFlow.collect { seconds ->
                _uiState.update { it.copy(skipBackSeconds = seconds) }
            }
        }
        viewModelScope.launch {
            audiobookSettings.skipForwardSecondsFlow.collect { seconds ->
                _uiState.update { it.copy(skipForwardSeconds = seconds) }
            }
        }
        viewModelScope.launch {
            val savedDefault = audiobookSettings.defaultSpeedFlow.first()
            if (!speedSeeded) {
                speedSeeded = true
                _uiState.update { it.copy(playbackSpeed = savedDefault.coerceIn(0.5f, 3.0f)) }
            }
        }
    }

    private fun loadDetail() {
        viewModelScope.launch {
            when (val r = catalogRepository.getItemDetail(contentId)) {
                is ApiResult.Success -> {
                    val d = r.data
                    if (hasRequestedFileId && requestedFileId == null) {
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                error = "Selected audiobook file is unavailable.",
                            )
                        }
                        return@launch
                    }

                    val selectedVersion = if (requestedFileId != null) {
                        d.versions.firstOrNull { it.fileId == requestedFileId }
                    } else {
                        d.versions.firstOrNull()
                    }

                    if (selectedVersion == null) {
                        val message = if (hasRequestedFileId) {
                            "Selected audiobook file is unavailable."
                        } else {
                            "No playable audiobook file is available."
                        }
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                error = message,
                            )
                        }
                        return@launch
                    }

                    val resumePosition = if (startFromBeginning) {
                        _resumePosition.value = null
                        null
                    } else {
                        loadResumePositionSnapshot(d.userData?.positionSeconds)
                    }
                    val offlineMedia = offlineMediaResolver.findLocalMedia(
                        contentId = d.contentId,
                        requestedFileId = selectedVersion.fileId,
                        allowFallback = !hasRequestedFileId,
                    )
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            title = d.title,
                            author = d.audiobook?.authorNames,
                            narrator = d.audiobook?.narratorNames,
                            overview = d.overview,
                            coverUrl = d.posterUrl,
                            coverThumbhash = d.posterThumbhash,
                            durationSeconds = d.audiobook?.totalDurationSeconds?.toDouble()
                                ?: selectedVersion.duration,
                            chapters = selectedVersion.chapters.orEmpty(),
                            selectedFileId = selectedVersion.fileId,
                            streamUrl = offlineMedia?.fileUrl,
                            sessionId = null,
                        )
                    }

                    if (offlineMedia != null) {
                        _uiState.update { it.copy(error = null) }
                        return@launch
                    }

                    val profileId = profileRepository.getActiveProfileId()
                    if (profileId == null) {
                        _uiState.update { it.copy(error = "No active profile") }
                        return@launch
                    }

                    // Audiobooks are audio-only; their sole "video" stream is
                    // an embedded cover-art still (mjpeg/png/jpeg). The server's
                    // resolver gates DIRECT play on the client decoding the
                    // file's video codec, so advertise the still-image codecs
                    // to keep audiobooks on DIRECT instead of a pointless
                    // audio-only transcode. Scoped here so real video playback
                    // (PlayerViewModel) keeps its true decoder list.
                    val capabilities = capabilityDetector.detect().let { caps ->
                        caps.copy(
                            codecsVideo = (caps.codecsVideo + AUDIOBOOK_COVER_ART_CODECS)
                                .distinct(),
                        )
                    }
                    when (val playback = playbackSessionManager.startSession(
                        fileId = selectedVersion.fileId,
                        profileId = profileId,
                        capabilities = capabilities,
                        startPosition = resumePosition ?: 0.0,
                    )) {
                        is ApiResult.Success -> applySession(
                            session = playback.data,
                            seekSeconds = resumePosition ?: 0.0,
                        )
                        is ApiResult.Error -> _uiState.update {
                            it.copy(
                                streamUrl = null,
                                sessionId = null,
                                isPlaying = false,
                                isPaused = true,
                                error = playback.message.ifBlank { "Audiobook playback failed" },
                            )
                        }
                        is ApiResult.NetworkError -> _uiState.update {
                            it.copy(
                                streamUrl = null,
                                sessionId = null,
                                isPlaying = false,
                                isPaused = true,
                                error = playback.exception.message ?: "Network error",
                            )
                        }
                    }
                }
                is ApiResult.Error -> loadOfflineOnly(error = r.message)
                is ApiResult.NetworkError -> loadOfflineOnly(error = r.exception.message)
            }
        }
    }

    /**
     * Apply a started session to UI state, honoring the server's
     * `play_method`. Mirrors the video player's
     * [com.continuum.app.android.ui.screens.player.PlayerViewModel] handling:
     * a DIRECT session streams [PlaybackSessionResponse.streamUrl] as-is, while
     * REMUX / TRANSCODE require an explicit transcode start whose HLS manifest
     * URL is what Media3 must actually load. Without this branch the raw
     * `stream_url` for a transcode session 404s until a job is started.
     *
     * Audiobooks have no video resolution, so the transcode resolution is left
     * empty — the server keeps audio-only delivery.
     */
    private suspend fun applySession(
        session: PlaybackSessionResponse,
        seekSeconds: Double,
    ) {
        // Server stream URLs are relative (e.g. /playback/stream/...). The
        // Compose layer hands them straight to Media3, so they must be
        // absolute here or OkHttp fails the open with "Malformed URL".
        val serverUrl = playbackSessionManager.getServerUrl()
        if (session.playMethod == PlayMethod.TRANSCODE || session.playMethod == PlayMethod.REMUX) {
            val mode = if (session.playMethod == PlayMethod.REMUX) {
                PlaybackSessionManager.TranscodeMode.REMUX
            } else {
                PlaybackSessionManager.TranscodeMode.FULL
            }
            when (val r = playbackSessionManager.startTranscodeFallback(
                session = session,
                seekSeconds = seekSeconds,
                resolution = "",
                mode = mode,
            )) {
                is ApiResult.Success -> _uiState.update {
                    it.copy(
                        streamUrl = resolvePlaybackStreamUrl(serverUrl, r.data.streamUrl),
                        sessionId = r.data.sessionId,
                        error = null,
                    )
                }
                is ApiResult.Error -> _uiState.update {
                    it.copy(
                        streamUrl = null,
                        sessionId = null,
                        isPlaying = false,
                        isPaused = true,
                        error = r.message.ifBlank { "Audiobook transcode failed" },
                    )
                }
                is ApiResult.NetworkError -> _uiState.update {
                    it.copy(
                        streamUrl = null,
                        sessionId = null,
                        isPlaying = false,
                        isPaused = true,
                        error = r.exception.message ?: "Network error",
                    )
                }
            }
        } else {
            _uiState.update {
                it.copy(
                    streamUrl = resolvePlaybackStreamUrl(serverUrl, session.streamUrl),
                    sessionId = session.sessionId,
                    error = null,
                )
            }
        }
    }

    private suspend fun loadOfflineOnly(error: String?) {
        val media = offlineMediaResolver.findLocalMedia(
            contentId = contentId,
            requestedFileId = requestedFileId,
            allowFallback = !hasRequestedFileId,
        )
        if (media == null) {
            _uiState.update { it.copy(isLoading = false, error = error) }
            return
        }
        // No server detail when offline — resume from the local snapshot alone
        // (unless the user explicitly chose to play from the beginning).
        if (!startFromBeginning) loadResumePositionSnapshot()
        _uiState.update {
            it.copy(
                isLoading = false,
                title = media.sidecar.title,
                author = media.sidecar.author,
                narrator = media.sidecar.narrator,
                overview = media.sidecar.overview,
                coverUrl = media.sidecar.posterUrl,
                coverThumbhash = media.sidecar.posterThumbhash,
                durationSeconds = media.sidecar.durationSeconds ?: 0.0,
                chapters = media.sidecar.chapters.orEmpty(),
                streamUrl = media.fileUrl,
                selectedFileId = media.fileId,
                sessionId = null,
                error = null,
            )
        }
    }

    /** Update local position tracker. Driven by Media3 player callback in
     *  the Compose layer. */
    fun onPositionChanged(seconds: Double) {
        val previous = _uiState.value.positionSeconds
        _uiState.update { it.copy(positionSeconds = seconds) }
        maybeFireSleepBoundary(previous, seconds)
    }

    /** Reflect Media3's *actual* playing state (false while buffering/seeking).
     *  Deliberately does NOT touch [isPaused]: a seek triggers a transient
     *  rebuffer that flips isPlaying false, and conflating that with pause
     *  intent latched the player paused after every skip. Pause intent comes
     *  from [onPauseStateChanged] (playWhenReady) instead. */
    fun onPlayingChanged(playing: Boolean) {
        _uiState.update { it.copy(isPlaying = playing) }
    }

    /** Reflect Media3's playWhenReady — the real pause intent. */
    fun onPauseStateChanged(isPaused: Boolean) {
        _uiState.update { it.copy(isPaused = isPaused) }
    }

    fun togglePlay() {
        _uiState.update { it.copy(isPaused = !it.isPaused) }
    }

    /** Audiobook-style ±30s seek. The Compose layer reads the requested
     *  position from [pendingSeekToSeconds] and clears the marker once it
     *  applies the seek to the underlying Media3 controller. */
    private val _pendingSeek = MutableStateFlow<Double?>(null)
    val pendingSeekToSeconds: StateFlow<Double?> = _pendingSeek.asStateFlow()

    fun seekBy(deltaSeconds: Double) {
        val target = (_uiState.value.positionSeconds + deltaSeconds)
            .coerceIn(0.0, _uiState.value.durationSeconds.coerceAtLeast(0.0))
        _pendingSeek.value = target
    }

    fun seekTo(seconds: Double) {
        _pendingSeek.value = seconds.coerceAtLeast(0.0)
    }

    fun consumePendingSeek() { _pendingSeek.value = null }

    fun jumpToChapter(chapter: VersionChapter) {
        seekTo(chapter.startSeconds)
    }

    /** Seek to the previous chapter, restarting the current chapter when more
     *  than [AudiobookChapters.PREV_RESTART_THRESHOLD_SECONDS] into it. */
    fun skipToPreviousChapter() {
        val state = _uiState.value
        seekTo(
            AudiobookChapters.previousChapterTarget(
                state.chapters.toAudiobookChapters(),
                state.positionSeconds,
            ),
        )
    }

    /** Seek to the start of the next chapter (clamps on the last chapter). */
    fun skipToNextChapter() {
        val state = _uiState.value
        seekTo(
            AudiobookChapters.nextChapterTarget(
                state.chapters.toAudiobookChapters(),
                state.positionSeconds,
            ),
        )
    }

    fun setSpeed(speed: Float) {
        _uiState.update { it.copy(playbackSpeed = speed.coerceIn(0.5f, 3.0f)) }
    }

    /** Apply [speed] to the current session and persist it as the default
     *  for future audiobooks. */
    fun setDefaultSpeed(speed: Float) {
        val clamped = speed.coerceIn(0.5f, 3.0f)
        speedSeeded = true
        _uiState.update { it.copy(playbackSpeed = clamped) }
        viewModelScope.launch { audiobookSettings.setDefaultSpeed(clamped) }
    }

    // ── Configurable skip ─────────────────────────────────────────────────

    /** Skip back by the user's configured interval (default 30s). */
    fun skipBack() = seekBy(-_uiState.value.skipBackSeconds.toDouble())

    /** Skip forward by the user's configured interval (default 30s). */
    fun skipForward() = seekBy(_uiState.value.skipForwardSeconds.toDouble())

    fun setSkipBackSeconds(seconds: Int) {
        viewModelScope.launch { audiobookSettings.setSkipBackSeconds(seconds) }
    }

    fun setSkipForwardSeconds(seconds: Int) {
        viewModelScope.launch { audiobookSettings.setSkipForwardSeconds(seconds) }
    }

    // ── Sleep timer ──────────────────────────────────────────────────────

    private var sleepTimerJob: Job? = null

    /** Fixed position (seconds) an end-of-chapter / end-of-book timer fires
     *  on. Resolved once at apply time so it does not drift as the user
     *  seeks; null when no boundary timer is armed. */
    private var sleepBoundarySeconds: Double? = null

    /**
     * Sleep timer requests. Apply via [applySleepTimer]; the player
     * screen mirrors [SleepTimerEffect] into the controller (auto-pause
     * fires when [remainingSeconds] hits 0).
     */
    private val _sleepTimerChoice = MutableStateFlow<SleepTimerChoice>(SleepTimerChoice.Off)
    val sleepTimerChoice: StateFlow<SleepTimerChoice> = _sleepTimerChoice.asStateFlow()

    /**
     * Apply a new timer choice, cancelling any active timer first.
     * [SleepTimerChoice.Minutes] runs a wall-clock countdown; [EndOfChapter]
     * and [EndOfBook] resolve a fixed position boundary now (via the pure
     * [AudiobookChapters] math) and pause when playback crosses it.
     */
    fun applySleepTimer(choice: SleepTimerChoice) {
        sleepTimerJob?.cancel()
        sleepTimerJob = null
        sleepBoundarySeconds = null
        _sleepTimerChoice.value = choice

        when (choice) {
            SleepTimerChoice.Off ->
                _uiState.update { it.copy(sleepTimerMinutesLeft = null) }
            is SleepTimerChoice.Minutes ->
                startCountdown(choice.minutes * 60)
            SleepTimerChoice.EndOfChapter, SleepTimerChoice.EndOfBook -> {
                val state = _uiState.value
                sleepBoundarySeconds = sleepBoundaryFor(
                    choice = choice,
                    chapters = state.chapters.toAudiobookChapters(),
                    durationSeconds = state.durationSeconds,
                    positionSeconds = state.positionSeconds,
                )
                // Boundary timers have no minute countdown; the selected
                // choice is what the UI reflects.
                _uiState.update { it.copy(sleepTimerMinutesLeft = null) }
            }
        }
    }

    private fun startCountdown(totalSeconds: Int) {
        _uiState.update { it.copy(sleepTimerMinutesLeft = ((totalSeconds + 59) / 60).coerceAtLeast(1)) }
        sleepTimerJob = viewModelScope.launch {
            var remaining = totalSeconds
            while (remaining > 0) {
                delay(1000)
                remaining -= 1
                _uiState.update {
                    it.copy(
                        sleepTimerMinutesLeft = ((remaining + 59) / 60)
                            .coerceAtLeast(0)
                            .takeIf { remaining > 0 },
                    )
                }
            }
            fireSleep()
        }
    }

    /** Position watcher for the boundary timers. Pauses exactly once, on the
     *  tick that crosses the resolved boundary. Driven by [onPositionChanged]. */
    private fun maybeFireSleepBoundary(previousSeconds: Double, currentSeconds: Double) {
        val boundary = sleepBoundarySeconds ?: return
        if (AudiobookChapters.hasCrossedBoundary(previousSeconds, currentSeconds, boundary)) {
            sleepBoundarySeconds = null
            fireSleep()
        }
    }

    /** Pause playback and clear the timer. The screen's LaunchedEffect on
     *  isPaused mirrors this into the controller. */
    private fun fireSleep() {
        _uiState.update { it.copy(isPaused = true, isPlaying = false, sleepTimerMinutesLeft = null) }
        _sleepTimerChoice.value = SleepTimerChoice.Off
    }

    fun startSleepTimer(minutes: Int) = applySleepTimer(SleepTimerChoice.Minutes(minutes))
    fun cancelSleepTimer() = applySleepTimer(SleepTimerChoice.Off)

    // ── Bookmarks ────────────────────────────────────────────────────────

    private suspend fun resolveScope(): Pair<String, String> {
        val serverId = serverRegistry.activeServerId.value ?: DownloadEnqueuer.DEFAULT_SERVER_ID
        val profileId = profileRepository.getActiveProfileId() ?: DownloadEnqueuer.DEFAULT_PROFILE_ID
        return serverId to profileId
    }

    private fun loadBookmarks() {
        viewModelScope.launch {
            val (serverId, profileId) = resolveScope()
            val loaded = withContext(Dispatchers.IO) {
                bookmarksStore.list(serverId, profileId, contentId)
            }
            _bookmarks.value = loaded
        }
    }

    /** Drop a bookmark at the current position. Chapter title is
     *  captured so the list can render it without re-resolving. */
    fun addBookmark(note: String? = null) {
        val state = _uiState.value
        val chapterIndex = AudiobookChapters.currentIndex(
            state.chapters.toAudiobookChapters(),
            state.positionSeconds,
        )
        val chapter = state.chapters.getOrNull(chapterIndex)
            ?.takeIf { state.positionSeconds >= it.startSeconds }

        val bookmark = AudiobookBookmark(
            id = generateBookmarkId(),
            positionSeconds = state.positionSeconds,
            chapterTitle = chapter?.title,
            note = note?.takeIf { it.isNotBlank() },
            createdAtMs = System.currentTimeMillis(),
        )
        viewModelScope.launch {
            val (serverId, profileId) = resolveScope()
            val updated = withContext(Dispatchers.IO) {
                bookmarksStore.add(serverId, profileId, contentId, bookmark)
            }
            _bookmarks.value = updated
        }
    }

    fun removeBookmark(id: String) {
        viewModelScope.launch {
            val (serverId, profileId) = resolveScope()
            val updated = withContext(Dispatchers.IO) {
                bookmarksStore.remove(serverId, profileId, contentId, id)
            }
            _bookmarks.value = updated
        }
    }

    fun jumpToBookmark(bookmark: AudiobookBookmark) {
        seekTo(bookmark.positionSeconds)
    }

    private fun generateBookmarkId(): String =
        // Compact, sortable-ish, sufficient for client-side uniqueness.
        // Server-issued ids will replace these once /bookmarks lands.
        "local-${System.currentTimeMillis()}-${Random.nextInt(0xFFFF).toString(16)}"

    // ── Position resume ──────────────────────────────────────────────────

    private var positionSaveJob: Job? = null
    private var stoppingSessionId: String? = null

    /** Snapshot the current position (and any future server progress
     *  report). Called from periodic timer + pause + seek + close. */
    private fun savePosition() {
        val state = _uiState.value
        if (state.durationSeconds <= 0) return  // metadata not loaded yet
        viewModelScope.launch {
            if (state.positionSeconds > 0) {
                val (serverId, profileId) = resolveScope()
                withContext(Dispatchers.IO) {
                    runCatching {
                        positionStore.write(
                            serverId, profileId, contentId,
                            AudiobookPositionStore.Snapshot(
                                positionSeconds = state.positionSeconds,
                                durationSeconds = state.durationSeconds,
                                updatedAtMs = System.currentTimeMillis(),
                            ),
                        )
                    }
                }
            }
            reportSessionProgress(state)
        }
    }

    /** Resume-on-open. Returns the furthest of the on-device local snapshot
     *  and the server's recorded position ([serverPositionSeconds], from the
     *  item's `user_data`) so we never resume behind progress made on another
     *  device — and so a device that has never played this book locally still
     *  resumes from the server's Continue-Listening position instead of 0:00.
     *  Exposed via [resumePositionSeconds] for the Compose host to seek to once
     *  the controller + metadata are ready. */
    private suspend fun loadResumePositionSnapshot(serverPositionSeconds: Double? = null): Double? {
        val (serverId, profileId) = resolveScope()
        val snapshot = withContext(Dispatchers.IO) {
            positionStore.read(serverId, profileId, contentId)
        }
        val local = snapshot?.positionSeconds?.takeIf { it > 0 }
        val server = serverPositionSeconds?.takeIf { it > 0 }
        val position = listOfNotNull(local, server).maxOrNull()
        _resumePosition.value = position
        return position
    }

    fun consumeResumePosition() { _resumePosition.value = null }

    /** Persist position every 5s while playing so a crash loses at
     *  most a few seconds. Also fires on pause/seek/close via direct
     *  [savePosition] calls. */
    private fun startPeriodicPositionSave() {
        positionSaveJob?.cancel()
        positionSaveJob = viewModelScope.launch {
            while (true) {
                delay(5000)
                if (_uiState.value.isPlaying) savePosition()
            }
        }
    }

    /** Public hook for the Compose host's lifecycle bridge: call on
     *  pause, seek, or destroy. */
    fun flushPosition() = savePosition()

    fun stopPlaybackSession() {
        val state = _uiState.value
        val sessionId = state.sessionId ?: return
        if (stoppingSessionId == sessionId) return
        stoppingSessionId = sessionId
        _uiState.update {
            it.copy(
                streamUrl = null,
                isPlaying = false,
                isPaused = true,
            )
        }
        viewModelScope.launch {
            try {
                withContext(NonCancellable + Dispatchers.IO) {
                    reportAndStopSession(
                        sessionId = sessionId,
                        positionSeconds = state.positionSeconds,
                        isPaused = true,
                    )
                }
            } finally {
                if (_uiState.value.sessionId == sessionId) {
                    _uiState.update { it.copy(sessionId = null) }
                }
                if (stoppingSessionId == sessionId) {
                    stoppingSessionId = null
                }
            }
        }
    }

    private suspend fun reportSessionProgress(state: AudiobookPlayerUiState) {
        val sessionId = state.sessionId ?: return
        runCatching {
            playbackSessionManager.reportProgress(
                sessionId = sessionId,
                position = state.positionSeconds,
                isPaused = state.isPaused,
            )
        }
    }

    private suspend fun reportAndStopSession(
        sessionId: String,
        positionSeconds: Double,
        isPaused: Boolean,
    ) {
        runCatching {
            playbackSessionManager.reportProgress(
                sessionId = sessionId,
                position = positionSeconds,
                isPaused = isPaused,
            )
        }
        runCatching { playbackSessionManager.stopSession(sessionId) }
    }

    override fun onCleared() {
        sleepTimerJob?.cancel()
        positionSaveJob?.cancel()
        val state = _uiState.value
        state.sessionId?.let { sessionId ->
            runCatching {
                runBlocking(Dispatchers.IO) {
                    reportAndStopSession(
                        sessionId = sessionId,
                        positionSeconds = state.positionSeconds,
                        isPaused = true,
                    )
                }
            }
        }
        super.onCleared()
    }

    companion object {
        private const val TAG = "AudiobookPlayerViewModel"

        /** Still-image codecs ffprobe reports for embedded audiobook cover
         *  art. Advertised as "video" so the server resolves these audio-only
         *  items to DIRECT instead of transcoding the poster. */
        private val AUDIOBOOK_COVER_ART_CODECS =
            listOf("mjpeg", "png", "jpeg", "bmp", "gif")

        /**
         * Fixed position boundary (seconds) a boundary sleep timer should fire
         * on for [choice], resolved against [positionSeconds] at apply time;
         * null for [SleepTimerChoice.Off] / [SleepTimerChoice.Minutes]. An
         * end-of-chapter timer with no chapters degrades to the book end.
         * Pure → unit-tested.
         */
        internal fun sleepBoundaryFor(
            choice: SleepTimerChoice,
            chapters: List<AudiobookChapter>,
            durationSeconds: Double,
            positionSeconds: Double,
        ): Double? = when (choice) {
            SleepTimerChoice.EndOfChapter ->
                AudiobookChapters.currentChapterEndSeconds(chapters, positionSeconds)
                    ?: AudiobookChapters.bookEndSeconds(chapters, durationSeconds)
            SleepTimerChoice.EndOfBook ->
                AudiobookChapters.bookEndSeconds(chapters, durationSeconds)
            SleepTimerChoice.Off, is SleepTimerChoice.Minutes -> null
        }

        /**
         * Whether playback stepping from [previousSeconds] to [currentSeconds]
         * should trip a sleep pause for [choice] — a pure composition of
         * [sleepBoundaryFor] (resolved from the prior position) and
         * [AudiobookChapters.hasCrossedBoundary]. Stands in for the
         * Android-bound position watcher in unit tests.
         */
        internal fun shouldPauseForSleep(
            choice: SleepTimerChoice,
            chapters: List<AudiobookChapter>,
            durationSeconds: Double,
            previousSeconds: Double,
            currentSeconds: Double,
        ): Boolean {
            val boundary = sleepBoundaryFor(choice, chapters, durationSeconds, previousSeconds)
                ?: return false
            return AudiobookChapters.hasCrossedBoundary(previousSeconds, currentSeconds, boundary)
        }
    }
}

/** Project the server chapter list onto the pure [AudiobookChapter] span
 *  model that [AudiobookChapters] math operates on. */
private fun List<VersionChapter>.toAudiobookChapters(): List<AudiobookChapter> =
    map { AudiobookChapter(startSeconds = it.startSeconds, endSeconds = it.endSeconds) }
