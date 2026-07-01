package org.siloserver.silo.common.network

import android.content.Context
import android.os.Build
import org.siloserver.silo.network.SiloDeviceMetadata
import org.siloserver.silo.network.DeviceMetadataProvider
import java.util.UUID

class AndroidDeviceMetadataProvider(
    private val context: Context,
    private val platform: String,
) : DeviceMetadataProvider {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override suspend fun current(): SiloDeviceMetadata {
        val deviceId = prefs.getString(KEY_DEVICE_ID, null) ?: UUID.randomUUID().toString().also { generated ->
            prefs.edit().putString(KEY_DEVICE_ID, generated).apply()
        }
        val model = listOfNotNull(Build.MANUFACTURER?.trim(), Build.MODEL?.trim())
            .distinct()
            .filter { it.isNotBlank() }
            .joinToString(" ")
            .ifBlank { "Android Device" }
        return SiloDeviceMetadata(
            id = deviceId,
            name = model,
            platform = platform,
        )
    }

    private companion object {
        const val PREFS_NAME = "silo_device_metadata"
        const val KEY_DEVICE_ID = "device_id"
    }
}
