package com.continuum.app.android.ui.screens.reader.reflow

import com.continuum.app.common.ebook.ReaderDisplaySettings
import com.continuum.app.common.ebook.ReaderTheme
import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
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

    @Test fun `reader margins inset content without shrinking the page surface`() {
        val css = ReaderDisplaySettings(marginScale = 1f).toReflowStyle(false).toCss()

        assertTrue(css.contains("padding:1.2em"))
        assertTrue(css.contains("box-sizing:border-box"))
        assertFalse(Regex("""#reflow-root\{[^}]*margin:1\.2em""").containsMatchIn(css))
    }

    @Test fun `stylesheet targets book typography beyond the root container`() {
        val css = ReaderDisplaySettings().toReflowStyle(false).toCss()

        assertTrue(css.contains("#reflow-root{"))
        assertTrue(css.contains("#reflow-root p{"))
        assertTrue(css.contains("#reflow-root h1"))
        assertTrue(css.contains("#reflow-root img"))
        assertTrue(css.contains("overflow-wrap:break-word"))
        assertTrue(css.contains("break-inside:avoid"))
        assertFalse(css.trim().startsWith("color:"))
    }

    @Test fun `paginator applies generated stylesheet directly`() {
        val paginator = File("src/androidMain/assets/reader/reflow/paginator.js").readText()

        assertTrue(paginator.contains("styleEl.textContent = css"))
        assertFalse(paginator.contains("'#reflow-root{'+css+'}'"))
    }
}
