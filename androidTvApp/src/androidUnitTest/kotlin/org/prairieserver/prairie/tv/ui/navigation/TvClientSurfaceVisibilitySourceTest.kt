package org.prairieserver.prairie.tv.ui.navigation

import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TvClientSurfaceVisibilitySourceTest {
    private val settingsScreen = File(
        "src/androidMain/kotlin/org/prairieserver/prairie/tv/ui/screens/settings/TvSettingsScreen.kt",
    ).readText()
    private val shell = File(
        "src/androidMain/kotlin/org/prairieserver/prairie/tv/ui/shell/TvMainShell.kt",
    ).readText()

    @Test
    fun requestsAreFeatureGatedInTvProfileMenu() {
        assertTrue(shell.contains("RequestsFeatureStore"))
        assertTrue(shell.contains("requestsFeatureStore.reset()"))
        assertTrue(shell.contains("requestsFeatureStore.refresh()"))
        assertTrue(shell.contains("ProfileDropdownRow(label = \"Watchlist\""))
        assertTrue(shell.contains("ProfileDropdownRow(label = \"Favorites\""))
        assertTrue(shell.contains("ProfileDropdownRow(label = \"History\""))
        assertTrue(shell.contains("TvMainRoute.Requests.route"))
        assertTrue(shell.contains("showRequests = requestsEnabled"))
        assertTrue(shell.contains("onRequests = closeMenuAnd"))
        assertTrue(shell.contains("if (showRequests) {"))
        assertTrue(shell.contains("label = \"Requests\""))
        assertTrue(shell.contains("icon = Icons.Filled.AutoAwesome"))
        assertTrue(shell.contains("onClick = onRequests"))
        assertFalse(shell.contains("label = \"Admin Dashboard\",\n                icon = Icons.Filled.AdminPanelSettings"))
        assertFalse(settingsScreen.contains("CLIENT_REQUESTS_SURFACE_ENABLED"))
        assertFalse(settingsScreen.contains("SettingsActionRow(label = \"Requests\""))
    }
}
