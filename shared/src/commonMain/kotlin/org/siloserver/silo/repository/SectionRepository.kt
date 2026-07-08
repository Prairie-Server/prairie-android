package org.siloserver.silo.repository

import org.siloserver.silo.model.catalog.CatalogResponse
import org.siloserver.silo.model.section.HomeLayoutResponse
import org.siloserver.silo.model.section.HomeSectionItemsResponse
import org.siloserver.silo.model.section.LibraryCollection
import org.siloserver.silo.model.section.LibraryCollectionsResponse
import org.siloserver.silo.model.section.SectionsResponse
import org.siloserver.silo.network.ApiResult
import org.siloserver.silo.network.api.SectionApi
import org.siloserver.silo.network.map
import org.siloserver.silo.repository.port.CatalogCachePort
import org.siloserver.silo.repository.port.NoOpCatalogCachePort
import org.siloserver.silo.repository.port.canServeCache

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
