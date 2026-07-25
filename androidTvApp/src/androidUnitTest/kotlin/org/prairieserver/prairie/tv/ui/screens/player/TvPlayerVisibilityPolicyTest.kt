package org.prairieserver.prairie.tv.ui.screens.player

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TvPlayerVisibilityPolicyTest {
    @Test
    fun reconnectSpinnerIsHiddenInPipAndNextUp() {
        assertFalse(shouldShowReconnectSpinner(true, false, true))
        assertFalse(shouldShowReconnectSpinner(true, true, false))
        assertTrue(shouldShowReconnectSpinner(true, false, false))
        assertFalse(shouldShowReconnectSpinner(false, false, false))
    }
}
