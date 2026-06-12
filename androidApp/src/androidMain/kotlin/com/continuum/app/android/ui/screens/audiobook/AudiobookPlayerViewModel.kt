package com.continuum.app.android.ui.screens.audiobook

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.continuum.app.common.audiobook.AudiobookBookmarksStore
import com.continuum.app.common.audiobook.AudiobookPositionStore
import com.continuum.app.common.downloads.DownloadEnqueuer
import com.continuum.app.common.downloads.OfflineMediaResolver
import com.continuum.app.common.player.PlaybackCapabilityDetector
import com.continuum.app.common.player.PlaybackSessionManager
import com.continuum.app.common.player.SleepTimerChoice
import com.continuum.app.common.player.resolvePlaybackStreamUrl
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
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
    val coverUrl: String? = null,
    val coverThumbhash: String? = null,
    val chapters: List<VersionChapter> = emptyList(),
    val durationSeconds: Double = 0.0,
    val positionSeconds: Double = 0.0,
    val isPlaying: Boolean = false,
    val isPaused: Boolean = true,
    val playbackSpeed: Float = 1.0f,
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
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val contentId: String = savedStateHandle.get<String>("contentId") ?: ""
    private val requestedFileIdRaw: String? = savedStateHandle.get<String>("fileId")
    private val hasRequestedFileId: Boolean = !requestedFileIdRaw.isNullOrBlank()
    private val requestedFileId: Int? = requestedFileIdRaw?.toIntOrNull()

    private val _uiState = MutableStateFlow(AudiobookPlayerUiState())
    val uiState: StateFlow<AudiobookPlayerUiState> = _uiState.asStateFlow()

    private val _bookmarks = MutableStateFlow<List<AudiobookBookmark>>(emptyList())
    val bookmarks: StateFlow<List<AudiobookBookmark>> = _bookmarks.asStateFlow()

    /** Position the player should seek to on first prepare. Resolved from
     *  the local snapshot at init; the Compose layer reads it via
     *  [resumePositionSeconds] and consumes [consumeResumePosition] once
     *  it's applied. Server-side resume can replace this later. */
    private val _resumePosition = MutableStateFlow<Double?>(null)
    val resumePositionSeconds: StateFlow<Double?> = _resumePosition.asStateFlow()

    init {
        if (contentId.isNotBlank()) {
            loadDetail()
            loadBookmarks()
            startPeriodicPositionSave()
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

                    val resumePosition = loadResumePositionSnapshot()
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

    private fun loadOfflineOnly(error: String?) {
        val media = offlineMediaResolver.findLocalMedia(
            contentId = contentId,
            requestedFileId = requestedFileId,
            allowFallback = !hasRequestedFileId,
        )
        if (media == null) {
            _uiState.update { it.copy(isLoading = false, error = error) }
            return
        }
        _uiState.update {
            it.copy(
                isLoading = false,
                title = media.sidecar.title,
                coverUrl = media.sidecar.posterUrl,
                coverThumbhash = media.sidecar.posterThumbhash,
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
        _uiState.update { it.copy(positionSeconds = seconds) }
    }

    /** Reflect playWhenReady state from Media3. */
    fun onPlayingChanged(playing: Boolean) {
        _uiState.update { it.copy(isPlaying = playing, isPaused = !playing) }
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

    fun setSpeed(speed: Float) {
        _uiState.update { it.copy(playbackSpeed = speed.coerceIn(0.5f, 3.0f)) }
    }

    // ── Sleep timer ──────────────────────────────────────────────────────

    private var sleepTimerJob: Job? = null

    /**
     * Sleep timer requests. Apply via [applySleepTimer]; the player
     * screen mirrors [SleepTimerEffect] into the controller (auto-pause
     * fires when [remainingSeconds] hits 0).
     */
    private val _sleepTimerChoice = MutableStateFlow<SleepTimerChoice>(SleepTimerChoice.Off)
    val sleepTimerChoice: StateFlow<SleepTimerChoice> = _sleepTimerChoice.asStateFlow()

    /** Apply a new timer choice. Cancels any active timer first. For
     *  [SleepTimerChoice.EndOfChapter] we resolve the duration against
     *  the current chapter when the screen knows position; for now we
     *  approximate by computing chapter-end relative to current position. */
    fun applySleepTimer(choice: SleepTimerChoice) {
        sleepTimerJob?.cancel()
        sleepTimerJob = null
        _sleepTimerChoice.value = choice

        val seconds = when (choice) {
            SleepTimerChoice.Off -> {
                _uiState.update { it.copy(sleepTimerMinutesLeft = null) }
                return
            }
            is SleepTimerChoice.Minutes -> choice.minutes * 60
            SleepTimerChoice.EndOfChapter -> {
                val state = _uiState.value
                val chapter = state.chapters.firstOrNull {
                    state.positionSeconds >= it.startSeconds && state.positionSeconds < it.endSeconds
                } ?: state.chapters.lastOrNull()
                val remaining = chapter?.let { it.endSeconds - state.positionSeconds }
                    ?.toInt()?.coerceAtLeast(60) ?: (15 * 60)
                remaining
            }
        }

        _uiState.update { it.copy(sleepTimerMinutesLeft = ((seconds + 59) / 60).coerceAtLeast(1)) }
        sleepTimerJob = viewModelScope.launch {
            var remaining = seconds
            while (remaining > 0) {
                delay(1000)
                remaining -= 1
                _uiState.update { it.copy(sleepTimerMinutesLeft = ((remaining + 59) / 60).coerceAtLeast(0).takeIf { v -> remaining > 0 }) }
            }
            // Fire: pause playback. The screen's LaunchedEffect on
            // isPaused will mirror this into the controller.
            _uiState.update { it.copy(isPaused = true, isPlaying = false, sleepTimerMinutesLeft = null) }
            _sleepTimerChoice.value = SleepTimerChoice.Off
        }
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
        val chapter = state.chapters.firstOrNull {
            state.positionSeconds >= it.startSeconds && state.positionSeconds < it.endSeconds
        } ?: state.chapters.lastOrNull()?.takeIf { state.positionSeconds >= it.startSeconds }

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

    /** Resume-on-open. Reads the local snapshot synchronously (on IO)
     *  and exposes it via [resumePositionSeconds] so the Compose host
     *  can issue a seekTo once the controller is ready + the metadata
     *  has resolved (or it may decide to ignore based on UX, e.g.
     *  prompt "Resume from 3:42:18?"). */
    private suspend fun loadResumePositionSnapshot(): Double? {
        val (serverId, profileId) = resolveScope()
        val snapshot = withContext(Dispatchers.IO) {
            positionStore.read(serverId, profileId, contentId)
        }
        val position = snapshot?.positionSeconds?.takeIf { it > 0 }
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
    }
}
