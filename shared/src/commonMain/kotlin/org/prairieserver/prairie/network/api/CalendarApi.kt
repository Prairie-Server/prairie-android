package org.prairieserver.prairie.network.api

import org.prairieserver.prairie.model.calendar.CalendarFilter
import org.prairieserver.prairie.model.calendar.CalendarResponse
import org.prairieserver.prairie.network.ApiResult
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.http.*
import kotlinx.serialization.json.*
import org.prairieserver.prairie.network.*
import org.prairieserver.prairie.network.apiv2.*

/**
 * Calendar / upcoming endpoint. Kept behind an interface so repository and
 * ViewModel tests can fake the transport, matching the RequestsApi shape.
 */
interface CalendarApi {
    suspend fun capture(): AuthScopeSnapshot?
    suspend fun current(owner: AuthScopeSnapshot): Boolean


    /**
     * GET /api/v2/calendar — max 31-day span. Dates are ISO "YYYY-MM-DD";
     * [timezone] is an IANA id used by the server to compute local air dates.
     */
    suspend fun getCalendar(
        start: String,
        end: String,
        filter: String = CalendarFilter.All,
        libraryId: Int? = null,
        timezone: String? = null,
        owner: AuthScopeSnapshot,
    ): ApiResult<CalendarResponse>
}

class DefaultCalendarApi(private val client: HttpClient, private val tokens: TokenManager, private val gate: ApiV2Gate) : CalendarApi {
    override suspend fun capture(): AuthScopeSnapshot? = tokens.captureProfileScope()
    override suspend fun current(owner: AuthScopeSnapshot): Boolean = owner.stillOwns(tokens, OwnerPolicy.FULL)

    override suspend fun getCalendar(
        start: String,
        end: String,
        filter: String,
        libraryId: Int?,
        timezone: String?,
        owner: AuthScopeSnapshot,
    ): ApiResult<CalendarResponse> =
        ownedV2Call<JsonObject, CalendarResponse>(gate, tokens, owner, OwnerPolicy.FULL, HttpStatusCode.OK, { scope ->
            client.get("/api/v2/calendar") {
                authScope(scope!!); requirePrairieAuth()
                parameter("start", start)
                parameter("end", end)
                parameter("filter", filter)
                libraryId?.let { parameter("library_id", it) }
                timezone?.let { parameter("timezone", it) }
            }
        }) { body ->
            val days = body["events"] as? JsonArray ?: throw IllegalArgumentException("Missing events.")
            require(days.all { it.jsonObject["items"] is JsonArray }) { "Missing day items." }
            val calendar = PrairieJson.decodeFromJsonElement<CalendarResponse>(body)
            require(calendar.events.all { day -> day.items.all { it.contentId.isNotBlank() } }) { "Blank event identity." }
            calendar
        }
}
