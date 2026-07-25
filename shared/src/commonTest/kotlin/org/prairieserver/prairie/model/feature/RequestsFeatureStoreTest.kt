package org.prairieserver.prairie.model.feature

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.prairieserver.prairie.model.request.CreateMediaRequest
import org.prairieserver.prairie.model.request.MediaRequest
import org.prairieserver.prairie.model.request.RequestMediaDetail
import org.prairieserver.prairie.model.request.RequestMediaPage
import org.prairieserver.prairie.model.request.RequestsDiscoverResponse
import org.prairieserver.prairie.model.request.RequestsFeatureStatus
import org.prairieserver.prairie.model.request.RequestsListResponse
import org.prairieserver.prairie.network.ApiResult
import org.prairieserver.prairie.network.api.RequestsApi
import org.prairieserver.prairie.repository.RequestsRepository

class RequestsFeatureStoreTest {

    @Test
    fun startsHiddenAndFollowsSuccessfulStatusProbe() = runTest {
        val store = RequestsFeatureStore(
            RequestsRepository(
                FakeRequestsApi(
                    ApiResult.Success(RequestsFeatureStatus(requestsEnabled = true)),
                ),
            ),
        )

        assertFalse(store.isEnabled.value)

        store.refresh()

        assertTrue(store.isEnabled.value)
    }

    @Test
    fun transientFailureKeepsPreviousCapabilityValue() = runTest {
        val api = FakeRequestsApi(
            ApiResult.Success(RequestsFeatureStatus(requestsEnabled = true)),
            ApiResult.NetworkError(IllegalStateException("offline")),
        )
        val store = RequestsFeatureStore(RequestsRepository(api))

        store.refresh()
        store.refresh()

        assertTrue(store.isEnabled.value)
    }

    @Test
    fun resetHidesRequestsBeforeNextProbe() = runTest {
        val store = RequestsFeatureStore(
            RequestsRepository(
                FakeRequestsApi(
                    ApiResult.Success(RequestsFeatureStatus(requestsEnabled = true)),
                ),
            ),
        )

        store.refresh()
        store.reset()

        assertFalse(store.isEnabled.value)
    }
}

private class FakeRequestsApi(
    vararg statusResults: ApiResult<RequestsFeatureStatus>,
) : RequestsApi {
    private val statusResults = statusResults.toMutableList()

    override suspend fun status(): ApiResult<RequestsFeatureStatus> =
        if (statusResults.isNotEmpty()) {
            statusResults.removeAt(0)
        } else {
            ApiResult.NetworkError(IllegalStateException("no fake status result"))
        }

    override suspend fun discover(): ApiResult<RequestsDiscoverResponse> =
        ApiResult.NetworkError(IllegalStateException("not used"))

    override suspend fun discoverSection(section: String, page: Int): ApiResult<RequestMediaPage> =
        ApiResult.NetworkError(IllegalStateException("not used"))

    override suspend fun search(
        query: String,
        mediaType: String?,
        page: Int,
    ): ApiResult<RequestMediaPage> = ApiResult.NetworkError(IllegalStateException("not used"))

    override suspend fun detail(mediaType: String, tmdbId: Int): ApiResult<RequestMediaDetail> =
        ApiResult.NetworkError(IllegalStateException("not used"))

    override suspend fun create(request: CreateMediaRequest): ApiResult<MediaRequest> =
        ApiResult.NetworkError(IllegalStateException("not used"))

    override suspend fun mine(
        status: String?,
        outcome: String?,
        limit: Int?,
        offset: Int?,
    ): ApiResult<RequestsListResponse> = ApiResult.NetworkError(IllegalStateException("not used"))

    override suspend fun get(id: String): ApiResult<MediaRequest> =
        ApiResult.NetworkError(IllegalStateException("not used"))

    override suspend fun cancel(id: String): ApiResult<MediaRequest> =
        ApiResult.NetworkError(IllegalStateException("not used"))
}
