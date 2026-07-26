package org.prairieserver.prairie.domain

import org.prairieserver.prairie.network.ApiResult
import org.prairieserver.prairie.network.PrairieJson
import org.prairieserver.prairie.network.api.PersonalDataApi
import org.prairieserver.prairie.network.api.SectionApi
import org.prairieserver.prairie.repository.PersonalDataRepository
import org.prairieserver.prairie.repository.SectionRepository
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

class GetHomeDataUseCaseTest {
    private fun useCase(home: String, history: String, homeStatus: HttpStatusCode = HttpStatusCode.OK): GetHomeDataUseCase {
        val client = HttpClient(MockEngine { req ->
            val body = if (req.url.encodedPath.contains("history")) history else home
            val status = if (req.url.encodedPath.contains("history")) HttpStatusCode.OK else homeStatus
            respond(body, status, headersOf(HttpHeaders.ContentType, "application/json"))
        }) { install(ContentNegotiation) { json(PrairieJson) } }
        return GetHomeDataUseCase(
            SectionRepository(SectionApi(client)),
            PersonalDataRepository(PersonalDataApi(client)),
        )
    }

    @Test
    fun assemblesContinueWatchingAndRecentlyAdded() = runTest {
        val home = """{"sections":[{"id":"ra","section_type":"recently_added","title":"RA","items":[{"content_id":"m1","type":"movie","title":"New"}]}]}"""
        val history = """{"total":1,"has_more":false,"items":[{"content_id":"m2","type":"movie","title":"Old"}]}"""
        val result = useCase(home, history).getHomeData()
        assertIs<ApiResult.Success<HomeData>>(result)
        assertEquals("m2", result.data.continueWatching.single().contentId)
        assertEquals("m1", result.data.recentlyAdded.single().contentId)
    }

    @Test
    fun propagatesSectionFailures() = runTest {
        assertIs<ApiResult.Error>(useCase("{}", """{"items":[]}""", HttpStatusCode.BadRequest).getHomeData())
        val client = HttpClient(MockEngine { throw IllegalStateException("x") }) {
            install(ContentNegotiation) { json(PrairieJson) }
        }
        val result = GetHomeDataUseCase(
            SectionRepository(SectionApi(client)),
            PersonalDataRepository(PersonalDataApi(client)),
        ).getHomeData()
        assertIs<ApiResult.NetworkError>(result)
    }
}
