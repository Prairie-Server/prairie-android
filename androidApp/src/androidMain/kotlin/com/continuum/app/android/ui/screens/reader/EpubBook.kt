package com.continuum.app.android.ui.screens.reader

import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipFile

/**
 * Minimal EPUB parser. Holds the unpacked archive on disk so the
 * WebView can resolve relative URLs naturally; nothing else in the app
 * touches the unpacked tree.
 */
internal class EpubBook private constructor(
    val unpackedRoot: File,
    private val opfDir: File,
    val spine: List<String>,  // chapter hrefs relative to opfDir
) {
    /** Read a chapter's HTML by spine href. Returns null when missing. */
    fun readChapterHtml(href: String): String? {
        val f = File(opfDir, href)
        if (!f.isFile) return null
        return f.readText(Charsets.UTF_8)
    }

    companion object {
        fun open(epub: File, cacheRoot: File): EpubBook {
            // Unpack each EPUB once into a cache subdir keyed by file
            // path so subsequent opens skip the unzip.
            val key = readerCacheKey(epub.absolutePath)
            val unpacked = File(cacheRoot, "readers/epub-$key").apply { mkdirs() }
            if (unpacked.listFiles().isNullOrEmpty()) {
                ZipFile(epub).use { zip ->
                    zip.entries().toList().forEach { entry ->
                        val out = File(unpacked, entry.name)
                        if (entry.isDirectory) {
                            out.mkdirs()
                        } else {
                            out.parentFile?.mkdirs()
                            zip.getInputStream(entry).use { it.copyTo(FileOutputStream(out)) }
                        }
                    }
                }
            }

            // container.xml points to the OPF package.
            val containerXml = File(unpacked, "META-INF/container.xml").readText()
            val opfHref = OPF_HREF_REGEX.find(containerXml)?.groupValues?.get(1)
                ?: error("EPUB missing OPF rootfile")
            val opfFile = File(unpacked, opfHref)
            val opfDir = opfFile.parentFile!!
            val opfXml = opfFile.readText()

            // Spine entries reference manifest items by idref. Manifest
            // items carry the actual href. Build the chapter list by
            // joining the two.
            val manifest = MANIFEST_ITEM_REGEX.findAll(opfXml).associate {
                it.groupValues[1] to it.groupValues[2]
            }
            val spine = SPINE_ITEMREF_REGEX.findAll(opfXml)
                .map { it.groupValues[1] }
                .mapNotNull { manifest[it] }
                .toList()

            return EpubBook(unpacked, opfDir, spine)
        }

        // Quick-and-dirty regex parsing — full XML parser overkill for
        // OPF's well-formed structure. Real spec compliance lands when
        // we hit a malformed EPUB in the wild.
        private val OPF_HREF_REGEX = Regex("""<rootfile[^>]*full-path="([^"]+)"""")
        private val MANIFEST_ITEM_REGEX =
            Regex("""<item\s+(?=[^>]*\bid="([^"]+)")(?=[^>]*\bhref="([^"]+)")[^>]*/>""")
        private val SPINE_ITEMREF_REGEX = Regex("""<itemref[^>]*idref="([^"]+)"""")

    }
}
