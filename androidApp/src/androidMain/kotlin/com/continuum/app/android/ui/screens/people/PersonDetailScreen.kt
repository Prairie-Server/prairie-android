package com.continuum.app.android.ui.screens.people

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.border
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.continuum.app.android.ui.components.EmptyStateView
import com.continuum.app.android.ui.components.ErrorView
import com.continuum.app.android.ui.components.LoadingIndicator
import com.continuum.app.android.ui.components.MediaCard
import com.continuum.app.android.ui.components.rememberBrowseItemCardActions
import com.continuum.app.android.ui.theme.ContinuumOnSurface
import com.continuum.app.android.ui.theme.ContinuumSecondaryText
import com.continuum.app.android.ui.theme.ContinuumSurfaceElevated
import com.continuum.app.android.ui.theme.ContinuumSurfaceVariant
import com.continuum.app.android.ui.theme.PillShape
import com.continuum.app.common.ui.components.ThumbhashImage
import com.continuum.app.model.catalog.BrowseItem
import com.continuum.app.model.catalog.Person

private val SafePadding = 16.dp
private val LargePadding = 24.dp

/**
 * Person detail screen — actor / creator profile.
 *
 * Mirrors the iOS `PersonDetailView` phone layout:
 *   - top header: 132dp portrait + name + metadata badges + bio
 *   - filmography: filter row (All / Movies / Series) → poster grid
 */
@Composable
fun PersonDetailScreen(
    onBackClick: () -> Unit,
    onItemClick: (String) -> Unit,
    viewModel: PersonDetailViewModel,
) {
    val state by viewModel.uiState.collectAsState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        when {
            state.isLoading && state.person == null -> {
                LoadingIndicator()
            }
            state.error != null && state.person == null -> {
                ErrorView(
                    message = state.error ?: "Something went wrong",
                    onRetry = { viewModel.reload() },
                )
            }
            state.person != null -> {
                PersonDetailContent(
                    person = state.person!!,
                    items = state.items,
                    isLoadingItems = state.isLoadingItems,
                    selectedFilter = state.selectedFilter,
                    onFilterSelected = { viewModel.applyFilter(it) },
                    onItemClick = onItemClick,
                )
            }
            else -> Unit
        }

        IconButton(
            onClick = onBackClick,
            modifier = Modifier
                .align(Alignment.TopStart)
                .statusBarsPadding()
                .padding(8.dp)
                .size(40.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.45f)),
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                tint = Color.White,
            )
        }
    }
}

@Composable
private fun PersonDetailContent(
    person: Person,
    items: List<BrowseItem>,
    isLoadingItems: Boolean,
    selectedFilter: PersonMediaFilter,
    onFilterSelected: (PersonMediaFilter) -> Unit,
    onItemClick: (String) -> Unit,
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 110.dp),
        contentPadding = PaddingValues(
            start = SafePadding,
            end = SafePadding,
            top = 0.dp,
            bottom = LargePadding,
        ),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(top = 56.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                PersonHeader(person = person)
                FilmographyHeader(
                    selected = selectedFilter,
                    totalLoaded = items.size,
                    onSelect = onFilterSelected,
                )
            }
        }

        if (items.isEmpty() && isLoadingItems) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(
                        color = ContinuumOnSurface,
                    )
                }
            }
        } else if (items.isEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(260.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    EmptyStateView(
                        title = "No titles found",
                        subtitle = "There are no movies or series linked to this person yet.",
                        icon = Icons.Outlined.Movie,
                    )
                }
            }
        } else {
            items(items, key = { it.contentId }) { item ->
                val (actions, userState) = rememberBrowseItemCardActions(item)
                MediaCard(
                    title = item.title,
                    posterUrl = item.posterUrl,
                    posterThumbhash = item.posterThumbhash,
                    year = item.year.takeIf { it > 0 },
                    type = item.type,
                    userState = userState,
                    onClick = { onItemClick(item.contentId) },
                    modifier = Modifier.fillMaxWidth(),
                    overlay = com.continuum.app.overlays.OverlayDataExtractor.fromBrowseItem(item),
                    actions = actions,
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PersonHeader(person: Person) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(18.dp),
        verticalAlignment = Alignment.Top,
    ) {
        PersonPortrait(person = person)
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = person.name,
                fontSize = 30.sp,
                fontWeight = FontWeight.Bold,
                color = ContinuumOnSurface,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            val badges = buildList {
                person.birthDate?.takeIf { it.isNotBlank() }?.let { add("Born $it") }
                person.deathDate?.takeIf { it.isNotBlank() }?.let { add("Died $it") }
                person.birthplace?.takeIf { it.isNotBlank() }?.let { add(it) }
            }
            if (badges.isNotEmpty()) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    badges.forEach { badge ->
                        Surface(
                            shape = PillShape,
                            color = ContinuumSurfaceVariant,
                        ) {
                            Text(
                                text = badge,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Normal,
                                color = ContinuumOnSurface,
                                modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                            )
                        }
                    }
                }
            }
            val bio = person.bio?.trim()?.takeIf { it.isNotBlank() }
            if (bio != null) {
                Text(
                    text = bio,
                    fontSize = 14.sp,
                    color = ContinuumSecondaryText,
                    maxLines = 8,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun PersonPortrait(person: Person) {
    val width = 132.dp
    val height = (width.value * 1.5f).dp
    val shape = RoundedCornerShape(8.dp)
    Box(
        modifier = Modifier
            .size(width = width, height = height)
            .clip(shape)
            .background(ContinuumSurfaceElevated)
            .border(1.dp, Color.White.copy(alpha = 0.10f), shape),
        contentAlignment = Alignment.Center,
    ) {
        if (!person.photoUrl.isNullOrBlank()) {
            ThumbhashImage(
                url = person.photoUrl,
                thumbhash = person.photoThumbhash,
                contentDescription = person.name,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Text(
                text = personInitials(person.name),
                fontSize = (width.value * 0.28f).sp,
                fontWeight = FontWeight.SemiBold,
                color = ContinuumSecondaryText,
            )
        }
    }
}

@Composable
private fun FilmographyHeader(
    selected: PersonMediaFilter,
    totalLoaded: Int,
    onSelect: (PersonMediaFilter) -> Unit,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = "Filmography",
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = ContinuumOnSurface,
                modifier = Modifier.weight(1f),
            )
            if (totalLoaded > 0) {
                Text(
                    text = if (totalLoaded == 1) "1 title" else "$totalLoaded titles",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Normal,
                    color = ContinuumSecondaryText,
                )
            }
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.horizontalScroll(rememberScrollState()),
        ) {
            PersonMediaFilter.values().forEach { filter ->
                FilterChip(
                    title = filter.title,
                    isSelected = filter == selected,
                    onClick = { onSelect(filter) },
                )
            }
        }
    }
}

@Composable
private fun FilterChip(
    title: String,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        shape = PillShape,
        color = if (isSelected) ContinuumSurfaceVariant else ContinuumSurfaceElevated.copy(alpha = 0.55f),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            Color.White.copy(alpha = if (isSelected) 0.16f else 0.08f),
        ),
        modifier = Modifier.clickable(onClick = onClick),
    ) {
        Text(
            text = title,
            fontSize = 12.sp,
            fontWeight = FontWeight.Normal,
            color = if (isSelected) ContinuumOnSurface else ContinuumSecondaryText,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
        )
    }
}

private fun personInitials(name: String): String {
    val parts = name.split(' ').filter { it.isNotBlank() }
    val initials = parts.take(2).mapNotNull { it.firstOrNull()?.uppercaseChar() }
    return if (initials.isEmpty()) "?" else initials.joinToString("")
}
