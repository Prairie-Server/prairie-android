package com.continuum.app.android.ui.screens.recommendations

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.continuum.app.android.ui.screens.home.HomeSectionRow
import com.continuum.app.viewmodel.RecommendationsViewModel
import org.koin.compose.viewmodel.koinViewModel

/**
 * Phone Recommendations ("For You") screen.
 *
 * Mirrors iOS `RecommendationsView.swift` (phone) 1:1: a saved-shortcuts pill
 * row (Watchlist / Favorites) above the recommendation section rows, the same
 * SectionRow layout used by Home, iOS section spacing, and the iOS sparkles
 * empty state. The screen title + actions header is supplied by the shared
 * `MainAppTopBar` in `MainScreen` (matching iOS `TabTopBarActions`).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecommendationsScreen(
    onItemClick: (String) -> Unit,
    contentTopPadding: Dp = 0.dp,
    onWatchlistClick: () -> Unit = {},
    onFavoritesClick: () -> Unit = {},
    viewModel: RecommendationsViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsState()

    when {
        state.isLoading && state.sections.isEmpty() -> {
            // iOS phone loading state is an empty (Color.clear) placeholder.
            Box(modifier = Modifier.fillMaxSize().padding(top = contentTopPadding))
        }

        state.error != null && state.sections.isEmpty() -> {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp, vertical = contentTopPadding + 24.dp),
                verticalArrangement = Arrangement.Center,
            ) {
                Icon(
                    imageVector = Icons.Outlined.ErrorOutline,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = state.error ?: "Failed to load recommendations",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Try reloading your personalized feed.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(20.dp))
                Button(onClick = { viewModel.loadRecommendations() }) {
                    Text("Retry")
                }
            }
        }

        else -> {
            PullToRefreshBox(
                isRefreshing = state.isRefreshing,
                onRefresh = { viewModel.refresh() },
                modifier = Modifier.fillMaxSize(),
            ) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    // Keep room for the floating bottom nav while preserving iOS
                    // section rhythm inside the list.
                    contentPadding = PaddingValues(bottom = 96.dp),
                    // iOS sectionSpacing (phone) = largePadding (24).
                    verticalArrangement = Arrangement.spacedBy(24.dp),
                ) {
                    item {
                        Spacer(modifier = Modifier.height(contentTopPadding + 8.dp))
                    }

                    // iOS renders the SavedShortcutsRow above the sections at all
                    // times (it is not gated on having recommendations).
                    item {
                        SavedShortcutsRow(
                            onWatchlistClick = onWatchlistClick,
                            onFavoritesClick = onFavoritesClick,
                            modifier = Modifier.padding(horizontal = 16.dp),
                        )
                    }

                    if (state.sections.isEmpty()) {
                        item {
                            RecommendationsEmptyState(
                                modifier = Modifier.padding(top = 24.dp),
                            )
                        }
                    } else {
                        items(
                            items = state.sections,
                            key = { it.id },
                        ) { section ->
                            HomeSectionRow(
                                section = section,
                                onItemClick = onItemClick,
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Watchlist / Favorites pill row. Mirrors iOS `SavedShortcutsRow` (phone):
 * HStack spacing 12, capsule pills 40 tall with 15 horizontal padding, a
 * 1.5pt white-30% border, and a 14sp-semibold title with a 13sp-semibold icon.
 */
@Composable
private fun SavedShortcutsRow(
    onWatchlistClick: () -> Unit,
    onFavoritesClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SavedShortcutPill(
            title = "Watchlist",
            icon = Icons.Filled.Bookmark,
            onClick = onWatchlistClick,
        )
        SavedShortcutPill(
            title = "Favorites",
            icon = Icons.Filled.Favorite,
            onClick = onFavoritesClick,
        )
    }
}

@Composable
private fun SavedShortcutPill(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
) {
    OutlinedButton(
        onClick = onClick,
        shape = CircleShape,
        contentPadding = PaddingValues(horizontal = 15.dp),
        border = BorderStroke(1.5.dp, Color.White.copy(alpha = 0.3f)),
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = MaterialTheme.colorScheme.onSurface,
        ),
        modifier = Modifier.height(40.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(13.dp),
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = title,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
        )
    }
}

/**
 * iOS phone empty state: a sparkles glyph, a 14sp-bold headline, and a 12sp
 * caption explaining the screen will learn what the viewer likes.
 */
@Composable
private fun RecommendationsEmptyState(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            imageVector = Icons.Outlined.AutoAwesome,
            contentDescription = null,
            modifier = Modifier.size(44.dp),
            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
        )
        Text(
            text = "No recommendations yet",
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = "Watch some content and we'll learn what you like.",
            fontSize = 12.sp,
            fontWeight = FontWeight.Normal,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 24.dp),
        )
    }
}
