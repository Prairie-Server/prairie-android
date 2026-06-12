package com.continuum.app.network.api

import com.continuum.app.model.watchtogether.AddSuggestionRequest
import com.continuum.app.model.watchtogether.CreateRoomRequest
import com.continuum.app.model.watchtogether.JoinRoomRequest
import com.continuum.app.model.watchtogether.PromoteSuggestionRequest
import com.continuum.app.model.watchtogether.RoomResponse
import com.continuum.app.model.watchtogether.SetSelectionRequest
import com.continuum.app.model.watchtogether.SuggestionsResponse
import com.continuum.app.model.watchtogether.UpdatePolicyRequest
import com.continuum.app.network.ApiResult
import io.ktor.client.HttpClient
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType

/**
 * Watch Together REST surface (`/api/v1/watch-together`). Create/join return a
 * second token (the **room JWT**) distinct from the auth JWT; every
 * room-scoped call passes it as the `room_token` query param. Behind an
 * interface so the repository's tests fake the transport (matching
 * NotificationsApi/AdminApi).
 *
 * The room WS is a separate transport (see WatchTogetherRealtimeClient); this
 * is REST only. 204 (close) maps to Unit; 409 (vote dup / not-voted) and 410
 * (gone) surface as [ApiResult.Error].
 */
interface WatchTogetherApi {

    /** POST /rooms — caller becomes host; 201 with room + room_access_token. */
    suspend fun createRoom(request: CreateRoomRequest): ApiResult<RoomResponse>

    /** POST /join — resolves a code or join token; 200 with room + room_access_token. */
    suspend fun joinRoom(request: JoinRoomRequest): ApiResult<RoomResponse>

    /** GET /rooms/{id}?room_token= — current room snapshot. */
    suspend fun getRoom(roomId: String, roomToken: String): ApiResult<RoomResponse>

    /** PUT /rooms/{id}/selection?room_token= (host-only). */
    suspend fun setSelection(
        roomId: String,
        roomToken: String,
        request: SetSelectionRequest,
    ): ApiResult<RoomResponse>

    /** PATCH /rooms/{id}/policy?room_token= (host-only). */
    suspend fun updatePolicy(
        roomId: String,
        roomToken: String,
        request: UpdatePolicyRequest,
    ): ApiResult<RoomResponse>

    /** DELETE /rooms/{id}?room_token= (host-only) — 204 → Unit. */
    suspend fun closeRoom(roomId: String, roomToken: String): ApiResult<Unit>

    /** GET /rooms/{id}/suggestions?room_token=. */
    suspend fun listSuggestions(roomId: String, roomToken: String): ApiResult<SuggestionsResponse>

    /** POST /rooms/{id}/suggestions?room_token=. */
    suspend fun addSuggestion(
        roomId: String,
        roomToken: String,
        request: AddSuggestionRequest,
    ): ApiResult<SuggestionsResponse>

    /** DELETE /rooms/{id}/suggestions/{sid}?room_token= (host or suggester). */
    suspend fun deleteSuggestion(
        roomId: String,
        roomToken: String,
        suggestionId: String,
    ): ApiResult<SuggestionsResponse>

    /** POST /rooms/{id}/suggestions/{sid}/vote?room_token= — 409 on dup. */
    suspend fun vote(
        roomId: String,
        roomToken: String,
        suggestionId: String,
    ): ApiResult<SuggestionsResponse>

    /** DELETE /rooms/{id}/suggestions/{sid}/vote?room_token= — 409 if not voted. */
    suspend fun unvote(
        roomId: String,
        roomToken: String,
        suggestionId: String,
    ): ApiResult<SuggestionsResponse>

    /** POST /rooms/{id}/suggestions/promote?room_token= (host-only) → room. */
    suspend fun promoteSuggestion(
        roomId: String,
        roomToken: String,
        request: PromoteSuggestionRequest,
    ): ApiResult<RoomResponse>
}

class DefaultWatchTogetherApi(private val client: HttpClient) : WatchTogetherApi {

    override suspend fun createRoom(request: CreateRoomRequest): ApiResult<RoomResponse> =
        safeApiCall {
            client.post("$BASE/rooms") {
                contentType(ContentType.Application.Json)
                setBody(request)
            }
        }

    override suspend fun joinRoom(request: JoinRoomRequest): ApiResult<RoomResponse> =
        safeApiCall {
            client.post("$BASE/join") {
                contentType(ContentType.Application.Json)
                setBody(request)
            }
        }

    override suspend fun getRoom(roomId: String, roomToken: String): ApiResult<RoomResponse> =
        safeApiCall {
            client.get("$BASE/rooms/$roomId") { parameter("room_token", roomToken) }
        }

    override suspend fun setSelection(
        roomId: String,
        roomToken: String,
        request: SetSelectionRequest,
    ): ApiResult<RoomResponse> = safeApiCall {
        client.put("$BASE/rooms/$roomId/selection") {
            parameter("room_token", roomToken)
            contentType(ContentType.Application.Json)
            setBody(request)
        }
    }

    override suspend fun updatePolicy(
        roomId: String,
        roomToken: String,
        request: UpdatePolicyRequest,
    ): ApiResult<RoomResponse> = safeApiCall {
        client.patch("$BASE/rooms/$roomId/policy") {
            parameter("room_token", roomToken)
            contentType(ContentType.Application.Json)
            setBody(request)
        }
    }

    override suspend fun closeRoom(roomId: String, roomToken: String): ApiResult<Unit> =
        safeApiCall {
            client.delete("$BASE/rooms/$roomId") { parameter("room_token", roomToken) }
        }

    override suspend fun listSuggestions(
        roomId: String,
        roomToken: String,
    ): ApiResult<SuggestionsResponse> = safeApiCall {
        client.get("$BASE/rooms/$roomId/suggestions") { parameter("room_token", roomToken) }
    }

    override suspend fun addSuggestion(
        roomId: String,
        roomToken: String,
        request: AddSuggestionRequest,
    ): ApiResult<SuggestionsResponse> = safeApiCall {
        client.post("$BASE/rooms/$roomId/suggestions") {
            parameter("room_token", roomToken)
            contentType(ContentType.Application.Json)
            setBody(request)
        }
    }

    override suspend fun deleteSuggestion(
        roomId: String,
        roomToken: String,
        suggestionId: String,
    ): ApiResult<SuggestionsResponse> = safeApiCall {
        client.delete("$BASE/rooms/$roomId/suggestions/$suggestionId") {
            parameter("room_token", roomToken)
        }
    }

    override suspend fun vote(
        roomId: String,
        roomToken: String,
        suggestionId: String,
    ): ApiResult<SuggestionsResponse> = safeApiCall {
        client.post("$BASE/rooms/$roomId/suggestions/$suggestionId/vote") {
            parameter("room_token", roomToken)
        }
    }

    override suspend fun unvote(
        roomId: String,
        roomToken: String,
        suggestionId: String,
    ): ApiResult<SuggestionsResponse> = safeApiCall {
        client.delete("$BASE/rooms/$roomId/suggestions/$suggestionId/vote") {
            parameter("room_token", roomToken)
        }
    }

    override suspend fun promoteSuggestion(
        roomId: String,
        roomToken: String,
        request: PromoteSuggestionRequest,
    ): ApiResult<RoomResponse> = safeApiCall {
        client.post("$BASE/rooms/$roomId/suggestions/promote") {
            parameter("room_token", roomToken)
            contentType(ContentType.Application.Json)
            setBody(request)
        }
    }

    private companion object {
        const val BASE = "/api/v1/watch-together"
    }
}
