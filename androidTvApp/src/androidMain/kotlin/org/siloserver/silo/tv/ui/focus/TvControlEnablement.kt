package org.siloserver.silo.tv.ui.focus

import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics

/**
 * Disabled TV controls come in two kinds, and they must not be wired alike.
 *
 * **Structural** unavailability — the action can never apply in this context
 * (a selector with one choice, "Debug logging" under consent NEVER, an option
 * row for a track the file does not carry). Those belong out of the focus
 * graph: leaving them focusable makes the D-pad walk through dead stops.
 *
 * **Transient** gating — a request is in flight, or a form is still being
 * filled. Those must STAY in the focus graph. Android TV does not re-home
 * focus when the focused node stops being focusable: the ring simply
 * disappears and the D-pad goes dead until something requests focus again.
 * Nothing does, because the initial-focus policies are one-shot. In a modal
 * whose controls are all busy-gated (the PIN keypad, the join-code grid) that
 * strands the viewer with Back as the only working key — which is the very
 * failure this focus work exists to remove.
 */
internal data class TvControlState(
    /** Whether the control takes part in D-pad focus traversal. */
    val focusable: Boolean,
    /** Whether activating the control runs its action. */
    val actionable: Boolean,
) {
    companion object {
        /** The action is unavailable here at all. Drops out of the focus graph. */
        fun structural(isEnabled: Boolean) =
            TvControlState(focusable = isEnabled, actionable = isEnabled)

        /** In-flight work or an evolving form. Stays focusable, action suppressed. */
        fun transient(isEnabled: Boolean) =
            TvControlState(focusable = true, actionable = isEnabled)
    }

    fun perform(action: () -> Unit) {
        if (actionable) action()
    }
}

/**
 * Wire a [TvControlState] to a control: focus participation and truthful
 * accessibility state.
 *
 * Passing `focusable` to a TV component's `enabled` parameter does NOT achieve
 * the focus half, which is the trap this exists to close. `tvClickable` — the
 * shared basis of TV Material's clickable `Surface` and therefore of `Button`
 * — calls `focusable()` with its *default* `enabled = true` and never forwards
 * the component's own `enabled`; that flag reaches only the D-pad-enter handler
 * and the semantics block. A disabled TV button is consequently still a focus
 * stop: it just refuses to activate. (Verified against the tv-material 1.0.1
 * bytecode, not inferred from the API shape.)
 *
 * So structural exclusion has to be asked for explicitly, and on the control's
 * own modifier chain ahead of the component's internal focusable — which is
 * where a `modifier` parameter lands.
 *
 * Every call site predating this used [TvControlState.transient], where
 * `focusable` is always true, so the promise was never tested. It is kept here
 * rather than at each call site so it cannot be forgotten again.
 */
internal fun Modifier.tvControlSemantics(controlState: TvControlState): Modifier =
    tvFocusSuppressed(!controlState.focusable)
        .semantics {
            if (!controlState.actionable) disabled()
        }
