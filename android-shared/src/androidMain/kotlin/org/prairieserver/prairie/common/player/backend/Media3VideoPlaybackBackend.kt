package org.prairieserver.prairie.common.player.backend

import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import org.prairieserver.prairie.common.player.AudioTrackManager
import org.prairieserver.prairie.common.player.PrairiePlayerFactory
import org.prairieserver.prairie.common.player.VideoPlayerMediaSpec
import org.prairieserver.prairie.common.player.mountVideoMedia
import org.prairieserver.prairie.common.player.refreshMountedVideoMedia
import org.prairieserver.prairie.common.player.video.VideoPlayerTrackEntry
import org.prairieserver.prairie.common.player.video.VideoTrackSelectionCoordinator
import org.prairieserver.prairie.model.playback.AudioPassthroughCapabilities
import org.prairieserver.prairie.model.playback.HdrCapabilities
import org.prairieserver.prairie.model.playback.PlayerSubtitleInfo
import org.prairieserver.prairie.model.playback.SubtitleIdentity

@UnstableApi
class Media3VideoPlaybackBackend(
    private val playerFactory: PrairiePlayerFactory,
    private val audioTrackManager: AudioTrackManager,
    private val trackSelectionCoordinator: VideoTrackSelectionCoordinator,
    override val player: Player,
) : VideoPlaybackBackend {
    override val kind: VideoPlaybackBackendKind = VideoPlaybackBackendKind.Media3
    override val capabilities: VideoBackendCapabilities = VideoBackendCapabilities.media3()

    private var mountedSpec: VideoPlayerMediaSpec? = null

    override fun mount(
        spec: VideoPlayerMediaSpec,
        startPositionMs: Long,
        playWhenReady: Boolean,
    ) {
        mountedSpec = spec
        mountVideoMedia(
            player = player,
            playerFactory = playerFactory,
            spec = spec,
            startPositionMs = startPositionMs,
            playWhenReady = playWhenReady,
        )
    }

    override fun refresh(spec: VideoPlayerMediaSpec) {
        mountedSpec = spec
        refreshMountedVideoMedia(
            player = player,
            playerFactory = playerFactory,
            spec = spec,
        )
    }

    override fun selectSubtitle(track: VideoPlayerTrackEntry?): Boolean =
        trackSelectionCoordinator.selectSubtitle(
            player = player,
            playerFactory = playerFactory,
            mediaSpec = requireMediaSpecForExternalSubtitle(track),
            selectedTrack = track,
        )

    override fun selectMountedSubtitle(
        identity: SubtitleIdentity,
    ): Boolean = trackSelectionCoordinator.selectMountedSubtitle(
        player = player,
        identity = identity,
    )

    override fun selectMountedSubtitle(
        subtitles: List<PlayerSubtitleInfo>,
        selectedIndex: Int,
    ): Boolean = trackSelectionCoordinator.selectMountedSubtitle(
        player = player,
        subtitles = subtitles,
        selectedIndex = selectedIndex,
    )

    override fun selectAudioTrack(track: VideoPlayerTrackEntry) {
        trackSelectionCoordinator.selectAudioTrack(
            player = player,
            audioTrackManager = audioTrackManager,
            selectedTrack = track,
        )
    }

    override fun applyTrackSelection(
        audioCaps: AudioPassthroughCapabilities,
        displayHdr: HdrCapabilities,
        preferredAudioLanguage: String?,
        preferredTextLanguage: String?,
        hdrEnabled: Boolean,
    ) {
        playerFactory.applyTrackSelectionPresets(
            player = player,
            audioCaps = audioCaps,
            displayHdr = displayHdr,
            preferredAudioLanguage = preferredAudioLanguage,
            preferredTextLanguage = preferredTextLanguage,
            hdrEnabled = hdrEnabled,
        )
    }

    override fun release() {
        playerFactory.releasePlayer(player)
    }

    private fun requireMediaSpecForExternalSubtitle(track: VideoPlayerTrackEntry?): VideoPlayerMediaSpec {
        val spec = mountedSpec
        if (spec != null) return spec
        if (track?.subtitle == null) {
            return VideoPlayerMediaSpec(
                streamUrl = "",
                playMethod = org.prairieserver.prairie.model.playback.PlayMethod.DIRECT,
                serverUrl = "",
            )
        }
        error("Cannot select an external subtitle before video media has been mounted.")
    }
}
