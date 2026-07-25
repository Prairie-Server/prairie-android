package org.prairieserver.prairie.common.ebook

import kotlin.test.Test
import kotlin.test.assertEquals

class ReaderDisplaySettingsTest {
    @Test fun `defaults are serif and 1_5 line height`() {
        val s = ReaderDisplaySettings()
        assertEquals(ReaderFontFamily.Serif, s.fontFamily)
        assertEquals(1.5f, s.lineHeight)
    }

    @Test fun `normalized clamps line height`() {
        assertEquals(2.2f, ReaderDisplaySettings(lineHeight = 5f).normalized().lineHeight)
        assertEquals(1.1f, ReaderDisplaySettings(lineHeight = 0.1f).normalized().lineHeight)
    }
}
