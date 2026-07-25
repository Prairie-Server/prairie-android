package org.prairieserver.prairie.common.player.backend

import org.junit.Assert.assertEquals
import org.junit.Test

class VideoBackendCapabilitiesBrandingTest {
    @Test
    fun `Media3 backend is presented as PrairiePlayer`() {
        val capabilities = VideoBackendCapabilities.media3()

        assertEquals("PrairiePlayer", capabilities.displayName)
        assertEquals("PrairiePlayer", capabilities.route.displayName)
    }
}
