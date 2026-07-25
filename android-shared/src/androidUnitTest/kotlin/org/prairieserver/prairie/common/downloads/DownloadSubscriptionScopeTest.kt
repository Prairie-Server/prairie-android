package org.prairieserver.prairie.common.downloads

import kotlin.test.Test
import kotlin.test.assertEquals

class DownloadSubscriptionScopeTest {

    @Test
    fun explicitInputScopeWinsOverActiveScope() {
        assertEquals(
            "srv-1" to "prof-1",
            downloadSubscriptionScope(
                inputServerId = "srv-1",
                inputProfileId = "prof-1",
                activeServerId = "srv-2",
                activeProfileId = "prof-2",
            ),
        )
    }

    @Test
    fun periodicRunWithoutInputUsesActiveScope() {
        assertEquals(
            "srv-2" to "prof-2",
            downloadSubscriptionScope(
                inputServerId = null,
                inputProfileId = null,
                activeServerId = "srv-2",
                activeProfileId = "prof-2",
            ),
        )
    }

    @Test
    fun missingScopesFallBackToDownloadDefaults() {
        assertEquals(
            DownloadEnqueuer.DEFAULT_SERVER_ID to DownloadEnqueuer.DEFAULT_PROFILE_ID,
            downloadSubscriptionScope(
                inputServerId = null,
                inputProfileId = null,
                activeServerId = null,
                activeProfileId = null,
            ),
        )
    }

    @Test
    fun partialInputScopeIsIgnoredAsAWhole() {
        // A one-shot enqueue always sets both keys; a lone value is stale
        // data and must not mix scopes (e.g. input server + active profile).
        assertEquals(
            "srv-2" to "prof-2",
            downloadSubscriptionScope(
                inputServerId = "srv-1",
                inputProfileId = null,
                activeServerId = "srv-2",
                activeProfileId = "prof-2",
            ),
        )
    }
}
