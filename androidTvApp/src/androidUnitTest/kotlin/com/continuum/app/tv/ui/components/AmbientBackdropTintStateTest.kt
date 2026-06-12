package com.continuum.app.tv.ui.components

import androidx.compose.ui.graphics.Color
import com.continuum.app.model.section.SectionItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AmbientBackdropTintStateTest {

    // SectionItem (see shared/.../SectionModels.kt) is a data class whose
    // only non-defaulted fields are contentId, type, and title. Everything
    // else has a sensible default, so a minimal constructor call is enough
    // for state-holder tests that only care about contentId equality.
    private fun item(id: String): SectionItem = SectionItem(
        contentId = id,
        type = "movie",
        title = "Item $id",
    )

    @Test
    fun `accent updates when result matches current item`() {
        val state = AmbientBackdropTintState()
        val a = item("a")
        state.set(a)
        state.acceptAccent(a, Color.Red)
        assertEquals(Color.Red, state.accent)
        assertEquals(a, state.currentItem)
    }

    @Test
    fun `accent ignored when stale result arrives after item changed`() {
        val state = AmbientBackdropTintState()
        val a = item("a")
        val b = item("b")
        state.set(a)
        state.set(b)
        state.acceptAccent(a, Color.Red)
        assertNull(state.accent)
        assertEquals(b, state.currentItem)
    }

    @Test
    fun `setting null item clears current`() {
        val state = AmbientBackdropTintState()
        state.set(item("a"))
        state.set(null)
        assertNull(state.currentItem)
    }
}
