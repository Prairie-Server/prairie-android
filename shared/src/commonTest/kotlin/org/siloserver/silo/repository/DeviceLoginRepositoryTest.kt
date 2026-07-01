package org.siloserver.silo.repository

import org.siloserver.silo.model.auth.DeviceLoginPollResponse
import org.siloserver.silo.model.auth.DeviceLoginDecisionResponse
import org.siloserver.silo.model.auth.DeviceLoginLookupResponse
import org.siloserver.silo.model.auth.DeviceLoginStartResponse
import org.siloserver.silo.network.ApiResult
import org.siloserver.silo.network.api.DeviceLoginApi
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class DeviceLoginRepositoryTest {

    private fun session(deviceCode: String = "DEV_CODE_123") = DeviceLoginStartResponse(
        deviceCode = deviceCode,
        userCode = "ABCD-1234",
        matchCode = "M1",
        verificationUri = "https://silo.example/device",
        verificationUriComplete = "https://silo.example/device?token=t1",
        expiresAt = "2099-01-01T00:00:00Z",
        expiresIn = 600,
        interval = 1,  // small for tests
        deviceName = "Shield",
        devicePlatform = "androidtv",
    )

    private fun pollResponse(
        status: String = "pending",
        accessToken: String? = null,
        refreshToken: String? = null,
    ) = DeviceLoginPollResponse(
        status = status,
        accessToken = accessToken,
        refreshToken = refreshToken,
    )

    private fun fakeApi(
        lookup: ApiResult<DeviceLoginLookupResponse> = ApiResult.Success(
            DeviceLoginLookupResponse(
                status = "pending",
                userCode = "ABCD-1234",
                matchCode = "M1",
                deviceName = "Shield",
                devicePlatform = "androidtv",
            ),
        ),
        approve: ApiResult<DeviceLoginDecisionResponse> = ApiResult.Success(
            DeviceLoginDecisionResponse(status = "approved"),
        ),
        deny: ApiResult<DeviceLoginDecisionResponse> = ApiResult.Success(
            DeviceLoginDecisionResponse(status = "denied"),
        ),
    ) = object : DeviceLoginApi {
        override suspend fun startDeviceLogin(deviceName: String?, devicePlatform: String?) =
            ApiResult.Success(session())
        override suspend fun pollDeviceLogin(deviceCode: String) =
            ApiResult.Success(pollResponse(status = "approved", accessToken = "at", refreshToken = "rt"))
        override suspend fun lookupDeviceLogin(token: String?, code: String?) = lookup
        override suspend fun approveDeviceLogin(token: String?, code: String?) = approve
        override suspend fun denyDeviceLogin(token: String?, code: String?) = deny
    }

    @Test
    fun `lookup returns device login details for scanned token`() = runTest(UnconfinedTestDispatcher()) {
        val repo = DeviceLoginRepository(fakeApi())

        val result = repo.lookup(token = "browser-token", code = null)

        assertIs<ApiResult.Success<DeviceLoginLookupResponse>>(result)
        assertEquals("Shield", result.data.deviceName)
        assertEquals("M1", result.data.matchCode)
    }

    @Test
    fun `approve sends scanned token through device login API`() = runTest(UnconfinedTestDispatcher()) {
        val repo = DeviceLoginRepository(fakeApi())

        val result = repo.approve(token = "browser-token", code = null)

        assertIs<ApiResult.Success<DeviceLoginDecisionResponse>>(result)
        assertEquals("approved", result.data.status)
    }

    @Test
    fun `deny sends manual user code through device login API`() = runTest(UnconfinedTestDispatcher()) {
        val repo = DeviceLoginRepository(fakeApi())

        val result = repo.deny(token = null, code = "ABCD-1234")

        assertIs<ApiResult.Success<DeviceLoginDecisionResponse>>(result)
        assertEquals("denied", result.data.status)
    }

    @Test
    fun `start failure transitions to Failed(StartFailed)`() = runTest(UnconfinedTestDispatcher()) {
        val api = object : DeviceLoginApi {
            override suspend fun startDeviceLogin(deviceName: String?, devicePlatform: String?) =
                ApiResult.Error(code = 500, error = "", message = "boom")
            override suspend fun pollDeviceLogin(deviceCode: String) =
                error("unreachable")
            override suspend fun lookupDeviceLogin(token: String?, code: String?) =
                error("unreachable")
            override suspend fun approveDeviceLogin(token: String?, code: String?) =
                error("unreachable")
            override suspend fun denyDeviceLogin(token: String?, code: String?) =
                error("unreachable")
        }
        val repo = DeviceLoginRepository(api)
        repo.begin("d", "p")
        val state = repo.state.value
        assertIs<DeviceLoginRepository.DeviceLoginState.Failed>(state)
        assertEquals(DeviceLoginRepository.FailureReason.StartFailed, state.reason)
    }

    @Test
    fun `pending status keeps polling until approved`() = runTest(UnconfinedTestDispatcher()) {
        var pollCount = 0
        val api = object : DeviceLoginApi {
            override suspend fun startDeviceLogin(deviceName: String?, devicePlatform: String?) =
                ApiResult.Success(session())
            override suspend fun pollDeviceLogin(deviceCode: String): ApiResult<DeviceLoginPollResponse> {
                pollCount++
                return if (pollCount < 3) {
                    ApiResult.Success(pollResponse(status = "pending"))
                } else {
                    ApiResult.Success(
                        pollResponse(
                            status = "approved",
                            accessToken = "at",
                            refreshToken = "rt",
                        )
                    )
                }
            }
            override suspend fun lookupDeviceLogin(token: String?, code: String?) =
                error("unreachable")
            override suspend fun approveDeviceLogin(token: String?, code: String?) =
                error("unreachable")
            override suspend fun denyDeviceLogin(token: String?, code: String?) =
                error("unreachable")
        }
        val repo = DeviceLoginRepository(api)
        repo.begin("d", "p")
        assertIs<DeviceLoginRepository.DeviceLoginState.Approved>(repo.state.value)
        assertTrue(pollCount >= 3)
    }

    @Test
    fun `404 on poll transitions to Failed(Expired)`() = runTest(UnconfinedTestDispatcher()) {
        val api = object : DeviceLoginApi {
            override suspend fun startDeviceLogin(deviceName: String?, devicePlatform: String?) =
                ApiResult.Success(session())
            override suspend fun pollDeviceLogin(deviceCode: String) =
                ApiResult.Error(code = 404, error = "", message = "Not Found")
            override suspend fun lookupDeviceLogin(token: String?, code: String?) =
                error("unreachable")
            override suspend fun approveDeviceLogin(token: String?, code: String?) =
                error("unreachable")
            override suspend fun denyDeviceLogin(token: String?, code: String?) =
                error("unreachable")
        }
        val repo = DeviceLoginRepository(api)
        repo.begin("d", "p")
        val state = repo.state.value
        assertIs<DeviceLoginRepository.DeviceLoginState.Failed>(state)
        assertEquals(DeviceLoginRepository.FailureReason.Expired, state.reason)
    }

    @Test
    fun `approved without tokens transitions to Failed(MissingTokens)`() = runTest(UnconfinedTestDispatcher()) {
        val api = object : DeviceLoginApi {
            override suspend fun startDeviceLogin(deviceName: String?, devicePlatform: String?) =
                ApiResult.Success(session())
            override suspend fun pollDeviceLogin(deviceCode: String) =
                ApiResult.Success(pollResponse(status = "approved"))  // no tokens
            override suspend fun lookupDeviceLogin(token: String?, code: String?) =
                error("unreachable")
            override suspend fun approveDeviceLogin(token: String?, code: String?) =
                error("unreachable")
            override suspend fun denyDeviceLogin(token: String?, code: String?) =
                error("unreachable")
        }
        val repo = DeviceLoginRepository(api)
        repo.begin("d", "p")
        val state = repo.state.value
        assertIs<DeviceLoginRepository.DeviceLoginState.Failed>(state)
        assertEquals(DeviceLoginRepository.FailureReason.MissingTokens, state.reason)
    }

    @Test
    fun `denied status transitions to Failed(Denied)`() = runTest(UnconfinedTestDispatcher()) {
        val api = object : DeviceLoginApi {
            override suspend fun startDeviceLogin(deviceName: String?, devicePlatform: String?) =
                ApiResult.Success(session())
            override suspend fun pollDeviceLogin(deviceCode: String) =
                ApiResult.Success(pollResponse(status = "denied"))
            override suspend fun lookupDeviceLogin(token: String?, code: String?) =
                error("unreachable")
            override suspend fun approveDeviceLogin(token: String?, code: String?) =
                error("unreachable")
            override suspend fun denyDeviceLogin(token: String?, code: String?) =
                error("unreachable")
        }
        val repo = DeviceLoginRepository(api)
        repo.begin("d", "p")
        val state = repo.state.value
        assertIs<DeviceLoginRepository.DeviceLoginState.Failed>(state)
        assertEquals(DeviceLoginRepository.FailureReason.Denied, state.reason)
    }

    @Test
    fun `unknown status transitions to Failed(UnknownStatus)`() = runTest(UnconfinedTestDispatcher()) {
        val api = object : DeviceLoginApi {
            override suspend fun startDeviceLogin(deviceName: String?, devicePlatform: String?) =
                ApiResult.Success(session())
            override suspend fun pollDeviceLogin(deviceCode: String) =
                ApiResult.Success(pollResponse(status = "warp_speed_pending"))
            override suspend fun lookupDeviceLogin(token: String?, code: String?) =
                error("unreachable")
            override suspend fun approveDeviceLogin(token: String?, code: String?) =
                error("unreachable")
            override suspend fun denyDeviceLogin(token: String?, code: String?) =
                error("unreachable")
        }
        val repo = DeviceLoginRepository(api)
        repo.begin("d", "p")
        val state = repo.state.value
        assertIs<DeviceLoginRepository.DeviceLoginState.Failed>(state)
        assertEquals(DeviceLoginRepository.FailureReason.UnknownStatus, state.reason)
    }

    @Test
    fun `network error on start transitions to Failed(StartFailed)`() = runTest(UnconfinedTestDispatcher()) {
        val api = object : DeviceLoginApi {
            override suspend fun startDeviceLogin(deviceName: String?, devicePlatform: String?) =
                ApiResult.NetworkError(exception = RuntimeException("offline"))
            override suspend fun pollDeviceLogin(deviceCode: String) = error("unreachable")
            override suspend fun lookupDeviceLogin(token: String?, code: String?) =
                error("unreachable")
            override suspend fun approveDeviceLogin(token: String?, code: String?) =
                error("unreachable")
            override suspend fun denyDeviceLogin(token: String?, code: String?) =
                error("unreachable")
        }
        val repo = DeviceLoginRepository(api)
        repo.begin("d", "p")
        val state = repo.state.value
        assertIs<DeviceLoginRepository.DeviceLoginState.Failed>(state)
        assertEquals(DeviceLoginRepository.FailureReason.StartFailed, state.reason)
    }
}
