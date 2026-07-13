package org.siloserver.silo.common.pip

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SiloPictureInPicturePolicyTest {
    @Test
    fun `mobile PiP is unavailable on Android 7`() {
        assertFalse(
            siloCanEnterPictureInPicture(
                surface = SiloPictureInPictureSurface.Mobile,
                sdkInt = 24,
                deviceSupportsPictureInPicture = true,
                enabled = true,
                videoActive = true,
                isPlaying = true,
            ),
        )
    }

    @Test
    fun `mobile PiP is available from API 26 when playback is active`() {
        assertTrue(
            siloCanEnterPictureInPicture(
                surface = SiloPictureInPictureSurface.Mobile,
                sdkInt = 26,
                deviceSupportsPictureInPicture = true,
                enabled = true,
                videoActive = true,
                isPlaying = true,
            ),
        )
    }

    @Test
    fun `mobile PiP requires opt in device support and active playback`() {
        val baseline = SiloPictureInPictureSurface.Mobile
        assertFalse(siloCanEnterPictureInPicture(baseline, 31, false, true, true, true))
        assertFalse(siloCanEnterPictureInPicture(baseline, 31, true, false, true, true))
        assertFalse(siloCanEnterPictureInPicture(baseline, 31, true, true, false, true))
        assertFalse(siloCanEnterPictureInPicture(baseline, 31, true, true, true, false))
    }

    @Test
    fun `tv PiP is gated to Android 14 TV multitasking devices`() {
        assertFalse(
            siloCanEnterPictureInPicture(
                surface = SiloPictureInPictureSurface.Tv,
                sdkInt = 33,
                deviceSupportsPictureInPicture = true,
                enabled = true,
                videoActive = true,
                isPlaying = true,
            ),
        )
        assertTrue(
            siloCanEnterPictureInPicture(
                surface = SiloPictureInPictureSurface.Tv,
                sdkInt = 34,
                deviceSupportsPictureInPicture = true,
                enabled = true,
                videoActive = true,
                isPlaying = true,
            ),
        )
    }

    @Test
    fun `initially disabled PiP does not touch system parameters`() {
        assertTrue(
            siloShouldUpdatePictureInPictureParams(
                sdkInt = 30,
                deviceSupportsPictureInPicture = true,
                wasEnabled = false,
                enabled = true,
            ),
        )
        assertFalse(
            siloShouldUpdatePictureInPictureParams(
                sdkInt = 30,
                deviceSupportsPictureInPicture = true,
                wasEnabled = false,
                enabled = false,
            ),
        )
        assertTrue(
            siloShouldUpdatePictureInPictureParams(
                sdkInt = 30,
                deviceSupportsPictureInPicture = true,
                wasEnabled = true,
                enabled = false,
            ),
        )
    }
}
