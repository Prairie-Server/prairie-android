package org.siloserver.silo.network

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Shared encrypted SharedPreferences instance used by both [AndroidServerRegistry]
 * and [EncryptedTokenManagerImpl]. Wired as a Koin singleton so both classes
 * see the same handle — opening the file twice on cold start would mean two
 * separate Keystore lookups + decryption passes for the same content.
 */
const val SECURE_PREFS_NAME = "silo_secure_tokens"

fun createSecureSharedPrefs(context: Context): SharedPreferences {
    val appContext = context.applicationContext
    val masterKey = MasterKey.Builder(appContext)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()
    return EncryptedSharedPreferences.create(
        appContext,
        SECURE_PREFS_NAME,
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )
}
