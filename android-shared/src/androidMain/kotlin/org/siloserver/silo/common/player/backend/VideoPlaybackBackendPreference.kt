package org.siloserver.silo.common.player.backend

import kotlinx.serialization.Serializable

@Serializable
enum class VideoPlaybackBackendPreference {
    Auto,
    Media3,
    Mpv,
}
