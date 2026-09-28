package org.prairieserver.prairie.network.api

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import org.prairieserver.prairie.model.personal.Collection
import org.prairieserver.prairie.model.personal.CollectionGroup
import org.prairieserver.prairie.model.personal.CreateCollectionGroupRequest
import org.prairieserver.prairie.model.personal.CreateCollectionRequest
import org.prairieserver.prairie.model.personal.ReorderCollectionGroupsRequest
import org.prairieserver.prairie.model.personal.ReorderCollectionsRequest
import org.prairieserver.prairie.model.personal.UpdateCollectionGroupRequest
import org.prairieserver.prairie.model.personal.UpdateCollectionRequest
import org.prairieserver.prairie.network.ApiResult
import org.prairieserver.prairie.network.PrairieJson
import org.prairieserver.prairie.network.apiv2.ApiV2Gate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CollectionApiRoutesTest {
    private val jsonHeaders = headersOf(HttpHeaders.ContentType, "application/json")

    private fun api(
        seen: MutableList<String>,
        etag: String? = "\"tag\"",
        body: (String) -> String,
    ): CollectionApi {
        val client = HttpClient(MockEngine { request ->
            val path = request.url.encodedPath
            seen += "${request.method.value} $path"
            val headers = if (etag != null) {
                headersOf(HttpHeaders.ContentType to listOf("application/json"), HttpHeaders.ETag to listOf(etag))
            } else jsonHeaders
            if (request.method == HttpMethod.Delete || (request.method == HttpMethod.Put && !path.endsWith("/capabilities"))) {
                respond("", HttpStatusCode.NoContent)
            } else {
                respond(body(path), HttpStatusCode.OK, headers)
            }
        }) { install(ContentNegotiation) { json(PrairieJson) } }
        return CollectionApi(client, ApiV2Gate.Unrestricted)
    }

    private val collectionBody = """{"id":"c1","name":"Films"}"""
    private val groupBody = """{"id":"g1","name":"Group"}"""
    private val orderBody = """{"ordered_ids":["a","b"]}"""

    @Test
    fun editorReadsCarryEtagAndDecodeEachShape() = runTest {
        val seen = mutableListOf<String>()
        val api = api(seen) { path ->
            when {
                path.endsWith("/capabilities") -> """{"groups":true,"imports":false,"artwork":true,"item_reorder":true}"""
                path.endsWith("/order") -> orderBody
                path.contains("/groups/") -> groupBody
                else -> collectionBody
            }
        }

        val caps = assertIs<ApiResult.Success<CollectionCapabilities>>(api.capabilities()).data
        assertTrue(caps.groups && caps.artwork && caps.itemReorder)
        assertEquals("g1", assertIs<ApiResult.Success<CollectionEditor<CollectionGroup>>>(api.getGroup("g1")).data.value.id)
        val groupsOrder = assertIs<ApiResult.Success<CollectionEditor<CollectionOrder>>>(api.getGroupsOrder()).data
        assertEquals(listOf("a", "b"), groupsOrder.value.orderedIds)
        assertEquals("\"tag\"", groupsOrder.etag)
        assertNull(groupsOrder.scope)
        assertIs<ApiResult.Success<*>>(api.getCollectionsOrder())
        assertIs<ApiResult.Success<*>>(api.getCollectionsOrder("g 1"))
        assertIs<ApiResult.Success<*>>(api.getItemsOrder("c1"))

        assertTrue("GET /api/v2/collections/capabilities" in seen)
        assertTrue("GET /api/v2/collections/groups/g1" in seen)
        assertTrue("GET /api/v2/collections/groups/order" in seen)
        assertTrue("GET /api/v2/collections/order" in seen)
        assertTrue("GET /api/v2/collections/c1/items/order" in seen)
    }

    @Test
    fun weakOrMissingEtagRefusesToProduceAnEditor() = runTest {
        val weak = api(mutableListOf(), etag = "W/\"weak\"") { collectionBody }
        assertEquals("missing_etag", assertIs<ApiResult.Error>(weak.getCollection("c1")).error)
        val missing = api(mutableListOf(), etag = null) { collectionBody }
        assertEquals("missing_etag", assertIs<ApiResult.Error>(missing.getCollection("c1")).error)
    }

    @Test
    fun mutationsUseV2RoutesWithPreconditions() = runTest {
        val seen = mutableListOf<String>()
        val api = api(seen) { path -> if (path.contains("/groups")) groupBody else collectionBody }
        val collectionEditor = CollectionEditor(Collection(id = "c1", name = "Films"), "\"tag\"", null)
        val groupEditor = CollectionEditor(CollectionGroup(id = "g1", name = "Group"), "\"tag\"", null)
        val orderEditor = CollectionEditor(CollectionOrder(orderedIds = listOf("a")), "\"tag\"", null)

        assertEquals("c1", assertIs<ApiResult.Success<Collection>>(api.createCollection(CreateCollectionRequest(name = "Films"))).data.id)
        assertIs<ApiResult.Success<Collection>>(api.updateCollection("c1", UpdateCollectionRequest(name = "New"), collectionEditor))
        assertIs<ApiResult.Success<Collection>>(api.moveCollectionToGroup("c1", "g1", collectionEditor))
        assertIs<ApiResult.Success<Unit>>(api.deleteCollection("c1", collectionEditor))
        assertIs<ApiResult.Success<Unit>>(api.removeItem("c1", "m1"))
        assertEquals("g1", assertIs<ApiResult.Success<CollectionGroup>>(api.createGroup(CreateCollectionGroupRequest(name = "Group"))).data.id)
        assertIs<ApiResult.Success<CollectionGroup>>(api.updateGroup("g1", UpdateCollectionGroupRequest(name = "G"), groupEditor))
        assertIs<ApiResult.Success<Unit>>(api.deleteGroup("g1", groupEditor))
        assertIs<ApiResult.Success<Unit>>(api.reorderGroups(ReorderCollectionGroupsRequest(listOf("g1")), orderEditor))
        assertIs<ApiResult.Success<Unit>>(api.reorderCollections(ReorderCollectionsRequest(listOf("c1")), orderEditor))
        assertIs<ApiResult.Success<Unit>>(api.reorderItems("c1", listOf("m1"), orderEditor))

        assertEquals(
            listOf(
                "POST /api/v2/collections",
                "PATCH /api/v2/collections/c1",
                "PATCH /api/v2/collections/c1",
                "DELETE /api/v2/collections/c1",
                "DELETE /api/v2/collections/c1/items/m1",
                "POST /api/v2/collections/groups",
                "PATCH /api/v2/collections/groups/g1",
                "DELETE /api/v2/collections/groups/g1",
                "PUT /api/v2/collections/groups/order",
                "PUT /api/v2/collections/order",
                "PUT /api/v2/collections/c1/items/order",
            ),
            seen,
        )
    }

    @Test
    fun reorderRefusesPartialOrderWindows() = runTest {
        val seen = mutableListOf<String>()
        val api = api(seen) { orderBody }
        val partial = CollectionEditor(CollectionOrder(orderedIds = listOf("a"), hasMore = true), "\"tag\"", null)
        assertIs<ApiResult.NetworkError>(api.reorderGroups(ReorderCollectionGroupsRequest(listOf("a")), partial))
        assertIs<ApiResult.NetworkError>(api.reorderCollections(ReorderCollectionsRequest(listOf("a")), partial))
        assertIs<ApiResult.NetworkError>(api.reorderItems("c1", listOf("a"), partial))
        assertTrue(seen.isEmpty())
    }

    @Test
    fun collectionItemsRejectsRepeatedCursor() = runTest {
        val api = api(mutableListOf()) {
            """{"items":[],"page":{"has_more":true,"next_cursor":"same"},"total":0}"""
        }
        val continuation = CollectionContinuation(cursor = "same", collectionId = "c1", limit = 40, scope = null)
        val result = api.getCollectionItems("c1", continuation)
        assertEquals("invalid_cursor", assertIs<ApiResult.Error>(result).error)
    }
}
