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
                // Dolby Vision must outrank a planned Media3 engine: the plan
                // is computed without phone-side DV knowledge, and Media3 has
                // no DV handling — direct-play DV on that path can decode
                // audio+subtitles over a permanently black surface. Plain
                // HDR10/HDR10+/HLG stays on Media3, which handles it fine
                // (verified by A/B on device). Transcoded output is exempt
                // (server already resolved HDR); MPV handles DV via gpu-next.
                request.hasDolbyVisionVideo && request.playMethod != PlayMethod.TRANSCODE -> VideoPlaybackBackendKind.Mpv
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
                else -> VideoPlaybackBackendKind.Media3
            }
        }
}
