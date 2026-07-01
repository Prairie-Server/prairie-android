package org.siloserver.silo.common.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import org.siloserver.silo.common.data.db.SiloDatabase
import org.siloserver.silo.model.section.ResolvedSection
import org.siloserver.silo.model.section.SectionItem
import org.siloserver.silo.network.AuthScopeSnapshot
import kotlinx.coroutines.test.runTest
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

@RunWith(RobolectricTestRunner::class)
class RoomHomeCacheRepositoryTest {

    private val db = Room.inMemoryDatabaseBuilder(
        ApplicationProvider.getApplicationContext(),
        SiloDatabase::class.java,
    ).allowMainThreadQueries().build()

    private var scope: AuthScopeSnapshot? = AuthScopeSnapshot("s1", "p1", "https://s1.example", null)

    private val repo = RoomHomeCacheRepository(
        db = db,
        snapshotProvider = { scope },
        now = { 1000L },
    )

    @AfterTest
    fun tearDown() = db.close()

    private fun section(id: String, item: String) = ResolvedSection(
        id = id,
        sectionType = "continue_watching",
        title = "Continue Watching",
        items = listOf(SectionItem(contentId = item, type = "movie", title = "Movie $item")),
    )

    @Test
    fun roundTripsCachedSections() = runTest {
        assertNull(repo.getCachedHome())
        repo.cacheHome(listOf(section("s-cw", "c1"), section("s-rec", "c2")))
        val back = repo.getCachedHome()
        assertEquals(2, back?.sections?.size)
        assertEquals("s-cw", back?.sections?.get(0)?.id)
        assertEquals("c1", back?.sections?.get(0)?.items?.first()?.contentId)
        assertEquals(1000L, back?.cachedAtMs)
    }

    @Test
    fun cacheIsScopedAndNoOpWithoutScope() = runTest {
        repo.cacheHome(listOf(section("s-cw", "c1")))
        // Different scope → no cached home.
        scope = AuthScopeSnapshot("s2", "p1", "https://s2.example", null)
        assertNull(repo.getCachedHome())
        // No scope → no-op read.
        scope = null
        assertNull(repo.getCachedHome())
    }
}
