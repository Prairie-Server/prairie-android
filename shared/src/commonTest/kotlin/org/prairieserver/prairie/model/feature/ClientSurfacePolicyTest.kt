package org.prairieserver.prairie.model.feature

import kotlin.test.Test
import kotlin.test.assertTrue

class ClientSurfacePolicyTest {
    @Test
    fun watchTogetherIsExposedInTheDetailOverflows() {
        assertTrue(CLIENT_WATCH_TOGETHER_SURFACE_ENABLED)
    }
}
