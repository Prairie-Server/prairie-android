package com.continuum.app.network.api

import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.http.*
import com.continuum.app.model.catalog.*
import com.continuum.app.network.ApiResult

class CatalogApi(private val client: HttpClient) {

    suspend fun getCatalog(
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
    ): ApiResult<CatalogResponse> = safeApiCall {
        client.get("/api/v1/catalog") {
            source?.let { parameter("source", it) }
            query?.let { parameter("q", it) }
            mediaType?.let { parameter("type", it) }
            libraryId?.let { parameter("library_id", it) }
            genre?.let { parameter("genre", it) }
            contentRating?.let { parameter("content_rating", it) }
            sort?.let { parameter("sort", it) }
            order?.let { parameter("order", it) }
            offset?.let { parameter("offset", it) }
            limit?.let { parameter("limit", it) }
            namePrefix?.let { parameter("name_prefix", it) }
            yearMin?.let { parameter("year_min", it) }
            yearMax?.let { parameter("year_max", it) }
            snapshotAt?.let { parameter("snapshot", it) }
        }
    }

    suspend fun getFilters(libraryId: Int? = null): ApiResult<CatalogFiltersResponse> = safeApiCall {
        client.get("/api/v1/catalog/filters") {
            libraryId?.let { parameter("library_id", it) }
        }
    }

    suspend fun getItemDetail(id: String): ApiResult<ItemDetail> = safeApiCall {
        client.get("/api/v1/catalog/items/$id")
    }

    suspend fun getItemVersions(id: String): ApiResult<List<FileVersion>> = safeApiCall {
        client.get("/api/v1/catalog/items/$id/versions")
    }

    suspend fun getItemEpisodes(id: String): ApiResult<EpisodesResponse> = safeApiCall {
        client.get("/api/v1/catalog/items/$id/episodes")
    }

    suspend fun getSeasons(seriesId: String): ApiResult<SeasonsResponse> = safeApiCall {
        client.get("/api/v1/catalog/series/$seriesId/seasons")
    }

    suspend fun getEpisodes(
        seriesId: String,
        seasonNumber: Int
    ): ApiResult<EpisodesResponse> = safeApiCall {
        client.get("/api/v1/catalog/series/$seriesId/seasons/$seasonNumber/episodes")
    }

    suspend fun getWatchDetail(id: String): ApiResult<WatchDetail> = safeApiCall {
        client.get("/api/v1/watch/$id")
    }

    suspend fun searchPeople(query: String? = null): ApiResult<List<Person>> = safeApiCall {
        client.get("/api/v1/people") {
            query?.let { parameter("q", it) }
        }
    }

    suspend fun getPerson(id: Int): ApiResult<Person> = safeApiCall {
        client.get("/api/v1/people/$id")
    }

    /**
     * Filmography for a person — wraps `/api/v1/catalog?source=person&person_id=...`.
     * Mirrors the iOS `personCatalogItems` helper.
     */
    suspend fun getPersonItems(
        personId: Int,
        mediaType: String? = null,
        offset: Int? = null,
        limit: Int? = null,
        snapshotAt: String? = null,
    ): ApiResult<CatalogResponse> = safeApiCall {
        client.get("/api/v1/catalog") {
            parameter("source", "person")
            parameter("person_id", personId.toString())
            parameter("sort", "year")
            parameter("order", "desc")
            mediaType?.let { parameter("type", it) }
            offset?.let { parameter("offset", it) }
            limit?.let { parameter("limit", it) }
            snapshotAt?.let { parameter("snapshot", it) }
        }
    }
}
