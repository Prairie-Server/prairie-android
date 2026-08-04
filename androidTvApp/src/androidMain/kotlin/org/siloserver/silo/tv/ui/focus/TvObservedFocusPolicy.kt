package org.siloserver.silo.tv.ui.focus

import kotlin.coroutines.cancellation.CancellationException

internal enum class TvFocusTargetState { NotReady, Ready, Disposed }

internal enum class TvFocusRequestOutcome { Rejected, AcceptedUnobserved, Focused }

internal enum class TvObservedFocusResult { Focused, Exhausted, Disposed }

internal fun observeTvFocusRequest(
    requestAccepted: Boolean,
    isFocused: Boolean,
): TvFocusRequestOutcome = when {
    isFocused -> TvFocusRequestOutcome.Focused
    requestAccepted -> TvFocusRequestOutcome.AcceptedUnobserved
    else -> TvFocusRequestOutcome.Rejected
}

internal suspend fun requestFocusUntilObserved(
    maxAttempts: Int,
    awaitAttempt: suspend () -> Unit,
    targetState: () -> TvFocusTargetState,
    requestFocus: () -> Boolean,
    isFocused: () -> Boolean,
): TvObservedFocusResult {
    require(maxAttempts > 0) { "maxAttempts must be positive" }

    repeat(maxAttempts) {
        awaitAttempt()
        if (isFocused()) return TvObservedFocusResult.Focused

        when (targetState()) {
            TvFocusTargetState.Disposed -> return TvObservedFocusResult.Disposed
            TvFocusTargetState.NotReady -> Unit
            TvFocusTargetState.Ready -> {
                val accepted = runCatching(requestFocus).getOrElse { exception ->
                    if (exception is CancellationException) throw exception
                    false
                }
                if (observeTvFocusRequest(accepted, isFocused()) == TvFocusRequestOutcome.Focused) {
                    return TvObservedFocusResult.Focused
                }
            }
        }
    }

    return when {
        isFocused() -> TvObservedFocusResult.Focused
        targetState() == TvFocusTargetState.Disposed -> TvObservedFocusResult.Disposed
        else -> TvObservedFocusResult.Exhausted
    }
}
