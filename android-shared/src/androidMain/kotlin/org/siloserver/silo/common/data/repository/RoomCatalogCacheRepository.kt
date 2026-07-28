package org.siloserver.silo.common.data.repository

import org.siloserver.silo.common.data.db.SiloDatabase
import org.siloserver.silo.common.data.db.entity.CatalogCacheEntity
import org.siloserver.silo.model.catalog.CatalogResponse
import org.siloserver.silo.model.catalog.EpisodesResponse
import org.siloserver.silo.model.catalog.ItemDetail
import org.siloserver.silo.model.catalog.SeasonsResponse
import org.siloserver.silo.model.personal.UserLibrary
import org.siloserver.silo.model.section.ResolvedSection
import org.siloserver.silo.network.AuthScopeSnapshot
import org.siloserver.silo.network.DefaultIdentityTransitionBarrier
import org.siloserver.silo.network.IdentityTransitionBarrier
import org.siloserver.silo.repository.port.CatalogCachePort
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
    private val identityTransitions: IdentityTransitionBarrier = DefaultIdentityTransitionBarrier(),
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

    override suspend fun cacheLibrarySections(libraryId: Int, sections: List<ResolvedSection>) =
        put(librarySectionsKey(libraryId), json.encodeToString(sections))

    override suspend fun getCachedLibrarySections(libraryId: Int): List<ResolvedSection>? =
        get(librarySectionsKey(libraryId))?.let { runCatching { json.decodeFromString<List<ResolvedSection>>(it) }.getOrNull() }

    override suspend fun cacheItemDetail(contentId: String, detail: ItemDetail) =
        put(itemDetailKey(contentId), json.encodeToString(detail))

    override suspend fun getCachedItemDetail(contentId: String): ItemDetail? =
        get(itemDetailKey(contentId))?.let { runCatching { json.decodeFromString<ItemDetail>(it) }.getOrNull() }

    override suspend fun cacheSeasons(seriesId: String, response: SeasonsResponse) =
        put(seasonsKey(seriesId), json.encodeToString(response))

    override suspend fun getCachedSeasons(seriesId: String): SeasonsResponse? =
        get(seasonsKey(seriesId))?.let { runCatching { json.decodeFromString<SeasonsResponse>(it) }.getOrNull() }

    override suspend fun cacheEpisodes(seriesId: String, seasonNumber: Int, response: EpisodesResponse) =
        put(episodesKey(seriesId, seasonNumber), json.encodeToString(response))

    override suspend fun getCachedEpisodes(seriesId: String, seasonNumber: Int): EpisodesResponse? =
        get(episodesKey(seriesId, seasonNumber))?.let { runCatching { json.decodeFromString<EpisodesResponse>(it) }.getOrNull() }

    private suspend fun put(cacheKey: String, jsonStr: String) {
        val requestIdentityGeneration = identityTransitions.generation.value
        val snapshot = snapshotProvider() ?: return
        val profileId = snapshot.profileId ?: return
        if (requestIdentityGeneration != identityTransitions.generation.value) return
        // A Room row must fit SQLite's ~2MB CursorWindow or the *read* throws
        // SQLiteBlobTooBigException. Big library pages can exceed it, so don't
        // store an unreadable row — drop any prior row for this key and skip.
        if (jsonStr.encodeToByteArray().size > MAX_CACHE_BYTES) {
            runCatching { dao.delete(snapshot.serverId, profileId, cacheKey) }
            return
        }
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
        // An oversized legacy row throws SQLiteBlobTooBigException on read; purge
        // it so it can't keep crashing the browse screen, and serve online.
        return runCatching { dao.get(snapshot.serverId, profileId, cacheKey) }
            .getOrElse {
                runCatching { dao.delete(snapshot.serverId, profileId, cacheKey) }
                null
            }?.json
    }

    private companion object {
        // Stay well under SQLite's ~2MB CursorWindow.
        const val MAX_CACHE_BYTES = 1_500_000
        const val KEY_LIBRARIES = "libraries"
        fun libraryKey(libraryId: Int) = "library:$libraryId"
        fun librarySectionsKey(libraryId: Int) = "library-sections:$libraryId"
        fun itemDetailKey(contentId: String) = "item-detail:$contentId"
        fun seasonsKey(seriesId: String) = "seasons:$seriesId"
        fun episodesKey(seriesId: String, seasonNumber: Int) = "episodes:$seriesId:$seasonNumber"
    }
}
