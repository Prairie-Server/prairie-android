package org.prairieserver.prairie.common.network

import android.content.Context
import android.content.pm.PackageInfo
import android.os.Build
import org.prairieserver.prairie.network.PrairieDeviceMetadata
import org.prairieserver.prairie.network.DeviceMetadataProvider
import java.util.UUID

class AndroidDeviceMetadataProvider(
    private val context: Context,
    private val platform: String,
) : DeviceMetadataProvider {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val cachedClientName: String by lazy { clientNameFor(platform) }
    private val cachedClientVersion: String? by lazy { appVersionName() }

    override suspend fun current(): PrairieDeviceMetadata {
        val deviceId = prefs.getString(KEY_DEVICE_ID, null) ?: UUID.randomUUID().toString().also { generated ->
            prefs.edit().putString(KEY_DEVICE_ID, generated).apply()
        }
        val model = listOfNotNull(Build.MANUFACTURER?.trim(), Build.MODEL?.trim())
            .distinct()
            .filter { it.isNotBlank() }
            .joinToString(" ")
            .ifBlank { "Android Device" }
        return PrairieDeviceMetadata(
            id = deviceId,
            name = model,
            platform = platform,
            clientName = cachedClientName,
            clientVersion = cachedClientVersion,
        )
    }

    private fun clientNameFor(platform: String): String =
        when (platform) {
            "android-tv" -> "Prairie Android TV"
            "android" -> "Prairie Android"
            else -> "Prairie Android"
        }

    @Suppress("DEPRECATION")
    private fun appVersionName(): String? =
        runCatching<PackageInfo> {
            context.packageManager.getPackageInfo(context.packageName, 0)
        }.getOrNull()
            ?.versionName
            ?.trim()
            ?.takeIf { it.isNotBlank() }

    private companion object {
        const val PREFS_NAME = "prairie_device_metadata"
        const val KEY_DEVICE_ID = "device_id"
    }
}
