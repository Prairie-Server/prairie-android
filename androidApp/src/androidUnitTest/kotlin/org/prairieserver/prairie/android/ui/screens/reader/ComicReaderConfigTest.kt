package org.prairieserver.prairie.android.ui.screens.reader

import kotlin.test.Test
import kotlin.test.assertEquals

class ComicReaderConfigTest {
    @Test fun `LTR maps left third to previous, right third to next`() {
        val cfg = ComicReaderConfig(direction = ReadingDirection.LeftToRight)
        assertEquals(ComicTapAction.Previous, cfg.tapAction(0.1f))
        assertEquals(ComicTapAction.ToggleChrome, cfg.tapAction(0.5f))
        assertEquals(ComicTapAction.Next, cfg.tapAction(0.9f))
    }

    @Test fun `RTL inverts left and right thirds`() {
        val cfg = ComicReaderConfig(direction = ReadingDirection.RightToLeft)
        assertEquals(ComicTapAction.Next, cfg.tapAction(0.1f))
        assertEquals(ComicTapAction.Previous, cfg.tapAction(0.9f))
    }

    @Test fun `center is always toggle chrome regardless of direction`() {
        assertEquals(ComicTapAction.ToggleChrome, ComicReaderConfig(direction = ReadingDirection.RightToLeft).tapAction(0.5f))
    }
}
