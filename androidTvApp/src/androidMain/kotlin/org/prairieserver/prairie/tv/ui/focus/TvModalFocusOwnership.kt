package org.prairieserver.prairie.tv.ui.focus

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

/**
 * Take a control out of D-pad traversal while a modal owns focus.
 *
 * The containment in [tvModalFocusBoundary] only holds focus that is already
 * inside the modal. It does nothing about focus that never got in — and an
 * in-window overlay leaves the surface underneath fully composed and fully
 * focusable, so a covered transport button keeps taking Select while a panel is
 * open in front of it.
 *
 * Apply on the same modifier chain as the control's own focusable, ahead of it.
 * An ancestor works too, but only conditionally: a focus target resolves its
 * properties by walking up and applying each `FocusPropertiesModifierNode` it
 * finds, stopping at the first ancestor that is itself a focus target. Anything
 * introducing one in between — a `focusGroup()`, a TV `Surface`, another
 * `focusable()` — swallows the suppression before it arrives, silently. Placing
 * it on the control's own chain has no such dependency on what sits above.
 *
 * (`focusGroup()` is not that mechanism, despite looking like it: it deactivates
 * its own target through `Focusability.Never` rather than through an inherited
 * focus property, which is exactly why a focus group's children stay focusable.)
 *
 * Suppression alone can strand the viewer. Android TV does not re-home focus
 * when the focused node stops being focusable: the ring disappears and the
 * D-pad goes dead until something requests focus. So every surface that
 * suppresses must also give focus somewhere to land — the modal on the way in
 * (see the dialog initial-focus adapter) and [TvRestoreFocusOnModalDismiss] on
 * the way out.
 */
internal fun Modifier.tvFocusSuppressed(suppressed: Boolean): Modifier =
    if (suppressed) focusProperties { canFocus = false } else this

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
