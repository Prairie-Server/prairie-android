package com.continuum.app.common.data.repository

import androidx.room.withTransaction
import com.continuum.app.common.data.db.SiloDatabase
import com.continuum.app.common.data.db.entity.ContentItemStateEntity
import com.continuum.app.common.data.db.entity.DirtyOperationEntity
import com.continuum.app.common.data.db.entity.UserItemStateEntity
import com.continuum.app.common.data.sync.OutboxOperation
import com.continuum.app.common.data.sync.OutboxSyncScheduler
import com.continuum.app.network.AuthScopeSnapshot
import com.continuum.app.repository.port.OutboxHandle
import com.continuum.app.repository.port.UserItemStatePort
import com.continuum.app.repository.port.WriteOutcome
import kotlinx.serialization.json.JsonPrimitive
import java.util.UUID

/**
 * Room-backed [UserItemStatePort] (Track B). Each content-level mutation
 * writes an optimistic [ContentItemStateEntity] projection **and** a pending
 * [DirtyOperationEntity] outbox op in one transaction, then returns a handle so
 * [com.continuum.app.repository.PersonalDataRepository] can [resolve] the op
 * with the inline network outcome.
 *
 * Scope is captured as ONE atomic [AuthScopeSnapshot] per call (not separate
 * server/profile reads, which could straddle a switch). The snapshot's
 * `(serverId, profileId)` scopes the projection + outbox rows, and the handle
 * carries the snapshot so the repository can PIN the inline network call to the
 * exact scope the op was recorded for — a switch between record and send then
 * can't route the write to the wrong account. No active scope (or no profile) →
 * records nothing and returns [OutboxHandle.NONE]; the inline call still runs
 * (unpinned, current scope) so foreground behaviour is unchanged.
 *
 * Coalesce keys are server-scoped (`serverId|profileId|contentId|kind`) so a
 * newer pending op of the same kind+target replaces an older un-synced one
 * without crossing server/profile boundaries.
 */
class RoomUserItemStateRepository(
    private val db: SiloDatabase,
    private val snapshotProvider: suspend () -> AuthScopeSnapshot?,
    private val syncScheduler: OutboxSyncScheduler = OutboxSyncScheduler.NONE,
    private val now: () -> Long = { System.currentTimeMillis() },
    private val idGenerator: () -> String = { UUID.randomUUID().toString() },
) : UserItemStatePort {

    private val contentDao = db.contentItemStateDao()
    private val userStateDao = db.userItemStateDao()
    private val outboxDao = db.dirtyOperationDao()

    override suspend fun recordWatched(contentId: String, watched: Boolean): OutboxHandle =
        record(contentId, OutboxOperation.SET_WATCHED, JsonPrimitive(watched).toString()) {
            it.copy(watched = watched)
        }

    override suspend fun recordFavorite(contentId: String, favorite: Boolean): OutboxHandle =
        record(contentId, OutboxOperation.SET_FAVORITE, JsonPrimitive(favorite).toString()) {
            it.copy(favorite = favorite)
        }

    override suspend fun recordRating(contentId: String, rating: Int?): OutboxHandle =
        record(
            contentId,
            OutboxOperation.SET_RATING,
            if (rating == null) "null" else JsonPrimitive(rating).toString(),
        ) {
            it.copy(ratingValue = rating)
        }

    override suspend fun recordPosition(
        contentId: String,
        fileId: Int,
        positionSeconds: Double,
        durationSeconds: Double?,
    ) {
        // Reject values that would enqueue invalid JSON and poison the drain
        // (a NaN/Infinity/negative would parse-fail after claim and retry forever).
        if (contentId.isBlank() || !positionSeconds.isFinite() || positionSeconds < 0.0) return
        val safeDuration = durationSeconds?.takeIf { it.isFinite() && it > 0.0 }

        val snapshot = snapshotProvider() ?: return
        val serverId = snapshot.serverId
        val profileId = snapshot.profileId ?: return
        val nowMs = now()

        db.withTransaction {
            // File-level local projection for resume (preserve track/cfi fields).
            val existing = userStateDao.get(serverId, profileId, contentId, fileId)
            val row = existing?.copy(
                positionSeconds = positionSeconds,
                durationSeconds = safeDuration,
                clientUpdatedAtMs = nowMs,
            ) ?: UserItemStateEntity(
                serverId = serverId,
                profileId = profileId,
                contentId = contentId,
                fileId = fileId,
                positionSeconds = positionSeconds,
                durationSeconds = safeDuration,
                audioFingerprint = null,
                subtitleFingerprint = null,
                cfi = null,
                readProgress = null,
                clientUpdatedAtMs = nowMs,
                serverUpdatedAtMs = null,
            )
            userStateDao.upsert(row)

            // Content-level outbox op: syncProgress is keyed by content id, so the
            // coalesce key omits fileId — all pending positions for the item
            // collapse to the latest.
            outboxDao.enqueueCoalescing(
                DirtyOperationEntity(
                    opKind = OutboxOperation.SET_POSITION,
                    serverId = serverId,
                    profileId = profileId,
                    targetContentId = contentId,
                    targetFileId = fileId,
                    coalesceKey = "$serverId|$profileId|$contentId|${OutboxOperation.SET_POSITION}",
                    idempotencyKey = idGenerator(),
                    payloadJson = OutboxOperation.encodePositionPayload(positionSeconds, safeDuration),
                    createdAtMs = nowMs,
                    nextAttemptAtMs = nowMs,
                ),
            )
        }
    }

    override suspend fun localPosition(contentId: String, fileId: Int): Double? {
        val snapshot = snapshotProvider() ?: return null
        val profileId = snapshot.profileId ?: return null
        return userStateDao.get(snapshot.serverId, profileId, contentId, fileId)
            ?.positionSeconds
            ?.takeIf { it > 0.0 }
    }

    override suspend fun localPositionForContent(contentId: String): Double? {
        val snapshot = snapshotProvider() ?: return null
        val profileId = snapshot.profileId ?: return null
        return userStateDao.getByContent(snapshot.serverId, profileId, contentId)
            .mapNotNull { it.positionSeconds.takeIf { p -> p > 0.0 } }
            .maxOrNull()
    }

    override suspend fun resolve(handle: OutboxHandle, outcome: WriteOutcome) {
        if (handle.opId < 0) return
        when (outcome) {
            // Acked, or rejected for good — either way the op must not be replayed.
            WriteOutcome.SYNCED, WriteOutcome.TERMINAL -> outboxDao.deleteById(handle.opId)
            // Transient: leave the pending op and ask the sync engine to drain it.
            // Triggered here (not in record()) so it can't race the inline call.
            WriteOutcome.RETRIABLE -> syncScheduler.requestSync()
        }
    }

    private suspend fun record(
        contentId: String,
        opKind: String,
        payloadJson: String,
        applyField: (ContentItemStateEntity) -> ContentItemStateEntity,
    ): OutboxHandle {
        val snapshot = snapshotProvider() ?: return OutboxHandle.NONE
        val serverId = snapshot.serverId
        val profileId = snapshot.profileId ?: return OutboxHandle.NONE
        val nowMs = now()

        var opId = OutboxHandle.NONE.opId
        db.withTransaction {
            // Read-modify-write so a single-field mutation (e.g. favorite) does
            // not clobber another field (e.g. an existing rating) on the row.
            val existing = contentDao.get(serverId, profileId, contentId)
                ?: ContentItemStateEntity(
                    serverId = serverId,
                    profileId = profileId,
                    contentId = contentId,
                    watched = null,
                    ratingValue = null,
                    favorite = null,
                    clientUpdatedAtMs = nowMs,
                    serverUpdatedAtMs = null,
                )
            contentDao.upsert(applyField(existing).copy(clientUpdatedAtMs = nowMs))

            opId = outboxDao.enqueueCoalescing(
                DirtyOperationEntity(
                    opKind = opKind,
                    serverId = serverId,
                    profileId = profileId,
                    targetContentId = contentId,
                    targetFileId = null,
                    coalesceKey = "$serverId|$profileId|$contentId|$opKind",
                    idempotencyKey = idGenerator(),
                    payloadJson = payloadJson,
                    createdAtMs = nowMs,
                    nextAttemptAtMs = nowMs,
                ),
            )
        }
        return OutboxHandle(opId, snapshot)
    }
}
