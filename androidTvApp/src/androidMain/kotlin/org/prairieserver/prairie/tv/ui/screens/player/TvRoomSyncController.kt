package org.prairieserver.prairie.tv.ui.screens.player

import android.os.SystemClock
import org.prairieserver.prairie.RoomSyncEngine
import org.prairieserver.prairie.SyncDecision
import org.prairieserver.prairie.model.watchtogether.MemberRole
import org.prairieserver.prairie.model.watchtogether.RoomPhase
import org.prairieserver.prairie.model.watchtogether.RoomPlaybackState
import org.prairieserver.prairie.model.watchtogether.RoomSnapshot
import org.prairieserver.prairie.model.watchtogether.TransportAction
import org.prairieserver.prairie.repository.PongSample
import org.prairieserver.prairie.repository.ScheduledTransportCommand
import org.prairieserver.prairie.repository.WatchTogetherRepository
import org.prairieserver.prairie.watchtogether.RoomTransportIntent
import org.prairieserver.prairie.watchtogether.roomTransportAuthorized
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.abs
import java.time.Instant

// ---- Pure transport-authority gate (unit-tested) ---------------------------

/**
 * A local transport intent originating from the TV UI. Named distinctly from
 * the shared [TransportAction] (play/pause/seek wire enum) to avoid a clash —
 * the UI only distinguishes "is this a play/pause toggle or a seek?" for the
 * authority check; the wire action is derived later.
 */
enum class TvTransportIntent { PlayPause, Seek }

/** Whether a [TvTransportIntent] may be broadcast as a `transport_request`. */
enum class TransportGate { Send, Blocked }

/**
 * Decide whether the local member may drive [action] right now. Delegates to the
 * shared [roomTransportAuthorized] (the single source of truth mirroring the
 * server): seek is host-only, play/pause follows `self_can_control_transport`,
 * and nothing is allowed outside the Playing phase.
 */
fun tvRoomTransportGate(s: RoomSnapshot?, action: TvTransportIntent): TransportGate {
    val intent = when (action) {
        TvTransportIntent.PlayPause -> RoomTransportIntent.PlayPause
        TvTransportIntent.Seek -> RoomTransportIntent.Seek
    }
    return if (roomTransportAuthorized(s, intent)) TransportGate.Send else TransportGate.Blocked
}

// ---- State-report cadence/suppression gate (duplicated from mobile) --------
//
// Mobile's pure shouldEmitStateReport lives in androidApp, which is NOT a TV
// dependency, so we duplicate the exact logic here (matching the
// notifications/subtitle cross-app duplication convention). Logic must stay in
// lockstep with androidApp's RoomSyncStateReportGate.

/**
 * Emit a `state_report` once per [cadenceMs], EXCEPT inside a +/-
 * [suppressWindowMs] window around a pending transport command's local execute
 * time — reporting our position right as we are about to seek/play would feed
 * the server a stale pre-execute sample and fight the sync barrier.
 */
fun tvShouldEmitStateReport(
    nowMs: Long,
    lastReportMs: Long,
    cadenceMs: Long,
    pendingExecuteAtMs: Long?,
    suppressWindowMs: Long,
): Boolean {
    if (nowMs - lastReportMs < cadenceMs) return false
    if (pendingExecuteAtMs != null && abs(nowMs - pendingExecuteAtMs) <= suppressWindowMs) return false
    return true
}

/**
 * Binds a Watch Together room to the TV [TvPlayerViewModel] for the lifetime of
 * a synced-player screen. Active ONLY when the player route carries a roomId.
 * This is the TV mirror of the mobile `RoomSyncController` (M3) in its final
 * fixed state.
 *
 * The controller OWNS a [RoomSyncEngine] (a pure shared class that lives in the
 * binding, not the repo). It:
 *  - launches the repo WS reconnect loop ([WatchTogetherRepository.connect] is
 *    suspend) and attaches the player's playback session id once it resolves;
 *  - drives a ping loop (one ping on start + every 15s) so pongs flow, stamps
 *    `clientReceivedMs` on each pong with the SAME wall clock used for the ping
 *    `client_sent_at`, and feeds [RoomSyncEngine.recordPongSample];
 *  - feeds each [ScheduledTransportCommand] into [RoomSyncEngine.decide] and
 *    applies the resulting [SyncDecision] (seek / setPlaying) at the engine-
 *    scheduled local delay, auto-emitting `ready` on the waiting barrier;
 *  - emits `state_report` on a ~1.5s cadence, suppressed around a pending
 *    execute (see [tvShouldEmitStateReport]);
 *  - emits `ready`/`buffering` on buffer transitions while the room is waiting;
 *  - routes user play/pause/seek through `transport_request`, gated on
 *    [tvRoomTransportGate];
 *  - surfaces `room_closed` ([closedReason], TERMINAL only) so the screen exits.
 *    Transient server `error` frames do NOT eject the user (they flow on the
 *    repo's `errors` stream, never feeding [closedReason]).
 *
 * ## Clock domains (critical)
 *  - WALL CLOCK ([System.currentTimeMillis]) is used for everything the engine
 *    sees: the ping `client_sent_at` RFC3339 string, the `clientReceivedMs`
 *    pong stamp, and `nowLocalMs` passed to [RoomSyncEngine.decide]. The engine
 *    derives its offset as `server - clientWallClock`, and the server timestamps
 *    are wall-clock epoch millis, so these MUST share the same clock.
 *  - MONOTONIC ([SystemClock.elapsedRealtime]) is used only for the local
 *    state-report cadence/suppression gate + the engine-scheduled execute
 *    delay's pending marker — pure local timing that must not jump if the wall
 *    clock is adjusted.
 */
class TvRoomSyncController(
    private val roomId: String,
    private val repository: WatchTogetherRepository,
    private val viewModel: TvPlayerViewModel,
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

    // Session id we have already SENT attach_session for. We send attach_session
    // AT MOST ONCE per session-id resolution; this latch suppresses re-sends on
    // the many intervening pre-echo snapshots. Re-armed on a genuine server-side
    // detach (see [serverHadOurSession]) so a reconnect re-attaches exactly once.
    @Volatile private var sentAttachForSessionId: String? = null

    // Tracks whether the LAST snapshot had the server associated with our
    // session. A true -> false transition is a genuine server-side detach
    // (mid-session reconnect drops the association); that — not the pre-echo
    // window — re-arms the latch to re-send attach_session once.
    @Volatile private var serverHadOurSession: Boolean = false

    fun start() {
        // Reconnect-with-backoff loop. The lobby's connect() ran in its own
        // (now-dead) scope (the lobby route was popped on hand-off), so the
        // player owns the live connection and re-runs connect(roomId).
        scope.launch { repository.connect(roomId) }

        // Attach the player's own playback session id, and RE-attach on WS
        // reconnect. We combine the local session id with each server snapshot
        // and use a SENT latch distinct from the echo-confirmed check:
        //  - send attach_session AT MOST ONCE per session-id resolution, the
        //    moment the id first resolves (set the latch immediately so
        //    intervening pre-echo snapshots don't re-send);
        //  - re-arm the latch (and re-send once) ONLY on a genuine server-side
        //    detach: a snapshot transition where the server HAD our session and
        //    now doesn't ([serverHadOurSession] true -> false). A mid-session
        //    reconnect produces exactly that transition.
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
                    if (serverHadOurSession && !serverHasOurSession) {
                        sentAttachForSessionId = null
                    }
                    serverHadOurSession = serverHasOurSession

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
                    tvShouldEmitStateReport(
                        now,
                        lastReportMs,
                        REPORT_CADENCE_MS,
                        pendingExecuteAtMs,
                        SUPPRESS_WINDOW_MS,
                    )
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
     * sample to the engine. A sample with any missing server timestamp can't
     * update the offset, so we drop it.
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

        // Use the deadband-free immediate-seek path: room corrective seeks can
        // be as small as the engine's 0.35s DRIFT_THRESHOLD_MS, and a normal
        // seekRequest would be fine on TV (no position-mirror deadband), but we
        // route through seekImmediate to match mobile's contract exactly and
        // guarantee the MediaController moves regardless of any future deadband.
        decision.seekToMs?.let { ms -> viewModel.seekImmediate(ms / 1000.0) }
        // Idempotent pause: set the desired state directly (NOT a toggle) so a
        // duplicate command can't flip us the wrong way.
        viewModel.setPaused(!decision.setPlaying)
        pendingExecuteAtMs = null

        // Auto-emit ready on the waiting barrier (command.playback_state == waiting).
        if (decision.shouldEmitReady && sessionId != null) {
            val s = viewModel.uiState.value
            repository.ready(sessionId, s.position, s.isPaused)
        }
    }

    // ---- User-initiated transport: route through the room instead of local apply ----

    /**
     * Route a local play/pause through the room. Gated: a guest under
     * host_only is a no-op; the screen also disables the affordance, this is
     * the defensive backstop.
     */
    fun onUserPlayPause() {
        if (tvRoomTransportGate(repository.roomSnapshot.value, TvTransportIntent.PlayPause) != TransportGate.Send) {
            return
        }
        val state = viewModel.uiState.value
        val willPause = !state.isPaused
        scope.launch {
            repository.transportRequest(
                action = if (willPause) TransportAction.Pause.wire else TransportAction.Play.wire,
                positionSeconds = state.position,
                isPaused = willPause,
            )
        }
    }

    /** Route a local seek through the room. Guests never seek (gated). */
    fun onUserSeek(positionSeconds: Double) {
        if (tvRoomTransportGate(repository.roomSnapshot.value, TvTransportIntent.Seek) != TransportGate.Send) {
            return
        }
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
