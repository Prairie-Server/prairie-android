package org.siloserver.silo.network.api

import org.siloserver.silo.model.admin.CreateUserRequest
import org.siloserver.silo.model.admin.ScanCancelRequest
import org.siloserver.silo.model.admin.ScanRequest
import org.siloserver.silo.model.admin.SessionControlAction
import org.siloserver.silo.model.admin.SessionControlRequest
import org.siloserver.silo.model.admin.UpdateUserRequest
import org.siloserver.silo.network.ApiResult
import org.siloserver.silo.network.SiloJson
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

class AdminApiTest {

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
    ): Pair<AdminApi, Captured> {
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
            install(ContentNegotiation) { json(SiloJson) }
        }
        return DefaultAdminApi(client) to captured
    }

    private val statsBody = """
        {"total_items":1,"total_files":1,"total_users":1,"total_movies":1,
         "total_movie_files":1,"total_shows":0,"total_show_files":0,
         "active_streams":0,"total_storage_bytes":10,
         "watch_provider_activity":{"trakt_connected_profiles":1,"scrobbles_24h":2}}
    """.trimIndent()

    @Test
    fun `getStats omits refresh when false`() = runTest {
        val (api, captured) = api(responseBody = statsBody)
        val result = api.getStats(refresh = false)
        assertEquals(HttpMethod.Get, captured.method)
        assertEquals("/api/v1/admin/stats", captured.path)
        assertFalse("refresh" in captured.query.keys)
        assertIs<ApiResult.Success<*>>(result)
        assertEquals(2L, (result as ApiResult.Success).data.watchProviderActivity.scrobbles24h)
    }

    @Test
    fun `getStats passes refresh=true`() = runTest {
        val (api, captured) = api(responseBody = statsBody)
        api.getStats(refresh = true)
        assertEquals("true", captured.query["refresh"])
    }

    @Test
    fun `getUsers hits users path`() = runTest {
        val (api, captured) = api(responseBody = "[]")
        val result = api.getUsers()
        assertEquals(HttpMethod.Get, captured.method)
        assertEquals("/api/v1/admin/users", captured.path)
        assertIs<ApiResult.Success<*>>(result)
    }

    @Test
    fun `getUser hits id path`() = runTest {
        val (api, captured) = api(
            responseBody = """{"id":7,"username":"a","email":"a@x.io","role":"user",
                "permissions":[],"enabled":true,"library_ids":[],"max_playback_quality":"",
                "max_streams":0,"max_transcodes":0,"max_profiles":0,
                "download_allowed":false,"download_transcode_allowed":false,
                "created_at":"t","updated_at":"t"}""",
        )
        val result = api.getUser(7)
        assertEquals("/api/v1/admin/users/7", captured.path)
        assertIs<ApiResult.Success<*>>(result)
        assertEquals(7, (result as ApiResult.Success).data.id)
    }

    @Test
    fun `createUser posts body to users path`() = runTest {
        val (api, captured) = api(
            responseBody = """{"id":9,"username":"bob","email":"b@x.io","role":"user",
                "permissions":[],"enabled":true,"library_ids":[],"max_playback_quality":"",
                "max_streams":0,"max_transcodes":0,"max_profiles":0,
                "download_allowed":false,"download_transcode_allowed":false,
                "created_at":"t","updated_at":"t"}""",
        )
        val result = api.createUser(
            CreateUserRequest(
                username = "bob", email = "b@x.io", password = "pw", role = "user",
                createDefaultProfile = true,
            ),
        )
        assertEquals(HttpMethod.Post, captured.method)
        assertEquals("/api/v1/admin/users", captured.path)
        val sent = SiloJson.parseToJsonElement(captured.body).jsonObject
        assertEquals("bob", sent["username"]?.toString()?.trim('"'))
        assertTrue("password" in sent.keys)
        assertTrue("max_streams" !in sent.keys) // null omitted
        assertIs<ApiResult.Success<*>>(result)
    }

    @Test
    fun `updateUser puts partial body to id path`() = runTest {
        val (api, captured) = api(
            responseBody = """{"id":7,"username":"a","email":"a@x.io","role":"user",
                "permissions":[],"enabled":false,"library_ids":[],"max_playback_quality":"",
                "max_streams":4,"max_transcodes":0,"max_profiles":0,
                "download_allowed":false,"download_transcode_allowed":false,
                "created_at":"t","updated_at":"t"}""",
        )
        val result = api.updateUser(7, UpdateUserRequest(enabled = false, maxStreams = 4))
        assertEquals(HttpMethod.Put, captured.method)
        assertEquals("/api/v1/admin/users/7", captured.path)
        val sent = SiloJson.parseToJsonElement(captured.body).jsonObject
        assertEquals(setOf("enabled", "max_streams"), sent.keys) // only set fields
        assertIs<ApiResult.Success<*>>(result)
    }

    @Test
    fun `deleteUser deletes id path and maps 204 to Unit`() = runTest {
        val (api, captured) = api(status = HttpStatusCode.NoContent, responseBody = "")
        val result = api.deleteUser(7)
        assertEquals(HttpMethod.Delete, captured.method)
        assertEquals("/api/v1/admin/users/7", captured.path)
        assertEquals(ApiResult.Success(Unit), result)
    }

    @Test
    fun `getSessions hits sessions path`() = runTest {
        val (api, captured) = api(responseBody = "[]")
        val result = api.getSessions()
        assertEquals(HttpMethod.Get, captured.method)
        assertEquals("/api/v1/admin/sessions", captured.path)
        assertIs<ApiResult.Success<*>>(result)
    }

    @Test
    fun `sessionControl posts action path with body and decodes response`() = runTest {
        val (api, captured) = api(
            status = HttpStatusCode.Accepted,
            responseBody = """{"command_id":"cmd-1","status":"dispatched"}""",
        )
        val result = api.sessionControl(
            "sess-9",
            SessionControlAction.Message,
            SessionControlRequest(title = "Heads up", message = "Stopping soon"),
        )
        assertEquals(HttpMethod.Post, captured.method)
        assertEquals("/api/v1/admin/sessions/sess-9/message", captured.path)
        val sent = SiloJson.parseToJsonElement(captured.body).jsonObject
        assertEquals("Stopping soon", sent["message"]?.toString()?.trim('"'))
        assertTrue("reason" !in sent.keys) // null omitted
        assertIs<ApiResult.Success<*>>(result)
        assertEquals("cmd-1", (result as ApiResult.Success).data.commandId)
    }

    @Test
    fun `sessionControl pause uses pause segment`() = runTest {
        val (api, captured) = api(
            status = HttpStatusCode.Accepted,
            responseBody = """{"command_id":"c","status":"dispatched"}""",
        )
        api.sessionControl("s1", SessionControlAction.Pause, SessionControlRequest(deadlineMs = 5000))
        assertEquals("/api/v1/admin/sessions/s1/pause", captured.path)
        val sent = SiloJson.parseToJsonElement(captured.body).jsonObject
        assertEquals("5000", sent["deadline_ms"]?.toString())
    }

    @Test
    fun `getAppLogs passes filters and cursor and limit, omits nulls`() = runTest {
        val (api, captured) = api(responseBody = """{"entries":[]}""")
        val result = api.getAppLogs(
            level = "error",
            component = "scanner",
            nodeId = null,
            requestId = null,
            sessionId = null,
            playbackSessionId = null,
            userId = 3,
            from = "2026-06-12T00:00:00Z",
            to = null,
            query = "fail",
            cursor = "cur-1",
            limit = 50,
        )
        assertEquals("/api/v1/admin/logs/app", captured.path)
        assertEquals("error", captured.query["level"])
        assertEquals("scanner", captured.query["component"])
        assertEquals("3", captured.query["user_id"])
        assertEquals("2026-06-12T00:00:00Z", captured.query["from"])
        assertEquals("fail", captured.query["q"])
        assertEquals("cur-1", captured.query["cursor"])
        assertEquals("50", captured.query["limit"])
        assertFalse("node_id" in captured.query.keys)
        assertFalse("to" in captured.query.keys)
        assertIs<ApiResult.Success<*>>(result)
    }

    @Test
    fun `getAuditLogs passes audit filters and omits nulls`() = runTest {
        val (api, captured) = api(responseBody = """{"entries":[]}""")
        api.getAuditLogs(
            method = "POST",
            pathPrefix = "/api/v1/admin",
            statusCode = 201,
            clientIp = null,
            requestId = null,
            sessionId = null,
            playbackSessionId = null,
            userId = null,
            from = null,
            to = null,
            cursor = null,
            limit = 100,
        )
        assertEquals("/api/v1/admin/logs/audit", captured.path)
        assertEquals("POST", captured.query["method"])
        assertEquals("/api/v1/admin", captured.query["path_prefix"])
        assertEquals("201", captured.query["status_code"])
        assertEquals("100", captured.query["limit"])
        assertFalse("client_ip" in captured.query.keys)
        assertFalse("cursor" in captured.query.keys)
    }

    @Test
    fun `triggerScan posts to libraries scan with body`() = runTest {
        val (api, captured) = api(
            responseBody = """{"status":"scanning","mode":"incremental","library_id":4}""",
        )
        val result = api.triggerScan(ScanRequest(libraryId = 4))
        assertEquals(HttpMethod.Post, captured.method)
        assertEquals("/api/v1/libraries/scan", captured.path)
        val sent = SiloJson.parseToJsonElement(captured.body).jsonObject
        assertEquals("4", sent["library_id"]?.toString())
        assertTrue("path" !in sent.keys)
        assertIs<ApiResult.Success<*>>(result)
        assertEquals(4, (result as ApiResult.Success).data.libraryId)
    }

    @Test
    fun `cancelScan posts to libraries scan cancel with body`() = runTest {
        val (api, captured) = api(responseBody = """{"cancelled":1,"library_id":4}""")
        val result = api.cancelScan(ScanCancelRequest(libraryId = 4))
        assertEquals(HttpMethod.Post, captured.method)
        assertEquals("/api/v1/libraries/scan/cancel", captured.path)
        val sent = SiloJson.parseToJsonElement(captured.body).jsonObject
        assertEquals("4", sent["library_id"]?.toString())
        assertIs<ApiResult.Success<*>>(result)
        assertEquals(1, (result as ApiResult.Success).data.cancelled)
    }

    @Test
    fun `server error surfaces as ApiResult Error with message`() = runTest {
        val (api, _) = api(
            status = HttpStatusCode.Forbidden,
            responseBody = """{"error":"forbidden","message":"Admin access required"}""",
        )
        val result = api.getStats(refresh = false)
        assertIs<ApiResult.Error>(result)
        assertEquals(403, result.code)
        assertEquals("Admin access required", result.message)
    }
}
