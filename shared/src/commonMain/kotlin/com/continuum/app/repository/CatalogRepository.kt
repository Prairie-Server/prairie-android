package com.continuum.app.repository

import com.continuum.app.model.catalog.CatalogFiltersResponse
import com.continuum.app.model.catalog.CatalogResponse
import com.continuum.app.model.catalog.EpisodesResponse
import com.continuum.app.model.catalog.FileVersion
import com.continuum.app.model.catalog.ItemDetail
import com.continuum.app.model.catalog.Person
import com.continuum.app.model.catalog.SeasonsResponse
import com.continuum.app.model.catalog.WatchDetail
import com.continuum.app.network.ApiResult
import com.continuum.app.network.api.CatalogApi

class CatalogRepository(
    private val catalogApi: CatalogApi,
) {
    /** Browse the catalog with optional filters, sorting, and pagination. */
    suspend fun browse(
        source: String? = null,
        query: String? = null,
        mediaType: String? = null,
        libraryId: Int? = null,
        genre: String? = null,
        contentRating: String? = null,
        sort: String? = null,
        order: String? = null,
        offset: Int? = null,
        limit: Int? = null,
        namePrefix: String? = null,
        yearMin: Int? = null,
        yearMax: Int? = null,
        snapshotAt: String? = null,
    ): ApiResult<CatalogResponse> =
        catalogApi.getCatalog(
            source = source,
            query = query,
            mediaType = mediaType,
            libraryId = libraryId,
            genre = genre,
            contentRating = contentRating,
            sort = sort,
            order = order,
            offset = offset,
            limit = limit,
            namePrefix = namePrefix,
            yearMin = yearMin,
            yearMax = yearMax,
            snapshotAt = snapshotAt,
        )

    /** Returns available filter options (genres, studios, etc.) for the catalog. */
    suspend fun getFilters(libraryId: Int? = null): ApiResult<CatalogFiltersResponse> =
        catalogApi.getFilters(libraryId)

    /** Fetches full metadata for a single catalog item. */
    suspend fun getItemDetail(contentId: String): ApiResult<ItemDetail> =
        catalogApi.getItemDetail(contentId)

    /** Fetches playback-oriented detail (versions, user progress, intro/credits markers). */
    suspend fun getWatchDetail(contentId: String): ApiResult<WatchDetail> =
        catalogApi.getWatchDetail(contentId)

    /** Lists seasons for a series. */
    suspend fun getSeasons(seriesId: String): ApiResult<SeasonsResponse> =
        catalogApi.getSeasons(seriesId)

    /** Lists episodes for a specific season of a series. */
    suspend fun getEpisodes(seriesId: String, seasonNumber: Int): ApiResult<EpisodesResponse> =
        catalogApi.getEpisodes(seriesId, seasonNumber)

    /** Lists all episodes directly attached to an item (e.g. a season content ID). */
    suspend fun getItemEpisodes(contentId: String): ApiResult<EpisodesResponse> =
        catalogApi.getItemEpisodes(contentId)

    /** Lists all available file versions for an item. */
    suspend fun getItemVersions(contentId: String): ApiResult<List<FileVersion>> =
        catalogApi.getItemVersions(contentId)

    /** Searches for people (cast/crew) by name. */
    suspend fun searchPeople(query: String): ApiResult<List<Person>> =
        catalogApi.searchPeople(query)

    /** Fetches details for a specific person. */
    suspend fun getPerson(id: Int): ApiResult<Person> =
        catalogApi.getPerson(id)

    /** Filmography for a person — movies and series they appear in. */
    suspend fun getPersonItems(
        personId: Int,
        mediaType: String? = null,
        offset: Int? = null,
        limit: Int? = null,
    ): ApiResult<CatalogResponse> =
        catalogApi.getPersonItems(personId, mediaType, offset, limit)
}
