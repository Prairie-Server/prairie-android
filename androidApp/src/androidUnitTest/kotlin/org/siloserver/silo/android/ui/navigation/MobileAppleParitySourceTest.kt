package org.siloserver.silo.android.ui.navigation

import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MobileAppleParitySourceTest {
    private val root = "src/androidMain/kotlin/org/siloserver/silo/android"

    @Test
    fun rootShellMatchesIosTabAndChromeShape() {
        val bottomNav = File("$root/ui/navigation/BottomNavBar.kt").readText()
        val appNavigation = File("$root/ui/navigation/AppNavigation.kt").readText()
        val mainScreen = File("$root/ui/screens/MainScreen.kt").readText()
        val homeScreen = File("$root/ui/screens/home/HomeScreen.kt").readText()
        val topBar = File("$root/ui/components/MainAppTopBar.kt").readText()
        val libraries = File("$root/ui/screens/libraries/LibrariesScreen.kt").readText()
        val recommendations = File("$root/ui/screens/recommendations/RecommendationsScreen.kt").readText()
        val settings = File("$root/ui/screens/settings/SettingsScreen.kt").readText()
        val search = File("$root/ui/screens/search/SearchScreen.kt").readText()
        val searchBar = File("$root/ui/screens/search/SearchBar.kt").readText()

        assertTrue(
            bottomNav.contains("Calendar(Route.Calendar.route, \"Calendar\""),
            "Android mobile bottom navigation should expose iOS's Calendar tab.",
        )
        assertTrue(
            appNavigation.contains("MainScreen(navController, Tab.Calendar)"),
            "The Calendar route should enter the mobile tab shell, not a pushed back-stack screen.",
        )
        assertTrue(
            mainScreen.contains("Tab.Calendar ->"),
            "MainScreen should render Calendar as a first-class tab root.",
        )
        assertTrue(
            homeScreen.contains("SiloWordmark("),
            "Home chrome should include the same left-side Silo wordmark anchor as iOS HomeView.",
        )
        assertTrue(
            topBar.contains("painterResource(id = R.drawable.silo_wordmark)"),
            "Shared Android mobile top bars should use the real Silo wordmark asset.",
        )
        assertFalse(
            topBar.contains("text = \"Media\""),
            "The iOS wordmark does not include the old Android-only Media subtitle pill.",
        )
        assertTrue(
            libraries.contains("label = \"Library\""),
            "Libraries should expose iOS's Recommended / Library / Collections subtab set.",
        )
        assertTrue(
            recommendations.contains("SavedShortcutsRow("),
            "For You should expose iOS's saved Watchlist/Favorites shortcut row before recommendations.",
        )
        assertTrue(
            mainScreen.contains("onWatchlistClick = { navController.navigate(Route.Watchlist.route) }"),
            "The For You Watchlist shortcut should navigate to the existing Watchlist route.",
        )
        assertTrue(
            mainScreen.contains("onFavoritesClick = { navController.navigate(Route.Favorites.route) }"),
            "The For You Favorites shortcut should navigate to the existing Favorites route.",
        )
        assertTrue(
            settings.contains("SettingsSectionHeader(title = \"Library\")"),
            "Settings should expose iOS's Library section.",
        )
        assertTrue(
            settings.contains("label = \"Watchlist\""),
            "Settings Library section should include Watchlist.",
        )
        assertTrue(
            settings.contains("label = \"Favorites\""),
            "Settings Library section should include Favorites.",
        )
        assertTrue(
            settings.contains("label = \"Watch History\""),
            "Settings Library section should include Watch History.",
        )
        assertTrue(
            settings.contains("label = \"Collections\""),
            "Settings Library section should include Collections.",
        )
        assertTrue(
            appNavigation.contains("onNavigateToWatchlist = { navController.navigate(Route.Watchlist.route) }"),
            "Settings Watchlist row should route to the existing Watchlist screen.",
        )
        assertTrue(
            appNavigation.contains("onNavigateToFavorites = { navController.navigate(Route.Favorites.route) }"),
            "Settings Favorites row should route to the existing Favorites screen.",
        )
        assertTrue(
            appNavigation.contains("onNavigateToHistory = { navController.navigate(Route.History.route) }"),
            "Settings Watch History row should route to the existing History screen.",
        )
        assertTrue(
            appNavigation.contains("onNavigateToCollections = { navController.navigate(Route.Collections().route) }"),
            "Settings Collections row should route to the existing Collections screen.",
        )
        assertTrue(
            search.contains("state.query.isNotBlank() && state.availableMediaTypes.size > 1"),
            "Search media filters should stay hidden until the user enters a query, matching iOS searchable behavior.",
        )
        assertTrue(
            search.contains("text = \"Search Silo\""),
            "The empty Search state should use the iOS-style Search Silo entry point.",
        )
        assertTrue(
            searchBar.contains("text = \"Search Silo\""),
            "The Search field placeholder should describe app-wide search instead of only library search.",
        )
    }
}
