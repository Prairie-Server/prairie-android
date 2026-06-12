package com.continuum.app.repository

import com.continuum.app.model.calendar.CalendarFilter
import com.continuum.app.model.calendar.CalendarResponse
import com.continuum.app.network.ApiResult
import com.continuum.app.network.api.CalendarApi

/** Thin pass-through over [CalendarApi]; the calendar holds no client-side cache state. */
class CalendarRepository(private val api: CalendarApi) {

    suspend fun getCalendar(
        start: String,
        end: String,
        filter: String = CalendarFilter.All,
        libraryId: Int? = null,
        timezone: String? = null,
    ): ApiResult<CalendarResponse> = api.getCalendar(start, end, filter, libraryId, timezone)
}
