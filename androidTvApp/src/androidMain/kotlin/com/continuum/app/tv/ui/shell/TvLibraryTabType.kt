package com.continuum.app.tv.ui.shell

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.LocalMovies
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Tv
import androidx.compose.ui.graphics.vector.ImageVector
import com.continuum.app.model.navigation.isAudiobookLikeLibraryType
import com.continuum.app.model.personal.UserLibrary

/**
 * A library content type that can surface as a root tab (Skyline §3.1).
 *
 * Tabs represent *types*, not individual server libraries — a type's tab
 * appears only when the active profile can see at least one library of that
 * type. Mirrors tvOS `TVLibraryTabType` (titles, icons, libraries header,
 * and the [matches] classification rule).
 *
 * Classification reuses the shared [com.continuum.app.model.navigation]
 * library-type string sets so Android and the type→MediaMode mapping stay in
 * lockstep; nothing here hardcodes raw type strings except the two type names
 * (`movies`/`music`) tvOS itself special-cases.
 */
enum class TvLibraryTabType {
    Movies,
    Series,
    Music,
    Audiobooks;

    /** Top-bar tab label / cascade context title. */
    val title: String
        get() = when (this) {
            Movies -> "Movies"
            Series -> "Series"
            Music -> "Music"
            Audiobooks -> "Audiobooks"
        }

    /**
     * Quiet type-cue glyph for a library row in the cascade level-1 panel
     * (§5.3). Drawn from the same icon set [com.continuum.app.tv.ui.screens.libraries]
     * already uses; `MusicNote` is the closest available stand-in for tvOS's
     * `music.note`.
     */
    val icon: ImageVector
        get() = when (this) {
            Movies -> Icons.Filled.LocalMovies
            Series -> Icons.Filled.Tv
            Music -> Icons.Filled.MusicNote
            Audiobooks -> Icons.Filled.Headphones
        }

    /** Mono section header for the cascade level-1 panel (§5.3). */
    val librariesHeader: String
        get() = when (this) {
            Movies -> "MOVIE LIBRARIES"
            Series -> "SERIES LIBRARIES"
            Music -> "MUSIC LIBRARIES"
            Audiobooks -> "AUDIOBOOK LIBRARIES"
        }

    /**
     * Whether a server [library] belongs under this tab. Mirrors tvOS
     * `TVLibraryTabType.matches`:
     * - Movies → the `movies` video type.
     * - Series → series/show video types.
     * - Music → the `music` audio type.
     * - Audiobooks → audiobook-like types (delegated to the shared set).
     */
    fun matches(library: UserLibrary): Boolean {
        val type = library.type.trim().lowercase()
        return when (this) {
            Movies -> type in MOVIE_TYPES
            Series -> type in SERIES_TYPES
            Music -> type in MUSIC_TYPES
            Audiobooks -> isAudiobookLikeLibraryType(library.type)
        }
    }

    companion object {
        // Subsets of the shared videoLibraryTypes set, split by tvOS's
        // movies-vs-series tab distinction (which the shared set doesn't make).
        // Movies also absorbs the generic "video" type so a video library can
        // never be orphaned without a tab (tvOS matches "movies" exactly and
        // would leave a bare "video" library unreachable — we don't).
        private val MOVIE_TYPES = setOf("movie", "movies", "video")
        private val SERIES_TYPES = setOf("series", "show", "shows", "tv")

        // The audio side of the shared audioLibraryTypes set, minus the
        // audiobook-like types which surface under their own tab.
        private val MUSIC_TYPES = setOf("music", "album", "albums", "artist", "artists", "audio")
    }
}
