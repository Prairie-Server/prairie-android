package com.continuum.app.android.ui.screens.player

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PlayerViewModelStartPositionTest {
    @Test
    fun offlinePlaybackUsesSharedStartPositionResolver() {
        val source = java.io.File(
            "src/androidMain/kotlin/com/continuum/app/android/ui/screens/player/PlayerViewModel.kt",
        ).readText()
        val offlineBranch = source
            .substringAfter("private suspend fun tryLocalPlayback")
            .substringBefore("override fun onCleared")

        assertTrue(offlineBranch.contains("resolvePlaybackStartPosition("))
        assertFalse(offlineBranch.contains("?: watchDetail?.userData?.positionSeconds"))
    }
}
