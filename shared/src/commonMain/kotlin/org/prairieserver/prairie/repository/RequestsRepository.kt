package org.prairieserver.prairie.repository

import org.prairieserver.prairie.model.request.CreateMediaRequest
import org.prairieserver.prairie.model.request.MediaRequest
import org.prairieserver.prairie.model.request.RequestMediaDetail
import org.prairieserver.prairie.model.request.RequestMediaPage
import org.prairieserver.prairie.model.request.RequestsDiscoverResponse
import org.prairieserver.prairie.model.request.RequestsFeatureStatus
import org.prairieserver.prairie.network.ApiResult
import org.prairieserver.prairie.network.api.RequestsApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class RequestsRepository(private val api: RequestsApi) {

    private val _mine = MutableStateFlow<List<MediaRequest>>(emptyList())
    val mine: StateFlow<List<MediaRequest>> = _mine.asStateFlow()

    suspend fun status(): ApiResult<RequestsFeatureStatus> = api.status()

    suspend fun discover(): ApiResult<RequestsDiscoverResponse> = api.discover()

    suspend fun discoverSection(section: String, page: Int = 1): ApiResult<RequestMediaPage> =
        api.discoverSection(section, page)

    suspend fun search(
        query: String,
        mediaType: String? = null,
        page: Int = 1,
    ): ApiResult<RequestMediaPage> = api.search(query, mediaType, page)

    suspend fun detail(mediaType: String, tmdbId: Int): ApiResult<RequestMediaDetail> =
        api.detail(mediaType, tmdbId)

    suspend fun get(id: String): ApiResult<MediaRequest> = api.get(id)

    suspend fun refreshMine(
        status: String? = null,
        outcome: String? = null,
        limit: Int? = null,
        offset: Int? = null,
    ): ApiResult<Unit> = when (val result = api.mine(status, outcome, limit, offset)) {
        is ApiResult.Success -> {
            _mine.value = result.data.requests
            ApiResult.Success(Unit)
        }
        is ApiResult.Error -> ApiResult.Error(result.code, result.error, result.message)
        is ApiResult.NetworkError -> ApiResult.NetworkError(result.exception)
    }

    suspend fun create(request: CreateMediaRequest): ApiResult<MediaRequest> =
        when (val result = api.create(request)) {
            is ApiResult.Success -> {
                upsertMine(result.data)
                ApiResult.Success(result.data)
            }
            is ApiResult.Error -> ApiResult.Error(result.code, result.error, result.message)
            is ApiResult.NetworkError -> ApiResult.NetworkError(result.exception)
        }

    suspend fun cancel(id: String): ApiResult<MediaRequest> =
        when (val result = api.cancel(id)) {
            is ApiResult.Success -> {
                upsertMine(result.data)
                ApiResult.Success(result.data)
            }
            is ApiResult.Error -> ApiResult.Error(result.code, result.error, result.message)
            is ApiResult.NetworkError -> ApiResult.NetworkError(result.exception)
        }

    fun reset() {
        _mine.value = emptyList()
    }

    private fun upsertMine(request: MediaRequest) {
        _mine.update { list ->
            val replaced = list.map { if (it.id == request.id) request else it }
            if (replaced.any { it.id == request.id }) replaced else replaced + request
        }
    }
}
