package org.prairieserver.prairie.tv.ui.screens.audiobook

import kotlin.test.Test
import kotlin.test.assertTrue

class TvAudiobookPlayerScreenExitTest {
    private val source = java.io.File(
        "src/androidMain/kotlin/org/prairieserver/prairie/tv/ui/screens/audiobook/TvAudiobookPlayerScreen.kt",
    ).readText()

    @Test
    fun tvAudiobookScreenDoesNotPrepareMediaAfterExitBegins() {
        val prepareEffect = source
            .substringAfter("LaunchedEffect(controller, state.streamUrl)")
            .substringBefore("LaunchedEffect(controller, state.isPaused)")

        assertTrue(
            source.contains("var exitRequested by remember"),
            "TV audiobook screen must track explicit exit state",
        )
        assertTrue(
            prepareEffect.contains("if (exitRequested) return@LaunchedEffect"),
            "TV audiobook screen must not prepare media after exit has been requested",
        )
    }

    @Test
    fun tvAudiobookBackHandlerStopsPlaybackBeforeNavigation() {
        val backHandler = source
            .substringAfter("BackHandler(enabled = true)")
            .substringBefore("Box(modifier")

        assertTrue(
            backHandler.contains("exitRequested = true"),
            "TV audiobook back must mark exit before navigation",
        )
        assertTrue(
            backHandler.contains("viewModel.stopPlaybackSession()"),
            "TV audiobook back must clear playback state before navigation",
        )
        assertTrue(
            backHandler.contains("onExit()"),
            "TV audiobook back must still delegate navigation",
        )
    }
}
