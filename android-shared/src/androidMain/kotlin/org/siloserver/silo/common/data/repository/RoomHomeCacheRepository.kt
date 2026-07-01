package org.siloserver.silo.common.data.repository

import org.siloserver.silo.common.data.db.SiloDatabase
import org.siloserver.silo.common.data.db.entity.HomeCacheEntity
import org.siloserver.silo.model.section.ResolvedSection
import org.siloserver.silo.network.AuthScopeSnapshot
import org.siloserver.silo.repository.port.HomeCachePort
import org.siloserver.silo.repository.port.HomeCacheSnapshot
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
