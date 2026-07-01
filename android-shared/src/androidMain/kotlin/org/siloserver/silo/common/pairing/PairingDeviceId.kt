package org.siloserver.silo.common.pairing

import android.content.Context
import android.provider.Settings
import java.util.UUID

/**
 * Resolves a stable device id for the pairing TXT record and
 * [PairingMessage.Hello.tvDeviceId]: prefers [Settings.Secure.ANDROID_ID],
 * falling back to a persisted random UUID.
 */
object PairingDeviceId {
    private const val PREFS_NAME = "silo_pairing_identity"
    private const val KEY_DEVICE_ID = "device_id"

    @Suppress("HardwareIds")
    fun stable(context: Context): String {
        val androidId = runCatching {
            Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
        }.getOrNull()
        if (!androidId.isNullOrBlank()) return androidId
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.getString(KEY_DEVICE_ID, null)?.let { return it }
        return UUID.randomUUID().toString().also {
            prefs.edit().putString(KEY_DEVICE_ID, it).apply()
        }
    }
}
