package org.siloserver.silo.common.player.video

/**
 * Video codecs MPV's FFmpeg build software-decodes reliably — the Android
 * analog of silo-apple's CompatibilityPlayer "codec tail" (AV1 via dav1d,
 * VP9/VP8, MPEG-4 Part 2 / DivX / Xvid, MPEG-2, VC-1/WMV9).
 *
 * These are advertised to the server (in addition to the hardware-probed
 * codecs) only when the MPV device floor is met, so files carrying them
 * DIRECT to MPV instead of transcoding. h264/hevc are deliberately absent:
 * they are always hardware-probed, and claiming them here would bypass the
 * DV/HDR capability checks.
 */
val mpvSoftwareVideoCodecs: List<String> =
    listOf("av1", "vp9", "vp8", "mpeg4", "mpeg2video", "vc1", "wmv3")

fun isMpvSoftwareDecodableVideoCodec(codec: String?): Boolean =
    codec?.trim()?.lowercase()?.takeIf { it.isNotBlank() } in mpvSoftwareVideoCodecs
