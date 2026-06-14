package com.continuum.app.tv.ui.screens.player

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

class TvPlayerUsabilityTest {
    private val source = File(
        "src/androidMain/kotlin/com/continuum/app/tv/ui/screens/player/TvPlayerScreen.kt",
    ).readText()

    @Test
    fun embeddedPlayerViewDoesNotStealRemoteFocusFromComposeControls() {
        assertTrue(source.contains("isFocusable = false"))
        assertTrue(source.contains("isFocusableInTouchMode = false"))
        assertTrue(source.contains("descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS"))
    }
}
