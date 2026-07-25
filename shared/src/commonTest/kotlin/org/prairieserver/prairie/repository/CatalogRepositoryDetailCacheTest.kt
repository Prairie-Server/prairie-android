package org.prairieserver.prairie.repository

import org.prairieserver.prairie.model.catalog.ItemDetail
import org.prairieserver.prairie.model.catalog.SeasonsResponse
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
import kotlin.test.assertTrue

/** Locks the cache-with-fallback contract on [CatalogRepository.getItemDetail]. */
class CatalogRepositoryDetailCacheTest {

    private class FakeCache(
        val preset: ItemDetail? = null,
        val seasonsPreset: SeasonsResponse? = null,
    ) : CatalogCachePort {
        var cachedId: String? = null
        override suspend fun cacheItemDetail(contentId: String, detail: ItemDetail) { cachedId = contentId }
        override suspend fun getCachedItemDetail(contentId: String): ItemDetail? = preset
        override suspend fun getCachedSeasons(seriesId: String): SeasonsResponse? = seasonsPreset
    }

    private fun repo(status: HttpStatusCode, body: String, cache: CatalogCachePort): CatalogRepository {
        val client = HttpClient(
            MockEngine { respond(body, status, headersOf(HttpHeaders.ContentType, "application/json")) },
        ) {
            install(ContentNegotiation) { json(PrairieJson) }
        }
        return CatalogRepository(CatalogApi(client), cache)
    }

    private fun repoThatFailsOnNetwork(cache: CatalogCachePort): CatalogRepository {
        val client = HttpClient(
            MockEngine { error("Network should not be used for a cached detail peek") },
        ) {
            install(ContentNegotiation) { json(PrairieJson) }
        }
        return CatalogRepository(CatalogApi(client), cache)
    }

    @Test
    fun cachesOnSuccess() = runTest {
        val cache = FakeCache(preset = null)
        val result = repo(HttpStatusCode.OK, """{"content_id":"c1","type":"movie","title":"A"}""", cache)
            .getItemDetail("c1")
        assertTrue(result is ApiResult.Success)
        assertEquals("c1", cache.cachedId)
    }

    @Test
    fun servesCacheOnServer5xx() = runTest {
        val cache = FakeCache(preset = ItemDetail(contentId = "c1", type = "movie", title = "Cached"))
        val result = repo(HttpStatusCode.BadGateway, "{}", cache).getItemDetail("c1")
        assertTrue(result is ApiResult.Success)
        assertEquals("Cached", result.data.title)
    }

    @Test
    fun exposesCachedDetailWithoutNetworkForFastDetailShells() = runTest {
        val cache = FakeCache(preset = ItemDetail(contentId = "c1", type = "movie", title = "Cached"))
        val detail = repoThatFailsOnNetwork(cache).getCachedItemDetail("c1")
        assertEquals("Cached", detail?.title)
    }

    @Test
    fun doesNotServeCacheOn4xx() = runTest {
        val cache = FakeCache(preset = ItemDetail(contentId = "c1", type = "movie", title = "Cached"))
        val result = repo(HttpStatusCode.NotFound, "{}", cache).getItemDetail("c1")
        assertTrue(result is ApiResult.Error)
    }

    @Test
    fun seasonsServesCacheOffline() = runTest {
        val cache = FakeCache(seasonsPreset = SeasonsResponse(seasons = emptyList()))
        val result = repo(HttpStatusCode.ServiceUnavailable, "{}", cache).getSeasons("series-1")
        assertTrue(result is ApiResult.Success)
    }
}
