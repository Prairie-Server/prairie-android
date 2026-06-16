package com.continuum.app.common.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.continuum.app.common.data.db.entity.ContentItemStateEntity

/**
 * Content-level user-state projection access. Partial-field merges
 * (a favorite toggle must not wipe an existing rating) are done as a
 * read-modify-write in the caller's transaction via [get] + [upsert].
 */
@Dao
interface ContentItemStateDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(row: ContentItemStateEntity)

    @Query(
        "SELECT * FROM content_item_state " +
            "WHERE serverId = :serverId AND profileId = :profileId AND contentId = :contentId",
    )
    suspend fun get(serverId: String, profileId: String, contentId: String): ContentItemStateEntity?

    @Query(
        "SELECT * FROM content_item_state " +
            "WHERE serverId = :serverId AND profileId = :profileId AND favorite = 1",
    )
    suspend fun favorites(serverId: String, profileId: String): List<ContentItemStateEntity>

    @Query("DELETE FROM content_item_state WHERE serverId = :serverId AND profileId = :profileId AND contentId = :contentId")
    suspend fun delete(serverId: String, profileId: String, contentId: String)
}
