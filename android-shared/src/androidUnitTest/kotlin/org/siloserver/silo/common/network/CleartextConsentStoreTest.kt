package org.siloserver.silo.common.network

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import java.nio.file.Files
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CleartextConsentStoreTest {

    @Test
    fun approvalPersistsOnlyNormalizedOriginDigest() = runTest {
        val file = Files.createTempDirectory("cleartext-consent").resolve("preferences.preferences_pb").toFile()
        val dataStore = PreferenceDataStoreFactory.create(scope = backgroundScope) { file }
        val store = DataStoreCleartextConsentStore(dataStore)
        val supplied = "HTTP://User:secret@SILO.LAN:80/private?token=credential#fragment"

        store.approve(supplied)

        assertTrue(store.isApproved("http://silo.lan/another-path"))
        val persisted = dataStore.data.first().asMap()
        assertEquals(1, persisted.size)
        val digests = persisted.values.single() as Set<*>
        assertEquals(1, digests.size)
        assertTrue((digests.single() as String).matches(Regex("[0-9a-f]{64}")))
        val serialized = persisted.toString()
        assertFalse(serialized.contains("silo.lan", ignoreCase = true))
        assertFalse(serialized.contains("secret", ignoreCase = true))
        assertFalse(serialized.contains("token", ignoreCase = true))
        assertFalse(serialized.contains("credential", ignoreCase = true))
    }

    @Test
    fun approvalsAreSpecificToNormalizedSchemeHostAndPort() = runTest {
        val file = Files.createTempDirectory("cleartext-origin").resolve("preferences.preferences_pb").toFile()
        val store = DataStoreCleartextConsentStore(
            PreferenceDataStoreFactory.create(scope = backgroundScope) { file },
        )

        store.approve("http://SILO.LAN:8090/path")

        assertTrue(store.isApproved("http://silo.lan:8090/other"))
        assertFalse(store.isApproved("http://silo.lan:8091"))
        assertFalse(store.isApproved("http://other.lan:8090"))
        assertFalse(store.isApproved("https://silo.lan:8090"))
    }

    @Test
    fun normalizedOriginDropsCredentialsPathQueryFragmentAndDefaultPort() {
        assertEquals(
            "http://silo.lan",
            cleartextOrigin("HTTP://user:password@SILO.LAN:80/path?q=token#fragment"),
        )
        assertEquals("http://silo.lan:8090", cleartextOrigin("http://SILO.LAN:8090/path"))
    }
}
