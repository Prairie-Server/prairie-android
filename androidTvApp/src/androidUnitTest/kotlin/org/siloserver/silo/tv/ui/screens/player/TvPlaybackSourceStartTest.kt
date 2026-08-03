package org.siloserver.silo.tv.ui.screens.player

import kotlin.test.Test
import kotlin.test.assertEquals

class TvPlaybackSourceStartTest {
    @Test
    fun adoptedSourceStartUsesTheRewoundServerRequest() {
        assertEquals(
            593.0,
            resolveTvSourceStartPosition(
                startRequestPosition = 593.0,
                serverSourceStartPosition = 600.0,
                playerStartPosition = 0.0,
            ),
        )
    }

    @Test
    fun explicitStartOverKeepsZeroSourceStart() {
        assertEquals(
            0.0,
            resolveTvSourceStartPosition(
                startRequestPosition = 0.0,
                serverSourceStartPosition = 600.0,
                playerStartPosition = 0.0,
            ),
        )
    }

    @Test
    fun serverSourceAnchorWinsWhenNoPositionWasRequested() {
        assertEquals(
            3_005.0,
            resolveTvSourceStartPosition(
                startRequestPosition = null,
                serverSourceStartPosition = 3_005.0,
                playerStartPosition = 5.0,
            ),
        )
    }
}
