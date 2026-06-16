package com.continuum.app.common.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.continuum.app.common.data.db.entity.LegacyImportEntity

/**
 * Tracks which legacy sources have been imported into Room so the one-time
 * migration (Task 6) can re-run idempotently — skipping unchanged sources
 * (same hash + mtime) and re-importing changed ones.
 */
@Dao
interface LegacyImportDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(row: LegacyImportEntity)

    @Query("SELECT * FROM legacy_imports WHERE sourceKind = :sourceKind AND sourcePath = :sourcePath")
    suspend fun get(sourceKind: String, sourcePath: String): LegacyImportEntity?

    @Query("SELECT * FROM legacy_imports WHERE sourceKind = :sourceKind")
    suspend fun getByKind(sourceKind: String): List<LegacyImportEntity>

    @Query("SELECT COUNT(*) FROM legacy_imports")
    suspend fun count(): Int
}
