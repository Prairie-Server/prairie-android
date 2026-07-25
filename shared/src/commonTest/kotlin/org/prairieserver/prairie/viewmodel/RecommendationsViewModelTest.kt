package org.prairieserver.prairie.viewmodel

import org.prairieserver.prairie.network.PrairieJson
import org.prairieserver.prairie.network.api.RecommendationApi
import org.prairieserver.prairie.repository.RecommendationRepository
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class RecommendationsViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()
    @BeforeTest fun setUp() { Dispatchers.setMain(dispatcher) }
    @AfterTest fun tearDown() { Dispatchers.resetMain() }

    private fun vm(discover: String, taste: String = """{"top_genres":["scifi"]}""", status: HttpStatusCode = HttpStatusCode.OK): RecommendationsViewModel {
        val client = HttpClient(MockEngine { req ->
            val body = if (req.url.encodedPath.contains("taste")) taste else discover
            respond(body, status, headersOf(HttpHeaders.ContentType, "application/json"))
        }) { install(ContentNegotiation) { json(PrairieJson) } }
        return RecommendationsViewModel(RecommendationRepository(RecommendationApi(client)))
    }

    private suspend fun RecommendationsViewModel.awaitIdle() =
        uiState.first { !it.isLoading && !it.isRefreshing }

    @Test
    fun loadsDiscoverRowsPreferringForYou() = runTest(dispatcher) {
        val discover = """{"rows":[
          {"type":"because","label":"Because","items":[{"content_id":"m1","type":"movie","title":"A"}]},
          {"type":"for_you","label":"For You","items":[{"content_id":"m2","type":"movie","title":"B"}]}
        ]}"""
        val state = vm(discover).awaitIdle()
        assertFalse(state.isLoading)
        assertNull(state.error)
        assertEquals("For You", state.sections.first().title)
        assertEquals(listOf("scifi"), state.tasteProfile?.topGenres)
    }

    @Test
    fun surfacesHttpAndNetworkErrors() = runTest(dispatcher) {
        assertTrue(vm("{}", status = HttpStatusCode.BadRequest).awaitIdle().error != null)
        val client = HttpClient(MockEngine { throw IllegalStateException("x") }) {
            install(ContentNegotiation) { json(PrairieJson) }
        }
        val state = RecommendationsViewModel(RecommendationRepository(RecommendationApi(client))).awaitIdle()
        assertTrue(state.error!!.contains("Network"))
    }

    @Test
    fun refreshReloads() = runTest(dispatcher) {
        val model = vm("""{"rows":[{"type":"x","label":"X","items":[{"content_id":"m1","type":"movie","title":"A"}]}]}""")
        model.awaitIdle()
        model.refresh()
        model.awaitIdle()
        assertFalse(model.uiState.value.isRefreshing)
        model.loadRecommendations()
        model.awaitIdle()
    }
}
