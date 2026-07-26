package org.prairieserver.prairie.util

/**
 * Artwork URL helpers mirroring prairie-server `internal/artworkkey` / web `artworkUrl.ts`.
 * Canonical cache keys stay `.webp`; clients try AVIF → WebP → PNG for older devices.
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
     * Ordered load candidates: AVIF → WebP → PNG when the input is WebP.
     */
    fun candidates(objectPath: String?): List<String> {
        val trimmed = objectPath?.trim().orEmpty()
        if (trimmed.isEmpty()) return emptyList()
        val out = mutableListOf<String>()
        webPAVIFSibling(trimmed)?.let { out.add(it) }
        out.add(trimmed)
        webPPNGSibling(trimmed)?.let { out.add(it) }
        return out
    }

    /** Prefer the AVIF sibling when one can be derived; otherwise the original URL. */
    fun preferred(objectPath: String): String = webPAVIFSibling(objectPath) ?: objectPath

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
