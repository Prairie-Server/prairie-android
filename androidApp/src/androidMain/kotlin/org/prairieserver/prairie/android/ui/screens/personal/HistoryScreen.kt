package org.prairieserver.prairie.android.ui.screens.personal

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import org.prairieserver.prairie.android.ui.components.PrairieTopBar
import org.prairieserver.prairie.common.R as CommonR

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
            PrairieTopBar(
                title = stringResource(CommonR.string.personal_history_title),
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
