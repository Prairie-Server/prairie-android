package com.continuum.app.repository

import com.continuum.app.model.settings.EffectiveSetting
import com.continuum.app.model.settings.EffectiveSubtitleAppearance
import com.continuum.app.model.settings.SubtitleAppearance
import com.continuum.app.network.ApiResult
import com.continuum.app.network.api.SettingsApi
import com.continuum.app.network.map

class SettingsRepository(
    private val settingsApi: SettingsApi,
) {
    suspend fun listSettings(): ApiResult<Map<String, String>> =
        settingsApi.getSettings().map { response ->
            response.settings.associate { it.key to it.value }
        }

    suspend fun getSetting(key: String): ApiResult<String> =
        settingsApi.getSetting(key).map { it.value }

    suspend fun setSetting(key: String, value: String): ApiResult<Unit> =
        settingsApi.setSetting(key, value)

    suspend fun deleteSetting(key: String): ApiResult<Unit> =
        settingsApi.deleteSetting(key)

    suspend fun getDeviceSetting(key: String): ApiResult<String> =
        settingsApi.getDeviceSetting(key).map { it.value }

    suspend fun setDeviceSetting(key: String, value: String): ApiResult<Unit> =
        settingsApi.setDeviceSetting(key, value)

    suspend fun deleteDeviceSetting(key: String): ApiResult<Unit> =
        settingsApi.deleteDeviceSetting(key)

    suspend fun getEffectiveSettings(keys: List<String>): ApiResult<Map<String, EffectiveSetting>> =
        settingsApi.getEffectiveSettings(keys).map { response ->
            response.settings.associateBy { it.key }
        }

    suspend fun getEffectiveSubtitleAppearance(): ApiResult<EffectiveSubtitleAppearance> =
        settingsApi.getEffectiveSubtitleAppearance()

    suspend fun setDeviceSubtitleAppearanceOverride(
        appearance: SubtitleAppearance,
        profileId: String? = null,
    ): ApiResult<Unit> =
        settingsApi.setDeviceSubtitleAppearanceOverride(appearance, profileId)

    suspend fun deleteDeviceSubtitleAppearanceOverride(): ApiResult<Unit> =
        settingsApi.deleteDeviceSubtitleAppearanceOverride()
}
