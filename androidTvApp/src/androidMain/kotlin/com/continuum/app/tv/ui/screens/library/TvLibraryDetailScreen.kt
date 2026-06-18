package com.continuum.app.tv.ui.screens.library

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.LocalMovies
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Border
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.continuum.app.common.ui.components.ThumbhashImage
import com.continuum.app.model.section.LibraryCollection
import com.continuum.app.model.section.ResolvedSection
import com.continuum.app.model.section.SectionItem
import com.continuum.app.model.section.splitFeatured
import com.continuum.app.tv.ui.components.TvAlphabetRail
import com.continuum.app.tv.ui.components.TvCardWidth
import com.continuum.app.tv.ui.components.TvCatalogEmptyState
import com.continuum.app.tv.ui.components.TvErrorScreen
import com.continuum.app.tv.ui.components.TvFilterSheet
import com.continuum.app.tv.ui.components.TvHomeHeroCarousel
import com.continuum.app.tv.ui.components.TvMediaCard
import com.continuum.app.tv.ui.components.TvMediaRow
import com.continuum.app.tv.ui.components.TvRootHeroBackdrop
import com.continuum.app.tv.ui.components.TvRowStyle
import com.continuum.app.tv.ui.shell.TvTopMenuLayout
import com.continuum.app.tv.ui.theme.HeroDimens
import com.continuum.app.tv.ui.theme.Spacing
import com.continuum.app.tv.ui.theme.SubtleSurface
import java.time.Year
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * Android TV library detail surface — mirrors `TVLibraryGridView` +
 * `TVLibraryCollectionsView` from tvOS. The screen exposes three sections in
 * tvOS order as a focus-driven mode slider: **Recommended** (hero + section
 * rows), **Collections** (grouped 2:3 poster grid), and **Browse** (poster
 * grid with a persistent right-edge A–Z alphabet rail).
 *
 * The big-letter library header (64sp bold title + subtitle) lives at the
 * top of every section so the screen never feels rootless. The in-grid Filter
 * sheet (genre / year / sort) sits below the header on Browse; the A–Z rail
 * ([TvAlphabetRail]) lives in a Row to the right of the Browse grid and jumps
 * the browse name-prefix filter (it replaces the old in-sheet "Jump to" chips).
 *
 * Catalog grid metrics track tvOS `TVCatalogGrid`: 40dp column spacing, 60dp
 * row spacing, load-more within 8 rows of the end. The Browse grid uses 5
 * columns to clear the rail; the Collections grid uses 6.
 */
@Composable
fun TvLibraryDetailScreen(
    libraryId: Int,
    libraryTitle: String,
    libraryType: String,
    canSwitchLibrary: Boolean,
    onSwitchLibrary: () -> Unit,
    onItemClick: (contentId: String) -> Unit,
    onCollectionClick: (collectionId: String, title: String) -> Unit,
    onInitialContentFocus: () -> Unit = {},
    // When the screen is opened from the Skyline cascade with a committed
    // section pill, this drives the initial tab (Recommended / Library /
    // Collections). Null leaves the ViewModel's default (Recommended) and any
    // user-driven tab changes alone.
    initialSection: TvLibraryTab? = null,
    // Monotonic nonce bumped by the host on every cascade commit. Keying the
    // section-apply effect on it (not just initialSection) makes re-committing
    // the SAME pill re-apply the section instead of being a silent no-op.
    sectionRequestNonce: Int = 0,
    viewModel: TvLibraryDetailViewModel = koinViewModel(
        key = "library-$libraryId",
        parameters = { parametersOf(libraryId, libraryTitle, libraryType) },
    ),
) {
    val state by viewModel.uiState.collectAsState()

    // Apply the committed cascade section on entry / whenever the commit
    // changes it. Keyed on sectionRequestNonce (bumped on every commit) AND the
    // section value, so re-committing the SAME pill re-applies the section
    // rather than being a silent no-op, while a non-commit recomposition leaves
    // manual in-screen tab moves untouched.
    LaunchedEffect(sectionRequestNonce, initialSection) {
        initialSection?.let(viewModel::onTabSelected)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        when (state.selectedTab) {
            TvLibraryTab.Recommended -> RecommendedTab(
                state = state,
                canSwitchLibrary = canSwitchLibrary,
                onSwitchLibrary = onSwitchLibrary,
                onTabSelected = viewModel::onTabSelected,
                onItemClick = onItemClick,
                onRetry = viewModel::retryRecommended,
                onInitialContentFocus = onInitialContentFocus,
            )
            TvLibraryTab.Browse -> LibraryTab(
                state = state,
                canSwitchLibrary = canSwitchLibrary,
                onSwitchLibrary = onSwitchLibrary,
                onTabSelected = viewModel::onTabSelected,
                onItemClick = onItemClick,
                onGenreChanged = viewModel::onGenreChanged,
                onSortChanged = viewModel::onSortChanged,
                onNamePrefixChanged = viewModel::onNamePrefixChanged,
                onYearRangeChanged = viewModel::onYearRangeChanged,
                onLoadMore = viewModel::loadMoreBrowse,
                onRetry = viewModel::retryBrowse,
                onInitialContentFocus = onInitialContentFocus,
            )
            TvLibraryTab.Collections -> CollectionsTab(
                state = state,
                canSwitchLibrary = canSwitchLibrary,
                onSwitchLibrary = onSwitchLibrary,
                onTabSelected = viewModel::onTabSelected,
                onCollectionClick = onCollectionClick,
                onRetry = viewModel::retryCollections,
                onInitialContentFocus = onInitialContentFocus,
            )
        }
    }
}

// ============================================================================
// Tab content
// ============================================================================

@Composable
private fun RecommendedTab(
    state: TvLibraryDetailViewModel.UiState,
    canSwitchLibrary: Boolean,
    onSwitchLibrary: () -> Unit,
    onTabSelected: (TvLibraryTab) -> Unit,
    onItemClick: (String) -> Unit,
    onRetry: () -> Unit,
    onInitialContentFocus: () -> Unit,
) {
    val (featuredSection, restSections) = state.sections.splitFeatured().let { it.featured to it.rest }
    val rows = restSections.filter { it.items.isNotEmpty() }

    val heroFocusRequester = remember { FocusRequester() }
    val firstRowFocusRequester = remember { FocusRequester() }
    val tabSliderFocusRequester = remember { FocusRequester() }
    var initialFocusRequested by remember { mutableStateOf(false) }
    var heroItem by remember(featuredSection?.id) {
        mutableStateOf<SectionItem?>(featuredSection?.items?.firstOrNull())
    }

    val firstRowId = rows.firstOrNull()?.id

    LaunchedEffect(firstRowId, featuredSection?.id) {
        if (initialFocusRequested || featuredSection != null) return@LaunchedEffect
        kotlinx.coroutines.delay(120)
        val target = if (firstRowId != null) firstRowFocusRequester else tabSliderFocusRequester
        runCatching { target.requestFocus() }
        onInitialContentFocus()
        initialFocusRequested = true
    }

    Box(
        modifier = Modifier.fillMaxSize(),
    ) {
        if (featuredSection != null) {
            TvRootHeroBackdrop(
                item = heroItem,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(Spacing.sectionSpacing),
            contentPadding = PaddingValues(
                top = if (featuredSection == null) TvTopMenuLayout.contentTopInset else 0.dp,
                bottom = Spacing.xxxl,
            ),
        ) {
            featuredSection?.let { section ->
                item(key = "library-featured:${section.id}") {
                    TvHomeHeroCarousel(
                        items = section.items,
                        onItemClick = onItemClick,
                        // The library hero keeps OK = open detail (no separate
                        // play affordance here); long-press also opens detail.
                        onPlayItem = { onItemClick(it.contentId) },
                        heroHeight = HeroDimens.HomeHeight,
                        autoFocus = !initialFocusRequested,
                        initialFocusRequester = heroFocusRequester,
                        // Only wire `down` when the first row exists to attach
                        // the requester; otherwise Down resolves to an
                        // unattached FocusRequester and crashes.
                        downFocusRequester = firstRowFocusRequester
                            .takeIf { firstRowId != null },
                        // Consume Down only when there's no row to move to;
                        // when a row exists, return false so the focus system
                        // falls through to focusProperties.down (the attached
                        // firstRowFocusRequester). Returning true here used to
                        // swallow Down before focusProperties.down could act,
                        // dead-ending the hero whenever rows existed.
                        onDirectionDown = {
                            firstRowId == null
                        },
                        onAutoFocusClaimed = {
                            initialFocusRequested = true
                            onInitialContentFocus()
                        },
                        onFocusEntered = onInitialContentFocus,
                        onActiveItemChanged = { item -> heroItem = item },
                    )
                }
            }

            item(key = "header") {
                LibraryHeader(
                    title = state.title,
                    libraryType = state.libraryType,
                    subtitle = recommendedSubtitle(state),
                    selectedTab = state.selectedTab,
                    canSwitchLibrary = canSwitchLibrary,
                    onSwitchLibrary = onSwitchLibrary,
                    onTabSelected = onTabSelected,
                    tabSliderFocusRequester = tabSliderFocusRequester,
                )
            }

            when {
                state.recommendedLoading && state.sections.isEmpty() -> {
                    item(key = "loading") { InlineLoadingState() }
                }
                state.recommendedError != null && state.sections.isEmpty() -> {
                    item(key = "error") {
                        TvErrorScreen(
                            message = state.recommendedError,
                            onRetry = onRetry,
                            modifier = Modifier.padding(
                                start = Spacing.safeArea,
                                end = Spacing.safeArea,
                            ),
                        )
                    }
                }
                rows.isEmpty() && featuredSection == null -> {
                    item(key = "empty") {
                        TvCatalogEmptyState(
                            message = "${state.title} is empty.",
                        )
                    }
                }
                else -> {
                    items(rows, key = ResolvedSection::id) { section ->
                        TvMediaRow(
                            title = section.title,
                            items = section.items,
                            onItemClick = onItemClick,
                            showProgress = section.isProgressRow(),
                            style = TvRowStyle.Poster,
                            startPadding = Spacing.safeArea,
                            endPadding = Spacing.safeArea,
                            itemSpacing = LibraryGridColumnSpacing,
                            rowTopPadding = 0.dp,
                            rowBottomPadding = 0.dp,
                            upFocusRequester = heroFocusRequester
                                .takeIf { section.id == firstRowId && featuredSection != null },
                            firstItemFocusRequester = firstRowFocusRequester
                                .takeIf { section.id == firstRowId },
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LibraryTab(
    state: TvLibraryDetailViewModel.UiState,
    canSwitchLibrary: Boolean,
    onSwitchLibrary: () -> Unit,
    onTabSelected: (TvLibraryTab) -> Unit,
    onItemClick: (String) -> Unit,
    onGenreChanged: (String?) -> Unit,
    onSortChanged: (TvLibrarySortOption) -> Unit,
    onNamePrefixChanged: (String?) -> Unit,
    onYearRangeChanged: (Int?, Int?) -> Unit,
    onLoadMore: () -> Unit,
    onRetry: () -> Unit,
    onInitialContentFocus: () -> Unit,
) {
    var showFilterSheet by remember { mutableStateOf(false) }

    val tabSliderFocusRequester = remember { FocusRequester() }
    val firstGridItemFocusRequester = remember { FocusRequester() }
    var initialFocusRequested by remember { mutableStateOf(false) }

    LaunchedEffect(state.browseItems.isNotEmpty()) {
        if (initialFocusRequested) return@LaunchedEffect
        kotlinx.coroutines.delay(120)
        val target = if (state.browseItems.isNotEmpty()) {
            firstGridItemFocusRequester
        } else {
            tabSliderFocusRequester
        }
        runCatching { target.requestFocus() }
        onInitialContentFocus()
        initialFocusRequested = true
    }

    val sortLabel = TvLibrarySortOption.fromWire(state.browseFilter.sort).label

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            if (state.browseError != null && state.browseItems.isEmpty()) {
                LibraryHeader(
                    title = state.title,
                    libraryType = state.libraryType,
                    subtitle = librarySubtitle(state),
                    selectedTab = state.selectedTab,
                    canSwitchLibrary = canSwitchLibrary,
                    onSwitchLibrary = onSwitchLibrary,
                    onTabSelected = onTabSelected,
                    tabSliderFocusRequester = tabSliderFocusRequester,
                    extra = {
                        FilterRow(
                            filter = state.browseFilter,
                            sortLabel = sortLabel,
                            onOpenFilters = { showFilterSheet = true },
                        )
                    },
                    modifier = Modifier.padding(top = TvTopMenuLayout.contentTopInset),
                )
                TvErrorScreen(
                    message = state.browseError,
                    onRetry = onRetry,
                    modifier = Modifier.padding(
                        start = Spacing.safeArea,
                        end = Spacing.safeArea,
                    ),
                )
            } else {
                // Grid + right-edge alphabet rail (tvOS `TVLibraryGridView`):
                // the rail sits in a Row to the RIGHT of the Browse grid and
                // jumps the browse name-prefix filter. The grid drops to 5
                // columns to clear the rail.
                Row(modifier = Modifier.fillMaxSize()) {
                    Box(modifier = Modifier.weight(1f)) {
                        LibraryGrid(
                            state = state,
                            canSwitchLibrary = canSwitchLibrary,
                            onSwitchLibrary = onSwitchLibrary,
                            onTabSelected = onTabSelected,
                            onItemClick = onItemClick,
                            onLoadMore = onLoadMore,
                            sortLabel = sortLabel,
                            onOpenFilters = { showFilterSheet = true },
                            tabSliderFocusRequester = tabSliderFocusRequester,
                            firstItemFocusRequester = firstGridItemFocusRequester,
                        )
                    }
                    TvAlphabetRail(
                        selected = state.browseFilter.namePrefix,
                        onSelect = onNamePrefixChanged,
                        modifier = Modifier.padding(end = Spacing.md),
                    )
                }
            }
        }

        TvFilterSheet(
            visible = showFilterSheet,
            onDismiss = { showFilterSheet = false },
        ) {
            val currentYear = remember { Year.now().value }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(Spacing.lg),
            ) {
                // --- Genre section ---
                FilterSectionHeader("Genre")
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    FilterChoiceChip(
                        label = "All",
                        selected = state.browseFilter.genre == null,
                        onClick = { onGenreChanged(null) },
                    )
                    state.genres.forEach { genre ->
                        FilterChoiceChip(
                            label = genre,
                            selected = state.browseFilter.genre == genre,
                            onClick = { onGenreChanged(genre) },
                        )
                    }
                }

                // --- Year section ---
                FilterSectionHeader("Year")
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    val noYearSelected = state.browseFilter.yearMin == null &&
                        state.browseFilter.yearMax == null
                    FilterChoiceChip(
                        label = "Any",
                        selected = noYearSelected,
                        onClick = { onYearRangeChanged(null, null) },
                    )
                    TvLibraryYearOptions.forCurrentYear(currentYear).forEach { option ->
                        FilterChoiceChip(
                            label = option.label,
                            selected = state.browseFilter.yearMin == option.yearMin &&
                                state.browseFilter.yearMax == option.yearMax,
                            onClick = { onYearRangeChanged(option.yearMin, option.yearMax) },
                        )
                    }
                }

                // --- Sort section ---
                FilterSectionHeader("Sort")
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    TvLibrarySortOption.entries.forEach { option ->
                        FilterChoiceChip(
                            label = option.label,
                            selected = state.browseFilter.sort == option.wireValue,
                            onClick = { onSortChanged(option) },
                        )
                    }
                }

                // The A–Z "Jump to" picker moved out of the sheet onto the
                // persistent right-edge TvAlphabetRail (tvOS parity), so it is
                // intentionally no longer rendered here.
            }
        }
    }
}

@Composable
private fun LibraryGrid(
    state: TvLibraryDetailViewModel.UiState,
    canSwitchLibrary: Boolean,
    onSwitchLibrary: () -> Unit,
    onTabSelected: (TvLibraryTab) -> Unit,
    onItemClick: (String) -> Unit,
    onLoadMore: () -> Unit,
    sortLabel: String,
    onOpenFilters: () -> Unit,
    tabSliderFocusRequester: FocusRequester,
    firstItemFocusRequester: FocusRequester,
) {
    val gridState: LazyGridState = rememberLazyGridState()

    val nearEnd by remember(
        gridState,
        state.browseHasMore,
        state.browseItems.size,
        state.browseLoading,
        state.browseLoadingMore,
    ) {
        derivedStateOf {
            if (!state.browseHasMore || state.browseLoading || state.browseLoadingMore) {
                false
            } else {
                val lastVisible = gridState.layoutInfo.visibleItemsInfo.lastOrNull()
                // Spec 3.2: load-more within 8 rows of end. Grid is 6 columns wide.
                state.browseItems.isNotEmpty() &&
                    lastVisible != null &&
                    lastVisible.index >= state.browseItems.size -
                        (LibraryGridLoadMoreRowsThreshold * LibraryBrowseGridColumns)
            }
        }
    }

    LaunchedEffect(nearEnd) {
        if (nearEnd) onLoadMore()
    }

    // Jump to the top of the result set whenever the A–Z prefix changes, so an
    // alphabet-rail letter-jump actually lands at the start of that prefix's
    // results instead of keeping a deep scroll position from the old set.
    LaunchedEffect(state.browseFilter.namePrefix) {
        gridState.scrollToItem(0)
    }

    LazyVerticalGrid(
        state = gridState,
        columns = GridCells.Fixed(LibraryBrowseGridColumns),
        modifier = Modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.spacedBy(LibraryGridColumnSpacing),
        verticalArrangement = Arrangement.spacedBy(LibraryGridRowSpacing),
        contentPadding = PaddingValues(
            start = Spacing.safeArea,
            top = TvTopMenuLayout.contentTopInset,
            end = Spacing.md,
            bottom = Spacing.xxxl,
        ),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }, key = "header") {
            LibraryHeader(
                title = state.title,
                libraryType = state.libraryType,
                subtitle = librarySubtitle(state),
                selectedTab = state.selectedTab,
                canSwitchLibrary = canSwitchLibrary,
                onSwitchLibrary = onSwitchLibrary,
                onTabSelected = onTabSelected,
                tabSliderFocusRequester = tabSliderFocusRequester,
                extra = {
                    FilterRow(
                        filter = state.browseFilter,
                        sortLabel = sortLabel,
                        onOpenFilters = onOpenFilters,
                    )
                },
                horizontalPadding = 0.dp,
            )
        }

        if (state.browseLoading && state.browseItems.isEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }, key = "loading") {
                InlineLoadingState()
            }
        } else if (state.browseItems.isEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }, key = "empty") {
                TvCatalogEmptyState(message = "No titles match the current filters.")
            }
        } else {
            itemsIndexed(
                state.browseItems,
                key = { _, item -> item.contentId },
            ) { index, item ->
                val (actions, userState) = com.continuum.app.tv.ui.components.rememberTvBrowseItemCardActions(item)
                TvMediaCard(
                    title = item.title,
                    posterUrl = item.posterUrl,
                    posterThumbhash = item.posterThumbhash,
                    year = item.year.takeIf { it > 0 },
                    userState = userState,
                    width = TvCardWidth,
                    fillWidth = true,
                    onClick = { onItemClick(item.contentId) },
                    focusRequester = firstItemFocusRequester.takeIf { index == 0 },
                    modifier = Modifier.fillMaxWidth(),
                    actions = actions,
                )
            }
        }

        if (state.browseLoadingMore) {
            item(span = { GridItemSpan(maxLineSpan) }, key = "loading-more") {
                InlineLoadingState(verticalPadding = 24.dp)
            }
        }
    }
}

@Composable
private fun CollectionsTab(
    state: TvLibraryDetailViewModel.UiState,
    canSwitchLibrary: Boolean,
    onSwitchLibrary: () -> Unit,
    onTabSelected: (TvLibraryTab) -> Unit,
    onCollectionClick: (String, String) -> Unit,
    onRetry: () -> Unit,
    onInitialContentFocus: () -> Unit,
) {
    val tabSliderFocusRequester = remember { FocusRequester() }
    val firstCollectionFocusRequester = remember { FocusRequester() }
    var initialFocusRequested by remember { mutableStateOf(false) }

    LaunchedEffect(state.collections.isNotEmpty()) {
        if (initialFocusRequested) return@LaunchedEffect
        kotlinx.coroutines.delay(120)
        val target = if (state.collections.isNotEmpty()) {
            firstCollectionFocusRequester
        } else {
            tabSliderFocusRequester
        }
        runCatching { target.requestFocus() }
        onInitialContentFocus()
        initialFocusRequested = true
    }

    // First collection of the first non-empty group claims initial focus.
    val firstCollectionId = state.collectionSections
        .firstOrNull { it.collections.isNotEmpty() }
        ?.collections?.firstOrNull()?.id

    LazyVerticalGrid(
        columns = GridCells.Fixed(LibraryGridColumns),
        modifier = Modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.spacedBy(LibraryGridColumnSpacing),
        verticalArrangement = Arrangement.spacedBy(LibraryGridRowSpacing),
        contentPadding = PaddingValues(
            start = Spacing.safeArea,
            top = TvTopMenuLayout.contentTopInset,
            end = Spacing.safeArea,
            bottom = Spacing.xxxl,
        ),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }, key = "header") {
            LibraryHeader(
                title = state.title,
                libraryType = state.libraryType,
                subtitle = collectionsSubtitle(state),
                selectedTab = state.selectedTab,
                canSwitchLibrary = canSwitchLibrary,
                onSwitchLibrary = onSwitchLibrary,
                onTabSelected = onTabSelected,
                tabSliderFocusRequester = tabSliderFocusRequester,
                horizontalPadding = 0.dp,
            )
        }

        when {
            state.collectionsLoading && state.collections.isEmpty() -> {
                item(span = { GridItemSpan(maxLineSpan) }, key = "loading") {
                    InlineLoadingState()
                }
            }
            state.collectionsError != null && state.collections.isEmpty() -> {
                item(span = { GridItemSpan(maxLineSpan) }, key = "error") {
                    TvErrorScreen(message = state.collectionsError, onRetry = onRetry)
                }
            }
            state.collections.isEmpty() -> {
                item(span = { GridItemSpan(maxLineSpan) }, key = "empty") {
                    TvCatalogEmptyState(message = "No collections in this library.")
                }
            }
            // Grouped collections (tvOS `TVLibraryCollectionsView`): a mono
            // uppercase group header, then a grid of 2:3 poster cards. A
            // section with an empty name (flat / ungrouped bucket) renders no
            // header.
            else -> state.collectionSections.forEachIndexed { sectionIndex, section ->
                if (section.collections.isEmpty()) return@forEachIndexed
                if (section.name.isNotEmpty()) {
                    item(
                        span = { GridItemSpan(maxLineSpan) },
                        key = "group-header:$sectionIndex:${section.name}",
                    ) {
                        CollectionsGroupHeader(name = section.name)
                    }
                }
                itemsIndexed(
                    section.collections,
                    key = { _, collection -> "$sectionIndex:${collection.id}" },
                ) { _, collection ->
                    TvCollectionCard(
                        collection = collection,
                        onClick = { onCollectionClick(collection.id, collection.name) },
                        focusRequester = firstCollectionFocusRequester
                            .takeIf { collection.id == firstCollectionId },
                    )
                }
            }
        }
    }
}

/** Mono uppercase group header for the grouped collections grid (tvOS §6.3). */
@Composable
private fun CollectionsGroupHeader(name: String) {
    Text(
        text = name.uppercase(),
        color = Color.White.copy(alpha = 0.38f),
        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
        fontSize = 14.sp,
        letterSpacing = 3.6.sp,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

// ============================================================================
// Header (title + subtitle + tab slider + library badge)
// ============================================================================

@Composable
private fun LibraryHeader(
    title: String,
    libraryType: String,
    subtitle: String,
    selectedTab: TvLibraryTab,
    canSwitchLibrary: Boolean,
    onSwitchLibrary: () -> Unit,
    onTabSelected: (TvLibraryTab) -> Unit,
    tabSliderFocusRequester: FocusRequester,
    extra: (@Composable () -> Unit)? = null,
    modifier: Modifier = Modifier,
    horizontalPadding: androidx.compose.ui.unit.Dp = Spacing.safeArea,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = horizontalPadding),
        verticalArrangement = Arrangement.spacedBy(Spacing.lg),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.lg),
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = title,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 64.sp,
                    lineHeight = 68.sp,
                    letterSpacing = (-1.6).sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.titleSmall,
                    color = Color.White.copy(alpha = 0.6f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            LibrarySwitcherPill(
                title = title,
                type = libraryType,
                canSwitch = canSwitchLibrary,
                onClick = onSwitchLibrary,
            )
        }

        TabSlider(
            selectedTab = selectedTab,
            onTabSelected = onTabSelected,
            focusRequester = tabSliderFocusRequester,
        )

        if (extra != null) extra()
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun TabSlider(
    selectedTab: TvLibraryTab,
    onTabSelected: (TvLibraryTab) -> Unit,
    focusRequester: FocusRequester,
) {
    Row(
        modifier = Modifier
            .background(Color.White.copy(alpha = 0.08f), RoundedCornerShape(999.dp))
            .border(1.dp, Color.White.copy(alpha = 0.06f), RoundedCornerShape(999.dp))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        TvLibraryTab.entries.forEach { tab ->
            TabSliderPill(
                label = tab.label,
                selected = selectedTab == tab,
                onClick = { onTabSelected(tab) },
                focusRequester = focusRequester.takeIf { selectedTab == tab },
            )
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun TabSliderPill(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    focusRequester: FocusRequester?,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    val containerColor = when {
        isFocused -> Color.White.copy(alpha = 0.94f)
        selected -> Color.White.copy(alpha = 0.18f)
        else -> Color.Transparent
    }
    val contentColor = when {
        isFocused -> Color.Black
        selected -> Color.White
        else -> Color.White.copy(alpha = 0.7f)
    }

    Surface(
        onClick = onClick,
        interactionSource = interactionSource,
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(999.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = containerColor,
            contentColor = contentColor,
            focusedContainerColor = Color.White.copy(alpha = 0.94f),
            focusedContentColor = Color.Black,
            pressedContainerColor = Color.White.copy(alpha = 0.94f),
            pressedContentColor = Color.Black,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.025f),
        border = ClickableSurfaceDefaults.border(
            border = Border(
                border = BorderStroke(0.dp, Color.Transparent),
                shape = RoundedCornerShape(999.dp),
            ),
            focusedBorder = Border(
                border = BorderStroke(0.dp, Color.Transparent),
                shape = RoundedCornerShape(999.dp),
            ),
        ),
        modifier = Modifier
            .let { if (focusRequester != null) it.focusRequester(focusRequester) else it },
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 22.dp, vertical = 10.dp),
            maxLines = 1,
        )
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun LibrarySwitcherPill(
    title: String,
    type: String,
    canSwitch: Boolean,
    onClick: () -> Unit,
) {
    val typeLabel = libraryTypeLabel(type).uppercase()
    val icon = libraryTypeIcon(type)

    if (!canSwitch) {
        Row(
            modifier = Modifier
                .background(Color.White.copy(alpha = 0.08f), RoundedCornerShape(999.dp))
                .border(1.dp, Color.White.copy(alpha = 0.06f), RoundedCornerShape(999.dp))
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(18.dp),
            )
            Column {
                Text(
                    text = typeLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.55f),
                    letterSpacing = 1.2.sp,
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        return
    }

    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    val container = if (isFocused) Color.White.copy(alpha = 0.94f) else Color.White.copy(alpha = 0.08f)
    val foreground = if (isFocused) Color.Black else Color.White

    Surface(
        onClick = onClick,
        interactionSource = interactionSource,
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(999.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = container,
            contentColor = foreground,
            focusedContainerColor = Color.White.copy(alpha = 0.94f),
            focusedContentColor = Color.Black,
            pressedContainerColor = Color.White.copy(alpha = 0.94f),
            pressedContentColor = Color.Black,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.025f),
        border = ClickableSurfaceDefaults.border(
            border = Border(
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.06f)),
                shape = RoundedCornerShape(999.dp),
            ),
            focusedBorder = Border(
                border = BorderStroke(0.dp, Color.Transparent),
                shape = RoundedCornerShape(999.dp),
            ),
        ),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = foreground,
                modifier = Modifier.size(18.dp),
            )
            Column {
                Text(
                    text = typeLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = foreground.copy(alpha = 0.55f),
                    letterSpacing = 1.2.sp,
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = foreground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Icon(
                imageVector = Icons.Filled.KeyboardArrowDown,
                contentDescription = null,
                tint = foreground.copy(alpha = 0.7f),
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

// ============================================================================
// Library tab filter row
// ============================================================================

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FilterRow(
    filter: TvLibraryBrowseFilter,
    sortLabel: String,
    onOpenFilters: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val currentYear = remember { Year.now().value }
    FlowRow(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = Spacing.sm),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        FilterEntryButton(
            label = "Filter",
            onClick = onOpenFilters,
        )

        if (filter.genre != null) {
            ActiveFilterPill(label = "Genre: ${filter.genre}")
        }
        if (filter.yearMin != null || filter.yearMax != null) {
            val label = TvLibraryYearOptions.match(
                currentYear = currentYear,
                yearMin = filter.yearMin,
                yearMax = filter.yearMax,
            )?.label ?: "${filter.yearMin ?: "?"}-${filter.yearMax ?: "?"}"
            ActiveFilterPill(label = "Year: $label")
        }
        ActiveFilterPill(label = "Sort: $sortLabel")
        if (filter.namePrefix != null) {
            ActiveFilterPill(label = "# ${filter.namePrefix}")
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun FilterEntryButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    val container = if (isFocused) Color.White.copy(alpha = 0.94f) else Color.White.copy(alpha = 0.08f)
    val foreground = if (isFocused) Color.Black else Color.White

    Surface(
        onClick = onClick,
        interactionSource = interactionSource,
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(12.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = container,
            contentColor = foreground,
            focusedContainerColor = Color.White.copy(alpha = 0.94f),
            focusedContentColor = Color.Black,
            pressedContainerColor = Color.White.copy(alpha = 0.94f),
            pressedContentColor = Color.Black,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.025f),
        border = ClickableSurfaceDefaults.border(
            border = Border(
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.06f)),
                shape = RoundedCornerShape(12.dp),
            ),
            focusedBorder = Border(
                border = BorderStroke(0.dp, Color.Transparent),
                shape = RoundedCornerShape(12.dp),
            ),
        ),
        modifier = modifier,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleSmall,
            color = foreground,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp),
        )
    }
}

@Composable
private fun ActiveFilterPill(
    label: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .background(
                color = Color.White.copy(alpha = 0.08f),
                shape = RoundedCornerShape(999.dp),
            )
            .border(
                width = 1.dp,
                color = Color.White.copy(alpha = 0.06f),
                shape = RoundedCornerShape(999.dp),
            )
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = Color.White.copy(alpha = 0.85f),
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun FilterSectionHeader(label: String) {
    Text(
        text = label,
        style = MaterialTheme.typography.titleMedium,
        color = Color.White.copy(alpha = 0.7f),
        fontWeight = FontWeight.SemiBold,
    )
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun FilterChoiceChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    val container = when {
        isFocused -> Color.White.copy(alpha = 0.94f)
        selected -> Color.White.copy(alpha = 0.22f)
        else -> Color.White.copy(alpha = 0.08f)
    }
    val foreground = when {
        isFocused -> Color.Black
        selected -> Color.White
        else -> Color.White.copy(alpha = 0.85f)
    }

    Surface(
        onClick = onClick,
        interactionSource = interactionSource,
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(12.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = container,
            contentColor = foreground,
            focusedContainerColor = Color.White.copy(alpha = 0.94f),
            focusedContentColor = Color.Black,
            pressedContainerColor = Color.White.copy(alpha = 0.94f),
            pressedContentColor = Color.Black,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
        border = ClickableSurfaceDefaults.border(
            border = Border(
                border = BorderStroke(
                    width = if (selected) 1.dp else 1.dp,
                    color = if (selected) Color.White.copy(alpha = 0.35f) else Color.White.copy(alpha = 0.06f),
                ),
                shape = RoundedCornerShape(12.dp),
            ),
            focusedBorder = Border(
                border = BorderStroke(0.dp, Color.Transparent),
                shape = RoundedCornerShape(12.dp),
            ),
        ),
        modifier = modifier,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = foreground,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
        )
    }
}

// ============================================================================
// Collection card (renders inside the Collections grid)
// ============================================================================

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun TvCollectionCard(
    collection: LibraryCollection,
    onClick: () -> Unit,
    focusRequester: FocusRequester? = null,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Card(
            onClick = onClick,
            shape = CardDefaults.shape(shape = RoundedCornerShape(8.dp)),
            modifier = Modifier
                .let { if (focusRequester != null) it.focusRequester(focusRequester) else it }
                .fillMaxWidth()
                .aspectRatio(2f / 3f),
        ) {
            if (!collection.posterUrl.isNullOrBlank()) {
                ThumbhashImage(
                    url = collection.posterUrl,
                    thumbhash = collection.posterThumbhash,
                    contentDescription = collection.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(SubtleSurface),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Filled.VideoLibrary,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.8f),
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Centered caption with a caps count noun ("12 MOVIES"), matching
        // tvOS `TVCollectionPosterCard`.
        Text(
            text = collection.name,
            style = MaterialTheme.typography.titleSmall,
            color = Color.White.copy(alpha = 0.92f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        collectionCountText(collection)?.let { countText ->
            Text(
                text = countText,
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.7f),
                maxLines = 1,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** `12 MOVIES`-style caps count, deriving the noun from the collection type. */
private fun collectionCountText(collection: LibraryCollection): String? {
    val count = collection.itemCount ?: return null
    if (count <= 0) return null
    val plural = count != 1
    val noun = when (collection.collectionType?.lowercase()) {
        "movie", "movies" -> if (plural) "movies" else "movie"
        "series", "show", "shows", "tvshows" -> if (plural) "shows" else "show"
        "album", "albums" -> if (plural) "albums" else "album"
        "audiobook", "audiobooks", "book", "books" -> if (plural) "books" else "book"
        else -> if (plural) "items" else "item"
    }
    return "$count $noun".uppercase()
}

// ============================================================================
// Helpers
// ============================================================================

@Composable
private fun InlineLoadingState(verticalPadding: androidx.compose.ui.unit.Dp = 48.dp) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = verticalPadding),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator(color = Color.White)
    }
}

private fun ResolvedSection.isProgressRow(): Boolean {
    val type = sectionType.lowercase()
    return type.contains("continue") ||
        type.contains("in_progress") ||
        type.contains("next_up") ||
        type.contains("up_next")
}

private fun libraryTypeIcon(type: String): ImageVector = when (type.lowercase()) {
    "movies", "movie" -> Icons.Filled.LocalMovies
    "series", "shows", "tv" -> Icons.Filled.Tv
    "audiobook", "audiobooks" -> Icons.Filled.Headphones
    else -> Icons.Filled.VideoLibrary
}

private fun libraryTypeLabel(type: String): String = when (type.lowercase()) {
    "movies", "movie" -> "Movies"
    "series", "shows", "tv" -> "TV Shows"
    "audiobook", "audiobooks" -> "Audiobooks"
    else -> "Library"
}

private fun recommendedSubtitle(state: TvLibraryDetailViewModel.UiState): String {
    val sectionCount = state.sections.count { it.items.isNotEmpty() }
    return if (sectionCount > 0) {
        "$sectionCount ${if (sectionCount == 1) "section" else "sections"}"
    } else {
        "Recommended for you"
    }
}

private fun librarySubtitle(state: TvLibraryDetailViewModel.UiState): String {
    val parts = buildList {
        state.browseFilter.namePrefix?.let { prefix ->
            add(
                when (prefix) {
                    "#" -> "Titles starting with #"
                    else -> "Titles starting with $prefix"
                },
            )
        }
        state.browseFilter.genre?.let { genre -> add(genre) }
        if (state.browseItems.isNotEmpty()) {
            add(
                "${state.browseItems.size}${if (state.browseHasMore) "+" else ""} titles",
            )
        }
    }
    return parts.joinToString(" · ").ifBlank { "All titles" }
}

private fun collectionsSubtitle(state: TvLibraryDetailViewModel.UiState): String {
    val count = state.collections.size
    return if (count > 0) {
        "$count ${if (count == 1) "collection" else "collections"}"
    } else {
        "Collections"
    }
}

// Catalog grid metrics, 1:1 with tvOS `TVCatalogGrid`: 6 columns, 40dp column
// spacing, 60dp row spacing. The Browse grid drops to 5 columns to clear the
// right-edge alphabet rail (tvOS shrinks the same way).
private const val LibraryGridColumns = 6
private const val LibraryBrowseGridColumns = 5
private val LibraryGridColumnSpacing = 40.dp
private val LibraryGridRowSpacing = 60.dp
private const val LibraryGridLoadMoreRowsThreshold = 8
