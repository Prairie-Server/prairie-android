package org.prairieserver.prairie.android.cast

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * The last TV the user deliberately controlled, persisted so a later app
 * launch can silently re-attach while that TV is still playing (mirrors
 * prairie-apple's `prairiecontrol.lastTarget` UserDefaults entry). Host/port are
 * only a hint — auto-resume re-discovers the TV over mDNS before connecting.
 */
@Serializable
data class PrairieCastPersistedTarget(
    val deviceId: String,
    val name: String,
    val serverId: String? = null,
)

interface PrairieCastLastTargetStore {
    fun save(target: PrairieCastPersistedTarget)

    fun load(): PrairieCastPersistedTarget?

    fun clear()
}

class SharedPrefsPrairieCastLastTargetStore(context: Context) : PrairieCastLastTargetStore {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }

    override fun save(target: PrairieCastPersistedTarget) {
        prefs.edit().putString(KEY, json.encodeToString(PrairieCastPersistedTarget.serializer(), target)).apply()
    }

    override fun load(): PrairieCastPersistedTarget? =
        prefs.getString(KEY, null)?.let { raw ->
            runCatching { json.decodeFromString(PrairieCastPersistedTarget.serializer(), raw) }.getOrNull()
        }

    override fun clear() {
        prefs.edit().remove(KEY).apply()
    }

    private companion object {
        const val PREFS_NAME = "prairiecast"
        const val KEY = "last_target"
    }
}
