package com.continuum.app.tv.ui.screens.people

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.continuum.app.common.ui.components.ThumbhashImage
import com.continuum.app.model.catalog.Person
import com.continuum.app.tv.ui.components.TvCatalogEmptyState
import com.continuum.app.tv.ui.components.TvCatalogGrid
import com.continuum.app.tv.ui.components.TvErrorScreen
import com.continuum.app.tv.ui.components.TvLoadingScreen
import com.continuum.app.tv.ui.theme.DarkSurfaceElevated
import com.continuum.app.tv.ui.theme.Spacing
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * Android TV person detail surface — the cast/crew profile plus their
 * filmography. Mirrors the phone `PersonDetailScreen`: a header (large portrait
 * + name + birth/death/birthplace badges + bio) over a media-type filter row
 * and a poster grid.
 *
 * TV idioms follow [com.continuum.app.tv.ui.screens.library.TvLibraryDetailScreen]:
 * a 6-column `LazyVerticalGrid` with the editorial header spanning the full
 * width as the first item, `TvMediaCard` posters with d-pad focus, and a
 * focus-claiming `LaunchedEffect` so the remote lands on the first title once
 * the filmography loads. `onOpenItemDetail` routes a poster to its detail page.
 */
@Composable
fun TvPersonDetailScreen(
    personId: Int,
    onOpenItemDetail: (contentId: String) -> Unit,
    onBack: () -> Unit,
    viewModel: TvPersonDetailViewModel = koinViewModel(
        key = "person-$personId",
        parameters = { parametersOf(personId) },
    ),
) {
    val state by viewModel.uiState.collectAsState()

    BackHandler(enabled = true) { onBack() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        when {
            state.isLoading && state.person == null -> TvLoadingScreen(
                modifier = Modifier.background(MaterialTheme.colorScheme.background),
            )
            state.error != null && state.person == null -> TvErrorScreen(
                message = state.error!!,
                onRetry = viewModel::reload,
                modifier = Modifier.background(MaterialTheme.colorScheme.background),
            )
            state.person != null -> TvPersonDetailContent(
                person = state.person!!,
                state = state,
                onFilterSelected = viewModel::applyFilter,
                onOpenItemDetail = onOpenItemDetail,
            )
        }
    }
}

@Composable
private fun TvPersonDetailContent(
    person: Person,
    state: TvPersonDetailUiState,
    onFilterSelected: (TvPersonMediaFilter) -> Unit,
    onOpenItemDetail: (contentId: String) -> Unit,
) {
    val firstItemFocusRequester = remember { FocusRequester() }
    var initialFocusRequested by remember { mutableStateOf(false) }

    // One-shot: focus the grid only the first time items appear. Without the
    // guard, a filter reload (items cleared then repopulated) re-runs this and
    // yanks focus back to card 1 mid-browse.
    LaunchedEffect(state.items.isNotEmpty()) {
        if (initialFocusRequested || state.items.isEmpty()) return@LaunchedEffect
        kotlinx.coroutines.delay(120)
        runCatching { firstItemFocusRequester.requestFocus() }
        initialFocusRequested = true
    }

    // Shared catalog grid. Filmography is fetched in one shot (no pagination),
    // so hasMore is false and onLoadMore is a no-op; isLoadingItems drives the
    // in-grid loading footer. The portrait + filmography filter row ride along
    // as the full-width header so they scroll with the posters.
    TvCatalogGrid(
        items = state.items,
        isLoading = state.isLoadingItems,
        hasMore = false,
        onItemClick = onOpenItemDetail,
        onLoadMore = {},
        modifier = Modifier.fillMaxSize(),
        fixedColumnCount = PersonGridColumns,
        contentPadding = PaddingValues(
            start = Spacing.safeArea,
            top = Spacing.xxxl,
            end = Spacing.safeArea,
            bottom = Spacing.xxxl,
        ),
        horizontalSpacing = PersonGridItemSpacing,
        verticalSpacing = Spacing.sectionSpacing,
        firstItemFocusRequester = firstItemFocusRequester,
        header = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(28.dp),
            ) {
                PersonHeader(person = person)
                FilmographyHeader(
                    selected = state.selectedFilter,
                    totalLoaded = state.items.size,
                    onSelect = onFilterSelected,
                )
            }
        },
        emptyState = {
            TvCatalogEmptyState(message = "No titles found.")
        },
    )
}

// ============================================================================
// Header (portrait + name + metadata badges + bio)
// ============================================================================

@Composable
private fun PersonHeader(person: Person) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(36.dp),
        verticalAlignment = Alignment.Top,
    ) {
        PersonPortrait(person = person)
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Text(
                text = person.name,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 56.sp,
                lineHeight = 60.sp,
                letterSpacing = (-1.4).sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            val badges = buildList {
                person.birthDate?.takeIf { it.isNotBlank() }?.let { add("Born $it") }
                person.deathDate?.takeIf { it.isNotBlank() }?.let { add("Died $it") }
                person.birthplace?.takeIf { it.isNotBlank() }?.let { add(it) }
            }
            if (badges.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    badges.forEach { badge -> MetadataBadge(text = badge) }
                }
            }
            val bio = person.bio?.trim()?.takeIf { it.isNotBlank() }
            if (bio != null) {
                Text(
                    text = bio,
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.White.copy(alpha = 0.78f),
                    lineHeight = 30.sp,
                    maxLines = 6,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun PersonPortrait(person: Person) {
    val width = 220.dp
    val height = (width.value * 1.5f).dp
    Box(
        modifier = Modifier
            .size(width = width, height = height)
            .clip(RoundedCornerShape(16.dp))
            .background(DarkSurfaceElevated),
        contentAlignment = Alignment.Center,
    ) {
        if (!person.photoUrl.isNullOrBlank()) {
            ThumbhashImage(
                url = person.photoUrl,
                thumbhash = person.photoThumbhash,
                contentDescription = person.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Icon(
                imageVector = Icons.Filled.Person,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.6f),
                modifier = Modifier.size(width * 0.4f),
            )
        }
    }
}

@Composable
private fun MetadataBadge(text: String) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(Color.White.copy(alpha = 0.08f))
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = Color.White.copy(alpha = 0.85f),
            fontWeight = FontWeight.Medium,
        )
    }
}

// ============================================================================
// Filmography header (title + count + media-type filter chips)
// ============================================================================

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FilmographyHeader(
    selected: TvPersonMediaFilter,
    totalLoaded: Int,
    onSelect: (TvPersonMediaFilter) -> Unit,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(18.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(verticalAlignment = Alignment.Bottom) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = "FILMOGRAPHY",
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White.copy(alpha = 0.55f),
                    letterSpacing = 1.4.sp,
                )
                Text(
                    text = "Appearances",
                    style = MaterialTheme.typography.displaySmall,
                    color = Color.White,
                )
            }
            if (totalLoaded > 0) {
                Text(
                    text = if (totalLoaded == 1) "1 title" else "$totalLoaded titles",
                    style = MaterialTheme.typography.titleSmall,
                    color = Color.White.copy(alpha = 0.6f),
                )
            }
        }
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            TvPersonMediaFilter.entries.forEach { filter ->
                FilterChoiceChip(
                    label = filter.title,
                    selected = filter == selected,
                    onClick = { onSelect(filter) },
                )
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun FilterChoiceChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    val container = when {
        isFocused -> Color.White.copy(alpha = 0.94f)
        selected -> Color.White.copy(alpha = 0.22f)
        else -> Color.White.copy(alpha = 0.08f)
    }
    val foreground = when {
        isFocused -> Color.Black
        selected -> Color.White
        else -> Color.White.copy(alpha = 0.85f)
    }

    Surface(
        onClick = onClick,
        interactionSource = interactionSource,
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(12.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = container,
            contentColor = foreground,
            focusedContainerColor = Color.White.copy(alpha = 0.94f),
            focusedContentColor = Color.Black,
            pressedContainerColor = Color.White.copy(alpha = 0.94f),
            pressedContentColor = Color.Black,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
        border = ClickableSurfaceDefaults.border(
            border = Border(
                border = BorderStroke(
                    width = 1.dp,
                    color = if (selected) Color.White.copy(alpha = 0.35f) else Color.White.copy(alpha = 0.06f),
                ),
                shape = RoundedCornerShape(12.dp),
            ),
            focusedBorder = Border(
                border = BorderStroke(0.dp, Color.Transparent),
                shape = RoundedCornerShape(12.dp),
            ),
        ),
        modifier = modifier,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = foreground,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
        )
    }
}

private const val PersonGridColumns = 6
private val PersonGridItemSpacing = 40.dp
