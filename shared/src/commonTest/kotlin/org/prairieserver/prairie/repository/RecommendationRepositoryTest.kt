package org.prairieserver.prairie.repository

import org.prairieserver.prairie.network.ApiResult
import org.prairieserver.prairie.network.PrairieJson
import org.prairieserver.prairie.network.api.RecommendationApi
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

class RecommendationRepositoryTest {
    private fun repo(body: String): RecommendationRepository {
        val client = HttpClient(
            MockEngine { respond(body, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json")) },
        ) { install(ContentNegotiation) { json(PrairieJson) } }
        return RecommendationRepository(RecommendationApi(client))
    }

    @Test
    fun coversDiscoverTasteAndSimilar() = runTest {
        assertIs<ApiResult.Success<*>>(repo("""{"rows":[]}""").getDiscover())
        assertIs<ApiResult.Success<*>>(repo("""{"top_genres":["sci-fi"]}""").getTasteProfile())
        assertIs<ApiResult.Success<*>>(repo("""{"items":[]}""").getSimilar("m1", limit = 5))
    }
}
