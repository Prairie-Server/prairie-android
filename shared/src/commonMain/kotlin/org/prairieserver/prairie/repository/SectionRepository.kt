package org.prairieserver.prairie.repository

import org.prairieserver.prairie.model.catalog.CatalogResponse
import org.prairieserver.prairie.model.section.HomeLayoutResponse
import org.prairieserver.prairie.model.section.HomeSectionItemsResponse
import org.prairieserver.prairie.model.section.LibraryCollection
import org.prairieserver.prairie.model.section.LibraryCollectionsResponse
import org.prairieserver.prairie.model.section.SectionsResponse
import org.prairieserver.prairie.network.ApiResult
import org.prairieserver.prairie.network.api.SectionApi
import org.prairieserver.prairie.network.map
import org.prairieserver.prairie.repository.port.CatalogCachePort
import org.prairieserver.prairie.repository.port.NoOpCatalogCachePort
import org.prairieserver.prairie.repository.port.canServeCache

class SectionRepository(
    private val sectionApi: SectionApi,
    /** Offline read cache for a library's Recommended sections (Track B). No-op by default. */
    private val catalogCache: CatalogCachePort = NoOpCatalogCachePort,
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

    /** Fetches a library's resolved sections (offline: last cached sections). */
    suspend fun getLibrarySections(libraryId: Int): ApiResult<SectionsResponse> {
        val result = sectionApi.getLibrarySections(libraryId)
        if (result is ApiResult.Success) {
            catalogCache.cacheLibrarySections(libraryId, result.data.sections)
            return result
        }
        if (result.canServeCache()) {
            catalogCache.getCachedLibrarySections(libraryId)
                ?.let { return ApiResult.Success(SectionsResponse(sections = it)) }
        }
        return result
    }

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
    /** Pages a library collection's items via the catalog resolver. */
    suspend fun getLibraryCollectionItems(
        collectionId: String,
        offset: Int = 0,
        limit: Int = 60,
    ): ApiResult<CatalogResponse> =
        sectionApi.getLibraryCollectionItems(collectionId, offset, limit)
}
