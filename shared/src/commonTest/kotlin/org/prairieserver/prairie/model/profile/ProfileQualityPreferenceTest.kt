package org.prairieserver.prairie.model.profile

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ProfileQualityPreferenceTest {
    @Test
    fun canonicalProfileQualityPreferenceStoresWireValuesNotDisplayLabels() {
        assertNull(canonicalProfileQualityPreference(null))
        assertNull(canonicalProfileQualityPreference("   "))
        assertNull(canonicalProfileQualityPreference("Auto"))
        assertEquals("2160p", canonicalProfileQualityPreference("4K"))
        assertEquals("2160p", canonicalProfileQualityPreference("2160p"))
        assertEquals("1080p", canonicalProfileQualityPreference("1080p"))
        assertEquals("720p", canonicalProfileQualityPreference("720p"))
        assertEquals("480p", canonicalProfileQualityPreference("480p"))
        assertEquals("cinema", canonicalProfileQualityPreference(" Cinema "))
    }

    @Test
    fun displayProfileQualityPreferenceAcceptsLegacyWireValues() {
        assertEquals("Auto", displayProfileQualityPreference(null))
        assertEquals("Auto", displayProfileQualityPreference(" "))
        assertEquals("Auto", displayProfileQualityPreference("auto"))
        assertEquals("4K", displayProfileQualityPreference("4k"))
        assertEquals("4K", displayProfileQualityPreference("2160p"))
        assertEquals("1080p", displayProfileQualityPreference("1080p"))
        assertEquals("720p", displayProfileQualityPreference("720p"))
        assertEquals("480p", displayProfileQualityPreference("480p"))
        assertEquals("Cinema", displayProfileQualityPreference(" Cinema "))
    }
}
