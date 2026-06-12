package com.continuum.app.android.ui.screens.reader.reflow

import com.continuum.app.common.ebook.ReaderDisplaySettings
import com.continuum.app.common.ebook.ReaderTheme
import kotlin.test.Test
import kotlin.test.assertTrue

class ReflowStyleTest {
    @Test fun `system theme resolves to dark when device is dark`() {
        val css = ReaderDisplaySettings(theme = ReaderTheme.System)
            .toReflowStyle(systemDark = true).toCss()
        assertTrue(css.contains("#1c1b1f"))
    }
    @Test fun `system theme resolves to light when device is light`() {
        val css = ReaderDisplaySettings(theme = ReaderTheme.System)
            .toReflowStyle(systemDark = false).toCss()
        assertTrue(css.contains("#fffbfe"))
    }
    @Test fun `explicit sepia ignores system dark`() {
        val css = ReaderDisplaySettings(theme = ReaderTheme.Sepia)
            .toReflowStyle(systemDark = true).toCss()
        assertTrue(css.contains("#f4ecd8"))
    }
    @Test fun `text scale becomes font-size percent`() {
        val css = ReaderDisplaySettings(textScale = 1.5f).toReflowStyle(false).toCss()
        assertTrue(css.contains("font-size: 150%"))
    }
}
