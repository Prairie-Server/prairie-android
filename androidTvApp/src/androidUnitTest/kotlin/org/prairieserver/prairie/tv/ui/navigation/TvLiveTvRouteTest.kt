package org.prairieserver.prairie.tv.ui.navigation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Live TV is Prairie-only; the shell's profile dropdown and channel list rely
 * on these routes. Plain JVM: the route encoder is java.net based, so no
 * Robolectric (android.net.Uri would be stubbed and yield "null").
 */
class TvLiveTvRouteTest {

    @Test
    fun liveTvRouteIsStable() {
        assertEquals("main/livetv", TvMainRoute.LiveTv.route)
    }

    @Test
    fun playerRouteEncodesIdAndName() {
        assertEquals("main/livetv/player/ch-1", TvMainRoute.LiveTvPlayer("ch-1").route)
        assertEquals(
            "main/livetv/player/a%2Fb?name=News%20%26%20Weather",
            TvMainRoute.LiveTvPlayer("a/b", "News & Weather").route,
        )
    }

    @Test
    fun playerRoutePatternDeclaresBothArguments() {
        val pattern = TvMainRoute.LiveTvPlayer.ROUTE
        assertTrue(pattern.startsWith("main/livetv/player/"))
        assertTrue(pattern.contains("{${TvMainRoute.LiveTvPlayer.ARG_CHANNEL_ID}}"))
        assertTrue(pattern.contains("{${TvMainRoute.LiveTvPlayer.ARG_NAME}}"))
    }
}
