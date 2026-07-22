package org.siloserver.silo.tv.ui.screens.player

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TvPlayerPresentationStateTest {
    @Test
    fun positionOnlyChangesProduceEqualPresentationState() {
        val first = TvPlayerViewModel.UiState(position = 12.0, duration = 100.0)
        val second = first.copy(position = 12.5)

        assertEquals(first.withoutPlaybackClock(), second.withoutPlaybackClock())
        assertEquals(PlaybackClock(12.0, 100.0), first.toPlaybackClock())
        assertEquals(PlaybackClock(12.5, 100.0), second.toPlaybackClock())
    }

    @Test
    fun screenCollectsStructuralStateAtRootAndClockOnlyInsideWrapper() {
        val source = File(
            "src/androidMain/kotlin/org/siloserver/silo/tv/ui/screens/player/TvPlayerScreen.kt",
        ).readText()

        assertTrue(source.contains("viewModel.presentationState.collectAsState()"))
        assertFalse(source.contains("val state by viewModel.uiState.collectAsState()"))
        assertTrue(source.contains("viewModel.playbackClock.collectAsState()"))
        assertTrue(source.contains("viewModel.uiState.value.toSiloCastPlaybackState("))
        assertFalse(source.contains("latestPlayerState"))
    }
}
