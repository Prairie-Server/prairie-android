package com.continuum.app.common.player.backend

import com.continuum.app.model.playback.PlayMethod

data class VideoPlaybackBackendRequest(
    val contentId: String? = null,
    val fileId: Int? = null,
    val playMethod: PlayMethod? = null,
    val formFactor: VideoPlaybackFormFactor = VideoPlaybackFormFactor.Unknown,
    val preference: VideoPlaybackBackendPreference = VideoPlaybackBackendPreference.Auto,
)
