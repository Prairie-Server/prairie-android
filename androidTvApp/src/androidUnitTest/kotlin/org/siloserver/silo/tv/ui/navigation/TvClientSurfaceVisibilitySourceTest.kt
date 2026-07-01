package org.siloserver.silo.tv.ui.navigation

import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TvClientSurfaceVisibilitySourceTest {
    private val settingsScreen = File(
        "src/androidMain/kotlin/org/siloserver/silo/tv/ui/screens/settings/TvSettingsScreen.kt",
    ).readText()
    private val shell = File(
        "src/androidMain/kotlin/org/siloserver/silo/tv/ui/shell/TvMainShell.kt",
    ).readText()

    @Test
    fun requestsCodeRemainsRoutableButHiddenFromTvSettings() {
        assertTrue(settingsScreen.contains("CLIENT_REQUESTS_SURFACE_ENABLED"))
        assertTrue(settingsScreen.contains("if (CLIENT_REQUESTS_SURFACE_ENABLED)"))
        assertTrue(shell.contains("TvMainRoute.Requests.route"))
    }
}
