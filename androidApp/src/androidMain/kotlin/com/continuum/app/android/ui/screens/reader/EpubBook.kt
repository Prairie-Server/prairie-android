package com.continuum.app.android.ui.screens.reader

import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
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
        val root = unpackedRoot.canonicalFile
        val f = File(opfDir, href).canonicalFile
        if (f.path != root.path && !f.path.startsWith(root.path + File.separator)) return null
        if (!f.isFile) return null
        return f.readText(Charsets.UTF_8)
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
            val opfHref = OPF_HREF_REGEX.find(containerXml)?.groupValues?.get(1)
                ?: error("EPUB missing OPF rootfile")
            val opfFile = safeChild(unpacked, opfHref, unpacked)
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

        // Quick-and-dirty regex parsing — full XML parser overkill for
        // OPF's well-formed structure. Real spec compliance lands when
        // we hit a malformed EPUB in the wild.
        private val OPF_HREF_REGEX = Regex("""<rootfile[^>]*full-path="([^"]+)"""")
        private val MANIFEST_ITEM_REGEX =
            Regex("""<item\s+(?=[^>]*\bid="([^"]+)")(?=[^>]*\bhref="([^"]+)")[^>]*>""")
        private val SPINE_ITEMREF_REGEX = Regex("""<itemref[^>]*idref="([^"]+)"""")

    }
}
