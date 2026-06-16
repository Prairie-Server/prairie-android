package com.continuum.app.common.data.repository

import com.continuum.app.common.data.db.SiloDatabase
import com.continuum.app.common.data.db.entity.CatalogCacheEntity
import com.continuum.app.model.catalog.CatalogResponse
import com.continuum.app.model.personal.UserLibrary
import com.continuum.app.network.AuthScopeSnapshot
import com.continuum.app.repository.port.CatalogCachePort
import kotlinx.serialization.json.Json

/**
 * Room-backed [CatalogCachePort] (Track B). Stores each catalog read as a JSON
 * blob in the generic `catalog_cache` table, scoped to the active
 * `(serverId, profileId)`. Corrupt/forward-incompatible JSON decodes to null
 * rather than crashing the browse screen.
 */
class RoomCatalogCacheRepository(
    db: SiloDatabase,
    private val snapshotProvider: suspend () -> AuthScopeSnapshot?,
    private val now: () -> Long = { System.currentTimeMillis() },
) : CatalogCachePort {

    private val dao = db.catalogCacheDao()
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun cacheLibraries(libraries: List<UserLibrary>) =
        put(KEY_LIBRARIES, json.encodeToString(libraries))

    override suspend fun getCachedLibraries(): List<UserLibrary>? =
        get(KEY_LIBRARIES)?.let { runCatching { json.decodeFromString<List<UserLibrary>>(it) }.getOrNull() }

    override suspend fun cacheDefaultLibraryPage(libraryId: Int, response: CatalogResponse) =
        put(libraryKey(libraryId), json.encodeToString(response))

    override suspend fun getCachedDefaultLibraryPage(libraryId: Int): CatalogResponse? =
        get(libraryKey(libraryId))?.let { runCatching { json.decodeFromString<CatalogResponse>(it) }.getOrNull() }

    private suspend fun put(cacheKey: String, jsonStr: String) {
        val snapshot = snapshotProvider() ?: return
        val profileId = snapshot.profileId ?: return
        dao.upsert(
            CatalogCacheEntity(
                serverId = snapshot.serverId,
                profileId = profileId,
                cacheKey = cacheKey,
                json = jsonStr,
                cachedAtMs = now(),
            ),
        )
    }

    private suspend fun get(cacheKey: String): String? {
        val snapshot = snapshotProvider() ?: return null
        val profileId = snapshot.profileId ?: return null
        return dao.get(snapshot.serverId, profileId, cacheKey)?.json
    }

    private companion object {
        const val KEY_LIBRARIES = "libraries"
        fun libraryKey(libraryId: Int) = "library:$libraryId"
    }
}
