package org.prairieserver.prairie.domain.player

import org.prairieserver.prairie.model.catalog.TimeRange
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

sealed interface IntroAutoSkipState {
    data object Hidden : IntroAutoSkipState
    data object ShowingButton : IntroAutoSkipState
    data class CountingDown(val secondsRemaining: Int) : IntroAutoSkipState
}

class IntroAutoSkipController(
    private val scope: CoroutineScope,
    private val countdownSeconds: Int = 5,
) {
    private val _state = MutableStateFlow<IntroAutoSkipState>(IntroAutoSkipState.Hidden)
    val state: StateFlow<IntroAutoSkipState> = _state.asStateFlow()

    private val cancelledKeys = mutableSetOf<String>()
    private var countdownJob: Job? = null
    private var activeKey: String? = null

    fun observe(
        position: Flow<Double>,
        introRange: Flow<TimeRange?>,
        autoSkipEnabled: Flow<Boolean>,
        introKey: Flow<String?>,
        onAutoSkipFire: suspend (toSeconds: Double) -> Unit,
    ): Job {
        return scope.launch {
            combine(position, introRange, autoSkipEnabled, introKey) { pos, range, enabled, key ->
                Inputs(pos, range, enabled, key)
            }
                .distinctUntilChanged()
                .collect { handle(it, onAutoSkipFire) }
        }
    }

    fun cancelCountdown() {
        val key = activeKey ?: return
        cancelledKeys.add(key)
        countdownJob?.cancel()
        countdownJob = null
        _state.value = IntroAutoSkipState.ShowingButton
    }

    fun reset() {
        cancelledKeys.clear()
        countdownJob?.cancel()
        countdownJob = null
        activeKey = null
        _state.value = IntroAutoSkipState.Hidden
    }

    private suspend fun handle(
        inputs: Inputs,
        onAutoSkipFire: suspend (toSeconds: Double) -> Unit,
    ) {
        val (pos, range, enabled, key) = inputs

        val insideRange = range != null &&
            key != null &&
            pos >= range.start &&
            pos < range.end

        if (!insideRange) {
            if (countdownJob != null) {
                countdownJob?.cancel()
                countdownJob = null
            }
            activeKey = null
            if (_state.value !is IntroAutoSkipState.Hidden) {
                _state.value = IntroAutoSkipState.Hidden
            }
            return
        }

        // insideRange ⇒ range and key non-null
        val safeRange = range!!
        val safeKey = key!!

        // If the active key changed, drop any in-flight countdown.
        if (activeKey != null && activeKey != safeKey) {
            countdownJob?.cancel()
            countdownJob = null
        }
        activeKey = safeKey

        val isCancelled = safeKey in cancelledKeys
        if (!enabled || isCancelled) {
            if (countdownJob != null) {
                countdownJob?.cancel()
                countdownJob = null
            }
            if (_state.value !is IntroAutoSkipState.ShowingButton) {
                _state.value = IntroAutoSkipState.ShowingButton
            }
            return
        }

        // Auto-skip enabled, key not cancelled — start countdown if not already running for this key.
        if (countdownJob?.isActive == true) return
        countdownJob = scope.launch {
            var remaining = countdownSeconds
            while (remaining > 0) {
                _state.value = IntroAutoSkipState.CountingDown(remaining)
                delay(1000L)
                remaining -= 1
            }
            _state.value = IntroAutoSkipState.Hidden
            countdownJob = null
            onAutoSkipFire(safeRange.end)
        }
    }

    private data class Inputs(
        val position: Double,
        val range: TimeRange?,
        val enabled: Boolean,
        val key: String?,
    )
}
