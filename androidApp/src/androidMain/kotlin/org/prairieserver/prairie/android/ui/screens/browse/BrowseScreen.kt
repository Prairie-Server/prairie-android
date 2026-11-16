package org.prairieserver.prairie.android.ui.screens.browse

import org.prairieserver.prairie.android.ui.components.PosterArtworkAspectRatio
import org.prairieserver.prairie.common.i18n.LocaleFormat
import org.prairieserver.prairie.common.i18n.currentFormatLocale
import org.prairieserver.prairie.common.i18n.currentNumberLocale
import org.prairieserver.prairie.common.i18n.text
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.prairieserver.prairie.android.ui.components.PosterGridSkeleton
import org.prairieserver.prairie.android.ui.components.PrairieDropdownMenu
import org.prairieserver.prairie.android.ui.components.PrairieDropdownMenuItem
import org.prairieserver.prairie.android.ui.components.rememberShimmerProgress
import org.prairieserver.prairie.android.ui.theme.PrairieSurfaceElevated
import org.prairieserver.prairie.android.ui.util.formatCardDate
import org.prairieserver.prairie.catalog.sort.CatalogSortField
import org.prairieserver.prairie.catalog.sort.CatalogSortRelevance
import org.prairieserver.prairie.catalog.sort.CatalogSortScope
import org.prairieserver.prairie.common.catalog.labelRes
import org.prairieserver.prairie.common.R as CommonR

/**
 * The catalog/browse screen with filter chips and an infinite-scroll grid.
 *
 * Displays a top app bar with the library name (or "Browse"), filter chips
 * for quick genre/rating access, and a [CatalogGrid] of media cards.
 * A [FilterSheet] bottom sheet provides full filter and sort controls.
 *
 * @param onItemClick Callback with content ID when a card is tapped.
 * @param onBackClick Callback when the back button is pressed.
 * @param viewModel The browse ViewModel (provided by Koin).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowseScreen(
    onItemClick: (String) -> Unit,
    onBackClick: () -> Unit,
    viewModel: BrowseViewModel,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsState()
    val letterJump by viewModel.letterJump.collectAsState()
    val shownRatingSources = org.prairieserver.prairie.android.ui.components.rememberShownRatingSources()
    var showFilterSheet by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = state.title ?: stringResource(CommonR.string.library_browse),
                        style = MaterialTheme.typography.headlineSmall,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(CommonR.string.common_back),
                        )
                    }
                },
                actions = {
                    // iOS control bar: sort menu first, then the filter button
                    // with an active-facet count badge.
                    var sortMenuOpen by remember { mutableStateOf(false) }
                    IconButton(onClick = { sortMenuOpen = true }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Sort,
                            contentDescription = stringResource(CommonR.string.library_sort),
                        )
                    }
                    PrairieDropdownMenu(
                        expanded = sortMenuOpen,
                        onDismissRequest = { sortMenuOpen = false },
                    ) {
                        val sortRelevance = browseSortRelevance(state.mediaType)
                        browseSortOptions(
                            mediaType = state.mediaType,
                            shownRatingSources = shownRatingSources,
                            current = state.filterState.sort,
                        ).forEach { field ->
                            val label = stringResource(field.labelRes(sortRelevance))
                            val active = state.filterState.sort == field.wireValue
                            val arrow = if (state.filterState.order == "desc") "↓" else "↑"
                            val shown = if (active) "$label  $arrow" else label
                            PrairieDropdownMenuItem(
                                text = {
                                    Text(
                                        text = shown,
                                        fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                                    )
                                },
                                onClick = {
                                    viewModel.selectSort(field.wireValue, field.defaultOrder)
                                    sortMenuOpen = false
                                },
                            )
                        }
                    }
                    androidx.compose.material3.BadgedBox(
                        badge = {
                            if (state.filterState.activeFacetCount > 0) {
                                androidx.compose.material3.Badge {
                                    Text(LocaleFormat.integer(state.filterState.activeFacetCount, currentNumberLocale()))
                                }
                            }
                        },
                    ) {
                        IconButton(onClick = { showFilterSheet = true }) {
                            Icon(
                                imageVector = Icons.Default.FilterList,
                                contentDescription = stringResource(CommonR.string.filters_title),
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background.copy(alpha = 0.94f),
                    navigationIconContentColor = MaterialTheme.colorScheme.onSurface,
                    actionIconContentColor = MaterialTheme.colorScheme.onSurface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                ),
            )
        },
        modifier = modifier,
    ) { contentPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding),
        ) {
            // Active filter chips — iOS phone: horizontal scroll of removable
            // capsule chips; each chip removes exactly one facet value.
            if (state.filterState.hasActiveFilters) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    org.prairieserver.prairie.catalog.filter.CatalogFacet.available(state.mediaType).forEach { facet ->
                        state.filterState.valuesFor(facet).sorted().forEach { value ->
                            ActiveFilterChip(
                                label = facetValueLabel(facet, value),
                                onRemove = {
                                    viewModel.applyFilterState(state.filterState.toggle(facet, value))
                                },
                            )
                        }
                    }
                }
            }

            // Content area
            when {
                state.isLoading && state.items.isEmpty() -> {
                    // Square once the list has shown audiobooks (a re-sort or
                    // filter reloads it); the first load can't know yet.
                    PosterGridSkeleton(
                        progress = rememberShimmerProgress(),
                        posterAspect = if (state.mediaType == org.prairieserver.prairie.catalog.filter.BrowseFacetMediaType.Audiobook) {
                            1f
                        } else {
                            PosterArtworkAspectRatio
                        },
                    )
                }

                state.error != null -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Default.ErrorOutline,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(48.dp),
                            )
                            Text(
                                text = state.error?.text() ?: stringResource(CommonR.string.status_errorGeneric),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Button(onClick = { viewModel.refresh() }) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(stringResource(CommonR.string.common_retry))
                            }
                        }
                    }
                }

                state.items.isEmpty() -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Default.Movie,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier.size(64.dp),
                            )
                            Text(
                                text = if (state.libraryHasItems == false) {
                                    stringResource(CommonR.string.library_empty)
                                } else {
                                    stringResource(CommonR.string.library_noItems)
                                },
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                            )
                            Text(
                                text = if (state.libraryHasItems == false) {
                                    stringResource(CommonR.string.library_emptyBody)
                                } else {
                                    stringResource(CommonR.string.library_tryAdjustingFilters)
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                }

                else -> {
                    // iOS phone Browse shows the grid directly with no count header.
                    val dateLocale = currentFormatLocale()
                    CatalogGrid(
                        items = state.items,
                        isLoadingMore = state.isLoadingMore,
                        hasMore = state.hasMore,
                        onItemClick = onItemClick,
                        onLoadMore = { viewModel.loadMore() },
                        // Date sorts surface the sorted-by date under each card.
                        cardSubtitle = when (state.filterState.sort) {
                            "added_at" -> { item -> formatCardDate(item.addedAt, dateLocale) }
                            "release_date" -> { item -> formatCardDate(item.releaseDate, dateLocale) }
                            "last_air_date" -> { item -> formatCardDate(item.lastAirDate, dateLocale) }
                            else -> null
                        },
                        letterJump = letterJump,
                        listKey = state.filterState,
                        onPrepareLetterJump = viewModel::prepareLetterJump,
                        letterJumpPosition = viewModel::letterJumpPosition,
                        viewDensity = state.catalogDensity,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
    }

    // Filter bottom sheet — edits a draft and commits on dismiss (iOS: no
    // Apply button; FilterView commits via onDisappear).
    if (showFilterSheet) {
        FilterSheet(
            viewDensity = state.catalogDensity,
            onSelectDensity = { viewModel.selectViewDensity(it) },
            currentFilters = state.filterState,
            availableFilters = state.availableFilters,
            mediaType = state.mediaType,
            preserveFilters = state.preserveFilters,
            onCommit = { viewModel.applyFilterState(it) },
            onSetPreserve = { viewModel.setPreserveFilters(it) },
            onDismiss = { showFilterSheet = false },
        )
    }
}

/**
 * The sort list's media. Video Browse spans libraries, so it lists every
 * field, as the web client's cross-library catalog does.
 */
internal fun browseSortRelevance(
    mediaType: org.prairieserver.prairie.catalog.filter.BrowseFacetMediaType,
): CatalogSortRelevance = when (mediaType) {
    org.prairieserver.prairie.catalog.filter.BrowseFacetMediaType.Video -> CatalogSortRelevance.Any
    org.prairieserver.prairie.catalog.filter.BrowseFacetMediaType.Audiobook ->
        CatalogSortRelevance.Only(CatalogSortScope.Audiobook)
}

/** Sort fields per media type, in the web client's order. [current] stays listed while it is the active sort. */
internal fun browseSortOptions(
    mediaType: org.prairieserver.prairie.catalog.filter.BrowseFacetMediaType,
    shownRatingSources: Set<String>,
    current: String? = null,
): List<CatalogSortField> = CatalogSortField.options(
    relevance = browseSortRelevance(mediaType),
    includePersonalized = true,
    shownRatingSources = shownRatingSources,
    keep = CatalogSortField.fromWire(current),
)

/** Chip label for a facet value (decades and watch statuses have labels
 *  distinct from their wire values). */
@Composable
internal fun facetValueLabel(
    facet: org.prairieserver.prairie.catalog.filter.CatalogFacet,
    value: String,
): String = when (facet) {
    org.prairieserver.prairie.catalog.filter.CatalogFacet.Decade ->
        value.toIntOrNull()?.let { org.prairieserver.prairie.catalog.filter.decadeLabel(it).text() }
            ?: stringResource(CommonR.string.filters_decade, value)
    org.prairieserver.prairie.catalog.filter.CatalogFacet.WatchStatus ->
        watchStatusLabel(value)?.let { stringResource(it) } ?: value
    org.prairieserver.prairie.catalog.filter.CatalogFacet.DynamicRange ->
        org.prairieserver.prairie.catalog.filter.CatalogFacet.DYNAMIC_RANGE_OPTIONS
            .firstOrNull { it.first == value }?.second?.text() ?: value
    else -> value
}

/** A facet's name in the interface language (`shared` keeps an English one). */
@Composable
internal fun facetLabel(facet: org.prairieserver.prairie.catalog.filter.CatalogFacet): String = stringResource(
    when (facet) {
        org.prairieserver.prairie.catalog.filter.CatalogFacet.Genre -> CommonR.string.filters_facet_genres
        org.prairieserver.prairie.catalog.filter.CatalogFacet.Decade -> CommonR.string.filters_facet_decade
        org.prairieserver.prairie.catalog.filter.CatalogFacet.WatchStatus -> CommonR.string.filters_facet_watchStatus
        org.prairieserver.prairie.catalog.filter.CatalogFacet.ContentRating -> CommonR.string.filters_facet_contentRating
        org.prairieserver.prairie.catalog.filter.CatalogFacet.Resolution -> CommonR.string.filters_facet_resolution
        org.prairieserver.prairie.catalog.filter.CatalogFacet.DynamicRange -> CommonR.string.filters_facet_dynamicRange
        org.prairieserver.prairie.catalog.filter.CatalogFacet.Studio -> CommonR.string.filters_facet_studio
        org.prairieserver.prairie.catalog.filter.CatalogFacet.Network -> CommonR.string.filters_facet_network
        org.prairieserver.prairie.catalog.filter.CatalogFacet.Country -> CommonR.string.filters_facet_country
        org.prairieserver.prairie.catalog.filter.CatalogFacet.AudioLanguage -> CommonR.string.filters_facet_audioLanguage
        org.prairieserver.prairie.catalog.filter.CatalogFacet.SubtitleLanguage -> CommonR.string.filters_facet_subtitleLanguage
        org.prairieserver.prairie.catalog.filter.CatalogFacet.OriginalLanguage -> CommonR.string.filters_facet_originalLanguage
        org.prairieserver.prairie.catalog.filter.CatalogFacet.Author -> CommonR.string.filters_facet_author
        org.prairieserver.prairie.catalog.filter.CatalogFacet.Narrator -> CommonR.string.filters_facet_narrator
        org.prairieserver.prairie.catalog.filter.CatalogFacet.SeriesName -> CommonR.string.filters_facet_series
    },
)

/** Watch-status chip labels by wire value; `shared` keeps English ones. */
@androidx.annotation.StringRes
private fun watchStatusLabel(value: String): Int? = when (value) {
    "unwatched" -> CommonR.string.filters_value_unwatched
    "inProgress" -> CommonR.string.filters_value_inProgress
    "watched" -> CommonR.string.filters_value_watched
    "favorited" -> CommonR.string.filters_value_favorited
    "watchlist" -> CommonR.string.filters_value_watchlist
    else -> null
}

/**
 * Removable active-filter capsule chip matching the iOS phone Browse view:
 * surfaceElevated capsule, 12sp label in onSurface, an xmark.circle.fill-style
 * close glyph (14pt) in secondary text, 4dp inner spacing, 10/5 padding.
 */
@Composable
private fun ActiveFilterChip(
    label: String,
    onRemove: () -> Unit,
) {
    Row(
        modifier = Modifier
            .clip(CircleShape)
            .background(PrairieSurfaceElevated)
            .padding(horizontal = 10.dp, vertical = 5.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Icon(
            imageVector = Icons.Filled.Cancel,
            contentDescription = stringResource(CommonR.string.filters_remove),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .size(14.dp)
                .clickable(onClick = onRemove),
        )
    }
}
