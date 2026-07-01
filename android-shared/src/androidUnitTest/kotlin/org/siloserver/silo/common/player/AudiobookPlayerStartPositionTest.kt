package org.siloserver.silo.common.player

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AudiobookPlayerStartPositionTest {
    private val source = java.io.File(
        "src/androidMain/kotlin/org/siloserver/silo/common/player/AudiobookPlayerViewModel.kt",
    ).readText()

    @Test
    fun audiobookServerPlaybackUsesSharedStartPositionResolver() {
        assertTrue(source.contains("resolvePlaybackStartRequestPosition("))
        assertTrue(source.contains("resolvePlaybackStartPosition("))
        assertFalse(source.contains("startPosition = resumePosition ?: 0.0"))
        assertFalse(source.contains("seekSeconds = resumePosition ?: 0.0"))
    }

    @Test
    fun audiobookRouteStartPositionIsExplicitOverride() {
        assertTrue(source.contains("savedStateHandle.get<String>(\"startPosition\")"))
        assertTrue(source.contains("requestedStartPosition != null -> requestedStartPosition"))
        assertTrue(source.contains("_resumePosition.value = explicitStartOverride.takeIf { it > 0.0 }"))
        assertTrue(source.contains("_resumePosition.value = requestStartPosition?.takeIf { it > 0.0 }"))
    }

    @Test
    fun stopPlaybackSessionClearsPlayableStateEvenWithoutRemoteSession() {
        val stopBody = source
            .substringAfter("fun stopPlaybackSession()")
            .substringBefore("private suspend fun reportSessionProgress")
        val clearStateIndex = stopBody.indexOf("_uiState.update")
        val sessionReturnIndex = stopBody.indexOf("sessionId ?: return")

        assertTrue(
            clearStateIndex >= 0,
            "audiobook stop must clear UI playable state",
        )
        assertTrue(
            sessionReturnIndex < 0 || clearStateIndex < sessionReturnIndex,
            "audiobook stop must clear local/offline playback state before any session-id return",
        )
        assertTrue(
            stopBody.contains("streamUrl = null"),
            "audiobook stop must clear stale stream URL",
        )
        assertTrue(
            stopBody.contains("sessionId = null"),
            "audiobook stop must clear active session id from UI state",
        )
    }
}
