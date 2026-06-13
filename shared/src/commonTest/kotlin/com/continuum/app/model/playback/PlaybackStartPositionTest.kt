package com.continuum.app.model.playback

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PlaybackStartPositionTest {
    @Test
    fun `session resume position is not erased by zero detail progress`() {
        assertEquals(
            318.5,
            resolvePlaybackStartPosition(
                overridePosition = null,
                sessionPosition = 318.5,
                detailPosition = 0.0,
            ),
        )
    }

    @Test
    fun `explicit override can intentionally start at zero`() {
        assertEquals(
            0.0,
            resolvePlaybackStartPosition(
                overridePosition = 0.0,
                sessionPosition = 318.5,
                detailPosition = 200.0,
            ),
        )
    }

    @Test
    fun `detail progress is a fallback when session position is empty`() {
        assertEquals(
            204.0,
            resolvePlaybackStartPosition(
                overridePosition = null,
                sessionPosition = 0.0,
                detailPosition = 204.0,
            ),
        )
    }

    @Test
    fun `start request omits empty detail progress`() {
        assertNull(resolvePlaybackStartRequestPosition(overridePosition = null, detailPosition = 0.0))
        assertEquals(204.0, resolvePlaybackStartRequestPosition(overridePosition = null, detailPosition = 204.0))
        assertEquals(0.0, resolvePlaybackStartRequestPosition(overridePosition = 0.0, detailPosition = 204.0))
    }
}
