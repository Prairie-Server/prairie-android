package com.continuum.app.android.ui.screens.reader

import java.io.File
import java.io.FileOutputStream
import java.net.URI
import java.security.MessageDigest
import java.util.zip.ZipFile

/**
 * Minimal EPUB parser. Holds the unpacked archive on disk so the
 * WebView can resolve relative URLs naturally; nothing else in the app
 * touches the unpacked tree.
 */
internal class EpubBook private constructor(
    val unpackedRoot: File,
    val opfDir: File,
    val spine: List<String>,  // chapter hrefs relative to opfDir
) {
    /** Read a chapter's HTML by spine href. Returns null when missing. */
    fun readChapterHtml(href: String): String? {
        val root = unpackedRoot.canonicalFile
        val f = File(opfDir, href).canonicalFile
        if (f.path != root.path && !f.path.startsWith(root.path + File.separator)) return null
        if (!f.isFile) return null
        return f.readText(Charsets.UTF_8)
    }

    /**
     * Base URL for one spine item. EPUB resources are resolved relative to the
     * chapter file, not necessarily the OPF directory; chapters commonly live in
     * `OEBPS/xhtml/` while images live in `OEBPS/images/` and use `../images/...`.
     */
    fun chapterBaseUrl(href: String): String {
        val root = unpackedRoot.canonicalFile
        val chapterFile = File(opfDir, href).canonicalFile
        val chapterDir = (chapterFile.parentFile ?: opfDir).canonicalFile
        val isInsideRoot = chapterDir.path == root.path ||
            chapterDir.path.startsWith(root.path + File.separator)
        return readerDirectoryBaseUrl(if (isInsideRoot) chapterDir else opfDir)
    }

    companion object {
        fun open(epub: File, cacheRoot: File): EpubBook {
            val key = fileContentCacheKey(epub)
            val cacheDir = File(cacheRoot, "readers").apply { mkdirs() }
            val unpacked = File(cacheDir, "epub-$key")
            if (unpacked.listFiles().isNullOrEmpty()) {
                unpackAtomically(epub, unpacked)
            }

            // container.xml points to the OPF package.
            val containerXml = safeChild(unpacked, "META-INF/container.xml", unpacked).readText()
            val opfHref = ROOTFILE_TAG_REGEX.findAll(containerXml)
                .mapNotNull { it.value.xmlAttribute("full-path") }
                .firstOrNull()
                ?: error("EPUB missing OPF rootfile")
            val opfFile = safeChild(unpacked, opfHref, unpacked)
            val opfDir = opfFile.parentFile!!
            val opfXml = opfFile.readText()

            // Spine entries reference manifest items by idref. Manifest
            // items carry the actual href. Build the chapter list by
            // joining the two.
            val manifest = ITEM_TAG_REGEX.findAll(opfXml).mapNotNull {
                val tag = it.value
                val id = tag.xmlAttribute("id")
                val href = tag.xmlAttribute("href")
                if (id != null && href != null) id to href else null
            }.toMap()
            val spine = ITEMREF_TAG_REGEX.findAll(opfXml)
                .mapNotNull { it.value.xmlAttribute("idref") }
                .mapNotNull { manifest[it] }
                .toList()

            return EpubBook(unpacked, opfDir, spine)
        }

        private fun unpackAtomically(epub: File, unpacked: File) {
            val tmp = File(unpacked.parentFile, "${unpacked.name}.tmp-${System.nanoTime()}")
            tmp.deleteRecursively()
            tmp.mkdirs()
            try {
                ZipFile(epub).use { zip ->
                    zip.entries().toList().forEach { entry ->
                        val out = safeChild(tmp, entry.name, tmp)
                        if (entry.isDirectory) {
                            out.mkdirs()
                        } else {
                            out.parentFile?.mkdirs()
                            zip.getInputStream(entry).use { input ->
                                FileOutputStream(out).use { output -> input.copyTo(output) }
                            }
                        }
                    }
                }
                unpacked.deleteRecursively()
                if (!tmp.renameTo(unpacked)) {
                    tmp.copyRecursively(unpacked, overwrite = true)
                    tmp.deleteRecursively()
                }
            } catch (throwable: Throwable) {
                tmp.deleteRecursively()
                throw throwable
            }
        }

        private fun safeChild(parent: File, name: String, root: File): File {
            val canonicalRoot = root.canonicalFile
            val out = File(parent, name).canonicalFile
            require(out.path == canonicalRoot.path || out.path.startsWith(canonicalRoot.path + File.separator)) {
                "EPUB entry escapes unpack directory: $name"
            }
            return out
        }

        private fun fileContentCacheKey(file: File): String {
            val md = MessageDigest.getInstance("SHA-1")
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            file.inputStream().use { input ->
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    md.update(buffer, 0, read)
                }
            }
            return md.digest().joinToString("") { "%02x".format(it) }
        }

        private fun String.xmlAttribute(name: String): String? {
            val pattern = Regex("""\b${Regex.escape(name)}\s*=\s*(['"])(.*?)\1""")
            return pattern.find(this)?.groupValues?.get(2)?.decodeXmlAttributeEntities()
        }

        private fun String.decodeXmlAttributeEntities(): String =
            replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .replace("&apos;", "'")

        private val ROOTFILE_TAG_REGEX = Regex("""<rootfile\b[^>]*>""", RegexOption.IGNORE_CASE)
        private val ITEM_TAG_REGEX = Regex("""<item\b[^>]*>""", RegexOption.IGNORE_CASE)
        private val ITEMREF_TAG_REGEX = Regex("""<itemref\b[^>]*>""", RegexOption.IGNORE_CASE)
    }
}

internal fun readerDirectoryBaseUrl(directory: File): String {
    val path = directory.absoluteFile.path.trimEnd(File.separatorChar) + File.separator
    return URI("file", "", path, null).toASCIIString()
}
