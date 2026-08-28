package org.prairieserver.prairie.tv.ui.components

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
import org.prairieserver.prairie.tv.ui.focus.TvFocusAcquisitionBudgetMillis
import org.prairieserver.prairie.tv.ui.focus.TvObservedFocusResult
import org.prairieserver.prairie.tv.ui.focus.requestFocusUntilObserved

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
 *
 * [reacquireKey] re-runs acquisition when an overlay swaps its own body — a
 * dialog that replaces its form with a progress view renders zero focusables
 * for a while, and the form coming back needs focus again or the overlay is
 * dead to the D-pad. Overlays with one fixed body leave it alone. Re-acquisition
 * checks focus first, so a viewer already inside the overlay is not dragged back
 * to the first row by an unrelated key change.
 *
 * [enabled] suppresses acquisition for a body that has nothing to focus at all
 * (an in-flight "Submitting…" message). Without it those phases spend the whole
 * budget requesting a target that is not in the tree and then ask the focus
 * system to enter an overlay with nothing in it.
 */
@Composable
internal fun rememberTvDialogInitialFocus(
    target: FocusRequester,
    reacquireKey: Any? = Unit,
    enabled: Boolean = true,
): Modifier {
    var overlayHasFocus by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    LaunchedEffect(target, reacquireKey, enabled) {
        if (!enabled || overlayHasFocus) return@LaunchedEffect
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
