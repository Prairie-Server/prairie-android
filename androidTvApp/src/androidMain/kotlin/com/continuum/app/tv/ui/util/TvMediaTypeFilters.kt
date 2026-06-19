package com.continuum.app.tv.ui.util

import com.continuum.app.model.catalog.BrowseItem
import com.continuum.app.model.navigation.isAudiobookLikeLibraryType
import com.continuum.app.model.navigation.isEbookLikeLibraryType
import com.continuum.app.model.personal.UserLibrary
import com.continuum.app.model.section.ResolvedSection
import kotlin.jvm.JvmName

/**
 * TV hides ebook-like content (no reader on TV) but keeps audiobooks.
 * Both predicates delegate to the shared reading taxonomy in MediaMode.kt
 * so the type sets cannot drift between platforms; only the TV-specific
 * hide/keep decision lives here.
 */
fun isTvHiddenMediaType(type: String?): Boolean = isEbookLikeLibraryType(type)

fun isAudiobookMediaType(type: String?): Boolean = isAudiobookLikeLibraryType(type)

fun tvArtworkAspectRatioForMediaType(type: String?): Float? =
    if (isAudiobookMediaType(type)) 1f else null

@JvmName("browseItemsVisibleOnTv")
fun Iterable<BrowseItem>.visibleOnTv(): List<BrowseItem> =
    filterNot { isTvHiddenMediaType(it.type) }

@JvmName("userLibrariesVisibleOnTv")
fun Iterable<UserLibrary>.visibleOnTv(): List<UserLibrary> =
    filterNot { isTvHiddenMediaType(it.type) }

@JvmName("resolvedSectionsVisibleOnTv")
fun Iterable<ResolvedSection>.visibleOnTv(): List<ResolvedSection> =
    mapNotNull { section ->
        val visibleItems = section.items.filterNot { isTvHiddenMediaType(it.type) }
        section.takeIf { visibleItems.isNotEmpty() }?.copy(items = visibleItems)
    }

/**
 * Catalog `mediaType` to request when browsing a library of [type]. Returns
 * null for types that don't map to a known catalog media type (e.g. music/
 * audio) so the browse is scoped by `libraryId` alone instead of being wrongly
 * forced to "movie" — which previously hid every item in a music library.
 */
fun tvCatalogMediaTypeFor(type: String): String? = when (type.lowercase()) {
    "movie", "movies" -> "movie"
    "series", "shows", "tv" -> "series"
    "audiobook", "audiobooks" -> "audiobook"
    else -> null
}
