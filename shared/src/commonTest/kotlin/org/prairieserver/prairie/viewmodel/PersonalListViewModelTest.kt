package org.prairieserver.prairie.viewmodel

import androidx.lifecycle.viewModelScope
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
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class PersonalListViewModelTest {
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
        val requestedOffsets = mutableListOf<Int>()
        val client = HttpClient(MockEngine { req ->
            val path = req.url.encodedPath
            val offset = req.url.parameters["offset"]?.toIntOrNull() ?: 0
            val isMutation = path.contains("/favorites/") && req.method.value != "GET"
            if (!isMutation) requestedOffsets += offset
            val body = when {
                isMutation -> ""
                offset <= 0 ->
                    """{"total":3,"has_more":true,"items":[{"content_id":"m1","type":"movie","title":"A"},{"content_id":"m2","type":"movie","title":"B"}]}"""
                else ->
                    """{"total":3,"has_more":false,"items":[{"content_id":"m3","type":"movie","title":"C"}]}"""
            }
            val status = if (isMutation) HttpStatusCode.NoContent else HttpStatusCode.OK
            respond(body, status, headersOf(HttpHeaders.ContentType, "application/json"))
        }) { install(ContentNegotiation) { json(PrairieJson) } }
        val vm = track(FavoritesViewModel(PersonalDataRepository(PersonalDataApi(client))))
        vm.awaitIdle()
        assertEquals(2, vm.uiState.value.items.size)
        assertTrue(vm.hasLoadedOnce)
        assertTrue(vm.uiState.value.hasMore)
        // Pagination path: loadMore should append when hasMore is true.
        val beforeLoadMore = vm.uiState.value.items.size
        vm.loadMore()
        dispatcher.scheduler.advanceUntilIdle()
        vm.uiState.first { !it.isLoadingMore && it.items.size > beforeLoadMore }
        assertTrue(vm.uiState.value.items.size > beforeLoadMore)
        assertTrue(requestedOffsets.any { it > 0 }, "loadMore should request offset > 0, got $requestedOffsets")
        vm.toggleFavorite("m1")
        vm.uiState.first { it.items.none { item -> item.contentId == "m1" } }
        assertTrue(vm.uiState.value.items.none { it.contentId == "m1" })
        vm.refresh()
        vm.awaitIdle()
        vm.retry()
        vm.awaitIdle()
        assertTrue(vm.hasLoadedOnce)
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
        val watch = track(WatchlistViewModel(PersonalDataRepository(PersonalDataApi(watchClient))))
        watch.awaitIdle()
        assertEquals(1, watch.uiState.value.items.size)
        watch.removeFromWatchlist("m1")
        watch.uiState.first { it.items.isEmpty() }

        val err = track(FavoritesViewModel(repo("{}", HttpStatusCode.BadRequest)))
        err.awaitIdle()
        assertFalse(err.uiState.value.isLoading)
        assertTrue(err.uiState.value.error != null)

        val netClient = HttpClient(MockEngine { throw IllegalStateException("down") }) {
            install(ContentNegotiation) { json(PrairieJson) }
        }
        val net = track(HistoryViewModel(PersonalDataRepository(PersonalDataApi(netClient))))
        net.awaitIdle()
        assertTrue(net.uiState.value.error!!.contains("Network"))
    }
}
