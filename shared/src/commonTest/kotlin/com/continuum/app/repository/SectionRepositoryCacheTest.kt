package com.continuum.app.repository

import com.continuum.app.model.section.ResolvedSection
import com.continuum.app.network.ApiResult
import com.continuum.app.network.ContinuumJson
import com.continuum.app.network.api.SectionApi
import com.continuum.app.repository.port.CatalogCachePort
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

/**
 * Locks the repo-level cache-with-fallback contract on [SectionRepository.getLibrarySections]:
 * cache on success, serve cached on NetworkError/5xx, never on 4xx.
 */
class SectionRepositoryCacheTest {

    private class FakeCache(val preset: List<ResolvedSection>?) : CatalogCachePort {
        var cachedFor: Int? = null
        var cachedSections: List<ResolvedSection>? = null
        override suspend fun cacheLibrarySections(libraryId: Int, sections: List<ResolvedSection>) {
            cachedFor = libraryId; cachedSections = sections
        }
        override suspend fun getCachedLibrarySections(libraryId: Int): List<ResolvedSection>? = preset
    }

    private fun repo(status: HttpStatusCode, body: String, cache: CatalogCachePort): SectionRepository {
        val client = HttpClient(
            MockEngine { respond(body, status, headersOf(HttpHeaders.ContentType, "application/json")) },
        ) {
            install(ContentNegotiation) { json(ContinuumJson) }
        }
        return SectionRepository(SectionApi(client), cache)
    }

    private fun section(id: String) = ResolvedSection(id = id, sectionType = id, title = id)

    @Test
    fun cachesOnSuccess() = runTest {
        val cache = FakeCache(preset = null)
        val result = repo(HttpStatusCode.OK, """{"sections":[{"id":"s","section_type":"s","title":"s"}]}""", cache)
            .getLibrarySections(7)
        assertTrue(result is ApiResult.Success)
        assertEquals(7, cache.cachedFor)
        assertEquals("s", cache.cachedSections?.first()?.id)
    }

    @Test
    fun servesCacheOnServer5xx() = runTest {
        val cache = FakeCache(preset = listOf(section("cached")))
        val result = repo(HttpStatusCode.ServiceUnavailable, "{}", cache).getLibrarySections(7)
        assertTrue(result is ApiResult.Success)
        assertEquals("cached", result.data.sections.first().id)
    }

    @Test
    fun doesNotServeCacheOn4xx() = runTest {
        val cache = FakeCache(preset = listOf(section("cached")))
        val result = repo(HttpStatusCode.NotFound, "{}", cache).getLibrarySections(7)
        assertTrue(result is ApiResult.Error)
    }
}
