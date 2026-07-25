package org.prairieserver.prairie.common.settings

import android.content.Context

open class AndroidServerSettingsCache(
    context: Context,
) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    open fun getString(serverUrl: String, key: String, defaultValue: String = ""): String =
        prefs.getString(storageKey(serverUrl, key), defaultValue) ?: defaultValue

    open fun putString(serverUrl: String, key: String, value: String) {
        prefs.edit().putString(storageKey(serverUrl, key), value).apply()
    }

    open fun getBoolean(serverUrl: String, key: String, defaultValue: Boolean): Boolean =
        prefs.getString(storageKey(serverUrl, key), null)?.toBooleanStrictOrNull() ?: defaultValue

    open fun putBoolean(serverUrl: String, key: String, value: Boolean) {
        putString(serverUrl, key, value.toString())
    }

    fun migrationKey(serverUrl: String, scope: String): String =
        storageKey(serverUrl, "migration.$scope")

    open fun isMigrationComplete(serverUrl: String, scope: String): Boolean =
        prefs.getBoolean(migrationKey(serverUrl, scope), false)

    open fun markMigrationComplete(serverUrl: String, scope: String) {
        prefs.edit().putBoolean(migrationKey(serverUrl, scope), true).apply()
    }

    private fun storageKey(serverUrl: String, key: String): String =
        "${serverUrl.trimEnd('/')}:$key"

    private companion object {
        const val PREFS_NAME = "prairie_server_settings_cache"
    }
}
