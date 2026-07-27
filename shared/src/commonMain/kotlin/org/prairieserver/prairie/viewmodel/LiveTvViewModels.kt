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
import org.prairieserver.prairie.model.livetv.LiveTvRecording
import org.prairieserver.prairie.model.livetv.LiveTvScheduleRecordingRequest
import org.prairieserver.prairie.model.livetv.LiveTvSessionStartResponse
import org.prairieserver.prairie.network.ApiResult
import org.prairieserver.prairie.network.errorMessage
import org.prairieserver.prairie.repository.LiveTvRepository
import org.prairieserver.prairie.util.parseRfc3339ToEpochMillis

enum class LiveTvTab {
    Guide,
    Channels,
    Recordings,
}

data class LiveTvChannelRow(
    val channel: LiveTvChannel,
    val nowPlaying: LiveTvProgram? = null,
    val upNext: LiveTvProgram? = null,
)

data class LiveTvUiState(
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val selectedTab: LiveTvTab = LiveTvTab.Guide,
    val channels: List<LiveTvChannelRow> = emptyList(),
    val recordings: List<LiveTvRecording> = emptyList(),
    val channelQuery: String = "",
    val recordingMessage: String? = null,
    val error: String? = null,
) {
    val filteredChannels: List<LiveTvChannelRow>
        get() {
            val q = channelQuery.trim().lowercase()
            if (q.isEmpty()) return channels
            return channels.filter { row ->
                val ch = row.channel
                ch.displayName.lowercase().contains(q) ||
                    ch.displayNumber.lowercase().contains(q) ||
                    ch.callsign.lowercase().contains(q) ||
                    (row.nowPlaying?.title?.lowercase()?.contains(q) == true)
            }
        }

    val activeRecordings: List<LiveTvRecording>
        get() = recordings.filter { it.isActive }

    val historyRecordings: List<LiveTvRecording>
        get() = recordings.filter { !it.isActive }
}

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
            fetchAll()
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshing = true, error = null, recordingMessage = null) }
            fetchAll()
            _uiState.update { it.copy(isRefreshing = false) }
        }
    }

    fun selectTab(tab: LiveTvTab) {
        _uiState.update { it.copy(selectedTab = tab) }
        if (tab == LiveTvTab.Recordings) {
            viewModelScope.launch { fetchRecordings() }
        }
    }

    fun setChannelQuery(query: String) {
        _uiState.update { it.copy(channelQuery = query) }
    }

    private val schedulingProgramIds = mutableSetOf<String>()
    private val cancellingRecordingIds = mutableSetOf<String>()

    fun scheduleRecording(program: LiveTvProgram) {
        val programId = program.id.trim()
        if (programId.isEmpty() || !schedulingProgramIds.add(programId)) return
        val stopMs = parseRfc3339ToEpochMillis(program.stop)
        val nowMs = nowMillisProvider()
        if (stopMs != null && nowMs > 0L && stopMs <= nowMs) {
            schedulingProgramIds.remove(programId)
            _uiState.update { it.copy(recordingMessage = "Program already ended") }
            return
        }
        viewModelScope.launch {
            try {
                when (
                    val result = repository.scheduleRecording(
                        LiveTvScheduleRecordingRequest(programId = programId),
                    )
                ) {
                    is ApiResult.Success -> {
                        _uiState.update { it.copy(recordingMessage = "Recording scheduled") }
                        fetchRecordings()
                    }
                    is ApiResult.Error -> {
                        val message = when (result.code) {
                            403 -> "Not allowed to schedule recordings"
                            404 -> "Program not found"
                            409 -> "Recording already scheduled"
                            else -> "Could not schedule recording"
                        }
                        _uiState.update { it.copy(recordingMessage = message) }
                    }
                    is ApiResult.NetworkError -> {
                        _uiState.update {
                            it.copy(recordingMessage = "Could not schedule recording")
                        }
                    }
                }
            } finally {
                schedulingProgramIds.remove(programId)
            }
        }
    }

    fun cancelRecording(recording: LiveTvRecording) {
        val id = recording.id.trim()
        if (id.isEmpty() || !cancellingRecordingIds.add(id)) return
        viewModelScope.launch {
            try {
                when (val result = repository.cancelRecording(id)) {
                    is ApiResult.Success -> {
                        _uiState.update { it.copy(recordingMessage = "Recording cancelled") }
                        fetchRecordings()
                    }
                    is ApiResult.Error, is ApiResult.NetworkError -> {
                        _uiState.update {
                            it.copy(recordingMessage = "Could not cancel recording")
                        }
                    }
                }
            } finally {
                cancellingRecordingIds.remove(id)
            }
        }
    }

    fun clearRecordingMessage() {
        _uiState.update { it.copy(recordingMessage = null) }
    }

    private suspend fun fetchAll() {
        when (val result = repository.channels()) {
            is ApiResult.Success -> {
                val channels = result.data.channels.filter { it.enabled }
                val slots = loadNowNext(channels.map { it.id })
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        channels = channels.map { channel ->
                            val slot = slots[channel.id]
                            LiveTvChannelRow(
                                channel = channel,
                                nowPlaying = slot?.first,
                                upNext = slot?.second,
                            )
                        },
                        error = null,
                    )
                }
                fetchRecordings()
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

    private suspend fun fetchRecordings() {
        when (val result = repository.recordings()) {
            is ApiResult.Success -> {
                _uiState.update {
                    it.copy(
                        recordings = result.data.recordings.sortedByDescending { rec -> rec.start },
                    )
                }
            }
            is ApiResult.Error, is ApiResult.NetworkError -> {
                // Soft-fail: older servers / empty DVR should not blank the channel UI.
            }
        }
    }

    private suspend fun loadNowNext(
        channelIds: List<String>,
    ): Map<String, Pair<LiveTvProgram?, LiveTvProgram?>> {
        if (channelIds.isEmpty()) return emptyMap()
        return when (val guide = repository.guide(channelIds = channelIds)) {
            is ApiResult.Success -> {
                val now = nowMillisProvider()
                channelIds.associateWith { channelId ->
                    val sorted = guide.data.programs
                        .filter { it.channelId == channelId }
                        .sortedBy { it.start }
                    var current: LiveTvProgram? = null
                    var upcoming: LiveTvProgram? = null
                    for (program in sorted) {
                        val start = parseRfc3339ToEpochMillis(program.start) ?: continue
                        val stop = parseRfc3339ToEpochMillis(program.stop) ?: continue
                        if (now > 0L) {
                            if (now in start until stop) {
                                current = program
                                continue
                            }
                            if (start > now) {
                                upcoming = program
                                break
                            }
                        } else if (current == null) {
                            current = program
                        } else if (upcoming == null) {
                            upcoming = program
                            break
                        }
                    }
                    if (upcoming == null && current != null) {
                        val idx = sorted.indexOfFirst { it.id == current.id }
                        if (idx >= 0 && idx + 1 < sorted.size) {
                            upcoming = sorted[idx + 1]
                        }
                    }
                    current to upcoming
                }
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
 * Owns Live TV playback session lifecycle: POST session to obtain a playable
 * URL (HLS remux or MPEG-TS proxy), then DELETE when the player stops.
 * ExoPlayer wiring lives in the Android UI layer via PrairiePlayerFactory.
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
