package com.continuum.app.common.data.repository

import androidx.room.withTransaction
import com.continuum.app.common.data.db.SiloDatabase
import com.continuum.app.common.data.db.entity.ContentItemStateEntity
import com.continuum.app.common.data.db.entity.DirtyOperationEntity
import com.continuum.app.common.data.sync.OutboxOperation
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
 * Scope `(serverId, profileId)` is resolved per call via the injected
 * providers (wired to `TokenManager` in the platform DI module). If either is
 * absent — no active server/profile — the mutation records nothing and returns
 * [OutboxHandle.NONE]; the network call still proceeds in the repository.
 *
 * Coalesce keys are server-scoped (`serverId|profileId|contentId|kind`) so a
 * newer pending op of the same kind+target replaces an older un-synced one
 * without crossing server/profile boundaries.
 */
class RoomUserItemStateRepository(
    private val db: SiloDatabase,
    private val currentServerId: suspend () -> String?,
    private val currentProfileId: suspend () -> String?,
    private val now: () -> Long = { System.currentTimeMillis() },
    private val idGenerator: () -> String = { UUID.randomUUID().toString() },
) : UserItemStatePort {

    private val contentDao = db.contentItemStateDao()
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

    override suspend fun resolve(handle: OutboxHandle, outcome: WriteOutcome) {
        if (handle.opId < 0) return
        when (outcome) {
            // Acked, or rejected for good — either way the op must not be replayed.
            WriteOutcome.SYNCED, WriteOutcome.TERMINAL -> outboxDao.deleteById(handle.opId)
            // Transient: leave the pending op for the sync engine to retry.
            WriteOutcome.RETRIABLE -> Unit
        }
    }

    private suspend fun record(
        contentId: String,
        opKind: String,
        payloadJson: String,
        applyField: (ContentItemStateEntity) -> ContentItemStateEntity,
    ): OutboxHandle {
        val serverId = currentServerId() ?: return OutboxHandle.NONE
        val profileId = currentProfileId() ?: return OutboxHandle.NONE
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
        return OutboxHandle(opId)
    }
}
