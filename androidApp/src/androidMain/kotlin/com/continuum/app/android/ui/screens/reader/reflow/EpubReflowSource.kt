package com.continuum.app.android.ui.screens.reader.reflow

import com.continuum.app.android.ui.screens.reader.EpubBook

/**
 * Adapts an unpacked [EpubBook] into reflow sections. Each spine entry is
 * one section; HTML is served straight from the unpacked chapter file and
 * relative resources resolve against the on-disk unpacked root.
 */
internal class EpubReflowSource(private val book: EpubBook) : ReflowableSource {
    override val sections: List<ReflowSection> =
        book.spine.mapIndexed { i, href ->
            ReflowSection(
                index = i,
                title = href.substringAfterLast('/').substringBeforeLast('.'),
                approxChars = 4000,
            )
        }

    override val tableOfContents: List<ReflowTocEntry> =
        sections.map { ReflowTocEntry(it.title ?: "", it.index) }

    override suspend fun html(index: Int): String? =
        book.spine.getOrNull(index)?.let { book.readChapterHtml(it) }

    override fun baseUrl(index: Int): String =
        "file://${book.unpackedRoot.absolutePath}/"
}
