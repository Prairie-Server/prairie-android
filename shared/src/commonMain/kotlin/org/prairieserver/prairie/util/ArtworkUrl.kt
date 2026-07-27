package org.prairieserver.prairie.util

/**
 * Artwork URL helpers mirroring prairie-server `internal/artworkkey` / web `artworkUrl.ts`.
 * Canonical cache keys stay `.webp`; clients pick the best sibling immediately using
 * [ImageFormats] (one-time capability detection) instead of AVIF-first trial-and-error.
 */
object ArtworkUrl {
    /**
     * AVIF sibling of a canonical `.webp` URL/path. Non-WebP inputs return null.
     * Query/fragment are preserved for signed CDN URLs.
     */
    fun webPAVIFSibling(objectPath: String?): String? = webPFormatSibling(objectPath, ".avif")

    /**
     * PNG sibling of a canonical `.webp` URL/path. Non-WebP inputs return null.
     */
    fun webPPNGSibling(objectPath: String?): String? = webPFormatSibling(objectPath, ".png")

    /**
     * Ordered load candidates using the process [ImageFormats] preference list.
     */
    fun candidates(objectPath: String?): List<String> {
        val trimmed = objectPath?.trim().orEmpty()
        if (trimmed.isEmpty()) return emptyList()
        val byFormat = mapOf(
            ImageFormats.WEBP to trimmed,
            ImageFormats.AVIF to webPAVIFSibling(trimmed),
            ImageFormats.PNG to webPPNGSibling(trimmed),
        )
        val out = mutableListOf<String>()
        val seen = mutableSetOf<String>()
        for (format in ImageFormats.preferred()) {
            val url = byFormat[format]?.takeIf { it.isNotBlank() } ?: continue
            if (seen.add(url)) out.add(url)
        }
        if (out.isEmpty()) out.add(trimmed)
        return out
    }

    /** Best immediate artwork URL for this device without codec probing. */
    fun preferred(objectPath: String): String = candidates(objectPath).firstOrNull() ?: objectPath

    private fun webPFormatSibling(objectPath: String?, ext: String): String? {
        val trimmed = objectPath?.trim().orEmpty()
        if (trimmed.isEmpty()) return null

        if (trimmed.contains("://")) {
            val queryIndex = trimmed.indexOf('?')
            val fragmentIndex = trimmed.indexOf('#')
            val pathEnd =
                when {
                    queryIndex >= 0 && fragmentIndex >= 0 -> minOf(queryIndex, fragmentIndex)
                    queryIndex >= 0 -> queryIndex
                    fragmentIndex >= 0 -> fragmentIndex
                    else -> trimmed.length
                }
            val path = trimmed.substring(0, pathEnd)
            val suffix = trimmed.substring(pathEnd)
            val rewritten = rewriteWebPPath(path, ext) ?: return null
            return rewritten + suffix
        }

        return rewriteWebPPath(trimmed, ext)
    }

    private fun rewriteWebPPath(path: String, ext: String): String? {
        val slash = path.lastIndexOf('/')
        val base = if (slash >= 0) path.substring(slash + 1) else path
        val dot = base.lastIndexOf('.')
        if (dot < 0) return null
        val cur = base.substring(dot)
        if (!cur.equals(".webp", ignoreCase = true)) return null
        val prefix = if (slash >= 0) path.substring(0, slash + 1) else ""
        val stem = base.substring(0, dot)
        return "$prefix$stem$ext"
    }
}
