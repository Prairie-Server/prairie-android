package org.prairieserver.prairie.common.player

import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.util.UnstableApi
import org.prairieserver.prairie.model.catalog.AudioTrack

/**
 * Manages audio track selection on the player.
 *
 * Widened from `ExoPlayer` to `Player` so callers that drive the player via a
 * `MediaController` (phone + TV since PR 5 — they talk to the service over
 * the session token, not a direct `ExoPlayer` handle) can still invoke track
 * selection. `Player.currentTracks` + `Player.trackSelectionParameters` are
 * the only surface this class needs.
 */
@UnstableApi
class AudioTrackManager {

    /**
     * Selects an audio track on the player by its index within the audio track groups.
     *
     * @param player The player instance (ExoPlayer or MediaController)
     * @param audioIndex The audio track index to select
     */
    fun selectAudioTrack(player: Player, audioIndex: Int) {
        val trackGroups = player.currentTracks.groups
        var audioGroupIndex = 0

        for (group in trackGroups) {
            if (group.type == C.TRACK_TYPE_AUDIO) {
                if (audioGroupIndex == audioIndex) {
                    player.trackSelectionParameters = player.trackSelectionParameters
                        .buildUpon()
                        .setOverrideForType(
                            TrackSelectionOverride(group.mediaTrackGroup, /* trackIndex = */ 0)
                        )
                        .build()
                    return
                }
                audioGroupIndex++
            }
        }
    }

    /**
     * Returns a display label for an audio track.
     * Prefers the title, falls back to language, then a generic index label.
     */
    fun getTrackLabel(track: AudioTrack, index: Int): String {
        val parts = mutableListOf<String>()

        track.title?.let { parts.add(it) }
        if (parts.isEmpty()) {
            track.language?.let { parts.add(it.uppercase()) }
        }

        track.channelLayout?.let { layout ->
            parts.add(layout)
        } ?: track.channels?.let { ch ->
            parts.add("${ch}ch")
        }

        track.codec?.uppercase()?.let { parts.add(it) }

        return if (parts.isEmpty()) "Audio ${index + 1}" else parts.joinToString(" - ")
    }
}
