package org.prairieserver.prairie.common.player.backend

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class VideoPlaybackBackendInterfaceTest {
    private val source = java.io.File(
        "src/androidMain/kotlin/org/prairieserver/prairie/common/player/backend/VideoPlaybackBackend.kt",
    )

    @Test
    fun backendInterfaceExposesOnlySharedPlaybackOperations() {
        val text = source.readText()

        assertTrue(text.contains("interface VideoPlaybackBackend"))
        assertTrue(text.contains("val kind: VideoPlaybackBackendKind"))
        assertTrue(text.contains("val capabilities: VideoBackendCapabilities"))
        assertTrue(text.contains("val player: Player"))
        assertTrue(text.contains("fun mount("))
        assertTrue(text.contains("fun refresh("))
        assertTrue(text.contains("fun selectSubtitle("))
        assertTrue(text.contains("fun selectMountedSubtitle("))
        assertTrue(text.contains("fun selectAudioTrack("))
        assertTrue(text.contains("fun applyTrackSelection("))
        assertTrue(text.contains("fun release()"))
        assertFalse(text.contains("mpv", ignoreCase = true))
        assertFalse(text.contains("libass", ignoreCase = true))
    }
}
