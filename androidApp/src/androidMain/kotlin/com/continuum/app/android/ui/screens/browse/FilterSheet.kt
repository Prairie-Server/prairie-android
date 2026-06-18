package com.continuum.app.android.ui.screens.browse

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.continuum.app.android.ui.theme.ContinuumSurfaceElevated
import com.continuum.app.model.catalog.CatalogFiltersResponse

private val sortOptions = listOf(
    "added_at" to "Date Added",
    "title" to "Title",
    "year" to "Release Year",
    "rating_imdb" to "IMDb Rating",
)

private val orderOptions = listOf(
    "desc" to "Descending",
    "asc" to "Ascending",
)

/**
 * Bottom sheet presenting filter and sort options for the browse screen.
 *
 * Allows multi-select for genres and content ratings, single-select for
 * sort field and order, with "Apply" and "Reset" actions.
 *
 * @param currentFilters Currently active filter selections.
 * @param availableFilters Available filter values from the server.
 * @param onApply Callback with the updated filters.
 * @param onReset Callback to reset all filters.
 * @param onDismiss Callback when the sheet is dismissed.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun FilterSheet(
    currentFilters: BrowseFilters,
    availableFilters: CatalogFiltersResponse?,
    onApply: (BrowseFilters) -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var selectedGenres by remember(currentFilters) { mutableStateOf(currentFilters.genres) }
    var selectedRatings by remember(currentFilters) { mutableStateOf(currentFilters.contentRatings) }
    var selectedSort by remember(currentFilters) { mutableStateOf(currentFilters.sort) }
    var selectedOrder by remember(currentFilters) { mutableStateOf(currentFilters.order) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            Text(
                text = "Filters",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(bottom = 16.dp),
            )

            // Sort by — iOS phone FilterView lists Sort By first, Genre second.
            Text(
                text = "Sort By",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(bottom = 8.dp),
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                sortOptions.forEach { (value, label) ->
                    FilterChip(
                        selected = selectedSort == value,
                        onClick = { selectedSort = value },
                        label = { Text(label) },
                        colors = filterSheetChipColors(selectedSort == value),
                        border = null,
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Sort order
            Text(
                text = "Sort Order",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(bottom = 8.dp),
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                orderOptions.forEach { (value, label) ->
                    FilterChip(
                        selected = selectedOrder == value,
                        onClick = { selectedOrder = value },
                        label = { Text(label) },
                        colors = filterSheetChipColors(selectedOrder == value),
                        border = null,
                    )
                }
            }

            // Genre multi-select — iOS largePadding (24) between sections.
            if (availableFilters != null && availableFilters.genres.isNotEmpty()) {
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    text = "Genre",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    availableFilters.genres.forEach { genre ->
                        val selected = genre in selectedGenres
                        FilterChip(
                            selected = selected,
                            onClick = {
                                selectedGenres = if (selected) {
                                    selectedGenres - genre
                                } else {
                                    selectedGenres + genre
                                }
                            },
                            label = { Text(genre) },
                            colors = filterSheetChipColors(selected),
                            border = null,
                        )
                    }
                }
            }

            // Content rating multi-select
            if (availableFilters != null && availableFilters.contentRatings.isNotEmpty()) {
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    text = "Content Rating",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    availableFilters.contentRatings.forEach { rating ->
                        val selected = rating in selectedRatings
                        FilterChip(
                            selected = selected,
                            onClick = {
                                selectedRatings = if (selected) {
                                    selectedRatings - rating
                                } else {
                                    selectedRatings + rating
                                }
                            },
                            label = { Text(rating) },
                            colors = filterSheetChipColors(selected),
                            border = null,
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Action buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedButton(
                    onClick = {
                        onReset()
                        onDismiss()
                    },
                    modifier = Modifier.weight(1f),
                    colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.onSurface,
                    ),
                    border = androidx.compose.foundation.BorderStroke(
                        width = 1.dp,
                        color = Color.White.copy(alpha = 0.1f),
                    ),
                ) {
                    Text("Reset")
                }
                Button(
                    onClick = {
                        onApply(
                            BrowseFilters(
                                genres = selectedGenres,
                                contentRatings = selectedRatings,
                                sort = selectedSort,
                                order = selectedOrder,
                            )
                        )
                        onDismiss()
                    },
                    modifier = Modifier.weight(1f),
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                        containerColor = Color.White,
                        contentColor = Color.Black,
                    ),
                ) {
                    Text("Apply")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

// iOS phone FilterView chip palette: selected = inverted onSurface bg with
// background-colored text; unselected = surfaceElevated bg with secondary text.
@Composable
private fun filterSheetChipColors(selected: Boolean) = FilterChipDefaults.filterChipColors(
    selectedContainerColor = MaterialTheme.colorScheme.onSurface,
    selectedLabelColor = MaterialTheme.colorScheme.background,
    selectedLeadingIconColor = MaterialTheme.colorScheme.background,
    containerColor = ContinuumSurfaceElevated,
    labelColor = if (selected) {
        MaterialTheme.colorScheme.background
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    },
)
