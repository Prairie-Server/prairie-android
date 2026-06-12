package com.continuum.app.network.api

import com.continuum.app.model.auth.DeviceLoginPollRequest
import com.continuum.app.model.auth.DeviceLoginPollResponse
import com.continuum.app.model.auth.DeviceLoginDecisionRequest
import com.continuum.app.model.auth.DeviceLoginDecisionResponse
import com.continuum.app.model.auth.DeviceLoginLookupResponse
import com.continuum.app.model.auth.DeviceLoginStartRequest
import com.continuum.app.model.auth.DeviceLoginStartResponse
import com.continuum.app.network.ApiResult
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType

/**
 * OAuth-style device-login endpoints. Mirrors Apple's tvOS
 * `AuthService.startDeviceLogin` / `AuthService.pollDeviceLogin`
 * (see `/opt/silo-apple/iosApp/iosApp/Screens/Auth/AuthService.swift:209-227`).
 *
 * Modeled as an interface so [DeviceLoginRepository] tests can substitute
 * a fake without standing up a Ktor [HttpClient]. The real implementation
 * is [DefaultDeviceLoginApi].
 */
interface DeviceLoginApi {

    suspend fun startDeviceLogin(
        deviceName: String?,
        devicePlatform: String?,
    ): ApiResult<DeviceLoginStartResponse>

    /**
     * Polls the server for the device-login status. A 404 surfaces as
     * [ApiResult.Error] with `code = 404` — the repository treats that
     * as a terminal "expired pairing row" signal.
     */
    suspend fun pollDeviceLogin(deviceCode: String): ApiResult<DeviceLoginPollResponse>

    suspend fun lookupDeviceLogin(
        token: String?,
        code: String?,
    ): ApiResult<DeviceLoginLookupResponse>

    suspend fun approveDeviceLogin(
        token: String?,
        code: String?,
    ): ApiResult<DeviceLoginDecisionResponse>

    suspend fun denyDeviceLogin(
        token: String?,
        code: String?,
    ): ApiResult<DeviceLoginDecisionResponse>
}

/**
 * Ktor-backed implementation. Funnels both calls through [safeApiCall]
 * for unified error handling, matching [AuthApi]'s pattern.
 */
class DefaultDeviceLoginApi(private val client: HttpClient) : DeviceLoginApi {

    override suspend fun startDeviceLogin(
        deviceName: String?,
        devicePlatform: String?,
    ): ApiResult<DeviceLoginStartResponse> = safeApiCall {
        client.post("/api/v1/auth/device/start") {
            contentType(ContentType.Application.Json)
            setBody(DeviceLoginStartRequest(deviceName, devicePlatform))
        }
    }

    override suspend fun pollDeviceLogin(deviceCode: String): ApiResult<DeviceLoginPollResponse> = safeApiCall {
        client.post("/api/v1/auth/device/poll") {
            contentType(ContentType.Application.Json)
            setBody(DeviceLoginPollRequest(deviceCode))
        }
    }

    override suspend fun lookupDeviceLogin(
        token: String?,
        code: String?,
    ): ApiResult<DeviceLoginLookupResponse> = safeApiCall {
        client.get("/api/v1/auth/device") {
            parameter("token", token?.takeIf { it.isNotBlank() })
            parameter("code", code?.takeIf { it.isNotBlank() })
        }
    }

    override suspend fun approveDeviceLogin(
        token: String?,
        code: String?,
    ): ApiResult<DeviceLoginDecisionResponse> = safeApiCall {
        client.post("/api/v1/auth/device/approve") {
            contentType(ContentType.Application.Json)
            setBody(DeviceLoginDecisionRequest(token = token, code = code))
        }
    }

    override suspend fun denyDeviceLogin(
        token: String?,
        code: String?,
    ): ApiResult<DeviceLoginDecisionResponse> = safeApiCall {
        client.post("/api/v1/auth/device/deny") {
            contentType(ContentType.Application.Json)
            setBody(DeviceLoginDecisionRequest(token = token, code = code))
        }
    }
}
