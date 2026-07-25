package org.prairieserver.prairie.common.player.video

import android.os.SystemClock
import org.prairieserver.prairie.common.diagnostics.DiagnosticsPlaybackLogger

class VideoPlaybackSessionCoordinator(
    private val starter: VideoPlaybackStarter,
) {
    suspend fun start(request: VideoPlaybackStartRequest): VideoPlayerUiState {
        DiagnosticsPlaybackLogger.sessionEvent("video start requested")
        val startedAtMs = SystemClock.elapsedRealtime()
        return when (val result = starter.start(request)) {
            is VideoPlaybackStartResult.Ready -> {
                DiagnosticsPlaybackLogger.startReady(SystemClock.elapsedRealtime() - startedAtMs)
                VideoPlayerUiState.Ready(
                    contentId = result.contentId,
                    fileId = result.fileId,
                    versions = result.versions,
                    fileResolution = result.fileResolution,
                    streamUrl = result.streamUrl,
                    playMethod = result.playMethod,
                    playbackPlan = result.playbackPlan,
                    playbackPlanV3 = result.playbackPlanV3,
                    requestHeaders = result.requestHeaders,
                    delivery = result.delivery,
                    container = result.container,
                    title = result.title,
                    subtitle = result.subtitle,
                    artworkUrl = result.artworkUrl,
                    startPositionSeconds = result.startPositionSeconds,
                    sourceStartPositionSeconds = result.sourceStartPositionSeconds,
                    sessionId = result.sessionId,
                    serverUrl = result.serverUrl,
                    accessToken = result.accessToken,
                    mediaFileId = result.mediaFileId,
                    audioTrackIndex = result.audioTrackIndex,
                    durationSeconds = result.durationSeconds,
                    subtitleUrls = result.subtitleUrls,
                    preferredAudioLanguage = result.preferredAudioLanguage,
                    preferredTextLanguage = result.preferredTextLanguage,
                    preferredSubtitleMode = result.preferredSubtitleMode,
                    showForcedSubtitles = result.showForcedSubtitles,
                    intro = result.intro,
                    credits = result.credits,
                    chapters = result.chapters,
                    seriesId = result.seriesId,
                    seasonNumber = result.seasonNumber,
                    episodeNumber = result.episodeNumber,
                )
            }
            is VideoPlaybackStartResult.Error -> {
                DiagnosticsPlaybackLogger.startFailure(result.diagnosticsCode)
                VideoPlayerUiState.Error(
                    contentId = result.contentId,
                    message = result.message,
                )
            }
            is VideoPlaybackStartResult.ServerUnreachable -> {
                DiagnosticsPlaybackLogger.sessionEvent("video server unreachable")
                VideoPlayerUiState.ServerUnreachable(
                    contentId = result.contentId,
                )
            }
        }
    }
}
