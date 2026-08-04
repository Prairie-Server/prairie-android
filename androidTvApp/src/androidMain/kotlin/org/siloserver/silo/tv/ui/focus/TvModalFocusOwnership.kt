package org.siloserver.silo.tv.ui.focus

import androidx.compose.foundation.focusGroup
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import kotlinx.coroutines.delay

/**
 * A modal's focus boundary.
 *
 * `focusGroup()` alone is not one. It prioritises traversal inside the group,
 * but it does not cancel a focus search at the group's edges — so D-pad
 * movement from a boundary control walks straight out of a visually modal
 * surface and into the still-composed page behind it. Cancelling the search on
 * exit is what actually keeps focus inside.
 *
 * Apply to the modal's content root, alongside whatever acquires focus within
 * it. Callers still need [TvRestoreFocusOnModalDismiss] to hand focus back.
 */
@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
internal fun Modifier.tvModalFocusBoundary(): Modifier = this
    .focusGroup()
    // `exit` cancels a focus search that is leaving the group, which is exactly
    // and only what a modal wants. Cancelling up/down/left/right on this node
    // instead would also govern movement between the modal's own controls —
    // trading an escape for a dead D-pad inside the modal. Same idiom as the
    // shell, the audiobook overlay and the player HUD.
    .focusProperties { exit = { FocusRequester.Cancel } }

private const val TvModalRestoreRetryDelayMillis = 60L

internal const val TvModalRestoreMaxAttempts =
    (TvFocusAcquisitionBudgetMillis / TvModalRestoreRetryDelayMillis).toInt()

/**
 * Hand focus back to whatever opened a modal once it closes.
 *
 * Without this the modal's nodes simply disappear and the focus system picks a
 * geometric successor — which is rarely the control the viewer used to open it,
 * and on a dimmed page is often something they cannot even see.
 *
 * Restoration is deliberately driven from the *caller's* scope rather than the
 * modal's: an exit animation keeps the modal's nodes alive after `visible` goes
 * false, so anything hosted inside it cannot reliably outlive its own dismissal.
 *
 * Nothing happens on first composition — a modal that has never been open has
 * nothing to restore, and stealing focus on screen entry is its own bug.
 *
 * [isOpenerFocused] must genuinely observe the opener. A request that merely
 * returns true has not acquired anything, and without observation the retry
 * cannot tell success from an accepted-but-unfocused request — it would keep
 * re-requesting for the whole budget after focus had already landed.
 */
@Composable
internal fun TvRestoreFocusOnModalDismiss(
    visible: Boolean,
    opener: FocusRequester?,
    isOpenerFocused: () -> Boolean,
) {
    var hasBeenVisible by remember { mutableStateOf(false) }

    LaunchedEffect(visible) {
        if (visible) {
            hasBeenVisible = true
            return@LaunchedEffect
        }
        if (!hasBeenVisible || opener == null) return@LaunchedEffect
        hasBeenVisible = false
        requestFocusUntilObserved(
            maxAttempts = TvModalRestoreMaxAttempts,
            awaitAttempt = { delay(TvModalRestoreRetryDelayMillis) },
            requestFocus = opener::requestFocus,
            // The opener sits behind an exit animation that is still tearing
            // the modal down, so early attempts land before it is focusable.
            isFocused = isOpenerFocused,
        )
    }
}
