package org.siloserver.silo.android.ui.screens.reader

import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith

class EpubBookTest {
    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun `open rejects zip entries that escape the unpack directory`() {
        val epub = tmp.newFile("bad.epub")
        writeEpub(
            epub,
            chapters = mapOf("OEBPS/chapter.xhtml" to "<html><body>Safe</body></html>"),
            extraEntries = mapOf("../escaped.txt" to "owned"),
        )

        assertFailsWith<IllegalArgumentException> {
            EpubBook.open(epub, tmp.root)
        }
        assertFalse(File(tmp.root, "escaped.txt").exists())
    }

    @Test
    fun `open refreshes unpacked cache when epub file changes at same path`() {
        val epub = tmp.newFile("changing.epub")
        writeEpub(epub, chapters = mapOf("OEBPS/chapter.xhtml" to "<html><body>Old</body></html>"))
        EpubBook.open(epub, tmp.root)

        writeEpub(epub, chapters = mapOf("OEBPS/chapter.xhtml" to "<html><body>New</body></html>"))
        val reopened = EpubBook.open(epub, tmp.root)

        assertEquals("<html><body>New</body></html>", reopened.readChapterHtml("chapter.xhtml"))
    }

    @Test
    fun `open parses non self closing manifest items`() {
        val epub = tmp.newFile("non-self-closing.epub")
        writeEpub(
            epub,
            chapters = mapOf("OEBPS/chapter.xhtml" to "<html><body>Chapter</body></html>"),
            selfClosingManifestItem = false,
        )

        val book = EpubBook.open(epub, tmp.root)

        assertEquals(listOf("chapter.xhtml"), book.spine)
        assertEquals("<html><body>Chapter</body></html>", book.readChapterHtml("chapter.xhtml"))
    }

    private fun writeEpub(
        target: File,
        chapters: Map<String, String>,
        extraEntries: Map<String, String> = emptyMap(),
        selfClosingManifestItem: Boolean = true,
    ) {
        val manifestItem = if (selfClosingManifestItem) {
            """<item id="chapter" href="chapter.xhtml" media-type="application/xhtml+xml"/>"""
        } else {
            """<item id="chapter" href="chapter.xhtml" media-type="application/xhtml+xml"></item>"""
        }
        ZipOutputStream(target.outputStream()).use { zip ->
            zip.writeTextEntry(
                "META-INF/container.xml",
                """<container><rootfiles><rootfile full-path="OEBPS/content.opf"/></rootfiles></container>""",
            )
            zip.writeTextEntry(
                "OEBPS/content.opf",
                """
                <package>
                  <manifest>
                    $manifestItem
                  </manifest>
                  <spine>
                    <itemref idref="chapter"/>
                  </spine>
                </package>
                """.trimIndent(),
            )
            chapters.forEach { (name, body) -> zip.writeTextEntry(name, body) }
            extraEntries.forEach { (name, body) -> zip.writeTextEntry(name, body) }
        }
    }

    private fun ZipOutputStream.writeTextEntry(name: String, body: String) {
        putNextEntry(ZipEntry(name))
        write(body.toByteArray())
        closeEntry()
    }
}
