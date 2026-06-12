package com.continuum.app.viewmodel

import com.continuum.app.model.admin.AdminAuditPage
import com.continuum.app.model.admin.AdminLogPage
import com.continuum.app.model.admin.AdminSession
import com.continuum.app.model.admin.AdminStats
import com.continuum.app.model.admin.AdminUser
import com.continuum.app.model.admin.CreateUserRequest
import com.continuum.app.model.admin.ScanCancelRequest
import com.continuum.app.model.admin.ScanCancelResponse
import com.continuum.app.model.admin.ScanRequest
import com.continuum.app.model.admin.ScanResponse
import com.continuum.app.model.admin.SessionControlAction
import com.continuum.app.model.admin.SessionControlRequest
import com.continuum.app.model.admin.SessionControlResponse
import com.continuum.app.model.admin.UpdateUserRequest
import com.continuum.app.network.ApiResult
import com.continuum.app.network.api.AdminApi
import com.continuum.app.repository.AdminRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class AdminUsersViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()
    @BeforeTest fun setUp() { Dispatchers.setMain(dispatcher) }
    @AfterTest fun tearDown() { Dispatchers.resetMain() }

    private fun u(id: Int, name: String, enabled: Boolean = true) = AdminUser(
        id = id, username = name, email = "$name@x.io", role = "user",
        permissions = emptyList(), enabled = enabled,
    )

    @Test fun `loads users on init`() = runTest(dispatcher) {
        val api = FakeAdminUsersApi(users = mutableListOf(u(1, "a"), u(2, "b")))
        val vm = AdminUsersViewModel(AdminRepository(api))
        assertEquals(2, vm.uiState.value.users.size)
    }

    @Test fun `delete removes user and surfaces message`() = runTest(dispatcher) {
        val api = FakeAdminUsersApi(users = mutableListOf(u(1, "a"), u(2, "b")))
        val vm = AdminUsersViewModel(AdminRepository(api))
        vm.deleteUser(1)
        assertTrue(vm.uiState.value.users.none { it.id == 1 })
        assertEquals("User deleted", vm.uiState.value.message)
    }

    @Test fun `delete failure keeps list and surfaces error message`() = runTest(dispatcher) {
        val api = FakeAdminUsersApi(
            users = mutableListOf(u(1, "a")),
            deleteError = ApiResult.Error(code = 403, error = "forbidden", message = "Cannot delete"),
        )
        val vm = AdminUsersViewModel(AdminRepository(api))
        vm.deleteUser(1)
        assertTrue(vm.uiState.value.users.any { it.id == 1 })
        assertEquals("Cannot delete", vm.uiState.value.message)
    }

    @Test fun `error surfaces server message`() = runTest(dispatcher) {
        val api = FakeAdminUsersApi(listError = ApiResult.Error(code = 500, error = "x", message = ""))
        assertEquals("Failed to load users", AdminUsersViewModel(AdminRepository(api)).uiState.value.error)
    }
}

private class FakeAdminUsersApi(
    private val users: MutableList<AdminUser> = mutableListOf(),
    private val listError: ApiResult<List<AdminUser>>? = null,
    private val deleteError: ApiResult<Unit>? = null,
) : AdminApi {
    override suspend fun getUsers(): ApiResult<List<AdminUser>> = listError ?: ApiResult.Success(users.toList())
    override suspend fun getUser(id: Int): ApiResult<AdminUser> {
        val match = users.firstOrNull { it.id == id }
        return if (match != null) ApiResult.Success(match) else ApiResult.Error(404, "nf", "")
    }
    override suspend fun createUser(request: CreateUserRequest): ApiResult<AdminUser> {
        val created = AdminUser(
            id = (users.maxOfOrNull { it.id } ?: 0) + 1,
            username = request.username, email = request.email,
            role = request.role, permissions = emptyList(), enabled = true,
        )
        users += created
        return ApiResult.Success(created)
    }
    override suspend fun updateUser(id: Int, request: UpdateUserRequest): ApiResult<AdminUser> {
        val idx = users.indexOfFirst { it.id == id }
        return if (idx >= 0) ApiResult.Success(users[idx]) else ApiResult.Error(404, "nf", "")
    }
    override suspend fun deleteUser(id: Int): ApiResult<Unit> {
        deleteError?.let { return it }
        users.removeAll { it.id == id }
        return ApiResult.Success(Unit)
    }

    override suspend fun getStats(refresh: Boolean): ApiResult<AdminStats> = error("unused")
    override suspend fun getSessions(): ApiResult<List<AdminSession>> = error("unused")
    override suspend fun sessionControl(
        sessionId: String,
        action: SessionControlAction,
        request: SessionControlRequest,
    ): ApiResult<SessionControlResponse> = error("unused")
    override suspend fun getAppLogs(
        level: String?, component: String?, nodeId: String?, requestId: String?,
        sessionId: String?, playbackSessionId: String?, userId: Int?,
        from: String?, to: String?, query: String?, cursor: String?, limit: Int,
    ): ApiResult<AdminLogPage> = error("unused")
    override suspend fun getAuditLogs(
        method: String?, pathPrefix: String?, statusCode: Int?, clientIp: String?,
        requestId: String?, sessionId: String?, playbackSessionId: String?, userId: Int?,
        from: String?, to: String?, cursor: String?, limit: Int,
    ): ApiResult<AdminAuditPage> = error("unused")
    override suspend fun triggerScan(request: ScanRequest): ApiResult<ScanResponse> = error("unused")
    override suspend fun cancelScan(request: ScanCancelRequest): ApiResult<ScanCancelResponse> = error("unused")
}
