package org.prairieserver.prairie.android.ui.screens.personal

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import org.prairieserver.prairie.android.ui.components.PrairieTopBar

@Composable
fun PersonalListsScreen(
    onBackClick: () -> Unit,
    onItemClick: (String) -> Unit,
) {
    var selectedTabIndex by rememberSaveable { mutableIntStateOf(0) }
    val tabs = listOf("Favorites", "Watchlist")

    Scaffold(
        topBar = {
            PrairieTopBar(
                title = "Favorites & Watchlist",
                onBackClick = onBackClick,
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            TabRow(selectedTabIndex = selectedTabIndex) {
                tabs.forEachIndexed { index, label ->
                    Tab(
                        selected = selectedTabIndex == index,
                        onClick = { selectedTabIndex = index },
                        text = { Text(label) },
                    )
                }
            }

            when (selectedTabIndex) {
                0 -> FavoritesGridContent(
                    onItemClick = onItemClick,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxSize(),
                )
                else -> WatchlistGridContent(
                    onItemClick = onItemClick,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxSize(),
                )
            }
        }
    }
}
