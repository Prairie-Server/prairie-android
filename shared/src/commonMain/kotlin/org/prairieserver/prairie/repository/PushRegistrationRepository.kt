package org.prairieserver.prairie.repository

import org.prairieserver.prairie.model.notifications.PushDeviceRegisterRequest
import org.prairieserver.prairie.network.AuthScopeSnapshot
import org.prairieserver.prairie.network.api.PushRegistrationApi

/** Transport facade; the phone registrar owns durable installation allocation. */
class PushRegistrationRepository(private val api: PushRegistrationApi) {
    suspend fun available(owner: AuthScopeSnapshot) = api.available(owner)
    suspend fun registerAndroidDevice(request: PushDeviceRegisterRequest, key: String, generation: Long, owner: AuthScopeSnapshot) =
        api.register(request, key, generation, owner)
    suspend fun unregisterDevice(deviceId: String, key: String, generation: Long, owner: AuthScopeSnapshot) =
        api.delete(deviceId, key, generation, owner)
}
