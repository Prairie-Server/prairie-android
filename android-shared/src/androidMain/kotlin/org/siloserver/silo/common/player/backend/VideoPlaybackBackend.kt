package org.siloserver.silo.common.player.backend

import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import org.siloserver.silo.common.player.VideoPlayerMediaSpec
import org.siloserver.silo.common.player.video.VideoPlayerTrackEntry
import org.siloserver.silo.model.playback.AudioPassthroughCapabilities
import org.siloserver.silo.model.playback.HdrCapabilities
import org.siloserver.silo.model.playback.PlayerSubtitleInfo

@UnstableApi
interface VideoPlaybackBackend {
    val kind: VideoPlaybackBackendKind
    val capabilities: VideoBackendCapabilities
    val player: Player

    fun mount(
        spec: VideoPlayerMediaSpec,
        startPositionMs: Long = spec.startPositionMs,
        playWhenReady: Boolean = true,
    )

    fun refresh(spec: VideoPlayerMediaSpec)

    fun selectSubtitle(track: VideoPlayerTrackEntry?): Boolean

    fun selectMountedSubtitle(
        subtitles: List<PlayerSubtitleInfo>,
        selectedIndex: Int,
    ): Boolean

    /**
     * Simultaneous second subtitle track (iOS parity). [selectedIndex] is an
     * index into [subtitles]; null turns the secondary off. Backends without
     * dual-track rendering report failure and the UI never offers the picker
     * (see [VideoBackendCapabilities.supportsSecondarySubtitles]).
     */
    fun selectSecondarySubtitle(
        subtitles: List<PlayerSubtitleInfo>,
        selectedIndex: Int?,
    ): Boolean = false

    fun selectAudioTrack(track: VideoPlayerTrackEntry)

    fun applyTrackSelection(
        audioCaps: AudioPassthroughCapabilities,
        displayHdr: HdrCapabilities = HdrCapabilities(),
        preferredAudioLanguage: String? = null,
        preferredTextLanguage: String? = null,
        hdrEnabled: Boolean = true,
    )

    fun release()
}
