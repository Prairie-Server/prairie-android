package org.siloserver.silo.common.player.backend

import org.siloserver.silo.model.playback.PlayMethod
import org.siloserver.silo.model.playback.PlaybackDelivery
import org.siloserver.silo.model.playback.PlaybackEngineKind

object VideoPlaybackBackendSelector {
    fun select(request: VideoPlaybackBackendRequest): VideoPlaybackBackendKind =
        when {
            // Device floor is absolute: API 24/25 must not select or instantiate MPV.
            !request.mpvSupportedOnDevice -> VideoPlaybackBackendKind.Media3
            request.preference == VideoPlaybackBackendPreference.Media3 -> VideoPlaybackBackendKind.Media3
            request.preference == VideoPlaybackBackendPreference.Mpv -> VideoPlaybackBackendKind.Mpv
            else -> when {
                // Route/session intent: ExoPlayer is the correct engine here.
                request.isCasting -> VideoPlaybackBackendKind.Media3
                request.isDrmProtected -> VideoPlaybackBackendKind.Media3
                request.isExternalDisplay -> VideoPlaybackBackendKind.Media3
                request.isAdaptiveHlsStream -> VideoPlaybackBackendKind.Media3
                request.delivery == PlaybackDelivery.SERVER_REMUX_HLS -> VideoPlaybackBackendKind.Media3
                request.delivery == PlaybackDelivery.SERVER_TRANSCODE_HLS -> VideoPlaybackBackendKind.Media3
                request.plannedEngine == PlaybackEngineKind.MEDIA3_DIRECT -> VideoPlaybackBackendKind.Media3
                request.plannedEngine == PlaybackEngineKind.MEDIA3_PROGRESSIVE_REMUX -> VideoPlaybackBackendKind.Media3
                request.plannedEngine == PlaybackEngineKind.MEDIA3_HLS -> VideoPlaybackBackendKind.Media3
                request.plannedEngine == PlaybackEngineKind.MPV_DIRECT -> VideoPlaybackBackendKind.Mpv
                request.delivery == PlaybackDelivery.SERVER_REMUX_PROGRESSIVE -> VideoPlaybackBackendKind.Mpv
                request.playMethod == PlayMethod.TRANSCODE -> VideoPlaybackBackendKind.Media3
                // Fidelity: MPV for hard containers / styled subtitles on supported devices.
                request.hasHardContainer -> VideoPlaybackBackendKind.Mpv
                request.hasStyledSubtitles -> VideoPlaybackBackendKind.Mpv
                // Codec floor: no hardware decoder for this codec, but MPV can
                // software-decode it (Apple codec-tail parity) — without this
                // the file would have been transcoded server-side.
                request.hasSoftwareOnlyVideoCodec -> VideoPlaybackBackendKind.Mpv
                // HDR fidelity: the Media3 route has no phone-side HDR track
                // handling or tone-mapping fallback and can end up decoding
                // audio+subtitles with a permanently black video surface —
                // route HDR sources to MPV, which tone-maps via gpu-next.
                request.hasHdrVideo -> VideoPlaybackBackendKind.Mpv
                else -> VideoPlaybackBackendKind.Media3
            }
        }
}
