package com.continuum.app.tv.ui.screens.library

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
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
import com.continuum.app.tv.ui.components.TvCardWidth
import com.continuum.app.tv.ui.components.TvCatalogEmptyState
import com.continuum.app.tv.ui.components.TvErrorScreen
import com.continuum.app.tv.ui.components.TvFullScreenPicker
import com.continuum.app.tv.ui.components.TvFullScreenPickerOption
import com.continuum.app.tv.ui.components.TvHomeHeroCarousel
import com.continuum.app.tv.ui.components.TvMediaCard
import com.continuum.app.tv.ui.components.TvMediaRow
import com.continuum.app.tv.ui.components.TvRootHeroBackdrop
import com.continuum.app.tv.ui.components.TvRowStyle
import com.continuum.app.tv.ui.shell.TvTopMenuLayout
import com.continuum.app.tv.ui.theme.HeroDimens
import com.continuum.app.tv.ui.theme.Spacing
import com.continuum.app.tv.ui.theme.SubtleSurface
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * Android TV library detail surface — mirrors `TVLibrariesTabView` +
 * `TVLibraryLandingView` from tvOS. The screen exposes three tabs as a focus-
 * driven mode slider: **Recommended** (hero + section rows), **Library**
 * (6-column poster grid with right-side alphabet rail), and **Collections**
 * (6-column collection grid).
 *
 * The big-letter library header (64sp bold title + subtitle) lives at the
 * top of every tab so the screen never feels rootless. Filter dropdowns
 * (Genre, Sort) sit immediately below the header on the Library tab; the
 * alphabet rail floats on the right edge as a vertical pill.
 *
 * Card dimensions, spacing, pagination thresholds, and the alphabet rail
 * pattern track Section 3.2 of `.android-parity/specs/ios-tv.md`:
 * 6-column grid, 200×300dp posters, 40dp horizontal / 60dp vertical spacing,
 * load-more fires within 8 rows of the end.
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
    viewModel: TvLibraryDetailViewModel = koinViewModel(
        key = "library-$libraryId",
        parameters = { parametersOf(libraryId, libraryTitle, libraryType) },
    ),
) {
    val state by viewModel.uiState.collectAsState()

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
            TvLibraryTab.Library -> LibraryTab(
                state = state,
                canSwitchLibrary = canSwitchLibrary,
                onSwitchLibrary = onSwitchLibrary,
                onTabSelected = viewModel::onTabSelected,
                onItemClick = onItemClick,
                onGenreChanged = viewModel::onGenreChanged,
                onSortChanged = viewModel::onSortChanged,
                onNamePrefixChanged = viewModel::onNamePrefixChanged,
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
                        heroHeight = HeroDimens.HomeHeight,
                        autoFocus = !initialFocusRequested,
                        initialFocusRequester = heroFocusRequester,
                        downFocusRequester = firstRowFocusRequester,
                        onDirectionDown = {
                            firstRowId != null
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
                            itemSpacing = LibraryGridItemSpacing,
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
    onLoadMore: () -> Unit,
    onRetry: () -> Unit,
    onInitialContentFocus: () -> Unit,
) {
    var showGenrePicker by remember { mutableStateOf(false) }
    var showSortPicker by remember { mutableStateOf(false) }

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

    val genreOptions = buildList {
        add(TvFullScreenPickerOption(id = "__all", title = "All"))
        addAll(
            state.genres.map { genre ->
                TvFullScreenPickerOption(id = genre, title = genre)
            },
        )
    }
    val sortOptions = TvLibrarySortOption.entries.map { sort ->
        TvFullScreenPickerOption(id = sort.wireValue, title = sort.label)
    }

    Row(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.weight(1f)) {
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
                            state = state,
                            onGenreClick = { showGenrePicker = true },
                            onSortClick = { showSortPicker = true },
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
                LibraryGrid(
                    state = state,
                    canSwitchLibrary = canSwitchLibrary,
                    onSwitchLibrary = onSwitchLibrary,
                    onTabSelected = onTabSelected,
                    onItemClick = onItemClick,
                    onLoadMore = onLoadMore,
                    onGenreClick = { showGenrePicker = true },
                    onSortClick = { showSortPicker = true },
                    tabSliderFocusRequester = tabSliderFocusRequester,
                    firstItemFocusRequester = firstGridItemFocusRequester,
                )
            }
        }

        TvAlphabetRail(
            selected = state.browseFilter.namePrefix,
            onSelect = onNamePrefixChanged,
            modifier = Modifier
                .padding(
                    top = TvTopMenuLayout.contentTopInset + 24.dp,
                    end = Spacing.md,
                    bottom = Spacing.xxl,
                )
                .width(44.dp),
        )
    }

    if (showGenrePicker) {
        TvFullScreenPicker(
            title = if (state.filtersLoading) "Genres" else "Genre",
            options = genreOptions,
            selectedId = state.browseFilter.genre ?: "__all",
            onSelect = { id ->
                showGenrePicker = false
                onGenreChanged(id.takeUnless { it == "__all" })
            },
            onDismiss = { showGenrePicker = false },
        )
    }

    if (showSortPicker) {
        TvFullScreenPicker(
            title = "Sort By",
            options = sortOptions,
            selectedId = state.browseFilter.sort,
            onSelect = { id ->
                showSortPicker = false
                onSortChanged(TvLibrarySortOption.fromWire(id))
            },
            onDismiss = { showSortPicker = false },
        )
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
    onGenreClick: () -> Unit,
    onSortClick: () -> Unit,
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
                        (LibraryGridLoadMoreRowsThreshold * LibraryGridColumns)
            }
        }
    }

    LaunchedEffect(nearEnd) {
        if (nearEnd) onLoadMore()
    }

    LazyVerticalGrid(
        state = gridState,
        columns = GridCells.Fixed(LibraryGridColumns),
        modifier = Modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.spacedBy(LibraryGridItemSpacing),
        verticalArrangement = Arrangement.spacedBy(Spacing.sectionSpacing),
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
                        state = state,
                        onGenreClick = onGenreClick,
                        onSortClick = onSortClick,
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

    LazyVerticalGrid(
        columns = GridCells.Fixed(LibraryGridColumns),
        modifier = Modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.spacedBy(LibraryGridItemSpacing),
        verticalArrangement = Arrangement.spacedBy(Spacing.sectionSpacing),
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
            else -> itemsIndexed(
                state.collections,
                key = { _, collection -> collection.id },
            ) { index, collection ->
                TvCollectionCard(
                    collection = collection,
                    onClick = { onCollectionClick(collection.id, collection.name) },
                    focusRequester = firstCollectionFocusRequester.takeIf { index == 0 },
                )
            }
        }
    }
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

@Composable
private fun FilterRow(
    state: TvLibraryDetailViewModel.UiState,
    onGenreClick: () -> Unit,
    onSortClick: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FilterDropdownButton(
            label = "Genre",
            value = state.browseFilter.genre ?: "All",
            onClick = onGenreClick,
            modifier = Modifier.widthIn(min = 200.dp, max = 280.dp),
        )
        FilterDropdownButton(
            label = "Sort",
            value = TvLibrarySortOption.fromWire(state.browseFilter.sort).label,
            onClick = onSortClick,
            modifier = Modifier.widthIn(min = 200.dp, max = 280.dp),
        )
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun FilterDropdownButton(
    label: String,
    value: String,
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
        Row(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = foreground.copy(alpha = 0.55f),
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleSmall,
                color = foreground,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
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

        Text(
            text = collection.name,
            style = MaterialTheme.typography.titleSmall,
            color = Color.White.copy(alpha = 0.92f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = collection.itemCount?.let {
                "$it ${if (it == 1) "item" else "items"}"
            } ?: "Collection",
            style = MaterialTheme.typography.bodySmall,
            color = Color.White.copy(alpha = 0.7f),
        )
    }
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
    else -> Icons.Filled.VideoLibrary
}

private fun libraryTypeLabel(type: String): String = when (type.lowercase()) {
    "movies", "movie" -> "Movies"
    "series", "shows", "tv" -> "TV Shows"
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

private const val LibraryGridColumns = 6
private val LibraryGridItemSpacing = 40.dp
private const val LibraryGridLoadMoreRowsThreshold = 8
