package com.continuum.app.android.ui.screens.reader

import kotlin.test.Test
import kotlin.test.assertEquals

class ComicPrefetchTest {
    @Test fun `prefetches a bounded window ahead and behind`() {
        assertEquals(listOf(4, 6, 3, 7), comicPrefetchTargets(current = 5, pageCount = 20, radius = 2, freeRamMb = 256))
    }
    @Test fun `clamps at the edges`() {
        assertEquals(listOf(1), comicPrefetchTargets(current = 0, pageCount = 2, radius = 2, freeRamMb = 256))
    }
    @Test fun `low RAM disables prefetch`() {
        assertEquals(emptyList(), comicPrefetchTargets(current = 5, pageCount = 20, radius = 2, freeRamMb = 60))
    }
    @Test fun `single-page or empty disables prefetch`() {
        assertEquals(emptyList(), comicPrefetchTargets(current = 0, pageCount = 1, radius = 2, freeRamMb = 256))
    }
}
