package org.prairieserver.prairie.common.data.repository

import org.prairieserver.prairie.common.data.db.PrairieDatabase
import org.prairieserver.prairie.common.data.db.entity.DownloadSubscriptionEntity
import org.prairieserver.prairie.model.download.DownloadQuality
import org.prairieserver.prairie.model.download.DownloadSubscription
import org.prairieserver.prairie.model.download.DownloadSubscriptionMediaKind
import org.prairieserver.prairie.model.download.DownloadSubscriptionTargetType
import org.prairieserver.prairie.repository.DownloadSubscriptionRepository

class RoomDownloadSubscriptionRepository(db: PrairieDatabase) : DownloadSubscriptionRepository {
    private val dao = db.downloadSubscriptionDao()

    override suspend fun upsert(subscription: DownloadSubscription) {
        dao.upsert(subscription.toEntity())
    }

    override suspend fun active(serverId: String, profileId: String): List<DownloadSubscription> =
        dao.getActive(serverId, profileId).map { it.toModel() }

    override suspend fun all(serverId: String, profileId: String): List<DownloadSubscription> =
        dao.getAll(serverId, profileId).map { it.toModel() }

    override suspend fun disable(serverId: String, profileId: String, id: String, updatedAt: Long) {
        dao.disable(serverId, profileId, id, updatedAt)
    }

    override suspend fun updateEvaluation(
        serverId: String,
        profileId: String,
        id: String,
        evaluatedAt: Long,
        error: String?,
        updatedAt: Long,
    ) {
        dao.updateEvaluation(serverId, profileId, id, evaluatedAt, error, updatedAt)
    }

    override suspend fun deleteAllForServer(serverId: String) {
        dao.deleteAllForServer(serverId)
    }

    private fun DownloadSubscription.toEntity() = DownloadSubscriptionEntity(
        id = id,
        serverId = serverId,
        profileId = profileId,
        targetType = targetType.wire,
        targetId = targetId,
        displayTitle = displayTitle,
        mediaKind = mediaKind.wire,
        quality = quality.wire,
        wifiOnly = wifiOnly,
        enabled = enabled,
        includeExisting = includeExisting,
        keepUnwatchedLimit = keepUnwatchedLimit,
        deleteWatchedAfterDays = deleteWatchedAfterDays,
        createdAt = createdAt,
        updatedAt = updatedAt,
        lastEvaluatedAt = lastEvaluatedAt,
        lastError = lastError,
    )

    private fun DownloadSubscriptionEntity.toModel() = DownloadSubscription(
        id = id,
        serverId = serverId,
        profileId = profileId,
        targetType = DownloadSubscriptionTargetType.fromWire(targetType),
        targetId = targetId,
        displayTitle = displayTitle,
        mediaKind = DownloadSubscriptionMediaKind.fromWire(mediaKind),
        quality = DownloadQuality.fromWire(quality),
        wifiOnly = wifiOnly,
        enabled = enabled,
        includeExisting = includeExisting,
        keepUnwatchedLimit = keepUnwatchedLimit,
        deleteWatchedAfterDays = deleteWatchedAfterDays,
        createdAt = createdAt,
        updatedAt = updatedAt,
        lastEvaluatedAt = lastEvaluatedAt,
        lastError = lastError,
    )
}
