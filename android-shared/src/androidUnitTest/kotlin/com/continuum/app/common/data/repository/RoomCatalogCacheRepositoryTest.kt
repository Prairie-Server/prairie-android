package com.continuum.app.common.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.continuum.app.common.data.db.SiloDatabase
import com.continuum.app.model.catalog.BrowseItem
import com.continuum.app.model.catalog.CatalogResponse
import com.continuum.app.model.personal.UserLibrary
import com.continuum.app.network.AuthScopeSnapshot
import kotlinx.coroutines.test.runTest
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

@RunWith(RobolectricTestRunner::class)
class RoomCatalogCacheRepositoryTest {

    private val db = Room.inMemoryDatabaseBuilder(
        ApplicationProvider.getApplicationContext(),
        SiloDatabase::class.java,
    ).allowMainThreadQueries().build()

    private var scope: AuthScopeSnapshot? = AuthScopeSnapshot("s1", "p1", "https://s1.example", null)
    private val repo = RoomCatalogCacheRepository(db = db, snapshotProvider = { scope }, now = { 1000L })

    @AfterTest
    fun tearDown() = db.close()

    @Test
    fun roundTripsLibraries() = runTest {
        assertNull(repo.getCachedLibraries())
        repo.cacheLibraries(listOf(UserLibrary(id = 1, name = "Movies", type = "movie")))
        val back = repo.getCachedLibraries()
        assertEquals(1, back?.size)
        assertEquals("Movies", back?.first()?.name)
    }

    @Test
    fun roundTripsDefaultLibraryPagePerLibraryId() = runTest {
        repo.cacheDefaultLibraryPage(7, CatalogResponse(total = 1, items = listOf(BrowseItem(contentId = "c1", type = "movie", title = "A"))))
        assertEquals("c1", repo.getCachedDefaultLibraryPage(7)?.items?.first()?.contentId)
        // Distinct key per library id.
        assertNull(repo.getCachedDefaultLibraryPage(8))
    }

    @Test
    fun roundTripsLibrarySectionsPerLibraryId() = runTest {
        val section = com.continuum.app.model.section.ResolvedSection(
            id = "recently_added",
            sectionType = "recently_added",
            title = "Recently Added",
            items = listOf(com.continuum.app.model.section.SectionItem(contentId = "c1", type = "movie", title = "A")),
        )
        repo.cacheLibrarySections(7, listOf(section))
        assertEquals("recently_added", repo.getCachedLibrarySections(7)?.first()?.id)
        assertNull(repo.getCachedLibrarySections(8))
        // Distinct from the browse-grid key for the same library id.
        assertNull(repo.getCachedDefaultLibraryPage(7))
    }

    @Test
    fun cacheIsScopedAndNoOpWithoutScope() = runTest {
        repo.cacheLibraries(listOf(UserLibrary(id = 1, name = "Movies", type = "movie")))
        scope = AuthScopeSnapshot("s2", "p1", "https://s2.example", null)
        assertNull(repo.getCachedLibraries())
        scope = null
        assertNull(repo.getCachedLibraries())
    }
}
