package org.prairieserver.prairie.android.cast

import kotlin.test.Test
import kotlin.test.assertEquals

class PrairieCastMediaSessionStarterTest {
    @Test
    fun `media uses an ordinary service start only while app is foregrounded`() {
        val media = RemoteServiceState(hasMedia = true)

        assertEquals(
            RemoteMediaServiceAction.Start,
            resolveRemoteMediaServiceAction(media, appForeground = true),
        )
        assertEquals(
            RemoteMediaServiceAction.None,
            resolveRemoteMediaServiceAction(media, appForeground = false),
        )
    }

    @Test
    fun `cleared media stops the service even while app is backgrounded`() {
        val empty = RemoteServiceState(hasMedia = false)

        assertEquals(
            RemoteMediaServiceAction.Stop,
            resolveRemoteMediaServiceAction(empty, appForeground = false),
        )
    }
}
