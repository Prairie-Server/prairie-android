package org.prairieserver.prairie.tv.ui.components

import androidx.compose.ui.graphics.Color
import org.prairieserver.prairie.model.section.SectionItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AmbientBackdropTintStateTest {

    // SectionItem (see shared/.../SectionModels.kt) is a data class whose
    // only non-defaulted fields are contentId, type, and title. Everything
    // else has a sensible default, so a minimal constructor call is enough
    // for state-holder tests. The tint now keys on the effective backdrop
    // URL, so give each item a distinct backdrop.
    private fun item(id: String): SectionItem = SectionItem(
        contentId = id,
        type = "movie",
        title = "Item $id",
        backdropUrl = "https://art/$id.jpg",
    )

    @Test
    fun `accent updates when result matches current backdrop`() {
        val state = AmbientBackdropTintState()
        val a = item("a")
        state.set(a)
        state.acceptAccent("https://art/a.jpg", Color.Red)
        assertEquals(Color.Red, state.accent)
        assertEquals(a, state.currentItem)
    }

    @Test
    fun `accent ignored when stale result arrives after backdrop changed`() {
        val state = AmbientBackdropTintState()
        val a = item("a")
        val b = item("b")
        state.set(a)
        state.set(b)
        state.acceptAccent("https://art/a.jpg", Color.Red)
        assertNull(state.accent)
        assertEquals(b, state.currentItem)
    }

    @Test
    fun `accent samples the explicit effective url overriding the item backdrop`() {
        val state = AmbientBackdropTintState()
        val a = item("a")
        // Episode upgraded to its series backdrop after enrichment: Home passes
        // the effective (enriched) url, which must be the key the accent uses.
        state.set(a, url = "https://art/series.jpg")
        // A late sample of the item's OWN backdrop is stale and must be ignored.
        state.acceptAccent("https://art/a.jpg", Color.Red)
        assertNull(state.accent)
        state.acceptAccent("https://art/series.jpg", Color.Green)
        assertEquals(Color.Green, state.accent)
    }

    @Test
    fun `setting null item clears current`() {
        val state = AmbientBackdropTintState()
        state.set(item("a"))
        state.set(null)
        assertNull(state.currentItem)
    }
}
