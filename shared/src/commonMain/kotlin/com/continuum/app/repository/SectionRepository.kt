package com.continuum.app.repository

import com.continuum.app.model.catalog.BrowseResponse
import com.continuum.app.model.section.HomeLayoutResponse
import com.continuum.app.model.section.HomeSectionItemsResponse
import com.continuum.app.model.section.LibraryCollection
import com.continuum.app.model.section.LibraryCollectionsResponse
import com.continuum.app.model.section.SectionsResponse
import com.continuum.app.network.ApiResult
import com.continuum.app.network.api.SectionApi
import com.continuum.app.network.map

class SectionRepository(
    private val sectionApi: SectionApi,
) {
    /** Fetches the home screen layout configuration. */
    suspend fun getHomeLayout(): ApiResult<HomeLayoutResponse> =
        sectionApi.getHomeLayout()

    /** Fetches all home screen sections (with items pre-resolved). */
    suspend fun getHomeSections(): ApiResult<SectionsResponse> =
        sectionApi.getHomeSections()

    /** Fetches the items within a specific home section. */
    suspend fun getHomeSectionItems(sectionId: String): ApiResult<HomeSectionItemsResponse> =
        sectionApi.getHomeSectionItems(sectionId)

    /** Fetches sections for a specific library. */
    suspend fun getLibrarySections(libraryId: Int): ApiResult<SectionsResponse> =
        sectionApi.getLibrarySections(libraryId)

    /** Fetches items within a specific library section. */
    suspend fun getLibrarySectionItems(
        libraryId: Int,
        sectionId: String,
    ): ApiResult<HomeSectionItemsResponse> =
        sectionApi.getLibrarySectionItems(libraryId, sectionId)

    /** Lists collections within a library as a flat list. Callers that need
     *  the grouped layout should use [getLibraryCollectionsGrouped]. */
    suspend fun getLibraryCollections(libraryId: Int): ApiResult<List<LibraryCollection>> =
        sectionApi.getLibraryCollections(libraryId).map { it.collections }

    /** Lists collections within a library, preserving group structure. */
    suspend fun getLibraryCollectionsGrouped(libraryId: Int): ApiResult<LibraryCollectionsResponse> =
        sectionApi.getLibraryCollections(libraryId)

    /** Fetches items within a library collection. */
    suspend fun getLibraryCollectionItems(
        libraryId: Int,
        collectionId: String,
    ): ApiResult<BrowseResponse> =
        sectionApi.getLibraryCollectionItems(libraryId, collectionId)
}
