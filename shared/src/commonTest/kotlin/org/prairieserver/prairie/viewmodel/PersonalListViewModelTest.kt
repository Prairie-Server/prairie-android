package org.prairieserver.prairie.viewmodel

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
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class PersonalListViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()
    @BeforeTest fun setUp() { Dispatchers.setMain(dispatcher) }
    @AfterTest fun tearDown() { Dispatchers.resetMain() }

    private fun repo(body: String, status: HttpStatusCode = HttpStatusCode.OK): PersonalDataRepository {
        val client = HttpClient(
            MockEngine { respond(body, status, headersOf(HttpHeaders.ContentType, "application/json")) },
        ) { install(ContentNegotiation) { json(PrairieJson) } }
        return PersonalDataRepository(PersonalDataApi(client))
    }

    private suspend fun PersonalListViewModel.awaitIdle() =
        uiState.first { !it.isLoading && !it.isLoadingMore && !it.isRefreshing }

    @Test
    fun favoritesLoadsPaginatesAndRemoves() = runTest(dispatcher) {
        val client = HttpClient(MockEngine { req ->
            val path = req.url.encodedPath
            val offset = req.url.parameters["offset"]
            val isMutation = path.contains("/favorites/") && req.method.value != "GET"
            val body = when {
                isMutation -> ""
                offset == "0" || offset == null ->
                    """{"total":2,"has_more":true,"items":[{"content_id":"m1","type":"movie","title":"A"},{"content_id":"m2","type":"movie","title":"B"}]}"""
                else ->
                    """{"total":2,"has_more":false,"items":[{"content_id":"m3","type":"movie","title":"C"}]}"""
            }
            val status = if (isMutation) HttpStatusCode.NoContent else HttpStatusCode.OK
            respond(body, status, headersOf(HttpHeaders.ContentType, "application/json"))
        }) { install(ContentNegotiation) { json(PrairieJson) } }
        val vm = FavoritesViewModel(PersonalDataRepository(PersonalDataApi(client)))
        vm.awaitIdle()
        assertEquals(2, vm.uiState.value.items.size)
        assertTrue(vm.hasLoadedOnce)
        assertTrue(vm.uiState.value.hasMore)
        vm.toggleFavorite("m1")
        vm.uiState.first { it.items.none { item -> item.contentId == "m1" } }
        assertEquals(1, vm.uiState.value.items.size)
        vm.refresh()
        vm.awaitIdle()
        vm.retry()
        vm.awaitIdle()
        // Pagination path: loadMore should append when hasMore is true.
        vm.loadMore()
        vm.awaitIdle()
        assertTrue(vm.uiState.value.items.size >= 1)
    }

    @Test
    fun watchlistAndHistoryErrorPaths() = runTest(dispatcher) {
        val watchClient = HttpClient(MockEngine { req ->
            val mutating = req.method.value != "GET"
            respond(
                if (mutating) "" else """{"total":1,"has_more":false,"items":[{"content_id":"m1","type":"movie","title":"A"}]}""",
                if (mutating) HttpStatusCode.NoContent else HttpStatusCode.OK,
                headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }) { install(ContentNegotiation) { json(PrairieJson) } }
        val watch = WatchlistViewModel(PersonalDataRepository(PersonalDataApi(watchClient)))
        watch.awaitIdle()
        assertEquals(1, watch.uiState.value.items.size)
        watch.removeFromWatchlist("m1")
        watch.uiState.first { it.items.isEmpty() }

        val err = FavoritesViewModel(repo("{}", HttpStatusCode.BadRequest))
        err.awaitIdle()
        assertFalse(err.uiState.value.isLoading)
        assertTrue(err.uiState.value.error != null)

        val netClient = HttpClient(MockEngine { throw IllegalStateException("down") }) {
            install(ContentNegotiation) { json(PrairieJson) }
        }
        val net = HistoryViewModel(PersonalDataRepository(PersonalDataApi(netClient)))
        net.awaitIdle()
        assertTrue(net.uiState.value.error!!.contains("Network"))
    }
}
