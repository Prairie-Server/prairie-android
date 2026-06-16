package com.continuum.app.repository

import com.continuum.app.model.catalog.CatalogResponse
import com.continuum.app.model.personal.ProgressListResponse
import com.continuum.app.model.personal.RatingEntry
import com.continuum.app.model.personal.SyncProgressItem
import com.continuum.app.model.personal.SyncProgressRequest
import com.continuum.app.model.personal.UserLibrary
import com.continuum.app.network.ApiResult
import com.continuum.app.network.api.PersonalDataApi
import com.continuum.app.network.map
import com.continuum.app.repository.port.NoOpUserItemStatePort
import com.continuum.app.repository.port.UserItemStatePort
import com.continuum.app.repository.port.toWriteOutcome

open class PersonalDataRepository(
    private val personalDataApi: PersonalDataApi,
    /**
     * Local-first side-channel (Track B). Defaults to the no-op port so
     * commonMain/tests stay network-only; the Android platform module binds a
     * Room-backed [UserItemStatePort] that records an optimistic projection +
     * outbox op around each content-level mutation below.
     */
    private val userItemStatePort: UserItemStatePort = NoOpUserItemStatePort,
) {
    // -- Libraries --

    /** Lists the libraries visible to the current user. */
    suspend fun listUserLibraries(): ApiResult<List<UserLibrary>> =
        personalDataApi.listUserLibraries()

    // -- Favorites --

    /** Lists the user's favorite items with pagination. */
    suspend fun listFavorites(offset: Int = 0, limit: Int = 40): ApiResult<CatalogResponse> =
        personalDataApi.listFavorites(offset, limit)

    /** Checks whether a specific item is in the user's favorites. */
    suspend fun isFavorite(itemId: String): ApiResult<Boolean> =
        personalDataApi.checkFavorite(itemId)

    /**
     * Adds or removes an item from the user's favorites.
     * @param isFavorite true to add, false to remove.
     */
    suspend fun toggleFavorite(itemId: String, isFavorite: Boolean): ApiResult<Unit> {
        val handle = userItemStatePort.recordFavorite(itemId, isFavorite)
        val result = if (isFavorite) {
            personalDataApi.addFavorite(itemId, handle.scope)
        } else {
            personalDataApi.removeFavorite(itemId, handle.scope)
        }
        userItemStatePort.resolve(handle, result.toWriteOutcome())
        return result
    }

    // -- Watchlist --

    /** Lists the user's watchlist items with pagination. */
    suspend fun listWatchlist(offset: Int = 0, limit: Int = 40): ApiResult<CatalogResponse> =
        personalDataApi.listWatchlist(offset, limit)

    /** Checks whether a specific item is on the user's watchlist. */
    suspend fun isInWatchlist(itemId: String): ApiResult<Boolean> =
        personalDataApi.checkWatchlist(itemId)

    /**
     * Adds or removes an item from the user's watchlist.
     * @param isInWatchlist true to add, false to remove.
     */
    suspend fun toggleWatchlist(itemId: String, isInWatchlist: Boolean): ApiResult<Unit> =
        if (isInWatchlist) {
            personalDataApi.addToWatchlist(itemId)
        } else {
            personalDataApi.removeFromWatchlist(itemId)
        }

    // -- History --

    /** Lists the user's watch history with pagination. */
    suspend fun listHistory(offset: Int = 0, limit: Int = 40): ApiResult<CatalogResponse> =
        personalDataApi.listHistory(offset, limit)

    // -- Progress --

    /** Lists all in-progress items for the current user. */
    suspend fun listProgress(): ApiResult<ProgressListResponse> =
        personalDataApi.listProgress()

    /** Syncs local progress state with the server. */
    open suspend fun syncProgress(items: List<SyncProgressItem>): ApiResult<Unit> =
        personalDataApi.syncProgress(SyncProgressRequest(items = items))

    // -- Ratings --

    /** Lists all ratings the current user has set. */
    suspend fun listRatings(): ApiResult<List<RatingEntry>> =
        personalDataApi.listRatings().map { it.ratings }

    /** Gets the user's rating for a specific item. */
    suspend fun getRating(itemId: String): ApiResult<RatingEntry> =
        personalDataApi.getRating(itemId)

    /** Sets or updates the user's star rating (integer 1-5) for a specific item. */
    suspend fun setRating(itemId: String, rating: Int): ApiResult<Unit> {
        val handle = userItemStatePort.recordRating(itemId, rating)
        val result = personalDataApi.setRating(itemId, rating, handle.scope)
        userItemStatePort.resolve(handle, result.toWriteOutcome())
        return result
    }

    /** Removes the user's rating for a specific item. */
    suspend fun deleteRating(itemId: String): ApiResult<Unit> {
        val handle = userItemStatePort.recordRating(itemId, null)
        val result = personalDataApi.deleteRating(itemId, handle.scope)
        userItemStatePort.resolve(handle, result.toWriteOutcome())
        return result
    }

    // -- Watched --

    /**
     * Toggle the watched state for an item. The server resolves leaf
     * targets, so passing a series / season ID marks the appropriate
     * episodes.
     */
    open suspend fun setWatched(itemId: String, watched: Boolean): ApiResult<Unit> {
        val handle = userItemStatePort.recordWatched(itemId, watched)
        val result = if (watched) {
            personalDataApi.markWatched(itemId, handle.scope)
        } else {
            personalDataApi.markUnwatched(itemId, handle.scope)
        }
        userItemStatePort.resolve(handle, result.toWriteOutcome())
        return result
    }

    // -- Continue Watching dismissals --

    /** Hide an item from the home Continue Watching row. */
    open suspend fun dismissContinueWatching(
        itemId: String,
        progressUpdatedAt: String
    ): ApiResult<Unit> =
        personalDataApi.dismissContinueWatching(itemId, progressUpdatedAt)

    /** Undo a Continue Watching dismissal. */
    open suspend fun undismissContinueWatching(itemId: String): ApiResult<Unit> =
        personalDataApi.undismissContinueWatching(itemId)
}
