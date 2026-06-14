package com.continuum.app.common.player.backend

import com.continuum.app.common.player.route.PlaybackRoute
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class VideoBackendCapabilitiesTest {

    @Test
    fun media3CapabilitiesDescribeCurrentPlayerBehavior() {
        val capabilities = VideoBackendCapabilities.media3()

        assertEquals(VideoPlaybackBackendKind.Media3, capabilities.backendKind)
        assertEquals(PlaybackRoute.Compatibility, capabilities.route)
        assertTrue(capabilities.supportsSidecarSubtitles)
        assertTrue(capabilities.supportsEmbeddedSubtitleSelection)
        assertTrue(capabilities.supportsAudioTrackSelection)
        assertTrue(capabilities.supportsBufferReporting)
        assertTrue(capabilities.supportsSubtitleDelay)
        assertTrue(capabilities.supportsAudioDelay)
        assertEquals(SubtitleRendering.Media3Text, capabilities.subtitleRendering)
    }

    @Test
    fun backendRequestDefaultsToAutoMedia3CompatibleSelection() {
        val request = VideoPlaybackBackendRequest()

        assertEquals(null, request.contentId)
        assertEquals(null, request.fileId)
        assertEquals(null, request.playMethod)
        assertEquals(VideoPlaybackFormFactor.Unknown, request.formFactor)
        assertEquals(VideoPlaybackBackendPreference.Auto, request.preference)
    }
}
