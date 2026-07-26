package org.prairieserver.prairie.repository

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.prairieserver.prairie.model.livetv.LiveTvChannel
import org.prairieserver.prairie.model.livetv.LiveTvChannelsResponse
import org.prairieserver.prairie.model.livetv.LiveTvGuideResponse
import org.prairieserver.prairie.model.livetv.LiveTvProgram
import org.prairieserver.prairie.model.livetv.LiveTvRecording
import org.prairieserver.prairie.model.livetv.LiveTvRecordingsResponse
import org.prairieserver.prairie.model.livetv.LiveTvScheduleRecordingRequest
import org.prairieserver.prairie.model.livetv.LiveTvSession
import org.prairieserver.prairie.model.livetv.LiveTvSessionStartResponse
import org.prairieserver.prairie.network.ApiResult
import org.prairieserver.prairie.network.api.LiveTvApi

class LiveTvRepository(private val api: LiveTvApi) {

    private val _channels = MutableStateFlow<List<LiveTvChannel>>(emptyList())
    val channels: StateFlow<List<LiveTvChannel>> = _channels.asStateFlow()

    suspend fun channels(tunerId: String? = null): ApiResult<LiveTvChannelsResponse> =
        when (val result = api.channels(tunerId)) {
            is ApiResult.Success -> {
                _channels.value = result.data.channels
                result
            }
            is ApiResult.Error, is ApiResult.NetworkError -> result
        }

    suspend fun guide(
        channelIds: List<String> = emptyList(),
        start: String? = null,
        end: String? = null,
    ): ApiResult<LiveTvGuideResponse> = api.guide(channelIds, start, end)

    suspend fun program(programId: String): ApiResult<LiveTvProgram> = api.program(programId)

    suspend fun startSession(channelId: String): ApiResult<LiveTvSessionStartResponse> =
        api.startSession(channelId)

    suspend fun releaseSession(sessionId: String): ApiResult<LiveTvSession> =
        api.releaseSession(sessionId)

    suspend fun recordings(status: String? = null): ApiResult<LiveTvRecordingsResponse> =
        api.recordings(status)

    suspend fun scheduleRecording(
        request: LiveTvScheduleRecordingRequest,
    ): ApiResult<LiveTvRecording> = api.scheduleRecording(request)

    suspend fun cancelRecording(recordingId: String): ApiResult<LiveTvRecording> =
        api.cancelRecording(recordingId)

    fun reset() {
        _channels.value = emptyList()
    }
}
