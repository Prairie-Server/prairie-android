package com.continuum.app.common.player.video

import androidx.annotation.OptIn
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import com.continuum.app.common.player.AudioTrackManager
import com.continuum.app.common.player.ContinuumPlayerFactory
import com.continuum.app.common.player.SubtitleManager
import com.continuum.app.common.player.VideoPlayerMediaSpec
import com.continuum.app.common.player.refreshMountedVideoMedia
import com.continuum.app.model.playback.PlayerSubtitleInfo

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
        playerFactory: ContinuumPlayerFactory,
        mediaSpec: VideoPlayerMediaSpec,
        selectedTrack: VideoPlayerTrackEntry?,
    ): Boolean {
        if (selectedTrack == null) {
            return subtitleManager.selectSubtitle(player, -1)
        }

        val subtitle = selectedTrack.subtitle
        if (subtitle != null) {
            refreshMountedVideoMedia(
                player = player,
                playerFactory = playerFactory,
                spec = mediaSpec.copy(subtitles = listOf(subtitle)),
            )
            return subtitleManager.selectSubtitle(player, listOf(subtitle), 0)
        }

        return subtitleManager.selectSubtitle(player, selectedTrack.index)
    }

    fun selectMountedSubtitle(
        player: Player,
        subtitles: List<PlayerSubtitleInfo>,
        selectedIndex: Int,
    ): Boolean = subtitleManager.selectSubtitle(player, subtitles, selectedIndex)

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
