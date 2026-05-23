package com.continuum.app.android.ui.screens.personal

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import com.continuum.app.android.ui.components.ContinuumTopBar

/**
 * Screen displaying the user's favorite items in a grid layout.
 *
 * Features:
 * - Pull-to-refresh
 * - Infinite scroll pagination
 * - Tap to navigate to item detail
 * - Watched badge overlay on played items
 */
@Composable
fun FavoritesScreen(
    onBackClick: () -> Unit,
    onItemClick: (String) -> Unit,
) {
    Scaffold(
        topBar = {
            ContinuumTopBar(
                title = "Favorites",
                onBackClick = onBackClick,
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        FavoritesGridContent(
            onItemClick = onItemClick,
            contentPadding = padding,
        )
    }
}
