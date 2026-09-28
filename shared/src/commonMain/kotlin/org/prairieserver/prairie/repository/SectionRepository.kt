package org.prairieserver.prairie.repository

import org.prairieserver.prairie.model.catalog.CatalogQueryGroup
import org.prairieserver.prairie.model.catalog.CatalogResponse
import org.prairieserver.prairie.model.section.HomeSectionItemsResponse
import org.prairieserver.prairie.model.section.LibraryCollection
import org.prairieserver.prairie.model.section.LibraryCollectionsResponse
import org.prairieserver.prairie.model.section.SectionsResponse
import org.prairieserver.prairie.network.ApiResult
import org.prairieserver.prairie.network.apiv2.CatalogContinuationV2
import org.prairieserver.prairie.network.apiv2.identityChanged
import org.prairieserver.prairie.network.api.SectionApi
import org.prairieserver.prairie.network.map
import org.prairieserver.prairie.repository.port.CatalogCachePort
import org.prairieserver.prairie.repository.port.NoOpCatalogCachePort
import org.prairieserver.prairie.repository.port.canServeCache
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive

class SectionRepository(
    private val sectionApi: SectionApi,
    /** Offline read cache for a library's Recommended sections (Track B). No-op by default. */
    private val catalogCache: CatalogCachePort = NoOpCatalogCachePort,
) {
    suspend fun captureHomeAuthority() = sectionApi.captureHomeAuthority()
    suspend fun isHomeAuthorityCurrent(owner: org.prairieserver.prairie.network.AuthScopeSnapshot) = sectionApi.isHomeAuthorityCurrent(owner)

    suspend fun getHomeSections(owner: org.prairieserver.prairie.network.AuthScopeSnapshot) = sectionApi.getHomeSections(owner)
    suspend fun getHomeSectionItems(id: String, owner: org.prairieserver.prairie.network.AuthScopeSnapshot) = sectionApi.getHomeSectionItems(id, owner)

    suspend fun dismissHomeItem(surface: String, itemId: String, anchor: String, owner: org.prairieserver.prairie.network.AuthScopeSnapshot) =
        sectionApi.dismissHomeItem(surface, itemId, anchor, owner)

    /** Scoped consumers never share the legacy Home request or its cache. */
    suspend fun loadScopedHomeSections(owner: org.prairieserver.prairie.network.AuthScopeSnapshot,
        stillCurrent: () -> Boolean, publish: (List<org.prairieserver.prairie.model.section.ResolvedSection>) -> Unit) {
        if (!currentCoroutineContext().isActive || !stillCurrent()) return
        // getHomeSections guards the owner before and after the exchange.
        val result = sectionApi.getHomeSections(owner)
        if (!currentCoroutineContext().isActive || !stillCurrent()) return
        if (result is ApiResult.Success) publish(result.data.sections)
    }

    /** Fetches a library's resolved sections (offline: last cached sections). */
    suspend fun getLibrarySections(libraryId: Int, owner: org.prairieserver.prairie.network.AuthScopeSnapshot): ApiResult<SectionsResponse> {
        // getLibrarySections guards the owner before and after the exchange.
        val result = sectionApi.getLibrarySections(libraryId, owner)
        if (result is ApiResult.Success) {
            catalogCache.cacheLibrarySectionsV2(libraryId, result.data.sections, owner)
            if (!isLibrarySectionAuthorityCurrent(owner)) return identityChanged()
            return result
        }
        if (result.canServeCache()) {
            val cached = catalogCache.getCachedLibrarySectionsV2(libraryId, owner)
            if (!isLibrarySectionAuthorityCurrent(owner)) return identityChanged()
            if (cached != null) return ApiResult.Success(SectionsResponse(cached))
        }
        return result
    }

    suspend fun captureLibrarySectionAuthority() = sectionApi.captureLibrarySectionAuthority()
    suspend fun isLibrarySectionAuthorityCurrent(owner: org.prairieserver.prairie.network.AuthScopeSnapshot) = sectionApi.isLibrarySectionAuthorityCurrent(owner)

    /** Fetches items within a specific library section. */
    suspend fun getLibrarySectionItems(
        libraryId: Int,
        sectionId: String,
        owner: org.prairieserver.prairie.network.AuthScopeSnapshot,
    ): ApiResult<HomeSectionItemsResponse> =
        sectionApi.getLibrarySectionItems(libraryId, sectionId, owner)

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
        continuation: CatalogContinuationV2? = null,
        limit: Int = 60,
        sort: String? = null,
        order: String? = null,
        queryGroups: List<CatalogQueryGroup> = emptyList(),
        match: String? = null,
    ): ApiResult<CatalogResponse> =
        sectionApi.getLibraryCollectionItems(
            collectionId = collectionId,
            continuation = continuation,
            limit = limit,
            sort = sort,
            order = order,
            queryGroups = queryGroups,
            match = match,
        )
}
