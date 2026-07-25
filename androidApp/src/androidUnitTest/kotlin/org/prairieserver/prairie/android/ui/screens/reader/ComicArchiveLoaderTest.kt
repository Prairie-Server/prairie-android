package org.prairieserver.prairie.android.ui.screens.reader

import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class ComicArchiveLoaderTest {

    @Test
    fun `loads image pages in lexicographic order`() {
        val file = createZip(
            "pages/002.png" to byteArrayOf(2),
            "pages/001.jpg" to byteArrayOf(1),
            "notes/info.txt" to byteArrayOf(0),
        )

        val result = loadComicArchivePages(file)

        val loaded = assertIs<ComicArchiveLoadResult.Loaded>(result)
        assertEquals(listOf("pages/001.jpg", "pages/002.png"), loaded.pages.map { it.entryName })
        assertEquals(listOf(0, 1), loaded.pages.map { it.index })
    }

    @Test
    fun `empty archive returns empty result`() {
        val file = createZip("notes/info.txt" to byteArrayOf(0))

        assertIs<ComicArchiveLoadResult.Empty>(loadComicArchivePages(file))
    }

    @Test
    fun `sample size halves dimensions until under the target`() {
        assertEquals(1, comicSampleSize(width = 1000, height = 1500, targetMaxDimension = 1920))
        assertEquals(2, comicSampleSize(width = 4000, height = 3000, targetMaxDimension = 1920))
        assertEquals(2, comicSampleSize(width = 3840, height = 1080, targetMaxDimension = 1920))
        assertEquals(4, comicSampleSize(width = 8000, height = 6000, targetMaxDimension = 1920))
    }

    @Test
    fun `sample size degrades to full resolution on unusable inputs`() {
        assertEquals(1, comicSampleSize(width = 0, height = 0, targetMaxDimension = 1920))
        assertEquals(1, comicSampleSize(width = -1, height = 100, targetMaxDimension = 1920))
        assertEquals(1, comicSampleSize(width = 100, height = 100, targetMaxDimension = 0))
    }

    @Test
    fun `numeric page names sort in natural numeric order`() {
        val file = createZip(
            "page2.png" to byteArrayOf(2),
            "page10.png" to byteArrayOf(10),
            "page1.png" to byteArrayOf(1),
        )

        val result = loadComicArchivePages(file)

        val loaded = assertIs<ComicArchiveLoadResult.Loaded>(result)
        assertEquals(listOf("page1.png", "page2.png", "page10.png"), loaded.pages.map { it.entryName })
    }

    @Test
    fun `invalid archive returns error result`() {
        val file = File.createTempFile("invalid-comic", ".cbz").apply {
            writeText("not a zip")
            deleteOnExit()
        }

        val result = loadComicArchivePages(file)

        assertIs<ComicArchiveLoadResult.Error>(result)
    }

    private fun createZip(vararg entries: Pair<String, ByteArray>): File {
        val file = File.createTempFile("comic", ".cbz")
        file.deleteOnExit()
        ZipOutputStream(file.outputStream()).use { zip ->
            entries.forEach { (name, bytes) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(bytes)
                zip.closeEntry()
            }
        }
        return file
    }
}
