package org.prairieserver.prairie.tv.data.preferences

import org.prairieserver.prairie.model.personal.UserLibrary
import org.prairieserver.prairie.tv.ui.shell.TvLibraryTabType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Exercises [TvLibraryScopeStore.resolve], the pure scope-resolution rule
 * (the getters/setters need a Context-backed DataStore, so the testable
 * surface is the resolution rule, mirroring tvOS
 * `TVLibraryScopeStore.resolvedLibrary`).
 */
class TvLibraryScopeStoreTest {

    private fun lib(id: Int, type: String, name: String = "lib$id") =
        UserLibrary(id = id, name = name, type = type)

    private val movieA = lib(1, "movies", "Movies A")
    private val movieB = lib(2, "movie", "Movies B")
    private val series = lib(3, "series", "Series")
    private val movies = listOf(movieA, movieB)

    @Test
    fun `stored id resolves to that library when present`() {
        assertEquals(
            movieB,
            TvLibraryScopeStore.resolve(storedId = 2, type = TvLibraryTabType.Movies, libraries = movies),
        )
    }

    @Test
    fun `null stored id falls back to first of type`() {
        assertEquals(
            movieA,
            TvLibraryScopeStore.resolve(storedId = null, type = TvLibraryTabType.Movies, libraries = movies),
        )
    }

    @Test
    fun `stale stored id falls back to first of type`() {
        assertEquals(
            movieA,
            TvLibraryScopeStore.resolve(storedId = 999, type = TvLibraryTabType.Movies, libraries = movies),
        )
    }

    @Test
    fun `only libraries of the type are considered`() {
        // A stored id pointing at a series library must not resolve under Movies.
        val mixed = listOf(series, movieA, movieB)
        assertEquals(
            movieA,
            TvLibraryScopeStore.resolve(storedId = 3, type = TvLibraryTabType.Movies, libraries = mixed),
        )
    }

    @Test
    fun `no libraries of the type resolves to null`() {
        assertNull(
            TvLibraryScopeStore.resolve(storedId = 1, type = TvLibraryTabType.Series, libraries = movies),
        )
        assertNull(
            TvLibraryScopeStore.resolve(storedId = null, type = TvLibraryTabType.Music, libraries = movies),
        )
    }
}
