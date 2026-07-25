package org.prairieserver.prairie.network.api

import org.prairieserver.prairie.model.watchtogether.AddSuggestionRequest
import org.prairieserver.prairie.model.watchtogether.CreateRoomRequest
import org.prairieserver.prairie.model.watchtogether.JoinRoomRequest
import org.prairieserver.prairie.model.watchtogether.PromoteSuggestionRequest
import org.prairieserver.prairie.model.watchtogether.SetSelectionRequest
import org.prairieserver.prairie.model.watchtogether.UpdatePolicyRequest
import org.prairieserver.prairie.network.ApiResult
import org.prairieserver.prairie.network.PrairieJson
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class WatchTogetherApiTest {

    private class Captured {
        var method: HttpMethod? = null
        var path: String = ""
        var query: Map<String, String?> = emptyMap()
        var body: String = ""
    }

    private val roomJson = """
        {"room_id":"room-1","phase":"lobby","playback_state":"idle","selection_mode":"host_pick",
         "selection_revision":0,"code":"ABCD1234","guest_control_policy":"host_only",
         "is_paused":false,"anchor_position_seconds":0.0,"anchor_updated_at":"2026-06-12T09:00:00Z",
         "generation":1,"member_count":1,"host_connected":true,"self_role":"host",
         "self_can_control_transport":true,"self_can_manage_room":true,"self_ignore_wait":false}
    """.trimIndent()

    private fun api(
        status: HttpStatusCode = HttpStatusCode.OK,
        responseBody: String = "{}",
        captured: Captured = Captured(),
    ): Pair<WatchTogetherApi, Captured> {
        val client = HttpClient(
            MockEngine { request ->
                captured.method = request.method
                captured.path = request.url.encodedPath
                captured.query = request.url.parameters.names()
                    .associateWith { request.url.parameters[it] }
                captured.body = request.body.toByteArray().decodeToString()
                respond(
                    content = responseBody,
                    status = status,
                    headers = headersOf(HttpHeaders.ContentType, "application/json"),
                )
            },
        ) {
            install(ContentNegotiation) { json(PrairieJson) }
        }
        return DefaultWatchTogetherApi(client) to captured
    }

    @Test
    fun `createRoom posts selection_mode and decodes room + token`() = runTest {
        val (api, captured) = api(
            status = HttpStatusCode.Created,
            responseBody = """{"room":$roomJson,"room_access_token":"jwt-1"}""",
        )
        val r = api.createRoom(CreateRoomRequest(selectionMode = "vote"))
        assertEquals(HttpMethod.Post, captured.method)
        assertEquals("/api/v1/watch-together/rooms", captured.path)
        assertEquals(setOf("selection_mode"), PrairieJson.parseToJsonElement(captured.body).jsonObject.keys)
        assertIs<ApiResult.Success<*>>(r)
        assertEquals("jwt-1", (r as ApiResult.Success).data.roomAccessToken)
    }

    @Test
    fun `joinRoom posts code and decodes room`() = runTest {
        val (api, captured) = api(responseBody = """{"room":$roomJson,"room_access_token":"jwt-2"}""")
        val r = api.joinRoom(JoinRoomRequest(code = "ABCD1234"))
        assertEquals(HttpMethod.Post, captured.method)
        assertEquals("/api/v1/watch-together/join", captured.path)
        assertIs<ApiResult.Success<*>>(r)
        assertEquals("room-1", (r as ApiResult.Success).data.room.roomId)
    }

    @Test
    fun `getRoom passes room_token query`() = runTest {
        val (api, captured) = api(responseBody = """{"room":$roomJson}""")
        api.getRoom("room-1", "jwt-room")
        assertEquals(HttpMethod.Get, captured.method)
        assertEquals("/api/v1/watch-together/rooms/room-1", captured.path)
        assertEquals("jwt-room", captured.query["room_token"])
    }

    @Test
    fun `setSelection puts content_id with room_token query`() = runTest {
        val (api, captured) = api(responseBody = """{"room":$roomJson}""")
        api.setSelection("room-1", "jwt-room", SetSelectionRequest(contentId = "tt-9", fileId = 3))
        assertEquals(HttpMethod.Put, captured.method)
        assertEquals("/api/v1/watch-together/rooms/room-1/selection", captured.path)
        assertEquals("jwt-room", captured.query["room_token"])
        val sent = PrairieJson.parseToJsonElement(captured.body).jsonObject
        assertEquals("tt-9", sent["content_id"]!!.toString().trim('"'))
    }

    @Test
    fun `updatePolicy patches policy with room_token query`() = runTest {
        val (api, captured) = api(responseBody = """{"room":$roomJson}""")
        api.updatePolicy("room-1", "jwt-room", UpdatePolicyRequest(guestControlPolicy = "guest_play_pause"))
        assertEquals(HttpMethod.Patch, captured.method)
        assertEquals("/api/v1/watch-together/rooms/room-1/policy", captured.path)
        assertEquals("jwt-room", captured.query["room_token"])
    }

    @Test
    fun `closeRoom deletes and maps 204 to Unit with room_token query`() = runTest {
        val (api, captured) = api(status = HttpStatusCode.NoContent, responseBody = "")
        val r = api.closeRoom("room-1", "jwt-room")
        assertEquals(HttpMethod.Delete, captured.method)
        assertEquals("/api/v1/watch-together/rooms/room-1", captured.path)
        assertEquals("jwt-room", captured.query["room_token"])
        assertEquals(ApiResult.Success(Unit), r)
    }

    @Test
    fun `listSuggestions gets suggestions with room_token query`() = runTest {
        val (api, captured) = api(responseBody = """{"suggestions":[]}""")
        api.listSuggestions("room-1", "jwt-room")
        assertEquals(HttpMethod.Get, captured.method)
        assertEquals("/api/v1/watch-together/rooms/room-1/suggestions", captured.path)
        assertEquals("jwt-room", captured.query["room_token"])
    }

    @Test
    fun `addSuggestion posts body with room_token query`() = runTest {
        val (api, captured) = api(
            status = HttpStatusCode.Created,
            responseBody = """{"suggestions":[]}""",
        )
        api.addSuggestion(
            "room-1", "jwt-room",
            AddSuggestionRequest(contentId = "c", contentType = "movie", title = "T"),
        )
        assertEquals(HttpMethod.Post, captured.method)
        assertEquals("/api/v1/watch-together/rooms/room-1/suggestions", captured.path)
        assertEquals("jwt-room", captured.query["room_token"])
    }

    @Test
    fun `deleteSuggestion deletes suggestion path with room_token query`() = runTest {
        val (api, captured) = api(responseBody = """{"suggestions":[]}""")
        api.deleteSuggestion("room-1", "jwt-room", "sug-5")
        assertEquals(HttpMethod.Delete, captured.method)
        assertEquals("/api/v1/watch-together/rooms/room-1/suggestions/sug-5", captured.path)
        assertEquals("jwt-room", captured.query["room_token"])
    }

    @Test
    fun `vote posts vote path with room_token query`() = runTest {
        val (api, captured) = api(responseBody = """{"suggestions":[]}""")
        api.vote("room-1", "jwt-room", "sug-5")
        assertEquals(HttpMethod.Post, captured.method)
        assertEquals("/api/v1/watch-together/rooms/room-1/suggestions/sug-5/vote", captured.path)
        assertEquals("jwt-room", captured.query["room_token"])
    }

    @Test
    fun `unvote deletes vote path with room_token query`() = runTest {
        val (api, captured) = api(responseBody = """{"suggestions":[]}""")
        api.unvote("room-1", "jwt-room", "sug-5")
        assertEquals(HttpMethod.Delete, captured.method)
        assertEquals("/api/v1/watch-together/rooms/room-1/suggestions/sug-5/vote", captured.path)
        assertEquals("jwt-room", captured.query["room_token"])
    }

    @Test
    fun `promote posts suggestion_id with room_token query and decodes room`() = runTest {
        val (api, captured) = api(responseBody = """{"room":$roomJson}""")
        api.promoteSuggestion("room-1", "jwt-room", PromoteSuggestionRequest(suggestionId = "sug-5"))
        assertEquals(HttpMethod.Post, captured.method)
        assertEquals("/api/v1/watch-together/rooms/room-1/suggestions/promote", captured.path)
        assertEquals("jwt-room", captured.query["room_token"])
        val sent = PrairieJson.parseToJsonElement(captured.body).jsonObject
        assertEquals("sug-5", sent["suggestion_id"]!!.toString().trim('"'))
    }

    @Test
    fun `vote 409 surfaces as ApiResult Error`() = runTest {
        val (api, _) = api(
            status = HttpStatusCode.Conflict,
            responseBody = """{"error":"conflict","message":"Already voted"}""",
        )
        val r = api.vote("room-1", "jwt-room", "sug-5")
        assertIs<ApiResult.Error>(r)
        assertEquals(409, r.code)
        assertEquals("Already voted", r.message)
    }

    @Test
    fun `join 410 gone surfaces as ApiResult Error`() = runTest {
        val (api, _) = api(
            status = HttpStatusCode.Gone,
            responseBody = """{"error":"gone","message":"Room is no longer active"}""",
        )
        val r = api.joinRoom(JoinRoomRequest(code = "DEAD0000"))
        assertIs<ApiResult.Error>(r)
        assertEquals(410, r.code)
        assertEquals("Room is no longer active", r.message)
    }
}
