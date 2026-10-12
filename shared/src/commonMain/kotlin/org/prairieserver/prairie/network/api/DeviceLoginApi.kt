package org.prairieserver.prairie.network.api

import org.prairieserver.prairie.model.auth.DeviceLoginCancelResponse
import org.prairieserver.prairie.model.auth.DeviceLoginPollRequest
import org.prairieserver.prairie.model.auth.DeviceLoginPollResponse
import org.prairieserver.prairie.model.auth.DeviceLoginDecisionRequest
import org.prairieserver.prairie.model.auth.DeviceLoginDecisionResponse
import org.prairieserver.prairie.model.auth.DeviceLoginCapabilityResponse
import org.prairieserver.prairie.model.auth.DeviceLoginLookupResponse
import org.prairieserver.prairie.model.auth.DeviceLoginStartRequest
import org.prairieserver.prairie.model.auth.DeviceLoginStartResponse
import org.prairieserver.prairie.network.apiv2.ApiV2Gate
import org.prairieserver.prairie.network.apiv2.safeApiV2Call
import org.prairieserver.prairie.network.map
import org.prairieserver.prairie.network.singleAttempt
import org.prairieserver.prairie.network.ApiResult
import org.prairieserver.prairie.network.AuthScopeSnapshot
import org.prairieserver.prairie.network.authScope
import org.prairieserver.prairie.network.freshPrairieAuth
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

    /**
     * The device-pairing capability document at [serverUrl]: whether device
     * sign-in is configured, and whether the server supports cancel and the
     * opened signal. Same document [remotePlaybackCapabilityAt] reads.
     */
    suspend fun deviceLoginCapabilityAt(
        serverUrl: String,
    ): ApiResult<DeviceLoginCapabilityResponse> = remotePlaybackCapabilityAt(serverUrl)

    /**
     * Withdraw this device's own request (`POST /auth/device/cancel`) so an
     * abandoned code can't be approved later. Only call it when the
     * capability reports `cancel`.
     */
    suspend fun cancelDeviceLoginAt(
        serverUrl: String,
        deviceCode: String,
    ): ApiResult<DeviceLoginCancelResponse> = unsupportedScopedDeviceLoginOperation()

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
 * Ktor-backed implementation. Uses [safeApiV2Call]
 * for unified error handling, matching [AuthApi]'s pattern.
 */
class DefaultDeviceLoginApi(private val client: HttpClient, private val gate: ApiV2Gate) : DeviceLoginApi {

    override suspend fun startDeviceLogin(
        deviceName: String?,
        devicePlatform: String?,
    ): ApiResult<DeviceLoginStartResponse> = safeApiV2Call(gate) {
        client.post("/api/v2/auth/device/start") {
            skipPrairieAuth()
            singleAttempt()
            contentType(ContentType.Application.Json)
            setBody(DeviceLoginStartRequest(deviceName, devicePlatform))
        }.requireAuthStatus(201)
    }

    override suspend fun pollDeviceLogin(deviceCode: String): ApiResult<DeviceLoginPollResponse> = safeApiV2Call<DevicePollV2>(gate) {
        client.post("/api/v2/auth/device/poll") {
            skipPrairieAuth()
            singleAttempt()
            contentType(ContentType.Application.Json)
            setBody(DeviceLoginPollRequest(deviceCode))
        }.requireAuthStatus(200)
    }.map { it.domain() }

    override suspend fun startDeviceLoginAt(
        serverUrl: String,
        deviceName: String?,
        devicePlatform: String?,
    ): ApiResult<DeviceLoginStartResponse> = safeApiV2Call(ApiV2Gate.Unrestricted) {
        client.post("${serverUrl.trimEnd('/')}/api/v2/auth/device/start") {
            skipPrairieAuth()
            singleAttempt()
            contentType(ContentType.Application.Json)
            setBody(DeviceLoginStartRequest(deviceName, devicePlatform))
        }.requireAuthStatus(201)
    }

    override suspend fun pollDeviceLoginAt(
        serverUrl: String,
        deviceCode: String,
    ): ApiResult<DeviceLoginPollResponse> = safeApiV2Call<DevicePollV2>(ApiV2Gate.Unrestricted) {
        client.post("${serverUrl.trimEnd('/')}/api/v2/auth/device/poll") {
            skipPrairieAuth()
            singleAttempt()
            contentType(ContentType.Application.Json)
            setBody(DeviceLoginPollRequest(deviceCode))
        }.requireAuthStatus(200)
    }.map { it.domain() }

    override suspend fun remotePlaybackCapabilityAt(
        serverUrl: String,
    ): ApiResult<DeviceLoginCapabilityResponse> = safeApiV2Call<DeviceCapabilityV2>(ApiV2Gate.Unrestricted) {
        client.get("${serverUrl.trimEnd('/')}/api/v2/auth/device/capability") {
            skipPrairieAuth()
        }.requireAuthStatus(200)
    }.map { it.domain() }

    override suspend fun deviceLoginCapabilityAt(
        serverUrl: String,
    ): ApiResult<DeviceLoginCapabilityResponse> = remotePlaybackCapabilityAt(serverUrl)

    override suspend fun cancelDeviceLoginAt(
        serverUrl: String,
        deviceCode: String,
    ): ApiResult<DeviceLoginCancelResponse> = safeApiV2Call(ApiV2Gate.Unrestricted) {
        client.post("${serverUrl.trimEnd('/')}/api/v2/auth/device/cancel") {
            skipPrairieAuth()
            singleAttempt()
            contentType(ContentType.Application.Json)
            setBody(DeviceLoginPollRequest(deviceCode))
        }.requireAuthStatus(200)
    }

    override suspend fun startRemotePlaybackAt(
        serverUrl: String,
        deviceName: String?,
        devicePlatform: String?,
    ): ApiResult<DeviceLoginStartResponse> = safeApiV2Call(ApiV2Gate.Unrestricted) {
        client.post("${serverUrl.trimEnd('/')}/api/v2/auth/device/start") {
            skipPrairieAuth()
            singleAttempt()
            contentType(ContentType.Application.Json)
            setBody(
                DeviceLoginStartRequest(
                    deviceName = deviceName,
                    devicePlatform = devicePlatform,
                    clientPurpose = "remote_playback",
                    temporary = true,
                ),
            )
        }.requireAuthStatus(201)
    }

    override suspend fun lookupDeviceLogin(
        token: String?,
        code: String?,
    ): ApiResult<DeviceLoginLookupResponse> = safeApiV2Call(gate) {
        client.get("/api/v2/auth/device") {
            skipPrairieAuth()
            parameter("token", token?.takeIf { it.isNotBlank() })
            parameter("code", code?.takeIf { it.isNotBlank() })
        }.requireAuthStatus(200)
    }

    override suspend fun approveDeviceLogin(
        token: String?,
        code: String?,
    ): ApiResult<DeviceLoginDecisionResponse> = safeApiV2Call(gate) {
        client.post("/api/v2/auth/device/approve") {
            // The approver refreshes before deciding (TV sign-in spec).
            freshPrairieAuth()
            contentType(ContentType.Application.Json)
            setBody(DeviceLoginDecisionRequest(token = token, code = code))
        }.requireAuthStatus(200)
    }

    override suspend fun denyDeviceLogin(
        token: String?,
        code: String?,
    ): ApiResult<DeviceLoginDecisionResponse> = safeApiV2Call(gate) {
        client.post("/api/v2/auth/device/deny") {
            // The approver refreshes before deciding (TV sign-in spec).
            freshPrairieAuth()
            contentType(ContentType.Application.Json)
            setBody(DeviceLoginDecisionRequest(token = token, code = code))
        }.requireAuthStatus(200)
    }

    override suspend fun lookupDeviceLoginForScope(
        scope: AuthScopeSnapshot,
        code: String,
    ): ApiResult<DeviceLoginLookupResponse> = safeApiV2Call(gate.forServer(scope.serverId)) {
        client.get("/api/v2/auth/device") {
            skipPrairieAuth()
            authScope(scope)
            parameter("code", code)
        }.requireAuthStatus(200)
    }

    override suspend fun approveDeviceLoginForScope(
        scope: AuthScopeSnapshot,
        code: String,
    ): ApiResult<DeviceLoginDecisionResponse> = safeApiV2Call(gate.forServer(scope.serverId)) {
        client.post("/api/v2/auth/device/approve") {
            authScope(scope)
            // The approver refreshes before deciding (TV sign-in spec).
            freshPrairieAuth()
            contentType(ContentType.Application.Json)
            setBody(DeviceLoginDecisionRequest(code = code))
        }.requireAuthStatus(200)
    }

    override suspend fun approveRemotePlaybackForScope(
        scope: AuthScopeSnapshot,
        code: String,
    ): ApiResult<DeviceLoginDecisionResponse> = safeApiV2Call(gate) {
        client.post("/api/v2/auth/device/approve-handoff") {
            authScope(scope)
            contentType(ContentType.Application.Json)
            setBody(DeviceLoginDecisionRequest(code = code))
        }.requireAuthStatus(200)
    }

    override suspend fun endRemotePlayback(scope: AuthScopeSnapshot): ApiResult<Unit> = safeApiV2Call(gate) {
        client.post("/api/v2/auth/logout") {
            authScope(scope)
            contentType(ContentType.Application.Json)
        }.requireAuthStatus(204)
    }

    override suspend fun denyDeviceLoginForScope(
        scope: AuthScopeSnapshot,
        code: String,
    ): ApiResult<DeviceLoginDecisionResponse> = safeApiV2Call(gate.forServer(scope.serverId)) {
        client.post("/api/v2/auth/device/deny") {
            authScope(scope)
            // The approver refreshes before deciding (TV sign-in spec).
            freshPrairieAuth()
            contentType(ContentType.Application.Json)
            setBody(DeviceLoginDecisionRequest(code = code))
        }.requireAuthStatus(200)
    }
}
