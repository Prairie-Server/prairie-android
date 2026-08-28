package org.prairieserver.prairie.common.player.backend

import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import org.prairieserver.prairie.common.player.VideoPlayerMediaSpec
import org.prairieserver.prairie.common.player.video.VideoPlayerTrackEntry
import org.prairieserver.prairie.model.playback.AudioPassthroughCapabilities
import org.prairieserver.prairie.model.playback.HdrCapabilities
import org.prairieserver.prairie.model.playback.PlayerSubtitleInfo
import org.prairieserver.prairie.model.playback.SubtitleIdentity

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
        identity: SubtitleIdentity,
    ): Boolean

    /** Compatibility bridge until every platform adapter publishes typed identity. */
    fun selectMountedSubtitle(
        subtitles: List<PlayerSubtitleInfo>,
        selectedIndex: Int,
    ): Boolean

    fun selectAudioTrack(track: VideoPlayerTrackEntry)

    /** Returns whether presets were actually assigned; false = skipped. */
    fun applyTrackSelection(
        audioCaps: AudioPassthroughCapabilities,
        displayHdr: HdrCapabilities = HdrCapabilities(),
        preferredAudioLanguage: String? = null,
        preferredTextLanguage: String? = null,
        hdrEnabled: Boolean = true,
    ): Boolean

    fun release()
}
