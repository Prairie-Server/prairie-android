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
    fun calendarCardsUseSharedTvosPosterTokens() {
        assertTrue(source.contains("import org.siloserver.silo.tv.ui.theme.RowDimens"))
        assertTrue(source.contains("private val cardWidth = RowDimens.PosterWidth"))
        assertTrue(source.contains("private val cardHeight = RowDimens.PosterHeight"))
        assertFalse(source.contains("private val cardWidth = 200.dp"))
    }

    @Test
    fun calendarShelvesUseHalfScaleTvosCardSpacing() {
        assertTrue(source.contains("private val CalendarCardSpacing = 20.dp"))
        assertTrue(source.contains("horizontalArrangement = Arrangement.spacedBy(CalendarCardSpacing)"))
    }
}
