package org.prairieserver.prairie.tv.cast

import org.prairieserver.prairie.cast.PrairieCastProtocol
import org.prairieserver.prairie.model.auth.DeviceLoginCapabilityResponse
import org.prairieserver.prairie.model.auth.DeviceLoginPollResponse
import org.prairieserver.prairie.model.auth.DeviceLoginStartResponse
import org.prairieserver.prairie.network.ApiResult
import org.prairieserver.prairie.network.AuthScopeSnapshot
import org.prairieserver.prairie.network.api.DeviceLoginApi

/** Remote-playback device-login API that approves every handoff unless told otherwise. */
internal class FakeRemotePlaybackApi : DeviceLoginApi {
    var endCalls = 0
    var approvedProfileId = "profile-1"
    var pollStatus = "approved"

    override suspend fun remotePlaybackCapabilityAt(serverUrl: String) = ApiResult.Success(
        DeviceLoginCapabilityResponse(
            remotePlaybackHandoff = true,
            protocolVersions = listOf(PrairieCastProtocol.version),
        ),
    )

    override suspend fun startRemotePlaybackAt(
        serverUrl: String,
        deviceName: String?,
        devicePlatform: String?,
    ) = ApiResult.Success(
        DeviceLoginStartResponse(
            deviceCode = "device-code",
            userCode = "USER",
            matchCode = "12",
            verificationUri = "$serverUrl/pair",
            verificationUriComplete = "$serverUrl/pair?code=USER",
            expiresAt = "2099-01-01T00:00:00Z",
            expiresIn = 60,
            interval = 1,
            deviceName = deviceName.orEmpty(),
            devicePlatform = devicePlatform.orEmpty(),
            clientPurpose = "remote_playback",
            temporary = true,
        ),
    )

    override suspend fun pollDeviceLoginAt(serverUrl: String, deviceCode: String) = ApiResult.Success(
        DeviceLoginPollResponse(
            status = pollStatus,
            accessToken = "access",
            refreshToken = "refresh",
            profileId = approvedProfileId,
            profileToken = "profile-token",
            temporary = true,
        ),
    )

    override suspend fun endRemotePlayback(scope: AuthScopeSnapshot): ApiResult<Unit> {
        endCalls += 1
        return ApiResult.Success(Unit)
    }

    override suspend fun startDeviceLogin(deviceName: String?, devicePlatform: String?) = error("unused")
    override suspend fun pollDeviceLogin(deviceCode: String) = error("unused")
    override suspend fun lookupDeviceLogin(token: String?, code: String?) = error("unused")
    override suspend fun approveDeviceLogin(token: String?, code: String?) = error("unused")
    override suspend fun denyDeviceLogin(token: String?, code: String?) = error("unused")
}
