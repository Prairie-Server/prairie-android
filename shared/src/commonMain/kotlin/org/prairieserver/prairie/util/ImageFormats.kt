package org.prairieserver.prairie.util

/**
 * One-time raster decode capability for Prairie Android clients.
 * Configure at process start from API level (AVIF requires API 31+).
 * Advertised to the server via `X-Prairie-Image-Formats`.
 */
object ImageFormats {
    const val AVIF = "avif"
    const val WEBP = "webp"
    const val PNG = "png"

    private val lock = Any()
    private var preferred: List<String> = listOf(WEBP, PNG)

    /** Ordered best-first format tokens (`avif`, `webp`, `png`). */
    fun preferred(): List<String> = synchronized(lock) { preferred }

    /** Value for the `X-Prairie-Image-Formats` request header. */
    fun headerValue(): String = preferred().joinToString(",")

    /**
     * Replace the process-wide preference list. Unknown tokens are dropped;
     * duplicates are removed. Empty input leaves the previous value unchanged.
     */
    fun configure(formats: List<String>) {
        val next = formats.map { it.trim().lowercase() }
            .filter { it == AVIF || it == WEBP || it == PNG }
            .distinct()
        if (next.isEmpty()) return
        synchronized(lock) { preferred = next }
    }

    /** API-31+ devices get AVIF first; older devices stay WebP → PNG. */
    fun configureForApiLevel(apiLevel: Int) {
        configure(
            if (apiLevel >= 31) {
                listOf(AVIF, WEBP, PNG)
            } else {
                listOf(WEBP, PNG)
            },
        )
    }

    /** Test helper — restores the conservative pre-probe default. */
    fun resetForTests() {
        synchronized(lock) { preferred = listOf(WEBP, PNG) }
    }
}
