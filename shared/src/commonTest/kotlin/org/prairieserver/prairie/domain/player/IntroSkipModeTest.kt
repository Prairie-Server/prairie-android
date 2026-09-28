package org.prairieserver.prairie.domain.player

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class IntroSkipModeTest {
    @Test
    fun wireValuesRoundTrip() {
        for (mode in IntroSkipMode.entries) {
            assertEquals(mode, IntroSkipMode.fromWire(mode.wireValue))
        }
        assertEquals(listOf("never", "ask", "always"), IntroSkipMode.entries.map { it.wireValue })
    }

    @Test
    fun unknownOrAbsentWireValuesAreNull() {
        assertNull(IntroSkipMode.fromWire(null))
        assertNull(IntroSkipMode.fromWire("ALWAYS"))
        assertNull(IntroSkipMode.fromWire("sometimes"))
    }

    @Test
    fun legacyBooleanCannotProduceNever() {
        assertEquals(IntroSkipMode.ALWAYS, IntroSkipMode.fromLegacyBoolean(true))
        assertEquals(IntroSkipMode.ASK, IntroSkipMode.fromLegacyBoolean(false))
        assertEquals(IntroSkipMode.ASK, IntroSkipMode.Default)
    }
}
