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
import com.continuum.app.model.admin.WatchProviderActivity
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
import kotlin.test.assertFalse
import kotlin.test.assertNull

@OptIn(ExperimentalCoroutinesApi::class)
class AdminStatsViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()
    @BeforeTest fun setUp() { Dispatchers.setMain(dispatcher) }
    @AfterTest fun tearDown() { Dispatchers.resetMain() }

    private fun stats() = AdminStats(
        totalItems = 10, totalFiles = 20, totalUsers = 3,
        totalMovies = 4, totalMovieFiles = 4, totalShows = 6, totalShowFiles = 16,
        activeStreams = 2, totalStorageBytes = 1024L * 1024L * 1024L,
        watchProviderActivity = WatchProviderActivity(traktConnectedProfiles = 1, scrobbles24h = 7),
    )

    @Test fun `loads stats on init`() = runTest(dispatcher) {
        val api = FakeAdminApi(ApiResult.Success(stats()))
        val state = AdminStatsViewModel(AdminRepository(api)).uiState.value
        assertFalse(state.isLoading)
        assertNull(state.error)
        assertEquals(2, state.stats?.activeStreams)
        assertEquals(false, api.calls.last()) // initial load: refresh=false
    }

    @Test fun `refresh requests a server recompute`() = runTest(dispatcher) {
        val api = FakeAdminApi(ApiResult.Success(stats()))
        val vm = AdminStatsViewModel(AdminRepository(api))
        vm.refresh()
        assertEquals(true, api.calls.last()) // refresh=true
        assertFalse(vm.uiState.value.isRefreshing)
    }

    @Test fun `error surfaces server message with fallback`() = runTest(dispatcher) {
        val api = FakeAdminApi(ApiResult.Error(code = 500, error = "internal", message = ""))
        assertEquals("Failed to load admin stats", AdminStatsViewModel(AdminRepository(api)).uiState.value.error)
    }

    @Test fun `network failure surfaces standard copy`() = runTest(dispatcher) {
        val api = FakeAdminApi(ApiResult.NetworkError(IllegalStateException("offline")))
        assertEquals("Network error. Check your connection.", AdminStatsViewModel(AdminRepository(api)).uiState.value.error)
    }
}

private class FakeAdminApi(var result: ApiResult<AdminStats>) : AdminApi {
    val calls = mutableListOf<Boolean>()

    override suspend fun getStats(refresh: Boolean): ApiResult<AdminStats> {
        calls += refresh
        return result
    }

    override suspend fun getUsers(): ApiResult<List<AdminUser>> = error("unused")
    override suspend fun getUser(id: Int): ApiResult<AdminUser> = error("unused")
    override suspend fun createUser(request: CreateUserRequest): ApiResult<AdminUser> = error("unused")
    override suspend fun updateUser(id: Int, request: UpdateUserRequest): ApiResult<AdminUser> = error("unused")
    override suspend fun deleteUser(id: Int): ApiResult<Unit> = error("unused")
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
