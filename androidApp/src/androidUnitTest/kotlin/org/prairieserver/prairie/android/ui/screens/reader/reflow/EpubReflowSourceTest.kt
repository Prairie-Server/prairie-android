package org.prairieserver.prairie.android.ui.screens.reader.reflow

import org.prairieserver.prairie.android.ui.screens.reader.EpubBook
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertTrue

class EpubReflowSourceTest {
    @Test
    fun baseUrlUsesCurrentChapterDirectorySoRelativeImagesResolve() {
        val tempDir = createTempDirectory("prairie-epub-test").toFile()
        try {
            val epub = File(tempDir, "book.epub")
            ZipOutputStream(epub.outputStream()).use { zip ->
                zip.putText(
                    "META-INF/container.xml",
                    """
                    <container>
                      <rootfiles>
                        <rootfile full-path="OEBPS/content.opf"/>
                      </rootfiles>
                    </container>
                    """.trimIndent(),
                )
                zip.putText(
                    "OEBPS/content.opf",
                    """
                    <package>
                      <manifest>
                        <item id="chapter" href="xhtml/chapter.xhtml"/>
                      </manifest>
                      <spine>
                        <itemref idref="chapter"/>
                      </spine>
                    </package>
                    """.trimIndent(),
                )
                zip.putText(
                    "OEBPS/xhtml/chapter.xhtml",
                    """<html><body><img src="../images/cover.jpg"/></body></html>""",
                )
                zip.putText("OEBPS/images/cover.jpg", "fake")
            }

            val book = EpubBook.open(epub, tempDir)
            val source = EpubReflowSource(book)

            assertTrue(
                source.baseUrl(0).endsWith("/OEBPS/xhtml/"),
                "Relative EPUB resources should resolve from the current chapter directory.",
            )
        } finally {
            tempDir.deleteRecursively()
        }
    }
}

private fun ZipOutputStream.putText(name: String, text: String) {
    putNextEntry(ZipEntry(name))
    write(text.toByteArray(Charsets.UTF_8))
    closeEntry()
}
