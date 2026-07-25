package org.prairieserver.prairie.network.api

import io.ktor.client.HttpClient
import io.ktor.client.request.delete
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.encodeURLPathPart
import org.prairieserver.prairie.model.notifications.PushDeviceRegisterRequest
import org.prairieserver.prairie.model.notifications.PushDeviceRegisterResponse
import org.prairieserver.prairie.network.ApiResult

interface PushRegistrationApi {
    suspend fun register(request: PushDeviceRegisterRequest): ApiResult<PushDeviceRegisterResponse>
    suspend fun delete(deviceId: String): ApiResult<Unit>
}

class DefaultPushRegistrationApi(private val client: HttpClient) : PushRegistrationApi {
    override suspend fun register(request: PushDeviceRegisterRequest): ApiResult<PushDeviceRegisterResponse> =
        safeApiCall {
            client.post(DEVICES_PATH) {
                contentType(ContentType.Application.Json)
                setBody(request)
            }
        }

    override suspend fun delete(deviceId: String): ApiResult<Unit> = safeApiCall {
        client.delete("$DEVICES_PATH/${deviceId.encodeURLPathPart()}")
    }

    companion object {
        const val DEVICES_PATH = "/api/v1/notifications/push/devices"
    }
}
