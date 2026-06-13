package com.continuum.app.common.player

import com.continuum.app.model.playback.PlayMethod
import kotlin.test.Test
import kotlin.test.assertEquals

class VideoPlayerMediaSpecTest {
    @Test
    fun startPositionMsConvertsSecondsToMilliseconds() {
        val spec = baseSpec(startPositionSeconds = 31.427)

        assertEquals(31_427L, spec.startPositionMs)
    }

    @Test
    fun startPositionMsClampsNegativeValuesToZero() {
        val spec = baseSpec(startPositionSeconds = -42.0)

        assertEquals(0L, spec.startPositionMs)
    }

    @Test
    fun startPositionMsClampsInvalidValuesToZero() {
        assertEquals(0L, baseSpec(startPositionSeconds = Double.NaN).startPositionMs)
        assertEquals(0L, baseSpec(startPositionSeconds = Double.POSITIVE_INFINITY).startPositionMs)
    }

    private fun baseSpec(startPositionSeconds: Double) = VideoPlayerMediaSpec(
        streamUrl = "https://lib.strm.cafe/api/stream/movie",
        playMethod = PlayMethod.DIRECT,
        serverUrl = "https://lib.strm.cafe",
        title = "Michael",
        subtitle = "Movie",
        artworkUrl = "https://lib.strm.cafe/poster.jpg",
        startPositionSeconds = startPositionSeconds,
    )
}
