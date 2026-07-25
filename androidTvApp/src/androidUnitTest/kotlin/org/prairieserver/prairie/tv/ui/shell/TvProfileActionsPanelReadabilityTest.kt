package org.prairieserver.prairie.tv.ui.shell

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

class TvProfileActionsPanelReadabilityTest {
    private val source = File(
        "src/androidMain/kotlin/org/prairieserver/prairie/tv/ui/shell/TvMainShell.kt",
    ).readText()

    @Test
    fun focusedProfileRowsInvertTextAgainstLightFocusBackground() {
        // TvProfileDropdown rows invert to a solid PrairieOnSurface fill with
        // DarkBackground content on focus (the Skyline capsule grammar).
        assertTrue(source.contains("collectIsFocusedAsState()"))
        assertTrue(source.contains("focusedContentColor = DarkBackground"))
        assertTrue(source.contains("if (isFocused) DarkBackground else PrairieOnSurface.copy(alpha = 0.9f)"))
    }

    @Test
    fun profilePanelSeparatesItselfFromContentBehindIt() {
        // The dropdown floats on its own frosted panel chrome (no full-screen
        // scrim) so content behind it can't visually merge.
        assertTrue(source.contains("tvSkylinePanelChrome()"))
    }
}
