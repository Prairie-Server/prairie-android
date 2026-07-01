package org.siloserver.silo.model.feature

import kotlin.test.Test
import kotlin.test.assertFalse

class ClientSurfacePolicyTest {
    @Test
    fun requestsAndWatchTogetherCodeStaysPresentButHiddenFromUserMenus() {
        assertFalse(CLIENT_REQUESTS_SURFACE_ENABLED)
        assertFalse(CLIENT_WATCH_TOGETHER_SURFACE_ENABLED)
    }
}
