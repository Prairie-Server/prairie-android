package org.siloserver.silo.model.catalog

import kotlin.test.Test
import kotlin.test.assertEquals

class SeasonDisplayOrderTest {
    private fun season(
        number: Int,
        specials: Boolean = false,
        id: String = "season-$number-$specials",
    ) = Season(
        contentId = id,
        seasonNumber = number,
        isSpecials = specials,
    )

    @Test
    fun `specials sort before regular seasons`() {
        val result = listOf(season(2), season(0), season(1)).sortedForDisplay()
        assertEquals(listOf(0, 1, 2), result.map(Season::seasonNumber))
    }

    @Test
    fun `specials flag is authoritative even for nonzero season number`() {
        val result = listOf(season(1), season(99, specials = true), season(2)).sortedForDisplay()
        assertEquals(listOf(99, 1, 2), result.map(Season::seasonNumber))
    }

    @Test
    fun `ordinary opening selects first regular season`() {
        val result = listOf(season(0), season(2), season(1))
            .initialSeasonForDisplay(preferredSeasonNumber = null)
        assertEquals(1, result?.seasonNumber)
    }

    @Test
    fun `requested specials remains selected`() {
        val result = listOf(season(2), season(0), season(1))
            .initialSeasonForDisplay(preferredSeasonNumber = 0)
        assertEquals(0, result?.seasonNumber)
    }

    @Test
    fun `specials-only series selects specials`() {
        val result = listOf(season(0))
            .initialSeasonForDisplay(preferredSeasonNumber = null)
        assertEquals(0, result?.seasonNumber)
    }
}
