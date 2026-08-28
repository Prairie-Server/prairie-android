package org.prairieserver.prairie.tv.ui.focus

import kotlin.coroutines.cancellation.CancellationException

/**
 * Two budgets, because the two situations fail differently.
 *
 * [TvFocusAcquisitionBudgetMillis] covers "nothing is focused yet" — a popup
 * that has just opened. Exhausting it leaves a dead D-pad, so it is generous.
 *
 * [TvFocusRelocationBudgetMillis] covers "focus is already somewhere usable and
 * we are trying to move it somewhere better" — a detail return, a settings page
 * preferring the selected row. Exhausting it degrades to a working fallback, so
 * it is short: a long relocation budget just means seconds of focus thrash.
 */
internal const val TvFocusAcquisitionBudgetMillis = 2_400L
internal const val TvFocusRelocationBudgetMillis = 480L

/** `withFrameNanos` cadence on a 60 Hz panel, used to size frame-based budgets. */
internal const val TvApproximateFrameMillis = 16L

/** Frame-paced attempts that fit inside [TvFocusRelocationBudgetMillis]. */
internal const val TvFrameRelocationMaxAttempts =
    (TvFocusRelocationBudgetMillis / TvApproximateFrameMillis).toInt()

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
    requestFocus: () -> Boolean,
    isFocused: () -> Boolean,
    // Callers with no attach/detach signal to offer (a popup's own content root
    // is composed for as long as the effect runs) leave this at Ready.
    targetState: () -> TvFocusTargetState = { TvFocusTargetState.Ready },
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
