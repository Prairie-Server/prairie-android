package org.prairieserver.prairie.network.api

import org.prairieserver.prairie.model.section.LibraryCollectionsResponse
import org.prairieserver.prairie.network.ApiResult
import org.prairieserver.prairie.network.PrairieJson
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

class SectionApiTest {
    private fun api(body: String, status: HttpStatusCode = HttpStatusCode.OK): SectionApi {
        val client = HttpClient(
            MockEngine { respond(body, status, headersOf(HttpHeaders.ContentType, "application/json")) },
        ) { install(ContentNegotiation) { json(PrairieJson) } }
        return SectionApi(client)
    }

    @Test
    fun homeAndLibraryEndpoints() = runTest {
        assertIs<ApiResult.Success<*>>(api("""{"sections":[]}""").getHomeLayout())
        assertIs<ApiResult.Success<*>>(api("""{"sections":[]}""").getHomeSections())
        assertIs<ApiResult.Success<*>>(api("""{"items":[]}""").getHomeSectionItems("s1"))
        assertIs<ApiResult.Success<*>>(api("""{"sections":[]}""").getLibrarySections(3))
        assertIs<ApiResult.Success<*>>(api("""{"items":[]}""").getLibrarySectionItems(3, "s1"))
        assertIs<ApiResult.Success<*>>(
            api("""{"total":0,"has_more":false,"items":[]}""").getLibraryCollectionItems("c1", 0, 20),
        )
    }

    @Test
    fun parsesFlatArrayCollections() = runTest {
        val result = api("""[{"id":"c1","title":"Sci-Fi","item_count":3}]""").getLibraryCollections(1)
        assertIs<ApiResult.Success<LibraryCollectionsResponse>>(result)
        assertEquals("c1", result.data.collections.single().id)
        assertEquals("Sci-Fi", result.data.collections.single().name)
        assertEquals(3, result.data.collections.single().itemCount)
    }

    @Test
    fun parsesFlatObjectCollections() = runTest {
        val result = api(
            """{"collections":[{"id":"c1","name":"Drama","poster_url":"p","poster_thumbhash":"h","creator_profile_id":"p1"}]}""",
        ).getLibraryCollections(1)
        assertIs<ApiResult.Success<LibraryCollectionsResponse>>(result)
        assertEquals("Drama", result.data.collections.single().name)
        assertEquals("p1", result.data.collections.single().creatorProfileId)
    }

    @Test
    fun parsesGroupedCollectionsWithUngrouped() = runTest {
        val body = """
        {
          "groups":[{
            "id":"g1","name":"By Genre","kind":"regular","sort_mode":"manual","sort_order":1,
            "collections":[{"id":"c1","title":"Action","item_count":"2"}]
          }],
          "ungrouped":{"sort_order":99,"collections":[{"id":"c2","title":"Misc"}]}
        }
        """.trimIndent()
        val result = api(body).getLibraryCollections(1)
        assertIs<ApiResult.Success<LibraryCollectionsResponse>>(result)
        assertEquals(1, result.data.groups.size)
        assertEquals("Action", result.data.groups.single().collections.single().name)
        assertEquals("Misc", result.data.ungrouped!!.collections.single().name)
        assertEquals(2, result.data.collections.size)
    }

    @Test
    fun emptyGroupsWithFlatCollectionsKeepsFlat() = runTest {
        val result = api(
            """{"groups":[],"collections":[{"id":"c1","title":"Only"}]}""",
        ).getLibraryCollections(1)
        assertIs<ApiResult.Success<LibraryCollectionsResponse>>(result)
        assertEquals("Only", result.data.collections.single().name)
        assertTrue(result.data.groups.isEmpty())
    }

    @Test
    fun collectionsHttpErrorAndNetworkError() = runTest {
        assertIs<ApiResult.Error>(
            api("""{"error":"nope","message":"denied"}""", HttpStatusCode.Forbidden).getLibraryCollections(1),
        )
        val client = HttpClient(MockEngine { throw IllegalStateException("down") }) {
            install(ContentNegotiation) { json(PrairieJson) }
        }
        assertIs<ApiResult.NetworkError>(SectionApi(client).getLibraryCollections(1))
    }
}
