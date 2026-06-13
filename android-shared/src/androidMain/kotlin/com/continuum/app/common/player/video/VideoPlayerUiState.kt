package com.continuum.app.common.player.video

import com.continuum.app.model.playback.PlayMethod

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
        val streamUrl: String,
        val playMethod: PlayMethod,
        val title: String,
        val subtitle: String?,
        val artworkUrl: String?,
        val startPositionSeconds: Double,
    ) : VideoPlayerUiState {
        override val hasPlayableMedia: Boolean = true

        val startPositionMs: Long
            get() {
                val seconds = if (startPositionSeconds.isFinite()) startPositionSeconds else 0.0
                return (seconds * 1000.0).toLong().coerceAtLeast(0L)
            }
    }
}
