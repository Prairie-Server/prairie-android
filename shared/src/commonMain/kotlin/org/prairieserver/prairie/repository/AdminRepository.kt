// shared/src/commonMain/kotlin/org/prairieserver/prairie/repository/AdminRepository.kt
package org.prairieserver.prairie.repository

import org.prairieserver.prairie.model.admin.AdminAuditPage
import org.prairieserver.prairie.model.admin.AdminLogPage
import org.prairieserver.prairie.model.admin.AdminSession
import org.prairieserver.prairie.model.admin.AdminStats
import org.prairieserver.prairie.model.admin.AdminUser
import org.prairieserver.prairie.model.admin.CreateUserRequest
import org.prairieserver.prairie.model.admin.ScanCancelRequest
import org.prairieserver.prairie.model.admin.ScanCancelResponse
import org.prairieserver.prairie.model.admin.ScanRequest
import org.prairieserver.prairie.model.admin.ScanResponse
import org.prairieserver.prairie.model.admin.SessionControlAction
import org.prairieserver.prairie.model.admin.SessionControlRequest
import org.prairieserver.prairie.model.admin.SessionControlResponse
import org.prairieserver.prairie.model.admin.UpdateUserRequest
import org.prairieserver.prairie.network.ApiResult
import org.prairieserver.prairie.network.api.AdminApi

/**
 * Thin pass-through over [AdminApi] for the mobile admin sub-screens and the
 * TV stats dashboard. Stateless (no cached flows): each admin screen owns its
 * ViewModel state and refreshes via pull-to-refresh, so the repository just
 * surfaces the typed [ApiResult] from the transport.
 *
 * Every admin route is gated server-side on acting-admin; the UI gates entry
 * with [org.prairieserver.prairie.model.auth.isActingAdmin].
 *
 * NOTE: [triggerScan]/[cancelScan] hit `/api/v1/libraries/scan[/cancel]`, NOT
 * `/admin/` routes (the scan endpoints live on the libraries handler server-side).
 * They are exposed here so the admin "Scans" sub-screen has a single
 * repository dependency.
 */
class AdminRepository(private val api: AdminApi) {

    suspend fun getStats(refresh: Boolean = false): ApiResult<AdminStats> =
        api.getStats(refresh)

    suspend fun getUsers(): ApiResult<List<AdminUser>> = api.getUsers()

    suspend fun getUser(id: Int): ApiResult<AdminUser> = api.getUser(id)

    suspend fun createUser(request: CreateUserRequest): ApiResult<AdminUser> =
        api.createUser(request)

    suspend fun updateUser(id: Int, request: UpdateUserRequest): ApiResult<AdminUser> =
        api.updateUser(id, request)

    suspend fun deleteUser(id: Int): ApiResult<Unit> = api.deleteUser(id)

    suspend fun getSessions(): ApiResult<List<AdminSession>> = api.getSessions()

    suspend fun sessionControl(
        sessionId: String,
        action: SessionControlAction,
        request: SessionControlRequest = SessionControlRequest(),
    ): ApiResult<SessionControlResponse> = api.sessionControl(sessionId, action, request)

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
    ): ApiResult<AdminLogPage> = api.getAppLogs(
        level, component, nodeId, requestId, sessionId, playbackSessionId,
        userId, from, to, query, cursor, limit,
    )

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
    ): ApiResult<AdminAuditPage> = api.getAuditLogs(
        method, pathPrefix, statusCode, clientIp, requestId, sessionId,
        playbackSessionId, userId, from, to, cursor, limit,
    )

    /** POST /api/v1/libraries/scan (not /admin) — see class KDoc. */
    suspend fun triggerScan(request: ScanRequest): ApiResult<ScanResponse> =
        api.triggerScan(request)

    /** POST /api/v1/libraries/scan/cancel (not /admin) — see class KDoc. */
    suspend fun cancelScan(request: ScanCancelRequest): ApiResult<ScanCancelResponse> =
        api.cancelScan(request)
}
