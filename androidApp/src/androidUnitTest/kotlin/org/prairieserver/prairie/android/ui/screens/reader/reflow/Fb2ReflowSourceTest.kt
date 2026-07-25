package org.prairieserver.prairie.android.ui.screens.reader.reflow

import org.prairieserver.prairie.model.book.BookFormat
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class Fb2ReflowSourceTest {
    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun `fb2 reflow source honors declared non utf8 xml encoding`() = runTest {
        val fb2 = tmp.newFile("book.fb2")
        fb2.writeBytes(RUSSIAN_FB2.toByteArray(charset("windows-1251")))

        val source = buildReflowableSource(BookFormat.Fb2, fb2)

        assertEquals("Глава первая", source.sections.first().title)
        assertTrue("Привет, мир." in source.html(0).orEmpty())
    }

    @Test
    fun `fbz reflow source parses the first fb2 entry as xml`() = runTest {
        val fbz = tmp.newFile("book.fbz")
        ZipOutputStream(fbz.outputStream()).use { zip ->
            zip.putNextEntry(ZipEntry("notes.txt"))
            zip.write("ignore".toByteArray())
            zip.closeEntry()
            zip.putNextEntry(ZipEntry("book/content.fb2"))
            zip.write(RUSSIAN_FB2.toByteArray(charset("windows-1251")))
            zip.closeEntry()
        }

        val source = buildReflowableSource(BookFormat.Fbz, fbz)

        assertEquals("Глава первая", source.sections.first().title)
        assertTrue("Привет, мир." in source.html(0).orEmpty())
    }

    private companion object {
        private val RUSSIAN_FB2 = """
            <?xml version="1.0" encoding="windows-1251"?>
            <FictionBook>
              <body>
                <section>
                  <title><p>Глава первая</p></title>
                  <p>Привет, мир.</p>
                </section>
              </body>
            </FictionBook>
        """.trimIndent()
    }
}
