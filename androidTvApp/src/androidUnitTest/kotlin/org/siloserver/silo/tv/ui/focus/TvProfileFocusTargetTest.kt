package org.siloserver.silo.tv.ui.focus

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TvProfileFocusTargetTest {

    private val abc = listOf("a", "b", "c", "d")

    @Test
    fun `the first arrival of the list is anchored`() {
        assertEquals(
            "a",
            tvProfileFocusTarget(
                previousIds = emptyList(),
                currentIds = abc,
                focusedId = null,
                hasMaterialized = false,
            ),
        )
    }

    @Test
    fun `deleting the first profile falls to the one that replaced it`() {
        assertEquals(
            "b",
            tvProfileFocusTarget(
                previousIds = abc,
                currentIds = listOf("b", "c", "d"),
                focusedId = "a",
                hasMaterialized = true,
            ),
        )
    }

    @Test
    fun `deleting the only profile anchors nothing`() {
        assertNull(
            tvProfileFocusTarget(
                previousIds = listOf("a"),
                currentIds = emptyList(),
                focusedId = "a",
                hasMaterialized = true,
            ),
        )
    }

    @Test
    fun `adding a profile leaves the focused one alone`() {
        assertEquals(
            "b",
            tvProfileFocusTarget(
                previousIds = abc,
                currentIds = abc + "e",
                focusedId = "b",
                hasMaterialized = true,
            ),
        )
    }

    @Test
    fun `a list that changed but kept the focused profile keeps focus on it`() {
        // Note the caller keys its effect on the ID list, so an *identical*
        // list never reaches here — this is the changed-list case, which is
        // what an edit or a deletion elsewhere in the grid produces.
        assertEquals(
            "c",
            tvProfileFocusTarget(
                previousIds = abc,
                currentIds = listOf("a", "c", "d"),
                focusedId = "c",
                hasMaterialized = true,
            ),
        )
    }

    @Test
    fun `an edited profile keeps focus even after it moves position`() {
        assertEquals(
            "c",
            tvProfileFocusTarget(
                previousIds = abc,
                currentIds = listOf("c", "a", "b", "d"),
                focusedId = "c",
                hasMaterialized = true,
            ),
        )
    }

    @Test
    fun `deleting the focused profile falls to the tile that took its place`() {
        assertEquals(
            "d",
            tvProfileFocusTarget(
                previousIds = abc,
                currentIds = listOf("a", "b", "d"),
                focusedId = "c",
                hasMaterialized = true,
            ),
        )
    }

    @Test
    fun `deleting the last profile falls back to the new last one`() {
        assertEquals(
            "c",
            tvProfileFocusTarget(
                previousIds = abc,
                currentIds = listOf("a", "b", "c"),
                focusedId = "d",
                hasMaterialized = true,
            ),
        )
    }

    @Test
    fun `a refresh with focus elsewhere on the screen is left alone`() {
        // Focus may be on Add Profile, Change Server or Sign Out. A profile
        // reload has no business pulling it into the grid.
        assertNull(
            tvProfileFocusTarget(
                previousIds = abc,
                currentIds = abc,
                focusedId = null,
                hasMaterialized = true,
            ),
        )
    }

    @Test
    fun `an empty list has nothing to anchor`() {
        assertNull(
            tvProfileFocusTarget(
                previousIds = abc,
                currentIds = emptyList(),
                focusedId = "a",
                hasMaterialized = true,
            ),
        )
        assertNull(
            tvProfileFocusTarget(
                previousIds = emptyList(),
                currentIds = emptyList(),
                focusedId = null,
                hasMaterialized = false,
            ),
        )
    }

    @Test
    fun `a focused profile that was never in the previous list is left alone`() {
        // No index to fall back from, so guessing would be worse than nothing.
        assertNull(
            tvProfileFocusTarget(
                previousIds = listOf("a", "b"),
                currentIds = listOf("a", "b"),
                focusedId = "ghost",
                hasMaterialized = true,
            ),
        )
    }
}
