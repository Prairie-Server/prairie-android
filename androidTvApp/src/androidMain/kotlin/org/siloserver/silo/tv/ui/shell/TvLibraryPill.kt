package org.siloserver.silo.tv.ui.shell

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.GridView
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * A section ("pill") offered within a library type's cascade flyout (§3 /
 * §5.3). 1:1 with tvOS `TVLibraryPill` (`TVLibraryPillRow.swift`):
 *
 * - [Recommended] — the server-driven landing feed (recommendations +
 *   sections); always first / the landing default.
 * - [Collections] — curated collections within the library.
 * - [Browse] — the full library, browsable as an A–Z grid.
 *
 * Every library type offers all three sections (tvOS `set(for:)` returns
 * `allCases` for every type).
 */
enum class TvLibraryPill {
    Recommended,
    Collections,
    Browse;

    /** Pill / flyout-row label. */
    val title: String
        get() = when (this) {
            Recommended -> "Recommended"
            Collections -> "Collections"
            Browse -> "Browse"
        }

    /** Glyph shown beside the section name in the cascade flyout (§5.3). */
    val icon: ImageVector
        get() = when (this) {
            Recommended -> Icons.Filled.AutoAwesome // tvOS `sparkles`
            Collections -> Icons.Filled.Collections // tvOS `square.stack.3d.up`
            Browse -> Icons.Filled.GridView // tvOS `square.grid.2x2`
        }

    companion object {
        /**
         * Sections offered for [type]. Mirrors tvOS `TVLibraryPill.set(for:)`,
         * which returns every case for every library type: Recommended ·
         * Collections · Browse.
         */
        fun set(type: TvLibraryTabType): List<TvLibraryPill> = entries.toList()
    }
}
