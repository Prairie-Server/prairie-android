package com.continuum.app.tv.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BookmarkAdded
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import com.continuum.app.tv.ui.theme.SelectedContainer

/**
 * Watchlist toggle styled to match the shared hero action row treatment.
 */
@Composable
fun TvWatchlistButton(
    inWatchlist: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    downFocusRequester: FocusRequester? = null,
    onDirectionDown: (() -> Boolean)? = null,
) {
    TvHeroToggleIconButton(
        selected = inWatchlist,
        selectedContainerColor = SelectedContainer.copy(alpha = 0.9f),
        selectedContentColor = Color.White,
        icon = if (inWatchlist) Icons.Default.BookmarkAdded else Icons.Outlined.BookmarkBorder,
        contentDescription = if (inWatchlist) "Remove from watchlist" else "Add to watchlist",
        onClick = onToggle,
        modifier = modifier,
        downFocusRequester = downFocusRequester,
        onDirectionDown = onDirectionDown,
    )
}
