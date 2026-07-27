package org.prairieserver.prairie.android.ui.screens.reader.reflow

import org.prairieserver.prairie.android.ui.screens.reader.EpubBook
import org.prairieserver.prairie.model.book.BookFormat
import java.io.File
import java.util.zip.ZipFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** One renderable unit (chapter / heading-delimited block) in a book. */
data class ReflowSection(val index: Int, val title: String?, val approxChars: Int)

/** A navigable entry pointing at a section by index. */
data class ReflowTocEntry(val title: String, val sectionIndex: Int)

/**
 * Format-agnostic view of a book's reflowable content. Adapters convert
 * their source format into HTML sections that the reflow WebView renders.
 */
interface ReflowableSource {
    val sections: List<ReflowSection>
    val tableOfContents: List<ReflowTocEntry>
    val resourceDirectoryName: String? get() = null

    /** HTML for [index], or null when the section is unreadable. */
    suspend fun html(index: Int): String?

    /** Base URL for resolving relative resources; "" when there are none. */
    fun baseUrl(index: Int): String
}

/** Escapes the five XML-significant characters for safe HTML text content. */
internal fun htmlEscape(s: String): String =
    s.replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&#39;")

/**
 * Builds a [ReflowableSource] for [file] given its [format]. Heavy I/O
 * (unzip, file reads) runs on the IO dispatcher.
 */
suspend fun buildReflowableSource(
    format: BookFormat,
    file: File,
    cacheRoot: File = file.parentFile ?: file,
): ReflowableSource =
    withContext(Dispatchers.IO) {
        when (format) {
            BookFormat.Epub -> EpubReflowSource(EpubBook.open(file, cacheRoot))
            BookFormat.Fb2 -> file.inputStream().use { Fb2ReflowSource.fromInputStream(it) }
            BookFormat.Fbz -> readFb2FromZip(file)
            BookFormat.Markdown -> MarkdownReflowSource(file.readText())
            BookFormat.Txt -> PlainTextReflowSource(file.readText())
            else -> error("unsupported reflow format $format")
        }
    }

/** Reads the first `.fb2` entry from an FBZ (zip) archive using XML encoding sniffing. */
private fun readFb2FromZip(file: File): Fb2ReflowSource =
    ZipFile(file).use { zip ->
        val entry =
            zip.entries().asSequence().firstOrNull { it.name.endsWith(".fb2", ignoreCase = true) }
                ?: error("FBZ archive contains no .fb2 entry")
        zip.getInputStream(entry).use { Fb2ReflowSource.fromInputStream(it) }
    }
