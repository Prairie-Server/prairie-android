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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import com.continuum.app.android.ui.components.MainAppHeaderContentPadding
import com.continuum.app.android.ui.components.MainAppTopBar
import com.continuum.app.android.ui.navigation.ContinuumBottomNavBar
import com.continuum.app.android.ui.navigation.Route
import com.continuum.app.android.ui.navigation.Tab
import com.continuum.app.android.ui.navigation.fallbackMobileTab
import com.continuum.app.android.ui.navigation.visibleMobileTabs
import com.continuum.app.android.ui.screens.libraries.LibrariesScreen
import com.continuum.app.android.ui.screens.libraries.LibrariesSelectorSheet
import com.continuum.app.android.ui.screens.libraries.LibrariesViewModel
import com.continuum.app.android.ui.screens.home.HomeScreen
import com.continuum.app.android.ui.screens.reading.ReadingHubScreen
import com.continuum.app.viewmodel.HomeViewModel
import com.continuum.app.model.navigation.MediaMode
import com.continuum.app.model.navigation.MediaModeCapabilities
import com.continuum.app.model.navigation.mobileMediaModeCapabilities
import com.continuum.app.network.ApiResult
import com.continuum.app.repository.PersonalDataRepository
import org.koin.compose.koinInject
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
    val librariesViewModel = if (currentTab == Tab.Audio) {
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

    // Downloads tab visibility: show whenever EITHER the server says there
    // are records OR we have bytes on disk. The on-disk check is what makes
    // the tab survive airplane mode — `repository.refresh()` returns an
    // empty list when offline, but the downloaded files are still there
    // and we want the user to reach them.
    val personalDataRepository: PersonalDataRepository = koinInject()
    val downloadsRepository: com.continuum.app.repository.DownloadsRepository = koinInject()
    val downloadStorage: com.continuum.app.common.downloads.DownloadStorage = koinInject()
    val mediaCapabilities by produceState(
        initialValue = MediaModeCapabilities(
            listOf(
                MediaMode.Video,
                MediaMode.Audio,
                MediaMode.Reading,
            ),
        ),
        personalDataRepository,
    ) {
        value = when (val result = personalDataRepository.listUserLibraries()) {
            is ApiResult.Success -> result.data.mobileMediaModeCapabilities()
            else -> value
        }
    }
    val downloadRecords by downloadsRepository.records.collectAsState()
    // Re-poll on records change (cheap, walks <filesDir>/downloads).
    val hasLocalDownloads = remember(downloadRecords) { downloadStorage.totalBytesUsed() > 0L }
    val visibleTabs = remember(mediaCapabilities, downloadRecords, hasLocalDownloads) {
        val hasAnyDownload = downloadRecords.isNotEmpty() || hasLocalDownloads
        visibleMobileTabs(
            capabilities = mediaCapabilities,
            showDownloads = hasAnyDownload,
        )
    }

    // If the user is on a tab no longer supported by their libraries (or
    // Downloads disappears), move them to the nearest visible media tab.
    LaunchedEffect(currentTab, visibleTabs) {
        if (currentTab !in visibleTabs) {
            val fallback = fallbackMobileTab(visibleTabs, currentTab) ?: Tab.Video
            navController.navigate(fallback.route) {
                popUpTo(Route.Video.route) { saveState = true }
                launchSingleTop = true
                restoreState = true
            }
        }
    }

    Scaffold(
        bottomBar = {
            ContinuumBottomNavBar(
                currentTab = currentTab,
                onTabSelected = { tab ->
                    navController.navigate(tab.route) {
                        popUpTo(Route.Video.route) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                tabs = visibleTabs,
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
                    Tab.Video -> {
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
                            onSearchClick = { navController.navigate(Route.Search().route) },
                            onPersonalListsClick = { navController.navigate(Route.PersonalLists.route) },
                            onCalendarClick = { navController.navigate(Route.Calendar.route) },
                            onInboxClick = { navController.navigate(Route.Inbox.route) },
                            onSettingsClick = { navController.navigate(Route.Settings.route) },
                            onSwitchProfileClick = {
                                navController.navigate(Route.ProfileSelection.route)
                            },
                            onSwitchServerClick = {
                                navController.navigate(Route.ServerList.route)
                            },
                        )
                    }
                    Tab.Audio -> {
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
                            onSearchClick = { navController.navigate(Route.Search().route) },
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
                    Tab.Reading -> {
                        ReadingHubScreen(
                            onItemClick = { contentId ->
                                navController.navigate(Route.ItemDetail(contentId).route)
                            },
                            onCollectionClick = { collectionId, libraryId ->
                                navController.navigate(Route.CollectionDetail(collectionId, libraryId).route)
                            },
                            activeProfile = headerState.activeProfile,
                            onSearchClick = { navController.navigate(Route.Search("reading").route) },
                            onSettingsClick = { navController.navigate(Route.Settings.route) },
                            onSwitchProfileClick = {
                                navController.navigate(Route.ProfileSelection.route)
                            },
                            onSwitchServerClick = {
                                navController.navigate(Route.ServerList.route)
                            },
                        )
                    }
                    Tab.Downloads -> {
                        com.continuum.app.android.ui.screens.downloads.DownloadsScreen(
                            // Tap on a downloaded row goes directly to the
                            // Player so it works offline. ItemDetail requires
                            // the server (getItemDetail / getWatchDetail) and
                            // would block the user with "No internet" on
                            // airplane mode. PlayerViewModel.tryLocalPlayback
                            // (offline-first) handles the rest.
                            onItemClick = { contentId ->
                                navController.navigate(Route.Player(contentId).route)
                            },
                            onReadEbook = { contentId, fileId ->
                                navController.navigate(Route.BookReader(contentId, fileId).route)
                            },
                            contentTopPadding = MainAppHeaderContentPadding,
                        )
                    }
                }
            }

            // Video, Audio, and Reading paint their own floating chrome.
            // Downloads still uses the global top bar.
            if (currentTab == Tab.Downloads) {
                MainAppTopBar(
                    activeProfile = headerState.activeProfile,
                    isProfileLoading = headerState.isLoading,
                    onSearchClick = { navController.navigate(Route.Search().route) },
                    onPersonalListsClick = { navController.navigate(Route.PersonalLists.route) },
                    onCalendarClick = { navController.navigate(Route.Calendar.route) },
                    onRequestsClick = { navController.navigate(Route.Requests.route) },
                    onInboxClick = { navController.navigate(Route.Inbox.route) },
                    onSettingsClick = { navController.navigate(Route.Settings.route) },
                    onSwitchProfileClick = {
                        navController.navigate(Route.ProfileSelection.route)
                    },
                    onSwitchServerClick = {
                        navController.navigate(Route.ServerList.route)
                    },
                )
            }

            if (currentTab == Tab.Audio && librariesState != null && showLibrarySelector) {
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
