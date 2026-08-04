package org.siloserver.silo.tv.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.onFocusChanged
import kotlinx.coroutines.delay
import org.siloserver.silo.tv.ui.focus.TvFocusTargetState
import org.siloserver.silo.tv.ui.focus.TvObservedFocusResult
import org.siloserver.silo.tv.ui.focus.requestFocusUntilObserved

internal const val TvDialogInitialFocusMaxAttempts = 40
private const val TvDialogInitialFocusRetryDelayMillis = 60L

internal suspend fun requestTvDialogInitialFocus(
    awaitAttempt: suspend () -> Unit,
    isOverlayFocused: () -> Boolean,
    requestFocus: () -> Boolean,
): TvObservedFocusResult = requestFocusUntilObserved(
    maxAttempts = TvDialogInitialFocusMaxAttempts,
    awaitAttempt = awaitAttempt,
    targetState = { TvFocusTargetState.Ready },
    requestFocus = requestFocus,
    isFocused = isOverlayFocused,
)

/**
 * Bounded retry-until-observed initial focus for popup overlays.
 *
 * Attach the returned modifier to the overlay content root. Focus on any child
 * completes acquisition; forty 60 ms attempts provide a 2.4 second ceiling.
 * Leaving composition cancels the effect through structured concurrency.
 */
@Composable
internal fun rememberTvDialogInitialFocus(target: FocusRequester): Modifier {
    var overlayHasFocus by remember { mutableStateOf(false) }
    LaunchedEffect(target) {
        requestTvDialogInitialFocus(
            awaitAttempt = { delay(TvDialogInitialFocusRetryDelayMillis) },
            isOverlayFocused = { overlayHasFocus },
            requestFocus = target::requestFocus,
        )
    }
    return Modifier.onFocusChanged { overlayHasFocus = it.hasFocus }
}
