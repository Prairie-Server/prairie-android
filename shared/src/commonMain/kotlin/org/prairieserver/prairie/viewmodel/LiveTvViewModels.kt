package org.prairieserver.prairie.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.prairieserver.prairie.model.livetv.LiveTvChannel
import org.prairieserver.prairie.model.livetv.LiveTvProgram
import org.prairieserver.prairie.model.livetv.LiveTvScheduleRecordingRequest
import org.prairieserver.prairie.model.livetv.LiveTvSessionStartResponse
import org.prairieserver.prairie.network.ApiResult
import org.prairieserver.prairie.network.errorMessage
import org.prairieserver.prairie.repository.LiveTvRepository
import org.prairieserver.prairie.util.parseRfc3339ToEpochMillis

data class LiveTvChannelRow(
    val channel: LiveTvChannel,
    val nowPlaying: LiveTvProgram? = null,
)

data class LiveTvUiState(
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val channels: List<LiveTvChannelRow> = emptyList(),
    val recordingMessage: String? = null,
    val error: String? = null,
)

class LiveTvViewModel(
    private val repository: LiveTvRepository,
    private val nowMillisProvider: () -> Long = { 0L },
) : ViewModel() {

    private val _uiState = MutableStateFlow(LiveTvUiState())
    val uiState: StateFlow<LiveTvUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            fetchChannels()
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshing = true, error = null, recordingMessage = null) }
            fetchChannels()
            _uiState.update { it.copy(isRefreshing = false) }
        }
    }

    fun scheduleRecording(program: LiveTvProgram) {
        if (program.id.isBlank()) return
        viewModelScope.launch {
            when (
                val result = repository.scheduleRecording(
                    LiveTvScheduleRecordingRequest(
                        programId = program.id,
                        channelId = program.channelId.takeIf { it.isNotBlank() },
                        start = program.start.takeIf { it.isNotBlank() },
                        stop = program.stop.takeIf { it.isNotBlank() },
                        title = program.title.takeIf { it.isNotBlank() },
                    ),
                )
            ) {
                is ApiResult.Success -> {
                    _uiState.update { it.copy(recordingMessage = "Recording scheduled") }
                }
                is ApiResult.Error, is ApiResult.NetworkError -> {
                    _uiState.update {
                        it.copy(
                            recordingMessage = result.errorMessage("Failed to schedule recording"),
                        )
                    }
                }
            }
        }
    }

    fun clearRecordingMessage() {
        _uiState.update { it.copy(recordingMessage = null) }
    }

    private suspend fun fetchChannels() {
        when (val result = repository.channels()) {
            is ApiResult.Success -> {
                val channels = result.data.channels.filter { it.enabled }
                val nowByChannel = loadNowPlaying(channels.map { it.id })
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        channels = channels.map { channel ->
                            LiveTvChannelRow(channel, nowByChannel[channel.id])
                        },
                        error = null,
                    )
                }
            }
            is ApiResult.Error, is ApiResult.NetworkError -> {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = result.errorMessage("Failed to load Live TV channels"),
                    )
                }
            }
        }
    }

    private suspend fun loadNowPlaying(channelIds: List<String>): Map<String, LiveTvProgram> {
        if (channelIds.isEmpty()) return emptyMap()
        return when (val guide = repository.guide(channelIds = channelIds)) {
            is ApiResult.Success -> {
                val now = nowMillisProvider()
                guide.data.programs
                    .filter { program ->
                        if (now <= 0L) return@filter true
                        val start = parseRfc3339ToEpochMillis(program.start) ?: return@filter false
                        val stop = parseRfc3339ToEpochMillis(program.stop) ?: return@filter false
                        now in start until stop
                    }
                    .groupBy { it.channelId }
                    .mapValues { (_, programs) -> programs.maxBy { it.start } }
            }
            is ApiResult.Error, is ApiResult.NetworkError -> emptyMap()
        }
    }
}

data class LiveTvPlayerUiState(
    val channelId: String = "",
    val channelName: String = "",
    val isStarting: Boolean = false,
    val session: LiveTvSessionStartResponse? = null,
    val error: String? = null,
)

/**
 * Owns Live TV playback session lifecycle: POST session to obtain an HLS URL,
 * then DELETE the session when the player stops. ExoPlayer wiring lives in the
 * Android UI layer via [org.prairieserver.prairie.common.player.PrairiePlayerFactory].
 */
class LiveTvPlayerViewModel(
    private val repository: LiveTvRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(LiveTvPlayerUiState())
    val uiState: StateFlow<LiveTvPlayerUiState> = _uiState.asStateFlow()

    fun start(channelId: String, channelName: String = "") {
        if (channelId.isBlank()) {
            _uiState.update {
                it.copy(error = "Missing channel", isStarting = false, session = null)
            }
            return
        }
        viewModelScope.launch {
            val previousSessionId = _uiState.value.session?.sessionId
            if (!previousSessionId.isNullOrBlank()) {
                repository.releaseSession(previousSessionId)
            }
            _uiState.update {
                it.copy(
                    channelId = channelId,
                    channelName = channelName,
                    isStarting = true,
                    session = null,
                    error = null,
                )
            }
            when (val result = repository.startSession(channelId)) {
                is ApiResult.Success -> {
                    val url = result.data.playableUrl
                    if (url.isBlank()) {
                        repository.releaseSession(result.data.sessionId)
                        _uiState.update {
                            it.copy(
                                isStarting = false,
                                session = null,
                                error = "Live TV session returned no stream URL",
                            )
                        }
                    } else {
                        _uiState.update {
                            it.copy(isStarting = false, session = result.data, error = null)
                        }
                    }
                }
                is ApiResult.Error, is ApiResult.NetworkError -> {
                    _uiState.update {
                        it.copy(
                            isStarting = false,
                            session = null,
                            error = result.errorMessage("Failed to start Live TV"),
                        )
                    }
                }
            }
        }
    }

    fun stop() {
        val sessionId = _uiState.value.session?.sessionId
        _uiState.update { it.copy(session = null, isStarting = false) }
        if (sessionId.isNullOrBlank()) return
        viewModelScope.launch {
            repository.releaseSession(sessionId)
        }
    }

    override fun onCleared() {
        val sessionId = _uiState.value.session?.sessionId
        if (!sessionId.isNullOrBlank()) {
            // Best-effort: viewModelScope is cancelled in onCleared, so fire a
            // detached release via a blocking-friendly path isn't available in
            // commonMain. Callers must also invoke [stop] from DisposableEffect.
            _uiState.update { it.copy(session = null) }
        }
        super.onCleared()
    }
}
