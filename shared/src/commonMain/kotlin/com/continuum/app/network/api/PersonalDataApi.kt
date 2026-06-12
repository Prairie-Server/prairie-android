package com.continuum.app.network.api

import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.http.*
import com.continuum.app.model.catalog.BrowseItem
import com.continuum.app.model.catalog.CatalogResponse
import com.continuum.app.model.personal.*
import com.continuum.app.network.ApiResult

class PersonalDataApi(private val client: HttpClient) {

    // --- User Libraries ---

    suspend fun listUserLibraries(): ApiResult<List<UserLibrary>> = safeApiCall {
        client.get("/api/v1/user/libraries")
    }

    // --- Favorites ---

    suspend fun listFavorites(
        offset: Int = 0,
        limit: Int = 40
    ): ApiResult<CatalogResponse> = safeApiCall {
        client.get("/api/v1/favorites") {
            parameter("offset", offset)
            parameter("limit", limit)
        }
    }

    suspend fun checkFavorite(itemId: String): ApiResult<Boolean> = safeApiCall {
        client.get("/api/v1/favorites/$itemId")
    }

    suspend fun addFavorite(itemId: String): ApiResult<Unit> = safeApiCall {
        client.put("/api/v1/favorites/$itemId")
    }

    suspend fun removeFavorite(itemId: String): ApiResult<Unit> = safeApiCall {
        client.delete("/api/v1/favorites/$itemId")
    }

    // --- Watchlist ---

    suspend fun listWatchlist(
        offset: Int = 0,
        limit: Int = 40
    ): ApiResult<CatalogResponse> = safeApiCall {
        client.get("/api/v1/watchlist") {
            parameter("offset", offset)
            parameter("limit", limit)
        }
    }

    suspend fun checkWatchlist(itemId: String): ApiResult<Boolean> = safeApiCall {
        client.get("/api/v1/watchlist/$itemId")
    }

    suspend fun addToWatchlist(itemId: String): ApiResult<Unit> = safeApiCall {
        client.put("/api/v1/watchlist/$itemId")
    }

    suspend fun removeFromWatchlist(itemId: String): ApiResult<Unit> = safeApiCall {
        client.delete("/api/v1/watchlist/$itemId")
    }

    // --- History ---

    suspend fun listHistory(
        offset: Int = 0,
        limit: Int = 40
    ): ApiResult<CatalogResponse> = safeApiCall {
        client.get("/api/v1/history") {
            parameter("offset", offset)
            parameter("limit", limit)
        }
    }

    // --- Progress ---

    suspend fun listProgress(): ApiResult<ProgressListResponse> = safeApiCall {
        client.get("/api/v1/progress")
    }

    suspend fun syncProgress(request: SyncProgressRequest): ApiResult<Unit> = safeApiCall {
        client.post("/api/v1/sync/progress") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }
    }

    // --- Ratings ---

    suspend fun listRatings(): ApiResult<RatingsResponse> = safeApiCall {
        client.get("/api/v1/ratings")
    }

    suspend fun getRating(itemId: String): ApiResult<RatingEntry> = safeApiCall {
        client.get("/api/v1/ratings/$itemId")
    }

    suspend fun setRating(itemId: String, rating: Int): ApiResult<Unit> = safeApiCall {
        client.put("/api/v1/ratings/$itemId") {
            contentType(ContentType.Application.Json)
            setBody(SetRatingRequest(rating))
        }
    }

    suspend fun deleteRating(itemId: String): ApiResult<Unit> = safeApiCall {
        client.delete("/api/v1/ratings/$itemId")
    }

    // --- Watched ---

    /** Mark an item (movie / series / season / episode) as watched. Server resolves leaf targets. */
    suspend fun markWatched(itemId: String): ApiResult<Unit> = safeApiCall {
        client.post("/api/v1/watched/$itemId")
    }

    /** Mark an item as unwatched. */
    suspend fun markUnwatched(itemId: String): ApiResult<Unit> = safeApiCall {
        client.delete("/api/v1/watched/$itemId")
    }

    // --- Continue Watching dismissals ---

    /** Hide an item from the home Continue Watching row without deleting progress. */
    suspend fun dismissContinueWatching(
        itemId: String,
        progressUpdatedAt: String
    ): ApiResult<Unit> = safeApiCall {
        client.put("/api/v1/home/dismissals/continue_watching/$itemId") {
            contentType(ContentType.Application.Json)
            setBody(ContinueWatchingDismissalRequest(progressUpdatedAt))
        }
    }

    /** Undo a Continue Watching dismissal. */
    suspend fun undismissContinueWatching(itemId: String): ApiResult<Unit> = safeApiCall {
        client.delete("/api/v1/home/dismissals/continue_watching/$itemId")
    }
}
