package com.continuum.app.common.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.continuum.app.common.data.db.entity.HomeCacheEntity

@Dao
interface HomeCacheDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(row: HomeCacheEntity)

    @Query("SELECT * FROM home_cache WHERE serverId = :serverId AND profileId = :profileId")
    suspend fun get(serverId: String, profileId: String): HomeCacheEntity?

    @Query("DELETE FROM home_cache WHERE serverId = :serverId AND profileId = :profileId")
    suspend fun delete(serverId: String, profileId: String)
}
