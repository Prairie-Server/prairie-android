// shared/src/commonTest/kotlin/org/prairieserver/prairie/repository/AdminRepositoryTest.kt
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
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class AdminRepositoryTest {

    /** Records calls and returns canned successes. */
    private class FakeAdminApi : AdminApi {
        val calls = mutableListOf<String>()

        var statsRefresh: Boolean? = null
        var lastSessionControl: Triple<String, SessionControlAction, SessionControlRequest>? = null
        var lastAppLogLimit: Int? = null

        override suspend fun getStats(refresh: Boolean): ApiResult<AdminStats> {
            calls += "getStats"
            statsRefresh = refresh
            return ApiResult.Success(AdminStats(totalUsers = 9))
        }

        override suspend fun getUsers(): ApiResult<List<AdminUser>> {
            calls += "getUsers"
            return ApiResult.Success(emptyList())
        }

        override suspend fun getUser(id: Int): ApiResult<AdminUser> {
            calls += "getUser:$id"
            return ApiResult.Success(
                AdminUser(id = id, username = "u", email = "u@x.io", role = "user"),
            )
        }

        override suspend fun createUser(request: CreateUserRequest): ApiResult<AdminUser> {
            calls += "createUser:${request.username}"
            return ApiResult.Success(
                AdminUser(id = 1, username = request.username, email = request.email, role = request.role),
            )
        }

        override suspend fun updateUser(id: Int, request: UpdateUserRequest): ApiResult<AdminUser> {
            calls += "updateUser:$id"
            return ApiResult.Success(
                AdminUser(id = id, username = "u", email = "u@x.io", role = "user"),
            )
        }

        override suspend fun deleteUser(id: Int): ApiResult<Unit> {
            calls += "deleteUser:$id"
            return ApiResult.Success(Unit)
        }

        override suspend fun getSessions(): ApiResult<List<AdminSession>> {
            calls += "getSessions"
            return ApiResult.Success(emptyList())
        }

        override suspend fun sessionControl(
            sessionId: String,
            action: SessionControlAction,
            request: SessionControlRequest,
        ): ApiResult<SessionControlResponse> {
            calls += "sessionControl:$sessionId:${action.wire}"
            lastSessionControl = Triple(sessionId, action, request)
            return ApiResult.Success(SessionControlResponse(commandId = "c", status = "dispatched"))
        }

        override suspend fun getAppLogs(
            level: String?, component: String?, nodeId: String?, requestId: String?,
            sessionId: String?, playbackSessionId: String?, userId: Int?,
            from: String?, to: String?, query: String?, cursor: String?, limit: Int,
        ): ApiResult<AdminLogPage> {
            calls += "getAppLogs"
            lastAppLogLimit = limit
            return ApiResult.Success(AdminLogPage())
        }

        override suspend fun getAuditLogs(
            method: String?, pathPrefix: String?, statusCode: Int?, clientIp: String?,
            requestId: String?, sessionId: String?, playbackSessionId: String?, userId: Int?,
            from: String?, to: String?, cursor: String?, limit: Int,
        ): ApiResult<AdminAuditPage> {
            calls += "getAuditLogs"
            return ApiResult.Success(AdminAuditPage())
        }

        override suspend fun triggerScan(request: ScanRequest): ApiResult<ScanResponse> {
            calls += "triggerScan:${request.libraryId}"
            return ApiResult.Success(
                ScanResponse(status = "scanning", mode = "incremental", libraryId = request.libraryId ?: -1),
            )
        }

        override suspend fun cancelScan(request: ScanCancelRequest): ApiResult<ScanCancelResponse> {
            calls += "cancelScan:${request.libraryId}"
            return ApiResult.Success(ScanCancelResponse(cancelled = 1, libraryId = request.libraryId))
        }
    }

    @Test
    fun `getStats passes refresh through and returns api result`() = runTest {
        val api = FakeAdminApi()
        val repo = AdminRepository(api)

        val result = repo.getStats(refresh = true)

        assertEquals(listOf("getStats"), api.calls)
        assertEquals(true, api.statsRefresh)
        assertIs<ApiResult.Success<*>>(result)
        assertEquals(9, (result as ApiResult.Success).data.totalUsers)
    }

    @Test
    fun `user CRUD pass-throughs delegate to api`() = runTest {
        val api = FakeAdminApi()
        val repo = AdminRepository(api)

        repo.getUsers()
        repo.getUser(7)
        repo.createUser(CreateUserRequest("bob", "b@x.io", "pw", "user"))
        repo.updateUser(7, UpdateUserRequest(enabled = false))
        repo.deleteUser(7)

        assertEquals(
            listOf("getUsers", "getUser:7", "createUser:bob", "updateUser:7", "deleteUser:7"),
            api.calls,
        )
    }

    @Test
    fun `sessions and control pass-throughs delegate to api`() = runTest {
        val api = FakeAdminApi()
        val repo = AdminRepository(api)

        repo.getSessions()
        val result = repo.sessionControl(
            "sess-1", SessionControlAction.Stop, SessionControlRequest(reason = "policy"),
        )

        assertEquals(listOf("getSessions", "sessionControl:sess-1:stop"), api.calls)
        assertEquals("sess-1", api.lastSessionControl?.first)
        assertEquals(SessionControlAction.Stop, api.lastSessionControl?.second)
        assertIs<ApiResult.Success<*>>(result)
    }

    @Test
    fun `log pass-throughs forward limit`() = runTest {
        val api = FakeAdminApi()
        val repo = AdminRepository(api)

        repo.getAppLogs(level = "error", limit = 25)
        repo.getAuditLogs(method = "POST")

        assertEquals(listOf("getAppLogs", "getAuditLogs"), api.calls)
        assertEquals(25, api.lastAppLogLimit)
    }

    @Test
    fun `scan pass-throughs delegate to api`() = runTest {
        val api = FakeAdminApi()
        val repo = AdminRepository(api)

        val scan = repo.triggerScan(ScanRequest(libraryId = 4))
        val cancel = repo.cancelScan(ScanCancelRequest(libraryId = 4))

        assertEquals(listOf("triggerScan:4", "cancelScan:4"), api.calls)
        assertIs<ApiResult.Success<*>>(scan)
        assertEquals(4, (scan as ApiResult.Success).data.libraryId)
        assertIs<ApiResult.Success<*>>(cancel)
        assertEquals(1, (cancel as ApiResult.Success).data.cancelled)
    }
}
