package com.continuum.app.common.player

enum class HdrType { SDR, HDR10, HDR10_PLUS, HLG, DOLBY_VISION }

/**
 * Pure HDR target selection: honor the content's HDR type only when the display
 * advertises it; otherwise fall back to SDR (tone-mapped). The Android read of
 * Display.Mode.getSupportedHdrTypes (API-34+) feeds [displaySupported] at the
 * call site; below API-34 the platform negotiates HDR implicitly and the caller
 * passes an empty set so this returns SDR — we never force an unsupported mode.
 */
object HdrModeSelection {
    fun choose(contentHdr: HdrType, displaySupported: Set<HdrType>): HdrType = when {
        contentHdr == HdrType.SDR -> HdrType.SDR
        contentHdr in displaySupported -> contentHdr
        else -> HdrType.SDR
    }
}
