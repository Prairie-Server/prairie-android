package org.prairieserver.prairie.repository

import org.prairieserver.prairie.model.notifications.PushDeviceRegisterRequest
import org.prairieserver.prairie.model.notifications.PushDeviceRegisterResponse
import org.prairieserver.prairie.network.ApiResult
import org.prairieserver.prairie.network.api.PushRegistrationApi

class PushRegistrationRepository(
    private val api: PushRegistrationApi,
) {
    suspend fun registerAndroidDevice(
        token: String,
        deviceId: String,
        pushMode: String = PUSH_MODE_PRIVATE,
    ): ApiResult<PushDeviceRegisterResponse> =
        api.register(
            PushDeviceRegisterRequest(
                platform = PLATFORM_ANDROID,
                token = token,
                deviceId = deviceId,
                pushMode = pushMode,
            ),
        )

    suspend fun unregisterDevice(deviceId: String): ApiResult<Unit> =
        api.delete(deviceId)

    companion object {
        const val PLATFORM_ANDROID = "android"
        const val PUSH_MODE_PRIVATE = "private_push"
    }
}
