package org.siloserver.silo.tv.ui.screens.recommendations

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import org.siloserver.silo.tv.ui.components.TvErrorScreen
import org.siloserver.silo.tv.ui.components.TvLoadingScreen
import org.siloserver.silo.tv.ui.components.TvMediaRow
import org.siloserver.silo.tv.ui.components.TvRowStyle
import org.siloserver.silo.tv.ui.theme.Spacing
import org.siloserver.silo.tv.ui.util.visibleOnTv
import org.siloserver.silo.viewmodel.RecommendationsViewModel
import org.koin.compose.viewmodel.koinViewModel

/**
 * "For You" tab. Reuses the shared [RecommendationsViewModel] that drives
 * the phone `/recommendations/discover` feed. Layout mirrors [TvHomeScreen]
 * (rows down the page) minus the featured hero — the discover API returns
 * section-style rows, not a hero card.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TvRecommendationsScreen(
    onItemClick: (contentId: String) -> Unit,
    onInitialContentFocus: () -> Unit = {},
    viewModel: RecommendationsViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val visibleSections = remember(state.sections) { state.sections.visibleOnTv() }
    val firstItemFocusRequester = remember { FocusRequester() }
    val firstSectionId = visibleSections.firstOrNull { it.items.isNotEmpty() }?.id

    // Gate the initial focus jump so a silent ViewModel re-emission (refresh,
    // background update) doesn't yank the user back to row 1, card 1 after
    // they've scrolled. We only fire focus once per screen entry.
    var initialFocusRequested by remember { mutableStateOf(false) }
    LaunchedEffect(firstSectionId) {
        if (initialFocusRequested || firstSectionId == null) return@LaunchedEffect
        runCatching { firstItemFocusRequester.requestFocus() }
        onInitialContentFocus()
        initialFocusRequested = true
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

    when {
        state.isLoading && state.sections.isEmpty() -> TvLoadingScreen(
            modifier = Modifier.background(MaterialTheme.colorScheme.background),
        )
        state.error != null && state.sections.isEmpty() -> TvErrorScreen(
            message = state.error ?: "Failed to load recommendations",
            onRetry = viewModel::loadRecommendations,
            modifier = Modifier.background(MaterialTheme.colorScheme.background),
        )
        visibleSections.isEmpty() -> {
            // The empty fallback must carry a focusable element so the D-pad has
            // somewhere to land and the tab can't strand the user (mirrors the
            // error branch's focusable retry). Grab focus so first entry lands
            // here rather than in the top menu — but only once per screen entry
            // (shared flag with the content branch), so an empty refresh result
            // can't yank focus away mid-browse.
            val retryFocusRequester = remember { FocusRequester() }
            LaunchedEffect(Unit) {
                if (!initialFocusRequested) {
                    runCatching { retryFocusRequester.requestFocus() }
                    onInitialContentFocus()
                    initialFocusRequested = true
                }
            }
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
                        modifier = Modifier.focusRequester(retryFocusRequester),
                    ) {
                        Text("Check again", style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
        else -> {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background),
                verticalArrangement = Arrangement.spacedBy(18.dp),
                contentPadding = PaddingValues(
                    top = Spacing.heroTopSafe,
                    bottom = 24.dp,
                ),
            ) {
                items(
                    items = visibleSections,
                    key = { it.id },
                    contentType = { "recommendation-section-row" },
                ) { section ->
                    TvMediaRow(
                        title = section.title,
                        items = section.items,
                        onItemClick = onItemClick,
                        style = TvRowStyle.Poster,
                        firstItemFocusRequester = firstItemFocusRequester.takeIf { section.id == firstSectionId },
                    )
                }
                item { Spacer(modifier = Modifier.height(8.dp)) }
            }
        }
    }
}
