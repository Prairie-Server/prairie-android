package com.continuum.app.network.api

import com.continuum.app.model.settings.EffectiveSettingsResponse
import com.continuum.app.model.settings.EffectiveSubtitleAppearance
import com.continuum.app.model.settings.PlaybackSettingsKeys
import com.continuum.app.model.settings.SettingEntry
import com.continuum.app.model.settings.SettingsListResponse
import com.continuum.app.model.settings.SubtitleAppearance
import com.continuum.app.model.settings.UpdateSettingRequest
import com.continuum.app.network.ApiResult
import io.ktor.client.HttpClient
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType

open class SettingsApi(private val client: HttpClient) {

    open suspend fun getSettings(): ApiResult<SettingsListResponse> = safeApiCall {
        client.get("/api/v1/settings")
    }

    open suspend fun getSetting(key: String): ApiResult<SettingEntry> = safeApiCall {
        client.get("/api/v1/settings/$key")
    }

    open suspend fun setSetting(key: String, value: String): ApiResult<Unit> = safeApiCall {
        client.put("/api/v1/settings/$key") {
            contentType(ContentType.Application.Json)
            setBody(UpdateSettingRequest(value))
        }
    }

    open suspend fun deleteSetting(key: String): ApiResult<Unit> = safeApiCall {
        client.delete("/api/v1/settings/$key")
    }

    open suspend fun getDeviceSetting(key: String): ApiResult<SettingEntry> = safeApiCall {
        client.get("/api/v1/settings/device/$key")
    }

    open suspend fun setDeviceSetting(
        key: String,
        value: String,
        profileId: String? = null,
    ): ApiResult<Unit> = safeApiCall {
        client.put("/api/v1/settings/device/$key") {
            if (!profileId.isNullOrBlank()) {
                header("X-Profile-Id", profileId)
            }
            contentType(ContentType.Application.Json)
            setBody(UpdateSettingRequest(value))
        }
    }

    open suspend fun deleteDeviceSetting(key: String): ApiResult<Unit> = safeApiCall {
        client.delete("/api/v1/settings/device/$key")
    }

    open suspend fun getEffectiveSettings(keys: List<String>): ApiResult<EffectiveSettingsResponse> = safeApiCall {
        client.get("/api/v1/settings/effective") {
            url {
                parameters.append("keys", keys.joinToString(","))
            }
        }
    }

    open suspend fun getEffectiveSubtitleAppearance(): ApiResult<EffectiveSubtitleAppearance> = safeApiCall {
        client.get("/api/v1/settings/subtitle_appearance/effective")
    }

    open suspend fun setDeviceSubtitleAppearanceOverride(
        appearance: SubtitleAppearance,
        profileId: String? = null,
    ): ApiResult<Unit> = setDeviceSetting(
        key = PlaybackSettingsKeys.SubtitleAppearance,
        value = appearance.toJsonString(),
        profileId = profileId,
    )

    open suspend fun deleteDeviceSubtitleAppearanceOverride(): ApiResult<Unit> =
        deleteDeviceSetting(PlaybackSettingsKeys.SubtitleAppearance)
}
