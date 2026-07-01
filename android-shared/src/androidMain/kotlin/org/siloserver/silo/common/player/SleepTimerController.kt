package org.siloserver.silo.common.player

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Session-scoped sleep timer for the Android player.
 *
 * Mirrors iOS `SleepTimer.swift`:
 *  - `start(minutes)` (re)schedules a fire after `minutes * 60` seconds, ticking
 *    [state] down each second.
 *  - `cancel()` returns to [SleepTimerState.Idle] without firing the callback.
 *  - `configure(onFire)` installs the side-effect (typically `mediaController.pause()`).
 *
 * The controller is android-only because the on-fire callback is dispatched on
 * the same single-thread context that drives [androidx.media3.session.MediaController];
 * pinning the scope to `Dispatchers.Main.immediate` (see PlayerInfraModule) keeps
 * pause invocation on the main thread without an extra hop.
 *
 * State mutations are serialised through a [Mutex] so a user-initiated [cancel]
 * cannot race with the natural-fire path that flips state to [SleepTimerState.Idle]
 * and invokes the callback.
 */
class SleepTimerController(
    private val scope: CoroutineScope,
) {
    private val _state = MutableStateFlow<SleepTimerState>(SleepTimerState.Idle)
    val state: StateFlow<SleepTimerState> = _state.asStateFlow()

    private val mutex = Mutex()
    private var tickJob: Job? = null
    private var onFireCallback: () -> Unit = {}

    /**
     * Install the on-fire callback. The caller wires this to e.g.
     * `_uiState.update { it.copy(isPaused = true) }`. Calling configure
     * replaces any previously installed callback.
     */
    fun configure(onFire: () -> Unit) {
        onFireCallback = onFire
    }

    /**
     * Start (or restart) the timer. `minutes <= 0` is equivalent to [cancel].
     */
    fun start(minutes: Int) {
        if (minutes <= 0) {
            cancel()
            return
        }
        scope.launch {
            mutex.withLock {
                tickJob?.cancel()
                tickJob = null
                _state.value = SleepTimerState.Active(minutes * 60)
            }
            // Launch the tick loop *after* releasing the mutex so cancel() can
            // grab it. The loop re-acquires the mutex per iteration to keep
            // state mutations serialised.
            val job = scope.launch {
                while (true) {
                    delay(1_000L)
                    var fire = false
                    mutex.withLock {
                        val current = _state.value
                        if (current !is SleepTimerState.Active) return@launch
                        val next = current.remainingSeconds - 1
                        if (next <= 0) {
                            _state.value = SleepTimerState.Idle
                            tickJob = null
                            fire = true
                        } else {
                            _state.value = SleepTimerState.Active(next)
                        }
                    }
                    if (fire) {
                        onFireCallback()
                        return@launch
                    }
                }
            }
            mutex.withLock {
                // If a concurrent cancel raced in between releasing the mutex
                // above and acquiring it here, the state is already Idle —
                // discard the freshly launched job in that case so we don't
                // resurrect the timer.
                if (_state.value is SleepTimerState.Active) {
                    tickJob = job
                } else {
                    job.cancel()
                }
            }
        }
    }

    /**
     * Cancel the active timer (if any). No-op when already idle.
     * The on-fire callback is NOT invoked.
     */
    fun cancel() {
        scope.launch {
            mutex.withLock {
                tickJob?.cancel()
                tickJob = null
                _state.value = SleepTimerState.Idle
            }
        }
    }
}

/**
 * Sleep-timer state — either idle or counting down.
 *
 * The countdown is exposed in seconds so the UI chip can render a 1Hz
 * "23m 17s"-style label without doing further math.
 */
sealed interface SleepTimerState {
    data object Idle : SleepTimerState
    data class Active(val remainingSeconds: Int) : SleepTimerState
}
