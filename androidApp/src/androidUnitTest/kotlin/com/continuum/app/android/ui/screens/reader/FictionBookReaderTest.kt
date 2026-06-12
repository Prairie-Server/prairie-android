package com.continuum.app.android.ui.screens.reader

import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class FictionBookReaderTest {
    @Test
    fun `parses fb2 title and body paragraphs into reader text`() {
        val result = parseFictionBookText(FB2_SAMPLE.byteInputStream())

        val loaded = assertIs<FictionBookLoadResult.Loaded>(result)
        assertTrue(loaded.text.startsWith("Sample Book\nAuthor Name"))
        assertTrue("Chapter One" in loaded.text)
        assertTrue("First paragraph." in loaded.text)
        assertTrue("Second paragraph with emphasis." in loaded.text)
    }

    @Test
    fun `loads fbz archive by selecting first fb2 entry`() {
        val archive = createZip(
            "notes/readme.txt" to "ignore me".toByteArray(),
            "book/sample.fb2" to FB2_SAMPLE.toByteArray(),
        )

        val result = loadFictionBookText(archive)

        val loaded = assertIs<FictionBookLoadResult.Loaded>(result)
        assertTrue("Sample Book" in loaded.text)
    }

    @Test
    fun `invalid fb2 reports error`() {
        val file = File.createTempFile("broken", ".fb2").apply {
            writeText("<FictionBook><body>")
            deleteOnExit()
        }

        assertIs<FictionBookLoadResult.Error>(loadFictionBookText(file))
    }

    @Test
    fun `honors declared xml encoding for non utf8 fb2`() {
        val fb2 = """
            <?xml version="1.0" encoding="windows-1251"?>
            <FictionBook>
              <description>
                <title-info>
                  <author>
                    <first-name>Лев</first-name>
                    <last-name>Толстой</last-name>
                  </author>
                  <book-title>Война и мир</book-title>
                </title-info>
              </description>
              <body>
                <section>
                  <p>Привет, мир.</p>
                </section>
              </body>
            </FictionBook>
        """.trimIndent()
        val bytes = fb2.toByteArray(charset("windows-1251"))

        val result = parseFictionBookText(bytes.inputStream())

        val loaded = assertIs<FictionBookLoadResult.Loaded>(result)
        assertTrue("Война и мир" in loaded.text)
        assertTrue("Лев Толстой" in loaded.text)
        assertTrue("Привет, мир." in loaded.text)
    }

    @Test
    fun `empty fb2 reports empty`() {
        val result = parseFictionBookText(
            """
            <FictionBook>
              <body><section /></body>
            </FictionBook>
            """.trimIndent().byteInputStream(),
        )

        assertEquals(FictionBookLoadResult.Empty, result)
    }

    private fun createZip(vararg entries: Pair<String, ByteArray>): File {
        val file = File.createTempFile("fictionbook", ".fbz")
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

    private companion object {
        private val FB2_SAMPLE = """
            <FictionBook>
              <description>
                <title-info>
                  <author>
                    <first-name>Author</first-name>
                    <last-name>Name</last-name>
                  </author>
                  <book-title>Sample Book</book-title>
                </title-info>
              </description>
              <body>
                <section>
                  <title><p>Chapter One</p></title>
                  <p>First paragraph.</p>
                  <p>Second paragraph with <emphasis>emphasis</emphasis>.</p>
                </section>
              </body>
            </FictionBook>
        """.trimIndent()
    }
}
