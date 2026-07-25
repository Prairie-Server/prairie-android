package org.prairieserver.prairie.common.player.video

fun interface VideoPlaybackStarter {
    suspend fun start(request: VideoPlaybackStartRequest): VideoPlaybackStartResult
}
