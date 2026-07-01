package org.siloserver.silo.android.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.font.FontWeight
import androidx.navigation.NavHostController
import org.siloserver.silo.android.ui.components.MainAppHeaderBodyHeight
import org.siloserver.silo.android.ui.components.MainAppTopBar
import org.siloserver.silo.android.ui.navigation.SiloBottomNavBar
import org.siloserver.silo.android.ui.navigation.Route
import org.siloserver.silo.android.ui.navigation.Tab
import org.siloserver.silo.android.ui.navigation.fallbackMobileTab
import org.siloserver.silo.android.ui.navigation.scopedLocalDownloadBytes
import org.siloserver.silo.android.ui.navigation.shouldShowDownloadsTab
import org.siloserver.silo.android.ui.navigation.visibleMobileTabs
import org.siloserver.silo.android.ui.screens.calendar.CalendarScreen
import org.siloserver.silo.android.ui.screens.home.HomeScreen
import org.siloserver.silo.android.ui.screens.libraries.LibrariesScreen
import org.siloserver.silo.android.ui.screens.libraries.LibrariesSelectorSheet
import org.siloserver.silo.android.ui.screens.libraries.LibrariesViewModel
import org.siloserver.silo.android.ui.screens.recommendations.RecommendationsScreen
import org.siloserver.silo.model.navigation.MediaMode
import org.siloserver.silo.model.navigation.MediaModeCapabilities
import org.siloserver.silo.model.navigation.mobileMediaModeCapabilities
import org.siloserver.silo.model.feature.CLIENT_REQUESTS_SURFACE_ENABLED
import org.siloserver.silo.network.ApiResult
import org.siloserver.silo.network.ServerRegistry
import org.siloserver.silo.repository.PersonalDataRepository
import org.siloserver.silo.viewmodel.HomeViewModel
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

    // Downloads tab visibility: show whenever EITHER the server says there
    // are records OR we have bytes on disk. The on-disk check is what makes
    // the tab survive airplane mode — `repository.refresh()` returns an
    // empty list when offline, but the downloaded files are still there
    // and we want the user to reach them.
    val personalDataRepository: PersonalDataRepository = koinInject()
    val downloadsRepository: org.siloserver.silo.repository.DownloadsRepository = koinInject()
    val downloadStorage: org.siloserver.silo.common.downloads.DownloadStorage = koinInject()
    val serverRegistry: ServerRegistry = koinInject()
    val activeEntry by serverRegistry.activeEntry.collectAsState()
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
    val activeScopeLocalBytes by produceState(
        initialValue = 0L,
        downloadRecords,
        activeEntry?.id,
        activeEntry?.profileId,
        headerState.activeProfile?.id,
    ) {
        value = scopedLocalDownloadBytes(
            storage = downloadStorage,
            serverId = activeEntry?.id,
            profileId = activeEntry?.profileId ?: headerState.activeProfile?.id,
        )
    }
    val visibleTabs = remember(mediaCapabilities, downloadRecords, activeScopeLocalBytes) {
        val hasAnyDownload = shouldShowDownloadsTab(
            serverRecordCount = downloadRecords.size,
            activeScopeLocalBytes = activeScopeLocalBytes,
        )
        visibleMobileTabs(
            capabilities = mediaCapabilities,
            showDownloads = hasAnyDownload,
        )
    }

    // The shared top bar floats over content; its real height is the status-bar
    // inset plus the fixed bar body. Offset tab content by that so the bar can't
    // overlap content on tall-cutout / large-display devices.
    val headerContentTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() +
        MainAppHeaderBodyHeight

    // If the user is on a tab no longer supported by their libraries (or
    // Downloads disappears), move them to the nearest visible media tab.
    LaunchedEffect(currentTab, visibleTabs) {
        if (currentTab !in visibleTabs) {
            val fallback = fallbackMobileTab(visibleTabs, currentTab) ?: Tab.Home
            navController.navigate(fallback.route) {
                popUpTo(Route.Home.route) { saveState = true }
                launchSingleTop = true
                restoreState = true
            }
        }
    }

    Scaffold(
        bottomBar = {
            SiloBottomNavBar(
                currentTab = currentTab,
                onTabSelected = { tab ->
                    navController.navigate(tab.route) {
                        popUpTo(Route.Home.route) { saveState = true }
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
                    Tab.Home -> {
                        val homeViewModel = koinViewModel<HomeViewModel>()
                        HomeScreen(
                            onItemClick = { contentId ->
                                navController.navigate(Route.ItemDetail(contentId).route)
                            },
                            onPlayClick = { contentId, resumePositionSeconds ->
                                navController.navigate(
                                    Route.Player(
                                        contentId = contentId,
                                        resumePositionSeconds = resumePositionSeconds,
                                    ).route,
                                )
                            },
                            onSeeAllClick = { sectionId ->
                                navController.navigate(Route.Browse().route)
                            },
                            viewModel = homeViewModel,
                            activeProfile = headerState.activeProfile,
                            onSearchClick = { navController.navigate(Route.Search().route) },
                            onPersonalListsClick = { navController.navigate(Route.PersonalLists.route) },
                            onCalendarClick = { navController.navigate(Route.Calendar.route) },
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
                            onPlayClick = { contentId, resumePositionSeconds ->
                                navController.navigate(
                                    Route.Player(
                                        contentId = contentId,
                                        resumePositionSeconds = resumePositionSeconds,
                                    ).route,
                                )
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
                    Tab.ForYou -> {
                        RecommendationsScreen(
                            onItemClick = { contentId ->
                                navController.navigate(Route.ItemDetail(contentId).route)
                            },
                            onWatchlistClick = { navController.navigate(Route.Watchlist.route) },
                            onFavoritesClick = { navController.navigate(Route.Favorites.route) },
                            contentTopPadding = headerContentTop,
                        )
                    }
                    Tab.Calendar -> {
                        CalendarScreen(
                            onBackClick = { navController.popBackStack() },
                            onItemClick = { contentId ->
                                navController.navigate(Route.ItemDetail(contentId).route)
                            },
                            showTopBar = false,
                            contentTopPadding = headerContentTop,
                        )
                    }
                    Tab.Downloads -> {
                        org.siloserver.silo.android.ui.screens.downloads.DownloadsScreen(
                            // Tap on a downloaded row goes directly to the right
                            // player so it works offline (ItemDetail needs the
                            // server and would block with "No internet"). Route
                            // audiobooks to the audiobook player so they get the
                            // audiobook UI + offline resume; everything else uses
                            // the video player's offline-first tryLocalPlayback.
                            onItemClick = { item ->
                                if (item.mediaType == org.siloserver.silo.model.download.DownloadMediaType.Audiobook) {
                                    navController.navigate(
                                        Route.AudiobookPlayer(item.contentId, item.fileId).route,
                                    )
                                } else {
                                    navController.navigate(Route.Player(item.contentId).route)
                                }
                            },
                            onReadEbook = { contentId, fileId ->
                                navController.navigate(Route.BookReader(contentId, fileId).route)
                            },
                            contentTopPadding = headerContentTop,
                        )
                    }
                }
            }

            // Home and Libraries paint their own floating chrome. Calendar,
            // Downloads, and For You use the shared iOS-style top chrome.
            if (currentTab == Tab.Downloads || currentTab == Tab.ForYou || currentTab == Tab.Calendar) {
                val title = when (currentTab) {
                    Tab.Calendar -> "Calendar"
                    Tab.Downloads -> "Downloads"
                    Tab.ForYou -> "For You"
                    else -> null
                }
                MainAppTopBar(
                    activeProfile = headerState.activeProfile,
                    isProfileLoading = headerState.isLoading,
                    onSearchClick = { navController.navigate(Route.Search().route) },
                    onPersonalListsClick = { navController.navigate(Route.PersonalLists.route) },
                    onCalendarClick = if (currentTab == Tab.Calendar) {
                        null
                    } else {
                        { navController.navigate(Route.Calendar.route) }
                    },
                    onRequestsClick = if (CLIENT_REQUESTS_SURFACE_ENABLED) {
                        { navController.navigate(Route.Requests.route) }
                    } else {
                        null
                    },
                    onSettingsClick = { navController.navigate(Route.Settings.route) },
                    onSwitchProfileClick = {
                        navController.navigate(Route.ProfileSelection.route)
                    },
                    onSwitchServerClick = {
                        navController.navigate(Route.ServerList.route)
                    },
                    leadingContent = {
                        if (title != null) {
                            Text(
                                text = title,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        } else {
                            org.siloserver.silo.android.ui.components.SiloWordmark()
                        }
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
