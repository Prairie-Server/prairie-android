package com.continuum.app.common.player.backend

import kotlinx.serialization.Serializable

@Serializable
enum class VideoPlaybackFormFactor {
    Unknown,
    Mobile,
    Tv,
}
