package org.prairieserver.prairie.common.player.video

import org.prairieserver.prairie.model.playback.CLIENT_DV8_HDR10_PLUS_SANITIZER
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PlaybackRuntimeCorrectionStateTest {
    @Test
    fun correctionIsEnabledOnlyByTheMountedPlan() {
        val state = PlaybackRuntimeCorrectionState()

        assertFalse(state.isEnabled(CLIENT_DV8_HDR10_PLUS_SANITIZER))

        state.activate(listOf(CLIENT_DV8_HDR10_PLUS_SANITIZER))
        assertTrue(state.isEnabled(CLIENT_DV8_HDR10_PLUS_SANITIZER))

        state.activate(emptyList())
        assertFalse(state.isEnabled(CLIENT_DV8_HDR10_PLUS_SANITIZER))
    }
}
