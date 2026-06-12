package com.continuum.app.android.ui.screens.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.continuum.app.android.ui.components.ContinuumTopBar
import com.continuum.app.android.ui.components.EmptyStateView
import com.continuum.app.android.ui.components.ErrorView
import com.continuum.app.android.ui.components.LoadingIndicator
import com.continuum.app.common.ui.components.ThumbhashImage
import com.continuum.app.model.calendar.CalendarBadge
import com.continuum.app.model.calendar.CalendarFilter
import com.continuum.app.model.calendar.CalendarItem
import com.continuum.app.model.personal.UserLibrary
import com.continuum.app.network.ApiResult
import com.continuum.app.repository.PersonalDataRepository
import com.continuum.app.viewmodel.CalendarViewModel
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Calendar / upcoming screen: week strip (7 day chips + prev/next + Today),
 * filter preset row (Following / Trending / All), library dropdown when the
 * user has more than one library, and a day-grouped list of event cards.
 * Taps route to the existing item-detail screen (series for episodes).
 */
@Composable
fun CalendarScreen(
    onBackClick: () -> Unit,
    onItemClick: (String) -> Unit,
    viewModel: CalendarViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsState()

    // Library list for the dropdown — same source MainScreen uses for
    // media-mode capabilities (PersonalDataRepository.listUserLibraries).
    val personalDataRepository: PersonalDataRepository = koinInject()
    val libraries by produceState(initialValue = emptyList<UserLibrary>()) {
        value = when (val result = personalDataRepository.listUserLibraries()) {
            is ApiResult.Success -> result.data
            else -> emptyList()
        }
    }

    Scaffold(
        topBar = {
            ContinuumTopBar(
                title = "Calendar",
                onBackClick = onBackClick,
                actions = {
                    if (libraries.size > 1) {
                        LibraryDropdown(
                            libraries = libraries,
                            selectedLibraryId = state.libraryId,
                            onSelect = viewModel::setLibrary,
                        )
                    }
                },
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            FilterPresetRow(
                selected = state.filter,
                onSelect = viewModel::setFilter,
            )
            WeekStrip(
                weekDates = state.weekDates,
                today = state.today,
                isCurrentWeek = state.isCurrentWeek,
                onPrevWeek = viewModel::prevWeek,
                onNextWeek = viewModel::nextWeek,
                onToday = viewModel::goToToday,
            )
            when {
                state.isLoading -> LoadingIndicator()
                state.error != null -> ErrorView(
                    message = state.error ?: "Something went wrong",
                    onRetry = viewModel::load,
                )
                !state.hasAnyItems -> EmptyStateView(
                    title = "Nothing scheduled",
                    subtitle = emptyCopy(state.filter),
                )
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    state.weekDates.forEach { date ->
                        val dayItems = state.itemsFor(date)
                        if (dayItems.isEmpty()) return@forEach
                        item(key = "header-$date") {
                            DayHeader(date = date, isToday = date == state.today)
                        }
                        items(dayItems, key = { "$date-${it.contentId}" }) { item ->
                            CalendarEventCard(
                                item = item,
                                onClick = { onItemClick(item.detailContentId) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FilterPresetRow(
    selected: String,
    onSelect: (String) -> Unit,
) {
    val presets = listOf(
        CalendarFilter.Following to "Following",
        CalendarFilter.Trending to "Trending",
        CalendarFilter.All to "All",
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        presets.forEach { (value, label) ->
            FilterChip(
                selected = selected == value,
                onClick = { onSelect(value) },
                label = { Text(label) },
            )
        }
    }
}

@Composable
private fun WeekStrip(
    weekDates: List<String>,
    today: String,
    isCurrentWeek: Boolean,
    onPrevWeek: () -> Unit,
    onNextWeek: () -> Unit,
    onToday: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onPrevWeek) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                    contentDescription = "Previous week",
                )
            }
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                weekDates.forEach { date ->
                    DayChip(date = date, isToday = date == today)
                }
            }
            IconButton(onClick = onNextWeek) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = "Next week",
                )
            }
        }
        if (!isCurrentWeek) {
            TextButton(
                onClick = onToday,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            ) {
                Text("Today")
            }
        }
    }
}

@Composable
private fun DayChip(date: String, isToday: Boolean) {
    val localDate = remember(date) { LocalDate.parse(date) }
    val background =
        if (isToday) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
    val contentColor =
        if (isToday) MaterialTheme.colorScheme.onPrimaryContainer
        else MaterialTheme.colorScheme.onSurfaceVariant
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(background)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = localDate.format(DateTimeFormatter.ofPattern("EEE", Locale.getDefault())),
            style = MaterialTheme.typography.labelSmall,
            color = contentColor,
        )
        Text(
            text = localDate.dayOfMonth.toString(),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = contentColor,
        )
    }
}

@Composable
private fun DayHeader(date: String, isToday: Boolean) {
    val localDate = remember(date) { LocalDate.parse(date) }
    Text(
        text = if (isToday) {
            "Today"
        } else {
            localDate.format(DateTimeFormatter.ofPattern("EEEE, MMM d", Locale.getDefault()))
        },
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = 8.dp),
    )
}

@Composable
private fun CalendarEventCard(
    item: CalendarItem,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (item.watched) 0.55f else 1f),
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ThumbhashImage(
                url = item.posterUrl,
                thumbhash = item.posterThumbhash,
                contentDescription = item.title,
                modifier = Modifier
                    .width(52.dp)
                    .height(78.dp)
                    .clip(RoundedCornerShape(8.dp)),
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (item.isEpisode) {
                    val marker = listOfNotNull(
                        item.seasonNumber?.let { s ->
                            item.episodeNumber?.let { e -> "S${s}E$e" }
                        },
                        item.episodeTitle,
                    ).joinToString(" • ")
                    if (marker.isNotBlank()) {
                        Text(
                            text = marker,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                item.airTime?.takeIf { it.isNotBlank() }?.let { airTime ->
                    Text(
                        text = airTime,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (item.badges.isNotEmpty()) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        item.badges.mapNotNull(::badgeLabel).forEach { label ->
                            BadgeChip(label)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BadgeChip(label: String) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}

@Composable
private fun LibraryDropdown(
    libraries: List<UserLibrary>,
    selectedLibraryId: Int?,
    onSelect: (Int?) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        TextButton(onClick = { expanded = true }) {
            Text(libraries.firstOrNull { it.id == selectedLibraryId }?.name ?: "All libraries")
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            DropdownMenuItem(
                text = { Text("All libraries") },
                onClick = {
                    expanded = false
                    onSelect(null)
                },
            )
            libraries.forEach { library ->
                DropdownMenuItem(
                    text = { Text(library.name) },
                    onClick = {
                        expanded = false
                        onSelect(library.id)
                    },
                )
            }
        }
    }
}

private fun badgeLabel(badge: String): String? = when (badge) {
    CalendarBadge.SeriesPremiere -> "Series Premiere"
    CalendarBadge.SeasonPremiere -> "Season Premiere"
    CalendarBadge.Finale -> "Finale"
    else -> null
}

private fun emptyCopy(filter: String): String = when (filter) {
    CalendarFilter.Following -> "Nothing airing this week from shows you follow. Try Trending or All."
    CalendarFilter.Trending -> "No trending releases this week."
    else -> "Nothing scheduled this week."
}
