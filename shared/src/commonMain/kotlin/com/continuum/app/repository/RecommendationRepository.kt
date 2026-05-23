package com.continuum.app.repository

import com.continuum.app.model.recommendation.DiscoverResponse
import com.continuum.app.model.recommendation.ScoredItemsResponse
import com.continuum.app.model.recommendation.TasteProfile
import com.continuum.app.network.ApiResult
import com.continuum.app.network.api.RecommendationApi

class RecommendationRepository(
    private val recommendationApi: RecommendationApi,
) {
    suspend fun getDiscover(): ApiResult<DiscoverResponse> =
        recommendationApi.getDiscover()

    suspend fun getTasteProfile(): ApiResult<TasteProfile> =
        recommendationApi.getTasteProfile()

    suspend fun getSimilar(
        contentId: String,
        limit: Int = 12,
    ): ApiResult<ScoredItemsResponse> =
        recommendationApi.getSimilar(contentId, limit)
}
