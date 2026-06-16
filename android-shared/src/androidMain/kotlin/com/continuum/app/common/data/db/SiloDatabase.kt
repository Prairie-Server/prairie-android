package com.continuum.app.common.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.continuum.app.common.data.db.dao.DirtyOperationDao
import com.continuum.app.common.data.db.dao.DownloadDao
import com.continuum.app.common.data.db.dao.LegacyImportDao
import com.continuum.app.common.data.db.dao.UserItemStateDao
import com.continuum.app.common.data.db.entity.DirtyOperationEntity
import com.continuum.app.common.data.db.entity.DownloadEntity
import com.continuum.app.common.data.db.entity.LegacyImportEntity
import com.continuum.app.common.data.db.entity.UserItemStateEntity

/**
 * The offline-first store for Silo (Track B). Source of truth for the
 * home/library browse + resume + downloads + user-state paths.
 *
 * Schema v1 ships with **no auto migrations** (per Codex review): the exported
 * schema under `android-shared/schemas` is the baseline. From v2 onward, prefer
 * Room auto migrations for additive/rename changes and manual migrations for
 * any data transform, validated with `MigrationTestHelper`.
 *
 * No `@TypeConverters` are declared — every entity field is a primitive,
 * String, or JSON-encoded String (e.g. [DownloadEntity.chaptersJson]).
 */
@Database(
    entities = [
        UserItemStateEntity::class,
        DirtyOperationEntity::class,
        DownloadEntity::class,
        LegacyImportEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class SiloDatabase : RoomDatabase() {
    abstract fun userItemStateDao(): UserItemStateDao
    abstract fun dirtyOperationDao(): DirtyOperationDao
    abstract fun downloadDao(): DownloadDao
    abstract fun legacyImportDao(): LegacyImportDao

    companion object {
        const val NAME = "silo.db"
    }
}
