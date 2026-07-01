package org.siloserver.silo.common.player.video

import org.siloserver.silo.model.catalog.TimeRange
import org.siloserver.silo.model.catalog.VersionChapter
import org.siloserver.silo.model.playback.PlayMethod
import org.siloserver.silo.model.playback.PlaybackDelivery
import org.siloserver.silo.model.playback.PlaybackExecutionPlan
import org.siloserver.silo.model.playback.PlayerSubtitleInfo

sealed interface VideoPlayerUiState {
    val contentId: String
    val hasPlayableMedia: Boolean

    data class Loading(
        override val contentId: String,
    ) : VideoPlayerUiState {
        override val hasPlayableMedia: Boolean = false
    }

    data class Error(
        override val contentId: String,
        val message: String,
    ) : VideoPlayerUiState {
        override val hasPlayableMedia: Boolean = false
    }

    data class Ready(
        override val contentId: String,
        val fileId: Int?,
        val fileResolution: String? = null,
        val streamUrl: String,
        val playMethod: PlayMethod,
        val playbackPlan: PlaybackExecutionPlan? = null,
        val delivery: PlaybackDelivery? = null,
        val container: String? = null,
        val title: String,
        val subtitle: String?,
        val artworkUrl: String?,
        val startPositionSeconds: Double,
        val sessionId: String? = null,
        val serverUrl: String = "",
        val accessToken: String = "",
        val mediaFileId: Int? = null,
        val audioTrackIndex: Int = 0,
        val durationSeconds: Double = 0.0,
        val subtitleUrls: List<PlayerSubtitleInfo> = emptyList(),
        val preferredAudioLanguage: String? = null,
        val preferredTextLanguage: String? = null,
        val preferredSubtitleMode: String? = null,
        val showForcedSubtitles: Boolean = true,
        val intro: TimeRange? = null,
        val credits: TimeRange? = null,
        val chapters: List<VersionChapter> = emptyList(),
        // Episode context for next-episode auto-advance (null for movies).
        val seriesId: String? = null,
        val seasonNumber: Int? = null,
        val episodeNumber: Int? = null,
    ) : VideoPlayerUiState {
        override val hasPlayableMedia: Boolean = true

        val startPositionMs: Long
            get() {
                val seconds = if (startPositionSeconds.isFinite()) startPositionSeconds else 0.0
                return (seconds * 1000.0).toLong().coerceAtLeast(0L)
            }
    }
}
