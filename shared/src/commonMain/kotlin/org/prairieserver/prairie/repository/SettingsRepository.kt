package org.prairieserver.prairie.repository

import org.prairieserver.prairie.model.settings.EffectiveSetting
import org.prairieserver.prairie.model.settings.EffectiveSubtitleAppearance
import org.prairieserver.prairie.model.settings.SubtitleAppearance
import org.prairieserver.prairie.network.ApiResult
import org.prairieserver.prairie.network.api.OverlayConfigResponse
import org.prairieserver.prairie.network.api.SettingsApi
import org.prairieserver.prairie.network.map

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

    suspend fun overlayConfig(): ApiResult<OverlayConfigResponse> =
        settingsApi.overlayConfig()

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
