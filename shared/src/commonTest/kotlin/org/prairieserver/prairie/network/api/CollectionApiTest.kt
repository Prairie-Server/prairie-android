package org.prairieserver.prairie.network.api

import org.prairieserver.prairie.model.personal.CreateCollectionGroupRequest
import org.prairieserver.prairie.model.personal.CreateCollectionRequest
import org.prairieserver.prairie.model.personal.ReorderCollectionGroupsRequest
import org.prairieserver.prairie.model.personal.ReorderCollectionsRequest
import org.prairieserver.prairie.model.personal.UpdateCollectionGroupRequest
import org.prairieserver.prairie.model.personal.UpdateCollectionRequest
import org.prairieserver.prairie.network.ApiResult
import org.prairieserver.prairie.network.PrairieJson
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.assertTrue

class CollectionApiTest {
    private class Cap { var path = ""; var body = ""; var query = mapOf<String, String?>() }
    private fun api(
        body: String = """{"id":"c1","name":"X"}""",
        status: HttpStatusCode = HttpStatusCode.OK,
        cap: Cap = Cap(),
    ): Pair<CollectionApi, Cap> {
        val client = HttpClient(
            MockEngine { req ->
                cap.path = req.url.encodedPath
                cap.query = req.url.parameters.names().associateWith { req.url.parameters[it] }
                cap.body = runCatching { req.body.toByteArray().decodeToString() }.getOrDefault("")
                respond(body, status, headersOf(HttpHeaders.ContentType, "application/json"))
            },
        ) { install(ContentNegotiation) { json(PrairieJson) } }
        return CollectionApi(client) to cap
    }

    @Test
    fun coversCollectionAndGroupRoutes() = runTest {
        assertIs<ApiResult.Success<*>>(api(body = """{"collections":[],"groups":[]}""").first.listCollections())
        assertIs<ApiResult.Success<*>>(
            api().first.createCollection(CreateCollectionRequest(name = "A", collectionType = "manual")),
        )
        assertIs<ApiResult.Success<*>>(api().first.updateCollection("c1", UpdateCollectionRequest(name = "B")))
        assertIs<ApiResult.Success<*>>(api(status = HttpStatusCode.NoContent, body = "").first.deleteCollection("c1"))

        val (_, moveCap) = api()
        assertIs<ApiResult.Success<*>>(api(cap = moveCap).first.moveCollectionToGroup("c1", null))
        assertTrue("null" in moveCap.body)

        val (_, itemsCap) = api(body = """{"total":0,"has_more":false,"items":[]}""")
        assertIs<ApiResult.Success<*>>(api(body = """{"total":0,"has_more":false,"items":[]}""", cap = itemsCap).first.getCollectionItems("c1", 5, 10))
        assertTrue(itemsCap.path.contains("catalog"))
        assertTrue(itemsCap.query["source"] == "user_collection")

        assertIs<ApiResult.Success<*>>(api(status = HttpStatusCode.NoContent, body = "").first.addItem("c1", "m1"))
        assertIs<ApiResult.Success<*>>(api(status = HttpStatusCode.NoContent, body = "").first.removeItem("c1", "m1"))

        assertIs<ApiResult.Success<*>>(
            api(body = """{"id":"g1","name":"G"}""").first.createGroup(CreateCollectionGroupRequest(name = "G")),
        )
        assertIs<ApiResult.Success<*>>(
            api(body = """{"id":"g1","name":"G2"}""").first.updateGroup("g1", UpdateCollectionGroupRequest(name = "G2")),
        )
        assertIs<ApiResult.Success<*>>(api(status = HttpStatusCode.NoContent, body = "").first.deleteGroup("g1"))
        assertIs<ApiResult.Success<*>>(
            api(status = HttpStatusCode.NoContent, body = "").first.reorderGroups(
                ReorderCollectionGroupsRequest(listOf("g1")),
            ),
        )
        assertIs<ApiResult.Success<*>>(
            api(status = HttpStatusCode.NoContent, body = "").first.reorderCollections(
                ReorderCollectionsRequest(orderedIds = listOf("c1"), groupId = "g1"),
            ),
        )
    }
}
