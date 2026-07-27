package org.siloserver.silo.android.ui.screens.reader

import org.siloserver.silo.common.io.ContentLimitExceeded
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

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
        assertNoPartialExtraction()
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

    @Test
    fun `production epub limits match the security policy`() {
        assertEquals(20_000, DEFAULT_EPUB_EXTRACTION_LIMITS.maxEntries)
        assertEquals(512L * 1024 * 1024, DEFAULT_EPUB_EXTRACTION_LIMITS.maxEntryBytes)
        assertEquals(2L * 1024 * 1024 * 1024, DEFAULT_EPUB_EXTRACTION_LIMITS.maxTotalBytes)
        assertEquals(200L, DEFAULT_EPUB_EXTRACTION_LIMITS.maxCompressionRatio)
    }

    @Test
    fun `open accepts exact entry count boundary`() {
        val epub = tmp.newFile("exact-entries.epub")
        writeEpub(epub, chapters = mapOf("OEBPS/chapter.xhtml" to "chapter"))

        val book = EpubBook.open(
            epub,
            tmp.root,
            limitsFor(epub).copy(maxEntries = zipEntries(epub).size),
        )

        assertEquals("chapter", book.readChapterHtml("chapter.xhtml"))
    }

    @Test
    fun `open rejects entry count limit plus one and removes partial directory`() {
        val epub = tmp.newFile("too-many-entries.epub")
        writeEpub(
            epub,
            chapters = mapOf("OEBPS/chapter.xhtml" to "chapter"),
            extraEntries = mapOf("extra.txt" to "extra"),
        )

        assertFailsWith<ContentLimitExceeded> {
            EpubBook.open(
                epub,
                tmp.root,
                limitsFor(epub).copy(maxEntries = zipEntries(epub).size - 1),
            )
        }

        assertNoPartialExtraction()
    }

    @Test
    fun `open accepts exact per entry byte boundary`() {
        val epub = tmp.newFile("exact-entry-bytes.epub")
        writeEpub(epub, chapters = mapOf("OEBPS/chapter.xhtml" to "chapter"))
        val maxEntryBytes = zipEntries(epub).maxOf { it.size }

        EpubBook.open(
            epub,
            tmp.root,
            limitsFor(epub).copy(maxEntryBytes = maxEntryBytes),
        )
    }

    @Test
    fun `open rejects per entry byte limit plus one and removes partial directory`() {
        val epub = tmp.newFile("large-entry.epub")
        writeEpub(epub, chapters = mapOf("OEBPS/chapter.xhtml" to "chapter"))
        val maxEntryBytes = zipEntries(epub).maxOf { it.size }

        assertFailsWith<ContentLimitExceeded> {
            EpubBook.open(
                epub,
                tmp.root,
                limitsFor(epub).copy(maxEntryBytes = maxEntryBytes - 1),
            )
        }

        assertNoPartialExtraction()
    }

    @Test
    fun `open accepts exact total byte boundary`() {
        val epub = tmp.newFile("exact-total.epub")
        writeEpub(epub, chapters = mapOf("OEBPS/chapter.xhtml" to "chapter"))
        val totalBytes = zipEntries(epub).sumOf { it.size }

        EpubBook.open(
            epub,
            tmp.root,
            limitsFor(epub).copy(maxTotalBytes = totalBytes),
        )
    }

    @Test
    fun `open rejects total byte limit plus one and removes partial directory`() {
        val epub = tmp.newFile("large-total.epub")
        writeEpub(epub, chapters = mapOf("OEBPS/chapter.xhtml" to "chapter"))
        val totalBytes = zipEntries(epub).sumOf { it.size }

        assertFailsWith<ContentLimitExceeded> {
            EpubBook.open(
                epub,
                tmp.root,
                limitsFor(epub).copy(maxTotalBytes = totalBytes - 1),
            )
        }

        assertNoPartialExtraction()
    }

    @Test
    fun `open accepts exact one to one compression ratio boundary`() {
        val epub = tmp.newFile("exact-ratio.epub")
        writeEpub(
            epub,
            chapters = mapOf("OEBPS/chapter.xhtml" to "chapter"),
            stored = true,
        )

        EpubBook.open(
            epub,
            tmp.root,
            limitsFor(epub).copy(maxCompressionRatio = 1),
        )
    }

    @Test
    fun `open rejects compression ratio over limit and removes partial directory`() {
        val epub = tmp.newFile("ratio-bomb.epub")
        writeEpub(
            epub,
            chapters = mapOf("OEBPS/chapter.xhtml" to "A".repeat(4096)),
        )

        assertFailsWith<ContentLimitExceeded> {
            EpubBook.open(
                epub,
                tmp.root,
                limitsFor(epub).copy(maxCompressionRatio = 1),
            )
        }

        assertNoPartialExtraction()
    }

    @Test
    fun `open removes extracted directory when epub metadata parsing fails`() {
        val epub = tmp.newFile("missing-opf.epub")
        ZipOutputStream(epub.outputStream()).use { zip ->
            zip.writeTextEntry(
                "META-INF/container.xml",
                "<container><rootfiles/></container>",
            )
        }

        assertFailsWith<IllegalStateException> {
            EpubBook.open(epub, tmp.root)
        }

        assertNoPartialExtraction()
    }

    @Test
    fun `chapter markup is bounded independently from passive archive resources`() {
        val epub = tmp.newFile("oversized-chapter.epub")
        writeEpub(
            epub,
            chapters = mapOf(
                "OEBPS/chapter.xhtml" to "<h1>Title</h1>" +
                    "A".repeat(MAX_EPUB_CHAPTER_MARKUP_BYTES.toInt()),
            ),
            stored = true,
        )

        val book = EpubBook.open(epub, tmp.root)

        assertFailsWith<ContentLimitExceeded> {
            book.readChapterHtml("chapter.xhtml")
        }
    }

    @Test
    fun `package metadata is bounded independently from archive entry size`() {
        val epub = tmp.newFile("oversized-package.epub")
        ZipOutputStream(epub.outputStream()).use { zip ->
            zip.writeTextEntry(
                "META-INF/container.xml",
                """<container><rootfiles><rootfile full-path="OEBPS/content.opf"/></rootfiles></container>""",
            )
            zip.writeTextEntry(
                "OEBPS/content.opf",
                "<package>" + " ".repeat(MAX_EPUB_PACKAGE_MARKUP_BYTES.toInt()) + "</package>",
            )
        }

        assertFailsWith<ContentLimitExceeded> {
            EpubBook.open(epub, tmp.root)
        }
        assertNoPartialExtraction()
    }

    private fun writeEpub(
        target: File,
        chapters: Map<String, String>,
        extraEntries: Map<String, String> = emptyMap(),
        selfClosingManifestItem: Boolean = true,
        stored: Boolean = false,
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
                stored,
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
                stored,
            )
            chapters.forEach { (name, body) -> zip.writeTextEntry(name, body, stored) }
            extraEntries.forEach { (name, body) -> zip.writeTextEntry(name, body, stored) }
        }
    }

    private fun ZipOutputStream.writeTextEntry(name: String, body: String, stored: Boolean = false) {
        val bytes = body.toByteArray()
        val entry = ZipEntry(name)
        if (stored) {
            val crc = CRC32().apply { update(bytes) }
            entry.method = ZipEntry.STORED
            entry.size = bytes.size.toLong()
            entry.compressedSize = bytes.size.toLong()
            entry.crc = crc.value
        }
        putNextEntry(entry)
        write(bytes)
        closeEntry()
    }

    private fun zipEntries(epub: File): List<ZipEntry> =
        ZipFile(epub).use { it.entries().toList() }

    private fun limitsFor(epub: File): EpubExtractionLimits {
        val entries = zipEntries(epub)
        return EpubExtractionLimits(
            maxEntries = entries.size + 1,
            maxEntryBytes = entries.maxOf { it.size } + 1,
            maxTotalBytes = entries.sumOf { it.size } + 1,
            maxCompressionRatio = Long.MAX_VALUE,
        )
    }

    private fun assertNoPartialExtraction() {
        val readerCache = File(tmp.root, "readers")
        assertTrue(
            readerCache.listFiles().orEmpty().none {
                it.name.startsWith("epub-") || ".tmp-" in it.name
            },
            "no partial EPUB extraction expected",
        )
    }
}
