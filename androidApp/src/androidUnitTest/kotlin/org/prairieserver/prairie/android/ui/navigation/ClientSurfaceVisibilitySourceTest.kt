package org.prairieserver.prairie.android.ui.navigation

import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ClientSurfaceVisibilitySourceTest {
    private val mainScreen = File(
        "src/androidMain/kotlin/org/prairieserver/prairie/android/ui/screens/MainScreen.kt",
    ).readText()
    private val appNavigation = File(
        "src/androidMain/kotlin/org/prairieserver/prairie/android/ui/navigation/AppNavigation.kt",
    ).readText()
    private val homeScreen = File(
        "src/androidMain/kotlin/org/prairieserver/prairie/android/ui/screens/home/HomeScreen.kt",
    ).readText()
    private val librariesScreen = File(
        "src/androidMain/kotlin/org/prairieserver/prairie/android/ui/screens/libraries/LibrariesScreen.kt",
    ).readText()
    private val mainAppTopBar = File(
        "src/androidMain/kotlin/org/prairieserver/prairie/android/ui/components/MainAppTopBar.kt",
    ).readText()
    private val settingsScreen = File(
        "src/androidMain/kotlin/org/prairieserver/prairie/android/ui/screens/settings/SettingsScreen.kt",
    ).readText()
    private val accountSection = File(
        "src/androidMain/kotlin/org/prairieserver/prairie/android/ui/screens/settings/AccountSection.kt",
    ).readText()
    private val itemDetailScreen = File(
        "src/androidMain/kotlin/org/prairieserver/prairie/android/ui/screens/detail/ItemDetailScreen.kt",
    ).readText()

    @Test
    fun requestsAreFeatureGatedInMobileProfileMenus() {
        assertTrue(mainScreen.contains("RequestsFeatureStore"))
        assertTrue(mainScreen.contains("requestsFeatureStore.reset()"))
        assertTrue(mainScreen.contains("requestsFeatureStore.refresh()"))
        assertTrue(mainScreen.contains("authRepository.logout()"))
        assertTrue(appNavigation.contains("Route.Requests.route"))
        assertTrue(mainScreen.contains("val requestsMenuAction: (() -> Unit)? = if (requestsEnabled)"))
        assertTrue(mainScreen.contains("navController.navigate(Route.Requests.route)"))
        assertFalse(mainScreen.contains("onRequestsClick = null"))
        assertTrue(homeScreen.contains("if (onRequestsClick != null)"))
        assertTrue(librariesScreen.contains("if (onRequestsClick != null)"))
        assertTrue(mainAppTopBar.contains("if (onRequestsClick != null)"))
        assertTrue(homeScreen.contains("text = \"Sign Out\""))
        assertTrue(librariesScreen.contains("text = \"Sign Out\""))
        assertTrue(mainAppTopBar.contains("text = \"Sign Out\""))
        assertFalse(homeScreen.contains("Favorites & Watchlist"))
        assertFalse(librariesScreen.contains("Favorites & Watchlist"))
        assertFalse(mainAppTopBar.contains("Favorites & Watchlist"))
        assertFalse(homeScreen.contains("Text(\"Calendar\")"))
        assertFalse(mainAppTopBar.contains("Text(\"Calendar\")"))
        // Requests is a profile-menu action (Apple parity), never a Settings row.
        assertFalse(settingsScreen.contains("onNavigateToRequests"))
        assertFalse(accountSection.contains("isRequestsVisible"))
        assertFalse(accountSection.contains("title = \"Requests\",\n                    icon = Icons.Default.Movie"))
    }

    @Test
    fun watchTogetherCodeRemainsRoutableButHiddenFromMobileDetailOverflow() {
        assertTrue(itemDetailScreen.contains("CLIENT_WATCH_TOGETHER_SURFACE_ENABLED"))
        assertTrue(itemDetailScreen.contains("onWatchTogether = if (CLIENT_WATCH_TOGETHER_SURFACE_ENABLED)"))
    }

    @Test
    fun liveTvIsFeatureGatedInMobileProfileMenus() {
        assertTrue(mainScreen.contains("LiveTvFeatureStore"))
        assertTrue(mainScreen.contains("liveTvFeatureStore.reset()"))
        assertTrue(mainScreen.contains("liveTvFeatureStore.refresh()"))
        assertTrue(appNavigation.contains("Route.LiveTv.route"))
        assertTrue(mainScreen.contains("val liveTvMenuAction: (() -> Unit)? = if (liveTvEnabled)"))
        assertTrue(mainScreen.contains("navController.navigate(Route.LiveTv.route)"))
        assertTrue(homeScreen.contains("if (onLiveTvClick != null)"))
        assertTrue(librariesScreen.contains("if (onLiveTvClick != null)"))
        assertTrue(mainAppTopBar.contains("if (onLiveTvClick != null)"))
        assertTrue(homeScreen.contains("Text(\"Live TV\")"))
        assertTrue(librariesScreen.contains("Text(\"Live TV\")"))
        assertTrue(mainAppTopBar.contains("Text(\"Live TV\")"))
        assertFalse(settingsScreen.contains("onNavigateToLiveTv"))
    }
}
