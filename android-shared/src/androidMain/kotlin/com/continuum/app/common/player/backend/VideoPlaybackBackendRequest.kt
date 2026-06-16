package com.continuum.app.common.player.backend

import com.continuum.app.model.playback.PlayMethod
import kotlinx.serialization.Serializable

@Serializable
data class VideoPlaybackBackendRequest(
    val contentId: String? = null,
    val fileId: Int? = null,
    val playMethod: PlayMethod? = null,
    val formFactor: VideoPlaybackFormFactor = VideoPlaybackFormFactor.Unknown,
    val preference: VideoPlaybackBackendPreference = VideoPlaybackBackendPreference.Auto,
    val hasHardContainer: Boolean = false,
    val hasStyledSubtitles: Boolean = false,
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
