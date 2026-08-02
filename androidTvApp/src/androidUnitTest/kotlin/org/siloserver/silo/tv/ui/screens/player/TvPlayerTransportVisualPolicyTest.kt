package org.siloserver.silo.tv.ui.screens.player

import kotlin.test.Test
import kotlin.test.assertTrue

class TvPlayerTransportVisualPolicyTest {

    @Test
    fun primaryAndSecondaryControlsMeetMinimumButtonTarget() {
        assertTrue(tvTransportControlMetrics(isPrimary = true).buttonSizeDp >= 44f)
        assertTrue(tvTransportControlMetrics(isPrimary = false).buttonSizeDp >= 44f)
    }

    @Test
    fun primaryControlMeetsMinimumGlyphSize() {
        assertTrue(tvTransportControlMetrics(isPrimary = true).symbolSizeDp >= 22f)
    }

    @Test
    fun secondaryControlMeetsMinimumGlyphSize() {
        assertTrue(tvTransportControlMetrics(isPrimary = false).symbolSizeDp >= 20f)
    }
}
