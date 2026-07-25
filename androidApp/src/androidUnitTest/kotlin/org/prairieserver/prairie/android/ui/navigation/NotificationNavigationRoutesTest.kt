package org.prairieserver.prairie.android.ui.navigation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class NotificationNavigationRoutesTest {
    @Test
    fun notificationRoutesAllowOnlyInboxAndItemDetails() {
        assertEquals(Route.Inbox.route, notificationNavigationRouteOrNull(Route.Inbox.route))
        assertEquals("item/123", notificationNavigationRouteOrNull(" item/123 "))

        assertNull(notificationNavigationRouteOrNull(null))
        assertNull(notificationNavigationRouteOrNull(""))
        assertNull(notificationNavigationRouteOrNull(Route.Requests.route))
        assertNull(notificationNavigationRouteOrNull(Route.MyRequests.route))
        assertNull(notificationNavigationRouteOrNull("player/123"))
        assertNull(notificationNavigationRouteOrNull("watch_together"))
        assertNull(notificationNavigationRouteOrNull("item/"))
        assertNull(notificationNavigationRouteOrNull("item/123/extra"))
        assertNull(notificationNavigationRouteOrNull("item/123?seasonNumber=1"))
    }
}
