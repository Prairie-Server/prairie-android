package org.siloserver.silo.playback

/**
 * Canonical primary subtitle language shared by catalog persistence and
 * mounted-player identity matching.
 *
 * Servers commonly expose ISO 639-2 aliases while Android decoders expose
 * ISO 639-1 tags. Region/script suffixes do not identify a different subtitle
 * artifact for the selection fallback, so matching uses the primary language.
 */
fun canonicalSubtitleLanguage(language: String?): String? {
    val primary = language
        ?.trim()
        ?.takeUnless { it.isBlank() || it.equals("und", ignoreCase = true) }
        ?.lowercase()
        ?.replace('_', '-')
        ?.substringBefore('-')
        ?: return null
    return when (primary) {
        "eng" -> "en"
        "spa" -> "es"
        "fre", "fra" -> "fr"
        "ger", "deu" -> "de"
        "dut", "nld" -> "nl"
        "jpn" -> "ja"
        "dan" -> "da"
        else -> primary
    }
}
