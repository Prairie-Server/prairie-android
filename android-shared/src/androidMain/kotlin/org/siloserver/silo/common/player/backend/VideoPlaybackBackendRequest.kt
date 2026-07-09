package org.siloserver.silo.common.player.backend

import org.siloserver.silo.model.playback.PlayMethod
import org.siloserver.silo.model.playback.PlaybackDelivery
import org.siloserver.silo.model.playback.PlaybackEngineKind
import org.siloserver.silo.model.playback.PlaybackRouteFamily
import kotlinx.serialization.Serializable

@Serializable
data class VideoPlaybackBackendRequest(
    val contentId: String? = null,
    val fileId: Int? = null,
    val playMethod: PlayMethod? = null,
    val delivery: PlaybackDelivery? = null,
    val plannedEngine: PlaybackEngineKind? = null,
    val routeFamily: PlaybackRouteFamily? = null,
    val formFactor: VideoPlaybackFormFactor = VideoPlaybackFormFactor.Unknown,
    val preference: VideoPlaybackBackendPreference = VideoPlaybackBackendPreference.Auto,
    val hasHardContainer: Boolean = false,
    val hasStyledSubtitles: Boolean = false,
    // True when the selected file's video codec has no hardware decoder on
    // this device but MPV can software-decode it (Apple codec-tail parity).
    // Only meaningful for DIRECT playback; the device floor still wins.
    val hasSoftwareOnlyVideoCodec: Boolean = false,
    // True when the selected file version carries HDR video. The Media3
    // ("compatibility") route has no phone-side HDR track handling or
    // tone-mapping fallback, and HDR sources there can decode audio+text
    // while the video renderer produces no visible frames — so Auto routes
    // HDR to MPV (gpu-next tone-maps) on supported devices. Route/session
    // intent (Cast/DRM/HLS/transcode) still wins first.
    val hasHdrVideo: Boolean = false,
    // True when the resolved playback URL is an adaptive HLS playlist. Some
    // legacy sessions arrive without a v2 playbackPlan/delivery even though the
    // URL is a master.m3u8; route those to Media3 up front instead of trying MPV
    // and relying on failure recovery.
    val isAdaptiveHlsStream: Boolean = false,
    // Route/session intent — any of these forces Media3 under Auto, because Cast,
    // DRM, and external/secondary displays are paths where ExoPlayer is the
    // correct/only engine and MPV's direct rendering does not apply.
    val isCasting: Boolean = false,
    val isDrmProtected: Boolean = false,
    val isExternalDisplay: Boolean = false,
    // Device-class floor result (computed at the call site from Build.VERSION +
    // Build.SUPPORTED_ABIS via MpvDeviceFloor). Default true so pure/unit call
    // sites keep prior behavior; production call sites pass the real value.
    val mpvSupportedOnDevice: Boolean = true,
)

fun isLikelyAdaptiveHlsStreamUrl(streamUrl: String?): Boolean {
    val normalized = streamUrl
        ?.substringBefore('?')
        ?.substringBefore('#')
        ?.trim()
        ?.lowercase()
        ?: return false
    return normalized.endsWith(".m3u8") ||
        normalized.endsWith("/master.m3u8") ||
        normalized.contains(".m3u8/")
}
