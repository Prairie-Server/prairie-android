package org.prairieserver.prairie.android.ui.screens.personal

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import org.prairieserver.prairie.android.ui.components.PrairieTopBar

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
            PrairieTopBar(
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
