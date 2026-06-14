package com.continuum.app.tv.ui.shell

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

class TvProfileActionsPanelReadabilityTest {
    private val source = File(
        "src/androidMain/kotlin/com/continuum/app/tv/ui/shell/TvMainShell.kt",
    ).readText()

    @Test
    fun focusedProfileRowsInvertTextAgainstLightFocusBackground() {
        assertTrue(source.contains("collectIsFocusedAsState()"))
        assertTrue(source.contains("focusedContentColor = Color.Black"))
        assertTrue(source.contains("color = if (isFocused) Color.Black else MaterialTheme.colorScheme.onSurface"))
    }

    @Test
    fun profilePanelSeparatesItselfFromContentBehindIt() {
        assertTrue(source.contains("Color.Black.copy(alpha = 0.58f)"))
        assertTrue(source.contains("color = MaterialTheme.colorScheme.surface,"))
        assertTrue(source.contains("content behind the menu cannot visually merge"))
    }
}
