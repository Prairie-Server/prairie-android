package org.prairieserver.prairie.tv.ui.components

import kotlin.test.Test
import kotlin.test.assertTrue

class TvAuroraBackdropSourceTest {
    private val source = java.io.File(
        "src/androidMain/kotlin/org/prairieserver/prairie/tv/ui/components/TvAuroraBackdrop.kt",
    ).readText()

    @Test
    fun profileVariantSuppressesVisibleRibbon() {
        assertTrue(
            source.contains("val Profile = TvAuroraVariant(-10f, 0.27f, 0.66f, ribbonIntensity = 0f)"),
            "The profile picker should match tvOS: soft glow, no visible diagonal ribbon.",
        )
        assertTrue(
            source.contains("if (variant.ribbonIntensity > 0f)"),
            "Ribbon drawing must be gated so profile screens can suppress the stripe.",
        )
    }
}
