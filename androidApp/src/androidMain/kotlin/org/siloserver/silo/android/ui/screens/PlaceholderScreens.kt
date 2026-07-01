package org.siloserver.silo.android.ui.screens

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import org.siloserver.silo.android.ui.components.PlaceholderScreen

/**
 * Placeholder composables for tab content inside MainScreen.
 * Downloads and Settings are now real implementations.
 * Other agents will replace Home and Search with real screen implementations.
 */

@Composable
fun HomePlaceholder(navController: NavHostController) =
    PlaceholderScreen("Home")

@Composable
fun SearchPlaceholder(navController: NavHostController) =
    PlaceholderScreen("Search")
