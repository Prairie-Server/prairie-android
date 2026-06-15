package com.continuum.app.android.ui.screens.reader.reflow

import com.continuum.app.android.ui.screens.reader.EpubBook
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.assertEquals
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

    private fun ZipOutputStream.writeEntry(name: String, content: String) {
        putNextEntry(ZipEntry(name))
        write(content.toByteArray())
        closeEntry()
    }
}
