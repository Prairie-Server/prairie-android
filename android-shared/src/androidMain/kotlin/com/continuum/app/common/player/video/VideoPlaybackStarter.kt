package com.continuum.app.common.player.video

fun interface VideoPlaybackStarter {
    suspend fun start(request: VideoPlaybackStartRequest): VideoPlaybackStartResult
}
