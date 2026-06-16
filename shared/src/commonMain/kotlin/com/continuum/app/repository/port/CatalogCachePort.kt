package com.continuum.app.repository.port

import com.continuum.app.model.catalog.CatalogResponse
import com.continuum.app.model.personal.UserLibrary
import com.continuum.app.network.ApiResult

/**
 * Offline read cache for catalog browse (Track B). Backs repository-level
 * cache-with-fallback in [com.continuum.app.repository.PersonalDataRepository]
 * (library list) and [com.continuum.app.repository.CatalogRepository] (a
 * library's default first page): a successful network result is cached; a later
 * offline/5xx failure serves the cached copy so the Libraries tab + grids render
 * with no network.
 *
 * commonMain port (so the shared repositories can depend on it) with a Room-backed
 * Android impl bound in the platform module; default no-op keeps tests/non-Android
 * network-only. Scope `(serverId, profileId)` is resolved inside the impl.
 */
interface CatalogCachePort {
    suspend fun cacheLibraries(libraries: List<UserLibrary>) {}
    suspend fun getCachedLibraries(): List<UserLibrary>? = null

    /** Cache the default (unfiltered, first-page) browse for a library. */
    suspend fun cacheDefaultLibraryPage(libraryId: Int, response: CatalogResponse) {}
    suspend fun getCachedDefaultLibraryPage(libraryId: Int): CatalogResponse? = null
}

/** Network-only default. */
object NoOpCatalogCachePort : CatalogCachePort

/**
 * Whether a failed result should fall back to cache: offline ([ApiResult.NetworkError],
 * which also covers parse failures) or a transient server 5xx. Never on a 4xx —
 * auth/permission/not-found must not silently serve stale data.
 */
fun ApiResult<*>.canServeCache(): Boolean = when (this) {
    is ApiResult.NetworkError -> true
    is ApiResult.Error -> code in 500..599
    is ApiResult.Success -> false
}
