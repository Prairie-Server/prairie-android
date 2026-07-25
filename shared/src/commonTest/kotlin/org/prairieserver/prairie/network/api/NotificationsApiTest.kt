package org.prairieserver.prairie.network.api

import org.prairieserver.prairie.model.notifications.NotificationPreferencesUpdate
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
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class NotificationsApiTest {

    private class Captured {
        var method: HttpMethod? = null
        var path: String = ""
        var query: Map<String, String?> = emptyMap()
        var body: String = ""
    }

    private fun api(
        status: HttpStatusCode = HttpStatusCode.OK,
        responseBody: String = "{}",
        captured: Captured = Captured(),
    ): Pair<NotificationsApi, Captured> {
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
        return DefaultNotificationsApi(client) to captured
    }

    @Test
    fun `list passes status and limit, omits null before, decodes rows + cursor`() = runTest {
        val (api, captured) = api(
            responseBody = """
                {"notifications":[{"id":"a","type":"episode.available","profile_id":"p",
                  "reason_flags":{},"created_at":"2026-06-12T09:00:00Z","read_at":null}],
                 "next_cursor":"Y3Vy"}
            """.trimIndent(),
        )

        val result = api.list(limit = 25, unreadOnly = true, before = null)

        assertEquals(HttpMethod.Get, captured.method)
        assertEquals("/api/v1/notifications", captured.path)
        assertEquals("unread", captured.query["status"])
        assertEquals("25", captured.query["limit"])
        assertFalse("before" in captured.query.keys) // null omitted
        assertIs<ApiResult.Success<*>>(result)
        assertEquals("Y3Vy", (result as ApiResult.Success).data.nextCursor)
    }

    @Test
    fun `list without unread filter omits status and passes before cursor`() = runTest {
        val (api, captured) = api(responseBody = """{"notifications":[]}""")
        api.list(limit = 50, unreadOnly = false, before = "cur-1")
        assertFalse("status" in captured.query.keys)
        assertEquals("cur-1", captured.query["before"])
    }

    @Test
    fun `sync passes since and decodes unread_count`() = runTest {
        val (api, captured) = api(
            responseBody = """{"notifications":[],"next_cursor":"z","unread_count":3}""",
        )
        val result = api.sync(since = "s-1", limit = 50)
        assertEquals("/api/v1/notifications/sync", captured.path)
        assertEquals("s-1", captured.query["since"])
        assertIs<ApiResult.Success<*>>(result)
        assertEquals(3, (result as ApiResult.Success).data.unreadCount)
    }

    @Test
    fun `get hits id path`() = runTest {
        val (api, captured) = api(
            responseBody = """{"id":"dlv-9","type":"episode.available","profile_id":"p",
                "reason_flags":{},"created_at":"2026-06-12T09:00:00Z","read_at":null}""",
        )
        val result = api.get("dlv-9")
        assertEquals("/api/v1/notifications/dlv-9", captured.path)
        assertIs<ApiResult.Success<*>>(result)
        assertEquals("dlv-9", (result as ApiResult.Success).data.id)
    }

    @Test
    fun `unreadCount hits path and decodes count`() = runTest {
        val (api, captured) = api(responseBody = """{"count":11}""")
        val result = api.unreadCount()
        assertEquals("/api/v1/notifications/unread-count", captured.path)
        assertIs<ApiResult.Success<*>>(result)
        assertEquals(11, (result as ApiResult.Success).data.count)
    }

    @Test
    fun `markRead posts to read path and maps 204 to Unit`() = runTest {
        val (api, captured) = api(status = HttpStatusCode.NoContent, responseBody = "")
        val result = api.markRead("dlv-9")
        assertEquals(HttpMethod.Post, captured.method)
        assertEquals("/api/v1/notifications/dlv-9/read", captured.path)
        assertEquals(ApiResult.Success(Unit), result)
    }

    @Test
    fun `markAllRead posts to read-all path and maps 204 to Unit`() = runTest {
        val (api, captured) = api(status = HttpStatusCode.NoContent, responseBody = "")
        val result = api.markAllRead()
        assertEquals("/api/v1/notifications/read-all", captured.path)
        assertEquals(ApiResult.Success(Unit), result)
    }

    @Test
    fun `getPreferences decodes full prefs`() = runTest {
        val (api, captured) = api(
            responseBody = """{"profile_id":"p","enabled":true,"notify_favorites":true,
                "notify_watchlist":true,"notify_continue_watching":true,"notify_next_up":false}""",
        )
        val result = api.getPreferences()
        assertEquals("/api/v1/notifications/preferences", captured.path)
        assertIs<ApiResult.Success<*>>(result)
        assertFalse((result as ApiResult.Success).data.notifyNextUp)
    }

    @Test
    fun `updatePreferences puts partial body omitting nulls`() = runTest {
        val (api, captured) = api(
            responseBody = """{"profile_id":"p","enabled":true,"notify_favorites":true,
                "notify_watchlist":false,"notify_continue_watching":true,"notify_next_up":true}""",
        )
        val result = api.updatePreferences(NotificationPreferencesUpdate(notifyWatchlist = false))
        assertEquals(HttpMethod.Put, captured.method)
        assertEquals("/api/v1/notifications/preferences", captured.path)
        val sent = PrairieJson.parseToJsonElement(captured.body).jsonObject
        assertEquals(setOf("notify_watchlist"), sent.keys) // only the set field
        assertIs<ApiResult.Success<*>>(result)
    }

    @Test
    fun `capability decodes android_push unavailable`() = runTest {
        val (api, captured) = api(
            responseBody = """
                {"in_app":{"enabled":true},
                 "apple_push":{"available":false,"provider":"off","supported_modes":["in_app_only"]},
                 "android_push":{"available":false,"provider":"off","supported_modes":["in_app_only"]},
                 "web_push":{"available":false},
                 "webhooks":{"available":false,"max_per_profile":0,"supported_types":[]},
                 "email":{"available":false,"modes":[],"digest_hour":0},
                 "discord":{"available":false,"modes":[],"digest_hour":0}}
            """.trimIndent(),
        )
        val result = api.capability()
        assertEquals("/api/v1/notifications/capability", captured.path)
        assertIs<ApiResult.Success<*>>(result)
        assertFalse((result as ApiResult.Success).data.androidPush.available)
    }

    @Test
    fun `wsTicket posts to ws-ticket and decodes ticket`() = runTest {
        val (api, captured) = api(responseBody = """{"ticket":"tkt-x","expires_in":30}""")
        val result = api.wsTicket()
        assertEquals(HttpMethod.Post, captured.method)
        assertEquals("/api/v1/events/ws-ticket", captured.path)
        assertIs<ApiResult.Success<*>>(result)
        assertEquals("tkt-x", (result as ApiResult.Success).data.ticket)
    }

    @Test
    fun `server error surfaces as ApiResult Error with message`() = runTest {
        val (api, _) = api(
            status = HttpStatusCode.NotFound,
            responseBody = """{"error":"not_found","message":"Notification not found"}""",
        )
        val result = api.get("missing")
        assertIs<ApiResult.Error>(result)
        assertEquals(404, result.code)
        assertEquals("Notification not found", result.message)
    }
}
