package com.continuum.app.android.ui.screens.reader.reflow

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
}
