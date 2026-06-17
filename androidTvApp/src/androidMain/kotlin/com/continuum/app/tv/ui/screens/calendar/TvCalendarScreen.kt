package com.continuum.app.tv.ui.screens.calendar

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Border
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Glow
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.continuum.app.common.ui.components.ThumbhashImage
import com.continuum.app.model.calendar.CalendarBadge
import com.continuum.app.model.calendar.CalendarFilter
import com.continuum.app.model.calendar.CalendarItem
import com.continuum.app.tv.ui.components.TvFilterChip
import com.continuum.app.tv.ui.components.TvLoadingScreen
import com.continuum.app.tv.ui.components.TvMetadataChip
import com.continuum.app.tv.ui.shell.TvTopMenuLayout
import com.continuum.app.tv.ui.theme.ContinuumBlue
import com.continuum.app.tv.ui.theme.DarkOnPrimary
import com.continuum.app.tv.ui.theme.ElevatedSurface
import com.continuum.app.tv.ui.theme.FocusedContainer
import com.continuum.app.tv.ui.theme.FocusedContent
import com.continuum.app.tv.ui.theme.Spacing
import com.continuum.app.tv.ui.theme.sectionEyebrow
import com.continuum.app.model.personal.UserLibrary
import com.continuum.app.network.ApiResult
import com.continuum.app.repository.PersonalDataRepository
import com.continuum.app.viewmodel.CalendarViewModel
import androidx.compose.runtime.produceState
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * TV calendar / upcoming screen. Reuses the shared [CalendarViewModel] that
 * drives the phone Calendar screen (week-bucketed releases from
 * `GET /api/v1/calendar`). Layout adapts the phone's week-strip + day list to a
 * 10-foot, D-pad surface:
 *
 *  - A focusable week-nav rail (◀ Prev / 7 day chips / Next ▶ / Today) — the
 *    arrow buttons and Today chip page the week; day chips highlight today.
 *  - A filter chip rail (Following / Trending / All), mirroring the phone presets.
 *  - A vertical, day-grouped list of focusable event cards. OK on a card opens
 *    item detail (series page for episodes) via [onOpenItemDetail].
 *
 * Mirrors [com.continuum.app.tv.ui.screens.recommendations.TvRecommendationsScreen]
 * for the koinViewModel + initial-focus-once pattern.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TvCalendarScreen(
    onOpenItemDetail: (contentId: String) -> Unit,
    onInitialContentFocus: () -> Unit = {},
    viewModel: CalendarViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsState()

    // Library list for the per-library filter — same source the phone Calendar
    // uses (PersonalDataRepository.listUserLibraries). Only surfaced when the
    // user has more than one library, matching the phone.
    val personalDataRepository: PersonalDataRepository = koinInject()
    val libraries by produceState(initialValue = emptyList<UserLibrary>()) {
        value = when (val result = personalDataRepository.listUserLibraries()) {
            is ApiResult.Success -> result.data
            else -> emptyList()
        }
    }

    // First focusable element of the screen is the Prev-week button so D-pad
    // lands on the nav rail; gate the jump so a silent re-emission doesn't yank
    // focus back after the user has navigated.
    val navFocusRequester = remember { FocusRequester() }
    var initialFocusRequested by remember { mutableStateOf(false) }
    LaunchedEffect(state.weekStart) {
        if (initialFocusRequested) return@LaunchedEffect
        runCatching { navFocusRequester.requestFocus() }
        onInitialContentFocus()
        initialFocusRequested = true
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        Column(
            modifier = Modifier.padding(
                start = Spacing.safeArea,
                end = Spacing.safeArea,
                top = TvTopMenuLayout.contentTopInset,
                bottom = Spacing.sm,
            ),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Text(
                text = "UPCOMING",
                style = sectionEyebrow,
                color = ContinuumBlue.copy(alpha = 0.92f),
            )
            Text(
                text = "Calendar",
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.onBackground,
            )
        }

        WeekNavRail(
            weekDates = state.weekDates,
            today = state.today,
            isCurrentWeek = state.isCurrentWeek,
            prevFocusRequester = navFocusRequester,
            onPrevWeek = viewModel::prevWeek,
            onNextWeek = viewModel::nextWeek,
            onToday = viewModel::goToToday,
        )

        FilterRail(
            selected = state.filter,
            onSelect = viewModel::setFilter,
        )

        if (libraries.size > 1) {
            LibraryRail(
                libraries = libraries,
                selectedLibraryId = state.libraryId,
                onSelect = viewModel::setLibrary,
            )
        }

        when {
            // Unconditional (phone parity): the shared VM keeps the old week's
            // days during a new week/filter load, so gating these on
            // !hasAnyItems would show stale content + suppress errors on a
            // week change.
            state.isLoading -> TvLoadingScreen()
            state.error != null -> CalendarMessage(
                title = state.error ?: "Failed to load calendar",
                subtitle = "Press the week arrows to try another week.",
            )
            !state.hasAnyItems -> CalendarMessage(
                title = "Nothing scheduled",
                subtitle = emptyCopy(state.filter),
            )
            else -> DayList(
                state = state,
                onOpenItemDetail = onOpenItemDetail,
            )
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun WeekNavRail(
    weekDates: List<String>,
    today: String,
    isCurrentWeek: Boolean,
    prevFocusRequester: FocusRequester,
    onPrevWeek: () -> Unit,
    onNextWeek: () -> Unit,
    onToday: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .focusGroup()
            .padding(horizontal = Spacing.safeArea, vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        NavArrowButton(
            arrow = NavArrow.Prev,
            focusRequester = prevFocusRequester,
            onClick = onPrevWeek,
        )
        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            weekDates.forEach { date ->
                DayChip(date = date, isToday = date == today)
            }
        }
        NavArrowButton(
            arrow = NavArrow.Next,
            onClick = onNextWeek,
        )
        if (!isCurrentWeek) {
            TvFilterChip(
                text = "Today",
                selected = false,
                onClick = onToday,
            )
        }
    }
}

private enum class NavArrow { Prev, Next }

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun NavArrowButton(
    arrow: NavArrow,
    onClick: () -> Unit,
    focusRequester: FocusRequester? = null,
) {
    val shape = RoundedCornerShape(100.dp)
    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(shape = shape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.White.copy(alpha = 0.08f),
            contentColor = Color.White.copy(alpha = 0.82f),
            focusedContainerColor = FocusedContainer,
            focusedContentColor = FocusedContent,
            pressedContainerColor = FocusedContainer,
            pressedContentColor = FocusedContent,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
        modifier = Modifier
            .size(48.dp)
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier),
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
            androidx.tv.material3.Icon(
                imageVector = when (arrow) {
                    NavArrow.Prev -> Icons.AutoMirrored.Filled.KeyboardArrowLeft
                    NavArrow.Next -> Icons.AutoMirrored.Filled.KeyboardArrowRight
                },
                contentDescription = when (arrow) {
                    NavArrow.Prev -> "Previous week"
                    NavArrow.Next -> "Next week"
                },
            )
        }
    }
}

@Composable
private fun DayChip(date: String, isToday: Boolean) {
    val localDate = remember(date) { LocalDate.parse(date) }
    val background = if (isToday) ContinuumBlue else Color.Transparent
    val contentColor = if (isToday) DarkOnPrimary else Color.White.copy(alpha = 0.72f)
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(background)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = localDate.format(DateTimeFormatter.ofPattern("EEE", Locale.getDefault())),
            style = MaterialTheme.typography.labelMedium,
            color = contentColor,
        )
        Text(
            text = localDate.dayOfMonth.toString(),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = contentColor,
        )
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun FilterRail(
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
            .focusGroup()
            .padding(horizontal = Spacing.safeArea, vertical = Spacing.sm),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        presets.forEach { (value, label) ->
            TvFilterChip(
                text = label,
                selected = selected == value,
                onClick = { onSelect(value) },
            )
        }
    }
}

/**
 * Per-library filter rail — an "All libraries" chip plus one chip per library,
 * shown only when the user has more than one library. Mirrors the phone
 * Calendar's library dropdown but as a D-pad-friendly chip rail (consistent
 * with [FilterRail]). Selecting drives the shared VM's `setLibrary`.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun LibraryRail(
    libraries: List<UserLibrary>,
    selectedLibraryId: Int?,
    onSelect: (Int?) -> Unit,
) {
    // LazyRow (not a plain Row) so a long library list scrolls horizontally and
    // D-pad focus brings off-screen chips into view instead of dead-ending.
    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .focusGroup(),
        contentPadding = PaddingValues(horizontal = Spacing.safeArea, vertical = Spacing.sm),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        item(key = "all-libraries") {
            TvFilterChip(
                text = "All libraries",
                selected = selectedLibraryId == null,
                onClick = { onSelect(null) },
            )
        }
        items(libraries, key = { it.id }) { library ->
            TvFilterChip(
                text = library.name,
                selected = selectedLibraryId == library.id,
                onClick = { onSelect(library.id) },
            )
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun DayList(
    state: com.continuum.app.viewmodel.CalendarUiState,
    onOpenItemDetail: (contentId: String) -> Unit,
) {
    val listState = rememberLazyListState()
    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .focusGroup(),
        contentPadding = PaddingValues(
            start = Spacing.safeArea,
            end = Spacing.safeArea,
            top = Spacing.sm,
            bottom = 56.dp,
        ),
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
                    onClick = { onOpenItemDetail(item.detailContentId) },
                )
            }
        }
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
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onBackground,
        modifier = Modifier.padding(top = Spacing.sm),
    )
}

private val cardShape = RoundedCornerShape(14.dp)

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun CalendarEventCard(
    item: CalendarItem,
    onClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        shape = CardDefaults.shape(shape = cardShape),
        scale = CardDefaults.scale(focusedScale = 1.02f),
        border = CardDefaults.border(
            focusedBorder = Border(BorderStroke(3.dp, Color.White), shape = cardShape),
        ),
        glow = CardDefaults.glow(
            focusedGlow = Glow(elevationColor = Color.White.copy(alpha = 0.25f), elevation = 16.dp),
        ),
        colors = CardDefaults.colors(containerColor = ElevatedSurface),
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (item.watched) 0.55f else 1f),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ThumbhashImage(
                url = item.posterUrl,
                thumbhash = item.posterThumbhash,
                contentDescription = item.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .width(64.dp)
                    .height(96.dp)
                    .clip(RoundedCornerShape(8.dp)),
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
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
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White.copy(alpha = 0.72f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                item.airTime?.takeIf { it.isNotBlank() }?.let { airTime ->
                    Text(
                        text = airTime,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.56f),
                    )
                }
                val badges = item.badges.mapNotNull(::badgeLabel)
                if (badges.isNotEmpty()) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        badges.forEach { label -> TvMetadataChip(text = label) }
                    }
                }
            }
        }
    }
}

@Composable
private fun CalendarMessage(title: String, subtitle: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = Spacing.safeArea),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            androidx.tv.material3.Icon(
                imageVector = Icons.Filled.CalendarMonth,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.62f),
                modifier = Modifier.size(56.dp),
            )
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                color = Color.White,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White.copy(alpha = 0.62f),
            )
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
