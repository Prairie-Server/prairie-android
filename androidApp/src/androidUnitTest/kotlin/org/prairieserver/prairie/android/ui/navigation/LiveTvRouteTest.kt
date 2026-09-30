package org.prairieserver.prairie.android.ui.navigation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Live TV is Prairie-only and has been dropped by an upstream sync once. These
 * cover the route contract the profile-menu entry and the channel list rely
 * on; scripts/check-prairie-invariants.sh covers the menu/NavHost wiring.
 */
class LiveTvRouteTest {

    @Test
    fun liveTvRouteIsStable() {
        assertEquals("livetv", Route.LiveTv.route)
    }

    @Test
    fun playerRouteOmitsBlankName() {
        assertEquals("livetv/player/ch-1", Route.LiveTvPlayer("ch-1").route)
        assertEquals("livetv/player/ch-1", Route.LiveTvPlayer("ch-1", "  ").route)
    }

    @Test
    fun playerRouteEncodesIdAndNameAsSingleArguments() {
        val route = Route.LiveTvPlayer(channelId = "a/b?c", channelName = "News & Weather 7").route
        assertEquals("livetv/player/a%2Fb%3Fc?name=News%20%26%20Weather%207", route)
    }

    @Test
    fun playerRoutePatternDeclaresBothArguments() {
        val pattern = Route.LiveTvPlayer.ROUTE
        assertTrue(pattern.startsWith("livetv/player/"))
        assertTrue(pattern.contains("{${Route.LiveTvPlayer.ARG_CHANNEL_ID}}"))
        assertTrue(pattern.contains("{${Route.LiveTvPlayer.ARG_NAME}}"))
    }
}
