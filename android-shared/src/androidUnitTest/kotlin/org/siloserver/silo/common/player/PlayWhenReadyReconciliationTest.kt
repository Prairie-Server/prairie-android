package org.siloserver.silo.common.player

import androidx.media3.common.Player
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PlayWhenReadyReconciliationTest {
    @Test
    fun `deliberate user and media session changes are reconciled`() {
        assertTrue(shouldReconcileExternalPlayWhenReady(Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST))
        assertTrue(shouldReconcileExternalPlayWhenReady(Player.PLAY_WHEN_READY_CHANGE_REASON_REMOTE))
    }

    @Test
    fun `local safety and lifecycle changes never become room intent`() {
        assertFalse(shouldReconcileExternalPlayWhenReady(Player.PLAY_WHEN_READY_CHANGE_REASON_AUDIO_FOCUS_LOSS))
        assertFalse(shouldReconcileExternalPlayWhenReady(Player.PLAY_WHEN_READY_CHANGE_REASON_AUDIO_BECOMING_NOISY))
        assertFalse(shouldReconcileExternalPlayWhenReady(Player.PLAY_WHEN_READY_CHANGE_REASON_END_OF_MEDIA_ITEM))
        assertFalse(shouldReconcileExternalPlayWhenReady(Player.PLAY_WHEN_READY_CHANGE_REASON_SUPPRESSED_TOO_LONG))
    }

    @Test
    fun `programmatic room synchronization is consumed instead of rebroadcast`() {
        val gate = PlayWhenReadyReconciliationGate()
        assertTrue(gate.requestProgrammaticChange(false))

        assertFalse(
            gate.onPlayWhenReadyChanged(
                false,
                Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST,
            ).shouldReconcile,
        )
        assertTrue(
            gate.onPlayWhenReadyChanged(
                true,
                Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST,
            ).shouldReconcile,
        )
    }

    @Test
    fun `unrelated safety callback does not consume a pending programmatic value`() {
        val gate = PlayWhenReadyReconciliationGate()
        gate.requestProgrammaticChange(false)

        assertFalse(
            gate.onPlayWhenReadyChanged(
                true,
                Player.PLAY_WHEN_READY_CHANGE_REASON_AUDIO_FOCUS_LOSS,
            ).shouldReconcile,
        )
        assertFalse(
            gate.onPlayWhenReadyChanged(
                false,
                Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST,
            ).shouldReconcile,
        )
    }

    @Test
    fun `rapid programmatic pause resume preserves ordered callback provenance`() {
        val gate = PlayWhenReadyReconciliationGate()
        assertTrue(gate.requestProgrammaticChange(false))
        assertFalse(gate.requestProgrammaticChange(true))

        val pause = gate.onPlayWhenReadyChanged(false, Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST)
        assertFalse(pause.shouldReconcile)
        assertEquals(true, pause.followUpProgrammaticValue)
        val play = gate.onPlayWhenReadyChanged(true, Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST)
        assertFalse(play.shouldReconcile)
        assertNull(play.followUpProgrammaticValue)
        assertFalse(gate.hasPendingChanges)
    }

    @Test
    fun `alternating queued targets coalesce before a second command is issued`() {
        val gate = PlayWhenReadyReconciliationGate()
        assertTrue(gate.requestProgrammaticChange(false))
        assertFalse(gate.requestProgrammaticChange(true))
        assertFalse(gate.requestProgrammaticChange(false))

        val pause = gate.onPlayWhenReadyChanged(false, Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST)
        assertFalse(pause.shouldReconcile)
        assertNull(pause.followUpProgrammaticValue)
        assertFalse(gate.hasPendingChanges)
    }

    @Test
    fun `dropped callback expires instead of swallowing a later user command`() {
        var nowMs = 0L
        val gate = PlayWhenReadyReconciliationGate(nowMs = { nowMs })
        gate.requestProgrammaticChange(false)
        nowMs = 2_001L

        assertTrue(
            gate.onPlayWhenReadyChanged(
                false,
                Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST,
            ).shouldReconcile,
        )
    }

    @Test
    fun `reset clears provenance on controller or room replacement`() {
        val gate = PlayWhenReadyReconciliationGate()
        gate.requestProgrammaticChange(false)
        gate.reset()

        assertTrue(
            gate.onPlayWhenReadyChanged(
                false,
                Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST,
            ).shouldReconcile,
        )
    }
}
