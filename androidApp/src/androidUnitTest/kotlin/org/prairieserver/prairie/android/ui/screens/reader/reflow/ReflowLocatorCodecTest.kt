package org.prairieserver.prairie.android.ui.screens.reader.reflow

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ReflowLocatorCodecTest {
    @Test fun `round-trips through json`() {
        val l = ReflowLocator(sectionIndex = 3, pageProgression = 0.4, bookProgression = 0.27)
        val decoded = ReflowLocatorCodec.decode(ReflowLocatorCodec.encode(l))
        assertEquals(l, decoded)
    }
    @Test fun `legacy page form decodes to null`() {
        assertNull(ReflowLocatorCodec.decode("page:7"))
    }
    @Test fun `blank and garbage decode to null`() {
        assertNull(ReflowLocatorCodec.decode(null))
        assertNull(ReflowLocatorCodec.decode(""))
        assertNull(ReflowLocatorCodec.decode("{not json"))
    }

    @Test fun `locator clamps stale section and invalid progress values`() {
        val locator = ReflowLocator(sectionIndex = 9, pageProgression = 2.0, bookProgression = -1.0)

        assertEquals(
            ReflowLocator(sectionIndex = 2, pageProgression = 1.0, bookProgression = 0.0),
            locator.coerceForSectionCount(3),
        )
    }

    @Test fun `locator clamps to section zero when source has no sections`() {
        val locator = ReflowLocator(sectionIndex = -4, pageProgression = -0.5, bookProgression = 3.0)

        assertEquals(
            ReflowLocator(sectionIndex = 0, pageProgression = 0.0, bookProgression = 1.0),
            locator.coerceForSectionCount(0),
        )
    }
}
