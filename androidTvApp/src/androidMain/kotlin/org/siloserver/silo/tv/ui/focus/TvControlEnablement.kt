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

/** Keeps transiently gated controls focusable while exposing truthful accessibility state. */
internal fun Modifier.tvControlSemantics(controlState: TvControlState): Modifier =
    semantics {
        if (!controlState.actionable) disabled()
    }
