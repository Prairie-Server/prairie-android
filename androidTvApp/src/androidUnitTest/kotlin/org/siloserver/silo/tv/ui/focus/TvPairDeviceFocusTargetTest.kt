package org.siloserver.silo.tv.ui.focus

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * States here mirror `DevicePairingUiState`, whose `canSubmit` is
 * `!isSubmitting && (token or code is non-blank)` — it means "there is an
 * identifier to submit", NOT "the lookup resolved". A token route therefore has
 * canSubmit true from construction, before anything has come back to approve.
 */
class TvPairDeviceFocusTargetTest {

    private fun target(
        hasCompleted: Boolean = false,
        hasResolvedLookup: Boolean = false,
        canEnterCode: Boolean = false,
        canSubmit: Boolean = false,
        isLoading: Boolean = false,
        isSubmitting: Boolean = false,
    ) = tvPairDeviceFocusTarget(
        hasCompleted, hasResolvedLookup, canEnterCode, canSubmit, isLoading, isSubmitting,
    )

    @Test
    fun `a token route offers Check while its automatic lookup is still running`() {
        // canSubmit is already true here purely because a token exists. Treating
        // that as "resolved" would focus Approve before there is anything to
        // approve; Check is disabled mid-lookup, so nothing is focusable.
        assertNull(target(canSubmit = true, isLoading = true))
    }

    @Test
    fun `once the lookup resolves, focus moves to Approve`() {
        // completedStatus does not change across this transition, which is why
        // keying focus on it left the panel unfocused.
        assertEquals(
            TvPairDeviceAction.Approve,
            target(hasResolvedLookup = true, canSubmit = true),
        )
    }

    @Test
    fun `a failed lookup keeps the identifier but falls to the re-enabled Check`() {
        // Error path: lookup stays null, isLoading clears, canSubmit is still
        // true because the token was never discarded.
        assertEquals(
            TvPairDeviceAction.Check,
            target(canSubmit = true, isLoading = false),
        )
    }

    @Test
    fun `nothing is focusable while a decision is being submitted`() {
        // Submission disables every action, and canSubmit is false throughout.
        assertNull(target(hasResolvedLookup = true, isSubmitting = true))
        assertNull(target(canEnterCode = true, isSubmitting = true))
    }

    @Test
    fun `the manual route offers code entry before any lookup exists`() {
        assertEquals(TvPairDeviceAction.EnterCode, target(canEnterCode = true))
    }

    @Test
    fun `a typed code does not become Approve until the lookup resolves`() {
        // canEnterCode and canSubmit are both true once a code is typed.
        assertEquals(
            TvPairDeviceAction.EnterCode,
            target(canEnterCode = true, canSubmit = true),
        )
        assertEquals(
            TvPairDeviceAction.Approve,
            target(canEnterCode = true, canSubmit = true, hasResolvedLookup = true),
        )
    }

    @Test
    fun `completion outranks everything`() {
        assertEquals(
            TvPairDeviceAction.Done,
            target(
                hasCompleted = true,
                hasResolvedLookup = true,
                canEnterCode = true,
                canSubmit = true,
            ),
        )
    }

    @Test
    fun `Deny is never the default target`() {
        // It sits next to Approve, one press away. Defaulting focus to the
        // destructive choice would be wrong.
        val everyReachableTarget = listOf(
            target(hasCompleted = true),
            target(hasResolvedLookup = true, canSubmit = true),
            target(canEnterCode = true),
            target(canSubmit = true),
            target(canSubmit = true, isLoading = true),
            // canSubmit is false while submitting, by construction.
            target(hasResolvedLookup = true, isSubmitting = true),
        )
        assertEquals(
            listOf(
                TvPairDeviceAction.Done,
                TvPairDeviceAction.Approve,
                TvPairDeviceAction.EnterCode,
                TvPairDeviceAction.Check,
                null,
                null,
            ),
            everyReachableTarget,
        )
    }
}
