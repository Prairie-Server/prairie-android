package org.prairieserver.prairie.overlays

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class OverlayEnumLabelsTest {
    @Test
    fun positionsCategoriesAndPresetsHaveLabels() {
        assertEquals(listOf("Top Left", "Top Right", "Bottom Left", "Bottom Right"), OverlayPosition.entries.map { it.displayName })
        assertEquals(OverlayPosition.BottomRight, OverlayPosition.fromRaw("bottom-right"))
        assertNull(OverlayPosition.fromRaw("middle"))

        assertEquals(listOf("Media Info", "Ratings", "Metadata", "Ribbons"), OverlayCategory.entries.map { it.displayName })
        assertTrue(OverlayCategory.entries.all { it.description.isNotBlank() })

        assertEquals(listOf("Minimal", "Classic", "Vibrant", "Pill", "Square"), PresetId.entries.map { it.label })
        assertTrue(PresetId.entries.all { it.description.isNotBlank() })
        assertEquals(PresetId.Square, PresetId.fromRaw("square"))
        assertNull(PresetId.fromRaw(null))

        assertEquals(OverlayId.RtCertifiedFresh, OverlayId.fromRaw("rt_certified_fresh"))
        assertNull(OverlayId.fromRaw("unknown"))
        assertEquals(listOf("background", "border", "text", "dot"), AccentStrategy.entries.map { it.raw })
    }
}
