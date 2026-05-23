package com.continuum.app.android.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.background
import androidx.compose.material3.Scaffold
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import com.continuum.app.android.ui.components.MainAppHeaderContentPadding
import com.continuum.app.android.ui.components.MainAppTopBar
import com.continuum.app.android.ui.navigation.ContinuumBottomNavBar
import com.continuum.app.android.ui.navigation.Route
import com.continuum.app.android.ui.navigation.Tab
import com.continuum.app.android.ui.screens.libraries.LibrariesScreen
import com.continuum.app.android.ui.screens.libraries.LibrariesSelectorSheet
import com.continuum.app.android.ui.screens.libraries.LibrariesViewModel
import com.continuum.app.android.ui.screens.home.HomeScreen
import com.continuum.app.viewmodel.HomeViewModel
import com.continuum.app.android.ui.screens.recommendations.RecommendationsScreen
import com.continuum.app.viewmodel.RecommendationsViewModel
import org.koin.compose.viewmodel.koinViewModel

/**
 * Main scaffold that hosts the bottom navigation bar and tab content.
 *
 * Each tab's content is rendered inline with real screen implementations.
 */
@Composable
fun MainScreen(
    navController: NavHostController,
    currentTab: Tab,
) {
    val headerViewModel = koinViewModel<MainHeaderViewModel>()
    val headerState by headerViewModel.uiState.collectAsState()
    val librariesViewModel = if (currentTab == Tab.Libraries) {
        koinViewModel<LibrariesViewModel>()
    } else {
        null
    }
    val librariesState = if (librariesViewModel != null) {
        librariesViewModel.uiState.collectAsState().value
    } else {
        null
    }
    var showLibrarySelector by rememberSaveable(currentTab) { mutableStateOf(false) }

    Scaffold(
        bottomBar = {
            ContinuumBottomNavBar(
                currentTab = currentTab,
                onTabSelected = { tab ->
                    navController.navigate(tab.route) {
                        popUpTo(Route.Home.route) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
            )
        },
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .consumeWindowInsets(padding),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = padding.calculateBottomPadding()),
            ) {
                when (currentTab) {
                    Tab.Home -> {
                        val homeViewModel = koinViewModel<HomeViewModel>()
                        HomeScreen(
                            onItemClick = { contentId ->
                                navController.navigate(Route.ItemDetail(contentId).route)
                            },
                            onPlayClick = { contentId ->
                                navController.navigate(Route.Player(contentId).route)
                            },
                            onSeeAllClick = { sectionId ->
                                navController.navigate(Route.Browse().route)
                            },
                            viewModel = homeViewModel,
                            activeProfile = headerState.activeProfile,
                            onSearchClick = { navController.navigate(Route.Search.route) },
                            onPersonalListsClick = { navController.navigate(Route.PersonalLists.route) },
                            onSettingsClick = { navController.navigate(Route.Settings.route) },
                            onSwitchProfileClick = {
                                navController.navigate(Route.ProfileSelection.route)
                            },
                            onSwitchServerClick = {
                                navController.navigate(Route.ServerList.route)
                            },
                        )
                    }
                    Tab.Libraries -> {
                        LibrariesScreen(
                            onItemClick = { contentId ->
                                navController.navigate(Route.ItemDetail(contentId).route)
                            },
                            onPlayClick = { contentId ->
                                navController.navigate(Route.Player(contentId).route)
                            },
                            onCollectionClick = { collectionId, libraryId ->
                                navController.navigate(Route.CollectionDetail(collectionId, libraryId).route)
                            },
                            viewModel = requireNotNull(librariesViewModel),
                            activeProfile = headerState.activeProfile,
                            onLibrarySelectorClick = { showLibrarySelector = true },
                            onSearchClick = { navController.navigate(Route.Search.route) },
                            onPersonalListsClick = { navController.navigate(Route.PersonalLists.route) },
                            onSettingsClick = { navController.navigate(Route.Settings.route) },
                            onSwitchProfileClick = {
                                navController.navigate(Route.ProfileSelection.route)
                            },
                            onSwitchServerClick = {
                                navController.navigate(Route.ServerList.route)
                            },
                        )
                    }
                    Tab.Recommendations -> {
                        val recommendationsViewModel = koinViewModel<RecommendationsViewModel>()
                        RecommendationsScreen(
                            onItemClick = { contentId ->
                                navController.navigate(Route.ItemDetail(contentId).route)
                            },
                            contentTopPadding = MainAppHeaderContentPadding,
                            viewModel = recommendationsViewModel,
                        )
                    }
                }
            }

            // Home and Libraries tabs paint their own iOS-style floating chrome
            // (HomeScreen.kt / LibrariesScreen.kt); skip the global
            // MainAppTopBar in those cases to avoid stacking two floating
            // headers on top of one another.
            if (currentTab != Tab.Home && currentTab != Tab.Libraries) {
                MainAppTopBar(
                    activeProfile = headerState.activeProfile,
                    isProfileLoading = headerState.isLoading,
                    onSearchClick = { navController.navigate(Route.Search.route) },
                    onPersonalListsClick = { navController.navigate(Route.PersonalLists.route) },
                    onSettingsClick = { navController.navigate(Route.Settings.route) },
                    onSwitchProfileClick = {
                        navController.navigate(Route.ProfileSelection.route)
                    },
                    onSwitchServerClick = {
                        navController.navigate(Route.ServerList.route)
                    },
                )
            }

            if (currentTab == Tab.Libraries && librariesState != null && showLibrarySelector) {
                LibrariesSelectorSheet(
                    libraries = librariesState.libraries,
                    selectedLibraryId = librariesState.selectedLibraryId,
                    onSelectLibrary = { libraryId ->
                        showLibrarySelector = false
                        librariesViewModel?.selectLibrary(libraryId)
                    },
                    onDismiss = { showLibrarySelector = false },
                )
            }
        }
    }
}
