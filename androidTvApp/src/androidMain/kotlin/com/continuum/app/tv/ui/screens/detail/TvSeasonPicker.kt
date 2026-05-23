package com.continuum.app.tv.ui.screens.detail

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.continuum.app.model.catalog.Season

/**
 * Horizontal scroll of season chips. Mirrors `TVSeasonChipRow` on tvOS:
 * selected reads as a filled white pill, focused fades in a translucent fill,
 * idle is outline only. Auto-centers the selected chip when it changes.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun TvSeasonPicker(
    seasons: List<Season>,
    selectedSeason: Int?,
    onSeasonSelected: (Season) -> Unit,
    modifier: Modifier = Modifier,
    onDirectionUp: (() -> Boolean)? = null,
) {
    if (seasons.isEmpty()) return
    val listState = rememberLazyListState()
    val selectedFocusRequester = remember { FocusRequester() }
    val selectedIndex = remember(selectedSeason, seasons) {
        seasons.indexOfFirst { it.seasonNumber == selectedSeason }.takeIf { it >= 0 }
    }
    LaunchedEffect(selectedIndex) {
        selectedIndex?.let { listState.animateScrollToItem(it) }
    }
    LazyRow(
        modifier = modifier
            .focusProperties {
                selectedIndex?.let {
                    enter = { selectedFocusRequester }
                }
            }
            .then(
                if (onDirectionUp != null) {
                    Modifier.onPreviewKeyEvent { event ->
                        if (event.type == KeyEventType.KeyDown && event.key == Key.DirectionUp) {
                            onDirectionUp()
                        } else {
                            false
                        }
                    }
                } else {
                    Modifier
                },
            )
            .focusGroup(),
        state = listState,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(vertical = 12.dp),
    ) {
        items(seasons, key = { it.seasonNumber }) { season ->
            TvSeasonChip(
                season = season,
                isSelected = season.seasonNumber == selectedSeason,
                onClick = { onSeasonSelected(season) },
                modifier = if (season.seasonNumber == selectedSeason) {
                    Modifier.focusRequester(selectedFocusRequester)
                } else {
                    Modifier
                },
            )
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun TvSeasonChip(
    season: Season,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(100.dp)
    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(shape = shape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (isSelected) Color.White else Color.Transparent,
            contentColor = if (isSelected) Color.Black else Color.White,
            focusedContainerColor = if (isSelected) Color.White else Color.White.copy(alpha = 0.18f),
            focusedContentColor = if (isSelected) Color.Black else Color.White,
            pressedContainerColor = if (isSelected) Color.White else Color.White.copy(alpha = 0.18f),
            pressedContentColor = if (isSelected) Color.Black else Color.White,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.04f),
        border = ClickableSurfaceDefaults.border(
            border = Border(
                border = BorderStroke(
                    width = if (isSelected) 0.dp else 1.5.dp,
                    color = if (isSelected) Color.Transparent else Color.White.copy(alpha = 0.25f),
                ),
                shape = shape,
            ),
            focusedBorder = Border(
                border = BorderStroke(0.dp, Color.Transparent),
                shape = shape,
            ),
        ),
        modifier = modifier,
    ) {
        Text(
            text = season.displayLabel(),
            style = MaterialTheme.typography.titleMedium.copy(
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
            ),
            modifier = Modifier.padding(horizontal = 26.dp, vertical = 14.dp),
        )
    }
}

private fun Season.displayLabel(): String {
    if (isSpecials) return "Specials"
    return title?.takeIf { it.isNotBlank() } ?: "Season $seasonNumber"
}
