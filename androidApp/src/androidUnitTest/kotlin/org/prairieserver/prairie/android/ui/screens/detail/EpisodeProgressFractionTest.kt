package org.prairieserver.prairie.android.ui.screens.detail

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class EpisodeProgressFractionTest {

    @Test
    fun returnsProgressForRewatchPosition() {
        assertEquals(0.25f, episodeProgressFraction(900.0, 3600.0))
    }

    @Test
    fun hidesZeroAndStaleCompletedProgress() {
        assertNull(episodeProgressFraction(0.0, 3600.0))
        assertNull(episodeProgressFraction(3600.0, 3600.0))
    }
}
