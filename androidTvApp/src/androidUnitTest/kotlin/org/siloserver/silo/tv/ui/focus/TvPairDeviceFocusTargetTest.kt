package org.siloserver.silo.tv.ui.focus

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Focus here is keyed on which control deserves focus, not on which control
 * happens to be enabled. The panel keeps something focusable in every state —
 * Check is gated transiently — so there is always an answer.
 *
 * Deny is absent from [TvPairDeviceAction] entirely rather than merely
 * unreachable, so "focus never defaults to the destructive choice" is a
 * compile-time property and not something asserted here.
 */
class TvPairDeviceFocusTargetTest {

    private fun target(
        hasCompleted: Boolean = false,
        hasResolvedLookup: Boolean = false,
        canEnterCode: Boolean = false,
    ) = tvPairDeviceFocusTarget(hasCompleted, hasResolvedLookup, canEnterCode)

    @Test
    fun `a token route waits on Check until its automatic lookup resolves`() {
        // The deep-link route has no code to enter and nothing to approve yet.
        // Check is the only control on screen, and it stays focusable while its
        // own lookup runs — which is why this is Check and not "nothing".
        assertEquals(TvPairDeviceAction.Check, target())
    }

    @Test
    fun `a resolved lookup moves focus to Approve`() {
        assertEquals(TvPairDeviceAction.Approve, target(hasResolvedLookup = true))
    }

    @Test
    fun `a failed lookup falls back to Check rather than to Approve`() {
        // The error path clears the lookup while the token stays set. Keying on
        // the identifier instead put focus on an Approve that could act on a
        // request the server had just rejected.
        assertEquals(TvPairDeviceAction.Check, target(hasResolvedLookup = false))
    }

    @Test
    fun `the manual route offers code entry before any lookup resolves`() {
        assertEquals(TvPairDeviceAction.EnterCode, target(canEnterCode = true))
    }

    @Test
    fun `a resolved lookup outranks code entry`() {
        assertEquals(
            TvPairDeviceAction.Approve,
            target(hasResolvedLookup = true, canEnterCode = true),
        )
    }

    @Test
    fun `completion outranks everything`() {
        assertEquals(
            TvPairDeviceAction.Done,
            target(hasCompleted = true, hasResolvedLookup = true, canEnterCode = true),
        )
    }
}
