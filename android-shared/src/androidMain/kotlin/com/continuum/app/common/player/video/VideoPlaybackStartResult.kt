package com.continuum.app.common.player.video

import com.continuum.app.model.playback.PlayMethod

sealed interface VideoPlaybackStartResult {
    data class Ready(
        val contentId: String,
        val fileId: Int?,
        val streamUrl: String,
        val playMethod: PlayMethod,
        val title: String,
        val subtitle: String?,
        val artworkUrl: String?,
        val startPositionSeconds: Double,
    ) : VideoPlaybackStartResult

    data class Error(
        val contentId: String,
        val message: String,
        val cause: Throwable? = null,
    ) : VideoPlaybackStartResult
}
