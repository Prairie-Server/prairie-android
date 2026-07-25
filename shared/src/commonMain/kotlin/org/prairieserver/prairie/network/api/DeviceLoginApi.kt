package org.prairieserver.prairie.network.api

import org.prairieserver.prairie.model.auth.DeviceLoginPollRequest
import org.prairieserver.prairie.model.auth.DeviceLoginPollResponse
import org.prairieserver.prairie.model.auth.DeviceLoginDecisionRequest
import org.prairieserver.prairie.model.auth.DeviceLoginDecisionResponse
import org.prairieserver.prairie.model.auth.DeviceLoginCapabilityResponse
import org.prairieserver.prairie.model.auth.DeviceLoginLookupResponse
import org.prairieserver.prairie.model.auth.DeviceLoginStartRequest
import org.prairieserver.prairie.model.auth.DeviceLoginStartResponse
import org.prairieserver.prairie.network.ApiResult
import org.prairieserver.prairie.network.AuthScopeSnapshot
import org.prairieserver.prairie.network.authScope
import org.prairieserver.prairie.network.skipPrairieAuth
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
 * (see `/opt/prairie-apple/iosApp/iosApp/Screens/Auth/AuthService.swift:209-227`).
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

    /**
     * Start device login against an untrusted candidate server without changing
     * the app's active server or sending its current bearer/profile headers.
     */
    suspend fun startDeviceLoginAt(
        serverUrl: String,
        deviceName: String?,
        devicePlatform: String?,
    ): ApiResult<DeviceLoginStartResponse> = unsupportedScopedDeviceLoginOperation()

    /** Poll the same candidate server without mutating or authenticating the active scope. */
    suspend fun pollDeviceLoginAt(
        serverUrl: String,
        deviceCode: String,
    ): ApiResult<DeviceLoginPollResponse> = unsupportedScopedDeviceLoginOperation()

    suspend fun remotePlaybackCapabilityAt(
        serverUrl: String,
    ): ApiResult<DeviceLoginCapabilityResponse> = ApiResult.Error(
        code = 501,
        error = "remote_playback_unsupported",
        message = "Remote playback handoff is not supported.",
    )

    suspend fun startRemotePlaybackAt(
        serverUrl: String,
        deviceName: String?,
        devicePlatform: String?,
    ): ApiResult<DeviceLoginStartResponse> = unsupportedScopedDeviceLoginOperation()

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

    /** Authorize a device-login request against a profileless, pinned server scope. */
    suspend fun lookupDeviceLoginForScope(
        scope: AuthScopeSnapshot,
        code: String,
    ): ApiResult<DeviceLoginLookupResponse> = unsupportedScopedDeviceLoginOperation()

    suspend fun approveDeviceLoginForScope(
        scope: AuthScopeSnapshot,
        code: String,
    ): ApiResult<DeviceLoginDecisionResponse> = unsupportedScopedDeviceLoginOperation()

    suspend fun approveRemotePlaybackForScope(
        scope: AuthScopeSnapshot,
        code: String,
    ): ApiResult<DeviceLoginDecisionResponse> = approveDeviceLoginForScope(scope, code)

    suspend fun endRemotePlayback(scope: AuthScopeSnapshot): ApiResult<Unit> = unsupportedScopedDeviceLoginOperation()

    suspend fun denyDeviceLoginForScope(
        scope: AuthScopeSnapshot,
        code: String,
    ): ApiResult<DeviceLoginDecisionResponse> = unsupportedScopedDeviceLoginOperation()
}

private fun <T> unsupportedScopedDeviceLoginOperation(): ApiResult<T> = ApiResult.Error(
    code = 501,
    error = "scoped_device_login_unsupported",
    message = "This device-login implementation does not support candidate or pinned auth scopes.",
)

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

    override suspend fun startDeviceLoginAt(
        serverUrl: String,
        deviceName: String?,
        devicePlatform: String?,
    ): ApiResult<DeviceLoginStartResponse> = safeApiCall {
        client.post("${serverUrl.trimEnd('/')}/api/v1/auth/device/start") {
            skipPrairieAuth()
            contentType(ContentType.Application.Json)
            setBody(DeviceLoginStartRequest(deviceName, devicePlatform))
        }
    }

    override suspend fun pollDeviceLoginAt(
        serverUrl: String,
        deviceCode: String,
    ): ApiResult<DeviceLoginPollResponse> = safeApiCall {
        client.post("${serverUrl.trimEnd('/')}/api/v1/auth/device/poll") {
            skipPrairieAuth()
            contentType(ContentType.Application.Json)
            setBody(DeviceLoginPollRequest(deviceCode))
        }
    }

    override suspend fun remotePlaybackCapabilityAt(
        serverUrl: String,
    ): ApiResult<DeviceLoginCapabilityResponse> = safeApiCall {
        client.get("${serverUrl.trimEnd('/')}/api/v1/auth/device/capability") {
            skipPrairieAuth()
        }
    }

    override suspend fun startRemotePlaybackAt(
        serverUrl: String,
        deviceName: String?,
        devicePlatform: String?,
    ): ApiResult<DeviceLoginStartResponse> = safeApiCall {
        client.post("${serverUrl.trimEnd('/')}/api/v1/auth/device/start") {
            skipPrairieAuth()
            contentType(ContentType.Application.Json)
            setBody(
                DeviceLoginStartRequest(
                    deviceName = deviceName,
                    devicePlatform = devicePlatform,
                    clientPurpose = "remote_playback",
                    temporary = true,
                ),
            )
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

    override suspend fun lookupDeviceLoginForScope(
        scope: AuthScopeSnapshot,
        code: String,
    ): ApiResult<DeviceLoginLookupResponse> = safeApiCall {
        client.get("/api/v1/auth/device") {
            authScope(scope)
            parameter("code", code)
        }
    }

    override suspend fun approveDeviceLoginForScope(
        scope: AuthScopeSnapshot,
        code: String,
    ): ApiResult<DeviceLoginDecisionResponse> = safeApiCall {
        client.post("/api/v1/auth/device/approve") {
            authScope(scope)
            contentType(ContentType.Application.Json)
            setBody(DeviceLoginDecisionRequest(code = code))
        }
    }

    override suspend fun approveRemotePlaybackForScope(
        scope: AuthScopeSnapshot,
        code: String,
    ): ApiResult<DeviceLoginDecisionResponse> = safeApiCall {
        client.post("/api/v1/auth/device/approve-handoff") {
            authScope(scope)
            contentType(ContentType.Application.Json)
            setBody(DeviceLoginDecisionRequest(code = code))
        }
    }

    override suspend fun endRemotePlayback(scope: AuthScopeSnapshot): ApiResult<Unit> = safeApiCall {
        client.post("/api/v1/auth/logout") {
            authScope(scope)
            contentType(ContentType.Application.Json)
        }
    }

    override suspend fun denyDeviceLoginForScope(
        scope: AuthScopeSnapshot,
        code: String,
    ): ApiResult<DeviceLoginDecisionResponse> = safeApiCall {
        client.post("/api/v1/auth/device/deny") {
            authScope(scope)
            contentType(ContentType.Application.Json)
            setBody(DeviceLoginDecisionRequest(code = code))
        }
    }
}
