package com.continuum.app.common.data.db

import android.content.Context
import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.continuum.app.common.data.db.dao.ContentItemStateDao
import com.continuum.app.common.data.db.dao.DirtyOperationDao
import com.continuum.app.common.data.db.dao.DownloadDao
import com.continuum.app.common.data.db.dao.HomeCacheDao
import com.continuum.app.common.data.db.dao.LegacyImportDao
import com.continuum.app.common.data.db.dao.UserItemStateDao
import com.continuum.app.common.data.db.entity.ContentItemStateEntity
import com.continuum.app.common.data.db.entity.DirtyOperationEntity
import com.continuum.app.common.data.db.entity.DownloadEntity
import com.continuum.app.common.data.db.entity.HomeCacheEntity
import com.continuum.app.common.data.db.entity.LegacyImportEntity
import com.continuum.app.common.data.db.entity.UserItemStateEntity

/**
 * The offline-first store for Silo (Track B). Source of truth for the
 * home/library browse + resume + downloads + user-state paths.
 *
 * v2 adds [HomeCacheEntity] via a Room **auto migration** (additive table, so it
 * preserves the existing outbox/projection data on upgrade). From here, prefer
 * auto migrations for additive/rename changes and manual migrations for any data
 * transform, validated with `MigrationTestHelper`.
 *
 * No `@TypeConverters` are declared — every entity field is a primitive,
 * String, or JSON-encoded String (e.g. [DownloadEntity.chaptersJson]).
 */
@Database(
    entities = [
        UserItemStateEntity::class,
        ContentItemStateEntity::class,
        DirtyOperationEntity::class,
        DownloadEntity::class,
        LegacyImportEntity::class,
        HomeCacheEntity::class,
    ],
    version = 2,
    exportSchema = true,
    autoMigrations = [AutoMigration(from = 1, to = 2)],
)
abstract class SiloDatabase : RoomDatabase() {
    abstract fun userItemStateDao(): UserItemStateDao
    abstract fun contentItemStateDao(): ContentItemStateDao
    abstract fun dirtyOperationDao(): DirtyOperationDao
    abstract fun downloadDao(): DownloadDao
    abstract fun legacyImportDao(): LegacyImportDao
    abstract fun homeCacheDao(): HomeCacheDao

    companion object {
        const val NAME = "silo.db"

        /**
         * Builds the on-disk database. Room is an `android-shared` implementation
         * detail, so the app DI modules construct the DB through this factory
         * rather than referencing `androidx.room` (which is not on their compile
         * classpath).
         */
        fun build(context: Context): SiloDatabase =
            Room.databaseBuilder(context.applicationContext, SiloDatabase::class.java, NAME).build()
    }
}
