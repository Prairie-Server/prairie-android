package com.continuum.app.common.data.sync

import android.util.Log
import com.continuum.app.common.data.db.SiloDatabase
import com.continuum.app.common.data.db.entity.DirtyOperationEntity
import com.continuum.app.network.AuthScopeSnapshot
import com.continuum.app.network.api.PersonalDataApi
import com.continuum.app.repository.port.WriteOutcome
import com.continuum.app.repository.port.toWriteOutcome

/**
 * Drains the `dirty_operations` outbox to the server (Track B). Replays each
 * pending op through the **raw [PersonalDataApi]** — never [PersonalDataRepository],
 * which would re-enter the local-first port and re-enqueue the op forever.
 *
 * Every send is **pinned** to the scope captured at drain start via
 * [AuthScopeSnapshot]: the auth plugin binds the request to that server URL +
 * profile and the live per-server access token, so a server/profile switch
 * mid-drain can't send an op to the wrong account, and continuing to drain the
 * captured scope after a switch is correct. That makes the older scope-recheck /
 * generation-tracking dance unnecessary.
 *
 * Correctness still rests on:
 * - **Atomic claim** ([DirtyOperationDao.claim]) — a row is sent at most once.
 * - **Reclaim** at drain start — in-flight rows stranded by a crash are dropped
 *   if a newer pending op supersedes them, else returned to pending.
 * - **Atomic supersede-or-record** on transient failure.
 *
 * Transient failures (no network / 401 / 408 / 429 / 5xx) are kept indefinitely
 * with capped backoff — offline data is never dropped on a retry cap. Only
 * terminal 4xx, unknown op kinds, and superseded rows are dropped.
 */
class SyncEngine(
    db: SiloDatabase,
    private val personalDataApi: PersonalDataApi,
    private val snapshotProvider: suspend () -> AuthScopeSnapshot?,
    private val now: () -> Long = { System.currentTimeMillis() },
    private val batchLimit: Int = 50,
) {
    private val dao = db.dirtyOperationDao()

    data class DrainResult(
        val synced: Int = 0,
        val dropped: Int = 0,
        val retriable: Int = 0,
        val remaining: Int = 0,
    ) {
        /**
         * True while any op for the drained scope is still queued (failed-and-
         * backing-off or not-yet-processed). The worker reschedules on this so a
         * clean partial batch or a backoff row can never strand the outbox.
         */
        val hasPendingWork: Boolean get() = remaining > 0
    }

    /**
     * Drain all currently-due ops for the active scope, pinning every send to the
     * captured snapshot. Loops over batches so a backlog larger than [batchLimit]
     * fully drains in one run.
     */
    suspend fun drainOnce(): DrainResult {
        val scope = snapshotProvider() ?: return DrainResult()
        val serverId = scope.serverId
        // Ops are always enqueued with a profile; no profile → nothing to drain.
        val profileId = scope.profileId ?: return DrainResult()

        // Reclaim crash-stranded in-flight rows before claiming new work.
        dao.deleteSupersededInFlight(serverId, profileId)
        dao.resetInFlightToPending(serverId, profileId)

        var synced = 0
        var dropped = 0
        var retriable = 0

        var batches = 0
        while (batches++ < MAX_BATCHES) {
            val batch = dao.dueBatch(serverId, profileId, now(), batchLimit)
            if (batch.isEmpty()) break

            for (op in batch) {
                if (dao.claim(op.id) != 1) continue // lost the claim; skip

                when (dispatch(op, scope)) {
                    WriteOutcome.SYNCED -> {
                        dao.deleteById(op.id)
                        synced++
                    }
                    WriteOutcome.TERMINAL -> {
                        dao.deleteById(op.id)
                        dropped++
                    }
                    WriteOutcome.RETRIABLE -> {
                        val superseded = dao.supersedeOrRecordFailure(
                            id = op.id,
                            coalesceKey = op.coalesceKey,
                            nowMs = now(),
                            nextAttemptAtMs = now() + backoffMs(op.attemptCount),
                            error = WriteOutcome.RETRIABLE.name,
                        )
                        if (superseded) dropped++ else retriable++
                    }
                }
            }
        }

        // Count remaining for whatever scope is active NOW (re-snapshot), not the
        // scope we just drained. If the user switched mid-drain, this keeps the
        // worker's retry chain alive for the newly-active scope — covering the
        // case where an activation enqueue was dropped by ExistingWorkPolicy.KEEP
        // while this worker was running.
        val endScope = snapshotProvider()
        val endProfileId = endScope?.profileId
        val remaining = if (endScope != null && endProfileId != null) {
            dao.countForScope(endScope.serverId, endProfileId)
        } else {
            0
        }

        return DrainResult(
            synced = synced,
            dropped = dropped,
            retriable = retriable,
            remaining = remaining,
        )
    }

    private suspend fun dispatch(op: DirtyOperationEntity, scope: AuthScopeSnapshot): WriteOutcome {
        val contentId = op.targetContentId
        val result = when (op.opKind) {
            OutboxOperation.SET_WATCHED -> {
                val watched = OutboxOperation.decodeBooleanPayload(op.payloadJson)
                if (watched) personalDataApi.markWatched(contentId, scope) else personalDataApi.markUnwatched(contentId, scope)
            }

            OutboxOperation.SET_FAVORITE -> {
                val favorite = OutboxOperation.decodeBooleanPayload(op.payloadJson)
                if (favorite) personalDataApi.addFavorite(contentId, scope) else personalDataApi.removeFavorite(contentId, scope)
            }

            OutboxOperation.SET_RATING -> {
                val rating = OutboxOperation.decodeRatingPayload(op.payloadJson)
                if (rating == null) personalDataApi.deleteRating(contentId, scope) else personalDataApi.setRating(contentId, rating, scope)
            }

            else -> {
                // This engine version cannot send this kind (e.g. SET_POSITION
                // replay is a later slice). Drop it rather than retry forever.
                Log.w(TAG, "Dropping un-replayable outbox op kind=${op.opKind} id=${op.id}")
                return WriteOutcome.TERMINAL
            }
        }
        return result.toWriteOutcome()
    }

    /** Capped exponential backoff: 30s · 2^attempt, ceiling 6h. */
    private fun backoffMs(attemptCount: Int): Long {
        val shift = attemptCount.coerceIn(0, 20)
        val delay = BASE_BACKOFF_MS shl shift
        return if (delay <= 0L || delay > MAX_BACKOFF_MS) MAX_BACKOFF_MS else delay
    }

    companion object {
        private const val TAG = "SyncEngine"
        private const val BASE_BACKOFF_MS = 30_000L
        private const val MAX_BACKOFF_MS = 6L * 60 * 60 * 1000

        // Backstop against a pathological re-due loop; a normal drain terminates
        // long before this because each op is deleted or pushed into the future.
        private const val MAX_BATCHES = 1_000
    }
}
