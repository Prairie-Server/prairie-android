package org.siloserver.silo.repository

import org.siloserver.silo.model.calendar.CalendarFilter
import org.siloserver.silo.model.calendar.CalendarResponse
import org.siloserver.silo.network.ApiResult
import org.siloserver.silo.network.api.CalendarApi

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
