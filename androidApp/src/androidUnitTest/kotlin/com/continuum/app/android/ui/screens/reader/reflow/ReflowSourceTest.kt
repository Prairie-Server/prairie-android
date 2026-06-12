package com.continuum.app.android.ui.screens.reader.reflow

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ReflowSourceTest {
    @Test fun `plain text becomes one section of paragraphs`() = runTest {
        val s = PlainTextReflowSource("Hello world\n\nSecond para")
        assertEquals(1, s.sections.size)
        val html = s.html(0)!!
        assertTrue(html.contains("<p>Hello world</p>"))
        assertTrue(html.contains("<p>Second para</p>"))
    }
    @Test fun `markdown splits on top-level headings`() = runTest {
        val s = MarkdownReflowSource("# One\n\ntext\n\n# Two\n\nmore")
        assertEquals(2, s.sections.size)
        assertEquals("One", s.sections[0].title)
        assertTrue(s.html(0)!!.contains("<h1>One</h1>"))
        assertTrue(s.html(0)!!.contains("<p>text</p>"))
    }
    @Test fun `fb2 sections map to html with titles`() = runTest {
        val fb2 = """
            <FictionBook><body>
              <section><title><p>Chapter 1</p></title><p>Alpha</p></section>
              <section><title><p>Chapter 2</p></title><p>Beta</p></section>
            </body></FictionBook>
        """.trimIndent()
        val s = Fb2ReflowSource.fromXml(fb2)
        assertEquals(2, s.sections.size)
        assertEquals("Chapter 1", s.sections[0].title)
        assertTrue(s.html(0)!!.contains("Alpha"))
    }
    @Test fun `approxChars reflects text length`() = runTest {
        val s = PlainTextReflowSource("abcdef")
        assertTrue(s.sections[0].approxChars >= 6)
    }
}
