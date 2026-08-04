package org.siloserver.silo.tv.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalFocusManager
import kotlinx.coroutines.delay
import org.siloserver.silo.tv.ui.focus.TvFocusAcquisitionBudgetMillis
import org.siloserver.silo.tv.ui.focus.TvObservedFocusResult
import org.siloserver.silo.tv.ui.focus.requestFocusUntilObserved

private const val TvDialogInitialFocusRetryDelayMillis = 60L

internal const val TvDialogInitialFocusMaxAttempts =
    (TvFocusAcquisitionBudgetMillis / TvDialogInitialFocusRetryDelayMillis).toInt()

internal suspend fun requestTvDialogInitialFocus(
    awaitAttempt: suspend () -> Unit,
    isOverlayFocused: () -> Boolean,
    requestFocus: () -> Boolean,
): TvObservedFocusResult = requestFocusUntilObserved(
    maxAttempts = TvDialogInitialFocusMaxAttempts,
    awaitAttempt = awaitAttempt,
    requestFocus = requestFocus,
    isFocused = isOverlayFocused,
)

/**
 * Bounded retry-until-observed initial focus for popup overlays.
 *
 * Attach the returned modifier to the overlay content root. Focus on any child
 * completes acquisition; the retry cadence divides
 * [TvFocusAcquisitionBudgetMillis] into fixed attempts. Leaving composition
 * cancels the effect through structured concurrency.
 *
 * Exhausting the budget must not end in a dead D-pad, which is the failure the
 * whole policy exists to prevent — so a last resort asks the focus system to
 * enter the overlay by traversal. That works even when [target] never became
 * focusable (an all-disabled option list, a control that left the graph while
 * the request was in flight), which is exactly when the retries run out.
 */
@Composable
internal fun rememberTvDialogInitialFocus(target: FocusRequester): Modifier {
    var overlayHasFocus by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    LaunchedEffect(target) {
        val result = requestTvDialogInitialFocus(
            awaitAttempt = { delay(TvDialogInitialFocusRetryDelayMillis) },
            isOverlayFocused = { overlayHasFocus },
            requestFocus = target::requestFocus,
        )
        if (result == TvObservedFocusResult.Exhausted && !overlayHasFocus) {
            runCatching { focusManager.moveFocus(FocusDirection.Enter) }
        }
    }
    return Modifier.onFocusChanged { overlayHasFocus = it.hasFocus }
}
