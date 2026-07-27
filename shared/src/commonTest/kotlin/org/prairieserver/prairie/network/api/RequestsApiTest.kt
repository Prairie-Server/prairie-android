package org.prairieserver.prairie.network.api

import org.prairieserver.prairie.model.request.CreateMediaRequest
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
import kotlin.test.assertIs

class RequestsApiTest {
    private fun api(
        body: String = "{}",
        status: HttpStatusCode = HttpStatusCode.OK,
    ): RequestsApi {
        val client = HttpClient(
            MockEngine {
                respond(body, status, headersOf(HttpHeaders.ContentType, "application/json"))
            },
        ) { install(ContentNegotiation) { json(PrairieJson) } }
        return DefaultRequestsApi(client)
    }

    private val mediaRequestJson = """
        {
          "id":"r1",
          "media_type":"movie",
          "tmdb_id":1,
          "title":"Dune",
          "status":"pending",
          "outcome":"active",
          "created_at":"t0",
          "updated_at":"t1"
        }
    """.trimIndent()

    @Test
    fun coversRequestRoutes() = runTest {
        assertIs<ApiResult.Success<*>>(api(body = """{"requests_enabled":true}""").status())
        assertIs<ApiResult.Success<*>>(api(body = """{"sections":[]}""").discover())
        assertIs<ApiResult.Success<*>>(
            api(body = """{"page":1,"results":[],"total_pages":0,"total_results":0}""")
                .discoverSection("trending", page = 2),
        )
        assertIs<ApiResult.Success<*>>(
            api(body = """{"page":1,"results":[],"total_pages":0,"total_results":0}""")
                .search("dune", mediaType = "movie", page = 1),
        )
        assertIs<ApiResult.Success<*>>(
            api(body = """{"media_type":"movie","tmdb_id":1,"title":"Dune"}""")
                .detail("movie", 1),
        )
        assertIs<ApiResult.Success<*>>(
            api(body = mediaRequestJson)
                .create(CreateMediaRequest(mediaType = "movie", tmdbId = 1, title = "Dune")),
        )
        assertIs<ApiResult.Success<*>>(
            api(body = """{"requests":[]}""")
                .mine(status = "pending", outcome = null, limit = 10, offset = 0),
        )
        assertIs<ApiResult.Success<*>>(api(body = mediaRequestJson).get("r1"))
        assertIs<ApiResult.Success<*>>(
            api(body = mediaRequestJson.replace("pending", "cancelled").replace("active", "cancelled"))
                .cancel("r1"),
        )
    }
}
