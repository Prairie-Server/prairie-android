package com.continuum.app.common.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.continuum.app.common.data.db.entity.CatalogCacheEntity

@Dao
interface CatalogCacheDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(row: CatalogCacheEntity)

    @Query("SELECT * FROM catalog_cache WHERE serverId = :serverId AND profileId = :profileId AND cacheKey = :cacheKey")
    suspend fun get(serverId: String, profileId: String, cacheKey: String): CatalogCacheEntity?
}
