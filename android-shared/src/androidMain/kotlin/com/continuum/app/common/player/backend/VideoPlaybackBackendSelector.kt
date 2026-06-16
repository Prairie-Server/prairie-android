package com.continuum.app.common.player.backend

import com.continuum.app.model.playback.PlayMethod

object VideoPlaybackBackendSelector {
    fun select(request: VideoPlaybackBackendRequest): VideoPlaybackBackendKind =
        when (request.preference) {
            VideoPlaybackBackendPreference.Media3 -> VideoPlaybackBackendKind.Media3
            VideoPlaybackBackendPreference.Mpv -> VideoPlaybackBackendKind.Mpv
            VideoPlaybackBackendPreference.Auto -> when {
                // Route/session intent: ExoPlayer is the correct engine here.
                request.isCasting -> VideoPlaybackBackendKind.Media3
                request.isDrmProtected -> VideoPlaybackBackendKind.Media3
                request.isExternalDisplay -> VideoPlaybackBackendKind.Media3
                request.playMethod == PlayMethod.TRANSCODE -> VideoPlaybackBackendKind.Media3
                // Fidelity: MPV for hard containers / styled subtitles.
                request.hasHardContainer -> VideoPlaybackBackendKind.Mpv
                request.hasStyledSubtitles -> VideoPlaybackBackendKind.Mpv
                else -> VideoPlaybackBackendKind.Media3
            }
        }
}
