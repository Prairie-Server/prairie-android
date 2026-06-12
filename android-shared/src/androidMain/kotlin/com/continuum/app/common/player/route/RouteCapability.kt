package com.continuum.app.common.player.route

/**
 * Per-route feature capability. Field names mirror Apple's
 * `tvos-player/05-route-capability-matrix.md` so HUD labels and code
 * vocabularies match across platforms.
 */
data class RouteCapability(
    /** Can we report player-side buffer state to the HUD? */
    val buffersReported: Boolean,

    /** User can toggle video-gravity (letterbox vs zoom). */
    val videoGravityToggle: Boolean,

    /** User can force-disable HDR (force SDR output). */
    val hdrToggle: Boolean,

    /** Audio delay slider in milliseconds is supported. */
    val audioDelaySupported: Boolean,

    /** Subtitle delay slider in milliseconds is supported. */
    val subtitleDelaySupported: Boolean,

    /** What kinds of subtitle styling are available. */
    val subtitleStyling: SubtitleStyling,

    /** Decoder tunneling supported (low-latency game-mode-ish playback). */
    val supportsTunneling: Boolean,

    /** Bitstream passthrough to AVR (Atmos/DTS:X) supported. */
    val supportsPassthrough: Boolean,
)

enum class SubtitleStyling {
    /** No client-side subtitle styling. */
    None,
    /** Limited — only what the system caption settings expose. */
    SystemOnly,
    /** Full — color/size/font/background can be overridden client-side. */
    Full,
}
