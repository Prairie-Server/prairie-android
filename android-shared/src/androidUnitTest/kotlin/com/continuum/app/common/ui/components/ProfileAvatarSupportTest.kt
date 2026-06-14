package com.continuum.app.common.ui.components

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ProfileAvatarSupportTest {
    @Test
    fun absoluteAvatarUrlsAreReturnedUnchanged() {
        assertEquals(
            "https://cdn.example.test/avatar.webp",
            resolveAvatarUrl("https://silo.example", "https://cdn.example.test/avatar.webp"),
        )
    }

    @Test
    fun relativeAvatarPathsResolveAgainstServerUrl() {
        assertEquals(
            "https://silo.example/api/v1/users/1/avatar.png",
            resolveAvatarUrl("https://silo.example/", "/api/v1/users/1/avatar.png"),
        )
    }

    @Test
    fun nonImageAvatarTextDoesNotBecomeAUrl() {
        assertNull(resolveAvatarUrl("https://silo.example", "JC"))
    }
}
