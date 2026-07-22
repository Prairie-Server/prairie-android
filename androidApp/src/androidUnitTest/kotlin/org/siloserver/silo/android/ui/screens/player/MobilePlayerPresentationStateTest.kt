package org.siloserver.silo.android.ui.screens.player

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MobilePlayerPresentationStateTest {
    @Test
    fun clockOnlyUpdatesLeaveStructuralStateEqual() {
        val first = PlayerViewModel.PlayerUiState(
            contentId = "movie-1",
            position = 12.0,
            duration = 100.0,
            bufferedPosition = 30.0,
        )
        val second = first.copy(position = 12.5, duration = 101.0, bufferedPosition = 35.0)

        assertEquals(first.withoutPlaybackClock(), second.withoutPlaybackClock())
        assertNotEquals(first.toPlaybackClock(), second.toPlaybackClock())
        assertEquals(second, second.withoutPlaybackClock().withPlaybackClock(second.toPlaybackClock()))
    }

    @Test
    fun configurationRecreationDoesNotReclaimInitialRouteLoadAfterInPlaceTransition() {
        val gate = InitialPlayerLoadGate()

        assertTrue(gate.claim())
        assertFalse(gate.claim())
    }

    @Test
    fun configurationDisposalReleasesControllerWithoutClearingPlayback() {
        assertFalse(shouldClearPlaybackOnControllerDispose(isChangingConfigurations = true))
        assertTrue(shouldClearPlaybackOnControllerDispose(isChangingConfigurations = false))
    }

    @Test
    fun subtitleRefreshNonceCanRestartAfterANewMediaMount() {
        val gate = SubtitleRefreshGate()

        assertTrue(gate.claim(1))
        assertFalse(gate.claim(1))

        gate.reset()

        assertTrue(gate.claim(1))
    }
}
