package org.prairieserver.prairie.tv.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import org.prairieserver.prairie.model.calendar.CalendarFilter
import org.prairieserver.prairie.model.calendar.CalendarItem
import org.prairieserver.prairie.model.calendar.CalendarItemType
import org.prairieserver.prairie.model.section.SectionItem
import org.prairieserver.prairie.network.ApiResult
import org.prairieserver.prairie.repository.CalendarRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.util.TimeZone

/**
 * Client-side "Coming this week" row for TV home. Fetches once on home load:
 * today..today+6 with filter=following, falling back to filter=all only when
 * the following set returns an EMPTY SUCCESS (nothing airing this week).
 * A fetch error (null) hides the row entirely. No week paging on TV.
 */
class TvUpcomingViewModel(
    private val repository: CalendarRepository,
) : ViewModel() {

    private val _items = MutableStateFlow<List<SectionItem>>(emptyList())
    val items: StateFlow<List<SectionItem>> = _items.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            val today = LocalDate.now()
            val start = today.toString()
            val end = today.plusDays(6).toString()
            val timezone = TimeZone.getDefault().id

            val following = fetch(start, end, CalendarFilter.Following, timezone)
            val resolved = resolveUpcoming(following) {
                fetch(start, end, CalendarFilter.All, timezone)
            }

            _items.value = resolved
                .distinctBy { it.detailContentId }
                .map { it.toSectionItem() }
        }
    }

    /**
     * Returns null on error (any non-Success ApiResult), or the flattened
     * item list on success (may be empty).
     */
    private suspend fun fetch(
        start: String,
        end: String,
        filter: String,
        timezone: String,
    ): List<CalendarItem>? = when (
        val result = repository.getCalendar(start = start, end = end, filter = filter, timezone = timezone)
    ) {
        is ApiResult.Success -> result.data.events.flatMap { it.items }
        else -> null
    }
}

/**
 * Pure resolution logic for the "Coming this week" row.
 *
 * - [following] == null  → fetch error; hide the row (return empty)
 * - [following] non-empty → use following (skip the All fetch entirely)
 * - [following] empty    → call [fetchAll]; use its result, or empty on error (null)
 */
internal suspend fun resolveUpcoming(
    following: List<CalendarItem>?,
    fetchAll: suspend () -> List<CalendarItem>?,
): List<CalendarItem> = when {
    following == null -> emptyList()
    following.isNotEmpty() -> following
    else -> fetchAll() ?: emptyList()
}

// SectionItem.type expects catalog type strings (e.g. "movie", "series").
private const val SERIES_SECTION_TYPE = "series"

/**
 * Adapter from [CalendarItem] into the [SectionItem] model consumed by [TvMediaRow].
 * Episodes collapse onto their series (detailContentId) so clicking a card opens
 * the TV series detail screen.
 */
internal fun CalendarItem.toSectionItem(): SectionItem = SectionItem(
    contentId = detailContentId,
    type = if (isEpisode) SERIES_SECTION_TYPE else CalendarItemType.Movie,
    title = title,
    seriesId = seriesId,
    seasonNumber = seasonNumber,
    episodeNumber = episodeNumber,
    posterUrl = posterUrl,
    posterThumbhash = posterThumbhash,
)
