package org.siloserver.silo.tv.ui.components

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

class TvAuroraChromeSourceTest {
    private val source = File(
        "src/androidMain/kotlin/org/siloserver/silo/tv/ui/components/TvAuroraChrome.kt",
    ).readText()

    @Test
    fun ghostButtonAnimatesTextColorWithFocusedBackground() {
        assertTrue(source.contains("val content by animateColorAsState("))
        assertTrue(source.contains("Text(text = label, color = content"))
    }
}
