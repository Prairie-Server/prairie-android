package com.continuum.app.common.player.video

import com.continuum.app.common.player.Playability
import com.continuum.app.model.playback.PlayMethod

/**
 * Detects the gap between "Media3 accepted this direct file" and "playback
 * actually started." Preflight covers known unsupported tracks and player
 * errors; this covers startup sessions that stay in BUFFERING forever.
 */
class PlaybackStartupStallDetector(
    private val startupGraceMs: Long = DEFAULT_STARTUP_GRACE_MS,
    private val startedProgressMs: Long = DEFAULT_STARTED_PROGRESS_MS,
) {
    private var sessionKey: String? = null
    private var playMethod: PlayMethod? = null
    private var mountedAtMs: Long = 0L
    private var startPositionMs: Long = 0L
    private var started = false
    private var signaled = false

    fun onMounted(
        sessionKey: String,
        playMethod: PlayMethod,
        startPositionMs: Long,
        nowMs: Long,
    ) {
        if (this.sessionKey == sessionKey) return
        this.sessionKey = sessionKey
        this.playMethod = playMethod
        this.mountedAtMs = nowMs
        this.startPositionMs = startPositionMs.coerceAtLeast(0L)
        this.started = false
        this.signaled = false
    }

    fun sample(
        sessionKey: String,
        nowMs: Long,
        playWhenReady: Boolean,
        isPlaying: Boolean,
        isBuffering: Boolean,
        currentPositionMs: Long,
        bufferedPositionMs: Long,
    ): Playability.StartupStalled? {
        if (sessionKey != this.sessionKey) return null
        if (isPlaying || currentPositionMs - startPositionMs >= startedProgressMs) {
            started = true
        }
        if (started || signaled || playMethod != PlayMethod.DIRECT) return null
        if (!playWhenReady || !isBuffering) return null

        val stalledForMs = nowMs - mountedAtMs
        if (stalledForMs <= startupGraceMs) return null

        signaled = true
        return Playability.StartupStalled(
            bufferedAheadMs = (bufferedPositionMs - currentPositionMs).coerceAtLeast(0L),
            stalledForMs = stalledForMs,
        )
    }

    companion object {
        const val DEFAULT_STARTUP_GRACE_MS: Long = 20_000L
        const val DEFAULT_STARTED_PROGRESS_MS: Long = 1_500L
    }
}
