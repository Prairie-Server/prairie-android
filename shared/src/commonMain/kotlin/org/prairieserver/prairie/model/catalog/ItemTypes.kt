package org.prairieserver.prairie.model.catalog

/**
 * Predicates over [ItemDetail.type] (catalog item types, always singular)
 * so detail screens stop hand-spelling type literals. Library-level
 * taxonomy (plural forms, `reading`, mode mapping) lives in
 * model/navigation/MediaMode.kt.
 */
private val bookLikeItemTypes = setOf(
    "book",
    "ebook",
    "comic",
    "manga",
)

private fun normalizedItemType(type: String?): String? =
    type?.trim()?.lowercase()?.takeIf { it.isNotEmpty() }

fun isAudiobookItemType(type: String?): Boolean =
    normalizedItemType(type) == "audiobook"

fun isEpisodeItemType(type: String?): Boolean =
    normalizedItemType(type) == "episode"

fun isBookLikeItemType(type: String?): Boolean =
    normalizedItemType(type) in bookLikeItemTypes
