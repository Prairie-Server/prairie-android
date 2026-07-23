package org.siloserver.silo.android.ui.screens.reader.reflow

import android.webkit.WebResourceResponse
import androidx.webkit.WebViewAssetLoader
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileInputStream
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.nio.file.Files

/**
 * Serves passive resources from extracted, content-addressed EPUB directories.
 *
 * The loader calls this handler with paths relative to `/epub/`. Every path is
 * decoded, normalized, and canonicalized before opening it. Symlinks are
 * rejected even when their targets remain inside [readersRoot], so a later
 * filesystem change cannot silently alter the resource boundary.
 */
internal class EpubResourcePathHandler(readersRoot: File) : WebViewAssetLoader.PathHandler {
    private val root = readersRoot.canonicalFile
    private val rootPath = root.toPath()

    override fun handle(path: String): WebResourceResponse {
        val decoded = decodePath(path) ?: return emptyNotFoundResponse()
        if (decoded.startsWith("/") || decoded.contains('\\')) return emptyNotFoundResponse()

        val segments = decoded.split('/')
        if (
            segments.isEmpty() ||
            !EPUB_DIRECTORY.matches(segments.first()) ||
            segments.any { it.isEmpty() || it == "." || it == ".." }
        ) {
            return emptyNotFoundResponse()
        }

        val normalizedPath = rootPath.resolve(decoded).normalize()
        if (!normalizedPath.startsWith(rootPath)) return emptyNotFoundResponse()
        if (containsSymbolicLink(normalizedPath.toFile())) return emptyNotFoundResponse()

        val file = runCatching { normalizedPath.toFile().canonicalFile }.getOrNull()
            ?: return emptyNotFoundResponse()
        if (file.toPath() != normalizedPath || !file.isFile) return emptyNotFoundResponse()

        return WebResourceResponse(
            mimeTypeFor(file),
            encodingFor(file),
            200,
            "OK",
            mapOf(
                "Cache-Control" to "no-store",
                "X-Content-Type-Options" to "nosniff",
            ),
            FileInputStream(file),
        )
    }

    private fun containsSymbolicLink(file: File): Boolean {
        var current = file
        while (current.toPath() != rootPath) {
            if (Files.isSymbolicLink(current.toPath())) return true
            current = current.parentFile ?: return true
        }
        return false
    }

    private fun decodePath(path: String): String? {
        var decoded = path
        repeat(MAX_DECODE_PASSES) {
            val next = runCatching {
                URLDecoder.decode(decoded.replace("+", "%2B"), StandardCharsets.UTF_8.name())
            }.getOrNull() ?: return null
            if (next == decoded) return decoded
            decoded = next
        }
        return null
    }

    private fun mimeTypeFor(file: File): String =
        when (file.extension.lowercase()) {
            "avif" -> "image/avif"
            "css" -> "text/css"
            "gif" -> "image/gif"
            "htm", "html" -> "text/html"
            "jpeg", "jpg" -> "image/jpeg"
            "otf" -> "font/otf"
            "png" -> "image/png"
            "svg", "svgz" -> "image/svg+xml"
            "ttf" -> "font/ttf"
            "webp" -> "image/webp"
            "woff" -> "font/woff"
            "woff2" -> "font/woff2"
            "xhtml" -> "application/xhtml+xml"
            else -> "application/octet-stream"
        }

    private fun encodingFor(file: File): String? =
        when (file.extension.lowercase()) {
            "css", "htm", "html", "svg", "svgz", "xhtml" -> StandardCharsets.UTF_8.name()
            else -> null
        }

    companion object {
        private val EPUB_DIRECTORY = Regex("""epub-[0-9a-f]{40}""")
        private const val MAX_DECODE_PASSES = 8
    }
}

internal fun emptyNotFoundResponse(): WebResourceResponse =
    WebResourceResponse(
        "text/plain",
        StandardCharsets.UTF_8.name(),
        404,
        "Not Found",
        mapOf(
            "Cache-Control" to "no-store",
            "X-Content-Type-Options" to "nosniff",
        ),
        ByteArrayInputStream(ByteArray(0)),
    )
