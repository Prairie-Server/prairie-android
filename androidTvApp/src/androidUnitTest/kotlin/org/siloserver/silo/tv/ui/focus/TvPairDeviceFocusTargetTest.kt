package org.siloserver.silo.tv.ui.focus

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TvPairDeviceFocusTargetTest {

    private fun target(
        hasCompleted: Boolean = false,
        canEnterCode: Boolean = false,
        canSubmit: Boolean = false,
        isLoading: Boolean = false,
        isSubmitting: Boolean = false,
    ) = tvPairDeviceFocusTarget(hasCompleted, canEnterCode, canSubmit, isLoading, isSubmitting)

    @Test
    fun `the token route has nothing to focus while its automatic lookup runs`() {
        // Deep-linked with a token: no code entry, Check disabled by isLoading.
        assertNull(target(isLoading = true))
    }

    @Test
    fun `when that lookup resolves, focus moves to Approve`() {
        // The bug: completedStatus never changed across this transition, so the
        // effect keyed on it never re-ran and the panel stayed unfocused.
        assertEquals(TvPairDeviceAction.Approve, target(canSubmit = true))
    }

    @Test
    fun `when that lookup fails, focus falls to the re-enabled Check`() {
        assertEquals(TvPairDeviceAction.Check, target(isLoading = false, canSubmit = false))
    }

    @Test
    fun `nothing is focusable while a decision is being submitted`() {
        assertNull(target(isSubmitting = true))
    }

    @Test
    fun `the manual route offers code entry first`() {
        assertEquals(TvPairDeviceAction.EnterCode, target(canEnterCode = true))
    }

    @Test
    fun `a resolved lookup outranks manual code entry`() {
        assertEquals(
            TvPairDeviceAction.Approve,
            target(canEnterCode = true, canSubmit = true),
        )
    }

    @Test
    fun `completion outranks everything`() {
        assertEquals(
            TvPairDeviceAction.Done,
            target(hasCompleted = true, canEnterCode = true, canSubmit = true),
        )
    }

    @Test
    fun `Deny is never the default target`() {
        // It sits next to Approve, one press away. Defaulting focus to the
        // destructive choice would be wrong.
        val everyReachableTarget = listOf(
            target(hasCompleted = true),
            target(canSubmit = true),
            target(canEnterCode = true),
            target(),
            target(isLoading = true),
            target(isSubmitting = true),
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
