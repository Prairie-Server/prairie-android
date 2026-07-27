package org.siloserver.silo.common.network

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import java.security.MessageDigest
import kotlinx.coroutines.flow.first
import org.siloserver.silo.network.CleartextOriginConsent
import org.siloserver.silo.network.canonicalHttpOrigin

interface CleartextConsentStore : CleartextOriginConsent {
    suspend fun approve(origin: String)
}

class DataStoreCleartextConsentStore(
    private val dataStore: DataStore<Preferences>,
) : CleartextConsentStore {
    constructor(context: Context) : this(
        PreferenceDataStoreFactory.create {
            context.preferencesDataStoreFile(DATA_STORE_NAME)
        },
    )

    override suspend fun isApproved(origin: String): Boolean {
        val normalized = cleartextOrigin(origin) ?: return false
        return originDigest(normalized) in dataStore.data.first()[APPROVED_ORIGIN_DIGESTS].orEmpty()
    }

    override suspend fun approve(origin: String) {
        val normalized = requireNotNull(cleartextOrigin(origin)) {
            "Cleartext approval requires a valid HTTP origin"
        }
        val digest = originDigest(normalized)
        dataStore.edit { preferences ->
            preferences[APPROVED_ORIGIN_DIGESTS] =
                preferences[APPROVED_ORIGIN_DIGESTS].orEmpty() + digest
        }
    }

    private companion object {
        private const val DATA_STORE_NAME = "silo_cleartext_consent"
        private val APPROVED_ORIGIN_DIGESTS = stringSetPreferencesKey("approved_origin_sha256")

        private fun originDigest(origin: String): String =
            MessageDigest.getInstance("SHA-256")
                .digest(origin.encodeToByteArray())
                .joinToString("") { byte -> "%02x".format(byte) }
    }
}

fun cleartextOrigin(url: String): String? = runCatching {
    canonicalHttpOrigin(url)?.takeIf { it.startsWith("http://") }
}.getOrNull()
