package org.prairieserver.prairie.common.player.route

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RouteCapabilityMatrixTest {

    @Test
    fun `matrix has an entry for every route`() {
        assertTrue(RouteCapabilityMatrix.isExhaustive())
    }

    @Test
    fun `PrairiePlayer route supports audio delay`() {
        val cap = RouteCapabilityMatrix.get(PlaybackRoute.PrairiePlayer)
        assertTrue(cap.audioDelaySupported)
    }

    @Test
    fun `hls route disables HDR toggle (server-baked)`() {
        val cap = RouteCapabilityMatrix.get(PlaybackRoute.Hls)
        assertFalse(cap.hdrToggle)
    }

    @Test
    fun `hls route uses SystemOnly subtitle styling`() {
        val cap = RouteCapabilityMatrix.get(PlaybackRoute.Hls)
        assertEquals(SubtitleStyling.SystemOnly, cap.subtitleStyling)
    }

    @Test
    fun `PrairiePlayer and native direct have identical capability flags`() {
        // The differentiator is codec breadth, not capability flags.
        val a = RouteCapabilityMatrix.get(PlaybackRoute.PrairiePlayer)
        val b = RouteCapabilityMatrix.get(PlaybackRoute.NativeDirect)
        assertEquals(a, b)
    }

    @Test
    fun `displayName is human-readable`() {
        assertEquals("PrairiePlayer", PlaybackRoute.PrairiePlayer.displayName)
        assertEquals("Native Direct", PlaybackRoute.NativeDirect.displayName)
        assertEquals("HLS", PlaybackRoute.Hls.displayName)
    }
}
