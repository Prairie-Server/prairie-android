package org.prairieserver.prairie.android.push

import org.prairieserver.prairie.android.ui.navigation.ExternalRouteScope
import org.prairieserver.prairie.android.ui.navigation.notificationExternalRouteOrNull
import kotlin.test.Test
import kotlin.test.assertEquals

class PushNotificationDeliveryIntegrationTest {
    @Test
    fun `posted notification retains its identity through route delivery`() {
        // Model the extras contract shared by PushNotificationPresenter and
        // MainActivity without starting Android, the Application, or its Koin
        // graph.
        val postedExtras = mapOf(
            PushNotificationPresenter.EXTRA_NAV_ROUTE to "item/episode-1",
            PushNotificationPresenter.EXTRA_SERVER_ID to "server-a",
            PushNotificationPresenter.EXTRA_PROFILE_ID to "kids",
        )

        val (route, scope) = requireNotNull(
            notificationExternalRouteOrNull(
                route = postedExtras[PushNotificationPresenter.EXTRA_NAV_ROUTE],
                serverId = postedExtras[PushNotificationPresenter.EXTRA_SERVER_ID],
                profileId = postedExtras[PushNotificationPresenter.EXTRA_PROFILE_ID],
            ),
        )

        assertEquals("item/episode-1", route)
        assertEquals(
            ExternalRouteScope.Identity(
                serverId = "server-a",
                profileId = "kids",
                // Process-local generations cannot be persisted in PendingIntents.
                identityGeneration = null,
            ),
            scope,
        )
    }
}
