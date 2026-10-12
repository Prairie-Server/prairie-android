package org.prairieserver.prairie.common.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import org.prairieserver.prairie.common.data.db.PrairieDatabase
import org.prairieserver.prairie.model.catalog.BrowseItem
import org.prairieserver.prairie.model.catalog.CatalogResponse
import org.prairieserver.prairie.model.personal.UserLibrary
import org.prairieserver.prairie.network.AuthScopeSnapshot
import org.prairieserver.prairie.network.DefaultIdentityTransitionBarrier
import org.prairieserver.prairie.network.IdentityTransitionKind
import org.prairieserver.prairie.repository.port.CatalogCacheWriteLease
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
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
        PrairieDatabase::class.java,
    ).allowMainThreadQueries().build()

    private var scope: AuthScopeSnapshot? = AuthScopeSnapshot("s1", "p1", "https://s1.example", null)
    private val repo = RoomCatalogCacheRepository(db = db, snapshotProvider = { scope }, now = { 1000L })

    @AfterTest
    fun tearDown() = db.close()

    @Test
    fun scopedV2SectionsExcludeLegacyAndReplacementAuthority() = runTest {
        val owner = scope!!
        val sections = listOf(org.prairieserver.prairie.model.section.ResolvedSection("row", "custom", "Row"))
        repo.cacheLibrarySections(7, sections)
        assertNull(repo.getCachedLibrarySectionsV2(7, owner))
        repo.cacheLibrarySectionsV2(7, sections, owner)
        assertEquals(sections, repo.getCachedLibrarySectionsV2(7, owner))
        assertNull(repo.getCachedLibrarySectionsV2(8, owner))
        scope = owner.copy(profileToken = "new")
        assertNull(repo.getCachedLibrarySectionsV2(7, scope!!))
        assertNull(repo.getCachedLibrarySectionsV2(7, owner))
        repo.cacheLibrarySectionsV2(7, sections, owner)
        assertNull(repo.getCachedLibrarySectionsV2(7, scope!!))
        scope = owner.copy(credentialEpoch = 2)
        assertNull(repo.getCachedLibrarySectionsV2(7, scope!!))
    }

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
        val section = org.prairieserver.prairie.model.section.ResolvedSection(
            id = "recently_added",
            sectionType = "recently_added",
            title = "Recently Added",
            items = listOf(org.prairieserver.prairie.model.section.SectionItem(contentId = "c1", type = "movie", title = "A")),
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

    @Test
    fun writeStartedBeforeProfileSwitchIsNotAttributedToNewProfile() = runTest {
        val snapshotRequested = CompletableDeferred<Unit>()
        val releaseSnapshot = CompletableDeferred<Unit>()
        val identityTransitions = DefaultIdentityTransitionBarrier()
        val delayedRepo = RoomCatalogCacheRepository(
            db = db,
            snapshotProvider = {
                snapshotRequested.complete(Unit)
                releaseSnapshot.await()
                scope
            },
            identityTransitions = identityTransitions,
            now = { 1000L },
        )

        val oldProfileWrite = async {
            delayedRepo.cacheLibraries(listOf(UserLibrary(id = 1, name = "Profile A", type = "movie")))
        }
        snapshotRequested.await()
        identityTransitions.changing(IdentityTransitionKind.PROFILE_SWITCH) {
            scope = AuthScopeSnapshot("s1", "p2", "https://s1.example", null)
        }
        releaseSnapshot.complete(Unit)
        oldProfileWrite.await()

        assertNull(delayedRepo.getCachedLibraries())
    }

    @Test
    fun writeRequestedByOldProfileButInvokedAfterSwitchIsNotAttributedToNewProfile() = runTest {
        val identityTransitions = DefaultIdentityTransitionBarrier()
        val oldProfileGeneration = identityTransitions.generation.value
        val guardedRepo = RoomCatalogCacheRepository(
            db = db,
            snapshotProvider = { scope },
            identityTransitions = identityTransitions,
            now = { 1000L },
        )
        identityTransitions.changing(IdentityTransitionKind.PROFILE_SWITCH) {
            scope = AuthScopeSnapshot("s1", "p2", "https://s1.example", null)
        }

        guardedRepo.cacheLibraries(
            listOf(UserLibrary(id = 1, name = "Profile A", type = "movie")),
            CatalogCacheWriteLease(oldProfileGeneration),
        )

        assertEquals(0L, oldProfileGeneration)
        assertNull(guardedRepo.getCachedLibraries())
    }
    @Test
    fun languageInvalidationPurgesOnlyCurrentScopesDetailMetadata() = runTest {
        val owner = scope!!
        val detail = org.prairieserver.prairie.model.catalog.ItemDetail(contentId = "c1", type = "series", title = "Old language")
        val seasons = org.prairieserver.prairie.model.catalog.SeasonsResponse()
        val episodes = org.prairieserver.prairie.model.catalog.EpisodesResponse()
        suspend fun populate() {
            repo.cacheItemDetail("c1", detail)
            repo.cacheSeasons("c1", seasons)
            repo.cacheEpisodes("c1", 1, episodes)
            repo.cacheLibraries(listOf(UserLibrary(id = 1, name = "Shows", type = "series")))
        }
        populate()
        scope = owner.copy(profileId = "p2")
        populate()
        scope = owner
        repo.invalidateDetailMetadata()
        assertNull(repo.getCachedItemDetail("c1"))
        assertNull(repo.getCachedSeasons("c1"))
        assertNull(repo.getCachedEpisodes("c1", 1))
        assertEquals("Shows", repo.getCachedLibraries()?.single()?.name)
        scope = owner.copy(profileId = "p2")
        assertEquals(detail, repo.getCachedItemDetail("c1"))
        assertEquals(seasons, repo.getCachedSeasons("c1"))
        assertEquals(episodes, repo.getCachedEpisodes("c1", 1))
    }

    @Test
    fun languagePurgeAfterProfileSwitchDeletesTheWriterAndRetainsTheActiveProfilesMetadata() = runTest {
        val owner = scope!!
        val detail = org.prairieserver.prairie.model.catalog.ItemDetail(contentId = "c1", type = "series", title = "Cached")
        val seasons = org.prairieserver.prairie.model.catalog.SeasonsResponse()
        val episodes = org.prairieserver.prairie.model.catalog.EpisodesResponse()
        suspend fun populate() {
            repo.cacheItemDetail("c1", detail)
            repo.cacheSeasons("c1", seasons)
            repo.cacheEpisodes("c1", 1, episodes)
        }
        populate()
        val language = org.prairieserver.prairie.domain.settings.CatalogLanguageRevision()
        language.changed(org.prairieserver.prairie.repository.port.CatalogCacheScope(owner.serverId, owner.profileId!!))
        scope = owner.copy(profileId = "p2")
        populate()
        val client = io.ktor.client.HttpClient(io.ktor.client.engine.mock.MockEngine { error("No live request expected") })
        val catalog = org.prairieserver.prairie.repository.CatalogRepository(
            org.prairieserver.prairie.network.api.CatalogApi(client), repo, catalogLanguage = language,
        )
        catalog.invalidateLocalizedMetadata()
        assertEquals(detail, repo.getCachedItemDetail("c1"))
        assertEquals(seasons, repo.getCachedSeasons("c1"))
        assertEquals(episodes, repo.getCachedEpisodes("c1", 1))
        scope = owner
        assertNull(catalog.getCachedItemDetail("c1"))
        assertNull(catalog.getCachedSeasons("c1"))
        assertNull(catalog.getCachedEpisodes("c1", 1))
    }

}
