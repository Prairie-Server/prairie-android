package org.prairieserver.prairie.android.ui.screens.reader.reflow

import kotlin.math.roundToInt

internal class ReflowInitialRelocationGate(initialPageProgression: Double) {
    private val initialProgression = initialPageProgression.coerceIn(0.0, 1.0)
    private var suppressUntilPage: Int? = null
    private var initialTargetConsumed = false

    fun consumeInitialPageTarget(pageCount: Int): Int? {
        if (initialTargetConsumed || initialProgression <= 0.0) {
            initialTargetConsumed = true
            return null
        }
        initialTargetConsumed = true
        val count = pageCount.coerceAtLeast(1)
        val target = (initialProgression * (count - 1)).roundToInt()
        suppressUntilPage = target
        return target
    }

    fun shouldPersistRelocation(page: Int): Boolean {
        val target = suppressUntilPage ?: return true
        if (page != target) return false
        suppressUntilPage = null
        return true
    }
}
