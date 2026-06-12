package com.continuum.app.tv.ui.screens.home

import com.continuum.app.model.calendar.CalendarItem
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Unit tests for [resolveUpcoming] — the pure resolution logic that decides
 * which list of [CalendarItem]s to surface in the "Coming this week" TV row.
 *
 * Rules under test:
 *   - following == null (fetch error)  → empty list (hide row)
 *   - following non-empty              → following (All is NOT called)
 *   - following empty (success)        → result of fetch(All)
 *   - following empty + All error      → empty list (hide row)
 */
class TvUpcomingResolveTest {

    // ── helpers ────────────────────────────────────────────────────────────────

    private fun item(id: String) = CalendarItem(
        contentId = id,
        type = "movie",
        title = "Title $id",
        airDate = "2026-06-15",
        localAirDate = "2026-06-15",
    )

    // ── test cases ─────────────────────────────────────────────────────────────

    @Test
    fun `following fetch error hides the row`() = runTest {
        val result = resolveUpcoming(
            following = null,               // null == fetch error
            fetchAll = { error("fetchAll must not be called") },
        )
        assertEquals(emptyList<CalendarItem>(), result)
    }

    @Test
    fun `following non-empty returns following and does not call fetchAll`() = runTest {
        var fetchAllCalled = false
        val followingItems = listOf(item("a"), item("b"))

        val result = resolveUpcoming(
            following = followingItems,
            fetchAll = {
                fetchAllCalled = true
                listOf(item("z"))
            },
        )

        assertEquals(followingItems, result)
        assertEquals(false, fetchAllCalled)
    }

    @Test
    fun `following empty success falls back to All results`() = runTest {
        val allItems = listOf(item("x"), item("y"))

        val result = resolveUpcoming(
            following = emptyList(),        // empty list == success with no items
            fetchAll = { allItems },
        )

        assertEquals(allItems, result)
    }

    @Test
    fun `following empty success with All fetch error hides the row`() = runTest {
        val result = resolveUpcoming(
            following = emptyList(),
            fetchAll = { null },            // null == fetch error on All
        )

        assertEquals(emptyList<CalendarItem>(), result)
    }
}
