package com.continuum.app.tv.ui.shell

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import kotlinx.coroutines.launch
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.continuum.app.common.ui.components.rememberProfileServerUrl
import com.continuum.app.model.navigation.MediaMode
import com.continuum.app.model.navigation.MediaModeCapabilities
import com.continuum.app.model.navigation.tvMediaModeCapabilities
import com.continuum.app.network.ApiResult
import com.continuum.app.repository.AuthRepository
import com.continuum.app.repository.NotificationsRepository
import com.continuum.app.repository.PersonalDataRepository
import com.continuum.app.repository.ProfileRepository
import com.continuum.app.tv.ui.navigation.TvMainRoute
import com.continuum.app.tv.ui.screens.notifications.TvInboxScreen
import com.continuum.app.tv.ui.screens.collections.TvCollectionsScreen
import com.continuum.app.tv.ui.screens.home.TvHomeScreen
import com.continuum.app.tv.ui.screens.libraries.TvLibrariesScreen
import com.continuum.app.tv.ui.screens.personal.TvFavoritesScreen
import com.continuum.app.tv.ui.screens.personal.TvHistoryScreen
import com.continuum.app.tv.ui.screens.personal.TvWatchlistScreen
import com.continuum.app.tv.ui.screens.recommendations.TvRecommendationsScreen
import com.continuum.app.tv.ui.screens.requests.TvMyRequestsScreen
import com.continuum.app.tv.ui.screens.requests.TvRequestsScreen
import com.continuum.app.tv.ui.screens.search.TvSearchScreen
import com.continuum.app.tv.ui.screens.admin.TvAdminScreen
import com.continuum.app.tv.ui.screens.settings.TvSettingsScreen
import org.koin.compose.koinInject

/**
 * Main authenticated TV shell. Mirrors `TVMainTabView` on tvOS: a content
 * `NavHost` with a `TvTopMenuBar` overlay that hosts Search / Home / Libraries
 * / For You + the profile dropdown. Settings, Collections, Favorites,
 * Watchlist, and History are not first-class menu items — they're reachable
 * from the Settings screen (opened via the profile menu) and remain navigable
 * by route inside the same NavHost so deep links keep working.
 */
@Composable
fun TvMainShell(
    onOpenItemDetail: (contentId: String) -> Unit,
    onOpenLibraryCollectionDetail: (libraryId: Int, collectionId: String, title: String) -> Unit,
    onOpenCollectionDetail: (collectionId: String, title: String) -> Unit,
    onSignedOut: () -> Unit,
    onSwitchProfile: () -> Unit,
    onSwitchServer: () -> Unit,
) {
    val nestedNav = rememberNavController()
    val currentEntry by nestedNav.currentBackStackEntryAsState()

    val authRepository: AuthRepository = koinInject()
    val personalDataRepository: PersonalDataRepository = koinInject()
    val profileRepository: ProfileRepository = koinInject()
    val notificationsRepository: NotificationsRepository = koinInject()
    val unreadCount by notificationsRepository.unreadCount.collectAsState()
    val serverUrl = rememberProfileServerUrl()

    val mediaCapabilities by produceState(
        initialValue = MediaModeCapabilities(listOf(MediaMode.Video, MediaMode.Audio)),
        personalDataRepository,
    ) {
        when (val result = personalDataRepository.listUserLibraries()) {
            is ApiResult.Success -> value = result.data.tvMediaModeCapabilities()
            is ApiResult.Error,
            is ApiResult.NetworkError -> Unit
        }
    }
    val visibleDestinations = remember(mediaCapabilities) {
        visibleTvDestinations(mediaCapabilities)
    }
    val currentRoute = currentEntry?.destination?.route ?: firstTvRoute(mediaCapabilities)

    val focusManager = LocalFocusManager.current
    val contentFocusRequester = remember { FocusRequester() }
    val searchInputFocusRequester = remember { FocusRequester() }

    // Counter pattern from tvOS spec §2.5: incrementing this nudges the menu
    // bar to re-request focus on its currently selected button. The bar's
    // `LaunchedEffect(focusRequest, isFocusSuppressed)` reacts.
    var menuFocusRequest by remember { mutableIntStateOf(0) }
    var contentFocusRequest by remember { mutableIntStateOf(0) }
    var isMenuFocused by remember { mutableStateOf(false) }

    var profileMenuOpen by remember { mutableStateOf(false) }

    val accountSnapshot by produceState(
        initialValue = TvAccountState(),
        authRepository,
        profileRepository,
    ) {
        val user = (authRepository.getCurrentUser() as? ApiResult.Success)?.data
        val activeProfile = profileRepository.getActiveProfile()
        value = TvAccountState(
            displayName = activeProfile?.name ?: user?.username ?: "Profile",
            avatar = activeProfile?.avatar,
        )
    }

    val selectedRoot by remember(currentRoute) {
        derivedStateOf { mapRouteToRoot(currentRoute) }
    }

    val navigateToRoute: (String) -> Unit = { route ->
        if (route != currentRoute) {
            nestedNav.navigate(route) {
                popUpTo(nestedNav.graph.startDestinationId) { saveState = true }
                launchSingleTop = true
                restoreState = true
            }
        }
    }

    val moveFocusToContent: (String) -> Unit = { route ->
        profileMenuOpen = false
        if (route == TvMainRoute.Search.route) {
            runCatching { searchInputFocusRequester.requestFocus() }
        } else {
            // Just request focus on the content group. The Box's
            // .focusRestorer() restores to the user's last-focused card
            // (e.g., card 7 of row 3) instead of slamming back to card 0.
            // The previous behavior also bumped `contentFocusRequest++`,
            // which fired LaunchedEffects in each screen that imperatively
            // re-focused index 0 — defeating the restorer. Initial focus
            // when a screen first loads is still handled by each screen's
            // own LaunchedEffect on its first data emission.
            runCatching { contentFocusRequester.requestFocus() }
        }
    }

    val onSelectRoot: (TvRootDestination) -> Unit = { dest ->
        val route = dest.toRoute()
        if (route != currentRoute) {
            navigateToRoute(route)
        }
        moveFocusToContent(route)
    }

    fun closeMenuAnd(action: () -> Unit): () -> Unit = {
        profileMenuOpen = false
        action()
    }

    // Open the notifications inbox: close the profile menu, navigate to the
    // nested inbox route, then move focus into the content area so the D-pad
    // lands on the inbox rather than lingering on the (now-hidden) menu.
    val openInbox: () -> Unit = {
        profileMenuOpen = false
        navigateToRoute(TvMainRoute.Inbox.route)
        moveFocusToContent(TvMainRoute.Inbox.route)
    }

    // Scroll-driven visibility for the top menu bar. Mirrors Apple's
    // `TVTopMenuBar` hide-on-scroll behavior (spec A.1): scrolling content
    // down fades/translates the menu out; scrolling up restores it. The
    // animation lives entirely in `graphicsLayer` so layout doesn't reflow
    // beneath the menu while it transitions.
    val menuVisibility = remember { Animatable(1f) }
    val scrollScope = rememberCoroutineScope()
    val nestedScrollConnection = remember(menuVisibility, scrollScope) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                // available.y < 0 means the user is scrolling content downward
                // (revealing items below the fold) — fade the menu out.
                // available.y > 0 means scrolling upward — fade it in.
                // We don't consume any scroll; the inner LazyColumn handles it fully.
                if (source == NestedScrollSource.UserInput) {
                    val deltaProgress = available.y / 240f
                    val target = (menuVisibility.value + deltaProgress).coerceIn(0f, 1f)
                    scrollScope.launch { menuVisibility.snapTo(target) }
                }
                return Offset.Zero
            }
        }
    }

    LaunchedEffect(currentRoute, visibleDestinations, mediaCapabilities) {
        // Only media-root tabs are eligible for the "tab no longer visible"
        // redirect. Non-tab routes (Settings, Inbox, Favorites, …) map to null
        // and must be left alone — otherwise an audio-only server (no Video
        // tab) would silently eject a user off the Inbox/Settings to Audio
        // when mediaCapabilities updates.
        val selected = mapRouteToRoot(currentRoute) ?: return@LaunchedEffect
        if (!selected.isVisibleIn(visibleDestinations)) {
            navigateToRoute(firstTvRoute(mediaCapabilities))
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        // Content layer — full-bleed, no left rail reserve. Up-arrow inside the
        // content's preview key handler routes focus to the menu when the user
        // is at the top row.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .nestedScroll(nestedScrollConnection)
                .focusRequester(contentFocusRequester)
                .focusRestorer()
                .focusGroup()
                .onPreviewKeyEvent { ev ->
                    when {
                        ev.type == KeyEventType.KeyDown && ev.key == Key.DirectionUp -> {
                            // Try to move focus up inside content; if that
                            // fails (we're already on the top row), hand
                            // focus to the menu bar.
                            val moved = focusManager.moveFocus(FocusDirection.Up)
                            if (!moved) {
                                menuFocusRequest++
                            }
                            !moved
                        }
                        ev.type == KeyEventType.KeyUp &&
                                (ev.key == Key.Back || ev.key == Key.Escape) -> {
                            when {
                                profileMenuOpen -> {
                                    profileMenuOpen = false
                                    menuFocusRequest++
                                    true
                                }
                                isMenuFocused -> {
                                    moveFocusToContent(currentRoute)
                                    true
                                }
                                // Pop within the inner NavHost when there's
                                // history to pop. Because navigateToRoute
                                // uses popUpTo(start) { saveState }, the
                                // back stack stays flat — typically [Home,
                                // currentTab] — so this pops the current
                                // tab back to Home. popBackStack() goes
                                // through the standard Navigation Compose
                                // path so saved state (scroll, ViewModel)
                                // is restored cleanly instead of triggering
                                // a fresh navigate() like the old code did.
                                nestedNav.previousBackStackEntry != null -> {
                                    nestedNav.popBackStack()
                                    true
                                }
                                // No inner history. Fall through so the
                                // activity's OnBackPressedDispatcher can
                                // finish the activity (default Android Back
                                // behavior at root).
                                else -> false
                            }
                        }
                        else -> false
                    }
                },
        ) {
            NavHost(
                navController = nestedNav,
                startDestination = firstTvRoute(mediaCapabilities),
                modifier = Modifier.fillMaxSize(),
            ) {
                composable(TvMainRoute.Video.route) {
                    TvHomeScreen(
                        onItemClick = onOpenItemDetail,
                        onInitialContentFocus = { profileMenuOpen = false },
                        focusRequest = contentFocusRequest,
                    )
                }
                composable(TvMainRoute.Home.route) {
                    TvHomeScreen(
                        onItemClick = onOpenItemDetail,
                        onInitialContentFocus = { profileMenuOpen = false },
                        focusRequest = contentFocusRequest,
                    )
                }
                composable(TvMainRoute.Search.route) {
                    TvSearchScreen(
                        onItemClick = onOpenItemDetail,
                        searchFieldFocusRequester = searchInputFocusRequester,
                    )
                }
                composable(TvMainRoute.Audio.route) {
                    TvLibrariesScreen(
                        onItemClick = onOpenItemDetail,
                        onLibraryCollectionClick = onOpenLibraryCollectionDetail,
                        onInitialContentFocus = { profileMenuOpen = false },
                    )
                }
                composable(TvMainRoute.Libraries.route) {
                    TvLibrariesScreen(
                        onItemClick = onOpenItemDetail,
                        onLibraryCollectionClick = onOpenLibraryCollectionDetail,
                        onInitialContentFocus = { profileMenuOpen = false },
                    )
                }
                composable(TvMainRoute.ForYou.route) {
                    TvRecommendationsScreen(
                        onItemClick = onOpenItemDetail,
                        onInitialContentFocus = { profileMenuOpen = false },
                    )
                }
                composable(TvMainRoute.Requests.route) {
                    TvRequestsScreen(
                        onOpenLibraryItem = onOpenItemDetail,
                        onOpenMyRequests = { navigateToRoute(TvMainRoute.MyRequests.route) },
                        onInitialContentFocus = { profileMenuOpen = false },
                    )
                }
                composable(TvMainRoute.MyRequests.route) {
                    TvMyRequestsScreen(
                        onOpenLibraryItem = onOpenItemDetail,
                        onInitialContentFocus = { profileMenuOpen = false },
                    )
                }
                composable(TvMainRoute.Collections.route) {
                    TvCollectionsScreen(
                        onCollectionClick = onOpenCollectionDetail,
                        onInitialContentFocus = { profileMenuOpen = false },
                    )
                }
                composable(TvMainRoute.Watchlist.route) {
                    TvWatchlistScreen(
                        onItemClick = onOpenItemDetail,
                        onInitialContentFocus = { profileMenuOpen = false },
                    )
                }
                composable(TvMainRoute.Favorites.route) {
                    TvFavoritesScreen(
                        onItemClick = onOpenItemDetail,
                        onInitialContentFocus = { profileMenuOpen = false },
                    )
                }
                composable(TvMainRoute.History.route) {
                    TvHistoryScreen(
                        onItemClick = onOpenItemDetail,
                        onInitialContentFocus = { profileMenuOpen = false },
                    )
                }
                composable(TvMainRoute.Settings.route) {
                    TvSettingsScreen(
                        onNavigateToFavorites = { navigateToRoute(TvMainRoute.Favorites.route) },
                        onNavigateToWatchlist = { navigateToRoute(TvMainRoute.Watchlist.route) },
                        onNavigateToHistory = { navigateToRoute(TvMainRoute.History.route) },
                        onNavigateToCollections = { navigateToRoute(TvMainRoute.Collections.route) },
                        onNavigateToAdmin = {
                            navigateToRoute(TvMainRoute.Admin.route)
                            moveFocusToContent(TvMainRoute.Admin.route)
                        },
                        onSignedOut = onSignedOut,
                        onSwitchProfile = onSwitchProfile,
                        onInitialContentFocus = { profileMenuOpen = false },
                    )
                }
                composable(TvMainRoute.Admin.route) {
                    TvAdminScreen(
                        onBack = {
                            if (nestedNav.previousBackStackEntry != null) {
                                nestedNav.popBackStack()
                            }
                        },
                    )
                }
                composable(TvMainRoute.Inbox.route) {
                    TvInboxScreen(
                        onOpenItemDetail = onOpenItemDetail,
                        onBack = {
                            if (nestedNav.previousBackStackEntry != null) {
                                nestedNav.popBackStack()
                            }
                        },
                    )
                }
            }
        }

        // Menu overlay — sits on top, gradient scrim fades into content.
        TvTopMenuBar(
            selectedRoot = selectedRoot,
            destinations = visibleDestinations,
            accountState = accountSnapshot,
            unreadCount = unreadCount,
            onSelectRoot = onSelectRoot,
            onProfileClick = { profileMenuOpen = !profileMenuOpen },
            onMoveDown = { moveFocusToContent(currentRoute) },
            isMenuFocused = isMenuFocused,
            onMenuFocusChange = { isMenuFocused = it },
            isFocusSuppressed = profileMenuOpen,
            focusRequest = menuFocusRequest,
            visibility = menuVisibility.value,
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopStart)
                .zIndex(1f),
        )

        if (profileMenuOpen) {
            TvProfileActionsPanel(
                onNotifications = openInbox,
                onSettings = closeMenuAnd {
                    navigateToRoute(TvMainRoute.Settings.route)
                    moveFocusToContent(TvMainRoute.Settings.route)
                },
                onSwitchProfile = closeMenuAnd(onSwitchProfile),
                onSwitchServer = closeMenuAnd(onSwitchServer),
                onSignOut = closeMenuAnd(onSignedOut),
                onDismiss = {
                    profileMenuOpen = false
                    menuFocusRequest++
                },
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(
                        top = TvTopMenuLayout.profileMenuTopInset,
                        start = TvTopMenuLayout.leadingInset,
                    )
                    .zIndex(2f),
            )
        }
    }
}

/**
 * Maps an in-app route string to the corresponding top-menu destination, or
 * `null` when the route is not one of the media-root tabs. Non-tab routes
 * (Settings, Collections, Favorites, Watchlist, History, Inbox, ForYou, …) are
 * legitimately navigable destinations reached from the profile menu / detail
 * flows; they must not be treated as the Video tab. Returning `null` keeps the
 * top bar from highlighting any tab and tells the redirect effect to leave the
 * user where they are instead of ejecting them to the first visible tab.
 */
private fun mapRouteToRoot(route: String): TvRootDestination? = when (route) {
    TvMainRoute.Search.route -> TvRootDestination.Search
    TvMainRoute.Video.route,
    TvMainRoute.Home.route -> TvRootDestination.Video
    TvMainRoute.Audio.route,
    TvMainRoute.Libraries.route -> TvRootDestination.Audio
    TvMainRoute.Requests.route,
    TvMainRoute.MyRequests.route -> TvRootDestination.Requests
    else -> null
}

private fun TvRootDestination.toRoute(): String = when (this) {
    TvRootDestination.Search -> TvMainRoute.Search.route
    TvRootDestination.Video -> TvMainRoute.Video.route
    TvRootDestination.Audio -> TvMainRoute.Audio.route
    TvRootDestination.Requests -> TvMainRoute.Requests.route
}

/**
 * Profile dropdown — fires from the avatar button on the top menu. tvOS uses
 * `TVProfileActionsPanel` (a dimmed full-screen overlay anchored top-left);
 * we render an anchored card so it floats below the avatar without dimming
 * the entire shell.
 */
@Composable
private fun TvProfileActionsPanel(
    onNotifications: () -> Unit,
    onSettings: () -> Unit,
    onSwitchProfile: () -> Unit,
    onSwitchServer: () -> Unit,
    onSignOut: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val firstFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { firstFocus.requestFocus() } }

    Column(
        modifier = modifier
            .width(160.dp)
            .background(
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(10.dp),
            )
            .padding(vertical = 4.dp)
            .focusGroup()
            .onPreviewKeyEvent { ev ->
                if (ev.type == KeyEventType.KeyUp &&
                    (ev.key == Key.Back || ev.key == Key.Escape)
                ) {
                    onDismiss()
                    true
                } else false
            },
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        ProfileActionRow(label = "Notifications", focusRequester = firstFocus, onClick = onNotifications)
        ProfileActionRow(label = "Switch Profile", onClick = onSwitchProfile)
        ProfileActionRow(label = "Settings", onClick = onSettings)
        ProfileActionRow(label = "Switch Server", onClick = onSwitchServer)
        ProfileActionRow(label = "Sign Out", onClick = onSignOut)
    }
}

@Composable
private fun ProfileActionRow(
    label: String,
    onClick: () -> Unit,
    focusRequester: FocusRequester? = null,
) {
    androidx.tv.material3.Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp)
            .let { if (focusRequester != null) it.focusRequester(focusRequester) else it },
        onClick = onClick,
        shape = androidx.tv.material3.ClickableSurfaceDefaults.shape(RoundedCornerShape(6.dp)),
    ) {
        Text(
            text = label,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 7.dp),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}
