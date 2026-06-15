package com.continuum.app.android.ui.screens.reader.reflow

import com.continuum.app.android.ui.screens.reader.EpubBook
import com.continuum.app.model.book.BookFormat
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EpubReflowSourceTest {
    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun `epub sections use human titles and measured text length`() = runTest {
        val epub = tmp.newFile("book.epub")
        ZipOutputStream(epub.outputStream()).use { zip ->
            zip.writeEntry(
                "META-INF/container.xml",
                """
                <container>
                  <rootfiles>
                    <rootfile full-path="OEBPS/content.opf" />
                  </rootfiles>
                </container>
                """.trimIndent(),
            )
            zip.writeEntry(
                "OEBPS/content.opf",
                """
                <package>
                  <manifest>
                    <item id="title" href="Text/title-page.xhtml" />
                    <item id="ch1" href="Text/chapter_001.xhtml" />
                  </manifest>
                  <spine>
                    <itemref idref="title" />
                    <itemref idref="ch1" />
                  </spine>
                </package>
                """.trimIndent(),
            )
            zip.writeEntry(
                "OEBPS/Text/title-page.xhtml",
                """
                <html><head><title>Angels &amp; Man</title></head>
                <body><h1>ANGELS &amp; MAN</h1><p>Rafael Nicolás</p></body></html>
                """.trimIndent(),
            )
            zip.writeEntry(
                "OEBPS/Text/chapter_001.xhtml",
                """
                <html><body><h1>Chapter One</h1><p>Alpha beta gamma.</p></body></html>
                """.trimIndent(),
            )
        }

        val source = EpubReflowSource(EpubBook.open(epub, tmp.root))

        assertEquals("Angels & Man", source.sections[0].title)
        assertEquals("Chapter One", source.sections[1].title)
        assertTrue(source.sections[1].approxChars in 16..200)
    }

    @Test
    fun `epub base url encodes filesystem paths for WebView resource loading`() = runTest {
        val cacheRoot = tmp.newFolder("Reader Cache #1")
        val epub = tmp.newFile("book.epub")
        writeMinimalEpub(epub)

        val source = EpubReflowSource(EpubBook.open(epub, cacheRoot))
        val baseUrl = source.baseUrl(0)

        assertTrue(baseUrl.startsWith("file://"))
        assertTrue(baseUrl.endsWith("/"))
        assertTrue(baseUrl.contains("Reader%20Cache%20%231"))
        assertFalse(baseUrl.contains("Reader Cache #1"))
    }

    @Test
    fun `epub parser accepts single quoted package attributes`() = runTest {
        val epub = tmp.newFile("single-quoted.epub")
        writeMinimalEpub(epub, quote = '\'')

        val source = EpubReflowSource(EpubBook.open(epub, tmp.root))

        assertEquals(1, source.sections.size)
        assertEquals("Chapter One", source.sections.first().title)
    }

    @Test
    fun `epub parser decodes xml entities in package and chapter paths`() = runTest {
        val epub = tmp.newFile("entity-paths.epub")
        ZipOutputStream(epub.outputStream()).use { zip ->
            zip.writeEntry(
                "META-INF/container.xml",
                """
                <container>
                  <rootfiles>
                    <rootfile full-path="OEBPS/content &amp; metadata.opf" />
                  </rootfiles>
                </container>
                """.trimIndent(),
            )
            zip.writeEntry(
                "OEBPS/content & metadata.opf",
                """
                <package>
                  <manifest>
                    <item id="ch1" href="Text/chapter &amp; one.xhtml" />
                  </manifest>
                  <spine>
                    <itemref idref="ch1" />
                  </spine>
                </package>
                """.trimIndent(),
            )
            zip.writeEntry(
                "OEBPS/Text/chapter & one.xhtml",
                """
                <html><body><h1>Chapter &amp; One</h1><p>Alpha beta gamma.</p></body></html>
                """.trimIndent(),
            )
        }

        val source = EpubReflowSource(EpubBook.open(epub, tmp.root))

        assertEquals(1, source.sections.size)
        assertEquals("Chapter & One", source.sections.first().title)
    }

    @Test
    fun `epub reflow source unpacks into caller cache instead of original download directory`() = runTest {
        val publicDownloadDir = tmp.newFolder("public-downloads")
        val cacheRoot = tmp.newFolder("reader-cache")
        val epub = File(publicDownloadDir, "book.epub")
        writeMinimalEpub(epub)

        buildReflowableSource(BookFormat.Epub, epub, cacheRoot)

        assertTrue(File(cacheRoot, "readers").isDirectory)
        assertFalse(File(publicDownloadDir, "readers").exists())
    }

    private fun ZipOutputStream.writeEntry(name: String, content: String) {
        putNextEntry(ZipEntry(name))
        write(content.toByteArray())
        closeEntry()
    }

    private fun writeMinimalEpub(epub: File, quote: Char = '"') {
        fun attr(value: String): String = "$quote$value$quote"
        ZipOutputStream(epub.outputStream()).use { zip ->
            zip.writeEntry(
                "META-INF/container.xml",
                """
                <container>
                  <rootfiles>
                    <rootfile full-path=${attr("OEBPS/content.opf")} />
                  </rootfiles>
                </container>
                """.trimIndent(),
            )
            zip.writeEntry(
                "OEBPS/content.opf",
                """
                <package>
                  <manifest>
                    <item id=${attr("ch1")} href=${attr("Text/chapter_001.xhtml")} />
                  </manifest>
                  <spine>
                    <itemref idref=${attr("ch1")} />
                  </spine>
                </package>
                """.trimIndent(),
            )
            zip.writeEntry(
                "OEBPS/Text/chapter_001.xhtml",
                """
                <html><body><h1>Chapter One</h1><p>Alpha beta gamma.</p></body></html>
                """.trimIndent(),
            )
        }
    }
}
