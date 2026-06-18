package com.continuum.app.tv.ui.shell

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
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
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.continuum.app.common.ui.components.rememberProfileServerUrl
import com.continuum.app.model.personal.UserLibrary
import com.continuum.app.network.ApiResult
import com.continuum.app.repository.AuthRepository
import com.continuum.app.repository.NotificationsRepository
import com.continuum.app.repository.PersonalDataRepository
import com.continuum.app.repository.ProfileRepository
import com.continuum.app.tv.data.preferences.TvLibraryScopeStore
import com.continuum.app.tv.ui.components.TvCatalogEmptyState
import com.continuum.app.tv.ui.navigation.TvMainRoute
import com.continuum.app.tv.ui.screens.library.TvLibraryDetailScreen
import com.continuum.app.tv.ui.screens.notifications.TvInboxScreen
import com.continuum.app.tv.ui.screens.admin.TvAdminHubScreen
import com.continuum.app.tv.ui.screens.admin.TvAdminLogsScreen
import com.continuum.app.tv.ui.screens.admin.TvAdminScansScreen
import com.continuum.app.tv.ui.screens.admin.TvAdminScreen
import com.continuum.app.tv.ui.screens.admin.TvAdminSessionsScreen
import com.continuum.app.tv.ui.screens.admin.TvAdminUserEditScreen
import com.continuum.app.tv.ui.screens.admin.TvAdminUsersScreen
import com.continuum.app.tv.ui.screens.browse.TvBrowseScreen
import com.continuum.app.tv.ui.screens.calendar.TvCalendarScreen
import com.continuum.app.tv.ui.screens.collections.TvCollectionsScreen
import com.continuum.app.tv.ui.screens.home.TvHomeScreen
import com.continuum.app.tv.ui.screens.libraries.TvLibrariesScreen
import com.continuum.app.tv.ui.screens.personal.TvFavoritesScreen
import com.continuum.app.tv.ui.screens.personal.TvHistoryScreen
import com.continuum.app.tv.ui.screens.personal.TvWatchlistScreen
import com.continuum.app.tv.ui.screens.recommendations.TvRecommendationsScreen
import com.continuum.app.tv.ui.screens.requests.TvMyRequestsScreen
import com.continuum.app.tv.ui.screens.requests.TvRequestDetailScreen
import com.continuum.app.tv.ui.screens.requests.TvRequestsScreen
import com.continuum.app.tv.ui.screens.search.TvSearchScreen
import com.continuum.app.tv.ui.screens.settings.TvManageSessionsScreen
import com.continuum.app.tv.ui.screens.settings.TvSettingsScreen
import com.continuum.app.tv.ui.util.visibleOnTv
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
    onPairDevice: () -> Unit,
    onPlayItem: (contentId: String, type: String?, resumePositionSeconds: Double?) -> Unit,
) {
    val nestedNav = rememberNavController()
    val currentEntry by nestedNav.currentBackStackEntryAsState()

    val authRepository: AuthRepository = koinInject()
    val personalDataRepository: PersonalDataRepository = koinInject()
    val profileRepository: ProfileRepository = koinInject()
    val notificationsRepository: NotificationsRepository = koinInject()
    val tvLibraryScopeStore: TvLibraryScopeStore = koinInject()
    val unreadCount by notificationsRepository.unreadCount.collectAsState()
    val serverUrl = rememberProfileServerUrl()

    // The raw list of libraries visible to this profile on TV, sorted by the
    // server's sort order (ebook-like libraries filtered out by visibleOnTv).
    // This drives both `visibleRoots` and per-type scope resolution.
    // Gates the snap-to-Home redirect below: while libraries are still loading
    // `visibleRoots` is only Home + Calendar, so a restored/deep-linked
    // `main/movies` route must NOT be treated as "type has no libraries" yet.
    var librariesLoaded by remember { mutableStateOf(false) }
    val libraries by produceState(
        initialValue = emptyList<UserLibrary>(),
        personalDataRepository,
    ) {
        when (val result = personalDataRepository.listUserLibraries()) {
            is ApiResult.Success ->
                value = result.data.visibleOnTv().sortedBy { it.sortOrder }
            is ApiResult.Error,
            is ApiResult.NetworkError -> Unit
        }
        // Mark loaded even on error (we've attempted) so the redirect can run;
        // an empty list then legitimately means "no libraries for this profile".
        librariesLoaded = true
    }

    // Skyline tab set (§3.1): Home + present library-type tabs + Calendar.
    val visibleRoots = remember(libraries) { visibleTvRoots(libraries) }

    // In-session scope/pill selections per library type. Scope selections are
    // also persisted via TvLibraryScopeStore; pill selections are session-only
    // (Stage 4 wires the cascade into these). Persistently composed.
    val scopeSelections: SnapshotStateMap<TvLibraryTabType, Int> = remember { mutableStateMapOf() }
    val pillSelections: SnapshotStateMap<TvLibraryTabType, TvLibraryPill> = remember { mutableStateMapOf() }

    // Resolved active library per type. resolvedLibrary is suspend, so resolve
    // it off-composition in a LaunchedEffect keyed on (libraries, scopeSelections)
    // and publish into this state map. Composition only ever reads the map.
    val resolvedLibraries: SnapshotStateMap<TvLibraryTabType, UserLibrary> =
        remember { mutableStateMapOf() }
    LaunchedEffect(libraries, scopeSelections.toMap()) {
        TvLibraryTabType.entries.forEach { type ->
            val ofType = libraries.filter { type.matches(it) }
            val selectedId = scopeSelections[type]
            val resolved = selectedId?.let { id -> ofType.firstOrNull { it.id == id } }
                ?: tvLibraryScopeStore.resolvedLibrary(type, libraries)
            if (resolved != null) {
                resolvedLibraries[type] = resolved
            } else {
                resolvedLibraries.remove(type)
            }
        }
    }
    val activeLibrary: (TvLibraryTabType) -> UserLibrary? = { type -> resolvedLibraries[type] }

    val currentRoute = currentEntry?.destination?.route ?: firstTvRoute()

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

    // Secondary routes (reached FROM another screen — Settings -> Favorites/
    // Watchlist/History/Collections/Requests, Requests -> MyRequests, profile ->
    // Inbox) push onto the current route instead of flattening to the tab root,
    // so Back returns to the parent screen (e.g. Settings) rather than Home.
    val navigateToSecondary: (String) -> Unit = { route ->
        if (route != currentRoute) {
            nestedNav.navigate(route) {
                launchSingleTop = true
                restoreState = true
            }
        }
    }

    // Parameterized form routes (e.g. AdminUserEdit) must NOT restore a saved
    // entry: all query variants share one destination id, so restoreState could
    // resurrect a stale entry (and its idempotent-loaded ViewModel) with the
    // wrong userId. Always start a fresh entry for these.
    val navigateToForm: (String) -> Unit = { route ->
        nestedNav.navigate(route) {
            launchSingleTop = false
            restoreState = false
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

    // Search is no longer a root tab — it's a trailing icon button. Navigate to
    // the (still-defined) Search route and drop focus into the search field.
    val onSearchPressed: () -> Unit = {
        if (TvMainRoute.Search.route != currentRoute) {
            navigateToRoute(TvMainRoute.Search.route)
        }
        moveFocusToContent(TvMainRoute.Search.route)
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
        navigateToSecondary(TvMainRoute.Inbox.route)
        moveFocusToContent(TvMainRoute.Inbox.route)
    }

    // Open the full Calendar (upcoming releases by week) from the profile menu.
    val openCalendar: () -> Unit = {
        profileMenuOpen = false
        navigateToSecondary(TvMainRoute.Calendar.route)
        moveFocusToContent(TvMainRoute.Calendar.route)
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

    // When focus is handed back to the top menu (Up at the top content row, or
    // closing the profile panel), the scroll-driven fade may have slid the menu
    // off-screen (visibility 0). Snap it back to fully visible first so we don't
    // focus an invisible bar. Guarded on >0 so it never runs on first compose.
    LaunchedEffect(menuFocusRequest) {
        if (menuFocusRequest > 0 && menuVisibility.value < 1f) {
            menuVisibility.animateTo(1f)
        }
    }

    LaunchedEffect(currentRoute, visibleRoots, librariesLoaded) {
        // Wait until libraries have actually loaded — before that `visibleRoots`
        // is just Home + Calendar, and a restored/deep-linked `main/movies` route
        // would be wrongly ejected even though that type exists.
        if (!librariesLoaded) return@LaunchedEffect
        // Only media-root tabs are eligible for the "tab no longer visible"
        // redirect. Non-tab routes (Settings, Inbox, Favorites, Search, …) map
        // to null and must be left alone — otherwise navigating to Settings
        // would silently eject the user back to Home. If the selected root is a
        // LibraryType whose type has no libraries, snap to Home.
        val selected = mapRouteToRoot(currentRoute) ?: return@LaunchedEffect
        if (!selected.isVisibleIn(visibleRoots)) {
            navigateToRoute(firstTvRoute())
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            // Shell-level Back/Escape. Placed on the outer Box (an ancestor of
            // BOTH the content layer and the top menu bar) so it fires no
            // matter which has focus. When the menu is focused, Back returns to
            // content instead of falling through to the activity and exiting.
            .onPreviewKeyEvent { ev ->
                if (ev.type == KeyEventType.KeyUp &&
                    (ev.key == Key.Back || ev.key == Key.Escape)
                ) {
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
                        // Pop within the inner NavHost when there's history to
                        // pop. navigateToRoute uses popUpTo(start) { saveState }
                        // so the back stack stays flat — typically [Home,
                        // currentTab] — and this pops the current tab back to
                        // Home through the standard Navigation Compose path,
                        // restoring saved state (scroll, ViewModel) cleanly.
                        nestedNav.previousBackStackEntry != null -> {
                            nestedNav.popBackStack()
                            true
                        }
                        // No inner history. Fall through so the activity's
                        // OnBackPressedDispatcher finishes the activity (default
                        // Android Back behavior at the root).
                        else -> false
                    }
                } else {
                    false
                }
            },
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
                            // Always consume: we performed the move (or routed
                            // to the menu) ourselves in the preview phase.
                            // Returning !moved let the default focus system run
                            // a second moveFocus(Up), skipping a row.
                            true
                        }
                        // Back/Escape is handled on the OUTER shell Box (below)
                        // so it fires regardless of whether focus is on content
                        // or on the top menu bar (a sibling of this content Box,
                        // not a descendant — so a handler here never sees Back
                        // while the menu is focused).
                        else -> false
                    }
                },
        ) {
            NavHost(
                navController = nestedNav,
                startDestination = firstTvRoute(),
                modifier = Modifier.fillMaxSize(),
            ) {
                composable(TvMainRoute.Video.route) {
                    TvHomeScreen(
                        onItemClick = onOpenItemDetail,
                        onPlayItem = onPlayItem,
                        onSeeAll = {
                            navigateToSecondary(TvMainRoute.Browse.route)
                            moveFocusToContent(TvMainRoute.Browse.route)
                        },
                        onInitialContentFocus = { profileMenuOpen = false },
                        focusRequest = contentFocusRequest,
                    )
                }
                composable(TvMainRoute.Home.route) {
                    TvHomeScreen(
                        onItemClick = onOpenItemDetail,
                        onPlayItem = onPlayItem,
                        onSeeAll = {
                            navigateToSecondary(TvMainRoute.Browse.route)
                            moveFocusToContent(TvMainRoute.Browse.route)
                        },
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
                // Content-type tabs (Skyline §3.1). Each renders the library
                // content scoped to that type's active library. The full-screen
                // picker stays the switch mechanism this stage (TvLibrariesScreen
                // still hosts it for the legacy Libraries route); the cascade
                // selector arrives in Stage 4.
                composable(TvMainRoute.Movies.route) {
                    TvLibraryTypeContent(
                        type = TvLibraryTabType.Movies,
                        library = activeLibrary(TvLibraryTabType.Movies),
                        onItemClick = onOpenItemDetail,
                        onLibraryCollectionClick = onOpenLibraryCollectionDetail,
                        onInitialContentFocus = { profileMenuOpen = false },
                    )
                }
                composable(TvMainRoute.Series.route) {
                    TvLibraryTypeContent(
                        type = TvLibraryTabType.Series,
                        library = activeLibrary(TvLibraryTabType.Series),
                        onItemClick = onOpenItemDetail,
                        onLibraryCollectionClick = onOpenLibraryCollectionDetail,
                        onInitialContentFocus = { profileMenuOpen = false },
                    )
                }
                composable(TvMainRoute.Music.route) {
                    TvLibraryTypeContent(
                        type = TvLibraryTabType.Music,
                        library = activeLibrary(TvLibraryTabType.Music),
                        onItemClick = onOpenItemDetail,
                        onLibraryCollectionClick = onOpenLibraryCollectionDetail,
                        onInitialContentFocus = { profileMenuOpen = false },
                    )
                }
                composable(TvMainRoute.Audiobooks.route) {
                    TvLibraryTypeContent(
                        type = TvLibraryTabType.Audiobooks,
                        library = activeLibrary(TvLibraryTabType.Audiobooks),
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
                        onOpenMyRequests = { navigateToSecondary(TvMainRoute.MyRequests.route) },
                        onOpenRequestDetail = { mt, id ->
                            navigateToSecondary(TvMainRoute.RequestDetail(mt, id).route)
                        },
                        onInitialContentFocus = { profileMenuOpen = false },
                    )
                }
                composable(TvMainRoute.MyRequests.route) {
                    TvMyRequestsScreen(
                        onOpenLibraryItem = onOpenItemDetail,
                        onOpenRequestDetail = { mt, id ->
                            navigateToSecondary(TvMainRoute.RequestDetail(mt, id).route)
                        },
                        onInitialContentFocus = { profileMenuOpen = false },
                    )
                }
                composable(
                    route = TvMainRoute.RequestDetail.ROUTE,
                    arguments = listOf(
                        navArgument(TvMainRoute.RequestDetail.ARG_MEDIA_TYPE) { type = NavType.StringType },
                        navArgument(TvMainRoute.RequestDetail.ARG_TMDB_ID) { type = NavType.IntType },
                    ),
                ) { entry ->
                    TvRequestDetailScreen(
                        mediaType = entry.arguments?.getString(TvMainRoute.RequestDetail.ARG_MEDIA_TYPE).orEmpty(),
                        tmdbId = entry.arguments?.getInt(TvMainRoute.RequestDetail.ARG_TMDB_ID) ?: 0,
                        onBack = { if (nestedNav.previousBackStackEntry != null) nestedNav.popBackStack() },
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
                        onNavigateToFavorites = { navigateToSecondary(TvMainRoute.Favorites.route) },
                        onNavigateToWatchlist = { navigateToSecondary(TvMainRoute.Watchlist.route) },
                        onNavigateToHistory = { navigateToSecondary(TvMainRoute.History.route) },
                        onNavigateToCollections = { navigateToSecondary(TvMainRoute.Collections.route) },
                        onNavigateToBrowse = {
                            navigateToSecondary(TvMainRoute.Browse.route)
                            moveFocusToContent(TvMainRoute.Browse.route)
                        },
                        onNavigateToRequests = {
                            navigateToSecondary(TvMainRoute.Requests.route)
                            moveFocusToContent(TvMainRoute.Requests.route)
                        },
                        onNavigateToAdmin = {
                            navigateToSecondary(TvMainRoute.AdminHub.route)
                            moveFocusToContent(TvMainRoute.AdminHub.route)
                        },
                        onManageSessions = { navigateToSecondary(TvMainRoute.ManageSessions.route) },
                        onPairDevice = onPairDevice,
                        onManageServers = onSwitchServer,
                        onSignedOut = onSignedOut,
                        onSwitchProfile = onSwitchProfile,
                        onInitialContentFocus = { profileMenuOpen = false },
                    )
                }
                composable(TvMainRoute.ManageSessions.route) {
                    TvManageSessionsScreen(onBack = { if (nestedNav.previousBackStackEntry != null) nestedNav.popBackStack() })
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
                composable(TvMainRoute.Calendar.route) {
                    TvCalendarScreen(
                        onOpenItemDetail = onOpenItemDetail,
                        onInitialContentFocus = { profileMenuOpen = false },
                    )
                }
                composable(TvMainRoute.Browse.route) {
                    TvBrowseScreen(
                        onOpenItemDetail = onOpenItemDetail,
                        onInitialContentFocus = { profileMenuOpen = false },
                    )
                }
                composable(TvMainRoute.AdminHub.route) {
                    TvAdminHubScreen(
                        onOpenDashboard = { navigateToSecondary(TvMainRoute.AdminDashboard.route) },
                        onOpenUsers = { navigateToSecondary(TvMainRoute.AdminUsers.route) },
                        onOpenSessions = { navigateToSecondary(TvMainRoute.AdminSessions.route) },
                        onOpenScans = { navigateToSecondary(TvMainRoute.AdminScans.route) },
                        onOpenLogs = { navigateToSecondary(TvMainRoute.AdminLogs.route) },
                        onBack = { if (nestedNav.previousBackStackEntry != null) nestedNav.popBackStack() },
                    )
                }
                composable(TvMainRoute.AdminDashboard.route) {
                    TvAdminScreen(onBack = { if (nestedNav.previousBackStackEntry != null) nestedNav.popBackStack() })
                }
                composable(TvMainRoute.AdminUsers.route) {
                    TvAdminUsersScreen(
                        onBack = { if (nestedNav.previousBackStackEntry != null) nestedNav.popBackStack() },
                        onCreateUser = { navigateToForm(TvMainRoute.AdminUserEdit().route) },
                        onEditUser = { id -> navigateToForm(TvMainRoute.AdminUserEdit(id).route) },
                    )
                }
                composable(
                    route = TvMainRoute.AdminUserEdit.ROUTE,
                    arguments = listOf(
                        navArgument(TvMainRoute.AdminUserEdit.ARG_USER_ID) {
                            type = NavType.StringType
                            nullable = true
                            defaultValue = null
                        },
                    ),
                ) { entry ->
                    val userId = entry.arguments
                        ?.getString(TvMainRoute.AdminUserEdit.ARG_USER_ID)
                        ?.toIntOrNull()
                    TvAdminUserEditScreen(
                        userId = userId,
                        onBack = { if (nestedNav.previousBackStackEntry != null) nestedNav.popBackStack() },
                        onSaved = { if (nestedNav.previousBackStackEntry != null) nestedNav.popBackStack() },
                    )
                }
                composable(TvMainRoute.AdminSessions.route) {
                    TvAdminSessionsScreen(onBack = { if (nestedNav.previousBackStackEntry != null) nestedNav.popBackStack() })
                }
                composable(TvMainRoute.AdminScans.route) {
                    TvAdminScansScreen(onBack = { if (nestedNav.previousBackStackEntry != null) nestedNav.popBackStack() })
                }
                composable(TvMainRoute.AdminLogs.route) {
                    TvAdminLogsScreen(onBack = { if (nestedNav.previousBackStackEntry != null) nestedNav.popBackStack() })
                }
            }
        }

        // Menu overlay — sits on top, gradient scrim fades into content.
        TvTopMenuBar(
            selectedRoot = selectedRoot,
            destinations = visibleRoots,
            accountState = accountSnapshot,
            unreadCount = unreadCount,
            onSelectRoot = onSelectRoot,
            onSearchClick = onSearchPressed,
            onProfileClick = { profileMenuOpen = !profileMenuOpen },
            onMoveDown = { moveFocusToContent(currentRoute) },
            isMenuFocused = isMenuFocused,
            onMenuFocusChange = { isMenuFocused = it },
            isFocusSuppressed = profileMenuOpen,
            focusRequest = menuFocusRequest,
            isSearchActive = currentRoute == TvMainRoute.Search.route,
            visibility = menuVisibility.value,
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopStart)
                .zIndex(1f),
        )

        if (profileMenuOpen) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.58f))
                    .zIndex(1f),
            )
            TvProfileActionsPanel(
                onNotifications = openInbox,
                onCalendar = openCalendar,
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
                    // The profile avatar now leads the *trailing* cluster, so the
                    // menu anchors at the bar's end edge (Stage 3 moved it there).
                    .align(Alignment.TopEnd)
                    .padding(
                        top = TvTopMenuLayout.profileMenuTopInset,
                        end = TvTopMenuLayout.trailingInset,
                    )
                    .zIndex(2f),
            )
        }
    }
}

/**
 * Renders the library content for a content-type tab, scoped to that type's
 * currently-active [library]. Reuses [TvLibraryDetailScreen] as-is (the same
 * surface the Libraries tab shows for a single library). When no active library
 * has resolved yet (libraries still loading, or the type genuinely has none) we
 * show a quiet empty state rather than crashing. The Stage 4 cascade selector
 * will replace the in-screen full-screen picker as the switch mechanism.
 */
@Composable
private fun TvLibraryTypeContent(
    type: TvLibraryTabType,
    library: UserLibrary?,
    onItemClick: (contentId: String) -> Unit,
    onLibraryCollectionClick: (libraryId: Int, collectionId: String, title: String) -> Unit,
    onInitialContentFocus: () -> Unit,
) {
    if (library == null) {
        TvCatalogEmptyState(
            message = "No ${type.title} libraries available for this profile.",
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
        )
        return
    }
    // Key on the library id so switching the active library rebuilds the
    // detail screen (and its keyed ViewModel) cleanly instead of reusing stale
    // state from the previous library.
    key(library.id) {
        TvLibraryDetailScreen(
            libraryId = library.id,
            libraryTitle = library.name,
            libraryType = library.type,
            canSwitchLibrary = false,
            onSwitchLibrary = {},
            onItemClick = onItemClick,
            onCollectionClick = { collectionId, title ->
                onLibraryCollectionClick(library.id, collectionId, title)
            },
            onInitialContentFocus = onInitialContentFocus,
        )
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
    // Video/Audio/Libraries are legacy aliases kept harmless during the nav
    // alignment; Video maps to Home and the others to no specific tab now that
    // content is reached via the per-type tabs.
    TvMainRoute.Video.route,
    TvMainRoute.Home.route -> TvRootDestination.Home
    TvMainRoute.Movies.route -> TvRootDestination.LibraryType(TvLibraryTabType.Movies)
    TvMainRoute.Series.route -> TvRootDestination.LibraryType(TvLibraryTabType.Series)
    TvMainRoute.Music.route -> TvRootDestination.LibraryType(TvLibraryTabType.Music)
    TvMainRoute.Audiobooks.route -> TvRootDestination.LibraryType(TvLibraryTabType.Audiobooks)
    TvMainRoute.Calendar.route -> TvRootDestination.Calendar
    // Search / ForYou are no longer tabs — they map to null so no top tab is
    // highlighted (Search is a trailing icon; ForYou is reached as a Home row).
    // Requests/MyRequests/Settings/Inbox/Audio/Libraries are likewise non-tab.
    else -> null
}

private fun TvRootDestination.toRoute(): String = when (this) {
    TvRootDestination.Home -> TvMainRoute.Home.route
    TvRootDestination.Calendar -> TvMainRoute.Calendar.route
    is TvRootDestination.LibraryType -> when (type) {
        TvLibraryTabType.Movies -> TvMainRoute.Movies.route
        TvLibraryTabType.Series -> TvMainRoute.Series.route
        TvLibraryTabType.Music -> TvMainRoute.Music.route
        TvLibraryTabType.Audiobooks -> TvMainRoute.Audiobooks.route
    }
}

/**
 * Profile dropdown — fires from the avatar button on the top menu. tvOS uses
 * `TVProfileActionsPanel` (a dimmed full-screen overlay anchored top-left);
 * we render the same interaction model as an anchored card with a scrim so
 * content behind the menu cannot visually merge with the actions.
 */
@Composable
private fun TvProfileActionsPanel(
    onNotifications: () -> Unit,
    onCalendar: () -> Unit,
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
            .width(300.dp)
            .background(
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(18.dp),
            )
            .border(
                width = 1.dp,
                color = Color.White.copy(alpha = 0.16f),
                shape = RoundedCornerShape(18.dp),
            )
            .padding(vertical = 12.dp)
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
        ProfileActionRow(label = "Calendar", onClick = onCalendar)
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
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(58.dp)
            .padding(horizontal = 10.dp)
            .let { if (focusRequester != null) it.focusRequester(focusRequester) else it },
        onClick = onClick,
        interactionSource = interactionSource,
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(12.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.onSurface,
            focusedContainerColor = Color.White.copy(alpha = 0.92f),
            focusedContentColor = Color.Black,
            pressedContainerColor = Color.White.copy(alpha = 0.86f),
            pressedContentColor = Color.Black,
        ),
        border = ClickableSurfaceDefaults.border(
            focusedBorder = Border(
                border = BorderStroke(1.5.dp, Color.White.copy(alpha = 0.96f)),
                shape = RoundedCornerShape(12.dp),
            ),
        ),
    ) {
        Text(
            text = label,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 14.dp),
            style = MaterialTheme.typography.titleSmall,
            color = if (isFocused) Color.Black else MaterialTheme.colorScheme.onSurface,
        )
    }
}
