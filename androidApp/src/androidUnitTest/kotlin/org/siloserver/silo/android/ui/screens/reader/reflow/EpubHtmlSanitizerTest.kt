package org.siloserver.silo.android.ui.screens.reader.reflow

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.jsoup.Jsoup

class EpubHtmlSanitizerTest {
    @Test
    fun sanitizerRemovesExecutableHtmlButKeepsRelativeResources() {
        val html = """
            <html><body onload="AndroidReflow.onEvent('x')">
              <script>AndroidReflow.onEvent('owned')</script>
              <p onclick="alert(1)" style="background:url(javascript:alert(1))">Text</p>
              <a href="https://example.invalid/track">remote</a>
              <img src="javascript:alert(1)" onerror="alert(1)" />
              <img src="../images/cover.jpg" alt="cover" />
            </body></html>
        """.trimIndent()

        val sanitized = sanitizeEpubChapterHtml(html)

        assertFalse(sanitized.contains("<script", ignoreCase = true))
        assertFalse(sanitized.contains("AndroidReflow", ignoreCase = true))
        assertFalse(sanitized.contains("onclick", ignoreCase = true))
        assertFalse(sanitized.contains("onerror", ignoreCase = true))
        assertFalse(sanitized.contains("javascript:", ignoreCase = true))
        assertFalse(sanitized.contains("https://example.invalid", ignoreCase = true))
        assertFalse(sanitized.contains("style=", ignoreCase = true))
        assertTrue(sanitized.contains("../images/cover.jpg"))
    }

    @Test
    fun sanitizerRemovesEntityEncodedUnsafeResourceUrls() {
        val html = """
            <html><body>
              <a href="jav&#x61;script:alert(1)">encoded js</a>
              <img src="java&#x0A;script:alert(1)" />
              <a href='&#x68;ttps://example.invalid/track'>remote</a>
              <img src="&sol;&sol;example.invalid/cover.jpg" />
              <a href="chapter&#x2d;1.xhtml">chapter</a>
            </body></html>
        """.trimIndent()

        val sanitized = sanitizeEpubChapterHtml(html)

        assertFalse(sanitized.contains("jav&#x61;script", ignoreCase = true))
        assertFalse(sanitized.contains("java&#x0A;script", ignoreCase = true))
        assertFalse(sanitized.contains("&#x68;ttps", ignoreCase = true))
        assertFalse(sanitized.contains("&sol;&sol;example.invalid", ignoreCase = true))
        val sanitizedDocument = Jsoup.parseBodyFragment(sanitized)
        assertEquals("chapter-1.xhtml", sanitizedDocument.selectFirst("a[href]")?.attr("href"))
    }

    @Test
    fun sanitizerRemovesAnyAbsoluteResourceUrlScheme() {
        val html = """
            <html><body>
              <img src="https:example.invalid/pixel.png" />
              <img src="file:/sdcard/Download/private.png" />
              <img src="content://media/external/images/1" />
              <img src="ftp://example.invalid/cover.jpg" />
              <img src="images/local-cover.jpg" />
            </body></html>
        """.trimIndent()

        val sanitized = sanitizeEpubChapterHtml(html)

        assertFalse(sanitized.contains("https:example.invalid", ignoreCase = true))
        assertFalse(sanitized.contains("file:/sdcard", ignoreCase = true))
        assertFalse(sanitized.contains("content://media", ignoreCase = true))
        assertFalse(sanitized.contains("ftp://example.invalid", ignoreCase = true))
        assertTrue(sanitized.contains("images/local-cover.jpg"))
    }

    @Test
    fun sanitizerCleansMalformedSvgAndMathMlMutationPayloads() {
        val html = """
            <svg>
              <desc>
                <math><mtext><table><mglyph><style><!--</style>
                <img title="--><img src=x onerror=alert('math-owned')>">
              </desc>
              <g><style><script>alert('svg-owned')</script></style></g>
            </svg>
        """.trimIndent()

        val sanitized = sanitizeEpubChapterHtml(html)
        val sanitizedDocument = Jsoup.parseBodyFragment(sanitized)

        assertTrue(sanitizedDocument.select("style, script").isEmpty())
        assertTrue(sanitizedDocument.select("[onerror]").isEmpty())
        assertFalse(sanitized.contains("svg-owned", ignoreCase = true))
    }

    @Test
    fun sanitizerRemovesActiveDocumentsAndExternalStyleResources() {
        val html = """
            <base href="https://example.invalid/" />
            <link rel="stylesheet" href="../styles/book.css" />
            <style>@import url("https://example.invalid/tracker.css");</style>
            <script src="../scripts/chapter.js">alert("owned")</script>
            <form action="../submit"><input name="secret" /><button>Send</button></form>
            <iframe src="../frames/chapter.xhtml" srcdoc="<script>alert(1)</script>"></iframe>
            <frame src="../frames/navigation.xhtml" />
            <frameset><frame src="../frames/content.xhtml" /></frameset>
            <object data="../objects/widget.svg"></object>
            <embed src="../objects/widget.svg" />
            <p onanimationstart="alert(1)" style="color: red">Readable text</p>
        """.trimIndent()

        val sanitized = sanitizeEpubChapterHtml(html)

        listOf(
            "base",
            "link",
            "style",
            "script",
            "form",
            "input",
            "button",
            "iframe",
            "frame",
            "frameset",
            "object",
            "embed",
        ).forEach { tag ->
            assertFalse(sanitized.contains("<$tag", ignoreCase = true), "kept <$tag>")
        }
        assertFalse(sanitized.contains("srcdoc", ignoreCase = true))
        assertFalse(sanitized.contains("onanimationstart", ignoreCase = true))
        assertFalse(sanitized.contains("style=", ignoreCase = true))
        assertFalse(sanitized.contains("tracker.css", ignoreCase = true))
        assertFalse(sanitized.contains("chapter.js", ignoreCase = true))
        assertTrue(sanitized.contains("Readable text"))
    }

    @Test
    fun sanitizerRejectsEncodedAndNonRelativeResourceUrls() {
        val html = """
            <a href="java%73cript:alert(1)">percent encoded script</a>
            <a href="%256a%2561vascript%253aalert(1)">double encoded script</a>
            <img src="%2f%2fexample.invalid/tracker.png" />
            <img src="%5c%5cexample.invalid/tracker.png" />
            <img src="/absolute/on-device/path.png" />
            <img src="\example.invalid\tracker.png" />
            <a href="java&#x09;script&colon;alert(1)">entity encoded script</a>
            <a href="mailto:reader@example.invalid">email</a>
        """.trimIndent()

        val sanitized = sanitizeEpubChapterHtml(html)
        val sanitizedDocument = Jsoup.parseBodyFragment(sanitized)

        assertTrue(sanitizedDocument.select("[href], [src], [xlink:href]").isEmpty())
    }

    @Test
    fun sanitizerPreservesRichEpubMarkupAndRelativeResources() {
        val html = """
            <article id="chapter-one" epub:type="chapter">
              <h1>Chapter <ruby>一<rp>(</rp><rt>one</rt><rp>)</rp></ruby></h1>
              <h2>Diagram</h2>
              <svg viewBox="0 0 100 100" role="img">
                <title>Safe diagram</title>
                <defs><path id="shape" d="M0 0 L10 10" /></defs>
                <circle cx="50" cy="50" r="25" fill="#336699" />
                <use xlink:href="../images/shapes.svg#shape" />
              </svg>
              <math display="block">
                <mrow><mi>x</mi><mo>=</mo><mfrac><mn>1</mn><mn>2</mn></mfrac></mrow>
              </math>
              <table>
                <caption>Values</caption>
                <thead><tr><th scope="col">Name</th></tr></thead>
                <tbody><tr><td>Example</td></tr></tbody>
              </table>
              <a href="../text/chapter-2.xhtml#start">Next chapter</a>
              <a href="#chapter-one">Chapter start</a>
              <img src="../images/cover.jpg" alt="Cover" width="600" height="900" />
            </article>
        """.trimIndent()

        val sanitized = sanitizeEpubChapterHtml(html)

        listOf(
            "<article",
            "<h1",
            "<h2",
            "<ruby",
            "<rp>",
            "<rt>",
            "<svg",
            "<path",
            "<circle",
            "<use",
            "<math",
            "<mrow",
            "<mfrac",
            "<table",
            "<caption",
            "<thead",
            "<tbody",
            "<th",
            "<td",
        ).forEach { element ->
            assertTrue(sanitized.contains(element, ignoreCase = true), "removed $element")
        }
        assertTrue(sanitized.contains("../images/shapes.svg#shape"))
        assertTrue(sanitized.contains("../text/chapter-2.xhtml#start"))
        assertTrue(sanitized.contains("href=\"#chapter-one\""))
        assertTrue(sanitized.contains("../images/cover.jpg"))
    }

    @Test
    fun sanitizerRejectsNonLocalSvgPaintUrls() {
        val html = """
            <svg>
              <path id="external"
                    fill="url(https://attacker.invalid/fill.svg#paint)"
                    stroke="url(javascript:alert(1))" />
              <circle id="data"
                      fill="url(data:image/svg+xml,owned)"
                      stroke="url(%68%74%74%70%73%3A%2F%2Fattacker.invalid/stroke.svg)" />
              <rect id="network"
                    fill="url(//attacker.invalid/fill.svg)"
                    stroke="url(\\attacker.invalid\stroke.svg)" />
              <ellipse id="absolute"
                       fill="url(/absolute/fill.svg#paint)"
                       stroke="url(file:/absolute/stroke.svg#paint)" />
              <text id="obfuscated"
                    fill="u&#x72;l(java&#x09;script:alert(1))"
                    stroke="url(&#x2f;&#x2f;attacker.invalid/stroke.svg)">text</text>
            </svg>
        """.trimIndent()

        val sanitizedDocument = Jsoup.parseBodyFragment(sanitizeEpubChapterHtml(html))

        assertTrue(sanitizedDocument.select("[fill], [stroke]").isEmpty())
    }

    @Test
    fun sanitizerPreservesSafeSvgPaintValues() {
        val html = """
            <svg>
              <defs>
                <linearGradient id="gradient">
                  <stop offset="0%" stop-color="#336699" />
                </linearGradient>
              </defs>
              <path id="local-paint" fill="url(#gradient)" stroke="url('#outline')" />
              <circle id="colors" fill="#336699" stroke="currentColor" />
              <rect id="keywords" fill="red" stroke="none" />
              <text id="functional-colors" fill="rgb(10 20 30 / 50%)"
                    stroke="hsl(120 100% 50%)">text</text>
            </svg>
        """.trimIndent()

        val sanitizedDocument = Jsoup.parseBodyFragment(sanitizeEpubChapterHtml(html))

        assertEquals("url(#gradient)", sanitizedDocument.getElementById("local-paint")?.attr("fill"))
        assertEquals("url('#outline')", sanitizedDocument.getElementById("local-paint")?.attr("stroke"))
        assertEquals("#336699", sanitizedDocument.getElementById("colors")?.attr("fill"))
        assertEquals("currentColor", sanitizedDocument.getElementById("colors")?.attr("stroke"))
        assertEquals("red", sanitizedDocument.getElementById("keywords")?.attr("fill"))
        assertEquals("none", sanitizedDocument.getElementById("keywords")?.attr("stroke"))
        assertEquals(
            "rgb(10 20 30 / 50%)",
            sanitizedDocument.getElementById("functional-colors")?.attr("fill"),
        )
        assertEquals(
            "hsl(120 100% 50%)",
            sanitizedDocument.getElementById("functional-colors")?.attr("stroke"),
        )
    }
}
