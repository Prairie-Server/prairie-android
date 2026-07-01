package org.siloserver.silo.common.player.backend

import kotlinx.serialization.Serializable

@Serializable
enum class VideoPlaybackFormFactor {
    Unknown,
    Mobile,
    Tv,
}
