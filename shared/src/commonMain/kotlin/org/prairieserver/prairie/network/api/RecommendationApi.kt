package org.prairieserver.prairie.network.api

import org.prairieserver.prairie.model.recommendation.DiscoverResponse
import org.prairieserver.prairie.model.recommendation.ScoredItemsResponse
import org.prairieserver.prairie.model.recommendation.TasteProfile
import org.prairieserver.prairie.network.ApiResult
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.parameter

class RecommendationApi(private val client: HttpClient) {

    suspend fun getDiscover(): ApiResult<DiscoverResponse> = safeApiCall {
        client.get("/api/v1/recommendations/discover")
    }

    suspend fun getTasteProfile(): ApiResult<TasteProfile> = safeApiCall {
        client.get("/api/v1/recommendations/taste-profile")
    }

    suspend fun getSimilar(
        contentId: String,
        limit: Int = 12,
    ): ApiResult<ScoredItemsResponse> = safeApiCall {
        client.get("/api/v1/recommendations/similar/$contentId") {
            parameter("limit", limit)
        }
    }
}
