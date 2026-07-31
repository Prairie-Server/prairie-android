package org.prairieserver.prairie.tv.ui.components

import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import kotlin.test.Test
import kotlin.test.assertEquals

class TvOptionDialogPositionProviderTest {

    @Test
    fun `centers tall episode actions in the window regardless of trigger position`() {
        val provider = TvOptionDialogWindowPositionProvider()
        val window = IntSize(width = 1920, height = 1080)
        val popup = IntSize(width = 600, height = 680)

        val nearTop = provider.calculatePosition(
            anchorBounds = IntRect(left = 80, top = 100, right = 240, bottom = 180),
            windowSize = window,
            layoutDirection = LayoutDirection.Ltr,
            popupContentSize = popup,
        )
        val nearBottom = provider.calculatePosition(
            anchorBounds = IntRect(left = 900, top = 850, right = 1100, bottom = 950),
            windowSize = window,
            layoutDirection = LayoutDirection.Ltr,
            popupContentSize = popup,
        )

        assertEquals(IntOffset(x = 660, y = 200), nearTop)
        assertEquals(nearTop, nearBottom)
    }

    @Test
    fun `keeps oversized dialog origin inside the viewport`() {
        val provider = TvOptionDialogWindowPositionProvider()

        val position = provider.calculatePosition(
            anchorBounds = IntRect(left = 400, top = 700, right = 700, bottom = 800),
            windowSize = IntSize(width = 1280, height = 720),
            layoutDirection = LayoutDirection.Rtl,
            popupContentSize = IntSize(width = 1400, height = 800),
        )

        assertEquals(IntOffset.Zero, position)
    }
}
