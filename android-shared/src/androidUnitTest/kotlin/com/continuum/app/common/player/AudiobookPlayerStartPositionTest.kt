package com.continuum.app.common.player

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AudiobookPlayerStartPositionTest {
    @Test
    fun audiobookServerPlaybackUsesSharedStartPositionResolver() {
        val source = java.io.File(
            "src/androidMain/kotlin/com/continuum/app/common/player/AudiobookPlayerViewModel.kt",
        ).readText()

        assertTrue(source.contains("resolvePlaybackStartRequestPosition("))
        assertTrue(source.contains("resolvePlaybackStartPosition("))
        assertFalse(source.contains("startPosition = resumePosition ?: 0.0"))
        assertFalse(source.contains("seekSeconds = resumePosition ?: 0.0"))
    }
}
