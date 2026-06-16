package com.continuum.app.common.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.continuum.app.common.data.db.entity.DirtyOperationEntity

/**
 * Outbox access: coalescing enqueue, due-batch drain selection, attempt
 * bookkeeping, and delete-on-ack. The drain loop claims a batch
 * ([markInFlight]), sends each op, then either [deleteById] on success or
 * [recordFailure] to schedule a backoff retry.
 */
@Dao
interface DirtyOperationDao {

    @Insert
    suspend fun insert(op: DirtyOperationEntity): Long

    /**
     * Enqueue, coalescing against any pending op with the same [DirtyOperationEntity.coalesceKey]:
     * the older un-synced op is dropped so only the latest intent is sent.
     * In-flight rows are left alone (a send is already underway for them).
     */
    @Transaction
    suspend fun enqueueCoalescing(op: DirtyOperationEntity): Long {
        deletePendingByCoalesceKey(op.coalesceKey)
        return insert(op)
    }

    @Query(
        "DELETE FROM dirty_operations WHERE coalesceKey = :coalesceKey AND state = '${DirtyOperationEntity.STATE_PENDING}'",
    )
    suspend fun deletePendingByCoalesceKey(coalesceKey: String)

    /** Oldest-due-first batch of sendable ops (FIFO via the nextAttemptAtMs,id index). */
    @Query(
        "SELECT * FROM dirty_operations " +
            "WHERE state = '${DirtyOperationEntity.STATE_PENDING}' AND nextAttemptAtMs <= :nowMs " +
            "ORDER BY nextAttemptAtMs ASC, id ASC LIMIT :limit",
    )
    suspend fun dueBatch(nowMs: Long, limit: Int): List<DirtyOperationEntity>

    @Query("UPDATE dirty_operations SET state = '${DirtyOperationEntity.STATE_IN_FLIGHT}' WHERE id = :id")
    suspend fun markInFlight(id: Long)

    @Query(
        "UPDATE dirty_operations SET " +
            "state = '${DirtyOperationEntity.STATE_PENDING}', " +
            "attemptCount = attemptCount + 1, " +
            "lastAttemptAtMs = :nowMs, nextAttemptAtMs = :nextAttemptAtMs, lastError = :error " +
            "WHERE id = :id",
    )
    suspend fun recordFailure(id: Long, nowMs: Long, nextAttemptAtMs: Long, error: String?)

    @Query("DELETE FROM dirty_operations WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT COUNT(*) FROM dirty_operations")
    suspend fun count(): Int
}
