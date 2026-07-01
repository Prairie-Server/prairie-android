package org.siloserver.silo.tv.ui.screens.servers

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

class TvServerListFocusSourceTest {
    private val source = File(
        "src/androidMain/kotlin/org/siloserver/silo/tv/ui/screens/servers/TvServerListScreen.kt",
    ).readText()

    @Test
    fun serverRowsAnchorFocusSafelyWhenListMaterializes() {
        assertTrue(source.contains("repeat(TvInitialFocusRetryCount)"))
        assertTrue(source.contains("if (runCatching { firstFocus.requestFocus() }.isSuccess) return@LaunchedEffect"))
        assertTrue(source.contains("delay(TvInitialFocusRetryDelayMs)"))
    }
}
