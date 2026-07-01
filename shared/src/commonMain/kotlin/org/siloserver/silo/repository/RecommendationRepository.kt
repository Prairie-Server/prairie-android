package org.siloserver.silo.repository

import org.siloserver.silo.model.recommendation.DiscoverResponse
import org.siloserver.silo.model.recommendation.ScoredItemsResponse
import org.siloserver.silo.model.recommendation.TasteProfile
import org.siloserver.silo.network.ApiResult
import org.siloserver.silo.network.api.RecommendationApi

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
