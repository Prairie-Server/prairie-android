package com.continuum.app.common.player.backend

import com.continuum.app.model.playback.PlayMethod
import kotlin.test.Test
import kotlin.test.assertEquals

class VideoPlaybackBackendSelectorTest {
    @Test
    fun explicitMedia3PreferenceWins() {
        val request = VideoPlaybackBackendRequest(
            preference = VideoPlaybackBackendPreference.Media3,
            hasHardContainer = true,
            hasStyledSubtitles = true,
        )

        assertEquals(VideoPlaybackBackendKind.Media3, VideoPlaybackBackendSelector.select(request))
    }

    @Test
    fun explicitMpvPreferenceWins() {
        val request = VideoPlaybackBackendRequest(preference = VideoPlaybackBackendPreference.Mpv)

        assertEquals(VideoPlaybackBackendKind.Mpv, VideoPlaybackBackendSelector.select(request))
    }

    @Test
    fun autoUsesMedia3ForTranscode() {
        val request = VideoPlaybackBackendRequest(playMethod = PlayMethod.TRANSCODE)

        assertEquals(VideoPlaybackBackendKind.Media3, VideoPlaybackBackendSelector.select(request))
    }

    @Test
    fun autoUsesMpvForHardContainersOrStyledSubtitles() {
        assertEquals(
            VideoPlaybackBackendKind.Mpv,
            VideoPlaybackBackendSelector.select(VideoPlaybackBackendRequest(hasHardContainer = true)),
        )
        assertEquals(
            VideoPlaybackBackendKind.Mpv,
            VideoPlaybackBackendSelector.select(VideoPlaybackBackendRequest(hasStyledSubtitles = true)),
        )
    }

    @Test
    fun autoUsesMedia3ForKnownVideoFormFactors() {
        assertEquals(
            VideoPlaybackBackendKind.Media3,
            VideoPlaybackBackendSelector.select(
                VideoPlaybackBackendRequest(formFactor = VideoPlaybackFormFactor.Mobile),
            ),
        )
        assertEquals(
            VideoPlaybackBackendKind.Media3,
            VideoPlaybackBackendSelector.select(
                VideoPlaybackBackendRequest(formFactor = VideoPlaybackFormFactor.Tv),
            ),
        )
    }

    @Test
    fun autoKeepsMedia3ForUnknownFormFactor() {
        assertEquals(
            VideoPlaybackBackendKind.Media3,
            VideoPlaybackBackendSelector.select(VideoPlaybackBackendRequest()),
        )
    }

    @Test
    fun autoForcesMedia3WhenCasting() {
        val request = VideoPlaybackBackendRequest(isCasting = true, hasHardContainer = true)
        assertEquals(VideoPlaybackBackendKind.Media3, VideoPlaybackBackendSelector.select(request))
    }

    @Test
    fun autoForcesMedia3WhenDrmProtected() {
        val request = VideoPlaybackBackendRequest(isDrmProtected = true, hasStyledSubtitles = true)
        assertEquals(VideoPlaybackBackendKind.Media3, VideoPlaybackBackendSelector.select(request))
    }

    @Test
    fun autoForcesMedia3OnExternalDisplay() {
        val request = VideoPlaybackBackendRequest(isExternalDisplay = true, hasHardContainer = true)
        assertEquals(VideoPlaybackBackendKind.Media3, VideoPlaybackBackendSelector.select(request))
    }

    @Test
    fun autoFallsBackToMedia3BelowMpvDeviceFloor() {
        val request = VideoPlaybackBackendRequest(
            hasHardContainer = true,
            mpvSupportedOnDevice = false,
        )
        assertEquals(VideoPlaybackBackendKind.Media3, VideoPlaybackBackendSelector.select(request))
    }
}
