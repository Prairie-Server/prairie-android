package org.prairieserver.prairie.common.pip

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PrairiePictureInPicturePolicyTest {
    @Test
    fun `mobile PiP is unavailable on Android 7`() {
        assertFalse(
            prairieCanEnterPictureInPicture(
                surface = PrairiePictureInPictureSurface.Mobile,
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
            prairieCanEnterPictureInPicture(
                surface = PrairiePictureInPictureSurface.Mobile,
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
        val baseline = PrairiePictureInPictureSurface.Mobile
        assertFalse(prairieCanEnterPictureInPicture(baseline, 31, false, true, true, true))
        assertFalse(prairieCanEnterPictureInPicture(baseline, 31, true, false, true, true))
        assertFalse(prairieCanEnterPictureInPicture(baseline, 31, true, true, false, true))
        assertFalse(prairieCanEnterPictureInPicture(baseline, 31, true, true, true, false))
    }

    @Test
    fun `tv PiP is gated to Android 14 TV multitasking devices`() {
        assertFalse(
            prairieCanEnterPictureInPicture(
                surface = PrairiePictureInPictureSurface.Tv,
                sdkInt = 33,
                deviceSupportsPictureInPicture = true,
                enabled = true,
                videoActive = true,
                isPlaying = true,
            ),
        )
        assertTrue(
            prairieCanEnterPictureInPicture(
                surface = PrairiePictureInPictureSurface.Tv,
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
            prairieShouldUpdatePictureInPictureParams(
                sdkInt = 30,
                deviceSupportsPictureInPicture = true,
                wasEnabled = false,
                enabled = true,
            ),
        )
        assertFalse(
            prairieShouldUpdatePictureInPictureParams(
                sdkInt = 30,
                deviceSupportsPictureInPicture = true,
                wasEnabled = false,
                enabled = false,
            ),
        )
        assertTrue(
            prairieShouldUpdatePictureInPictureParams(
                sdkInt = 30,
                deviceSupportsPictureInPicture = true,
                wasEnabled = true,
                enabled = false,
            ),
        )
    }
}
