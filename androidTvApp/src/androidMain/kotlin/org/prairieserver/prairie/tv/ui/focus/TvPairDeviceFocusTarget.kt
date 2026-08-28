package org.prairieserver.prairie.tv.ui.focus

/** The actions the pair-device panel can offer, in the order it offers them. */
internal enum class TvPairDeviceAction { EnterCode, Check, Approve, Done }

/**
 * The action that should hold focus right now.
 *
 * This answers "which control deserves focus", not "which control is
 * focusable", and the return is not nullable because every incomplete state
 * renders Check and every completed one renders Done.
 *
 * An earlier version returned null while a token lookup was running, on the
 * theory that the disabled Check had left the focus graph and nothing was
 * focusable. It had not: TV Material keeps disabled buttons focusable. So the
 * null was never a real state — it just meant focus was left whereever
 * traversal put it, including on controls that could not be used.
 *
 * A resolved lookup outranks Check, because approving is what the viewer came
 * to do. It is keyed on the lookup rather than on whether approving is
 * *currently* actionable, so submitting a decision — which briefly makes it
 * un-actionable — does not move the target out from under the viewer.
 *
 * Deny is deliberately never a target: it sits beside Approve and is one D-pad
 * press away, and defaulting focus to the destructive choice is wrong.
 */
internal fun tvPairDeviceFocusTarget(
    hasCompleted: Boolean,
    hasResolvedLookup: Boolean,
    canEnterCode: Boolean,
): TvPairDeviceAction = when {
    hasCompleted -> TvPairDeviceAction.Done
    hasResolvedLookup -> TvPairDeviceAction.Approve
    canEnterCode -> TvPairDeviceAction.EnterCode
    // Check is rendered in every incomplete state and gated transiently, so it
    // is always both present and focusable — which is what makes this total.
    else -> TvPairDeviceAction.Check
}
