package com.continuum.app.android.ui.screens.personal

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import com.continuum.app.android.ui.components.ContinuumTopBar

/**
 * Screen displaying the user's watchlist items in a grid layout.
 *
 * Same pattern as FavoritesScreen: pull-to-refresh, infinite scroll, tap to detail.
 */
@Composable
fun WatchlistScreen(
    onBackClick: () -> Unit,
    onItemClick: (String) -> Unit,
) {
    Scaffold(
        topBar = {
            ContinuumTopBar(
                title = "Watchlist",
                onBackClick = onBackClick,
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        WatchlistGridContent(
            onItemClick = onItemClick,
            contentPadding = padding,
        )
    }
}
