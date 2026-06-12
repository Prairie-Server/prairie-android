package com.continuum.app.tv.ui.screens.library

/**
 * Hardcoded decade-based year filter options. Year isn't returned by
 * `/api/v1/catalog/filters`, but the browse endpoint accepts yearMin /
 * yearMax — so we synthesize a coarse decade picker UI-side.
 *
 * The catch-all "Older" bucket bounds 0..(currentDecade - 50). The
 * "Any" entry is represented by the absence of an option in the picker
 * (clearing via a separate "Clear" button or by re-pressing the selected
 * decade — the caller decides UX).
 */
data class TvLibraryYearOption(
    val id: String,
    val label: String,
    val yearMin: Int,
    val yearMax: Int,
)

object TvLibraryYearOptions {
    /**
     * Returns the standard decade options anchored at [currentYear].
     * Pure function — no system time access — so tests can pin behavior
     * to a known year.
     */
    fun forCurrentYear(currentYear: Int): List<TvLibraryYearOption> {
        val currentDecadeStart = (currentYear / 10) * 10
        val decades = (0..4).map { offset ->
            val start = currentDecadeStart - (offset * 10)
            val end = start + 9
            TvLibraryYearOption(
                id = "decade-${start}",
                label = "${start}s",
                yearMin = start,
                yearMax = end,
            )
        }
        val olderCutoff = currentDecadeStart - 50
        val older = TvLibraryYearOption(
            id = "older",
            label = "Older",
            yearMin = 0,
            yearMax = olderCutoff - 1,
        )
        return decades + older
    }

    /**
     * Reverse lookup — given a (yearMin, yearMax) pair from the filter
     * state, find the matching option. Returns null if no option matches
     * exactly (e.g., custom range, or no year filter).
     */
    fun match(
        currentYear: Int,
        yearMin: Int?,
        yearMax: Int?,
    ): TvLibraryYearOption? {
        if (yearMin == null && yearMax == null) return null
        return forCurrentYear(currentYear).firstOrNull {
            it.yearMin == yearMin && it.yearMax == yearMax
        }
    }
}
