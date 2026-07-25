package org.prairieserver.prairie.domain

import org.prairieserver.prairie.network.ApiResult
import org.prairieserver.prairie.network.PrairieJson
import org.prairieserver.prairie.network.api.PersonalDataApi
import org.prairieserver.prairie.repository.PersonalDataRepository
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

class MediaActionsCoordinatorTest {
    @Test
    fun delegatesToPersonalDataRepository() = runTest {
        val client = HttpClient(
            MockEngine { respond("", HttpStatusCode.NoContent, headersOf(HttpHeaders.ContentType, "application/json")) },
        ) { install(ContentNegotiation) { json(PrairieJson) } }
        val c = MediaActionsCoordinator(PersonalDataRepository(PersonalDataApi(client)))
        assertIs<ApiResult.Success<*>>(c.setWatched("m1", true))
        assertIs<ApiResult.Success<*>>(c.setWatched("m1", false))
        assertIs<ApiResult.Success<*>>(c.toggleFavorite("m1", true))
        assertIs<ApiResult.Success<*>>(c.toggleFavorite("m1", false))
        assertIs<ApiResult.Success<*>>(c.toggleWatchlist("m1", true))
        assertIs<ApiResult.Success<*>>(c.toggleWatchlist("m1", false))
        assertIs<ApiResult.Success<*>>(c.dismissContinueWatching("m1", "t"))
        assertIs<ApiResult.Success<*>>(c.dismissNextUp("m1", "s1"))
    }
}
