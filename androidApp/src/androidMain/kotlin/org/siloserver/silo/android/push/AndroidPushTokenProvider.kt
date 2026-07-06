package org.siloserver.silo.android.push

import org.siloserver.silo.model.notifications.PushDeviceRegisterResponse
import org.siloserver.silo.network.ApiResult
import org.siloserver.silo.repository.PushRegistrationRepository

interface AndroidPushTokenProvider {
    suspend fun token(): String?
}

class DisabledAndroidPushTokenProvider : AndroidPushTokenProvider {
    override suspend fun token(): String? = null
}

class AndroidPushRegistrar(
    private val tokenProvider: AndroidPushTokenProvider,
    private val repository: PushRegistrationRepository,
    private val deviceIdProvider: () -> String,
) {
    suspend fun registerIfAvailable(): ApiResult<PushDeviceRegisterResponse>? {
        val token = tokenProvider.token()?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        return repository.registerAndroidDevice(
            token = token,
            deviceId = deviceIdProvider(),
        )
    }

    suspend fun unregisterDevice(): ApiResult<Unit> =
        repository.unregisterDevice(deviceIdProvider())
}
