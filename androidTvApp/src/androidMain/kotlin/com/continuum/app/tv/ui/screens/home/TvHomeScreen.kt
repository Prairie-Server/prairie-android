package com.continuum.app.tv.ui.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import com.continuum.app.model.catalog.ItemDetail
import com.continuum.app.model.section.ResolvedSection
import com.continuum.app.model.section.SectionItem
import com.continuum.app.network.ApiResult
import com.continuum.app.repository.CatalogRepository
import com.continuum.app.tv.ui.components.LocalAmbientBackdropTint
import com.continuum.app.tv.ui.components.TvErrorScreen
import com.continuum.app.tv.ui.components.TvFocusMarquee
import com.continuum.app.tv.ui.components.TvLoadingScreen
import com.continuum.app.tv.ui.components.TvMediaRow
import com.continuum.app.tv.ui.components.TvRootHeroBackdrop
import com.continuum.app.tv.ui.components.TvRowStyle
import com.continuum.app.tv.ui.components.rememberAmbientBackdropTintState
import com.continuum.app.tv.ui.components.rememberTvFocusMarqueeState
import com.continuum.app.tv.ui.shell.TvTopMenuLayout
import com.continuum.app.tv.ui.theme.RowDimens
import com.continuum.app.tv.ui.theme.Spacing
import com.continuum.app.tv.ui.util.visibleOnTv
import com.continuum.app.viewmodel.HomeViewModel
import kotlinx.coroutines.delay
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

/**
 * Android TV Home screen — the Skyline landing, a faithful port of tvOS
 * `TVSkylineSectionFeed` + `HomeView`. There is NO featured carousel: every
 * visible non-empty section renders as a horizontal row in server order. A
 * passive focus marquee (bottom-left) and the page backdrop both track whichever
 * row card currently holds focus, debounced ~150 ms. Rows scroll inside a lower
 * band so they never paint over the marquee text.
 *
 * "Recommended For You" is surfaced as a Home row (For You is no longer a tab);
 * its See-all opens the recommendations screen via [onOpenForYou].
 */
@Composable
fun TvHomeScreen(
    onItemClick: (contentId: String) -> Unit,
    onPlayItem: (contentId: String, type: String?, resumePositionSeconds: Double?) -> Unit = { _, _, _ -> },
    onSeeAll: () -> Unit = {},
    onOpenForYou: () -> Unit = {},
    onInitialContentFocus: () -> Unit = {},
    focusRequest: Int = 0,
    viewModel: HomeViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
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
            onItemClick = onItemClick,
            onSeeAll = onSeeAll,
            onOpenForYou = onOpenForYou,
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
    onItemClick: (String) -> Unit,
    onSeeAll: () -> Unit = {},
    onOpenForYou: () -> Unit = {},
    onInitialContentFocus: () -> Unit,
    focusRequest: Int,
    onSetWatched: (String, Boolean) -> Unit = { _, _ -> },
    onToggleFavorite: (String, Boolean) -> Unit = { _, _ -> },
    onToggleWatchlist: (String, Boolean) -> Unit = { _, _ -> },
    onDismissContinueWatching: (String, String) -> Unit = { _, _ -> },
) {
    // All visible non-empty sections, in server order — no featured split.
    val rows = remember(sections) { sections.filter { it.items.isNotEmpty() } }

    val tintState = rememberAmbientBackdropTintState()

    // §9 marquee enrichment: a non-blocking item-detail fetch that backfills the
    // aired/cast line and, for episodes, upgrades the hero to the series
    // backdrop. Mirrors tvOS `loadEnrichment` (ContinuumAPI.itemDetail).
    val catalogRepository: CatalogRepository = koinInject()
    val fetchDetail: suspend (String) -> ItemDetail? = remember(catalogRepository) {
        { contentId ->
            (catalogRepository.getItemDetail(contentId) as? ApiResult.Success)?.data
        }
    }
    val marquee = rememberTvFocusMarqueeState(fetchDetail = fetchDetail)
    val initialMarqueeSeed = remember(rows) {
        rows.firstOrNull()?.let { section ->
            section.items.firstOrNull()?.let { item ->
                TvHomeMarqueeSeed(item = item, rowTitle = section.title)
            }
        }
    }

    LaunchedEffect(initialMarqueeSeed?.item?.contentId, initialMarqueeSeed?.rowTitle) {
        val seed = initialMarqueeSeed ?: return@LaunchedEffect
        if (marquee.content == null) {
            marquee.seedInitialPreview(seed.item, seed.rowTitle)
        }
    }

    // Focused-card → marquee. Reported on focus gain only, so moving up into
    // chrome keeps the last previewed item.
    val onItemFocused: (SectionItem, String) -> Unit = { item, rowTitle ->
        marquee.preview(item, rowTitle)
    }

    // The tint follows the COMMITTED (debounced) marquee content and its
    // EFFECTIVE hero backdrop — so when enrichment upgrades an episode to its
    // series backdrop, the ambient wash re-samples from the upgraded art
    // (tvOS `sampleTintIfNeeded(for: backdropURL)`). Keyed on the effective
    // backdrop url so it doesn't churn while scrubbing across a row.
    LaunchedEffect(marquee.content?.heroBackdropUrl) {
        marquee.content?.let { tintState.set(it.source, it.heroBackdropUrl) }
    }

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

    // Entry focus → first row's first card on entry (no carousel gate).
    LaunchedEffect(firstRowId) {
        if (initialFocusRequested || firstRowId == null) return@LaunchedEffect
        delay(120)
        requestFirstRowFocus()
        initialFocusRequested = true
    }

    LaunchedEffect(focusRequest, firstRowId) {
        if (focusRequest == 0 || firstRowId == null) return@LaunchedEffect
        requestFirstRowFocus()
    }

    CompositionLocalProvider(LocalAmbientBackdropTint provides tintState) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
        ) {
            // Crisp corner-anchored backdrop + diagonal tint wash, driven by the
            // focused card's marquee content.
            TvRootHeroBackdrop(
                content = marquee.content,
                modifier = Modifier.fillMaxSize(),
            )

            // Rows live only in the lower band; the viewport clips at its top
            // edge so rows never paint through the marquee text while scrolling.
            val bandHeight = maxHeight * RowBandHeightFraction
            val trailingPreviewPadding = (bandHeight - TvHomeRowBandBottomInset).coerceAtLeast(0.dp)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(bandHeight)
                    .align(Alignment.BottomStart)
                    .clipToBounds(),
            ) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(TvHomeRowPreviewSpacing),
                    contentPadding = PaddingValues(
                        top = 0.dp,
                        bottom = trailingPreviewPadding,
                    ),
                ) {
                    items(
                        items = rows,
                        key = ResolvedSection::id,
                        contentType = { "home-section-row" },
                    ) { section ->
                        val isProgressRow = section.isProgressRow()
                        val isFirstRow = section.id == firstRowId
                        val isForYou = section.isForYouRow()
                        TvMediaRow(
                            title = section.title,
                            items = section.items,
                            onItemClick = onItemClick,
                            icon = if (isProgressRow) Icons.Filled.PlayCircle else null,
                            // No per-row "See all" on Home — except For You, whose
                            // See-all opens the recommendations screen.
                            onSeeAllClick = if (isForYou) onOpenForYou else null,
                            showProgress = isProgressRow,
                            style = if (isProgressRow) TvRowStyle.Backdrop else TvRowStyle.Poster,
                            startPadding = Spacing.safeArea,
                            endPadding = Spacing.safeArea,
                            itemSpacing = TvHomeItemSpacing,
                            rowTopPadding = TvHomeRowCardVerticalPadding,
                            rowBottomPadding = TvHomeRowCardVerticalPadding,
                            posterWidth = RowDimens.DensePosterWidth,
                            firstItemFocusRequester = firstRowFocusRequester
                                .takeIf { isFirstRow },
                            firstItemFocusRequest = if (isFirstRow) firstRowFocusRequest else 0,
                            onItemFocused = { item -> onItemFocused(item, section.title) },
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
                }
            }

            // Passive billboard floating over the band above the focused row.
            TvFocusMarquee(
                content = marquee.content,
                startPadding = Spacing.safeArea,
                bottomPadding = bandHeight + MarqueeBottomGap,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/** Home row card spacing — tvOS MediaRow cardSpacing 40pt maps to 20dp. */
private val TvHomeItemSpacing = 20.dp

/** Home row preview gap — tvOS rowBandPreviewSpacing 10pt maps to 5dp. */
private val TvHomeRowPreviewSpacing = 5.dp

/** Focus-lift breathing room — tvOS rowBandCardVerticalPadding 14pt maps to 7dp. */
private val TvHomeRowCardVerticalPadding = 7.dp

/** Lower-band bottom inset — tvOS rowBandBottomInset 20pt maps to 10dp. */
private val TvHomeRowBandBottomInset = 10.dp

/** Portion of the screen reserved for the row stack (tvOS rowBandHeightFraction). */
private const val RowBandHeightFraction = 0.50f

/** Gap between the marquee block and the top of the row band. */
private val MarqueeBottomGap = 16.dp

private fun ResolvedSection.isProgressRow(): Boolean {
    val type = sectionType.lowercase()
    return type.contains("continue") ||
        type.contains("in_progress") ||
        type.contains("next_up") ||
        type.contains("up_next")
}

private fun ResolvedSection.isForYouRow(): Boolean {
    // Only the dedicated For You / recommendations section opens the
    // recommendations screen — match exact type ids / titles, not a broad
    // "recommend" substring (which would also catch e.g. per-genre
    // "recommended_*" rows).
    val type = sectionType.lowercase()
    return type == "for_you" ||
        type == "foryou" ||
        type == "recommendations" ||
        title.equals("for you", ignoreCase = true) ||
        title.equals("recommended for you", ignoreCase = true)
}

private data class TvHomeMarqueeSeed(
    val item: SectionItem,
    val rowTitle: String,
)
