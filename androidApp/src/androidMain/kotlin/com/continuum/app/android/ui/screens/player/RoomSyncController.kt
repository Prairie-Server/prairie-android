package com.continuum.app.android.ui.screens.player

import android.os.SystemClock
import com.continuum.app.RoomSyncEngine
import com.continuum.app.SyncDecision
import com.continuum.app.model.watchtogether.MemberRole
import com.continuum.app.model.watchtogether.RoomPlaybackState
import com.continuum.app.model.watchtogether.RoomSnapshot
import com.continuum.app.model.watchtogether.TransportAction
import com.continuum.app.repository.PongSample
import com.continuum.app.repository.ScheduledTransportCommand
import com.continuum.app.repository.WatchTogetherRepository
import com.continuum.app.watchtogether.RoomTransportIntent
import com.continuum.app.watchtogether.roomTransportAuthorized
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.Instant

/**
 * Binds a Watch Together room to the mobile [PlayerViewModel] for the lifetime
 * of a synced-player screen. Active ONLY when the player route carries a roomId.
 *
 * The controller OWNS a [RoomSyncEngine] (the shared engine is a pure class that
 * lives in the binding, not the repo). It:
 *  - launches the repo WS reconnect loop ([WatchTogetherRepository.connect] is
 *    suspend) and attaches the player's playback session id once it resolves;
 *  - drives a ping loop so pongs flow, stamps `clientReceivedMs` on each pong and
 *    feeds [RoomSyncEngine.recordPongSample] to keep the clock offset fresh;
 *  - feeds each [ScheduledTransportCommand] into [RoomSyncEngine.decide] and
 *    applies the resulting [SyncDecision] (seek / setPlaying) at the engine-
 *    scheduled local delay, auto-emitting `ready` on the waiting barrier;
 *  - emits `state_report` on a ~1.5s cadence, suppressed around a pending execute
 *    (the only unit-tested decision — see [shouldEmitStateReport]);
 *  - emits `ready`/`buffering` on buffer transitions while the room is waiting;
 *  - routes user play/pause/seek through `transport_request`, gated on
 *    `self_can_control_transport`;
 *  - surfaces `room_closed` so the screen exits.
 *
 * ## Clock domains (critical)
 *  - WALL CLOCK ([System.currentTimeMillis]) is used for everything the engine
 *    sees: the ping `client_sent_at` RFC3339 string, the `clientReceivedMs`
 *    pong stamp, and `nowLocalMs` passed to [RoomSyncEngine.decide]. The engine
 *    derives its offset as `server - clientWallClock`, and the server timestamps
 *    are wall-clock epoch millis, so these MUST share the same clock.
 *  - MONOTONIC ([SystemClock.elapsedRealtime]) is used only for the local
 *    state-report cadence/suppression gate — pure local timing that must not
 *    jump if the wall clock is adjusted.
 */
class RoomSyncController(
    private val roomId: String,
    private val repository: WatchTogetherRepository,
    private val viewModel: PlayerViewModel,
    private val scope: CoroutineScope,
    private val engine: RoomSyncEngine = RoomSyncEngine(),
) {
    companion object {
        private const val REPORT_CADENCE_MS = 1_500L
        private const val SUPPRESS_WINDOW_MS = 250L
        private const val REPORT_TICK_MS = 250L
        private const val PING_INTERVAL_MS = 15_000L
    }

    /** Live room snapshot for the overlay. */
    val room: StateFlow<RoomSnapshot?> get() = repository.roomSnapshot

    /**
     * Non-null once the server CLOSES the room (host left / explicit close) — the
     * screen observes and exits. Transient server `error` frames do NOT set this
     * (they would wrongly eject the user); they flow on [WatchTogetherRepository.errors].
     */
    val closedReason: StateFlow<String?> get() = repository.roomClosedReason

    // Monotonic-domain state-report cadence bookkeeping.
    @Volatile private var lastReportMs = 0L
    @Volatile private var pendingExecuteAtMs: Long? = null

    // Session id we have already SENT attach_session for (in-flight/sent latch),
    // distinct from the server's echoed confirmation. We send attach_session AT
    // MOST ONCE per session-id resolution; this latch suppresses re-sends on the
    // many intervening snapshots (member-count / host-connected / transport-state)
    // the server emits before it echoes our attached_session_id back. Reset to
    // null when the session id clears OR on a genuine server-side detach (see
    // [serverHadOurSession]) so a reconnect re-attaches exactly once.
    @Volatile private var sentAttachForSessionId: String? = null

    // Tracks whether the LAST snapshot had the server associated with our session.
    // A true -> false transition is a genuine server-side detach (mid-session
    // reconnect drops the association); that — not the pre-echo window — is what
    // re-arms the latch to re-send attach_session once.
    @Volatile private var serverHadOurSession: Boolean = false

    fun start() {
        // Reconnect-with-backoff loop. Suspends until the scope is cancelled or
        // the server closes the room. The lobby's connect() ran in its own
        // (now-dead) scope, so the player owns the live connection.
        scope.launch { repository.connect(roomId) }

        // Attach the player's own playback session id, and RE-attach on WS
        // reconnect. We combine the local session id with each server snapshot.
        //
        // The server takes SEVERAL snapshots (member-count, host-connected,
        // transport-state changes) to echo back our attached_session_id. So
        // "the server doesn't yet have our session" is true for every snapshot
        // in that pre-echo window — sending on each would spam attach frames.
        // We therefore use a SENT latch ([sentAttachForSessionId]) that is
        // distinct from the echo-confirmed check:
        //  - send attach_session AT MOST ONCE per session-id resolution, the
        //    moment our session id first resolves (set the latch immediately so
        //    intervening pre-echo snapshots don't re-send);
        //  - re-arm the latch (and thus re-send once) ONLY on a genuine
        //    server-side detach: a snapshot transition where the server HAD our
        //    session and now doesn't ([serverHadOurSession] true -> false). A
        //    mid-session reconnect produces exactly that transition, so we
        //    re-attach once without ever firing during the initial pre-echo
        //    window (where serverHadOurSession was never true).
        scope.launch {
            combine(
                viewModel.uiState.map { it.sessionId }.distinctUntilChanged(),
                repository.roomSnapshot,
            ) { sessionId, snapshot -> sessionId to snapshot }
                .collect { (sessionId, snapshot) ->
                    if (sessionId == null) {
                        sentAttachForSessionId = null
                        serverHadOurSession = false
                        return@collect
                    }
                    val serverHasOurSession = snapshot?.attachedSessionId == sessionId
                    // Genuine server-side detach: previously associated, now not.
                    // Re-arm the latch so we re-attach this session exactly once.
                    if (serverHadOurSession && !serverHasOurSession) {
                        sentAttachForSessionId = null
                    }
                    serverHadOurSession = serverHasOurSession

                    // Send at most once per resolution while the server hasn't
                    // confirmed our association. Setting the latch BEFORE the
                    // (suspending) send is what suppresses pre-echo re-sends.
                    if (!serverHasOurSession && sentAttachForSessionId != sessionId) {
                        sentAttachForSessionId = sessionId
                        repository.attachSession(sessionId)
                    }
                }
        }

        // Clock-sync: drive pings (once on start + every PING_INTERVAL_MS) and
        // fold pongs into the engine's offset estimate.
        scope.launch {
            while (isActive) {
                repository.ping(clientSentAt = nowWallClockRfc3339())
                delay(PING_INTERVAL_MS)
            }
        }
        scope.launch {
            repository.pongs.collect { pong -> recordPong(pong) }
        }

        // Apply engine decisions for each scheduled transport command.
        scope.launch {
            repository.transportCommands.collect { scheduled -> handleCommand(scheduled) }
        }

        // Drift reporting loop (suppressed around a pending execute).
        scope.launch {
            while (isActive) {
                val state = viewModel.uiState.value
                val sessionId = state.sessionId
                val now = monotonicMs()
                if (sessionId != null &&
                    shouldEmitStateReport(now, lastReportMs, REPORT_CADENCE_MS, pendingExecuteAtMs, SUPPRESS_WINDOW_MS)
                ) {
                    lastReportMs = now
                    repository.stateReport(
                        sessionId = sessionId,
                        positionSeconds = state.position,
                        isPaused = state.isPaused,
                    )
                }
                delay(REPORT_TICK_MS)
            }
        }

        // ready / buffering during the waiting barrier.
        scope.launch {
            var lastBuffering: Boolean? = null
            viewModel.uiState.collect { state ->
                val waiting = repository.roomSnapshot.value?.playbackState == RoomPlaybackState.Waiting
                val sessionId = state.sessionId
                if (waiting && sessionId != null && state.isBuffering != lastBuffering) {
                    lastBuffering = state.isBuffering
                    if (state.isBuffering) {
                        repository.buffering(sessionId, state.position, state.isPaused)
                    } else {
                        repository.ready(sessionId, state.position, state.isPaused)
                    }
                }
            }
        }
    }

    /**
     * Stamp `clientReceivedMs` (wall clock) at collection time and feed the NTP
     * sample to the engine. Null guards: a sample with any missing server
     * timestamp can't update the offset, so we drop it.
     */
    private fun recordPong(pong: PongSample) {
        val clientSent = pong.clientSentMs ?: return
        val serverReceived = pong.serverReceivedMs ?: return
        val serverSent = pong.serverSentMs ?: return
        engine.recordPongSample(
            clientSentMs = clientSent,
            serverReceivedMs = serverReceived,
            serverSentMs = serverSent,
            clientReceivedMs = System.currentTimeMillis(),
        )
    }

    private suspend fun handleCommand(scheduled: ScheduledTransportCommand) {
        val command = scheduled.command
        val state = viewModel.uiState.value
        val snapshot = repository.roomSnapshot.value
        // executeAtMs null (malformed wire timestamp) → apply immediately; pass
        // nowWallClock so the engine computes a 0 delay.
        val nowWall = System.currentTimeMillis()
        val serverExecuteAtMs = scheduled.executeAtMs ?: nowWall
        val decision = engine.decide(
            command = command,
            serverExecuteAtMs = serverExecuteAtMs,
            currentLocalPositionMs = (state.position * 1000.0).toLong(),
            currentIsPlaying = !state.isPaused,
            nowLocalMs = nowWall,
            currentRevision = snapshot?.selectionRevision ?: command.selectionRevision,
            attachedSessionId = snapshot?.attachedSessionId ?: state.sessionId,
        ) ?: return // duplicate / revision / session gate → ignore

        applyDecision(decision, state.sessionId)
    }

    private suspend fun applyDecision(decision: SyncDecision, sessionId: String?) {
        val delayMs = decision.localExecuteDelayMs.coerceAtLeast(0L)
        // Record the pending execute against the MONOTONIC clock for the
        // state-report suppression gate, then wait the engine-computed delay.
        pendingExecuteAtMs = monotonicMs() + delayMs
        if (delayMs > 0) delay(delayMs)

        // Use the deadband-free immediate-seek path: room corrective seeks can be
        // as small as the engine's 0.35s DRIFT_THRESHOLD_MS, well under the 2.0s
        // position-mirror deadband in PlayerScreen, so onSeek() alone would never
        // reach the MediaController.
        decision.seekToMs?.let { ms -> viewModel.seekImmediate(ms / 1000.0) }
        // onPlayPause() is a TOGGLE — only flip when the desired state differs
        // from current intent.
        if (viewModel.uiState.value.isPaused == decision.setPlaying) {
            viewModel.onPlayPause()
        }
        pendingExecuteAtMs = null

        // Auto-emit ready on the waiting barrier (command.playback_state == waiting).
        if (decision.shouldEmitReady && sessionId != null) {
            val s = viewModel.uiState.value
            repository.ready(sessionId, s.position, s.isPaused)
        }
    }

    // ---- User-initiated transport: route through the room instead of local apply ----

    fun onUserPlayPause() {
        if (!roomTransportAuthorized(repository.roomSnapshot.value, RoomTransportIntent.PlayPause)) return
        val state = viewModel.uiState.value
        // isPaused is current intent; tapping toggles it. The request carries the
        // target action and the new paused state.
        val willPause = !state.isPaused
        scope.launch {
            repository.transportRequest(
                action = if (willPause) TransportAction.Pause.wire else TransportAction.Play.wire,
                positionSeconds = state.position,
                isPaused = willPause,
            )
        }
    }

    fun onUserSeek(positionSeconds: Double) {
        // Seek is host-only (the server rejects guest seeks regardless of policy).
        if (!roomTransportAuthorized(repository.roomSnapshot.value, RoomTransportIntent.Seek)) return
        val state = viewModel.uiState.value
        scope.launch {
            repository.transportRequest(
                action = TransportAction.Seek.wire,
                positionSeconds = positionSeconds,
                isPaused = state.isPaused,
            )
        }
    }

    /** Leave the room. Host close tears the room down for everyone; both reset local state. */
    fun leave(closeRoom: Boolean) {
        scope.launch {
            if (closeRoom) repository.closeRoom()
            engine.reset()
            repository.reset()
        }
    }

    /** Wall-clock RFC3339 string for the ping `client_sent_at`. */
    private fun nowWallClockRfc3339(): String = Instant.ofEpochMilli(System.currentTimeMillis()).toString()

    /** Monotonic clock for local-only cadence/scheduling. */
    private fun monotonicMs(): Long = SystemClock.elapsedRealtime()
}

/** Convenience: is the local member the room host? */
val RoomSnapshot.isHost: Boolean get() = selfRole == MemberRole.Host
