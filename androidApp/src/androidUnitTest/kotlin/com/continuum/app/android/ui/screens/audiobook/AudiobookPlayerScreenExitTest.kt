package com.continuum.app.android.ui.screens.audiobook

import kotlin.test.Test
import kotlin.test.assertTrue

class AudiobookPlayerScreenExitTest {
    private val source = java.io.File(
        "src/androidMain/kotlin/com/continuum/app/android/ui/screens/audiobook/AudiobookPlayerScreen.kt",
    ).readText()

    @Test
    fun audiobookScreenDoesNotPrepareMediaAfterExitBegins() {
        val prepareEffect = source
            .substringAfter("LaunchedEffect(controller, state.streamUrl)")
            .substringBefore("// Flush position")

        assertTrue(
            source.contains("var exitRequested by remember"),
            "mobile audiobook screen must track explicit exit state",
        )
        assertTrue(
            prepareEffect.contains("if (exitRequested) return@LaunchedEffect"),
            "mobile audiobook screen must not prepare media after exit has been requested",
        )
    }

    @Test
    fun audiobookBackButtonStopsPlaybackBeforeNavigation() {
        val backRow = source
            .substringAfter("// Back row + bookmark icon")
            .substringBefore("if (showBookmarksSheet)")

        assertTrue(
            backRow.contains("exitRequested = true"),
            "mobile audiobook back must mark exit before navigation",
        )
        assertTrue(
            backRow.contains("viewModel.stopPlaybackSession()"),
            "mobile audiobook back must clear playback state before navigation",
        )
        assertTrue(
            backRow.contains("onBackClick()"),
            "mobile audiobook back must still delegate navigation",
        )
    }
}
