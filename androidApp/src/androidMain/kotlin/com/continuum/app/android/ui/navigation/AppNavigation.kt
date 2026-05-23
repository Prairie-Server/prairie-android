package com.continuum.app.android.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.continuum.app.android.ui.screens.MainScreen
import com.continuum.app.android.ui.screens.auth.LoginScreen
import com.continuum.app.android.ui.screens.auth.ServerSetupScreen
import com.continuum.app.android.ui.screens.auth.SetupScreen
import com.continuum.app.android.ui.screens.auth.SignupScreen
import com.continuum.app.android.ui.screens.browse.BrowseScreen
import com.continuum.app.android.ui.screens.browse.BrowseViewModel
import com.continuum.app.android.ui.screens.collections.CollectionDetailScreen
import com.continuum.app.android.ui.screens.collections.CollectionsScreen
import com.continuum.app.android.ui.screens.collections.LibraryCollectionsScreen
import com.continuum.app.android.ui.screens.detail.ItemDetailScreen
import com.continuum.app.android.ui.screens.detail.ItemDetailViewModel
import com.continuum.app.android.ui.screens.people.PersonDetailScreen
import com.continuum.app.android.ui.screens.people.PersonDetailViewModel
import com.continuum.app.android.ui.screens.personal.FavoritesScreen
import com.continuum.app.android.ui.screens.personal.HistoryScreen
import com.continuum.app.android.ui.screens.personal.PersonalListsScreen
import com.continuum.app.android.ui.screens.personal.WatchlistScreen
import com.continuum.app.android.ui.screens.admin.AdminScreen
import com.continuum.app.android.ui.screens.player.PlayerScreen
import com.continuum.app.android.ui.screens.profiles.ProfileSelectionScreen
import com.continuum.app.android.ui.screens.search.SearchScreen
import com.continuum.app.android.ui.screens.search.SearchViewModel
import com.continuum.app.android.ui.screens.servers.ServerListScreen
import com.continuum.app.android.ui.screens.servers.ServerSwitchDestination
import com.continuum.app.android.ui.screens.settings.SettingsScreen
import com.continuum.app.network.TokenManager
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun AppNavigation(
    navController: NavHostController = rememberNavController(),
    startDestination: String = Route.Login.route,
) {
    val tokenManager: TokenManager = koinInject()

    // Graceful handling of server-side session invalidation (refresh 401'd).
    // The TokenManager has already wiped the active server's tokens by the
    // time this fires; we just route the user back to Login so they can
    // re-authenticate against the same server.
    LaunchedEffect(Unit) {
        tokenManager.sessionExpired.collect {
            navController.navigate(Route.Login.route) {
                popUpTo(0) { inclusive = true }
                launchSingleTop = true
            }
        }
    }

    NavHost(
        navController = navController,
        startDestination = startDestination,
    ) {
        // ---- Auth flow ----
        composable(Route.ServerSetup.route) {
            ServerSetupScreen(
                onNavigateToSetup = {
                    navController.navigate(Route.Setup.route) {
                        popUpTo(Route.ServerSetup.route) { inclusive = true }
                    }
                },
                onNavigateToLogin = { _ ->
                    navController.navigate(Route.Login.route) {
                        popUpTo(Route.ServerSetup.route) { inclusive = true }
                    }
                },
            )
        }
        composable(Route.Login.route) {
            LoginScreen(
                onNavigateToSignup = {
                    navController.navigate(Route.Signup.route)
                },
                onNavigateToProfiles = {
                    navController.navigate(Route.ProfileSelection.route) {
                        popUpTo(Route.Login.route) { inclusive = true }
                    }
                },
            )
        }
        composable(Route.Setup.route) {
            SetupScreen(
                onNavigateToProfiles = {
                    navController.navigate(Route.ProfileSelection.route) {
                        popUpTo(Route.Setup.route) { inclusive = true }
                    }
                },
            )
        }
        composable(Route.Signup.route) {
            SignupScreen(
                onNavigateToLogin = {
                    navController.popBackStack()
                },
                onNavigateToProfiles = {
                    navController.navigate(Route.ProfileSelection.route) {
                        popUpTo(Route.Signup.route) { inclusive = true }
                    }
                },
            )
        }

        // ---- Server list (multi-server management) ----
        composable(Route.ServerList.route) {
            ServerListScreen(
                onAddServer = {
                    navController.navigate(Route.ServerSetup.route)
                },
                onSwitched = { destination ->
                    // Route to whichever screen the new server's stored
                    // credentials can support — Home if a token+profile
                    // already exist (so the user stays signed in), else
                    // ProfileSelection or Login as appropriate.
                    val target = when (destination) {
                        ServerSwitchDestination.Home -> Route.Home.route
                        ServerSwitchDestination.ProfileSelection -> Route.ProfileSelection.route
                        ServerSwitchDestination.Login -> Route.Login.route
                    }
                    navController.navigate(target) {
                        popUpTo(0) { inclusive = true }
                        launchSingleTop = true
                    }
                },
                onBack = { navController.popBackStack() },
            )
        }

        // ---- Profile selection ----
        composable(Route.ProfileSelection.route) {
            ProfileSelectionScreen(
                onNavigateToHome = {
                    navController.navigate(Route.Home.route) {
                        popUpTo(0) { inclusive = true }
                    }
                },
                onNavigateToCreateProfile = {
                    // TODO: wire create profile screen
                },
                onNavigateToEditProfile = { _ ->
                    // TODO: wire edit profile screen
                },
            )
        }

        // ---- Main tabs ----
        composable(Route.Home.route) {
            MainScreen(navController, Tab.Home)
        }
        composable(Route.Libraries.route) {
            MainScreen(navController, Tab.Libraries)
        }
        composable(Route.Recommendations.route) {
            MainScreen(navController, Tab.Recommendations)
        }
        composable(Route.Settings.route) {
            SettingsScreen(
                onNavigateToAdmin = {
                    navController.navigate(Route.Admin.route)
                },
                onNavigateToServers = {
                    navController.navigate(Route.ServerList.route)
                },
                onLoggedOut = {
                    navController.navigate(Route.Login.route) {
                        popUpTo(0) { inclusive = true }
                    }
                },
                showTopBar = true,
                onBackClick = { navController.popBackStack() },
            )
        }
        composable(Route.Search.route) {
            val searchViewModel = koinViewModel<SearchViewModel>()
            SearchScreen(
                onItemClick = { contentId ->
                    navController.navigate(Route.ItemDetail(contentId).route)
                },
                onBackClick = { navController.popBackStack() },
                viewModel = searchViewModel,
            )
        }

        // ---- Browse / Catalog ----
        composable(
            route = Route.Browse.ROUTE,
            arguments = listOf(
                navArgument("libraryId") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
            ),
        ) {
            val browseViewModel = koinViewModel<BrowseViewModel>()
            BrowseScreen(
                onItemClick = { contentId ->
                    navController.navigate(Route.ItemDetail(contentId).route)
                },
                onBackClick = { navController.popBackStack() },
                viewModel = browseViewModel,
            )
        }

        composable(
            route = Route.CollectionDetail.ROUTE,
            arguments = listOf(
                navArgument("collectionId") { type = NavType.StringType },
                navArgument("libraryId") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
            ),
        ) { backStackEntry ->
            CollectionDetailScreen(
                collectionId = backStackEntry.arguments?.getString("collectionId") ?: "",
                onBackClick = { navController.popBackStack() },
                onItemClick = { contentId ->
                    navController.navigate(Route.ItemDetail(contentId).route)
                },
            )
        }

        // ---- Detail screens ----
        composable(
            route = Route.ItemDetail.ROUTE,
            arguments = listOf(
                navArgument("contentId") { type = NavType.StringType },
                navArgument("seasonNumber") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
            ),
        ) {
            val detailViewModel = koinViewModel<ItemDetailViewModel>()
            ItemDetailScreen(
                onBackClick = { navController.popBackStack() },
                onPlayClick = { contentId, fileId, audioTrackIndex, subtitleTrackIndex ->
                    navController.navigate(
                        Route.Player(
                            contentId = contentId,
                            fileId = fileId,
                            audioTrackIndex = audioTrackIndex,
                            subtitleTrackIndex = subtitleTrackIndex,
                        ).route,
                    )
                },
                onItemDetailClick = { contentId ->
                    navController.navigate(Route.ItemDetail(contentId).route)
                },
                onSeriesClick = { seriesId ->
                    navController.navigate(Route.ItemDetail(seriesId).route)
                },
                onSeasonClick = { seriesId, seasonNumber ->
                    navController.navigate(Route.ItemDetail(seriesId, seasonNumber).route)
                },
                onPersonClick = { personId ->
                    personId.toIntOrNull()?.let { id ->
                        navController.navigate(Route.PersonDetail(id).route)
                    }
                },
                viewModel = detailViewModel,
            )
        }

        composable(
            route = Route.PersonDetail.ROUTE,
            arguments = listOf(
                navArgument("personId") { type = NavType.IntType },
            ),
        ) {
            val personViewModel = koinViewModel<PersonDetailViewModel>()
            PersonDetailScreen(
                onBackClick = { navController.popBackStack() },
                onItemClick = { contentId ->
                    navController.navigate(Route.ItemDetail(contentId).route)
                },
                viewModel = personViewModel,
            )
        }

        // ---- Player (fullscreen) ----
        composable(
            route = Route.Player.ROUTE,
            arguments = listOf(
                navArgument("contentId") { type = NavType.StringType },
                navArgument("fileId") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
                navArgument("audioTrackIndex") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
                navArgument("subtitleTrackIndex") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
            ),
        ) { backStackEntry ->
            PlayerScreen(
                contentId = backStackEntry.arguments?.getString("contentId") ?: "",
                initialFileId = backStackEntry.arguments?.getString("fileId")?.toIntOrNull(),
                initialAudioTrackIndex = backStackEntry.arguments?.getString("audioTrackIndex")?.toIntOrNull(),
                initialSubtitleTrackIndex = backStackEntry.arguments?.getString("subtitleTrackIndex")?.toIntOrNull(),
                navController = navController,
            )
        }

        // ---- Personal data ----
        composable(Route.Favorites.route) {
            FavoritesScreen(
                onBackClick = { navController.popBackStack() },
                onItemClick = { contentId ->
                    navController.navigate(Route.ItemDetail(contentId).route)
                },
            )
        }
        composable(Route.Watchlist.route) {
            WatchlistScreen(
                onBackClick = { navController.popBackStack() },
                onItemClick = { contentId ->
                    navController.navigate(Route.ItemDetail(contentId).route)
                },
            )
        }
        composable(Route.PersonalLists.route) {
            PersonalListsScreen(
                onBackClick = { navController.popBackStack() },
                onItemClick = { contentId ->
                    navController.navigate(Route.ItemDetail(contentId).route)
                },
            )
        }
        composable(Route.History.route) {
            HistoryScreen(
                onBackClick = { navController.popBackStack() },
                onItemClick = { contentId ->
                    navController.navigate(Route.ItemDetail(contentId).route)
                },
            )
        }
        composable(
            route = Route.Collections.ROUTE,
            arguments = listOf(
                navArgument("libraryId") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
            ),
        ) { backStackEntry ->
            val libraryId = backStackEntry.arguments?.getString("libraryId")?.toIntOrNull()
            if (libraryId != null) {
                LibraryCollectionsScreen(
                    onBackClick = { navController.popBackStack() },
                    onCollectionClick = { collectionId ->
                        navController.navigate(Route.CollectionDetail(collectionId, libraryId).route)
                    },
                )
            } else {
                CollectionsScreen(
                    onBackClick = { navController.popBackStack() },
                    onCollectionClick = { collectionId ->
                        navController.navigate(Route.CollectionDetail(collectionId).route)
                    },
                )
            }
        }

        // ---- Admin ----
        composable(Route.Admin.route) {
            AdminScreen(
                onBackClick = { navController.popBackStack() },
            )
        }
    }
}

