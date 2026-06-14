package com.continuum.app.tv.ui.screens.search

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon as M3Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.continuum.app.tv.ui.components.TvCatalogGrid
import com.continuum.app.tv.ui.components.TvFilterChip
import com.continuum.app.tv.ui.components.tvOutlinedTextFieldColors
import com.continuum.app.tv.ui.shell.TvTopMenuLayout
import com.continuum.app.tv.ui.theme.ContinuumBlue
import com.continuum.app.tv.ui.theme.ContinuumBlueBorderIdle
import com.continuum.app.tv.ui.theme.ElevatedSurface
import com.continuum.app.tv.ui.theme.Spacing
import com.continuum.app.tv.ui.theme.sectionEyebrow
import com.continuum.app.tv.ui.theme.tvPageContentPadding
import com.continuum.app.tv.ui.theme.tvPageStartPadding
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TvSearchScreen(
    onItemClick: (contentId: String) -> Unit,
    searchFieldFocusRequester: FocusRequester? = null,
    viewModel: TvSearchViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val firstResultFocusRequester = remember { FocusRequester() }
    val firstFilterChipFocusRequester = remember { FocusRequester() }
    val internalSearchFieldFocusRequester = remember { FocusRequester() }
    val activeSearchFieldFocusRequester = searchFieldFocusRequester ?: internalSearchFieldFocusRequester

    LaunchedEffect(activeSearchFieldFocusRequester) {
        runCatching { activeSearchFieldFocusRequester.requestFocus() }
    }
    // Note: we deliberately do NOT auto-jump focus to the first result when
    // it appears. Doing so during the debounced as-you-type search yanks
    // focus out of the IME mid-keystroke. Instead, the explicit
    // focusProperties wired below give the user three reliable handoffs:
    //   • search field —DOWN→ first chip
    //   • first chip   —DOWN→ first card (when results exist)
    //   • first card   —UP→   first chip
    // and the IME's Search action still snaps focus straight into the grid.

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        TvCatalogGrid(
            items = state.items,
            isLoading = state.isLoading || state.isLoadingMore,
            hasMore = state.hasMore,
            onItemClick = onItemClick,
            onLoadMore = viewModel::loadMore,
            modifier = Modifier.fillMaxSize(),
            minCellWidth = 152.dp,
            contentPadding = tvPageContentPadding(
                top = TvTopMenuLayout.contentTopInset,
                bottom = Spacing.xxxl,
                end = 48.dp,
                expandedGap = Spacing.md,
            ),
            horizontalSpacing = 14.dp,
            verticalSpacing = 20.dp,
            firstItemFocusRequester = firstResultFocusRequester,
            // UP from the first card always lands back on the filter chip rail.
            // Without this Compose's spatial focus search prefers the wider
            // OutlinedTextField above and skips over the smaller chip row.
            firstItemCardModifier = Modifier.focusProperties {
                up = firstFilterChipFocusRequester
            },
            header = {
                SearchStage(
                    query = state.query,
                    mediaType = state.mediaType,
                    resultStatus = searchStatusText(
                        query = state.query,
                        total = state.total,
                        isSearching = state.isLoading,
                        error = state.error,
                    ),
                    hasResults = state.items.isNotEmpty(),
                    searchFieldFocusRequester = activeSearchFieldFocusRequester,
                    firstFilterChipFocusRequester = firstFilterChipFocusRequester,
                    firstResultFocusRequester = firstResultFocusRequester,
                    onQueryChanged = viewModel::onQueryChanged,
                    onSearchSubmitted = viewModel::submitSearch,
                    onMediaTypeChanged = viewModel::onMediaTypeChanged,
                    onResultsFocusRequested = {
                        runCatching { firstResultFocusRequester.requestFocus() }
                    },
                )
            },
            emptyState = {
                when {
                    state.query.isBlank() -> SearchFeedbackCard(
                        title = "Start typing to search your library",
                        body = "Results update as you type, and Search jumps straight into the grid.",
                    )
                    state.error != null -> SearchFeedbackCard(
                        title = "Search is unavailable right now",
                        body = state.error!!,
                    )
                    else -> SearchFeedbackCard(
                        title = "No matches for “${state.query}”",
                        body = "Try a shorter title or switch media filters.",
                    )
                }
            },
        )
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun SearchStage(
    query: String,
    mediaType: TvSearchMediaType,
    resultStatus: String?,
    hasResults: Boolean,
    searchFieldFocusRequester: FocusRequester,
    firstFilterChipFocusRequester: FocusRequester,
    firstResultFocusRequester: FocusRequester,
    onQueryChanged: (String) -> Unit,
    onSearchSubmitted: () -> Unit,
    onMediaTypeChanged: (TvSearchMediaType) -> Unit,
    onResultsFocusRequested: () -> Unit,
) {
    val startPadding = tvPageStartPadding(expandedGap = Spacing.md)
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val mediaTypes = remember { TvSearchMediaType.values().toList() }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = startPadding,
                end = 48.dp,
                top = 0.dp,
                bottom = Spacing.xs,
            )
            .widthIn(max = 760.dp),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "SEARCH",
                style = sectionEyebrow,
                color = ContinuumBlue.copy(alpha = 0.78f),
            )

            if (resultStatus != null) {
                SearchStatusPill(text = resultStatus)
            }
        }

        OutlinedTextField(
            value = query,
            onValueChange = onQueryChanged,
            leadingIcon = {
                M3Icon(
                    imageVector = Icons.Filled.Search,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.72f),
                )
            },
            placeholder = {
                androidx.compose.material3.Text(
                    text = "Search titles, movies, series, and audiobooks",
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontSize = 20.sp,
                        lineHeight = 24.sp,
                        letterSpacing = 0.sp,
                    ),
                    color = Color.White.copy(alpha = 0.56f),
                )
            },
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyLarge.copy(
                fontSize = 20.sp,
                lineHeight = 24.sp,
                letterSpacing = 0.sp,
                color = Color.White,
            ),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Text,
                imeAction = ImeAction.Search,
            ),
            keyboardActions = KeyboardActions(
                onSearch = {
                    onSearchSubmitted()
                    focusManager.clearFocus(force = true)
                    keyboardController?.hide()
                    if (hasResults) {
                        runCatching { onResultsFocusRequested() }
                    } else {
                        runCatching { firstFilterChipFocusRequester.requestFocus() }
                    }
                },
            ),
            colors = tvOutlinedTextFieldColors(
                focusedContainerColor = ElevatedSurface,
                unfocusedContainerColor = ElevatedSurface,
                focusedBorderColor = Color.White.copy(alpha = 0.34f),
                unfocusedBorderColor = ContinuumBlueBorderIdle,
            ),
            shape = RoundedCornerShape(18.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(62.dp)
                .focusRequester(searchFieldFocusRequester)
                // Pin DOWN to the chip rail so the user can always step from
                // the search field onto the All/Movies/Series filters,
                // regardless of whether result cards are also rendered below.
                .focusProperties { down = firstFilterChipFocusRequester },
        )

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(end = Spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            itemsIndexed(mediaTypes) { index, type ->
                val chipModifier = Modifier
                    .then(
                        if (index == 0) {
                            Modifier.focusRequester(firstFilterChipFocusRequester)
                        } else {
                            Modifier
                        },
                    )
                    .then(
                        // Only redirect DOWN when there's actually a card to
                        // land on — pointing at an unattached FocusRequester
                        // makes the key event a no-op and traps the user.
                        if (hasResults) {
                            Modifier.focusProperties { down = firstResultFocusRequester }
                        } else {
                            Modifier
                        },
                    )
                TvFilterChip(
                    text = type.label,
                    selected = mediaType == type,
                    onClick = { onMediaTypeChanged(type) },
                    modifier = chipModifier,
                )
            }
        }
    }
}

@Composable
private fun SearchStatusPill(text: String) {
    val shape = RoundedCornerShape(100.dp)
    Box(
        modifier = Modifier
            .clip(shape)
            .background(Color.White.copy(alpha = 0.045f))
            .border(1.dp, Color.White.copy(alpha = 0.08f), shape)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = Color.White.copy(alpha = 0.68f),
        )
    }
}

@Composable
private fun SearchFeedbackCard(
    title: String,
    body: String,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(24.dp)

    Box(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .widthIn(max = 680.dp)
                .clip(shape)
                .background(ElevatedSurface)
                .border(1.dp, Color.White.copy(alpha = 0.05f), shape)
                .padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.06f)),
                contentAlignment = Alignment.Center,
            ) {
                M3Icon(
                    imageVector = Icons.Filled.Search,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.72f),
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Text(
                    text = body,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.62f),
                )
            }
        }
    }
}

private fun searchStatusText(
    query: String,
    total: Int,
    isSearching: Boolean,
    error: String?,
): String? = when {
    query.isBlank() -> null
    isSearching -> "Searching…"
    error != null && total == 0 -> "Search unavailable"
    total == 0 -> "No results"
    total == 1 -> "1 result"
    else -> "$total results"
}
