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
        // For You Watchlist/Favorites toggle the saved list IN PLACE over the feed
        // rather than navigating to a separate page — a deliberate divergence from
        // iOS chosen by Jim (2026-07-09). The saved-list routes still exist and are
        // reachable from Settings (asserted below), just not from these shortcuts.
        assertTrue(
            recommendations.contains(
                "if (savedListSelection == SavedList.Watchlist) null else SavedList.Watchlist",
            ),
            "The For You Watchlist shortcut should toggle the saved list in place, not navigate.",
        )
        assertTrue(
            recommendations.contains(
                "if (savedListSelection == SavedList.Favorites) null else SavedList.Favorites",
            ),
            "The For You Favorites shortcut should toggle the saved list in place, not navigate.",
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
        assertTrue(
            search.contains("RequestsFeatureStore"),
            "Search should gate Apple's Available to request section through the shared Requests capability.",
        )
        assertTrue(
            search.contains("RequestSearchSection("),
            "Search should embed Apple's Available to request section when Requests are enabled.",
        )
        assertTrue(
            File("$root/ui/screens/search/RequestSearchSection.kt").readText().contains("RequestSearchFeedback("),
            "Request search should expose loading, empty, and error states instead of silently disappearing.",
        )
        assertTrue(
            appNavigation.contains("onRequestMediaClick = { item ->"),
            "Global Search request cards should navigate to the existing request-detail route.",
        )
    }
}
