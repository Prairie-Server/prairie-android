package org.prairieserver.prairie.network.api

import org.prairieserver.prairie.model.request.CreateMediaRequest
import org.prairieserver.prairie.model.request.MediaRequest
import org.prairieserver.prairie.model.request.RequestMediaDetail
import org.prairieserver.prairie.model.request.RequestMediaPage
import org.prairieserver.prairie.model.request.RequestsDiscoverResponse
import org.prairieserver.prairie.model.request.RequestsFeatureStatus
import org.prairieserver.prairie.model.request.RequestsListResponse
import org.prairieserver.prairie.network.ApiResult
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType

/**
 * User-facing media request endpoints. Kept behind an interface so repository
 * tests can fake the transport, matching the DeviceLogin API shape.
 */
interface RequestsApi {

    suspend fun status(): ApiResult<RequestsFeatureStatus>

    suspend fun discover(): ApiResult<RequestsDiscoverResponse>

    suspend fun discoverSection(section: String, page: Int = 1): ApiResult<RequestMediaPage>

    suspend fun search(
        query: String,
        mediaType: String? = null,
        page: Int = 1,
    ): ApiResult<RequestMediaPage>

    suspend fun detail(mediaType: String, tmdbId: Int): ApiResult<RequestMediaDetail>

    suspend fun create(request: CreateMediaRequest): ApiResult<MediaRequest>

    suspend fun mine(
        status: String? = null,
        outcome: String? = null,
        limit: Int? = null,
        offset: Int? = null,
    ): ApiResult<RequestsListResponse>

    suspend fun get(id: String): ApiResult<MediaRequest>

    suspend fun cancel(id: String): ApiResult<MediaRequest>
}

class DefaultRequestsApi(private val client: HttpClient) : RequestsApi {

    override suspend fun status(): ApiResult<RequestsFeatureStatus> = safeApiCall {
        client.get("/api/v1/requests/status")
    }

    override suspend fun discover(): ApiResult<RequestsDiscoverResponse> = safeApiCall {
        client.get("/api/v1/requests/discover")
    }

    override suspend fun discoverSection(section: String, page: Int): ApiResult<RequestMediaPage> = safeApiCall {
        client.get("/api/v1/requests/discover/$section") {
            parameter("page", page)
        }
    }

    override suspend fun search(
        query: String,
        mediaType: String?,
        page: Int,
    ): ApiResult<RequestMediaPage> = safeApiCall {
        client.get("/api/v1/requests/search") {
            parameter("q", query)
            parameter("media_type", mediaType)
            parameter("page", page)
        }
    }

    override suspend fun detail(mediaType: String, tmdbId: Int): ApiResult<RequestMediaDetail> = safeApiCall {
        client.get("/api/v1/requests/detail/$mediaType/$tmdbId")
    }

    override suspend fun create(request: CreateMediaRequest): ApiResult<MediaRequest> = safeApiCall {
        client.post("/api/v1/requests/") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }
    }

    override suspend fun mine(
        status: String?,
        outcome: String?,
        limit: Int?,
        offset: Int?,
    ): ApiResult<RequestsListResponse> = safeApiCall {
        client.get("/api/v1/requests/mine") {
            parameter("status", status)
            parameter("outcome", outcome)
            parameter("limit", limit)
            parameter("offset", offset)
        }
    }

    override suspend fun get(id: String): ApiResult<MediaRequest> = safeApiCall {
        client.get("/api/v1/requests/$id")
    }

    override suspend fun cancel(id: String): ApiResult<MediaRequest> = safeApiCall {
        client.post("/api/v1/requests/$id/cancel")
    }
}
