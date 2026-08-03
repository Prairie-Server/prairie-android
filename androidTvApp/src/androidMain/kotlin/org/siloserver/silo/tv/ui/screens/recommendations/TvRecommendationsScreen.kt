package org.siloserver.silo.tv.ui.screens.recommendations

import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Button
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import org.siloserver.silo.tv.ui.components.TvErrorScreen
import org.siloserver.silo.tv.ui.components.TvLoadingScreen
import org.siloserver.silo.tv.ui.components.TvMediaRow
import org.siloserver.silo.tv.ui.components.TvRowStyle
import org.siloserver.silo.tv.ui.components.TvHeroActionPill
import org.siloserver.silo.tv.ui.components.TvPillVariant
import org.siloserver.silo.tv.ui.screens.personal.TvFavoritesInline
import org.siloserver.silo.tv.ui.screens.personal.TvWatchlistInline
import org.siloserver.silo.tv.ui.shell.TvTopMenuLayout
import org.siloserver.silo.tv.ui.theme.Spacing
import org.siloserver.silo.tv.ui.theme.TvSmoothBringIntoViewSpec
import org.siloserver.silo.tv.ui.util.visibleOnTv
import org.siloserver.silo.viewmodel.RecommendationsViewModel
import org.koin.compose.viewmodel.koinViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

private val RecommendationsFilterBandHeight = 52.dp

internal data class ForYouListPosition(
    val firstVisibleItemIndex: Int,
    val firstVisibleItemScrollOffset: Int,
) {
    val isAtTop: Boolean
        get() = firstVisibleItemIndex == 0 && firstVisibleItemScrollOffset == 0
}

internal suspend fun maintainForYouTopAnchor(
    positionEvents: Flow<ForYouListPosition>,
    isFirstRowFocused: () -> Boolean,
    awaitRelocation: suspend () -> Unit,
    currentPosition: () -> ForYouListPosition,
    scrollToTop: suspend () -> Unit,
) {
    positionEvents.collect { observed ->
        if (!isFirstRowFocused() || observed.isAtTop) return@collect
        awaitRelocation()
        if (isFirstRowFocused() && !currentPosition().isAtTop) scrollToTop()
    }
}

/**
 * "For You" tab. Reuses the shared [RecommendationsViewModel] that drives
 * the phone `/recommendations/discover` feed. Layout mirrors [TvHomeScreen]
 * (rows down the page) minus the featured hero — the discover API returns
 * section-style rows, not a hero card.
 */
@OptIn(ExperimentalFoundationApi::class, ExperimentalTvMaterial3Api::class)
@Composable
fun TvRecommendationsScreen(
    onItemClick: (contentId: String) -> Unit,
    onRecommendationItemClick: (contentId: String) -> Unit,
    detailReturnFocusRequest: Int,
    detailReturnCardFocusRequester: FocusRequester,
    onSolidTopBarChanged: (Boolean) -> Unit,
    onInitialContentFocus: () -> Unit = {},
    focusRequest: Int = 0,
    entryRequest: TvForYouEntryRequest = TvForYouEntryRequest(),
    viewModel: RecommendationsViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val visibleSections = remember(state.sections) { state.sections.visibleOnTv() }
    val forYouFocusRequester = remember { FocusRequester() }
    val watchlistFocusRequester = remember { FocusRequester() }
    val favoritesFocusRequester = remember { FocusRequester() }
    val firstRecommendationRowFocusRequester = remember { FocusRequester() }
    val firstRecommendationCardFocusRequester = remember { FocusRequester() }
    val detailReturnRowFocusRequester = remember { FocusRequester() }
    val focusBridgeScope = rememberCoroutineScope()
    // rememberSaveable, not remember: opening an item disposes this screen's
    // composition, and a plain remember would re-initialise from
    // entryRequest.selection on the way back. Top-level For You entry carries
    // selection = null, so returning from a Watchlist item did not merely
    // forget the list — it actively reselected the recommendations feed.
    //
    // lastAppliedEntrySequence must survive with it. Resetting it to 0 makes
    // the LaunchedEffect below treat the unchanged entry request as new and
    // re-apply its selection, which reintroduces the same jump even once the
    // selection itself is saved.
    val recommendationsListState = rememberLazyListState()
    var savedListSelection by rememberSaveable { mutableStateOf(entryRequest.selection) }
    var lastAppliedEntrySequence by rememberSaveable { mutableIntStateOf(0) }
    var lastFocusedSectionId by rememberSaveable { mutableStateOf("") }
    var lastFocusedContentId by rememberSaveable { mutableStateOf("") }
    var lastFocusedRowIndex by rememberSaveable { mutableIntStateOf(0) }
    var lastFocusedCardIndex by rememberSaveable { mutableIntStateOf(0) }
    val lastFocusedTarget = if (
        lastFocusedSectionId.isNotBlank() && lastFocusedContentId.isNotBlank()
    ) {
        ForYouFocusTarget(
            sectionId = lastFocusedSectionId,
            contentId = lastFocusedContentId,
            rowIndex = lastFocusedRowIndex,
            cardIndex = lastFocusedCardIndex,
        )
    } else {
        null
    }
    val returnRows = remember(visibleSections) {
        visibleSections.map { section ->
            ForYouFocusRow(section.id, section.items.map { it.contentId })
        }
    }
    val resolvedReturnTarget = lastFocusedTarget?.let {
        resolveForYouReturnTarget(it, returnRows)
    }
    var firstRecommendationRowFocused by remember { mutableStateOf(false) }
    // The first row still owns the established filter-to-feed bridge. When it
    // is also the detail return row, use the return requester so the LazyRow
    // has only one row-container requester attached at a time.
    val firstRowContainerFocusRequester = if (resolvedReturnTarget?.rowIndex == 0) {
        detailReturnRowFocusRequester
    } else {
        firstRecommendationRowFocusRequester
    }
    val moveIntoRecommendations: () -> Boolean = {
        if (
            !shouldBridgeRecommendationsDown(
                showingRecommendations = savedListSelection == null,
                hasVisibleRecommendations = visibleSections.isNotEmpty(),
            )
        ) {
            false
        } else {
            focusBridgeScope.launch {
                requestRecommendationRowFocus(
                    requestRowContainer = {
                        runCatching { firstRowContainerFocusRequester.requestFocus() }
                            .getOrDefault(false)
                    },
                    awaitFrame = { withFrameNanos { } },
                    requestFirstCard = {
                        runCatching { firstRecommendationCardFocusRequester.requestFocus() }
                            .getOrDefault(false)
                    },
                )
            }
            true
        }
    }

    DisposableEffect(savedListSelection, onSolidTopBarChanged) {
        onSolidTopBarChanged(savedListSelection == null)
        onDispose { onSolidTopBarChanged(false) }
    }

    LaunchedEffect(entryRequest.sequence) {
        val applied = applyForYouEntryRequest(
            currentSelection = savedListSelection,
            lastAppliedSequence = lastAppliedEntrySequence,
            request = entryRequest,
        )
        savedListSelection = applied.selection
        lastAppliedEntrySequence = applied.lastAppliedSequence
        if (applied.appliedRequest) {
            requestForYouEntryFocus(
                selection = applied.selection,
                awaitFrame = { withFrameNanos { } },
                requestForYou = {
                    runCatching { forYouFocusRequester.requestFocus() }.getOrDefault(false)
                },
                requestWatchlist = {
                    runCatching { watchlistFocusRequester.requestFocus() }.getOrDefault(false)
                },
                requestFavorites = {
                    runCatching { favoritesFocusRequester.requestFocus() }.getOrDefault(false)
                },
            )
        }
    }

    LaunchedEffect(detailReturnFocusRequest, resolvedReturnTarget) {
        if (detailReturnFocusRequest == 0) return@LaunchedEffect
        val target = resolvedReturnTarget
        if (shouldFallbackForYouReturnToFilter(target)) {
            repeat(6) {
                withFrameNanos { }
                val handled = runCatching { forYouFocusRequester.requestFocus() }
                    .getOrDefault(false)
                if (handled) return@LaunchedEffect
            }
            return@LaunchedEffect
        }
        target ?: return@LaunchedEffect
        val rowVisible = recommendationsListState.layoutInfo.visibleItemsInfo
            .any { it.index == target.rowIndex }
        if (!rowVisible) recommendationsListState.scrollToItem(target.rowIndex)
        repeat(6) {
            withFrameNanos { }
            val handled = requestRecommendationRowFocus(
                requestRowContainer = {
                    runCatching { detailReturnRowFocusRequester.requestFocus() }
                        .getOrDefault(false)
                },
                awaitFrame = { withFrameNanos { } },
                requestFirstCard = {
                    runCatching { detailReturnCardFocusRequester.requestFocus() }
                        .getOrDefault(false)
                },
            )
            if (handled) return@LaunchedEffect
        }
    }

    // Match tvOS: recommendations remain the landing content when available;
    // an empty successful response defaults to the inline Watchlist fallback.
    LaunchedEffect(state.isLoading, state.error, visibleSections) {
        if (!state.isLoading && state.error == null && visibleSections.isEmpty() && savedListSelection == null) {
            savedListSelection = SavedListSelection.Watchlist
        }
    }

    LaunchedEffect(firstRecommendationRowFocused) {
        if (!firstRecommendationRowFocused) return@LaunchedEffect
        fun currentPosition() = ForYouListPosition(
            firstVisibleItemIndex = recommendationsListState.firstVisibleItemIndex,
            firstVisibleItemScrollOffset = recommendationsListState.firstVisibleItemScrollOffset,
        )
        // Stay suspended while the list is correctly anchored. A delayed
        // focus relocation can still move it after an initially-top sample;
        // snapshotFlow observes that later displacement without polling.
        maintainForYouTopAnchor(
            positionEvents = snapshotFlow { currentPosition() },
            isFirstRowFocused = { firstRecommendationRowFocused },
            awaitRelocation = { kotlinx.coroutines.delay(80) },
            currentPosition = ::currentPosition,
            scrollToTop = { recommendationsListState.animateScrollToItem(0) },
        )
    }

    // The saved-list shortcuts are the stable first row in every state. Focus
    // Watchlist once per entry, matching tvOS, without letting later refreshes
    // pull focus away from the user's current position.
    // rememberSaveable for the same reason as the selection above: these guard
    // a once-per-entry focus grab, and as plain `remember` they reset when an
    // item detail disposes this composition. The effect then re-fires on the
    // way back and slams focus onto the Watchlist pill while the feed is still
    // scrolled where the viewer left it — which is the shell's documented
    // anti-pattern ("fired LaunchedEffects in each screen that imperatively
    // re-focused index 0 — defeating the restorer"). Saved, the grab stays a
    // genuine once-per-entry action and the shell's content restorer is left
    // to put focus back where it was.
    var initialFocusRequested by rememberSaveable { mutableStateOf(false) }
    var lastAppliedFocusRequest by rememberSaveable { mutableStateOf(-1) }
    LaunchedEffect(focusRequest) {
        if (initialFocusRequested && focusRequest == lastAppliedFocusRequest) return@LaunchedEffect
        runCatching { watchlistFocusRequester.requestFocus() }
        onInitialContentFocus()
        initialFocusRequested = true
        lastAppliedFocusRequest = focusRequest
    }

    // TV has no pull-to-refresh, so ON_RESUME is the only quiet self-heal path.
    // The shared VM loads once in init and retains its state across tab swaps, so
    // an empty discover response otherwise leaves this tab a permanent dead end
    // until a profile switch/restart. If the feed is still the empty fallback
    // when the user returns (e.g. after watching and rating content), re-check.
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                val current = viewModel.uiState.value
                if (!current.isLoading && current.sections.visibleOnTv().isEmpty()) {
                    viewModel.refresh()
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        when {
            savedListSelection == SavedListSelection.Watchlist -> TvWatchlistInline(
                onItemClick = onItemClick,
                modifier = Modifier.padding(
                    top = TvTopMenuLayout.contentTopInset + RecommendationsFilterBandHeight,
                ),
            )
            savedListSelection == SavedListSelection.Favorites -> TvFavoritesInline(
                onItemClick = onItemClick,
                modifier = Modifier.padding(
                    top = TvTopMenuLayout.contentTopInset + RecommendationsFilterBandHeight,
                ),
            )
            state.isLoading && state.sections.isEmpty() -> TvLoadingScreen(
                modifier = Modifier.background(MaterialTheme.colorScheme.background),
            )
            state.error != null && state.sections.isEmpty() -> TvErrorScreen(
                message = state.error ?: "Failed to load recommendations",
                onRetry = viewModel::loadRecommendations,
                modifier = Modifier.background(MaterialTheme.colorScheme.background),
            )
            visibleSections.isEmpty() -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background)
                        .padding(horizontal = Spacing.safeArea),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.AutoAwesome,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.72f),
                            modifier = Modifier.size(28.dp),
                        )
                        Text(
                            text = "Not enough data yet",
                            style = MaterialTheme.typography.headlineSmall,
                            color = Color.White,
                        )
                        Text(
                            text = "Watch and rate more content to unlock personalized recommendations.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White.copy(alpha = 0.7f),
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = viewModel::loadRecommendations,
                            contentPadding = PaddingValues(horizontal = 32.dp, vertical = 12.dp),
                        ) {
                            Text("Check again", style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
            }
            else -> {
                CompositionLocalProvider(
                    LocalBringIntoViewSpec provides TvSmoothBringIntoViewSpec,
                ) {
                    LazyColumn(
                        // Hoisted above the `when` so it is not discarded when a
                        // refresh briefly flips this branch to loading/empty and
                        // back — that is what dropped the reader at the top of the
                        // feed after opening an item.
                        state = recommendationsListState,
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.background),
                        verticalArrangement = Arrangement.spacedBy(18.dp),
                        contentPadding = PaddingValues(
                            top = TvTopMenuLayout.contentTopInset + RecommendationsFilterBandHeight,
                            bottom = 24.dp,
                        ),
                    ) {
                        itemsIndexed(
                            items = visibleSections,
                            key = { _, section -> section.id },
                            contentType = { _, _ -> "recommendation-section-row" },
                        ) { index, section ->
                            TvMediaRow(
                                title = section.title,
                                items = section.items,
                                onItemClick = { contentId ->
                                    lastFocusedSectionId = section.id
                                    lastFocusedContentId = contentId
                                    lastFocusedRowIndex = index
                                    lastFocusedCardIndex = section.items.indexOfFirst {
                                        it.contentId == contentId
                                    }.coerceAtLeast(0)
                                    onRecommendationItemClick(contentId)
                                },
                                style = TvRowStyle.Poster,
                                firstItemFocusRequester = firstRecommendationCardFocusRequester
                                    .takeIf { index == 0 },
                                rowContainerFocusRequester = when {
                                    index == resolvedReturnTarget?.rowIndex ->
                                        detailReturnRowFocusRequester
                                    index == 0 -> firstRecommendationRowFocusRequester
                                    else -> null
                                },
                                restoreFocusIndex = resolvedReturnTarget?.cardIndex ?: -1,
                                restoreFocusRequester = detailReturnCardFocusRequester
                                    .takeIf { index == resolvedReturnTarget?.rowIndex },
                                onItemFocusedAtIndex = { item, cardIndex ->
                                    lastFocusedSectionId = section.id
                                    lastFocusedContentId = item.contentId
                                    lastFocusedRowIndex = index
                                    lastFocusedCardIndex = cardIndex
                                },
                                onDirectionUp = if (index == 0) {
                                    {
                                        runCatching { forYouFocusRequester.requestFocus() }
                                            .getOrDefault(false)
                                    }
                                } else {
                                    null
                                },
                                onRowFocusChanged = if (index == 0) {
                                    { focused -> firstRecommendationRowFocused = focused }
                                } else {
                                    null
                                },
                            )
                        }
                        item { Spacer(modifier = Modifier.height(8.dp)) }
                    }
                }
            }
        }

        if (savedListSelection != null && visibleSections.isEmpty()) {
            Text(
                text = "No recommendations yet — showing your saved titles.",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 16.sp,
                    lineHeight = 20.sp,
                ),
                color = Color.White.copy(alpha = 0.75f),
                modifier = Modifier.padding(
                    start = Spacing.safeArea,
                    top = TvTopMenuLayout.contentTopInset + 40.dp,
                ),
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = TvTopMenuLayout.contentTopInset)
                .height(RecommendationsFilterBandHeight)
                .background(MaterialTheme.colorScheme.background)
                .padding(horizontal = Spacing.safeArea),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TvHeroActionPill(
                label = "For You",
                icon = Icons.Outlined.AutoAwesome,
                variant = TvPillVariant.Hollow,
                selected = savedListSelection == null,
                focusRequester = forYouFocusRequester,
                onDirectionDown = moveIntoRecommendations,
                heightOverride = 32.dp,
                horizontalPaddingOverride = 13.dp,
                iconSizeOverride = 10.dp,
                iconLabelSpacingOverride = 5.dp,
                restBorderWidthOverride = 0.75.dp,
                focusedBorderWidthOverride = 1.5.dp,
                focusedScaleOverride = 1.045f,
                focusedGlowElevationOverride = 9.dp,
                labelStyle = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 14.sp,
                    lineHeight = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                ),
                onClick = { savedListSelection = null },
            )
            TvHeroActionPill(
                label = "Watchlist",
                icon = Icons.Filled.Bookmark,
                variant = TvPillVariant.Hollow,
                selected = savedListSelection == SavedListSelection.Watchlist,
                focusRequester = watchlistFocusRequester,
                onDirectionDown = moveIntoRecommendations,
                heightOverride = 32.dp,
                horizontalPaddingOverride = 13.dp,
                iconSizeOverride = 10.dp,
                iconLabelSpacingOverride = 5.dp,
                restBorderWidthOverride = 0.75.dp,
                focusedBorderWidthOverride = 1.5.dp,
                focusedScaleOverride = 1.045f,
                focusedGlowElevationOverride = 9.dp,
                labelStyle = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 14.sp,
                    lineHeight = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                ),
                onClick = { savedListSelection = SavedListSelection.Watchlist },
            )
            TvHeroActionPill(
                label = "Favorites",
                icon = Icons.Filled.Favorite,
                variant = TvPillVariant.Hollow,
                selected = savedListSelection == SavedListSelection.Favorites,
                focusRequester = favoritesFocusRequester,
                onDirectionDown = moveIntoRecommendations,
                heightOverride = 32.dp,
                horizontalPaddingOverride = 13.dp,
                iconSizeOverride = 10.dp,
                iconLabelSpacingOverride = 5.dp,
                restBorderWidthOverride = 0.75.dp,
                focusedBorderWidthOverride = 1.5.dp,
                focusedScaleOverride = 1.045f,
                focusedGlowElevationOverride = 9.dp,
                labelStyle = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 14.sp,
                    lineHeight = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                ),
                onClick = { savedListSelection = SavedListSelection.Favorites },
            )
        }
    }
}
