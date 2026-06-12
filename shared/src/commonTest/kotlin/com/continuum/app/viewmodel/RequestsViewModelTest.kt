package com.continuum.app.viewmodel

import com.continuum.app.model.request.CreateMediaRequest
import com.continuum.app.model.request.MediaRequest
import com.continuum.app.model.request.RequestAvailability
import com.continuum.app.model.request.RequestDiscoverySection
import com.continuum.app.model.request.RequestMediaDetail
import com.continuum.app.model.request.RequestMediaPage
import com.continuum.app.model.request.RequestMediaResult
import com.continuum.app.model.request.RequestMediaType
import com.continuum.app.model.request.RequestOutcome
import com.continuum.app.model.request.RequestState
import com.continuum.app.model.request.RequestStatus
import com.continuum.app.model.request.RequestsDiscoverResponse
import com.continuum.app.model.request.RequestsFeatureStatus
import com.continuum.app.model.request.RequestsListResponse
import com.continuum.app.network.ApiResult
import com.continuum.app.network.api.RequestsApi
import com.continuum.app.repository.RequestsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
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
class RequestsViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `requests view model loads status then discover sections`() = runTest(dispatcher) {
        val section = RequestDiscoverySection(
            key = "trending",
            title = "Trending",
            results = listOf(stubResult(tmdbId = 11, title = "The Signal")),
        )
        val api = FakeRequestsApi(
            statusResult = ApiResult.Success(RequestsFeatureStatus(requestsEnabled = true)),
            discoverResult = ApiResult.Success(RequestsDiscoverResponse(listOf(section))),
        )

        val viewModel = RequestsViewModel(RequestsRepository(api))
        val state = viewModel.uiState.value

        assertFalse(state.isLoading)
        assertTrue(state.isEnabled)
        assertEquals(listOf(section), state.sections)
        assertNull(state.error)
        assertEquals(listOf("status", "discover"), api.calls)
    }

    @Test
    fun `requests view model skips discover when requests are disabled`() = runTest(dispatcher) {
        val api = FakeRequestsApi(
            statusResult = ApiResult.Success(RequestsFeatureStatus(requestsEnabled = false)),
        )

        val viewModel = RequestsViewModel(RequestsRepository(api))
        val state = viewModel.uiState.value

        assertFalse(state.isLoading)
        assertFalse(state.isEnabled)
        assertTrue(state.sections.isEmpty())
        assertEquals("Requests are not enabled on this server.", state.error)
        assertEquals(listOf("status"), api.calls)
    }

    @Test
    fun `search view model updates filters and sends search request`() = runTest(dispatcher) {
        val page = RequestMediaPage(
            page = 1,
            totalPages = 3,
            totalResults = 1,
            results = listOf(stubResult(tmdbId = 22, title = "Moonbase")),
        )
        val api = FakeRequestsApi(searchResult = ApiResult.Success(page))
        val viewModel = RequestSearchViewModel(RequestsRepository(api))

        viewModel.onQueryChanged("moon")
        viewModel.onMediaTypeChanged(RequestMediaType.Series)
        viewModel.search()
        val state = viewModel.uiState.value

        assertEquals("moon", state.query)
        assertEquals(RequestMediaType.Series, state.mediaType)
        assertFalse(state.isLoading)
        assertEquals(page.results, state.results)
        assertEquals(3, state.totalPages)
        assertNull(state.error)
        assertEquals(listOf(SearchCall("moon", RequestMediaType.Series, 1)), api.searchCalls)
    }

    @Test
    fun `search view model treats all media type as no media type filter`() = runTest(dispatcher) {
        val api = FakeRequestsApi(searchResult = ApiResult.Success(RequestMediaPage()))
        val viewModel = RequestSearchViewModel(RequestsRepository(api))

        viewModel.onQueryChanged("alien")
        viewModel.onMediaTypeChanged(RequestMediaType.All)
        viewModel.search()

        assertEquals(listOf(SearchCall("alien", null, 1)), api.searchCalls)
    }

    @Test
    fun `search view model separates typed query from submitted query`() = runTest(dispatcher) {
        val api = FakeRequestsApi(searchResult = ApiResult.Success(RequestMediaPage()))
        val viewModel = RequestSearchViewModel(RequestsRepository(api))

        viewModel.onQueryChanged("blade")

        assertEquals("blade", viewModel.uiState.value.query)
        assertEquals("", viewModel.uiState.value.submittedQuery)
        assertFalse(viewModel.uiState.value.hasSubmittedQuery)

        viewModel.search()

        assertEquals("blade", viewModel.uiState.value.submittedQuery)
        assertTrue(viewModel.uiState.value.hasSubmittedQuery)
    }

    @Test
    fun `search view model marks query submitted while search is loading`() = runTest(dispatcher) {
        val api = FakeRequestsApi(
            searchHandler = { _, _, _ ->
                delay(1_000)
                ApiResult.Success(RequestMediaPage())
            },
        )
        val viewModel = RequestSearchViewModel(RequestsRepository(api))

        viewModel.onQueryChanged("blade")
        viewModel.search()

        val loadingState = viewModel.uiState.value
        assertTrue(loadingState.isLoading)
        assertEquals("blade", loadingState.submittedQuery)
        assertTrue(loadingState.hasSubmittedQuery)
        assertTrue(loadingState.results.isEmpty())
    }

    @Test
    fun `search view model keeps latest search results when prior search is slower`() = runTest(dispatcher) {
        val olderPage = RequestMediaPage(results = listOf(stubResult(tmdbId = 22, title = "Moonbase")))
        val latestPage = RequestMediaPage(results = listOf(stubResult(tmdbId = 23, title = "Marsbase")))
        val api = FakeRequestsApi(
            searchHandler = { query, _, _ ->
                if (query == "moon") {
                    delay(1_000)
                    ApiResult.Success(olderPage)
                } else {
                    ApiResult.Success(latestPage)
                }
            },
        )
        val viewModel = RequestSearchViewModel(RequestsRepository(api))

        viewModel.onQueryChanged("moon")
        viewModel.search()
        viewModel.onQueryChanged("mars")
        viewModel.search()
        dispatcher.scheduler.advanceUntilIdle()
        val state = viewModel.uiState.value

        assertFalse(state.isLoading)
        assertEquals(latestPage.results, state.results)
        assertEquals(
            listOf(SearchCall("moon", null, 1), SearchCall("mars", null, 1)),
            api.searchCalls,
        )
    }

    @Test
    fun `detail view model loads detail and refreshes after submit`() = runTest(dispatcher) {
        val detail = RequestMediaDetail(
            mediaType = RequestMediaType.Movie,
            tmdbId = 33,
            imdbId = "tt33",
            title = "Deep Archive",
            year = 2025,
            overview = "Buried media returns.",
            posterPath = "/poster.jpg",
            backdropPath = "/backdrop.jpg",
            request = RequestState(requestable = true),
        )
        val created = stubRequest(id = "request-33", tmdbId = 33, title = "Deep Archive")
        val refreshedDetail = detail.copy(
            request = RequestState(
                status = RequestStatus.Pending,
                requestable = false,
                requestId = "request-33",
            ),
        )
        val api = FakeRequestsApi(
            detailResults = ArrayDeque(
                listOf(
                    ApiResult.Success(detail),
                    ApiResult.Success(refreshedDetail),
                ),
            ),
            createResult = ApiResult.Success(created),
        )
        val viewModel = RequestDetailViewModel(
            repository = RequestsRepository(api),
            mediaType = RequestMediaType.Movie,
            tmdbId = 33,
        )

        viewModel.submitRequest()
        val state = viewModel.uiState.value

        assertFalse(state.isLoading)
        assertFalse(state.isSubmitting)
        assertEquals(refreshedDetail, state.detail)
        assertEquals("Request submitted.", state.notice)
        assertNull(state.error)
        assertEquals(
            CreateMediaRequest(
                mediaType = RequestMediaType.Movie,
                tmdbId = 33,
                imdbId = "tt33",
                title = "Deep Archive",
                year = 2025,
                overview = "Buried media returns.",
                posterPath = "/poster.jpg",
                backdropPath = "/backdrop.jpg",
            ),
            api.createRequests.single(),
        )
        assertEquals(
            listOf("detail:movie:33", "detail:movie:33"),
            api.calls,
        )
    }

    @Test
    fun `requests view model falls back to default message for blank discover errors`() = runTest(dispatcher) {
        val api = FakeRequestsApi(
            statusResult = ApiResult.Success(RequestsFeatureStatus(requestsEnabled = true)),
            discoverResult = ApiResult.Error(code = 500, error = "internal", message = ""),
        )

        val viewModel = RequestsViewModel(RequestsRepository(api))

        assertEquals("Failed to load requests", viewModel.uiState.value.error)
    }

    @Test
    fun `requests view model shows network copy when status request cannot reach the server`() = runTest(dispatcher) {
        val api = FakeRequestsApi(statusResult = ApiResult.NetworkError(IllegalStateException("offline")))

        val viewModel = RequestsViewModel(RequestsRepository(api))

        assertEquals("Network error. Check your connection.", viewModel.uiState.value.error)
    }

    @Test
    fun `my requests view model refreshes and cancels by id`() = runTest(dispatcher) {
        val pending = stubRequest(id = "request-1", tmdbId = 44, title = "Waiting")
        val cancelled = pending.copy(
            status = RequestStatus.Failed,
            outcome = RequestOutcome.Cancelled,
            updatedAt = "2026-06-02T00:00:00Z",
        )
        val api = FakeRequestsApi(
            mineResult = ApiResult.Success(RequestsListResponse(listOf(pending))),
            cancelResult = ApiResult.Success(cancelled),
        )
        val viewModel = MyRequestsViewModel(RequestsRepository(api))

        assertEquals(listOf(pending), viewModel.uiState.value.requests)

        viewModel.cancel("request-1")
        val state = viewModel.uiState.value

        assertFalse(state.isLoading)
        assertNull(state.actionInFlightId)
        assertEquals(listOf(cancelled), state.requests)
        assertNull(state.error)
        assertEquals(listOf("request-1"), api.cancelCalls)
    }
}

private data class SearchCall(
    val query: String,
    val mediaType: String?,
    val page: Int,
)

private class FakeRequestsApi(
    private val statusResult: ApiResult<RequestsFeatureStatus> =
        ApiResult.Success(RequestsFeatureStatus(requestsEnabled = true)),
    private val discoverResult: ApiResult<RequestsDiscoverResponse> =
        ApiResult.Success(RequestsDiscoverResponse()),
    private val searchResult: ApiResult<RequestMediaPage> =
        ApiResult.Success(RequestMediaPage()),
    private val detailResults: ArrayDeque<ApiResult<RequestMediaDetail>> =
        ArrayDeque(listOf(ApiResult.NetworkError(IllegalStateException("no detail fake")))),
    private val createResult: ApiResult<MediaRequest> =
        ApiResult.NetworkError(IllegalStateException("no create fake")),
    private val mineResult: ApiResult<RequestsListResponse> =
        ApiResult.Success(RequestsListResponse()),
    private val cancelResult: ApiResult<MediaRequest> =
        ApiResult.NetworkError(IllegalStateException("no cancel fake")),
    private val searchHandler: (suspend (
        query: String,
        mediaType: String?,
        page: Int,
    ) -> ApiResult<RequestMediaPage>)? = null,
) : RequestsApi {

    val calls = mutableListOf<String>()
    val searchCalls = mutableListOf<SearchCall>()
    val createRequests = mutableListOf<CreateMediaRequest>()
    val cancelCalls = mutableListOf<String>()

    override suspend fun status(): ApiResult<RequestsFeatureStatus> {
        calls += "status"
        return statusResult
    }

    override suspend fun discover(): ApiResult<RequestsDiscoverResponse> {
        calls += "discover"
        return discoverResult
    }

    override suspend fun discoverSection(section: String, page: Int): ApiResult<RequestMediaPage> =
        ApiResult.Success(RequestMediaPage(page = page))

    override suspend fun search(
        query: String,
        mediaType: String?,
        page: Int,
    ): ApiResult<RequestMediaPage> {
        searchCalls += SearchCall(query, mediaType, page)
        return searchHandler?.invoke(query, mediaType, page) ?: searchResult
    }

    override suspend fun detail(mediaType: String, tmdbId: Int): ApiResult<RequestMediaDetail> {
        calls += "detail:$mediaType:$tmdbId"
        return detailResults.removeFirstOrNull()
            ?: ApiResult.NetworkError(IllegalStateException("no detail fake"))
    }

    override suspend fun create(request: CreateMediaRequest): ApiResult<MediaRequest> {
        createRequests += request
        return createResult
    }

    override suspend fun mine(
        status: String?,
        outcome: String?,
        limit: Int?,
        offset: Int?,
    ): ApiResult<RequestsListResponse> {
        calls += "mine"
        return mineResult
    }

    override suspend fun get(id: String): ApiResult<MediaRequest> =
        ApiResult.NetworkError(IllegalStateException("no get fake"))

    override suspend fun cancel(id: String): ApiResult<MediaRequest> {
        cancelCalls += id
        return cancelResult
    }
}

private fun stubResult(
    tmdbId: Int,
    title: String,
    mediaType: String = RequestMediaType.Movie,
): RequestMediaResult = RequestMediaResult(
    mediaType = mediaType,
    tmdbId = tmdbId,
    title = title,
    availability = RequestAvailability.Missing,
)

private fun stubRequest(
    id: String,
    tmdbId: Int,
    title: String,
    status: String = RequestStatus.Pending,
    outcome: String = RequestOutcome.Active,
): MediaRequest = MediaRequest(
    id = id,
    mediaType = RequestMediaType.Movie,
    tmdbId = tmdbId,
    title = title,
    year = 2026,
    status = status,
    outcome = outcome,
    createdAt = "2026-06-01T00:00:00Z",
    updatedAt = "2026-06-01T00:00:00Z",
)
