package com.continuum.app.common.player.video

class VideoPlaybackSessionCoordinator(
    private val starter: VideoPlaybackStarter,
) {
    suspend fun start(request: VideoPlaybackStartRequest): VideoPlayerUiState {
        return when (val result = starter.start(request)) {
            is VideoPlaybackStartResult.Ready -> VideoPlayerUiState.Ready(
                contentId = result.contentId,
                fileId = result.fileId,
                streamUrl = result.streamUrl,
                playMethod = result.playMethod,
                title = result.title,
                subtitle = result.subtitle,
                artworkUrl = result.artworkUrl,
                startPositionSeconds = result.startPositionSeconds,
            )
            is VideoPlaybackStartResult.Error -> VideoPlayerUiState.Error(
                contentId = result.contentId,
                message = result.message,
            )
        }
    }
}
