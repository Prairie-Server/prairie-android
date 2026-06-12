package com.continuum.app.tv.ui.screens.library

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TvLibraryYearOptionsTest {

    @Test
    fun `forCurrentYear 2026 returns 5 decades plus older`() {
        val options = TvLibraryYearOptions.forCurrentYear(2026)
        assertEquals(6, options.size)
        assertEquals("2020s", options[0].label)
        assertEquals(2020, options[0].yearMin)
        assertEquals(2029, options[0].yearMax)
        assertEquals("1980s", options[4].label)
        assertEquals(1980, options[4].yearMin)
        assertEquals(1989, options[4].yearMax)
        assertEquals("Older", options[5].label)
        assertEquals(0, options[5].yearMin)
        assertEquals(1969, options[5].yearMax)
    }

    @Test
    fun `forCurrentYear 2030 anchors on 2030s`() {
        val options = TvLibraryYearOptions.forCurrentYear(2030)
        assertEquals("2030s", options[0].label)
        assertEquals(2030, options[0].yearMin)
    }

    @Test
    fun `match returns null when no filter set`() {
        assertNull(TvLibraryYearOptions.match(2026, null, null))
    }

    @Test
    fun `match returns the decade option when range matches`() {
        val match = TvLibraryYearOptions.match(2026, 2010, 2019)
        assertEquals("2010s", match?.label)
    }

    @Test
    fun `match returns null when range doesn't align to a decade`() {
        assertNull(TvLibraryYearOptions.match(2026, 1995, 2005))
    }
}
