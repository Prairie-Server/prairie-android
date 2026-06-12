package com.continuum.app.android.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Headphones
import androidx.compose.material.icons.outlined.LiveTv
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding

/**
 * Bottom navigation tabs for the main scaffold.
 */
enum class Tab(
    val route: String,
    val label: String,
    val icon: ImageVector,
    val selectedIcon: ImageVector,
) {
    Video(Route.Video.route, "Video", Icons.Outlined.LiveTv, Icons.Filled.LiveTv),
    Audio(Route.Audio.route, "Audio", Icons.Outlined.Headphones, Icons.Filled.Headphones),
    Reading(
        Route.Reading.route,
        "Reading",
        Icons.AutoMirrored.Outlined.MenuBook,
        Icons.AutoMirrored.Filled.MenuBook,
    ),
    Downloads(
        Route.Downloads.route,
        "Downloads",
        Icons.Outlined.Download,
        Icons.Filled.Download,
    );

    companion object {
        // Temporary aliases for legacy route call sites until the shell is
        // fully rewired to media-mode routes.
        val Home: Tab = Video
        val Libraries: Tab = Audio
        val Recommendations: Tab = Reading
    }
}

/**
 * Material 3 bottom navigation bar themed for Silo's dark-first design.
 */
@Composable
fun ContinuumBottomNavBar(
    currentTab: Tab,
    onTabSelected: (Tab) -> Unit,
    // Caller decides which tabs to render — used to hide the Downloads tab
    // when the user has no downloads in flight or on disk. Defaults to all
    // tabs for backwards-compat.
    tabs: List<Tab> = Tab.entries.toList(),
) {
    // Paint the bar background on the outer Box so it extends behind the
    // gesture-nav inset, then apply the inset as padding around the
    // NavigationBar itself. This keeps a clean 72dp content area for the
    // items so they sit vertically centered, instead of getting squeezed
    // toward the top by NavigationBar's internal inset padding.
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background.copy(alpha = 0.96f))
            .navigationBarsPadding(),
    ) {
        NavigationBar(
            containerColor = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.onSurface,
            tonalElevation = 0.dp,
            windowInsets = WindowInsets(0),
            modifier = Modifier.height(60.dp),
        ) {
            tabs.forEach { tab ->
                val selected = tab == currentTab
                NavigationBarItem(
                    selected = selected,
                    onClick = { onTabSelected(tab) },
                    icon = {
                        Icon(
                            imageVector = if (selected) tab.selectedIcon else tab.icon,
                            contentDescription = tab.label,
                        )
                    },
                    label = { Text(text = tab.label, style = MaterialTheme.typography.labelSmall) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.onSurface,
                        selectedTextColor = MaterialTheme.colorScheme.onSurface,
                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        indicatorColor = Color.White.copy(alpha = 0.08f),
                    ),
                )
            }
        }
    }
}
