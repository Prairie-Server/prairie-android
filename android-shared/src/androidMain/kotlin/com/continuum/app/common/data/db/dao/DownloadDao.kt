package com.continuum.app.common.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.continuum.app.common.data.db.entity.DownloadEntity

/**
 * Downloads projection access. Scoped reads seed the Downloads tab and the
 * offline player without a server round-trip; status scans drive
 * in-progress/queued sections.
 */
@Dao
interface DownloadDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(row: DownloadEntity)

    @Query(
        "SELECT * FROM downloads " +
            "WHERE serverId = :serverId AND profileId = :profileId AND mediaFileId = :mediaFileId",
    )
    suspend fun get(serverId: String, profileId: String, mediaFileId: Int): DownloadEntity?

    @Query("SELECT * FROM downloads WHERE recordId = :recordId")
    suspend fun getByRecordId(recordId: String): DownloadEntity?

    @Query(
        "SELECT * FROM downloads " +
            "WHERE serverId = :serverId AND profileId = :profileId AND contentId = :contentId",
    )
    suspend fun getByContent(serverId: String, profileId: String, contentId: String): List<DownloadEntity>

    @Query(
        "SELECT * FROM downloads WHERE serverId = :serverId AND profileId = :profileId ORDER BY updatedAtMs DESC",
    )
    suspend fun getAll(serverId: String, profileId: String): List<DownloadEntity>

    @Query(
        "SELECT * FROM downloads " +
            "WHERE serverId = :serverId AND profileId = :profileId AND status = :status " +
            "ORDER BY updatedAtMs DESC",
    )
    suspend fun getByStatus(serverId: String, profileId: String, status: String): List<DownloadEntity>

    @Query(
        "DELETE FROM downloads " +
            "WHERE serverId = :serverId AND profileId = :profileId AND mediaFileId = :mediaFileId",
    )
    suspend fun delete(serverId: String, profileId: String, mediaFileId: Int)

    @Query("DELETE FROM downloads WHERE serverId = :serverId AND profileId = :profileId")
    suspend fun deleteAll(serverId: String, profileId: String)
}
