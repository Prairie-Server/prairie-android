package org.prairieserver.prairie.common.player.backend

import androidx.annotation.OptIn
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import org.prairieserver.prairie.common.player.AudioTrackManager
import org.prairieserver.prairie.common.player.PrairiePlayerFactory
import org.prairieserver.prairie.common.player.SubtitleManager
import org.prairieserver.prairie.common.player.video.VideoTrackSelectionCoordinator

class VideoPlaybackBackendFactory(
    private val playerFactory: PrairiePlayerFactory,
    private val audioTrackManager: AudioTrackManager,
    private val subtitleManager: SubtitleManager,
) {
    @OptIn(UnstableApi::class)
    fun create(
        player: Player,
        request: VideoPlaybackBackendRequest = VideoPlaybackBackendRequest(),
    ): VideoPlaybackBackend {
        check(VideoPlaybackBackendSelector.select(request) == VideoPlaybackBackendKind.Media3)
        return Media3VideoPlaybackBackend(
            playerFactory = playerFactory,
            audioTrackManager = audioTrackManager,
            trackSelectionCoordinator = VideoTrackSelectionCoordinator(subtitleManager),
            player = player,
        )
    }
}
