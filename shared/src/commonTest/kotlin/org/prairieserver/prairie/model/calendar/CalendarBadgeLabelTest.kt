package org.prairieserver.prairie.model.calendar

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.prairieserver.prairie.common.i18n.UiText
import org.prairieserver.prairie.common.i18n.SharedString

class CalendarBadgeLabelTest {

    @Test
    fun `labels every badge the server sends`() {
        assertEquals(
            listOf(
                SharedString.CalendarSeriesPremiere, SharedString.CalendarSeasonPremiere, SharedString.CalendarFinale,
                SharedString.CalendarDigital, SharedString.CalendarPhysical,
            ).map { UiText.Key(it) },
            listOf("series_premiere", "season_premiere", "finale", "digital", "physical")
                .map(::calendarBadgeLabel),
        )
    }

    @Test
    fun `hides a badge it does not know`() {
        assertNull(calendarBadgeLabel("theatrical"))
    }
}
