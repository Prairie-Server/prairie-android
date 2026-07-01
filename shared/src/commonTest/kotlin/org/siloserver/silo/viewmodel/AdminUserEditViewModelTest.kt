package org.siloserver.silo.viewmodel

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
import org.siloserver.silo.network.api.AdminApi
import org.siloserver.silo.repository.AdminRepository
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
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class AdminUserEditViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()
    @BeforeTest fun setUp() { Dispatchers.setMain(dispatcher) }
    @AfterTest fun tearDown() { Dispatchers.resetMain() }

    @Test fun `create validates required fields before calling api`() = runTest(dispatcher) {
        val api = FakeEditApi()
        val vm = AdminUserEditViewModel(AdminRepository(api))
        vm.load(null)
        vm.onUsernameChange("u")
        vm.onEmailChange("bad")
        vm.onPasswordChange("secret1")
        vm.submit()
        assertEquals("Enter a valid email", vm.uiState.value.error)
        assertNull(api.lastCreate)
    }

    @Test fun `create submits a CreateUserRequest and signals success`() = runTest(dispatcher) {
        val api = FakeEditApi()
        val vm = AdminUserEditViewModel(AdminRepository(api))
        vm.load(null)
        vm.onUsernameChange("alice")
        vm.onEmailChange("alice@x.io")
        vm.onPasswordChange("secret1")
        vm.onRoleChange("admin")
        vm.onLibraryIdsChange("1, 2")
        vm.onMaxStreamsChange("3")
        vm.submit()
        val req = api.lastCreate
        assertTrue(vm.uiState.value.saveSuccess)
        assertEquals("alice", req?.username)
        assertEquals("admin", req?.role)
        assertEquals(listOf(1, 2), req?.libraryIds)
        assertEquals(3, req?.maxStreams)
    }

    @Test fun `edit loads the user and omits a blank password on update`() = runTest(dispatcher) {
        val existing = AdminUser(
            id = 7, username = "bob", email = "bob@x.io", role = "user",
            enabled = true, maxStreams = 2,
        )
        val api = FakeEditApi(user = existing)
        val vm = AdminUserEditViewModel(AdminRepository(api))
        vm.load(7)
        assertEquals("bob", vm.uiState.value.username)
        assertEquals("2", vm.uiState.value.maxStreamsText)
        vm.onEnabledChange(false)
        vm.submit() // password left blank
        assertTrue(vm.uiState.value.saveSuccess)
        assertEquals(7, api.lastUpdateId)
        assertEquals(false, api.lastUpdate?.enabled)
        assertNull(api.lastUpdate?.password)
    }

    @Test fun `edit rejects a too-short password reset`() = runTest(dispatcher) {
        val api = FakeEditApi(user = AdminUser(id = 7, username = "bob", email = "b@x.io", role = "user"))
        val vm = AdminUserEditViewModel(AdminRepository(api))
        vm.load(7)
        vm.onPasswordChange("123")
        vm.submit()
        assertFalse(vm.uiState.value.saveSuccess)
        assertEquals("Password must be at least 6 characters", vm.uiState.value.error)
        assertNull(api.lastUpdate)
    }

    @Test fun `edit omits libraryIds when the library field is blank`() = runTest(dispatcher) {
        val existing = AdminUser(
            id = 5, username = "carol", email = "carol@x.io", role = "user",
            enabled = true, libraryIds = listOf(3, 4),
        )
        val api = FakeEditApi(user = existing)
        val vm = AdminUserEditViewModel(AdminRepository(api))
        vm.load(5)
        vm.onLibraryIdsChange("")
        vm.submit()
        assertTrue(vm.uiState.value.saveSuccess)
        assertNull(api.lastUpdate?.libraryIds, "libraryIds must be null (omitted) when the field is blank")
    }
}

private class FakeEditApi(
    private val user: AdminUser? = null,
) : AdminApi {
    var lastCreate: CreateUserRequest? = null
    var lastUpdate: UpdateUserRequest? = null
    var lastUpdateId: Int? = null

    override suspend fun getUser(id: Int): ApiResult<AdminUser> =
        user?.let { ApiResult.Success(it) } ?: ApiResult.Error(404, "nf", "")
    override suspend fun createUser(request: CreateUserRequest): ApiResult<AdminUser> {
        lastCreate = request
        return ApiResult.Success(AdminUser(id = 1, username = request.username, email = request.email, role = request.role))
    }
    override suspend fun updateUser(id: Int, request: UpdateUserRequest): ApiResult<AdminUser> {
        lastUpdateId = id
        lastUpdate = request
        return ApiResult.Success(user ?: AdminUser(id = id, username = "x", email = "x@x.io", role = "user"))
    }

    override suspend fun getUsers(): ApiResult<List<AdminUser>> = error("unused")
    override suspend fun deleteUser(id: Int): ApiResult<Unit> = error("unused")
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
