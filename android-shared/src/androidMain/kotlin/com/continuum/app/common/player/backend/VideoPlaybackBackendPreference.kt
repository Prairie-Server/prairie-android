package com.continuum.app.common.player.backend

import kotlinx.serialization.Serializable

@Serializable
enum class VideoPlaybackBackendPreference {
    Auto,
    Media3,
    Mpv,
}
