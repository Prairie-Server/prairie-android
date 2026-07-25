package org.prairieserver.prairie.common.ui.components

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ProfileAvatarSupportTest {
    private val source = java.io.File(
        "src/androidMain/kotlin/org/prairieserver/prairie/common/ui/components/ProfileAvatarSupport.kt",
    ).readText()

    @Test
    fun absoluteAvatarUrlsAreReturnedUnchanged() {
        assertEquals(
            "https://cdn.example.test/avatar.webp",
            resolveAvatarUrl("https://prairie.example", "https://cdn.example.test/avatar.webp"),
        )
    }

    @Test
    fun relativeAvatarPathsResolveAgainstServerUrl() {
        assertEquals(
            "https://prairie.example/api/v1/users/1/avatar.png",
            resolveAvatarUrl("https://prairie.example/", "/api/v1/users/1/avatar.png"),
        )
    }

    @Test
    fun nonImageAvatarTextDoesNotBecomeAUrl() {
        assertNull(resolveAvatarUrl("https://prairie.example", "JC"))
    }

    @Test
    fun rememberProfileServerUrlUsesServerRegistryInsteadOfLegacyPrefs() {
        assertTrue(
            source.contains("ServerRegistry"),
            "Avatar helpers should use the active server registry entry",
        )
        assertFalse(
            source.contains("prairie_auth"),
            "Avatar helpers must not depend on the legacy plaintext auth preferences",
        )
    }
}
