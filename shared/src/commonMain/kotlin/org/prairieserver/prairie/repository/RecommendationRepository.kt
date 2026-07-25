package org.prairieserver.prairie.repository

import org.prairieserver.prairie.model.recommendation.DiscoverResponse
import org.prairieserver.prairie.model.recommendation.ScoredItemsResponse
import org.prairieserver.prairie.model.recommendation.TasteProfile
import org.prairieserver.prairie.network.ApiResult
import org.prairieserver.prairie.network.api.RecommendationApi

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
