package org.prairieserver.prairie.common.network

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.prairieserver.prairie.network.AndroidServerRegistry
import org.prairieserver.prairie.network.EncryptedTokenManagerImpl
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Upgrade-persistence: legacy unprefixed token keys written before multi-server
 * support must migrate into a registry entry + per-server scoped keys so an
 * app upgrade does not wipe the signed-in session.
 */
@RunWith(RobolectricTestRunner::class)
class LegacySecureTokenMigrationTest {

    @Test
    fun legacyUnprefixedTokensMigrateIntoRegistryScopedKeys() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = context.getSharedPreferences("legacy-token-migration-test", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()

        // Simulate a pre-multi-server install: unprefixed EncryptedTokenManagerImpl keys.
        prefs.edit()
            .putString("access_token", "legacy-access")
            .putString("refresh_token", "legacy-refresh")
            .putLong("token_expiry_epoch_ms", 1_700_000_000_000L)
            .putString("profile_id", "profile-living-room")
            .putString("profile_token", "legacy-profile-token")
            .putString("server_url", "https://Prairie.Example:8090/media/")
            .commit()

        val registry = AndroidServerRegistry(prefs)
        val normalized = AndroidServerRegistry.normalizeUrl("https://Prairie.Example:8090/media/")
        val serverId = AndroidServerRegistry.idFor(normalized)

        assertEquals(serverId, registry.activeServerId.value)
        assertEquals(normalized, registry.activeEntry.value?.url)
        assertEquals("profile-living-room", registry.activeEntry.value?.profileId)
        assertTrue(prefs.getBoolean(AndroidServerRegistry.KEY_MIGRATED, false))

        // Tokens moved under "<serverId>.<baseKey>" and legacy unprefixed keys cleared.
        assertEquals(
            "legacy-access",
            prefs.getString(AndroidServerRegistry.serverScopedKey(serverId, "access_token"), null),
        )
        assertEquals(
            "legacy-refresh",
            prefs.getString(AndroidServerRegistry.serverScopedKey(serverId, "refresh_token"), null),
        )
        assertEquals(
            1_700_000_000_000L,
            prefs.getLong(AndroidServerRegistry.serverScopedKey(serverId, "token_expiry_epoch_ms"), -1L),
        )
        assertEquals(
            "profile-living-room",
            prefs.getString(AndroidServerRegistry.serverScopedKey(serverId, "profile_id"), null),
        )
        assertEquals(
            "legacy-profile-token",
            prefs.getString(AndroidServerRegistry.serverScopedKey(serverId, "profile_token"), null),
        )
        assertNull(prefs.getString("access_token", null))
        assertNull(prefs.getString("refresh_token", null))
        assertNull(prefs.getString("profile_id", null))
        assertNull(prefs.getString("profile_token", null))
        assertNull(prefs.getString("server_url", null))
        assertFalse(prefs.contains("token_expiry_epoch_ms"))

        // EncryptedTokenManagerImpl reads the migrated scoped slot for the active server.
        val tokens = EncryptedTokenManagerImpl(prefs, registry)
        assertEquals("legacy-access", tokens.getAccessToken())
        assertEquals("legacy-refresh", tokens.getRefreshToken())
        assertEquals("profile-living-room", tokens.getProfileId())
        assertEquals("legacy-profile-token", tokens.getProfileToken())
        assertEquals(normalized, tokens.getServerUrl())

        // Second construction is a no-op (migration sentinel already set).
        val again = AndroidServerRegistry(prefs)
        assertEquals(serverId, again.activeServerId.value)
        assertNotNull(prefs.getString(AndroidServerRegistry.KEY_REGISTRY_STATE, null))
    }

    @Test
    fun orphanLegacyTokensWithoutServerUrlAreClearedWhenMarkingMigrated() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = context.getSharedPreferences("legacy-token-orphan-test", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()

        // Tokens exist but server_url is missing — previously stamped migrated
        // and left secrets on disk while EncryptedTokenManagerImpl only reads scoped keys.
        // Also seed non-secret legacy keys that must still be wiped.
        prefs.edit()
            .putString("access_token", "orphan-access")
            .putString("refresh_token", "orphan-refresh")
            .putString("profile_token", "orphan-profile")
            .putString("profile_id", "orphan-profile-id")
            .putLong("token_expiry_epoch_ms", 1_700_000_000_000L)
            .commit()

        val registry = AndroidServerRegistry(prefs)
        assertTrue(registry.entries.value.isEmpty())
        assertTrue(prefs.getBoolean(AndroidServerRegistry.KEY_MIGRATED, false))
        assertNull(prefs.getString("access_token", null))
        assertNull(prefs.getString("refresh_token", null))
        assertNull(prefs.getString("profile_token", null))
        assertNull(prefs.getString("profile_id", null))
        assertFalse(prefs.contains("token_expiry_epoch_ms"))
    }
}
