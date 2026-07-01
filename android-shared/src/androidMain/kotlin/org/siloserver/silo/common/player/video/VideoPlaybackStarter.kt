package org.siloserver.silo.common.player.video

fun interface VideoPlaybackStarter {
    suspend fun start(request: VideoPlaybackStartRequest): VideoPlaybackStartResult
}
