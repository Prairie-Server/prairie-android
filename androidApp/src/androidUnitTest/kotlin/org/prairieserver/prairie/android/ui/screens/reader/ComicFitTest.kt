package org.prairieserver.prairie.android.ui.screens.reader
import kotlin.test.Test
import kotlin.test.assertEquals
class ComicFitTest {
    @Test fun `fit width scales to viewport width`() {
        assertEquals(2.0f, comicFitScale(1000, 2000, 2000, 3000, ComicFitMode.Width), 1e-4f)
    }
    @Test fun `fit height scales to viewport height`() {
        assertEquals(1.5f, comicFitScale(1000, 2000, 2000, 3000, ComicFitMode.Height), 1e-4f)
    }
    @Test fun `fit screen uses the smaller axis`() {
        assertEquals(1.5f, comicFitScale(1000, 2000, 2000, 3000, ComicFitMode.Screen), 1e-4f)
    }
    @Test fun `original is unscaled`() {
        assertEquals(1.0f, comicFitScale(1000, 2000, 2000, 3000, ComicFitMode.Original), 1e-4f)
    }
    @Test fun `non-positive dimensions degrade to 1`() {
        assertEquals(1.0f, comicFitScale(0, 0, 100, 100, ComicFitMode.Width), 1e-4f)
    }
}
