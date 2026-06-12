package com.continuum.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.continuum.app.model.calendar.CalendarDay
import com.continuum.app.model.calendar.CalendarFilter
import com.continuum.app.model.calendar.CalendarItem
import com.continuum.app.network.ApiResult
import com.continuum.app.network.errorMessage
import com.continuum.app.repository.CalendarRepository
import com.continuum.app.util.IsoDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CalendarUiState(
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    /** ISO "YYYY-MM-DD" for the platform's current date. */
    val today: String = "",
    /** Monday anchoring the visible week, ISO "YYYY-MM-DD". */
    val weekStart: String = "",
    val filter: String = CalendarFilter.Following,
    val libraryId: Int? = null,
    /** Server-grouped day buckets for the visible week. */
    val days: List<CalendarDay> = emptyList(),
    val error: String? = null,
) {
    /** The 7 ISO dates of the visible week, Monday first. */
    val weekDates: List<String>
        get() = if (weekStart.isBlank()) emptyList() else (0L..6L).map { IsoDate.plusDays(weekStart, it) }

    val weekEnd: String
        get() = if (weekStart.isBlank()) "" else IsoDate.plusDays(weekStart, 6)

    val isCurrentWeek: Boolean
        get() = today.isNotBlank() && weekStart == IsoDate.weekStart(today)

    val hasAnyItems: Boolean
        get() = days.any { it.items.isNotEmpty() }

    fun itemsFor(date: String): List<CalendarItem> =
        days.firstOrNull { it.date == date }?.items.orEmpty()
}

/**
 * Shared calendar/upcoming ViewModel (pattern: RequestsViewModels). The
 * platform supplies "today" and the IANA timezone so week math stays
 * deterministic in commonTest — no Clock.System defaults baked in.
 */
class CalendarViewModel(
    private val repository: CalendarRepository,
    private val timezoneId: String,
    private val todayProvider: () -> String,
) : ViewModel() {

    /**
     * Monotonically increasing counter incremented on every fetch start.
     * Each in-flight coroutine captures the value at launch time and skips
     * state writes when a newer fetch has already started — preventing a
     * slow/stale response from overwriting a more-recent result.
     */
    private var loadGeneration = 0

    private val _uiState: MutableStateFlow<CalendarUiState>
    val uiState: StateFlow<CalendarUiState>

    init {
        val today = todayProvider()
        _uiState = MutableStateFlow(
            CalendarUiState(today = today, weekStart = IsoDate.weekStart(today)),
        )
        uiState = _uiState.asStateFlow()
        load()
    }

    fun load() {
        val generation = ++loadGeneration
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            fetch(generation)
        }
    }

    fun refresh() {
        val generation = ++loadGeneration
        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshing = true, error = null) }
            fetch(generation)
            if (generation == loadGeneration) {
                _uiState.update { it.copy(isRefreshing = false) }
            }
        }
    }

    fun nextWeek() = moveWeek(7)

    fun prevWeek() = moveWeek(-7)

    fun goToToday() {
        val today = todayProvider()
        val weekStart = IsoDate.weekStart(today)
        if (weekStart == _uiState.value.weekStart) return
        _uiState.update { it.copy(today = today, weekStart = weekStart) }
        load()
    }

    fun setFilter(filter: String) {
        if (filter == _uiState.value.filter) return
        _uiState.update { it.copy(filter = filter) }
        load()
    }

    fun setLibrary(libraryId: Int?) {
        if (libraryId == _uiState.value.libraryId) return
        _uiState.update { it.copy(libraryId = libraryId) }
        load()
    }

    private fun moveWeek(days: Long) {
        _uiState.update { it.copy(weekStart = IsoDate.plusDays(it.weekStart, days)) }
        load()
    }

    private suspend fun fetch(generation: Int) {
        val state = _uiState.value
        val result = repository.getCalendar(
            start = state.weekStart,
            end = state.weekEnd,
            filter = state.filter,
            libraryId = state.libraryId,
            timezone = timezoneId,
        )
        // Discard the result if a newer fetch has already started.
        if (generation != loadGeneration) return
        when (result) {
            is ApiResult.Success -> _uiState.update {
                it.copy(isLoading = false, days = result.data.events, error = null)
            }
            is ApiResult.Error, is ApiResult.NetworkError -> _uiState.update {
                it.copy(isLoading = false, error = result.errorMessage("Failed to load calendar"))
            }
        }
    }
}
