package org.prairieserver.prairie.tv.ui.components

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TvImeAwareFormTest {

    @Test
    fun `relocation requires focus visible IME and measured field`() {
        assertNull(
            tvImeRelocationKey(
                hasFocus = false,
                imeBottomPx = 720,
                fieldWidthPx = 640,
                fieldHeightPx = 112,
            ),
        )
        assertNull(
            tvImeRelocationKey(
                hasFocus = true,
                imeBottomPx = 0,
                fieldWidthPx = 640,
                fieldHeightPx = 112,
            ),
        )
        assertNull(
            tvImeRelocationKey(
                hasFocus = true,
                imeBottomPx = 720,
                fieldWidthPx = 0,
                fieldHeightPx = 112,
            ),
        )
        assertNull(
            tvImeRelocationKey(
                hasFocus = true,
                imeBottomPx = 720,
                fieldWidthPx = 640,
                fieldHeightPx = 0,
            ),
        )

        assertEquals(
            TvImeRelocationKey(
                imeBottomPx = 720,
                fieldWidthPx = 640,
                fieldHeightPx = 112,
            ),
            tvImeRelocationKey(
                hasFocus = true,
                imeBottomPx = 720,
                fieldWidthPx = 640,
                fieldHeightPx = 112,
            ),
        )
    }

    @Test
    fun `IME or field geometry changes create a new relocation key`() {
        val original = tvImeRelocationKey(
            hasFocus = true,
            imeBottomPx = 720,
            fieldWidthPx = 640,
            fieldHeightPx = 112,
        )
        val duplicate = tvImeRelocationKey(
            hasFocus = true,
            imeBottomPx = 720,
            fieldWidthPx = 640,
            fieldHeightPx = 112,
        )
        val resizedIme = tvImeRelocationKey(
            hasFocus = true,
            imeBottomPx = 760,
            fieldWidthPx = 640,
            fieldHeightPx = 112,
        )
        val resizedField = tvImeRelocationKey(
            hasFocus = true,
            imeBottomPx = 720,
            fieldWidthPx = 640,
            fieldHeightPx = 120,
        )

        assertEquals(original, duplicate)
        assertNotEquals(original, resizedIme)
        assertNotEquals(original, resizedField)
    }

    @Test
    fun `scroll restores only when a visible IME closes`() {
        assertTrue(shouldRestoreTvImeFormScroll(previousImeBottomPx = 720, currentImeBottomPx = 0))
        assertFalse(shouldRestoreTvImeFormScroll(previousImeBottomPx = 0, currentImeBottomPx = 0))
        assertFalse(shouldRestoreTvImeFormScroll(previousImeBottomPx = 0, currentImeBottomPx = 720))
        assertFalse(shouldRestoreTvImeFormScroll(previousImeBottomPx = 720, currentImeBottomPx = 760))
    }
}
