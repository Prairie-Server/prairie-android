package com.continuum.app.common.player.backend

import androidx.annotation.OptIn
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import com.continuum.app.common.player.AudioTrackManager
import com.continuum.app.common.player.ContinuumPlayerFactory
import com.continuum.app.common.player.mpv.MpvPlayer
import com.continuum.app.common.player.SubtitleManager
import com.continuum.app.common.player.video.VideoTrackSelectionCoordinator

class VideoPlaybackBackendFactory(
    private val playerFactory: ContinuumPlayerFactory,
    private val audioTrackManager: AudioTrackManager,
    private val subtitleManager: SubtitleManager,
) {
    @OptIn(UnstableApi::class)
    fun create(
        player: Player,
        request: VideoPlaybackBackendRequest = VideoPlaybackBackendRequest(),
    ): VideoPlaybackBackend {
        val selected = VideoPlaybackBackendSelector.select(request)
        val actual = if (
            selected == VideoPlaybackBackendKind.Mpv &&
            player is MpvPlayer
        ) {
            VideoPlaybackBackendKind.Mpv
        } else {
            VideoPlaybackBackendKind.Media3
        }

        return when (actual) {
            VideoPlaybackBackendKind.Media3 -> Media3VideoPlaybackBackend(
                playerFactory = playerFactory,
                audioTrackManager = audioTrackManager,
                trackSelectionCoordinator = VideoTrackSelectionCoordinator(subtitleManager),
                player = player,
            )
            VideoPlaybackBackendKind.Mpv -> MpvVideoPlaybackBackend(
                playerFactory = playerFactory,
                audioTrackManager = audioTrackManager,
                trackSelectionCoordinator = VideoTrackSelectionCoordinator(subtitleManager),
                player = player,
            )
        }
    }
}
