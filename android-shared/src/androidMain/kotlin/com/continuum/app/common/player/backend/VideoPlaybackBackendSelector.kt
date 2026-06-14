package com.continuum.app.common.player.backend

import com.continuum.app.model.playback.PlayMethod

object VideoPlaybackBackendSelector {
    fun select(request: VideoPlaybackBackendRequest): VideoPlaybackBackendKind =
        when (request.preference) {
            VideoPlaybackBackendPreference.Media3 -> VideoPlaybackBackendKind.Media3
            VideoPlaybackBackendPreference.Mpv -> VideoPlaybackBackendKind.Mpv
            VideoPlaybackBackendPreference.Auto -> when {
                request.playMethod == PlayMethod.TRANSCODE -> VideoPlaybackBackendKind.Media3
                request.hasHardContainer -> VideoPlaybackBackendKind.Mpv
                request.hasStyledSubtitles -> VideoPlaybackBackendKind.Mpv
                request.formFactor == VideoPlaybackFormFactor.Mobile -> VideoPlaybackBackendKind.Mpv
                request.formFactor == VideoPlaybackFormFactor.Tv -> VideoPlaybackBackendKind.Mpv
                else -> VideoPlaybackBackendKind.Media3
            }
        }
}
