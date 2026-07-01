// shared/src/commonMain/kotlin/org/siloserver/silo/network/api/AdminApi.kt
package org.siloserver.silo.network.api

import org.siloserver.silo.model.admin.AdminAuditPage
import org.siloserver.silo.model.admin.AdminLogPage
import org.siloserver.silo.model.admin.AdminSession
import org.siloserver.silo.model.admin.AdminStats
import org.siloserver.silo.model.admin.AdminUser
import org.siloserver.silo.model.admin.CreateUserRequest
import org.siloserver.silo.model.admin.ScanCancelRequest
import org.siloserver.silo.model.admin.ScanCancelResponse
import org.siloserver.silo.model.admin.ScanRequest
import org.siloserver.silo.model.admin.ScanResponse
import org.siloserver.silo.model.admin.SessionControlAction
import org.siloserver.silo.model.admin.SessionControlRequest
import org.siloserver.silo.model.admin.SessionControlResponse
import org.siloserver.silo.model.admin.UpdateUserRequest
import org.siloserver.silo.network.ApiResult
import io.ktor.client.HttpClient
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType

/**
 * Core-admin surface (stats, users, sessions + controls, logs, scans). Every
 * route is gated server-side on acting-admin; the UI mirrors that with
 * [org.siloserver.silo.model.auth.isActingAdmin]. Behind an interface so the
 * repository and its tests can fake the transport (matching
 * NotificationsApi/SubtitlesApi).
 *
 * NOTE: the scan endpoints ([triggerScan]/[cancelScan]) live under
 * `/api/v1/libraries`, NOT `/admin` — they are kept on this interface for
 * cohesion with the admin "Scans" sub-screen, which is the only admin caller.
 */
interface AdminApi {

    /** GET /api/v1/admin/stats[?refresh=true]. */
    suspend fun getStats(refresh: Boolean = false): ApiResult<AdminStats>

    /** GET /api/v1/admin/users — bare array. */
    suspend fun getUsers(): ApiResult<List<AdminUser>>

    /** GET /api/v1/admin/users/{id}. */
    suspend fun getUser(id: Int): ApiResult<AdminUser>

    /** POST /api/v1/admin/users. */
    suspend fun createUser(request: CreateUserRequest): ApiResult<AdminUser>

    /** PUT /api/v1/admin/users/{id} — partial; null fields omitted. */
    suspend fun updateUser(id: Int, request: UpdateUserRequest): ApiResult<AdminUser>

    /** DELETE /api/v1/admin/users/{id} — 204. */
    suspend fun deleteUser(id: Int): ApiResult<Unit>

    /** GET /api/v1/admin/sessions — bare array of active sessions. */
    suspend fun getSessions(): ApiResult<List<AdminSession>>

    /** POST /api/v1/admin/sessions/{id}/{action} — body optional per action. */
    suspend fun sessionControl(
        sessionId: String,
        action: SessionControlAction,
        request: SessionControlRequest = SessionControlRequest(),
    ): ApiResult<SessionControlResponse>

    /** GET /api/v1/admin/logs/app — cursor-paginated; null filters omitted. */
    suspend fun getAppLogs(
        level: String? = null,
        component: String? = null,
        nodeId: String? = null,
        requestId: String? = null,
        sessionId: String? = null,
        playbackSessionId: String? = null,
        userId: Int? = null,
        from: String? = null,
        to: String? = null,
        query: String? = null,
        cursor: String? = null,
        limit: Int = 100,
    ): ApiResult<AdminLogPage>

    /** GET /api/v1/admin/logs/audit — cursor-paginated; null filters omitted. */
    suspend fun getAuditLogs(
        method: String? = null,
        pathPrefix: String? = null,
        statusCode: Int? = null,
        clientIp: String? = null,
        requestId: String? = null,
        sessionId: String? = null,
        playbackSessionId: String? = null,
        userId: Int? = null,
        from: String? = null,
        to: String? = null,
        cursor: String? = null,
        limit: Int = 100,
    ): ApiResult<AdminAuditPage>

    /** POST /api/v1/libraries/scan (NOT /admin). */
    suspend fun triggerScan(request: ScanRequest): ApiResult<ScanResponse>

    /** POST /api/v1/libraries/scan/cancel (NOT /admin). */
    suspend fun cancelScan(request: ScanCancelRequest): ApiResult<ScanCancelResponse>
}

class DefaultAdminApi(private val client: HttpClient) : AdminApi {

    override suspend fun getStats(refresh: Boolean): ApiResult<AdminStats> = safeApiCall {
        client.get("/api/v1/admin/stats") {
            if (refresh) parameter("refresh", "true")
        }
    }

    override suspend fun getUsers(): ApiResult<List<AdminUser>> = safeApiCall {
        client.get("/api/v1/admin/users")
    }

    override suspend fun getUser(id: Int): ApiResult<AdminUser> = safeApiCall {
        client.get("/api/v1/admin/users/$id")
    }

    override suspend fun createUser(request: CreateUserRequest): ApiResult<AdminUser> = safeApiCall {
        client.post("/api/v1/admin/users") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }
    }

    override suspend fun updateUser(id: Int, request: UpdateUserRequest): ApiResult<AdminUser> = safeApiCall {
        client.put("/api/v1/admin/users/$id") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }
    }

    override suspend fun deleteUser(id: Int): ApiResult<Unit> = safeApiCall {
        client.delete("/api/v1/admin/users/$id")
    }

    override suspend fun getSessions(): ApiResult<List<AdminSession>> = safeApiCall {
        client.get("/api/v1/admin/sessions")
    }

    override suspend fun sessionControl(
        sessionId: String,
        action: SessionControlAction,
        request: SessionControlRequest,
    ): ApiResult<SessionControlResponse> = safeApiCall {
        client.post("/api/v1/admin/sessions/$sessionId/${action.wire}") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }
    }

    override suspend fun getAppLogs(
        level: String?,
        component: String?,
        nodeId: String?,
        requestId: String?,
        sessionId: String?,
        playbackSessionId: String?,
        userId: Int?,
        from: String?,
        to: String?,
        query: String?,
        cursor: String?,
        limit: Int,
    ): ApiResult<AdminLogPage> = safeApiCall {
        client.get("/api/v1/admin/logs/app") {
            level?.let { parameter("level", it) }
            component?.let { parameter("component", it) }
            nodeId?.let { parameter("node_id", it) }
            requestId?.let { parameter("request_id", it) }
            sessionId?.let { parameter("session_id", it) }
            playbackSessionId?.let { parameter("playback_session_id", it) }
            userId?.let { parameter("user_id", it) }
            from?.let { parameter("from", it) }
            to?.let { parameter("to", it) }
            query?.let { parameter("q", it) }
            cursor?.let { parameter("cursor", it) }
            parameter("limit", limit)
        }
    }

    override suspend fun getAuditLogs(
        method: String?,
        pathPrefix: String?,
        statusCode: Int?,
        clientIp: String?,
        requestId: String?,
        sessionId: String?,
        playbackSessionId: String?,
        userId: Int?,
        from: String?,
        to: String?,
        cursor: String?,
        limit: Int,
    ): ApiResult<AdminAuditPage> = safeApiCall {
        client.get("/api/v1/admin/logs/audit") {
            method?.let { parameter("method", it) }
            pathPrefix?.let { parameter("path_prefix", it) }
            statusCode?.let { parameter("status_code", it) }
            clientIp?.let { parameter("client_ip", it) }
            requestId?.let { parameter("request_id", it) }
            sessionId?.let { parameter("session_id", it) }
            playbackSessionId?.let { parameter("playback_session_id", it) }
            userId?.let { parameter("user_id", it) }
            from?.let { parameter("from", it) }
            to?.let { parameter("to", it) }
            cursor?.let { parameter("cursor", it) }
            parameter("limit", limit)
        }
    }

    override suspend fun triggerScan(request: ScanRequest): ApiResult<ScanResponse> = safeApiCall {
        client.post("/api/v1/libraries/scan") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }
    }

    override suspend fun cancelScan(request: ScanCancelRequest): ApiResult<ScanCancelResponse> = safeApiCall {
        client.post("/api/v1/libraries/scan/cancel") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }
    }
}
