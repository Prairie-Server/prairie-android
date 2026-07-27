package org.siloserver.silo.common.player

import androidx.media3.common.Player

/**
 * Only deliberate user/MediaSession changes may become room transport intent.
 *
 * Audio-focus loss, noisy-route changes, end-of-item, and playback suppression
 * are local player conditions. Broadcasting them would pause the room for
 * everyone, while forcibly undoing them could resume playback against the
 * platform's safety decision.
 */
fun shouldReconcileExternalPlayWhenReady(reason: Int): Boolean =
    reason == Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST ||
        reason == Player.PLAY_WHEN_READY_CHANGE_REASON_REMOTE

/**
 * Suppresses the callback produced by our own room/ViewModel synchronization.
 * Media3 reports a programmatic `playWhenReady` assignment as USER_REQUEST, so
 * the reason alone cannot distinguish it from a notification/headset command.
 */
class PlayWhenReadyReconciliationGate(
    private val nowMs: () -> Long = { android.os.SystemClock.elapsedRealtime() },
) {
    private data class PendingChange(val value: Boolean, val createdAtMs: Long)

    data class CallbackDecision(
        val shouldReconcile: Boolean,
        val followUpProgrammaticValue: Boolean? = null,
    )

    private var inFlight: PendingChange? = null
    private var queuedLatest: Boolean? = null

    val hasPendingChanges: Boolean
        get() {
            discardExpired()
            return inFlight != null || queuedLatest != null
        }

    /**
     * Register a programmatic target. Returns true only when the caller should
     * issue it now. While one command awaits its callback, later targets are
     * coalesced and issued by [onPlayWhenReadyChanged] after that callback.
     */
    fun requestProgrammaticChange(value: Boolean): Boolean {
        discardExpired()
        if (inFlight == null) {
            inFlight = PendingChange(value, nowMs())
            return true
        }
        queuedLatest = value
        return false
    }

    fun onPlayWhenReadyChanged(value: Boolean, reason: Int): CallbackDecision {
        discardExpired()
        if (inFlight?.value == value) {
            inFlight = null
            val followUp = queuedLatest
            queuedLatest = null
            if (followUp != null && followUp != value) {
                inFlight = PendingChange(followUp, nowMs())
                return CallbackDecision(
                    shouldReconcile = false,
                    followUpProgrammaticValue = followUp,
                )
            }
            return CallbackDecision(shouldReconcile = false)
        }
        return CallbackDecision(
            shouldReconcile = shouldReconcileExternalPlayWhenReady(reason),
        )
    }

    fun reset() {
        inFlight = null
        queuedLatest = null
    }

    private fun discardExpired() {
        val cutoff = nowMs() - PENDING_CHANGE_MAX_AGE_MS
        if (inFlight?.createdAtMs?.let { it < cutoff } == true) {
            inFlight = null
            queuedLatest = null
        }
    }

    private companion object {
        const val PENDING_CHANGE_MAX_AGE_MS = 2_000L
    }
}
