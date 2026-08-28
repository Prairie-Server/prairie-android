package org.prairieserver.prairie.common.player.video

import androidx.annotation.OptIn
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import org.prairieserver.prairie.common.player.AudioTrackManager
import org.prairieserver.prairie.common.player.PrairiePlayerFactory
import org.prairieserver.prairie.common.player.SubtitleManager
import org.prairieserver.prairie.common.player.VideoPlayerMediaSpec
import org.prairieserver.prairie.common.player.refreshMountedVideoMedia
import org.prairieserver.prairie.model.playback.PlayerSubtitleInfo
import org.prairieserver.prairie.model.playback.SubtitleIdentity

data class VideoPlayerTrackEntry(
    val index: Int,
    val label: String,
    val language: String?,
    val isSelected: Boolean,
    val subtitle: PlayerSubtitleInfo? = null,
)

@OptIn(UnstableApi::class)
class VideoTrackSelectionCoordinator(
    private val subtitleManager: SubtitleManager = SubtitleManager(),
) {
    fun selectSubtitle(
        player: Player,
        playerFactory: PrairiePlayerFactory,
        mediaSpec: VideoPlayerMediaSpec?,
        selectedTrack: VideoPlayerTrackEntry?,
    ): Boolean {
        if (selectedTrack == null) {
            return subtitleManager.selectSubtitle(player, -1)
        }

        val subtitle = selectedTrack.subtitle
        if (subtitle != null) {
            val mountedMediaSpec = mediaSpec ?: return false
            refreshMountedVideoMedia(
                player = player,
                playerFactory = playerFactory,
                spec = mountedMediaSpec.copy(subtitles = listOf(subtitle)),
            )
            return subtitleManager.selectSubtitle(player, listOf(subtitle), 0)
        }

        return subtitleManager.selectSubtitle(player, selectedTrack.index)
    }

    fun selectMountedSubtitle(
        player: Player,
        identity: SubtitleIdentity,
    ): Boolean = subtitleManager.selectSubtitle(player, identity)

    /** Compatibility bridge for adapters not yet migrated to typed identity. */
    fun selectMountedSubtitle(
        player: Player,
        subtitles: List<PlayerSubtitleInfo>,
        selectedIndex: Int,
    ): Boolean = subtitleManager.selectSubtitle(player, subtitles, selectedIndex)

    fun resolveSubtitleTrackId(
        player: Player,
        subtitles: List<PlayerSubtitleInfo>,
        subtitleIndex: Int,
    ): String? = subtitleManager.resolveSubtitleTrackId(player, subtitles, subtitleIndex)

    fun selectAudioTrack(
        player: Player,
        audioTrackManager: AudioTrackManager,
        selectedTrack: VideoPlayerTrackEntry,
    ) {
        audioTrackManager.selectAudioTrack(player, selectedTrack.index)
    }

    fun describeSubtitle(
        track: VideoPlayerTrackEntry,
        isAiGenerated: Boolean = false,
        isEnhanced: Boolean = false,
    ): String {
        val parts = mutableListOf(track.label.ifBlank { track.language ?: "Subtitle ${track.index + 1}" })
        if (isAiGenerated) parts += "AI"
        if (isEnhanced) parts += "Enhanced"
        return parts.joinToString(" - ")
    }
}
