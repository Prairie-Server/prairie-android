package org.prairieserver.prairie.viewmodel

import androidx.lifecycle.viewModelScope
import org.prairieserver.prairie.domain.MediaActionsCoordinator
import org.prairieserver.prairie.model.section.ResolvedSection
import org.prairieserver.prairie.model.section.SectionItem
import org.prairieserver.prairie.network.PrairieJson
import org.prairieserver.prairie.network.api.PersonalDataApi
import org.prairieserver.prairie.network.api.SectionApi
import org.prairieserver.prairie.repository.PersonalDataRepository
import org.prairieserver.prairie.repository.SectionRepository
import org.prairieserver.prairie.repository.port.HomeCachePort
import org.prairieserver.prairie.repository.port.HomeCacheSnapshot
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
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
class HomeViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()

    @BeforeTest fun setUp() { Dispatchers.setMain(dispatcher) }
    @AfterTest
    fun tearDown() {
        createdViewModels.forEach { it.viewModelScope.cancel() }
        createdViewModels.clear()
        Dispatchers.resetMain()
    }

    // Cancel viewModelScope coroutines BEFORE resetting Main: a coroutine
    // still parked on Dispatchers.Main when a later test calls setMain/resetMain
    // throws IllegalStateException from TestMainDispatcher.
    private val createdViewModels = mutableListOf<androidx.lifecycle.ViewModel>()

    private fun <T : androidx.lifecycle.ViewModel> track(viewModel: T): T {
        createdViewModels += viewModel
        return viewModel
    }


    private fun section(id: String) = ResolvedSection(
        id = id,
        sectionType = id,
        title = id,
        totalCount = 1,
        items = listOf(SectionItem(contentId = id, type = "movie", title = id)),
    )

    private fun vm(
        homeBody: String,
        homeStatus: HttpStatusCode = HttpStatusCode.OK,
        itemsBody: String = """{"items":[]}""",
        cache: HomeCachePort = object : HomeCachePort {},
        actionsSuccess: Boolean = true,
    ): HomeViewModel {
        val client = HttpClient(
            MockEngine { req ->
                val path = req.url.encodedPath
                val body = if (path.contains("/items")) itemsBody else homeBody
                val status = if (path.contains("/items")) HttpStatusCode.OK else homeStatus
                respond(body, status, headersOf(HttpHeaders.ContentType, "application/json"))
            },
        ) { install(ContentNegotiation) { json(PrairieJson) } }
        val personalClient = HttpClient(
            MockEngine {
                respond(
                    "",
                    if (actionsSuccess) HttpStatusCode.NoContent else HttpStatusCode.BadRequest,
                    headersOf(HttpHeaders.ContentType, "application/json"),
                )
            },
        ) { install(ContentNegotiation) { json(PrairieJson) } }
        return track(HomeViewModel(
            sectionRepository = SectionRepository(SectionApi(client)),
            mediaActions = MediaActionsCoordinator(PersonalDataRepository(PersonalDataApi(personalClient))),
            homeCache = cache,
        ))
    }

    private suspend fun HomeViewModel.awaitIdle(): HomeUiState =
        uiState.first { !it.isLoading && !it.isRefreshing }

    @Test
    fun loadsInlineSectionsOnInit() = runTest(dispatcher) {
        val body = """{"sections":[{"id":"cw","section_type":"continue_watching","title":"CW","total_count":1,"items":[{"content_id":"m1","type":"movie","title":"M"}]}]}"""
        val state = vm(body).awaitIdle()
        assertFalse(state.isLoading)
        assertNull(state.error)
        assertEquals(1, state.sections.size)
        assertEquals("m1", state.sections.single().items.single().contentId)
    }

    @Test
    fun servesCacheThenRefreshes() = runTest(dispatcher) {
        val cache = object : HomeCachePort {
            override suspend fun getCachedHome() = HomeCacheSnapshot(listOf(section("cached")), 1L)
            override suspend fun cacheHome(sections: List<ResolvedSection>) = Unit
        }
        val body = """{"sections":[{"id":"live","section_type":"recently_added","title":"RA","total_count":1,"items":[{"content_id":"m2","type":"movie","title":"Live"}]}]}"""
        val home = vm(body, cache = cache)
        val state = home.uiState.first { it.sections.any { section -> section.id == "live" } }
        assertEquals("live", state.sections.single().id)
    }

    @Test
    fun networkErrorSurfacesWhenEmpty() = runTest(dispatcher) {
        val client = HttpClient(MockEngine { throw IllegalStateException("down") }) {
            install(ContentNegotiation) { json(PrairieJson) }
        }
        val personal = HttpClient(MockEngine { respond("", HttpStatusCode.NoContent) }) {
            install(ContentNegotiation) { json(PrairieJson) }
        }
        val state = track(HomeViewModel(
            SectionRepository(SectionApi(client)),
            MediaActionsCoordinator(PersonalDataRepository(PersonalDataApi(personal))),
        )).awaitIdle()
        assertTrue(state.error!!.contains("Network"))
    }

    @Test
    fun optimisticActionsAndDismiss() = runTest(dispatcher) {
        val body = """{"sections":[
          {"id":"cw","section_type":"continue_watching","title":"CW","total_count":1,"items":[{"content_id":"m1","type":"movie","title":"M"}]},
          {"id":"ra","section_type":"recently_added","title":"RA","total_count":1,"items":[{"content_id":"m1","type":"movie","title":"M"}]}
        ]}"""
        val home = vm(body)
        home.awaitIdle()
        home.setWatched("m1", true)
        home.toggleFavorite("m1", true)
        home.toggleWatchlist("m1", true)
        home.dismissContinueWatching("m1", "2026-01-01T00:00:00Z")
        home.dismissNextUp("m1", "series-1")
        assertTrue(home.uiState.value.sections.none { it.sectionType == "continue_watching" })
        home.refresh()
        home.awaitIdle()
        home.refreshFromRealtime()
    }

    @Test
    fun resolvesEmptyInlineSectionsViaItemsEndpoint() = runTest(dispatcher) {
        val homeBody = """{"sections":[{"id":"s1","section_type":"custom","title":"S","total_count":1,"items":[]}]}"""
        val itemsBody = """{"section":{"id":"s1","section_type":"custom","title":"S","total_count":1,"items":[{"content_id":"m9","type":"movie","title":"X"}]}}"""
        val state = vm(homeBody, itemsBody = itemsBody).awaitIdle()
        assertEquals("m9", state.sections.single().items.single().contentId)
    }

    @Test
    fun httpErrorWhenEmptyShowsMessage() = runTest(dispatcher) {
        val state = vm("{}", homeStatus = HttpStatusCode.BadRequest).awaitIdle()
        assertFalse(state.isLoading)
        assertTrue(state.error != null)
    }
}
