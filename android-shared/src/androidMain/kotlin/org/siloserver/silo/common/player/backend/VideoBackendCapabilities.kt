package org.siloserver.silo.common.player.backend

import org.siloserver.silo.common.player.route.PlaybackRoute

data class VideoBackendCapabilities(
    val backendKind: VideoPlaybackBackendKind,
    val route: PlaybackRoute,
    val supportsSidecarSubtitles: Boolean,
    val supportsEmbeddedSubtitleSelection: Boolean,
    val supportsAudioTrackSelection: Boolean,
    val supportsBufferReporting: Boolean,
    val supportsSubtitleDelay: Boolean,
    val supportsAudioDelay: Boolean,
    val subtitleRendering: SubtitleRendering,
    val supportsHardContainers: Boolean,
    /** Simultaneous second subtitle track (iOS parity — supported on the
     *  native-render route only; Media3 has a single text renderer). */
    val supportsSecondarySubtitles: Boolean = false,
    val displayName: String,
) {
    companion object {
        fun media3(
            route: PlaybackRoute = PlaybackRoute.Compatibility,
        ): VideoBackendCapabilities = VideoBackendCapabilities(
            backendKind = VideoPlaybackBackendKind.Media3,
            route = route,
            supportsSidecarSubtitles = true,
            supportsEmbeddedSubtitleSelection = true,
            supportsAudioTrackSelection = true,
            supportsBufferReporting = true,
            supportsSubtitleDelay = true,
            supportsAudioDelay = true,
            subtitleRendering = SubtitleRendering.Media3Text,
            supportsHardContainers = false,
            displayName = "Media3",
        )

        fun mpv(
            route: PlaybackRoute = PlaybackRoute.Compatibility,
        ): VideoBackendCapabilities = VideoBackendCapabilities(
            backendKind = VideoPlaybackBackendKind.Mpv,
            route = route,
            supportsSidecarSubtitles = true,
            supportsEmbeddedSubtitleSelection = true,
            supportsAudioTrackSelection = true,
            supportsBufferReporting = true,
            supportsSubtitleDelay = true,
            supportsAudioDelay = false,
            subtitleRendering = SubtitleRendering.NativeBackend,
            supportsHardContainers = true,
            supportsSecondarySubtitles = true,
            displayName = "MPV",
        )
    }
}
