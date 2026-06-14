package com.continuum.app.common.player.backend

import androidx.annotation.OptIn
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import com.continuum.app.common.player.AudioTrackManager
import com.continuum.app.common.player.ContinuumPlayerFactory
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
    ): VideoPlaybackBackend = when (request.preference) {
        VideoPlaybackBackendPreference.Auto,
        VideoPlaybackBackendPreference.Media3,
        VideoPlaybackBackendPreference.Mpv,
        -> Media3VideoPlaybackBackend(
            playerFactory = playerFactory,
            audioTrackManager = audioTrackManager,
            trackSelectionCoordinator = VideoTrackSelectionCoordinator(subtitleManager),
            player = player,
        )
    }
}
