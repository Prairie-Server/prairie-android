package org.siloserver.silo.viewmodel

import org.siloserver.silo.model.catalog.BrowseItem
import org.siloserver.silo.model.catalog.CatalogResponse
import org.siloserver.silo.network.ApiResult
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/**
 * A page fetched at `offset = N` describes a list that a refresh has since
 * thrown away. Gating the TRIGGERS cannot prevent this on its own — the page is
 * already in flight when the refresh starts, and nothing cancels it — so the
 * check that matters happens when the page lands.
 *
 * These lists refresh on every resume, which is exactly when a viewer comes
 * back from a detail page, so the interleaving is ordinary rather than exotic.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PersonalListViewModelGenerationTest {

    private val dispatcher = UnconfinedTestDispatcher()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun item(id: String) = BrowseItem(contentId = id, type = "movie", title = id)

    private fun page(vararg ids: String, hasMore: Boolean = false) = ApiResult.Success(
        CatalogResponse(items = ids.map(::item), hasMore = hasMore, total = ids.size),
    )

    private class TestList : PersonalListViewModel(pageSize = 2) {
        val pending = ArrayDeque<CompletableDeferred<ApiResult<CatalogResponse>>>()
        val offsets = mutableListOf<Int>()

        override suspend fun fetchPage(offset: Int, limit: Int): ApiResult<CatalogResponse> {
            offsets += offset
            val deferred = CompletableDeferred<ApiResult<CatalogResponse>>()
            pending.addLast(deferred)
            return deferred.await()
        }

        fun start() = loadInitial()
    }

    @Test
    fun refreshDiscardsAPageThatWasAlreadyInFlight() = runTest {
        val vm = TestList()
        vm.start()
        vm.pending.removeFirst().complete(page("a", "b", hasMore = true))
        assertEquals(listOf("a", "b"), vm.uiState.value.items.map { it.contentId })

        // Page two goes out, then a resume refresh replaces the whole list.
        vm.loadMore()
        val pageTwo = vm.pending.removeFirst()
        vm.refresh()
        val refreshed = vm.pending.removeFirst()
        assertEquals(listOf(0, 2, 0), vm.offsets)

        // The refresh lands first and publishes a coherent page one.
        refreshed.complete(page("x", "y", hasMore = true))
        assertEquals(listOf("x", "y"), vm.uiState.value.items.map { it.contentId })

        // The superseded page must not append: items fetched at offset 2 of the
        // OLD list would land after "y" and leave a hole where the middle was.
        pageTwo.complete(page("c", "d"))
        assertEquals(listOf("x", "y"), vm.uiState.value.items.map { it.contentId })
        assertFalse(vm.uiState.value.isLoadingMore)
    }

    @Test
    fun aSupersededPageDoesNotPublishItsError() = runTest {
        val vm = TestList()
        vm.start()
        vm.pending.removeFirst().complete(page("a", "b", hasMore = true))

        vm.loadMore()
        val pageTwo = vm.pending.removeFirst()
        vm.refresh()
        vm.pending.removeFirst().complete(page("x", "y", hasMore = true))

        // A stale request's failure is not this list's failure. Showing it would
        // put an error banner over content that loaded perfectly well.
        pageTwo.complete(ApiResult.Error(code = 500, error = "stale", message = "stale page"))
        assertEquals(null, vm.uiState.value.error)
        assertEquals(listOf("x", "y"), vm.uiState.value.items.map { it.contentId })
        assertFalse(vm.uiState.value.isLoadingMore)
    }

    @Test
    fun anUncontestedPageStillAppends() = runTest {
        val vm = TestList()
        vm.start()
        vm.pending.removeFirst().complete(page("a", "b", hasMore = true))

        // The guard must not swallow ordinary pagination.
        vm.loadMore()
        vm.pending.removeFirst().complete(page("c", "d"))
        assertEquals(listOf("a", "b", "c", "d"), vm.uiState.value.items.map { it.contentId })
        assertFalse(vm.uiState.value.isLoadingMore)
    }
}
