package org.prairieserver.prairie.common.player.video

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class VideoPlayerRouteArgsTest {
    @Test
    fun parseResumePositionAcceptsFinitePositiveSeconds() {
        assertEquals(31.5, VideoPlayerRouteArgs.parseResumePosition("31.5"))
    }

    @Test
    fun parseResumePositionRejectsMissingOrInvalidValues() {
        assertNull(VideoPlayerRouteArgs.parseResumePosition(null))
        assertNull(VideoPlayerRouteArgs.parseResumePosition(""))
        assertNull(VideoPlayerRouteArgs.parseResumePosition("-1"))
        assertNull(VideoPlayerRouteArgs.parseResumePosition("NaN"))
        assertNull(VideoPlayerRouteArgs.parseResumePosition("Infinity"))
        assertNull(VideoPlayerRouteArgs.parseResumePosition("abc"))
    }

    @Test
    fun encodeResumePositionUsesStableDecimalText() {
        assertEquals("31.5", VideoPlayerRouteArgs.encodeResumePosition(31.5))
        assertNull(VideoPlayerRouteArgs.encodeResumePosition(null))
        assertNull(VideoPlayerRouteArgs.encodeResumePosition(Double.NaN))
        assertNull(VideoPlayerRouteArgs.encodeResumePosition(-1.0))
    }
}
