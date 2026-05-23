package com.continuum.app.tv.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.continuum.app.network.TokenManager
import com.continuum.app.tv.ui.shell.TvMainShell
import com.continuum.app.tv.ui.screens.admin.TvAdminScreen
import com.continuum.app.tv.ui.screens.auth.TvLoginScreen
import com.continuum.app.tv.ui.screens.auth.TvServerSetupScreen
import com.continuum.app.tv.ui.screens.collections.TvCollectionDetailScreen
import com.continuum.app.tv.ui.screens.detail.TvItemDetailScreen
import com.continuum.app.tv.ui.screens.library.TvLibraryCollectionDetailScreen
import com.continuum.app.tv.ui.screens.player.TvPlayerScreen
import com.continuum.app.tv.ui.screens.profiles.TvProfileSelectionScreen
import com.continuum.app.tv.ui.screens.servers.TvServerListScreen
import com.continuum.app.tv.ui.screens.servers.TvServerSwitchDestination
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

/**
 * Top-level TV navigation graph.
 *
 * ServerSetup → Login → ProfileSelection → Main (drawer). Item detail, player,
 * and collection detail are pushed on top of Main when the user drills down.
 * Settings-reachable grids (favorites, watchlist, history, collections,
 * admin) are also top-level routes so they can cover the full screen.
 */
@Composable
fun TvAppNavigation(
    startDestination: String,
    modifier: Modifier = Modifier,
) {
    val navController = rememberNavController()
    val scope = rememberCoroutineScope()
    val tokenManager: TokenManager = koinInject()

    // Graceful handling of server-side session invalidation (refresh 401'd).
    // The TokenManager has already cleared the active server's tokens by the
    // time this fires; we just route the user back to Login so they can
    // re-authenticate against the same server (the [ServerRegistry] entry is
    // preserved so they don't have to re-enter the URL).
    LaunchedEffect(Unit) {
        tokenManager.sessionExpired.collect {
            navController.navigate(TvRoute.Login.route) {
                // Clear the entire back stack so the user can't press Back
                // to return to a screen that has no credentials to render.
                popUpTo(0) { inclusive = true }
                launchSingleTop = true
            }
        }
    }

    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier,
    ) {
        composable(TvRoute.ServerSetup.route) {
            TvServerSetupScreen(
                onContinueToLogin = {
                    navController.navigate(TvRoute.Login.route) {
                        popUpTo(TvRoute.ServerSetup.route) { inclusive = true }
                    }
                },
            )
        }

        composable(TvRoute.ServerList.route) {
            TvServerListScreen(
                onAddServer = {
                    navController.navigate(TvRoute.ServerSetup.route)
                },
                onSwitched = { destination ->
                    // Land on the deepest route the new server's stored
                    // credentials support — keeps the user signed in across
                    // a server switch when tokens already exist for the target.
                    val target = when (destination) {
                        TvServerSwitchDestination.Home -> TvRoute.Main.route
                        TvServerSwitchDestination.ProfileSelection ->
                            TvRoute.ProfileSelection.route
                        TvServerSwitchDestination.Login -> TvRoute.Login.route
                    }
                    navController.navigate(target) {
                        popUpTo(0) { inclusive = true }
                        launchSingleTop = true
                    }
                },
                onBack = { navController.popBackStack() },
            )
        }

        composable(TvRoute.Login.route) {
            TvLoginScreen(
                onLoginSuccess = {
                    navController.navigate(TvRoute.ProfileSelection.route) {
                        popUpTo(TvRoute.Login.route) { inclusive = true }
                    }
                },
            )
        }

        composable(TvRoute.ProfileSelection.route) {
            TvProfileSelectionScreen(
                onProfileSelected = {
                    navController.navigate(TvRoute.Main.route) {
                        popUpTo(TvRoute.ProfileSelection.route) { inclusive = true }
                    }
                },
            )
        }

        composable(TvRoute.Main.route) {
            TvMainShell(
                onOpenItemDetail = { contentId ->
                    navController.navigate(TvRoute.ItemDetail(contentId).route)
                },
                onOpenLibraryCollectionDetail = { libraryId, collectionId, title ->
                    navController.navigate(
                        TvRoute.LibraryCollectionDetail(libraryId, collectionId, title).route,
                    )
                },
                onOpenCollectionDetail = { collectionId, title ->
                    navController.navigate(TvRoute.CollectionDetail(collectionId, title).route)
                },
                onNavigateToAdmin = {
                    navController.navigate(TvRoute.Admin.route)
                },
                onSignedOut = {
                    navController.navigate(TvRoute.ServerSetup.route) {
                        popUpTo(TvRoute.Main.route) { inclusive = true }
                    }
                },
                onSwitchProfile = {
                    scope.launch {
                        tokenManager.setProfileId(null)
                        tokenManager.setProfileToken(null)
                        navController.navigate(TvRoute.ProfileSelection.route) {
                            popUpTo(TvRoute.Main.route) { inclusive = true }
                        }
                    }
                },
                // Android TV is now multi-server (parity with tvOS). "Switch
                // Server" opens the server list; the user picks an existing
                // saved server or chooses Add to enter a new URL.
                onSwitchServer = {
                    navController.navigate(TvRoute.ServerList.route)
                },
            )
        }

        composable(
            route = TvRoute.ItemDetail.ROUTE,
            arguments = listOf(
                navArgument(TvRoute.ItemDetail.ARG_CONTENT_ID) { type = NavType.StringType },
                navArgument(TvRoute.ItemDetail.ARG_SEASON_NUMBER) {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
            ),
        ) { backStack ->
            val contentId = backStack.arguments
                ?.getString(TvRoute.ItemDetail.ARG_CONTENT_ID)
                .orEmpty()
            val seasonNumber = backStack.arguments
                ?.getString(TvRoute.ItemDetail.ARG_SEASON_NUMBER)
                ?.toIntOrNull()
            TvItemDetailScreen(
                contentId = contentId,
                seasonNumber = seasonNumber,
                // The detail screen's [TvVersionPicker] writes the chosen
                // version's fileId into [TvItemDetailViewModel.selectedFileId];
                // we forward it through the route so the player session
                // actually binds to that version instead of always defaulting
                // to the server's first listed file (which for multi-version
                // titles is often the lower-resolution encode).
                onPlay = { playContentId, fileId ->
                    navController.navigate(TvRoute.Player(playContentId, fileId).route)
                },
                onItemDetail = { itemContentId ->
                    navController.navigate(TvRoute.ItemDetail(itemContentId).route)
                },
                onSeriesClick = { seriesId ->
                    navController.navigate(TvRoute.ItemDetail(seriesId).route)
                },
                onSeasonClick = { seriesId, selectedSeason ->
                    navController.navigate(TvRoute.ItemDetail(seriesId, selectedSeason).route)
                },
                onBack = { navController.popBackStack() },
            )
        }

        composable(
            route = TvRoute.Player.ROUTE,
            arguments = listOf(
                navArgument(TvRoute.Player.ARG_CONTENT_ID) { type = NavType.StringType },
                navArgument(TvRoute.Player.ARG_FILE_ID) {
                    // Keep StringType because the query param is serialized as
                    // a string in [TvRoute.Player] and may be absent; NavType
                    // IntType can't represent "missing". We parse at the edge.
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
            ),
        ) { backStack ->
            val contentId = backStack.arguments
                ?.getString(TvRoute.Player.ARG_CONTENT_ID)
                .orEmpty()
            val preferredFileId = backStack.arguments
                ?.getString(TvRoute.Player.ARG_FILE_ID)
                ?.toIntOrNull()
            TvPlayerScreen(
                contentId = contentId,
                preferredFileId = preferredFileId,
                onExit = { navController.popBackStack() },
            )
        }

        // --- Personal data grids (Favorites/Watchlist/History) and Collections
        // are now nested rail destinations inside TvMainShell; only their
        // immersive detail screens remain at the top level.

        composable(
            route = TvRoute.LibraryCollectionDetail.ROUTE,
            arguments = listOf(
                navArgument(TvRoute.LibraryCollectionDetail.ARG_LIBRARY_ID) { type = NavType.IntType },
                navArgument(TvRoute.LibraryCollectionDetail.ARG_COLLECTION_ID) {
                    type = NavType.StringType
                },
                navArgument(TvRoute.LibraryCollectionDetail.ARG_TITLE) {
                    type = NavType.StringType
                    defaultValue = ""
                },
            ),
        ) { backStack ->
            val libraryId = backStack.arguments
                ?.getInt(TvRoute.LibraryCollectionDetail.ARG_LIBRARY_ID)
                ?: return@composable
            val collectionId = backStack.arguments
                ?.getString(TvRoute.LibraryCollectionDetail.ARG_COLLECTION_ID)
                ?: return@composable
            val title = backStack.arguments
                ?.getString(TvRoute.LibraryCollectionDetail.ARG_TITLE)
                .orEmpty()
            TvLibraryCollectionDetailScreen(
                libraryId = libraryId,
                collectionId = collectionId,
                title = title,
                onItemClick = { contentId ->
                    navController.navigate(TvRoute.ItemDetail(contentId).route)
                },
                onBack = { navController.popBackStack() },
            )
        }

        composable(
            route = TvRoute.CollectionDetail.ROUTE,
            arguments = listOf(
                navArgument(TvRoute.CollectionDetail.ARG_COLLECTION_ID) {
                    type = NavType.StringType
                },
                navArgument(TvRoute.CollectionDetail.ARG_TITLE) {
                    type = NavType.StringType
                    defaultValue = ""
                },
            ),
        ) { backStack ->
            val collectionId = backStack.arguments
                ?.getString(TvRoute.CollectionDetail.ARG_COLLECTION_ID) ?: return@composable
            val title = backStack.arguments
                ?.getString(TvRoute.CollectionDetail.ARG_TITLE).orEmpty()
            TvCollectionDetailScreen(
                collectionId = collectionId,
                title = title,
                onItemClick = { contentId ->
                    navController.navigate(TvRoute.ItemDetail(contentId).route)
                },
                onBack = { navController.popBackStack() },
            )
        }

        // --- Admin dashboard ---

        composable(TvRoute.Admin.route) {
            TvAdminScreen(onBack = { navController.popBackStack() })
        }
    }
}
