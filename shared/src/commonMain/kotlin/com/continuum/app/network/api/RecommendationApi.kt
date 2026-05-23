package com.continuum.app.network.api

import com.continuum.app.model.recommendation.DiscoverResponse
import com.continuum.app.model.recommendation.ScoredItemsResponse
import com.continuum.app.model.recommendation.TasteProfile
import com.continuum.app.network.ApiResult
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
