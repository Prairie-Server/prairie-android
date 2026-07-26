package org.prairieserver.prairie.repository

import org.prairieserver.prairie.model.catalog.CatalogResponse
import org.prairieserver.prairie.model.catalog.EpisodesResponse
import org.prairieserver.prairie.model.catalog.ItemDetail
import org.prairieserver.prairie.model.catalog.SeasonsResponse
import org.prairieserver.prairie.model.catalog.WatchDetail
import org.prairieserver.prairie.network.ApiResult
import org.prairieserver.prairie.network.PrairieJson
import org.prairieserver.prairie.network.api.CatalogApi
import org.prairieserver.prairie.repository.port.CatalogCachePort
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class CatalogRepositoryTest {

    private class FakeCache : CatalogCachePort {
        var cachedLibraryId: Int? = null
        var cachedPage: CatalogResponse? = null
        var cachedDetailId: String? = null
        var cachedDetail: ItemDetail? = null
        var cachedSeasonsId: String? = null
        var cachedSeasons: SeasonsResponse? = null
        var cachedEpisodesKey: String? = null
        var cachedEpisodes: EpisodesResponse? = null
        var presetPage: CatalogResponse? = null
        var presetDetail: ItemDetail? = null
        var presetSeasons: SeasonsResponse? = null
        var presetEpisodes: EpisodesResponse? = null

        override suspend fun cacheDefaultLibraryPage(libraryId: Int, response: CatalogResponse) {
            cachedLibraryId = libraryId
            cachedPage = response
        }
        override suspend fun getCachedDefaultLibraryPage(libraryId: Int) = presetPage
        override suspend fun cacheItemDetail(contentId: String, detail: ItemDetail) {
            cachedDetailId = contentId
            cachedDetail = detail
        }
        override suspend fun getCachedItemDetail(contentId: String) = presetDetail
        override suspend fun cacheSeasons(seriesId: String, response: SeasonsResponse) {
            cachedSeasonsId = seriesId
            cachedSeasons = response
        }
        override suspend fun getCachedSeasons(seriesId: String) = presetSeasons
        override suspend fun cacheEpisodes(seriesId: String, seasonNumber: Int, response: EpisodesResponse) {
            cachedEpisodesKey = "$seriesId/$seasonNumber"
            cachedEpisodes = response
        }
        override suspend fun getCachedEpisodes(seriesId: String, seasonNumber: Int) = presetEpisodes
    }

    private fun repo(status: HttpStatusCode, body: String, cache: CatalogCachePort = FakeCache()): CatalogRepository {
        val client = HttpClient(
            MockEngine { respond(body, status, headersOf(HttpHeaders.ContentType, "application/json")) },
        ) { install(ContentNegotiation) { json(PrairieJson) } }
        return CatalogRepository(CatalogApi(client), cache)
    }

    @Test
    fun browseCachesDefaultLibraryFirstPage() = runTest {
        val cache = FakeCache()
        val result = repo(HttpStatusCode.OK, """{"total":1,"has_more":false,"items":[]}""", cache)
            .browse(libraryId = 9)
        assertIs<ApiResult.Success<*>>(result)
        assertEquals(9, cache.cachedLibraryId)
    }

    @Test
    fun browseServesCacheOn5xxForDefaultPage() = runTest {
        val cache = FakeCache().apply {
            presetPage = CatalogResponse(total = 3, items = emptyList())
        }
        val result = repo(HttpStatusCode.ServiceUnavailable, "{}", cache).browse(libraryId = 9)
        assertIs<ApiResult.Success<CatalogResponse>>(result)
        assertEquals(3, result.data.total)
    }

    @Test
    fun browseDoesNotCacheFilteredQueries() = runTest {
        val cache = FakeCache()
        repo(HttpStatusCode.OK, """{"total":0,"has_more":false,"items":[]}""", cache)
            .browse(libraryId = 9, query = "batman")
        assertEquals(null, cache.cachedLibraryId)
    }

    @Test
    fun getItemDetailCachesAndFallsBack() = runTest {
        val cache = FakeCache()
        val ok = repo(
            HttpStatusCode.OK,
            """{"content_id":"m1","type":"movie","title":"M"}""",
            cache,
        ).getItemDetail("m1")
        assertIs<ApiResult.Success<*>>(ok)
        assertEquals("m1", cache.cachedDetailId)

        cache.presetDetail = ItemDetail(contentId = "m1", type = "movie", title = "Cached")
        val fallback = repo(HttpStatusCode.BadGateway, "{}", cache).getItemDetail("m1")
        assertIs<ApiResult.Success<ItemDetail>>(fallback)
        assertEquals("Cached", fallback.data.title)
        assertEquals("Cached", CatalogRepository(CatalogApi(
            HttpClient(MockEngine { respond("{}", HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json")) })
        ), cache).getCachedItemDetail("m1")?.title)
    }

    @Test
    fun seasonsEpisodesWatchDetailAndPassThroughs() = runTest {
        val cache = FakeCache()
        assertIs<ApiResult.Success<*>>(
            repo(HttpStatusCode.OK, """{"seasons":[]}""", cache).getSeasons("s1"),
        )
        assertEquals("s1", cache.cachedSeasonsId)
        cache.presetSeasons = SeasonsResponse()
        assertIs<ApiResult.Success<*>>(
            repo(HttpStatusCode.InternalServerError, "{}", cache).getSeasons("s1"),
        )

        assertIs<ApiResult.Success<*>>(
            repo(HttpStatusCode.OK, """{"episodes":[]}""", cache).getEpisodes("s1", 1),
        )
        assertEquals("s1/1", cache.cachedEpisodesKey)
        cache.presetEpisodes = EpisodesResponse()
        assertIs<ApiResult.Success<*>>(
            repo(HttpStatusCode.ServiceUnavailable, "{}", cache).getEpisodes("s1", 1),
        )

        val watch = repo(
            HttpStatusCode.OK,
            """{"content_id":"m1","type":"movie","title":"M"}""",
        ).getWatchDetail("m1")
        assertIs<ApiResult.Success<WatchDetail>>(watch)

        assertIs<ApiResult.Success<*>>(repo(HttpStatusCode.OK, """{"genres":[]}""").getFilters(1, true))
        assertIs<ApiResult.Success<*>>(
            repo(HttpStatusCode.OK, """{"groups":[],"has_more":false}""").getAudiobookGroups(1, "author"),
        )
        assertIs<ApiResult.Success<*>>(repo(HttpStatusCode.OK, """{"episodes":[]}""").getItemEpisodes("m1"))
        assertIs<ApiResult.Success<*>>(repo(HttpStatusCode.OK, "[]").getItemVersions("m1"))
        assertIs<ApiResult.Success<*>>(repo(HttpStatusCode.OK, "[]").searchPeople("ada"))
        assertIs<ApiResult.Success<*>>(repo(HttpStatusCode.NoContent, "").refreshPerson(7))
        assertIs<ApiResult.Success<*>>(
            repo(HttpStatusCode.OK, """{"id":7,"name":"Ada"}""").getPerson(7),
        )
        assertIs<ApiResult.Success<*>>(
            repo(HttpStatusCode.OK, """{"total":0,"has_more":false,"items":[]}""")
                .getPersonItems(7, mediaType = "movie", offset = 0, limit = 10, snapshotAt = "t"),
        )
    }
}
