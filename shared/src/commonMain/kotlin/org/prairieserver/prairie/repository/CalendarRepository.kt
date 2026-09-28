package org.prairieserver.prairie.repository

import org.prairieserver.prairie.model.calendar.CalendarFilter
import org.prairieserver.prairie.model.calendar.CalendarResponse
import org.prairieserver.prairie.network.ApiResult
import org.prairieserver.prairie.network.api.CalendarApi

/** Thin pass-through over [CalendarApi]; the calendar holds no client-side cache state. */
class CalendarRepository(private val api: CalendarApi) {
    suspend fun capture() = api.capture()
    suspend fun current(owner: org.prairieserver.prairie.network.AuthScopeSnapshot) = api.current(owner)


    suspend fun getCalendar(
        start: String,
        end: String,
        filter: String = CalendarFilter.All,
        libraryId: Int? = null,
        timezone: String? = null,
        owner: org.prairieserver.prairie.network.AuthScopeSnapshot,
    ): ApiResult<CalendarResponse> = api.getCalendar(start, end, filter, libraryId, timezone, owner)
}
