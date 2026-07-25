package org.prairieserver.prairie.network.api

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

class RecommendationApiTest {
    @Test
    fun coversRecommendationRoutes() = runTest {
        var path = ""
        var limit: String? = null
        val client = HttpClient(
            MockEngine { req ->
                path = req.url.encodedPath
                limit = req.url.parameters["limit"]
                respond("""{"rows":[]}""", HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
            },
        ) { install(ContentNegotiation) { json(PrairieJson) } }
        val api = RecommendationApi(client)
        assertIs<ApiResult.Success<*>>(api.getDiscover())
        assertEquals("/api/v1/recommendations/discover", path)

        val client2 = HttpClient(
            MockEngine { respond("""{"top_genres":[]}""", HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json")) },
        ) { install(ContentNegotiation) { json(PrairieJson) } }
        assertIs<ApiResult.Success<*>>(RecommendationApi(client2).getTasteProfile())

        val client3 = HttpClient(
            MockEngine { req ->
                path = req.url.encodedPath
                limit = req.url.parameters["limit"]
                respond("""{"items":[]}""", HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
            },
        ) { install(ContentNegotiation) { json(PrairieJson) } }
        assertIs<ApiResult.Success<*>>(RecommendationApi(client3).getSimilar("m1", 7))
        assertEquals("/api/v1/recommendations/similar/m1", path)
        assertEquals("7", limit)
    }
}
