package org.prairieserver.prairie.tv.ui.screens.watchtogether

import kotlin.test.Test
import kotlin.test.assertEquals

class TvWatchTogetherMenuInitialActionTest {
    @Test
    fun initialActionPrefersResumeOnlyWhenAvailable() {
        assertEquals(
            TvWatchTogetherMenuInitialAction.Resume,
            tvWatchTogetherMenuInitialAction(canResume = true),
        )
        assertEquals(
            TvWatchTogetherMenuInitialAction.Host,
            tvWatchTogetherMenuInitialAction(canResume = false),
        )
    }
}
