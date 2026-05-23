package com.continuum.app.common.player

import android.view.Display
import kotlin.math.abs

/**
 * Picks the best display mode for a given target refresh rate. Shared between
 * [HdrDisplayController] (TV — uses it as one input to an
 * HDMI-resolution-first selection) and [RefreshRateMatcher] (phone — uses it
 * as the *only* selection criterion, since the phone panel resolution is
 * fixed and changing it would downgrade the panel pipeline).
 */
internal object RefreshRateSelector {

    /** Closest refresh rate wins; integer-multiple rates get a small bonus. */
    fun pickByRefreshRate(
        modes: Array<Display.Mode>,
        frameRateHz: Float,
    ): Display.Mode? {
        if (modes.isEmpty() || frameRateHz <= 0f) return null
        return modes.minByOrNull { scoreMode(it, frameRateHz) }
    }

    /** Overload that filters candidates first (e.g. by resolution on a TV panel). */
    fun pickByRefreshRateAmong(
        candidates: List<Display.Mode>,
        frameRateHz: Float,
    ): Display.Mode? {
        if (candidates.isEmpty() || frameRateHz <= 0f) return null
        return candidates.minByOrNull { scoreMode(it, frameRateHz) }
    }

    private fun scoreMode(mode: Display.Mode, frameRateHz: Float): Float {
        val rateDelta = abs(mode.refreshRate - frameRateHz)
        val ratio = mode.refreshRate / frameRateHz
        val integerMultiple = abs(ratio - ratio.toInt()) < 0.01f && ratio >= 1f
        return rateDelta + if (integerMultiple) -0.25f else 0f
    }
}
