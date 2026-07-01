package org.siloserver.silo.network.api

import org.siloserver.silo.network.ApiResult
import org.siloserver.silo.network.SiloJson
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

class PersonalDataApiTest {

    private fun api(
        status: HttpStatusCode = HttpStatusCode.OK,
        responseBody: String = "",
    ): PersonalDataApi {
        val client = HttpClient(
            MockEngine {
                respond(
                    content = responseBody,
                    status = status,
                    headers = headersOf(HttpHeaders.ContentType, "application/json"),
                )
            },
        ) {
            install(ContentNegotiation) { json(SiloJson) }
        }
        return PersonalDataApi(client)
    }

    // --- checkFavorite ---

    @Test
    fun `checkFavorite returns true when server responds 204`() = runTest {
        val api = api(status = HttpStatusCode.NoContent)
        val result = api.checkFavorite("item-1")
        assertIs<ApiResult.Success<Boolean>>(result)
        assertEquals(true, result.data)
    }

    @Test
    fun `checkFavorite returns false when server responds 404`() = runTest {
        val api = api(status = HttpStatusCode.NotFound)
        val result = api.checkFavorite("item-1")
        assertIs<ApiResult.Success<Boolean>>(result)
        assertEquals(false, result.data)
    }

    // --- checkWatchlist ---

    @Test
    fun `checkWatchlist returns true when server responds 204`() = runTest {
        val api = api(status = HttpStatusCode.NoContent)
        val result = api.checkWatchlist("item-2")
        assertIs<ApiResult.Success<Boolean>>(result)
        assertEquals(true, result.data)
    }

    @Test
    fun `checkWatchlist returns false when server responds 404`() = runTest {
        val api = api(status = HttpStatusCode.NotFound)
        val result = api.checkWatchlist("item-2")
        assertIs<ApiResult.Success<Boolean>>(result)
        assertEquals(false, result.data)
    }
}
