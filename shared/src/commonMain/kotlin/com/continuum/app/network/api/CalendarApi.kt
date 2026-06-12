package com.continuum.app.network.api

import com.continuum.app.model.calendar.CalendarFilter
import com.continuum.app.model.calendar.CalendarResponse
import com.continuum.app.network.ApiResult
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.parameter

/**
 * Calendar / upcoming endpoint. Kept behind an interface so repository and
 * ViewModel tests can fake the transport, matching the RequestsApi shape.
 */
interface CalendarApi {

    /**
     * GET /api/v1/calendar — max 31-day span. Dates are ISO "YYYY-MM-DD";
     * [timezone] is an IANA id used by the server to compute local air dates.
     */
    suspend fun getCalendar(
        start: String,
        end: String,
        filter: String = CalendarFilter.All,
        libraryId: Int? = null,
        timezone: String? = null,
    ): ApiResult<CalendarResponse>
}

class DefaultCalendarApi(private val client: HttpClient) : CalendarApi {

    override suspend fun getCalendar(
        start: String,
        end: String,
        filter: String,
        libraryId: Int?,
        timezone: String?,
    ): ApiResult<CalendarResponse> = safeApiCall {
        client.get("/api/v1/calendar") {
            parameter("start", start)
            parameter("end", end)
            parameter("filter", filter)
            libraryId?.let { parameter("library_id", it) }
            timezone?.let { parameter("timezone", it) }
        }
    }
}
