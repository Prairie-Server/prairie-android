package org.prairieserver.prairie.repository

import org.prairieserver.prairie.model.personal.UpdateCollectionRequest
import org.prairieserver.prairie.network.ApiResult
import org.prairieserver.prairie.network.PrairieJson
import org.prairieserver.prairie.network.api.CollectionApi
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
import kotlin.test.assertIs

class CollectionRepositoryTest {
    private fun repo(body: String = """{"id":"c1","name":"Favs"}""", status: HttpStatusCode = HttpStatusCode.OK): CollectionRepository {
        val client = HttpClient(
            MockEngine { respond(body, status, headersOf(HttpHeaders.ContentType, "application/json")) },
        ) { install(ContentNegotiation) { json(PrairieJson) } }
        return CollectionRepository(CollectionApi(client))
    }

    @Test
    fun coversAllPassThroughMethods() = runTest {
        val listRepo = CollectionRepository(
            CollectionApi(
                HttpClient(
                    MockEngine {
                        respond(
                            """{"collections":[{"id":"c1","name":"Favs"}],"groups":[{"id":"g1","name":"G"}]}""",
                            HttpStatusCode.OK,
                            headersOf(HttpHeaders.ContentType, "application/json"),
                        )
                    },
                ) { install(ContentNegotiation) { json(PrairieJson) } },
            ),
        )
        assertIs<ApiResult.Success<*>>(listRepo.listCollections())
        assertIs<ApiResult.Success<*>>(repo().createCollection("Favs", "manual"))
        assertIs<ApiResult.Success<*>>(repo().updateCollection("c1", UpdateCollectionRequest(name = "N")))
        assertIs<ApiResult.Success<*>>(repo(status = HttpStatusCode.NoContent, body = "").deleteCollection("c1"))
        assertIs<ApiResult.Success<*>>(
            repo("""{"total":0,"has_more":false,"items":[]}""").getItems("c1", 0, 10),
        )
        assertIs<ApiResult.Success<*>>(repo(status = HttpStatusCode.NoContent, body = "").addItem("c1", "m1"))
        assertIs<ApiResult.Success<*>>(repo(status = HttpStatusCode.NoContent, body = "").removeItem("c1", "m1"))
        assertIs<ApiResult.Success<*>>(repo().moveCollectionToGroup("c1", null))
        assertIs<ApiResult.Success<*>>(
            repo("""{"id":"g1","name":"G"}""").createGroup("G"),
        )
        assertIs<ApiResult.Success<*>>(
            repo("""{"id":"g1","name":"G2"}""").renameGroup("g1", "G2"),
        )
        assertIs<ApiResult.Success<*>>(repo(status = HttpStatusCode.NoContent, body = "").deleteGroup("g1"))
        assertIs<ApiResult.Success<*>>(repo(status = HttpStatusCode.NoContent, body = "").reorderGroups(listOf("g1")))
        assertIs<ApiResult.Success<*>>(
            repo(status = HttpStatusCode.NoContent, body = "").reorderCollections(listOf("c1"), "g1"),
        )
    }
}
