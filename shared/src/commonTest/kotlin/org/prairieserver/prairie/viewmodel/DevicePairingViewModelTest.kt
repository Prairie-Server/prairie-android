package org.prairieserver.prairie.viewmodel

import androidx.lifecycle.viewModelScope
import org.prairieserver.prairie.model.auth.DeviceLoginDecisionResponse
import org.prairieserver.prairie.model.auth.DeviceLoginLookupResponse
import org.prairieserver.prairie.model.auth.DeviceLoginPollResponse
import org.prairieserver.prairie.model.auth.DeviceLoginStartResponse
import org.prairieserver.prairie.network.ApiResult
import org.prairieserver.prairie.network.api.DeviceLoginApi
import org.prairieserver.prairie.repository.DeviceLoginRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
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
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class DevicePairingViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()
    @BeforeTest fun setUp() { Dispatchers.setMain(dispatcher) }
    @AfterTest
    fun tearDown() {
        createdViewModels.forEach { it.viewModelScope.cancel() }
        createdViewModels.clear()
        Dispatchers.resetMain()
    }

    // Cancel viewModelScope coroutines BEFORE resetting Main: a coroutine
    // still parked on Dispatchers.Main when a later test calls setMain/resetMain
    // throws IllegalStateException from TestMainDispatcher.
    private val createdViewModels = mutableListOf<androidx.lifecycle.ViewModel>()

    private fun <T : androidx.lifecycle.ViewModel> track(viewModel: T): T {
        createdViewModels += viewModel
        return viewModel
    }


    private fun api(
        lookup: ApiResult<DeviceLoginLookupResponse> = ApiResult.Success(
            DeviceLoginLookupResponse(status = "pending", deviceName = "TV"),
        ),
        approve: ApiResult<DeviceLoginDecisionResponse> = ApiResult.Success(
            DeviceLoginDecisionResponse(status = "approved"),
        ),
        deny: ApiResult<DeviceLoginDecisionResponse> = ApiResult.Success(
            DeviceLoginDecisionResponse(status = "denied"),
        ),
    ) = object : DeviceLoginApi {
        override suspend fun startDeviceLogin(deviceName: String?, devicePlatform: String?) =
            ApiResult.Success(
                DeviceLoginStartResponse(
                    deviceCode = "d",
                    userCode = "U",
                    matchCode = "M",
                    verificationUri = "https://x",
                    verificationUriComplete = "https://x?c=d",
                    expiresAt = "t",
                    expiresIn = 1,
                    interval = 1,
                    deviceName = "TV",
                    devicePlatform = "androidtv",
                ),
            )
        override suspend fun pollDeviceLogin(deviceCode: String) =
            ApiResult.Success(DeviceLoginPollResponse(status = "pending"))
        override suspend fun lookupDeviceLogin(token: String?, code: String?) = lookup
        override suspend fun approveDeviceLogin(token: String?, code: String?) = approve
        override suspend fun denyDeviceLogin(token: String?, code: String?) = deny
    }

    @Test
    fun lookupApproveAndDenyHappyPath() = runTest(dispatcher) {
        val vm = track(DevicePairingViewModel(DeviceLoginRepository(api()), initialToken = "tok", initialCode = null))
        assertEquals("TV", vm.uiState.value.lookup?.deviceName)
        assertFalse(vm.uiState.value.isLoading)
        vm.approve()
        assertEquals("approved", vm.uiState.value.completedStatus)
        val denyVm = track(DevicePairingViewModel(DeviceLoginRepository(api()), initialToken = null, initialCode = "ABCD"))
        denyVm.deny()
        assertEquals("denied", denyVm.uiState.value.completedStatus)
    }

    @Test
    fun codeChangesAndErrors() = runTest(dispatcher) {
        val vm = track(DevicePairingViewModel(DeviceLoginRepository(api()), initialToken = null, initialCode = null))
        assertFalse(vm.uiState.value.canSubmit)
        vm.lookup()
        assertTrue(vm.uiState.value.error!!.contains("Enter the code"))
        vm.onCodeChanged(" abcd ")
        assertEquals("ABCD", vm.uiState.value.code)
        assertTrue(vm.uiState.value.canSubmit)

        val errVm = track(DevicePairingViewModel(
            DeviceLoginRepository(api(lookup = ApiResult.Error(401, "x", "nope"))),
            initialToken = "t",
            initialCode = null,
        ))
        assertTrue(errVm.uiState.value.error!!.contains("Sign in"))

        val netVm = track(DevicePairingViewModel(
            DeviceLoginRepository(api(lookup = ApiResult.NetworkError(IllegalStateException("x")))),
            initialToken = "t",
            initialCode = null,
        ))
        assertTrue(netVm.uiState.value.error!!.contains("Network"))
    }
}
