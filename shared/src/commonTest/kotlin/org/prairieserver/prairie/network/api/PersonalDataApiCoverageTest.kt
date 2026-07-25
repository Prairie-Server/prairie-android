package org.prairieserver.prairie.network.api

import org.prairieserver.prairie.model.personal.SyncProgressItem
import org.prairieserver.prairie.model.personal.SyncProgressRequest
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

class PersonalDataApiCoverageTest {
    private fun api(body: String = "{}", status: HttpStatusCode = HttpStatusCode.OK): PersonalDataApi {
        val client = HttpClient(
            MockEngine { respond(body, status, headersOf(HttpHeaders.ContentType, "application/json")) },
        ) { install(ContentNegotiation) { json(PrairieJson) } }
        return PersonalDataApi(client)
    }

    @Test
    fun coversRemainingPersonalDataRoutes() = runTest {
        assertIs<ApiResult.Success<*>>(api("[]").listUserLibraries())
        assertIs<ApiResult.Success<*>>(api("""{"total":0,"has_more":false,"items":[]}""").listFavorites())
        assertIs<ApiResult.Success<*>>(api(status = HttpStatusCode.NoContent, body = "").addFavorite("m1"))
        assertIs<ApiResult.Success<*>>(api(status = HttpStatusCode.NoContent, body = "").removeFavorite("m1"))
        assertIs<ApiResult.Success<*>>(api("""{"total":0,"has_more":false,"items":[]}""").listWatchlist())
        assertIs<ApiResult.Success<*>>(api(status = HttpStatusCode.NoContent, body = "").addToWatchlist("m1"))
        assertIs<ApiResult.Success<*>>(api(status = HttpStatusCode.NoContent, body = "").removeFromWatchlist("m1"))
        assertIs<ApiResult.Success<*>>(api("""{"total":0,"has_more":false,"items":[]}""").listHistory())
        assertIs<ApiResult.Success<*>>(api("""{"progress":[]}""").listProgress())
        assertIs<ApiResult.Success<*>>(
            api(status = HttpStatusCode.NoContent, body = "").syncProgress(
                SyncProgressRequest(items = listOf(SyncProgressItem("m1", 1.0, 10.0))),
            ),
        )
        assertIs<ApiResult.Success<*>>(api("""{"ratings":[]}""").listRatings())
        assertIs<ApiResult.Success<*>>(api("""{"media_item_id":"m1","rating":5}""").getRating("m1"))
        assertIs<ApiResult.Success<*>>(api(status = HttpStatusCode.NoContent, body = "").setRating("m1", 4))
        assertIs<ApiResult.Success<*>>(api(status = HttpStatusCode.NoContent, body = "").deleteRating("m1"))
        assertIs<ApiResult.Success<*>>(api(status = HttpStatusCode.NoContent, body = "").markWatched("m1"))
        assertIs<ApiResult.Success<*>>(api(status = HttpStatusCode.NoContent, body = "").markUnwatched("m1"))
        assertIs<ApiResult.Success<*>>(
            api(status = HttpStatusCode.NoContent, body = "").dismissContinueWatching("m1", "t"),
        )
        assertIs<ApiResult.Success<*>>(api(status = HttpStatusCode.NoContent, body = "").undismissContinueWatching("m1"))
        assertIs<ApiResult.Success<*>>(api(status = HttpStatusCode.NoContent, body = "").dismissNextUp("m1", "s1"))
    }
}
