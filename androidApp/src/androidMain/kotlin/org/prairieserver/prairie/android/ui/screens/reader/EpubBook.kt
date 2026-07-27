package org.prairieserver.prairie.android.ui.screens.reader

import org.prairieserver.prairie.common.io.ContentLimitExceeded
import org.prairieserver.prairie.common.io.checkedLimitedByteCount
import org.prairieserver.prairie.common.io.copyToLimited
import java.io.File
import java.io.ByteArrayOutputStream
import java.io.FileOutputStream
import java.net.URI
import java.security.MessageDigest
import java.util.zip.ZipFile

internal data class EpubExtractionLimits(
    val maxEntries: Int,
    val maxEntryBytes: Long,
    val maxTotalBytes: Long,
    val maxCompressionRatio: Long,
) {
    init {
        require(maxEntries >= 0) { "maxEntries must not be negative" }
        require(maxEntryBytes >= 0) { "maxEntryBytes must not be negative" }
        require(maxTotalBytes >= 0) { "maxTotalBytes must not be negative" }
        require(maxCompressionRatio > 0) { "maxCompressionRatio must be positive" }
    }
}

internal val DEFAULT_EPUB_EXTRACTION_LIMITS = EpubExtractionLimits(
    maxEntries = 20_000,
    maxEntryBytes = 512L * 1024 * 1024,
    maxTotalBytes = 2L * 1024 * 1024 * 1024,
    maxCompressionRatio = 200,
)

internal const val MAX_EPUB_CONTAINER_MARKUP_BYTES = 1L * 1024 * 1024
internal const val MAX_EPUB_PACKAGE_MARKUP_BYTES = 4L * 1024 * 1024
internal const val MAX_EPUB_CHAPTER_MARKUP_BYTES = 4L * 1024 * 1024
internal const val MAX_EPUB_CHAPTER_PREVIEW_BYTES = 64 * 1024

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
        return f.readUtf8Limited(MAX_EPUB_CHAPTER_MARKUP_BYTES, "EPUB chapter markup")
    }

    /** Small prefix used only for section title/weight metadata. */
    fun readChapterPreview(href: String): String? {
        val root = unpackedRoot.canonicalFile
        val f = File(opfDir, href).canonicalFile
        if (f.path != root.path && !f.path.startsWith(root.path + File.separator)) return null
        if (!f.isFile) return null
        return f.readUtf8Prefix(MAX_EPUB_CHAPTER_PREVIEW_BYTES)
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
        fun open(
            epub: File,
            cacheRoot: File,
            limits: EpubExtractionLimits = DEFAULT_EPUB_EXTRACTION_LIMITS,
        ): EpubBook {
            val key = fileContentCacheKey(epub)
            val cacheDir = File(cacheRoot, "readers").apply { mkdirs() }
            val unpacked = File(cacheDir, "epub-$key")
            try {
                validateArchiveMetadata(epub, limits)
                if (unpacked.listFiles().isNullOrEmpty()) {
                    unpackAtomically(epub, unpacked, limits)
                }

                // container.xml points to the OPF package.
                val containerXml = safeChild(unpacked, "META-INF/container.xml", unpacked)
                    .readUtf8Limited(MAX_EPUB_CONTAINER_MARKUP_BYTES, "EPUB container markup")
                val opfHref = ROOTFILE_TAG_REGEX.findAll(containerXml)
                    .mapNotNull { it.value.xmlAttribute("full-path") }
                    .firstOrNull()
                    ?: error("EPUB missing OPF rootfile")
                val opfFile = safeChild(unpacked, opfHref, unpacked)
                val opfDir = opfFile.parentFile!!
                val opfXml = opfFile.readUtf8Limited(
                    MAX_EPUB_PACKAGE_MARKUP_BYTES,
                    "EPUB package markup",
                )

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
            } catch (throwable: Throwable) {
                unpacked.deleteRecursively()
                throw throwable
            }
        }

        private fun validateArchiveMetadata(epub: File, limits: EpubExtractionLimits) {
            ZipFile(epub).use { zip ->
                val entries = zip.entries()
                var entryCount = 0
                var declaredTotal = 0L
                while (entries.hasMoreElements()) {
                    val entry = entries.nextElement()
                    entryCount += 1
                    if (entryCount > limits.maxEntries) {
                        throw ContentLimitExceeded(
                            limits.maxEntries.toLong(),
                            "EPUB entry count",
                        )
                    }
                    if (entry.size >= 0) {
                        checkedLimitedByteCount(
                            currentBytes = 0,
                            additionalBytes = entry.size,
                            maxBytes = limits.maxEntryBytes,
                            limitName = "EPUB entry",
                        )
                        declaredTotal = checkedLimitedByteCount(
                            currentBytes = declaredTotal,
                            additionalBytes = entry.size,
                            maxBytes = limits.maxTotalBytes,
                            limitName = "EPUB total output",
                        )
                        validateCompressionRatio(
                            uncompressedBytes = entry.size,
                            compressedBytes = entry.compressedSize,
                            limits = limits,
                        )
                    }
                }
            }
        }

        private fun unpackAtomically(
            epub: File,
            unpacked: File,
            limits: EpubExtractionLimits,
        ) {
            val tmp = File(unpacked.parentFile, "${unpacked.name}.tmp-${System.nanoTime()}")
            tmp.deleteRecursively()
            tmp.mkdirs()
            try {
                ZipFile(epub).use { zip ->
                    val entries = zip.entries()
                    var streamedTotal = 0L
                    while (entries.hasMoreElements()) {
                        val entry = entries.nextElement()
                        val out = safeChild(tmp, entry.name, tmp)
                        if (entry.isDirectory) {
                            out.mkdirs()
                        } else {
                            out.parentFile?.mkdirs()
                            val totalRemaining = limits.maxTotalBytes - streamedTotal
                            zip.getInputStream(entry).use { input ->
                                FileOutputStream(out).use { output ->
                                    val entryBytes = input.copyToLimited(
                                        out = output,
                                        maxBytes = minOf(limits.maxEntryBytes, totalRemaining),
                                    )
                                    streamedTotal = checkedLimitedByteCount(
                                        currentBytes = streamedTotal,
                                        additionalBytes = entryBytes,
                                        maxBytes = limits.maxTotalBytes,
                                        limitName = "EPUB total output",
                                    )
                                    validateCompressionRatio(
                                        uncompressedBytes = entryBytes,
                                        compressedBytes = entry.compressedSize,
                                        limits = limits,
                                    )
                                }
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
                unpacked.deleteRecursively()
                throw throwable
            }
        }

        private fun validateCompressionRatio(
            uncompressedBytes: Long,
            compressedBytes: Long,
            limits: EpubExtractionLimits,
        ) {
            if (compressedBytes < 0) {
                throw ContentLimitExceeded(
                    limits.maxCompressionRatio,
                    "EPUB compression ratio with unknown compressed size",
                )
            }
            if (compressedBytes == 0L) {
                if (uncompressedBytes > 0) {
                    throw ContentLimitExceeded(
                        limits.maxCompressionRatio,
                        "EPUB compression ratio",
                    )
                }
                return
            }
            val maxUncompressed = if (
                compressedBytes > Long.MAX_VALUE / limits.maxCompressionRatio
            ) {
                Long.MAX_VALUE
            } else {
                compressedBytes * limits.maxCompressionRatio
            }
            if (uncompressedBytes > maxUncompressed) {
                throw ContentLimitExceeded(
                    limits.maxCompressionRatio,
                    "EPUB compression ratio",
                )
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

private fun File.readUtf8Limited(maxBytes: Long, limitName: String): String {
    checkedLimitedByteCount(0, length(), maxBytes, limitName)
    val output = ByteArrayOutputStream(length().coerceAtMost(maxBytes).toInt())
    inputStream().use { input ->
        input.copyToLimited(output, maxBytes)
    }
    return output.toString(Charsets.UTF_8.name())
}

private fun File.readUtf8Prefix(maxBytes: Int): String {
    val output = ByteArrayOutputStream(minOf(length().coerceAtLeast(0), maxBytes.toLong()).toInt())
    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
    var remaining = maxBytes
    inputStream().use { input ->
        while (remaining > 0) {
            val read = input.read(buffer, 0, minOf(buffer.size, remaining))
            if (read < 0) break
            output.write(buffer, 0, read)
            remaining -= read
        }
    }
    return output.toString(Charsets.UTF_8.name())
}

internal fun readerDirectoryBaseUrl(directory: File): String {
    val canonicalDirectory = directory.canonicalFile
    val epubRoot = generateSequence(canonicalDirectory) { it.parentFile }
        .firstOrNull { EPUB_CACHE_DIRECTORY.matches(it.name) }
        ?: return ""
    val readersRoot = epubRoot.parentFile?.canonicalFile ?: return ""
    val rootPrefix = readersRoot.path + File.separator
    if (!canonicalDirectory.path.startsWith(rootPrefix)) return ""
    val relativePath = canonicalDirectory.path
        .removePrefix(rootPrefix)
        .replace(File.separatorChar, '/')
    val encodedPath = URI(null, null, "/$relativePath/", null).rawPath.removePrefix("/")
    return "https://appassets.androidplatform.net/epub/$encodedPath"
}

private val EPUB_CACHE_DIRECTORY = Regex("""epub-[0-9a-f]{40}""")
