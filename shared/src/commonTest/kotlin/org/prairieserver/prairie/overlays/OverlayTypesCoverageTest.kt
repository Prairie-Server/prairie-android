package org.prairieserver.prairie.overlays

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class OverlayTypesCoverageTest {

    @Test
    fun accentPaletteCategoriesAndPresets() {
        assertEquals(12, OverlayAccentPalette.entries.size)
        assertEquals("Gold", OverlayAccentPalette.entries.first().label)
        assertTrue(OverlayAccentPalette.entries.all { it.hex.startsWith("#") })

        for (category in OverlayCategory.entries) {
            assertTrue(category.displayName.isNotBlank())
            assertTrue(category.description.isNotBlank())
            assertNotNull(category.raw)
        }
        assertEquals(OverlayCategory.Tech, OverlayCategory.entries.first { it.raw == "tech" })

        for (preset in PresetId.entries) {
            assertTrue(preset.label.isNotBlank())
            assertTrue(preset.description.isNotBlank())
        }
        assertEquals(PresetId.Classic, PresetId.fromRaw("classic"))
        assertEquals(null, PresetId.fromRaw(null))
        assertEquals(null, PresetId.fromRaw("nope"))

        for (strategy in AccentStrategy.entries) {
            assertTrue(strategy.raw.isNotBlank())
        }
    }
}
