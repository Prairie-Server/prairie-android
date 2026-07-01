package org.siloserver.silo.common.player.backend

import kotlin.test.Test
import kotlin.test.assertTrue

class MpvVideoPlaybackBackendSourceTest {
    private val source = java.io.File(
        "src/androidMain/kotlin/org/siloserver/silo/common/player/backend/MpvVideoPlaybackBackend.kt",
    )

    @Test
    fun mpvBackendReportsNativeCapabilitiesAndUsesSharedMounting() {
        val text = source.readText()

        assertTrue(text.contains("class MpvVideoPlaybackBackend"))
        assertTrue(text.contains("override val kind: VideoPlaybackBackendKind = VideoPlaybackBackendKind.Mpv"))
        assertTrue(text.contains("override val capabilities: VideoBackendCapabilities = VideoBackendCapabilities.mpv()"))
        assertTrue(text.contains("mountVideoMedia("))
        assertTrue(text.contains("refreshMountedVideoMedia("))
        assertTrue(text.contains("trackSelectionCoordinator.selectSubtitle("))
        assertTrue(text.contains("trackSelectionCoordinator.selectAudioTrack("))
    }

    @Test
    fun mpvBackendDoesNotEagerOpenRemoteSubtitlesOnMount() {
        val text = source.readText()

        assertTrue(text.contains("private fun withoutEagerSubtitles"))
        assertTrue(text.contains("spec.copy(subtitles = emptyList())"))
        assertTrue(text.contains("spec = withoutEagerSubtitles(spec),"))
    }
}
