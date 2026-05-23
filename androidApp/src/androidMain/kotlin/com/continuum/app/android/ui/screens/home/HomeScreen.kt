package com.continuum.app.android.ui.screens.home

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.continuum.app.android.ui.components.EmptyStateView
import com.continuum.app.android.ui.components.ErrorView
import com.continuum.app.android.ui.components.HeroBackdropImage
import com.continuum.app.android.ui.components.HeroTintBackground
import com.continuum.app.android.ui.components.LoadingIndicator
import com.continuum.app.android.ui.screens.profiles.ProfileAvatar
import com.continuum.app.android.ui.util.rememberDominantColor
import com.continuum.app.model.profile.Profile
import com.continuum.app.model.section.splitFeatured
import com.continuum.app.viewmodel.HomeViewModel

private const val ChromeFadeDistanceDp = 72f

/**
 * Phone Home screen.
 *
 * Mirrors iOS `HomeView.swift` 1:1: a page-level blurred backdrop sampled
 * from the active hero, a tinted gradient that fades into the OLED base
 * behind the section rows, a floating top chrome (avatar + search + menu)
 * that fades in as content scrolls under it, and the featured carousel +
 * section rows themselves. The screen owns its own top inset and ignores
 * the parent Scaffold's status-bar padding so the backdrop reaches behind
 * the status bar.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onItemClick: (String) -> Unit,
    onPlayClick: (String) -> Unit,
    onSeeAllClick: (String) -> Unit,
    viewModel: HomeViewModel,
    activeProfile: Profile?,
    onSearchClick: () -> Unit,
    onPersonalListsClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onSwitchProfileClick: () -> Unit,
    onSwitchServerClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsState()
    val sections = state.sections
    val (featuredSection, regularSections) = remember(sections) {
        sections.splitFeatured().let { it.featured to it.rest }
    }

    var heroBackdropUrl by rememberSaveable { mutableStateOf<String?>(null) }
    var heroBackdropThumbhash by rememberSaveable { mutableStateOf<String?>(null) }

    val listState = rememberLazyListState()
    val density = LocalDensity.current
    val chromeFadePx = remember(density) {
        with(density) { ChromeFadeDistanceDp.dp.toPx() }
    }
    val scrollProgress by remember(chromeFadePx) {
        derivedStateOf {
            if (listState.firstVisibleItemIndex > 0) {
                1f
            } else {
                (listState.firstVisibleItemScrollOffset / chromeFadePx).coerceIn(0f, 1f)
            }
        }
    }

    val heroTint by rememberDominantColor(
        imageUrl = heroBackdropUrl,
        fallback = MaterialTheme.colorScheme.background,
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        // Page-level tint gradient, behind everything.
        HeroTintBackground(tint = heroTint)

        // Blurred backdrop image — full-bleed, ignores top inset, fades to clear.
        HeroBackdropImage(
            url = heroBackdropUrl,
            thumbhash = heroBackdropThumbhash,
        )

        when {
            state.isLoading && sections.isEmpty() -> LoadingIndicator()
            state.error != null && sections.isEmpty() -> ErrorView(
                message = state.error ?: "Something went wrong",
                onRetry = { viewModel.loadSections() },
            )
            sections.isEmpty() -> EmptyStateView(
                title = "No content yet",
                subtitle = "Once your library is scanned, it'll appear here.",
            )
            else -> PullToRefreshBox(
                isRefreshing = state.isRefreshing,
                onRefresh = { viewModel.refresh() },
                modifier = Modifier.fillMaxSize(),
            ) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(24.dp),
                ) {
                    if (featuredSection != null && featuredSection.items.isNotEmpty()) {
                        item(key = "featured") {
                            FeaturedCarousel(
                                items = featuredSection.items,
                                onPlayClick = onPlayClick,
                                onInfoClick = onItemClick,
                                onActiveBackdropChange = { url, thumbhash ->
                                    heroBackdropUrl = url
                                    heroBackdropThumbhash = thumbhash
                                },
                            )
                        }
                    } else {
                        // No hero → reserve runway under the floating header so
                        // the first row doesn't slide under the status bar chrome.
                        item(key = "noFeatured") {
                            Spacer(
                                modifier = Modifier
                                    .windowInsetsPadding(WindowInsets.statusBars)
                                    .height(72.dp),
                            )
                        }
                    }

                    items(
                        items = regularSections,
                        key = { it.id },
                    ) { section ->
                        HomeSectionRow(
                            section = section,
                            onItemClick = onItemClick,
                            onSeeAllClick = { onSeeAllClick(section.id) },
                            onSetWatched = viewModel::setWatched,
                            onToggleFavorite = viewModel::toggleFavorite,
                            onToggleWatchlist = viewModel::toggleWatchlist,
                            onDismissContinueWatching = { item ->
                                item.progressUpdatedAt?.let { ts ->
                                    viewModel.dismissContinueWatching(item.contentId, ts)
                                }
                            },
                        )
                    }

                    item(key = "bottomPad") {
                        Spacer(modifier = Modifier.height(80.dp))
                    }
                }
            }
        }

        // Floating top chrome — fades in as the user scrolls under it.
        HomeFloatingChrome(
            scrollProgress = scrollProgress,
            activeProfile = activeProfile,
            onSearchClick = onSearchClick,
            onPersonalListsClick = onPersonalListsClick,
            onSettingsClick = onSettingsClick,
            onSwitchProfileClick = onSwitchProfileClick,
            onSwitchServerClick = onSwitchServerClick,
        )
    }
}

@Composable
private fun HomeFloatingChrome(
    scrollProgress: Float,
    activeProfile: Profile?,
    onSearchClick: () -> Unit,
    onPersonalListsClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onSwitchProfileClick: () -> Unit,
    onSwitchServerClick: () -> Unit,
) {
    val statusBarPadding = WindowInsets.statusBars.asPaddingValues()
    val animatedFill by animateFloatAsState(
        targetValue = scrollProgress,
        label = "chromeFill",
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    0.0f to MaterialTheme.colorScheme.background.copy(alpha = 0.32f * animatedFill),
                    1.0f to MaterialTheme.colorScheme.background.copy(alpha = 0f),
                ),
            )
            .padding(
                top = statusBarPadding.calculateTopPadding() + 6.dp,
                start = 16.dp,
                end = 16.dp,
                bottom = 8.dp,
            ),
    ) {
        androidx.compose.foundation.layout.Row(
            modifier = Modifier.align(Alignment.CenterEnd),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            HomeChromeButton(onClick = onSearchClick) {
                Icon(
                    imageVector = Icons.Outlined.Search,
                    contentDescription = "Search",
                )
            }

            HomeProfileMenu(
                activeProfile = activeProfile,
                onPersonalListsClick = onPersonalListsClick,
                onSettingsClick = onSettingsClick,
                onSwitchProfileClick = onSwitchProfileClick,
                onSwitchServerClick = onSwitchServerClick,
            )
        }
    }
}

@Composable
private fun HomeChromeButton(
    onClick: () -> Unit,
    content: @Composable androidx.compose.foundation.layout.BoxScope.() -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f),
        contentColor = MaterialTheme.colorScheme.onSurface,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        border = androidx.compose.foundation.BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outline,
        ),
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .padding(4.dp),
            contentAlignment = Alignment.Center,
            content = content,
        )
    }
}

@Composable
private fun HomeProfileMenu(
    activeProfile: Profile?,
    onPersonalListsClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onSwitchProfileClick: () -> Unit,
    onSwitchServerClick: () -> Unit,
) {
    var menuExpanded by rememberSaveable { mutableStateOf(false) }
    Box {
        HomeChromeButton(onClick = { menuExpanded = true }) {
            if (activeProfile != null) {
                ProfileAvatar(
                    avatar = activeProfile.avatar,
                    name = activeProfile.name,
                    size = 32.dp,
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Person,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        DropdownMenu(
            expanded = menuExpanded,
            onDismissRequest = { menuExpanded = false },
        ) {
            DropdownMenuItem(
                text = { Text("Favorites & Watchlist") },
                onClick = {
                    menuExpanded = false
                    onPersonalListsClick()
                },
            )
            DropdownMenuItem(
                text = { Text("Settings") },
                onClick = {
                    menuExpanded = false
                    onSettingsClick()
                },
            )
            DropdownMenuItem(
                text = { Text("Switch Profile") },
                onClick = {
                    menuExpanded = false
                    onSwitchProfileClick()
                },
            )
            DropdownMenuItem(
                text = { Text("Switch Server") },
                onClick = {
                    menuExpanded = false
                    onSwitchServerClick()
                },
            )
        }
    }
}
