package org.prairieserver.prairie.android.ui.navigation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * A notification route is only honoured if it says whose it is.
 *
 * Missing extras used to produce `Identity(null, null)`, which matches every
 * identity — so the route ran against whoever happened to be signed in. Two
 * ways that arrives: a notification posted by a build from before the extras
 * existed, and an explicit Intent crafted against the exported Activity.
 */
class NotificationExternalRouteTest {

    @Test
    fun `a fully attributed notification is accepted`() {
        assertEquals(
            "item/abc" to ExternalRouteScope.Identity(serverId = "server-a", profileId = "kids"),
            notificationExternalRouteOrNull(
                route = "item/abc",
                serverId = "server-a",
                profileId = "kids",
            ),
        )
    }

    @Test
    fun `a notification with no identity is rejected`() {
        assertNull(
            notificationExternalRouteOrNull(route = "item/abc", serverId = null, profileId = null),
        )
    }

    /** Half an identity is worse than none — the missing half is a wildcard. */
    @Test
    fun `a half attributed notification is rejected`() {
        assertNull(
            notificationExternalRouteOrNull(
                route = "item/abc",
                serverId = "server-a",
                profileId = null,
            ),
        )
        assertNull(
            notificationExternalRouteOrNull(
                route = "item/abc",
                serverId = null,
                profileId = "kids",
            ),
        )
    }

    @Test
    fun `blank is not an identity`() {
        assertNull(
            notificationExternalRouteOrNull(route = "item/abc", serverId = "  ", profileId = "kids"),
        )
        assertNull(
            notificationExternalRouteOrNull(route = "  ", serverId = "server-a", profileId = "kids"),
        )
    }

    @Test
    fun `no route is nothing to deliver`() {
        assertNull(
            notificationExternalRouteOrNull(route = null, serverId = "server-a", profileId = "kids"),
        )
    }
}
