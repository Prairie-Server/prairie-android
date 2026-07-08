package org.siloserver.silo.tv.ui.screens.calendar

import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TvCalendarSizingSourceTest {
    private val source = File(
        "src/androidMain/kotlin/org/siloserver/silo/tv/ui/screens/calendar/TvCalendarScreen.kt",
    ).readText()

    @Test
    fun calendarCellsUseLandscapePosterPlusTextLayout() {
        // QA 2026-07-08: the portrait poster + caption-below stack was too
        // tall for the day shelves — cells are now a landscape row (small
        // poster left, text beside) with fixed tokens.
        assertTrue(source.contains("private val posterWidth = 96.dp"))
        assertTrue(source.contains("private val posterHeight = 144.dp"))
        assertTrue(source.contains("private val cellWidth = 400.dp"))
        // Meaningless midnight timestamps stay hidden.
        assertTrue(source.contains("it.isNotBlank() && it != \"00:00\""))
    }

    @Test
    fun calendarShelvesUseHalfScaleTvosCardSpacing() {
        assertTrue(source.contains("private val CalendarCardSpacing = 18.dp"))
        assertTrue(source.contains("horizontalArrangement = Arrangement.spacedBy(CalendarCardSpacing)"))
    }
}
