package org.siloserver.silo.common.player.backend

import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import org.siloserver.silo.common.player.AudioTrackManager
import org.siloserver.silo.common.player.SiloPlayerFactory
import org.siloserver.silo.common.player.VideoPlayerMediaSpec
import org.siloserver.silo.common.player.mountVideoMedia
import org.siloserver.silo.common.player.mpv.MpvPlayer
import org.siloserver.silo.common.player.refreshMountedVideoMedia
import org.siloserver.silo.common.player.video.VideoPlayerTrackEntry
import org.siloserver.silo.common.player.video.VideoTrackSelectionCoordinator
import org.siloserver.silo.model.playback.AudioPassthroughCapabilities
import org.siloserver.silo.model.playback.HdrCapabilities
import org.siloserver.silo.model.playback.PlayerSubtitleInfo

@UnstableApi
class MpvVideoPlaybackBackend(
    private val playerFactory: SiloPlayerFactory,
    private val audioTrackManager: AudioTrackManager,
    private val trackSelectionCoordinator: VideoTrackSelectionCoordinator,
    override val player: Player,
) : VideoPlaybackBackend {
    override val kind: VideoPlaybackBackendKind = VideoPlaybackBackendKind.Mpv
    override val capabilities: VideoBackendCapabilities = VideoBackendCapabilities.mpv()

    private var mountedSpec: VideoPlayerMediaSpec? = null

    override fun mount(
        spec: VideoPlayerMediaSpec,
        startPositionMs: Long,
        playWhenReady: Boolean,
    ) {
        mountedSpec = spec
        (player as? MpvPlayer)?.setAudioPassthroughCodecs(spec.audioPassthroughCodecs)
        mountVideoMedia(
            player = player,
            playerFactory = playerFactory,
            spec = withoutEagerSubtitles(spec),
            startPositionMs = startPositionMs,
            playWhenReady = playWhenReady,
        )
    }

    override fun refresh(spec: VideoPlayerMediaSpec) {
        mountedSpec = spec
        (player as? MpvPlayer)?.setAudioPassthroughCodecs(spec.audioPassthroughCodecs)
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
        subtitles: List<PlayerSubtitleInfo>,
        selectedIndex: Int,
    ): Boolean = trackSelectionCoordinator.selectMountedSubtitle(
        player = player,
        subtitles = subtitles,
        selectedIndex = selectedIndex,
    )

    override fun selectSecondarySubtitle(
        subtitles: List<PlayerSubtitleInfo>,
        selectedIndex: Int?,
    ): Boolean {
        val mpvPlayer = player as? MpvPlayer ?: return false
        if (selectedIndex == null) {
            mpvPlayer.setSecondarySubtitleId(null)
            return true
        }
        val trackId = trackSelectionCoordinator
            .resolveSubtitleTrackId(player, subtitles, selectedIndex) ?: return false
        mpvPlayer.setSecondarySubtitleId(trackId)
        return true
    }

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
        player.release()
    }

    private fun requireMediaSpecForExternalSubtitle(track: VideoPlayerTrackEntry?): VideoPlayerMediaSpec {
        val spec = mountedSpec
        if (spec != null) return spec
        if (track?.subtitle == null) {
            return VideoPlayerMediaSpec(
                streamUrl = "",
                playMethod = org.siloserver.silo.model.playback.PlayMethod.DIRECT,
                serverUrl = "",
            )
        }
        error("Cannot select an external subtitle before video media has been mounted.")
    }

    private fun withoutEagerSubtitles(spec: VideoPlayerMediaSpec): VideoPlayerMediaSpec =
        if (spec.subtitles.isEmpty()) spec else spec.copy(subtitles = emptyList())
}
