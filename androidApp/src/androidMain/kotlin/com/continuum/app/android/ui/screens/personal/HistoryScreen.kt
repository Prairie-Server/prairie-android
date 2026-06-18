package com.continuum.app.android.ui.screens.personal

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import com.continuum.app.android.ui.components.ContinuumTopBar

/**
 * Screen displaying the user's watch history in a poster grid layout,
 * matching the Favorites and Watchlist grids exactly.
 */
@Composable
fun HistoryScreen(
    onBackClick: () -> Unit,
    onItemClick: (String) -> Unit,
) {
    Scaffold(
        topBar = {
            ContinuumTopBar(
                title = "History",
                onBackClick = onBackClick,
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        HistoryGridContent(
            onItemClick = onItemClick,
            contentPadding = padding,
        )
    }
}
