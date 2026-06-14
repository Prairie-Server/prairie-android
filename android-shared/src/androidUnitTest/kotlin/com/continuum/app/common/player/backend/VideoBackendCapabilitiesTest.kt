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
    fun mpvCapabilitiesDescribeNativePlaybackBehavior() {
        val capabilities = VideoBackendCapabilities.mpv()

        assertEquals(VideoPlaybackBackendKind.Mpv, capabilities.backendKind)
        assertEquals(PlaybackRoute.Compatibility, capabilities.route)
        assertTrue(capabilities.supportsSidecarSubtitles)
        assertTrue(capabilities.supportsEmbeddedSubtitleSelection)
        assertTrue(capabilities.supportsAudioTrackSelection)
        assertTrue(capabilities.supportsBufferReporting)
        assertTrue(capabilities.supportsHardContainers)
        assertEquals(SubtitleRendering.NativeBackend, capabilities.subtitleRendering)
        assertEquals("MPV", capabilities.displayName)
    }

    @Test
    fun mpvDependencyIsDeclaredInSharedAndroidMain() {
        val catalog = java.io.File("../gradle/libs.versions.toml").readText()
        val build = java.io.File("build.gradle.kts").readText()

        assertTrue(catalog.contains("libmpv = \"1.0.0\""))
        assertTrue(catalog.contains("dev.jdtech.mpv"))
        assertTrue(build.contains("implementation(libs.libmpv)"))
    }

    @Test
    fun backendRequestDefaultsToAutoMedia3CompatibleSelection() {
        val request = VideoPlaybackBackendRequest()

        assertEquals(null, request.contentId)
        assertEquals(null, request.fileId)
        assertEquals(null, request.playMethod)
        assertEquals(VideoPlaybackFormFactor.Unknown, request.formFactor)
        assertEquals(VideoPlaybackBackendPreference.Auto, request.preference)
        assertEquals(false, request.hasHardContainer)
        assertEquals(false, request.hasStyledSubtitles)
    }
}
