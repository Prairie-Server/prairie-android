package com.continuum.app.common.data.repository

import com.continuum.app.common.data.db.SiloDatabase
import com.continuum.app.common.data.db.entity.HomeCacheEntity
import com.continuum.app.model.section.ResolvedSection
import com.continuum.app.network.AuthScopeSnapshot
import com.continuum.app.repository.port.HomeCachePort
import com.continuum.app.repository.port.HomeCacheSnapshot
import kotlinx.serialization.json.Json

/**
 * Room-backed [HomeCachePort] (Track B). Stores the resolved home layout as a
 * single JSON blob per `(serverId, profileId)` so the home renders offline.
 *
 * Scope comes from the active [AuthScopeSnapshot]; with no active server/profile
 * there's nothing to cache or serve (returns null). Corrupt/forward-incompatible
 * JSON decodes to null rather than crashing the home screen.
 */
class RoomHomeCacheRepository(
    db: SiloDatabase,
    private val snapshotProvider: suspend () -> AuthScopeSnapshot?,
    private val now: () -> Long = { System.currentTimeMillis() },
) : HomeCachePort {

    private val dao = db.homeCacheDao()
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun cacheHome(sections: List<ResolvedSection>) {
        val snapshot = snapshotProvider() ?: return
        val profileId = snapshot.profileId ?: return
        dao.upsert(
            HomeCacheEntity(
                serverId = snapshot.serverId,
                profileId = profileId,
                sectionsJson = json.encodeToString(sections),
                cachedAtMs = now(),
            ),
        )
    }

    override suspend fun getCachedHome(): HomeCacheSnapshot? {
        val snapshot = snapshotProvider() ?: return null
        val profileId = snapshot.profileId ?: return null
        val row = dao.get(snapshot.serverId, profileId) ?: return null
        val sections = runCatching {
            json.decodeFromString<List<ResolvedSection>>(row.sectionsJson)
        }.getOrNull() ?: return null
        return HomeCacheSnapshot(sections = sections, cachedAtMs = row.cachedAtMs)
    }
}
