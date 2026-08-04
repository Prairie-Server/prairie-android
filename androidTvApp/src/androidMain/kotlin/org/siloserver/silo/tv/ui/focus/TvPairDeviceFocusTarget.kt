package org.siloserver.silo.tv.ui.focus

/** The actions the pair-device panel can offer, in the order it offers them. */
internal enum class TvPairDeviceAction { EnterCode, Check, Approve, Done }

/**
 * The action that should hold focus right now, or `null` when the panel has
 * nothing actionable and focus must wait.
 *
 * Focus eligibility here is driven entirely by asynchronous state — a token
 * route starts its lookup automatically, which disables Check while it runs and
 * enables Approve/Deny when it resolves — and none of that changes
 * `completedStatus`. Keying initial focus on completion alone therefore
 * requested focus once, against a control that was disabled at that instant,
 * and never asked again: the panel could sit with no focus owner at all.
 *
 * A resolved lookup outranks Check, because approving is what the viewer came
 * to do. Deny is deliberately not a target: it sits beside Approve and is one
 * D-pad press away, and defaulting focus to the destructive choice is wrong.
 */
internal fun tvPairDeviceFocusTarget(
    hasCompleted: Boolean,
    hasResolvedLookup: Boolean,
    canEnterCode: Boolean,
    canSubmit: Boolean,
    isLoading: Boolean,
    isSubmitting: Boolean,
): TvPairDeviceAction? = when {
    hasCompleted -> TvPairDeviceAction.Done
    // canSubmit only means an identifier exists and nothing is being submitted
    // — on a token route it is true from construction, before the lookup has
    // returned anything to approve. The resolved lookup is the real signal.
    hasResolvedLookup && canSubmit -> TvPairDeviceAction.Approve
    canEnterCode && !isSubmitting -> TvPairDeviceAction.EnterCode
    // Check is the only remaining control, and only while it is enabled.
    !isLoading && !isSubmitting -> TvPairDeviceAction.Check
    else -> null
}
