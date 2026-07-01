package org.siloserver.silo.network.api

import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.http.*
import org.siloserver.silo.model.playback.ChangeAudioResponse
import org.siloserver.silo.model.playback.PlaybackPlanResponse
import org.siloserver.silo.model.playback.PlaybackRouteEventRequest
import org.siloserver.silo.model.playback.PlaybackSessionResponse
import org.siloserver.silo.model.playback.ProgressRequest
import org.siloserver.silo.model.playback.StartPlaybackRequest
import org.siloserver.silo.model.playback.TranscodeStartRequest
import org.siloserver.silo.model.playback.TranscodeStartResponse
import org.siloserver.silo.network.ApiResult
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

class PlaybackApi(private val client: HttpClient) {

    suspend fun startPlayback(request: StartPlaybackRequest): ApiResult<PlaybackSessionResponse> = safeApiCall {
        client.post("/api/v1/playback/start") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }
    }

    suspend fun decidePlayback(request: StartPlaybackRequest): ApiResult<PlaybackPlanResponse> = safeApiCall {
        client.post("/api/v1/playback/decide") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }
    }

    suspend fun updateProgress(
        sessionId: String,
        request: ProgressRequest
    ): ApiResult<Unit> = safeApiCall {
        client.post("/api/v1/playback/$sessionId/progress") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }
    }

    suspend fun stopPlayback(sessionId: String): ApiResult<Unit> = safeApiCall {
        client.delete("/api/v1/playback/$sessionId")
    }

    suspend fun startTranscode(request: TranscodeStartRequest): ApiResult<TranscodeStartResponse> = safeApiCall {
        client.post("/api/v1/playback/transcode/start") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }
    }

    suspend fun changeAudio(
        sessionId: String,
        audioTrackIndex: Int,
        position: Double? = null,
    ): ApiResult<ChangeAudioResponse> = safeApiCall {
        client.patch("/api/v1/playback/$sessionId/audio") {
            contentType(ContentType.Application.Json)
            setBody(ChangeAudioRequest(audioTrackIndex = audioTrackIndex, position = position))
        }
    }

    suspend fun reportRouteEvent(
        sessionId: String,
        request: PlaybackRouteEventRequest,
    ): ApiResult<Unit> = safeApiCall {
        client.post("/api/v1/playback/$sessionId/route-events") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }
    }
}

@Serializable
internal data class ChangeAudioRequest(
    @SerialName("audio_track_index")
    val audioTrackIndex: Int,
    @SerialName("position")
    val position: Double? = null,
)
