package com.continuum.app.tv.ui.components

import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TvMediaRowArtworkSourceTest {
    private val source = File(
        "src/androidMain/kotlin/com/continuum/app/tv/ui/components/TvMediaRow.kt",
    ).readText()

    @Test
    fun wideRowArtworkPrefersBackdropBeforePosterForAllItems() {
        assertTrue(source.contains("return backdropUrl ?: posterUrl"))
        assertTrue(source.contains("return backdropThumbhash ?: posterThumbhash"))
        assertFalse(source.contains("posterUrl ?: backdropUrl"))
        assertFalse(source.contains("posterThumbhash ?: backdropThumbhash"))
    }

    @Test
    fun mediaRowSupportsExplicitDirectionUpCallbacks() {
        assertTrue(
            source.contains("onDirectionUp: (() -> Boolean)? = null"),
            "Rows need an explicit D-pad Up callback for detail pages that must scroll/focus back to the hero.",
        )
        assertTrue(
            source.contains("onPreviewKeyEvent") &&
                source.contains("Key.DirectionUp") &&
                source.contains("onDirectionUp?.invoke()"),
            "The row should intercept DirectionUp instead of relying only on focusProperties.",
        )
    }
}
