package org.siloserver.silo.android.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.navigation.NavHostController
import androidx.navigation.NavOptionsBuilder
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
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
 * Total height of the translucent bottom chrome (cast mini bar + nav bar +
 * gesture inset) as measured by the main Scaffold. Tab content scrolls
 * edge-to-edge underneath the chrome (iOS glass tab bar behavior), so
 * scrollable screens add this to their bottom content padding to keep their
 * last items reachable above it. Zero outside the tab scaffold.
 */
val LocalBottomChromeInset = compositionLocalOf { 0.dp }

/**
 * Bottom navigation tabs for the main scaffold.
 */
enum class Tab(
    val route: String,
    val label: String,
    val icon: ImageVector,
    val selectedIcon: ImageVector,
) {
    Home(Route.Home.route, "Home", Icons.Outlined.Home, Icons.Filled.Home),
    Libraries(Route.Libraries.route, "Libraries", Icons.Outlined.GridView, Icons.Filled.GridView),
    ForYou(Route.Recommendations.route, "For You", Icons.Outlined.AutoAwesome, Icons.Filled.AutoAwesome),
    Calendar(Route.Calendar.route, "Calendar", Icons.Outlined.CalendarMonth, Icons.Filled.CalendarMonth),
    Downloads(
        Route.Downloads.route,
        "Downloads",
        Icons.Outlined.Download,
        Icons.Filled.Download,
    ),
}

/**
 * The tab destination sitting lowest on the back stack — the anchor tab
 * switching pops to.
 *
 * Derived from the live stack rather than remembered: `MainScreen` is composed
 * per tab destination, so a remembered anchor gave every tab its own copy, and
 * the graph's declared start destination keeps naming a tab even after that tab
 * is removed. Popping to a route that is not on the stack pops nothing, so
 * every tab tap stacked and Back walked back through previously visited tabs.
 *
 * This finds the oldest tab entry deterministically; what needs care is turning
 * it back into a route string, because `popUpTo(route)` resolves to the NEWEST
 * matching entry. The result is therefore unambiguous only while a tab route
 * appears at most once. Every path in this build that can add a tab entry keeps
 * that true: tab switching and the disappearing-tab cleanup both collapse to
 * the anchor before pushing, external tab links switch rather than push, and the
 * legacy-route aliases pop themselves inclusively before navigating, so their
 * `launchSingleTop` sees Home on top when Home sat immediately below them.
 * Duplicate tab routes are otherwise unsupported — a back stack restored from an
 * older build could arrive holding them, and this does not repair that, so older
 * tab entries may be left underneath.
 */
internal fun NavHostController.bottomMostTabRoute(): String? {
    val tabRoutes = Tab.entries.mapTo(mutableSetOf()) { it.route }
    return currentBackStack.value
        .firstOrNull { entry -> entry.destination.route in tabRoutes }
        ?.destination
        ?.route
}

/** The route's tab, if it is one. */
internal fun tabForRoute(route: String): Tab? = Tab.entries.firstOrNull { it.route == route }

/**
 * Standard tab-switch options: replace the current tab rather than stack it,
 * preserving each tab's own state.
 *
 * External links to a tab use these too, so `silo://downloads` behaves exactly
 * like tapping Downloads — one definition of what entering a tab means, rather
 * than two that drift.
 */
internal fun NavOptionsBuilder.tabSwitchNavOptions(anchorRoute: String?) {
    anchorRoute?.let { popUpTo(it) { saveState = true } }
    launchSingleTop = true
    restoreState = true
}

/**
 * Material 3 bottom navigation bar themed for Silo's dark-first design.
 */
@Composable
fun SiloBottomNavBar(
    currentTab: Tab,
    onTabSelected: (Tab) -> Unit,
    // Caller decides which tabs to render — used to hide the Downloads tab
    // when the user has no downloads in flight or on disk. Defaults to all
    // tabs for backwards-compat.
    tabs: List<Tab> = Tab.entries.toList(),
) {
    // Paint the bar background on the outer Box so it extends behind the
    // gesture-nav inset, then apply the inset as padding around the
    // NavigationBar itself. This keeps a clean 60dp content area for the
    // items so they sit vertically centered, instead of getting squeezed
    // toward the top by NavigationBar's internal inset padding.
    //
    // Translucent glass (iOS tab bar): content scrolls edge-to-edge beneath
    // the bar, so the fill is a light-to-heavier scrim — enough see-through
    // to read as glass, enough ink to keep labels legible over bright
    // posters — capped with the same hairline the top chrome uses. True
    // backdrop blur needs API 31 + a blur pipeline; the scrim is the
    // dependency-free equivalent.
    val glass = MaterialTheme.colorScheme.background
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    0f to glass.copy(alpha = 0.72f),
                    1f to glass.copy(alpha = 0.94f),
                ),
            ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(0.75.dp)
                .background(Color.White.copy(alpha = 0.08f)),
        )
        Box(modifier = Modifier.navigationBarsPadding()) {
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
}
