package com.continuum.app.tv.ui.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import com.continuum.app.model.section.ResolvedSection
import com.continuum.app.model.section.SectionItem
import com.continuum.app.model.section.splitFeatured
import com.continuum.app.tv.ui.components.LocalAmbientBackdropTint
import com.continuum.app.tv.ui.components.TvErrorScreen
import com.continuum.app.tv.ui.components.TvHomeHeroCarousel
import com.continuum.app.tv.ui.components.TvLoadingScreen
import com.continuum.app.tv.ui.components.TvMediaRow
import com.continuum.app.tv.ui.components.TvRootHeroBackdrop
import com.continuum.app.tv.ui.components.TvRowStyle
import com.continuum.app.tv.ui.components.rememberAmbientBackdropTintState
import com.continuum.app.tv.ui.shell.TvTopMenuLayout
import com.continuum.app.tv.ui.theme.HeroDimens
import com.continuum.app.tv.ui.theme.Spacing
import com.continuum.app.tv.ui.util.visibleOnTv
import com.continuum.app.viewmodel.HomeViewModel
import kotlinx.coroutines.delay
import org.koin.compose.viewmodel.koinViewModel

/**
 * Android TV Home screen — mirrors the tvOS `HomeView` + `TVRootHeroBackdrop`
 * pattern. A page-level blurred backdrop pulled from the first featured
 * section's hero artwork bleeds behind the content; a featured carousel renders
 * above regular horizontal section rows when the server provides one.
 *
 * Layout numbers track Section 3.1 of `.android-parity/specs/ios-tv.md`:
 * 200×300dp posters via [TvMediaRow], 40dp column spacing, 60dp row spacing,
 * the menu band reserves [TvTopMenuLayout.contentTopInset] at the top only when
 * there is no featured carousel to draw behind the floating menu.
 */
@Composable
fun TvHomeScreen(
    onItemClick: (contentId: String) -> Unit,
    onInitialContentFocus: () -> Unit = {},
    focusRequest: Int = 0,
    viewModel: HomeViewModel = koinViewModel(),
    upcomingViewModel: TvUpcomingViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val upcomingItems by upcomingViewModel.items.collectAsState()
    val visibleSections = remember(state.sections) { state.sections.visibleOnTv() }

    when {
        state.isLoading && state.sections.isEmpty() -> TvLoadingScreen(
            modifier = Modifier.background(MaterialTheme.colorScheme.background),
        )
        state.error != null && state.sections.isEmpty() -> TvErrorScreen(
            message = state.error!!,
            onRetry = viewModel::loadSections,
            modifier = Modifier.background(MaterialTheme.colorScheme.background),
        )
        else -> TvHomeContent(
            sections = visibleSections,
            upcomingItems = upcomingItems,
            onItemClick = onItemClick,
            onInitialContentFocus = onInitialContentFocus,
            focusRequest = focusRequest,
            onSetWatched = viewModel::setWatched,
            onToggleFavorite = viewModel::toggleFavorite,
            onToggleWatchlist = viewModel::toggleWatchlist,
            onDismissContinueWatching = viewModel::dismissContinueWatching,
        )
    }
}

@Composable
private fun TvHomeContent(
    sections: List<ResolvedSection>,
    upcomingItems: List<SectionItem> = emptyList(),
    onItemClick: (String) -> Unit,
    onInitialContentFocus: () -> Unit,
    focusRequest: Int,
    onSetWatched: (String, Boolean) -> Unit = { _, _ -> },
    onToggleFavorite: (String, Boolean) -> Unit = { _, _ -> },
    onToggleWatchlist: (String, Boolean) -> Unit = { _, _ -> },
    onDismissContinueWatching: (String, String) -> Unit = { _, _ -> },
) {
    val (featuredSection, restSections) = sections.splitFeatured().let { it.featured to it.rest }
    val rows = restSections.filter { it.items.isNotEmpty() }

    val tintState = rememberAmbientBackdropTintState()
    var activeHeroItem by remember(featuredSection?.id) {
        mutableStateOf(featuredSection?.items?.firstOrNull())
    }
    // Seed the tint state with the initial featured item on (re)entry; also
    // re-emits when the carousel advances via onActiveItemChanged below.
    LaunchedEffect(activeHeroItem?.contentId) {
        tintState.set(activeHeroItem)
    }

    val heroFocusRequester = remember { FocusRequester() }
    val firstRowFocusRequester = remember { FocusRequester() }
    var initialFocusRequested by remember { mutableStateOf(false) }
    var firstRowFocusRequest by remember { mutableIntStateOf(0) }

    val firstRowId = rows.firstOrNull()?.id
    fun requestFirstRowFocus(): Boolean {
        if (firstRowId == null) return false
        firstRowFocusRequest++
        onInitialContentFocus()
        return true
    }

    LaunchedEffect(firstRowId, featuredSection?.id) {
        if (initialFocusRequested || featuredSection != null || firstRowId == null) return@LaunchedEffect
        delay(120)
        requestFirstRowFocus()
        initialFocusRequested = true
    }

    LaunchedEffect(focusRequest, firstRowId, featuredSection?.id) {
        if (focusRequest == 0 || featuredSection != null || firstRowId == null) return@LaunchedEffect
        requestFirstRowFocus()
    }

    CompositionLocalProvider(LocalAmbientBackdropTint provides tintState) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
        ) {
            TvRootHeroBackdrop(
                item = activeHeroItem,
                modifier = Modifier.fillMaxWidth(),
            )

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(Spacing.sectionSpacing),
                contentPadding = PaddingValues(
                    top = if (featuredSection == null) TvTopMenuLayout.contentTopInset else 0.dp,
                    bottom = Spacing.xxxl,
                ),
            ) {
                featuredSection?.let { section ->
                    item(key = "featured:${section.id}") {
                        TvHomeHeroCarousel(
                            items = section.items,
                            onItemClick = onItemClick,
                            heroHeight = HeroDimens.HomeHeight,
                            autoFocus = !initialFocusRequested,
                            focusRequest = focusRequest,
                            initialFocusRequester = heroFocusRequester,
                            // Only wire `down` when a first row actually
                            // exists to attach the requester — otherwise the
                            // focus system would resolve Down to an unattached
                            // FocusRequester and crash. When rows exist the
                            // imperative onDirectionDown path moves focus, so
                            // this is belt-and-suspenders either way.
                            downFocusRequester = firstRowFocusRequester
                                .takeIf { firstRowId != null },
                            onDirectionDown = ::requestFirstRowFocus,
                            onAutoFocusClaimed = {
                                initialFocusRequested = true
                                onInitialContentFocus()
                            },
                            onFocusEntered = onInitialContentFocus,
                            onActiveItemChanged = { item ->
                                activeHeroItem = item
                                tintState.set(item)
                            },
                        )
                    }
                }

                items(rows, key = ResolvedSection::id) { section ->
                    val isProgressRow = section.isProgressRow()
                    val isFirstRow = section.id == firstRowId
                    TvMediaRow(
                        title = section.title,
                        items = section.items,
                        onItemClick = onItemClick,
                        showProgress = isProgressRow,
                        style = if (isProgressRow) TvRowStyle.Backdrop else TvRowStyle.Poster,
                        startPadding = Spacing.safeArea,
                        endPadding = Spacing.safeArea,
                        itemSpacing = TvHomeItemSpacing,
                        rowTopPadding = 0.dp,
                        rowBottomPadding = 0.dp,
                        upFocusRequester = heroFocusRequester
                            .takeIf { isFirstRow && featuredSection != null },
                        firstItemFocusRequester = firstRowFocusRequester
                            .takeIf { isFirstRow },
                        firstItemFocusRequest = if (isFirstRow) firstRowFocusRequest else 0,
                        cardActions = { item ->
                            com.continuum.app.tv.ui.components.TvMediaCardActions(
                                onSetWatched = { watched -> onSetWatched(item.contentId, watched) },
                                onToggleFavorite = { fav -> onToggleFavorite(item.contentId, fav) },
                                onToggleWatchlist = { wl -> onToggleWatchlist(item.contentId, wl) },
                                onRemoveFromContinueWatching = if (isProgressRow && item.progressUpdatedAt != null) {
                                    {
                                        item.progressUpdatedAt?.let { ts ->
                                            onDismissContinueWatching(item.contentId, ts)
                                        }
                                    }
                                } else null,
                            )
                        },
                    )
                }

                // Client-side calendar row — appended after all server sections.
                if (upcomingItems.isNotEmpty()) {
                    item(key = "upcoming-week") {
                        TvMediaRow(
                            title = "Coming this week",
                            items = upcomingItems,
                            onItemClick = onItemClick,
                            startPadding = Spacing.safeArea,
                            endPadding = Spacing.safeArea,
                            itemSpacing = TvHomeItemSpacing,
                            rowTopPadding = 0.dp,
                            rowBottomPadding = 0.dp,
                        )
                    }
                }
            }
        }
    }
}

/** Spec 3.1 — 40dp between cards, 60dp between rows. */
private val TvHomeItemSpacing = 20.dp

private fun ResolvedSection.isProgressRow(): Boolean {
    val type = sectionType.lowercase()
    return type.contains("continue") ||
        type.contains("in_progress") ||
        type.contains("next_up") ||
        type.contains("up_next")
}
