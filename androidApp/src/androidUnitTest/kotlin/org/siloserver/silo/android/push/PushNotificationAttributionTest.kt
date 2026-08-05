package org.siloserver.silo.android.push

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * A notification must be attributed to its issuer completely or not at all — a
 * missing component is a wildcard at delivery, so a half-attributed
 * notification can act under an identity that never generated it.
 */
class PushNotificationAttributionTest {

    @Test
    fun `a stable identity with a fetched row attributes`() {
        assertEquals(
            PushNotificationAttribution(serverId = "server-a", profileId = "kids"),
            pushNotificationAttribution(
                rowProfileId = "kids",
                serverIdBefore = "server-a",
                identityGenerationBefore = 7L,
                serverIdAfter = "server-a",
                identityGenerationAfter = 7L,
            ),
        )
    }

    /**
     * The original misattribution: a push issued by A arriving while B is active
     * misses its lookup, and falling back to the active profile stamped it as
     * B's and navigated into B's library.
     */
    @Test
    fun `a lookup miss does not fall back to the active profile`() {
        assertNull(
            pushNotificationAttribution(
                rowProfileId = null,
                serverIdBefore = "server-b",
                identityGenerationBefore = 7L,
                serverIdAfter = "server-b",
                identityGenerationAfter = 7L,
            ),
        )
    }

    @Test
    fun `a server switch during the fetch abandons attribution`() {
        assertNull(
            pushNotificationAttribution(
                rowProfileId = "kids",
                serverIdBefore = "server-a",
                identityGenerationBefore = 7L,
                serverIdAfter = "server-b",
                identityGenerationAfter = 8L,
            ),
        )
    }

    /**
     * A→B→A leaves the server id looking untouched, which is why generations
     * are compared rather than ids alone.
     */
    @Test
    fun `an A to B to A round trip is detected`() {
        assertNull(
            pushNotificationAttribution(
                rowProfileId = "kids",
                serverIdBefore = "server-a",
                identityGenerationBefore = 7L,
                serverIdAfter = "server-a",
                identityGenerationAfter = 9L,
            ),
        )
    }

    /**
     * Persistent credential writes move `credentialEpoch` without changing who
     * the user is. This pins that the predicate ignores the epoch entirely —
     * only the server and the identity generation decide.
     */
    @Test
    fun `credential churn does not abandon attribution`() {
        assertEquals(
            PushNotificationAttribution(serverId = "server-a", profileId = "kids"),
            pushNotificationAttribution(
                rowProfileId = "kids",
                serverIdBefore = "server-a",
                identityGenerationBefore = 7L,
                serverIdAfter = "server-a",
                identityGenerationAfter = 7L,
            ),
        )
    }

    @Test
    fun `no identity at all does not attribute`() {
        assertNull(
            pushNotificationAttribution(
                rowProfileId = "kids",
                serverIdBefore = null,
                identityGenerationBefore = null,
                serverIdAfter = null,
                identityGenerationAfter = null,
            ),
        )
    }

    @Test
    fun `a blank profile is not an identity`() {
        assertNull(
            pushNotificationAttribution(
                rowProfileId = "   ",
                serverIdBefore = "server-a",
                identityGenerationBefore = 7L,
                serverIdAfter = "server-a",
                identityGenerationAfter = 7L,
            ),
        )
    }
}
