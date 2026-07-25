# Open Issue Closure Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Close the remaining uncorrected Android issues: browse-only Collections (#5/#6), monitored downloads/retention/Reclaim Watched (#22), PrairieControl (#15/#16/#17), and Android push registration/receiver (#19), while keeping Requests/Admin/Watch Together hidden.

**Architecture:** Implement four isolated slices. Collections is a UI removal with source tests. Downloads adds Room-backed subscription/reclaim foundations and mobile UI. PrairieControl ports Apple PrairieCast through shared wire models, Android TV receiver plumbing, and Android phone browser/controller UI. Push is guarded behind a client registration contract so builds stay valid when Firebase configuration is absent.

**Tech Stack:** Kotlin Multiplatform Android targets, Jetpack Compose, Compose for TV, Room, WorkManager, Koin, kotlinx.serialization, Android NSD, Media3/mpv player adapters, Android notifications, optional Firebase Messaging integration.

## Global Constraints

- Android 7 remains supported.
- Ebooks remain mobile-only and must not surface on Android TV.
- Requests, Admin, and Watch Together stay hidden from Android phone and Android TV user menus.
- Downloads remain public/discoverable and use original filenames/formats for completed media bytes.
- New feature work must be test-first: add failing tests, verify the failure, implement, then verify green.
- Android TV surfaces must be D-pad operable with visible focus and no focus traps.
- Apple/tvOS is the master for PrairieControl protocol shape. Android must interoperate with Apple rather than inventing a parallel protocol.

---

## File Structure

Collections:
- Modify `androidApp/src/androidMain/kotlin/org/prairieserver/prairie/android/ui/screens/collections/CollectionsScreen.kt` to remove create/delete/move entry points.
- Modify `androidApp/src/androidMain/kotlin/org/prairieserver/prairie/android/ui/screens/collections/CollectionDetailScreen.kt` to remove delete/edit affordances.
- Modify `androidTvApp/src/androidMain/kotlin/org/prairieserver/prairie/tv/ui/screens/collections/TvCollectionsScreen.kt` to remove create/delete/move entry points.
- Modify `androidTvApp/src/androidMain/kotlin/org/prairieserver/prairie/tv/ui/screens/collections/TvCollectionDetailScreen.kt` to remove delete/edit affordances.
- Add source tests under `androidApp/src/androidUnitTest/.../collections` and `androidTvApp/src/androidUnitTest/.../collections`.

Downloads:
- Create shared domain models in `shared/src/commonMain/kotlin/org/prairieserver/prairie/model/download/DownloadSubscriptionModels.kt`.
- Add Room entity/DAO/repository in `android-shared/src/androidMain/kotlin/org/prairieserver/prairie/common/data/db/entity/DownloadSubscriptionEntity.kt`, `.../dao/DownloadSubscriptionDao.kt`, and `.../repository/RoomDownloadSubscriptionRepository.kt`.
- Update `android-shared/src/androidMain/kotlin/org/prairieserver/prairie/common/data/db/PrairieDatabase.kt` from version 6 to 7 with an additive auto migration and committed schema.
- Create evaluator/reclaim code in `android-shared/src/androidMain/kotlin/org/prairieserver/prairie/common/downloads/DownloadSubscriptionEvaluator.kt`, `DownloadSubscriptionWorker.kt`, and `DownloadReclaimPlanner.kt`.
- Update mobile downloads UI in `androidApp/src/androidMain/kotlin/org/prairieserver/prairie/android/ui/screens/downloads`.
- Add tests under `android-shared/src/androidUnitTest/kotlin/org/prairieserver/prairie/common/downloads` and `androidApp/src/androidUnitTest/kotlin/org/prairieserver/prairie/android/ui/screens/downloads`.

PrairieControl:
- Create shared PrairieCast wire models in `shared/src/commonMain/kotlin/org/prairieserver/prairie/cast/PrairieCastProtocol.kt`, `PrairieCastMessage.kt`, and `PrairieCastPlaybackClock.kt`.
- Create Android transport pieces in `android-shared/src/androidMain/kotlin/org/prairieserver/prairie/common/cast`: `PrairieCastFrame.kt`, `PrairieCastTransport.kt`, `PrairieCastNsdAdvertiser.kt`, `PrairieCastNsdBrowser.kt`.
- Add TV receiver/adapters in `androidTvApp/src/androidMain/kotlin/org/prairieserver/prairie/tv/cast` and wire from TV app lifecycle/player.
- Add phone controller/UI in `androidApp/src/androidMain/kotlin/org/prairieserver/prairie/android/cast` and `androidApp/src/androidMain/kotlin/org/prairieserver/prairie/android/ui/screens/cast`.
- Add source/behavior tests under `shared/src/commonTest/kotlin/org/prairieserver/prairie/cast`, `android-shared/src/androidUnitTest/.../cast`, `androidApp/src/androidUnitTest/.../cast`, and `androidTvApp/src/androidUnitTest/.../cast`.

Push:
- Create shared push registration models in `shared/src/commonMain/kotlin/org/prairieserver/prairie/model/notifications/PushRegistrationModels.kt`.
- Add API/repository in `shared/src/commonMain/kotlin/org/prairieserver/prairie/network/api/PushRegistrationApi.kt` and `shared/src/commonMain/kotlin/org/prairieserver/prairie/repository/PushRegistrationRepository.kt`.
- Add Android app push service/provider in `androidApp/src/androidMain/kotlin/org/prairieserver/prairie/android/push`.
- Add guarded Gradle dependency/config in `androidApp/build.gradle.kts` only if Firebase config is available or a compile-safe no-op provider is used.
- Add tests in `shared/src/commonTest/.../notifications` and `androidApp/src/androidUnitTest/.../push`.

---

### Task 1: Make Collections Browse-Only On Phone And TV

**Files:**
- Modify: `androidApp/src/androidMain/kotlin/org/prairieserver/prairie/android/ui/screens/collections/CollectionsScreen.kt`
- Modify: `androidApp/src/androidMain/kotlin/org/prairieserver/prairie/android/ui/screens/collections/CollectionDetailScreen.kt`
- Modify: `androidTvApp/src/androidMain/kotlin/org/prairieserver/prairie/tv/ui/screens/collections/TvCollectionsScreen.kt`
- Modify: `androidTvApp/src/androidMain/kotlin/org/prairieserver/prairie/tv/ui/screens/collections/TvCollectionDetailScreen.kt`
- Test: `androidApp/src/androidUnitTest/kotlin/org/prairieserver/prairie/android/ui/screens/collections/CollectionsBrowseOnlySourceTest.kt`
- Test: `androidTvApp/src/androidUnitTest/kotlin/org/prairieserver/prairie/tv/ui/screens/collections/TvCollectionsBrowseOnlySourceTest.kt`

**Interfaces:**
- Consumes: existing `CollectionsViewModel` list/read state and collection detail view models.
- Produces: phone/TV production screens that browse and open collections without authoring entry points.

- [ ] **Step 1: Write failing phone source test**

```kotlin
package org.prairieserver.prairie.android.ui.screens.collections

import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CollectionsBrowseOnlySourceTest {
    private fun source(path: String): String = File(path).readText()

    @Test
    fun collectionsScreenDoesNotExposeAuthoringControls() {
        val source = source("src/androidMain/kotlin/org/prairieserver/prairie/android/ui/screens/collections/CollectionsScreen.kt")
        listOf(
            "CreateCollectionSheet",
            "showCreateSheet",
            "createCollection",
            "deleteCollection",
            "moveCollection",
        ).forEach { forbidden ->
            assertFalse(source.contains(forbidden), "$forbidden must not be reachable from mobile Collections.")
        }
        assertTrue(source.contains("CollectionsScreen("), "Collections browse surface must remain.")
    }

    @Test
    fun collectionDetailDoesNotExposeDeleteOrEditActions() {
        val source = source("src/androidMain/kotlin/org/prairieserver/prairie/android/ui/screens/collections/CollectionDetailScreen.kt")
        listOf("deleteCollection", "updateCollection", "Edit Collection", "Delete Collection").forEach { forbidden ->
            assertFalse(source.contains(forbidden), "$forbidden must not be reachable from mobile Collection detail.")
        }
        assertTrue(source.contains("CollectionDetailScreen("), "Collection detail browse surface must remain.")
    }
}
```

- [ ] **Step 2: Write failing TV source test**

```kotlin
package org.prairieserver.prairie.tv.ui.screens.collections

import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TvCollectionsBrowseOnlySourceTest {
    private fun source(path: String): String = File(path).readText()

    @Test
    fun tvCollectionsScreenDoesNotExposeAuthoringControls() {
        val source = source("src/androidMain/kotlin/org/prairieserver/prairie/tv/ui/screens/collections/TvCollectionsScreen.kt")
        listOf(
            "TvCreateCollectionDialog",
            "showCreateSheet",
            "createCollection",
            "deleteCollection",
            "moveCollection",
            "Create Collection",
            "New Collection",
        ).forEach { forbidden ->
            assertFalse(source.contains(forbidden), "$forbidden must not be reachable from TV Collections.")
        }
        assertTrue(source.contains("TvCollectionsScreen("), "TV Collections browse surface must remain.")
    }

    @Test
    fun tvCollectionDetailDoesNotExposeDeleteOrEditActions() {
        val source = source("src/androidMain/kotlin/org/prairieserver/prairie/tv/ui/screens/collections/TvCollectionDetailScreen.kt")
        listOf("deleteCollection", "updateCollection", "Edit Collection", "Delete Collection").forEach { forbidden ->
            assertFalse(source.contains(forbidden), "$forbidden must not be reachable from TV Collection detail.")
        }
        assertTrue(source.contains("TvCollectionDetailScreen("), "TV Collection detail browse surface must remain.")
    }
}
```

- [ ] **Step 3: Run tests to verify RED**

Run:

```bash
./gradlew :androidApp:testDebugUnitTest --tests org.prairieserver.prairie.android.ui.screens.collections.CollectionsBrowseOnlySourceTest
./gradlew :androidTvApp:testDebugUnitTest --tests org.prairieserver.prairie.tv.ui.screens.collections.TvCollectionsBrowseOnlySourceTest
```

Expected: both fail because create/delete/move authoring strings are currently present.

- [ ] **Step 4: Remove authoring controls from phone**

Edit `CollectionsScreen.kt`:
- remove the top-bar/add `IconButton` that calls `viewModel::showCreateSheet`
- remove `CreateCollectionSheet(...)`
- remove row/menu actions that call `viewModel.deleteCollection` or `viewModel.moveCollection`
- change empty state copy/action from create-oriented to browse-only text

Edit `CollectionDetailScreen.kt`:
- remove delete/edit buttons and overflow menu actions
- keep member list, item click, playback/read navigation, loading/error/empty states

- [ ] **Step 5: Remove authoring controls from TV**

Edit `TvCollectionsScreen.kt`:
- remove `onCreateClick = viewModel::showCreateSheet`
- remove `TvCreateCollectionDialog(...)`
- remove delete/move controls and overflow options
- preserve D-pad focus through collection cards and group rows

Edit `TvCollectionDetailScreen.kt`:
- remove update/delete actions
- keep member browsing and item selection

- [ ] **Step 6: Run GREEN tests**

Run:

```bash
./gradlew :androidApp:testDebugUnitTest --tests org.prairieserver.prairie.android.ui.screens.collections.CollectionsBrowseOnlySourceTest
./gradlew :androidTvApp:testDebugUnitTest --tests org.prairieserver.prairie.tv.ui.screens.collections.TvCollectionsBrowseOnlySourceTest
```

Expected: PASS.

- [ ] **Step 7: Run existing navigation/surface tests**

Run:

```bash
./gradlew :androidApp:testDebugUnitTest --tests org.prairieserver.prairie.android.ui.navigation.ClientSurfaceVisibilitySourceTest
./gradlew :androidTvApp:testDebugUnitTest --tests org.prairieserver.prairie.tv.ui.navigation.TvClientSurfaceVisibilitySourceTest --tests org.prairieserver.prairie.tv.ui.TvUsabilityGuardTest
```

Expected: PASS; Requests/Admin/Watch Together remain hidden.

- [ ] **Step 8: Commit**

```bash
git add androidApp/src/androidMain/kotlin/org/prairieserver/prairie/android/ui/screens/collections androidTvApp/src/androidMain/kotlin/org/prairieserver/prairie/tv/ui/screens/collections androidApp/src/androidUnitTest/kotlin/org/prairieserver/prairie/android/ui/screens/collections androidTvApp/src/androidUnitTest/kotlin/org/prairieserver/prairie/tv/ui/screens/collections
git commit -m "fix: make Android collections browse-only"
```

---

### Task 2: Add Download Subscription Domain And Room Storage

**Files:**
- Create: `shared/src/commonMain/kotlin/org/prairieserver/prairie/model/download/DownloadSubscriptionModels.kt`
- Create: `android-shared/src/androidMain/kotlin/org/prairieserver/prairie/common/data/db/entity/DownloadSubscriptionEntity.kt`
- Create: `android-shared/src/androidMain/kotlin/org/prairieserver/prairie/common/data/db/dao/DownloadSubscriptionDao.kt`
- Create: `android-shared/src/androidMain/kotlin/org/prairieserver/prairie/common/data/repository/RoomDownloadSubscriptionRepository.kt`
- Modify: `android-shared/src/androidMain/kotlin/org/prairieserver/prairie/common/data/db/PrairieDatabase.kt`
- Test: `android-shared/src/androidUnitTest/kotlin/org/prairieserver/prairie/common/downloads/DownloadSubscriptionDaoTest.kt`

**Interfaces:**
- Produces: `DownloadSubscription`, `DownloadSubscriptionTargetType`, `DownloadSubscriptionMediaKind`, `DownloadSubscriptionRepository`.
- Later tasks consume: repository CRUD/query methods and active subscription flow/list.

- [ ] **Step 1: Write failing DAO test**

```kotlin
package org.prairieserver.prairie.common.downloads

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import org.prairieserver.prairie.common.data.db.PrairieDatabase
import org.prairieserver.prairie.common.data.db.entity.DownloadSubscriptionEntity
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DownloadSubscriptionDaoTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val db = Room.inMemoryDatabaseBuilder(context, PrairieDatabase::class.java).build()
    private val dao = db.downloadSubscriptionDao()

    @AfterTest
    fun closeDb() {
        db.close()
    }

    @Test
    fun upsertAndQueryActiveSubscriptionsByScope() = runTest {
        dao.upsert(subscription(id = "s1", targetId = "series-1", enabled = true))
        dao.upsert(subscription(id = "s2", targetId = "series-2", enabled = false))
        dao.upsert(subscription(id = "other", serverId = "server-b", targetId = "series-3", enabled = true))

        val active = dao.getActive("server-a", "profile-a")

        assertEquals(listOf("s1"), active.map { it.id })
        assertEquals("series-1", active.single().targetId)
    }

    @Test
    fun disableOnlyTouchesScopedRow() = runTest {
        dao.upsert(subscription(id = "s1", enabled = true))
        dao.disable("server-a", "profile-a", "s1", updatedAt = 20L)

        val row = dao.get("server-a", "profile-a", "s1")

        assertEquals(false, row?.enabled)
        assertEquals(20L, row?.updatedAt)
    }

    @Test
    fun deleteAllForServerRemovesOnlyThatServer() = runTest {
        dao.upsert(subscription(id = "s1", serverId = "server-a"))
        dao.upsert(subscription(id = "s2", serverId = "server-b"))
        dao.deleteAllForServer("server-a")

        assertNull(dao.get("server-a", "profile-a", "s1"))
        assertTrue(dao.getActive("server-b", "profile-a").isNotEmpty())
    }

    private fun subscription(
        id: String,
        serverId: String = "server-a",
        profileId: String = "profile-a",
        targetId: String = "series-1",
        enabled: Boolean = true,
    ) = DownloadSubscriptionEntity(
        id = id,
        serverId = serverId,
        profileId = profileId,
        targetType = "series",
        targetId = targetId,
        displayTitle = "Series",
        mediaKind = "video",
        quality = "original",
        wifiOnly = true,
        enabled = enabled,
        includeExisting = false,
        keepUnwatchedLimit = 3,
        deleteWatchedAfterDays = 7,
        createdAt = 1L,
        updatedAt = 1L,
        lastEvaluatedAt = null,
        lastError = null,
    )
}
```

- [ ] **Step 2: Run test to verify RED**

Run:

```bash
./gradlew :android-shared:testDebugUnitTest --tests org.prairieserver.prairie.common.downloads.DownloadSubscriptionDaoTest
```

Expected: FAIL because `DownloadSubscriptionEntity`, DAO, and `downloadSubscriptionDao()` do not exist.

- [ ] **Step 3: Add shared models**

Create `DownloadSubscriptionModels.kt`:

```kotlin
package org.prairieserver.prairie.model.download

import kotlinx.serialization.Serializable

@Serializable
enum class DownloadSubscriptionTargetType(val wire: String) {
    Series("series"),
    Season("season"),
    AudiobookSeries("audiobook_series"),
    Author("author"),
    Collection("collection");

    companion object {
        fun fromWire(value: String): DownloadSubscriptionTargetType =
            entries.firstOrNull { it.wire == value } ?: Series
    }
}

@Serializable
enum class DownloadSubscriptionMediaKind(val wire: String) {
    Video("video"),
    Audio("audio"),
    Reading("reading");

    companion object {
        fun fromWire(value: String): DownloadSubscriptionMediaKind =
            entries.firstOrNull { it.wire == value } ?: Video
    }
}

@Serializable
data class DownloadSubscription(
    val id: String,
    val serverId: String,
    val profileId: String,
    val targetType: DownloadSubscriptionTargetType,
    val targetId: String,
    val displayTitle: String,
    val mediaKind: DownloadSubscriptionMediaKind,
    val quality: DownloadQuality = DownloadQuality.Original,
    val wifiOnly: Boolean = true,
    val enabled: Boolean = true,
    val includeExisting: Boolean = false,
    val keepUnwatchedLimit: Int = 3,
    val deleteWatchedAfterDays: Int = 7,
    val createdAt: Long,
    val updatedAt: Long,
    val lastEvaluatedAt: Long? = null,
    val lastError: String? = null,
)
```

- [ ] **Step 4: Add Room entity and DAO**

Create `DownloadSubscriptionEntity.kt`:

```kotlin
package org.prairieserver.prairie.common.data.db.entity

import androidx.room.Entity
import androidx.room.Index

@Entity(
    tableName = "download_subscriptions",
    primaryKeys = ["serverId", "profileId", "id"],
    indices = [
        Index(value = ["serverId", "profileId", "enabled"]),
        Index(value = ["serverId", "profileId", "targetType", "targetId"], unique = true),
    ],
)
data class DownloadSubscriptionEntity(
    val id: String,
    val serverId: String,
    val profileId: String,
    val targetType: String,
    val targetId: String,
    val displayTitle: String,
    val mediaKind: String,
    val quality: String,
    val wifiOnly: Boolean,
    val enabled: Boolean,
    val includeExisting: Boolean,
    val keepUnwatchedLimit: Int,
    val deleteWatchedAfterDays: Int,
    val createdAt: Long,
    val updatedAt: Long,
    val lastEvaluatedAt: Long?,
    val lastError: String?,
)
```

Create `DownloadSubscriptionDao.kt`:

```kotlin
package org.prairieserver.prairie.common.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import org.prairieserver.prairie.common.data.db.entity.DownloadSubscriptionEntity

@Dao
interface DownloadSubscriptionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(row: DownloadSubscriptionEntity)

    @Query("SELECT * FROM download_subscriptions WHERE serverId = :serverId AND profileId = :profileId AND id = :id")
    suspend fun get(serverId: String, profileId: String, id: String): DownloadSubscriptionEntity?

    @Query("SELECT * FROM download_subscriptions WHERE serverId = :serverId AND profileId = :profileId ORDER BY updatedAt DESC")
    suspend fun getAll(serverId: String, profileId: String): List<DownloadSubscriptionEntity>

    @Query("SELECT * FROM download_subscriptions WHERE serverId = :serverId AND profileId = :profileId AND enabled = 1 ORDER BY updatedAt DESC")
    suspend fun getActive(serverId: String, profileId: String): List<DownloadSubscriptionEntity>

    @Query("UPDATE download_subscriptions SET enabled = 0, updatedAt = :updatedAt WHERE serverId = :serverId AND profileId = :profileId AND id = :id")
    suspend fun disable(serverId: String, profileId: String, id: String, updatedAt: Long)

    @Query("UPDATE download_subscriptions SET lastEvaluatedAt = :evaluatedAt, lastError = :error, updatedAt = :updatedAt WHERE serverId = :serverId AND profileId = :profileId AND id = :id")
    suspend fun updateEvaluation(serverId: String, profileId: String, id: String, evaluatedAt: Long, error: String?, updatedAt: Long)

    @Query("DELETE FROM download_subscriptions WHERE serverId = :serverId")
    suspend fun deleteAllForServer(serverId: String)
}
```

- [ ] **Step 5: Wire database version 7**

Modify `PrairieDatabase.kt`:
- import `DownloadSubscriptionDao`
- import `DownloadSubscriptionEntity`
- add `DownloadSubscriptionEntity::class` to `entities`
- bump `version = 7`
- add `AutoMigration(from = 6, to = 7)`
- add `abstract fun downloadSubscriptionDao(): DownloadSubscriptionDao`

Run:

```bash
./gradlew :android-shared:kspDebugKotlinAndroid
```

Expected: schema JSON for version 7 is generated under `android-shared/schemas`.

- [ ] **Step 6: Add repository mapping**

Create `RoomDownloadSubscriptionRepository.kt`:

```kotlin
package org.prairieserver.prairie.common.data.repository

import org.prairieserver.prairie.common.data.db.PrairieDatabase
import org.prairieserver.prairie.common.data.db.entity.DownloadSubscriptionEntity
import org.prairieserver.prairie.model.download.DownloadQuality
import org.prairieserver.prairie.model.download.DownloadSubscription
import org.prairieserver.prairie.model.download.DownloadSubscriptionMediaKind
import org.prairieserver.prairie.model.download.DownloadSubscriptionTargetType

class RoomDownloadSubscriptionRepository(private val db: PrairieDatabase) {
    private val dao = db.downloadSubscriptionDao()

    suspend fun upsert(subscription: DownloadSubscription) = dao.upsert(subscription.toEntity())
    suspend fun active(serverId: String, profileId: String): List<DownloadSubscription> =
        dao.getActive(serverId, profileId).map { it.toModel() }
    suspend fun all(serverId: String, profileId: String): List<DownloadSubscription> =
        dao.getAll(serverId, profileId).map { it.toModel() }
    suspend fun disable(serverId: String, profileId: String, id: String, updatedAt: Long) =
        dao.disable(serverId, profileId, id, updatedAt)
    suspend fun updateEvaluation(serverId: String, profileId: String, id: String, evaluatedAt: Long, error: String?, updatedAt: Long) =
        dao.updateEvaluation(serverId, profileId, id, evaluatedAt, error, updatedAt)

    private fun DownloadSubscription.toEntity() = DownloadSubscriptionEntity(
        id = id,
        serverId = serverId,
        profileId = profileId,
        targetType = targetType.wire,
        targetId = targetId,
        displayTitle = displayTitle,
        mediaKind = mediaKind.wire,
        quality = quality.wire,
        wifiOnly = wifiOnly,
        enabled = enabled,
        includeExisting = includeExisting,
        keepUnwatchedLimit = keepUnwatchedLimit,
        deleteWatchedAfterDays = deleteWatchedAfterDays,
        createdAt = createdAt,
        updatedAt = updatedAt,
        lastEvaluatedAt = lastEvaluatedAt,
        lastError = lastError,
    )

    private fun DownloadSubscriptionEntity.toModel() = DownloadSubscription(
        id = id,
        serverId = serverId,
        profileId = profileId,
        targetType = DownloadSubscriptionTargetType.fromWire(targetType),
        targetId = targetId,
        displayTitle = displayTitle,
        mediaKind = DownloadSubscriptionMediaKind.fromWire(mediaKind),
        quality = DownloadQuality.fromWire(quality),
        wifiOnly = wifiOnly,
        enabled = enabled,
        includeExisting = includeExisting,
        keepUnwatchedLimit = keepUnwatchedLimit,
        deleteWatchedAfterDays = deleteWatchedAfterDays,
        createdAt = createdAt,
        updatedAt = updatedAt,
        lastEvaluatedAt = lastEvaluatedAt,
        lastError = lastError,
    )
}
```

- [ ] **Step 7: Run GREEN tests**

Run:

```bash
./gradlew --rerun-tasks --no-build-cache :shared:testDebugUnitTest :android-shared:testDebugUnitTest --tests org.prairieserver.prairie.common.downloads.DownloadSubscriptionDaoTest
```

Expected: PASS.

- [ ] **Step 8: Commit**

```bash
git add shared/src/commonMain/kotlin/org/prairieserver/prairie/model/download/DownloadSubscriptionModels.kt android-shared/src/androidMain/kotlin/org/prairieserver/prairie/common/data/db android-shared/src/androidMain/kotlin/org/prairieserver/prairie/common/data/repository/RoomDownloadSubscriptionRepository.kt android-shared/src/androidUnitTest/kotlin/org/prairieserver/prairie/common/downloads/DownloadSubscriptionDaoTest.kt android-shared/schemas
git commit -m "feat: add download subscription storage"
```

---

### Task 3: Add Download Reclaim Planner And Subscription Evaluator Core

**Files:**
- Create: `android-shared/src/androidMain/kotlin/org/prairieserver/prairie/common/downloads/DownloadReclaimPlanner.kt`
- Create: `android-shared/src/androidMain/kotlin/org/prairieserver/prairie/common/downloads/DownloadSubscriptionEvaluator.kt`
- Test: `android-shared/src/androidUnitTest/kotlin/org/prairieserver/prairie/common/downloads/DownloadReclaimPlannerTest.kt`
- Test: `android-shared/src/androidUnitTest/kotlin/org/prairieserver/prairie/common/downloads/DownloadSubscriptionEvaluatorTest.kt`

**Interfaces:**
- Consumes: `DownloadSubscription`, local `DownloadEntity` rows, existing user-state port, and a candidate provider adapter.
- Produces: deterministic plans for enqueueing missing files and reclaiming completed local files.

- [ ] **Step 1: Write failing Reclaim planner test**

```kotlin
package org.prairieserver.prairie.common.downloads

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DownloadReclaimPlannerTest {
    @Test
    fun reclaimExcludesIncompleteFailedAndUnwatchedRows() {
        val plan = DownloadReclaimPlanner().plan(
            rows = listOf(
                row(id = "watched", status = "completed", bytes = 100, completed = true),
                row(id = "unwatched", status = "completed", bytes = 200, completed = false),
                row(id = "downloading", status = "downloading", bytes = 300, completed = true),
                row(id = "failed", status = "failed", bytes = 400, completed = true),
            ),
        )

        assertEquals(listOf("watched"), plan.items.map { it.recordId })
        assertEquals(100L, plan.totalBytes)
        assertEquals(1, plan.count)
    }

    @Test
    fun reclaimKeepsMostRecentUnwatchedLimit() {
        val plan = DownloadReclaimPlanner().plan(
            rows = listOf(
                row(id = "old-watched", status = "completed", bytes = 100, completed = true, updatedAt = 1),
                row(id = "new-watched", status = "completed", bytes = 200, completed = true, updatedAt = 10),
            ),
            keepNewestCompleted = 1,
        )

        assertEquals(listOf("old-watched"), plan.items.map { it.recordId })
    }

    private fun row(
        id: String,
        status: String,
        bytes: Long,
        completed: Boolean,
        updatedAt: Long = 1,
    ) = DownloadReclaimCandidate(
        recordId = id,
        contentId = "content-$id",
        mediaFileId = id.hashCode(),
        title = id,
        status = status,
        fileSizeBytes = bytes,
        completed = completed,
        updatedAtMs = updatedAt,
    )
}
```

- [ ] **Step 2: Write failing evaluator test**

```kotlin
package org.prairieserver.prairie.common.downloads

import kotlinx.coroutines.test.runTest
import org.prairieserver.prairie.model.download.DownloadQuality
import org.prairieserver.prairie.model.download.DownloadSubscription
import org.prairieserver.prairie.model.download.DownloadSubscriptionMediaKind
import org.prairieserver.prairie.model.download.DownloadSubscriptionTargetType
import kotlin.test.Test
import kotlin.test.assertEquals

class DownloadSubscriptionEvaluatorTest {
    @Test
    fun evaluatorEnqueuesMissingUnwatchedCandidatesOnly() = runTest {
        val provider = FakeProvider(
            candidates = listOf(
                DownloadSubscriptionCandidate("c1", 1, "Episode 1", completed = false),
                DownloadSubscriptionCandidate("c2", 2, "Episode 2", completed = true),
                DownloadSubscriptionCandidate("c3", 3, "Episode 3", completed = false),
            ),
        )
        val existing = setOf(3)
        val enqueued = mutableListOf<DownloadSubscriptionCandidate>()
        val evaluator = DownloadSubscriptionEvaluator(
            candidateProvider = provider,
            existingFileIds = { existing },
            enqueue = { candidate, quality ->
                enqueued += candidate
                assertEquals(DownloadQuality.TenMbps, quality)
            },
        )

        evaluator.evaluate(subscription(quality = DownloadQuality.TenMbps))

        assertEquals(listOf(1), enqueued.map { it.mediaFileId })
    }

    @Test
    fun evaluatorSkipsReadingCandidatesWhenTvSurfaceIsRequested() = runTest {
        val provider = FakeProvider(listOf(DownloadSubscriptionCandidate("ebook", 9, "Book", completed = false)))
        val enqueued = mutableListOf<DownloadSubscriptionCandidate>()
        val evaluator = DownloadSubscriptionEvaluator(
            candidateProvider = provider,
            existingFileIds = { emptySet() },
            enqueue = { candidate, _ -> enqueued += candidate },
            allowReading = false,
        )

        evaluator.evaluate(subscription(mediaKind = DownloadSubscriptionMediaKind.Reading))

        assertEquals(emptyList(), enqueued)
    }

    private fun subscription(
        quality: DownloadQuality = DownloadQuality.Original,
        mediaKind: DownloadSubscriptionMediaKind = DownloadSubscriptionMediaKind.Video,
    ) = DownloadSubscription(
        id = "sub",
        serverId = "server",
        profileId = "profile",
        targetType = DownloadSubscriptionTargetType.Series,
        targetId = "series",
        displayTitle = "Series",
        mediaKind = mediaKind,
        quality = quality,
        createdAt = 1,
        updatedAt = 1,
    )

    private class FakeProvider(private val candidates: List<DownloadSubscriptionCandidate>) : DownloadSubscriptionCandidateProvider {
        override suspend fun candidatesFor(subscription: DownloadSubscription): List<DownloadSubscriptionCandidate> = candidates
    }
}
```

- [ ] **Step 3: Run tests to verify RED**

Run:

```bash
./gradlew :android-shared:testDebugUnitTest --tests org.prairieserver.prairie.common.downloads.DownloadReclaimPlannerTest --tests org.prairieserver.prairie.common.downloads.DownloadSubscriptionEvaluatorTest
```

Expected: FAIL because planner/evaluator types do not exist.

- [ ] **Step 4: Implement reclaim planner**

Create `DownloadReclaimPlanner.kt`:

```kotlin
package org.prairieserver.prairie.common.downloads

data class DownloadReclaimCandidate(
    val recordId: String,
    val contentId: String,
    val mediaFileId: Int,
    val title: String,
    val status: String,
    val fileSizeBytes: Long,
    val completed: Boolean,
    val updatedAtMs: Long,
)

data class DownloadReclaimItem(
    val recordId: String,
    val mediaFileId: Int,
    val title: String,
    val fileSizeBytes: Long,
)

data class DownloadReclaimPlan(
    val items: List<DownloadReclaimItem>,
) {
    val totalBytes: Long = items.sumOf { it.fileSizeBytes }
    val count: Int = items.size
}

class DownloadReclaimPlanner {
    fun plan(rows: List<DownloadReclaimCandidate>, keepNewestCompleted: Int = 0): DownloadReclaimPlan {
        val reclaimable = rows
            .asSequence()
            .filter { it.status == "completed" }
            .filter { it.completed }
            .sortedByDescending { it.updatedAtMs }
            .drop(keepNewestCompleted.coerceAtLeast(0))
            .map {
                DownloadReclaimItem(
                    recordId = it.recordId,
                    mediaFileId = it.mediaFileId,
                    title = it.title,
                    fileSizeBytes = it.fileSizeBytes.coerceAtLeast(0),
                )
            }
            .toList()
        return DownloadReclaimPlan(reclaimable)
    }
}
```

- [ ] **Step 5: Implement evaluator core**

Create `DownloadSubscriptionEvaluator.kt`:

```kotlin
package org.prairieserver.prairie.common.downloads

import org.prairieserver.prairie.model.download.DownloadQuality
import org.prairieserver.prairie.model.download.DownloadSubscription
import org.prairieserver.prairie.model.download.DownloadSubscriptionMediaKind

data class DownloadSubscriptionCandidate(
    val contentId: String,
    val mediaFileId: Int,
    val title: String,
    val completed: Boolean,
)

interface DownloadSubscriptionCandidateProvider {
    suspend fun candidatesFor(subscription: DownloadSubscription): List<DownloadSubscriptionCandidate>
}

class DownloadSubscriptionEvaluator(
    private val candidateProvider: DownloadSubscriptionCandidateProvider,
    private val existingFileIds: suspend (DownloadSubscription) -> Set<Int>,
    private val enqueue: suspend (DownloadSubscriptionCandidate, DownloadQuality) -> Unit,
    private val allowReading: Boolean = true,
) {
    suspend fun evaluate(subscription: DownloadSubscription): Int {
        if (!subscription.enabled) return 0
        if (!allowReading && subscription.mediaKind == DownloadSubscriptionMediaKind.Reading) return 0

        val existing = existingFileIds(subscription)
        val candidates = candidateProvider.candidatesFor(subscription)
        var queued = 0
        for (candidate in candidates) {
            if (candidate.mediaFileId in existing) continue
            if (candidate.completed && !subscription.includeExisting) continue
            enqueue(candidate, subscription.quality)
            queued += 1
        }
        return queued
    }
}
```

- [ ] **Step 6: Run GREEN tests**

Run:

```bash
./gradlew --rerun-tasks --no-build-cache :android-shared:testDebugUnitTest --tests org.prairieserver.prairie.common.downloads.DownloadReclaimPlannerTest --tests org.prairieserver.prairie.common.downloads.DownloadSubscriptionEvaluatorTest
```

Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add android-shared/src/androidMain/kotlin/org/prairieserver/prairie/common/downloads/DownloadReclaimPlanner.kt android-shared/src/androidMain/kotlin/org/prairieserver/prairie/common/downloads/DownloadSubscriptionEvaluator.kt android-shared/src/androidUnitTest/kotlin/org/prairieserver/prairie/common/downloads/DownloadReclaimPlannerTest.kt android-shared/src/androidUnitTest/kotlin/org/prairieserver/prairie/common/downloads/DownloadSubscriptionEvaluatorTest.kt
git commit -m "feat: add download monitoring planner"
```

---

### Task 4: Wire Download Worker, Reclaim UI, And Subscription Controls

**Files:**
- Create: `android-shared/src/androidMain/kotlin/org/prairieserver/prairie/common/downloads/DownloadSubscriptionWorker.kt`
- Modify: `android-shared/src/androidMain/kotlin/org/prairieserver/prairie/common/downloads/DownloadEnqueuer.kt`
- Modify: `androidApp/src/androidMain/kotlin/org/prairieserver/prairie/android/ui/screens/downloads/DownloadsViewModel.kt`
- Modify: `androidApp/src/androidMain/kotlin/org/prairieserver/prairie/android/ui/screens/downloads/DownloadsScreen.kt`
- Modify: `androidApp/src/androidMain/kotlin/org/prairieserver/prairie/android/di/AndroidModule.kt`
- Test: `androidApp/src/androidUnitTest/kotlin/org/prairieserver/prairie/android/ui/screens/downloads/DownloadsMonitoringSourceTest.kt`

**Interfaces:**
- Consumes: Task 2 repository and Task 3 evaluator/planner.
- Produces: mobile-visible monitored downloads and Reclaim Watched actions; periodic worker scheduling.

- [ ] **Step 1: Write failing mobile source test**

```kotlin
package org.prairieserver.prairie.android.ui.screens.downloads

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

class DownloadsMonitoringSourceTest {
    @Test
    fun downloadsScreenExposesMonitoringAndReclaimActions() {
        val screen = File("src/androidMain/kotlin/org/prairieserver/prairie/android/ui/screens/downloads/DownloadsScreen.kt").readText()
        val vm = File("src/androidMain/kotlin/org/prairieserver/prairie/android/ui/screens/downloads/DownloadsViewModel.kt").readText()

        assertTrue(screen.contains("Reclaim Watched"), "Downloads screen must expose Reclaim Watched.")
        assertTrue(screen.contains("Monitored"), "Downloads screen must expose monitored downloads.")
        assertTrue(vm.contains("refreshSubscriptions"), "ViewModel must load monitored subscriptions.")
        assertTrue(vm.contains("reclaimWatched"), "ViewModel must execute reclaim watched.")
    }
}
```

- [ ] **Step 2: Run test to verify RED**

Run:

```bash
./gradlew :androidApp:testDebugUnitTest --tests org.prairieserver.prairie.android.ui.screens.downloads.DownloadsMonitoringSourceTest
```

Expected: FAIL because the UI/view model lacks monitored/reclaim affordances.

- [ ] **Step 3: Implement worker scheduling**

Create `DownloadSubscriptionWorker.kt` with:

```kotlin
class DownloadSubscriptionWorker(
    appContext: Context,
    params: WorkerParameters,
    private val repository: RoomDownloadSubscriptionRepository,
    private val evaluatorFactory: DownloadSubscriptionEvaluatorFactory,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val serverId = inputData.getString(KEY_SERVER_ID) ?: return Result.success()
        val profileId = inputData.getString(KEY_PROFILE_ID) ?: return Result.success()
        val now = System.currentTimeMillis()
        repository.active(serverId, profileId).forEach { subscription ->
            runCatching {
                evaluatorFactory.create().evaluate(subscription)
                repository.updateEvaluation(serverId, profileId, subscription.id, now, null, now)
            }.onFailure { error ->
                repository.updateEvaluation(serverId, profileId, subscription.id, now, error.message ?: error::class.simpleName, now)
            }
        }
        return Result.success()
    }

    companion object {
        const val KEY_SERVER_ID = "server_id"
        const val KEY_PROFILE_ID = "profile_id"
    }
}
```

Add a `DownloadSubscriptionEvaluatorFactory` in the same package that adapts current repositories/enqueuer. It should use existing detail/library repositories to enumerate candidates; if an endpoint is missing for a media kind, return an empty list and record `lastError` through worker exception handling.

- [ ] **Step 4: Add ViewModel state/actions**

Modify `DownloadsViewModel.kt`:
- add `subscriptions: List<DownloadSubscriptionUiItem>`
- add `reclaimPlan: DownloadReclaimPlan?`
- add `refreshSubscriptions()`
- add `runMonitoredDownloadsNow()`
- add `calculateReclaimWatched()`
- add `reclaimWatched()`

Map `DownloadItem` rows to `DownloadReclaimCandidate` using local progress/user-state overlays. Only pass completed rows to deletion after user confirmation.

- [ ] **Step 5: Add Downloads UI controls**

Modify `DownloadsScreen.kt`:
- add a compact "Monitored" section above downloads list
- add `Reclaim Watched` action in the header/overflow
- show count/bytes confirmation dialog before deleting
- show disabled/empty state when no reclaimable files exist

Use existing app visual patterns; do not add TV-only UI here.

- [ ] **Step 6: Run tests**

Run:

```bash
./gradlew :androidApp:testDebugUnitTest --tests org.prairieserver.prairie.android.ui.screens.downloads.DownloadsMonitoringSourceTest
./gradlew :android-shared:testDebugUnitTest --tests org.prairieserver.prairie.common.downloads.DownloadSubscriptionEvaluatorTest --tests org.prairieserver.prairie.common.downloads.DownloadReclaimPlannerTest
```

Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add android-shared/src/androidMain/kotlin/org/prairieserver/prairie/common/downloads androidApp/src/androidMain/kotlin/org/prairieserver/prairie/android/ui/screens/downloads androidApp/src/androidMain/kotlin/org/prairieserver/prairie/android/di/AndroidModule.kt androidApp/src/androidUnitTest/kotlin/org/prairieserver/prairie/android/ui/screens/downloads/DownloadsMonitoringSourceTest.kt
git commit -m "feat: expose monitored downloads and reclaim watched"
```

---

### Task 5: Add Apple-Compatible PrairieCast Wire Protocol

**Files:**
- Create: `shared/src/commonMain/kotlin/org/prairieserver/prairie/cast/PrairieCastProtocol.kt`
- Create: `shared/src/commonMain/kotlin/org/prairieserver/prairie/cast/PrairieCastMessage.kt`
- Create: `shared/src/commonMain/kotlin/org/prairieserver/prairie/cast/PrairieCastPlaybackClock.kt`
- Test: `shared/src/commonTest/kotlin/org/prairieserver/prairie/cast/PrairieCastMessageTest.kt`
- Test: `shared/src/commonTest/kotlin/org/prairieserver/prairie/cast/PrairieCastPlaybackClockTest.kt`

**Interfaces:**
- Produces: serialized PrairieCast message contract consumed by TV receiver and phone controller.

- [ ] **Step 1: Write failing message tests**

```kotlin
package org.prairieserver.prairie.cast

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PrairieCastMessageTest {
    private val json = Json { encodeDefaults = false; ignoreUnknownKeys = true }

    @Test
    fun controlCommandUsesAppleSnakeCaseNames() {
        val encoded = json.encodeToString(PrairieCastMessage.serializer(), PrairieCastMessage.Control(PrairieCastControlCommand.playPause()))
        assertTrue(encoded.contains("\"type\":\"control\""))
        assertTrue(encoded.contains("\"name\":\"play_pause\""))
        assertEquals(
            PrairieCastMessage.Control(PrairieCastControlCommand.playPause()),
            json.decodeFromString(PrairieCastMessage.serializer(), encoded),
        )
    }

    @Test
    fun subtitleOffRoundTripsWithNullTrackId() {
        val msg = PrairieCastMessage.Control(PrairieCastControlCommand.selectSubtitleTrack(null))
        val encoded = json.encodeToString(PrairieCastMessage.serializer(), msg)
        assertTrue(encoded.contains("\"name\":\"select_subtitle_track\""))
        assertEquals(msg, json.decodeFromString(PrairieCastMessage.serializer(), encoded))
    }

    @Test
    fun helloMatchesServiceVersion() {
        assertEquals(1, PrairieCastProtocol.version)
        assertEquals("_prairiecast._tcp", PrairieCastProtocol.serviceType)
    }
}
```

- [ ] **Step 2: Write failing clock tests**

```kotlin
package org.prairieserver.prairie.cast

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PrairieCastPlaybackClockTest {
    @Test
    fun displayTimeInterpolatesWhilePlayingAndClampsToDuration() {
        val clock = PrairieCastPlaybackClock()
        clock.ingest(state(isPlaying = true, currentTime = 10.0, duration = 20.0), nowMs = 1_000)

        assertEquals(13.0, clock.displayTime(nowMs = 4_000), 0.01)
        assertEquals(20.0, clock.displayTime(nowMs = 60_000), 0.01)
    }

    @Test
    fun optimisticSeekWinsUntilSnapshotCatchesUp() {
        val clock = PrairieCastPlaybackClock()
        clock.ingest(state(currentTime = 10.0, duration = 3_000.0), nowMs = 1_000)
        clock.setOptimisticTime(1_200.0, nowMs = 1_000)
        clock.ingest(state(currentTime = 10.0, duration = 3_000.0), nowMs = 1_500)

        assertEquals(1_200.0, clock.displayTime(nowMs = 1_500), 0.01)

        clock.ingest(state(currentTime = 1_200.0, duration = 3_000.0), nowMs = 2_000)
        assertEquals(1_200.0, clock.displayTime(nowMs = 2_000), 0.01)
    }

    @Test
    fun optimisticPlayingWinsBriefly() {
        val clock = PrairieCastPlaybackClock()
        clock.ingest(state(isPlaying = false), nowMs = 1_000)
        clock.setOptimisticPlaying(true, nowMs = 1_000)
        assertTrue(clock.isPlaying(nowMs = 1_500))
    }

    private fun state(
        isPlaying: Boolean = false,
        currentTime: Double = 0.0,
        duration: Double = 100.0,
    ) = PrairieCastPlaybackState(
        contentId = "content",
        sessionId = null,
        title = "Title",
        subtitle = null,
        isPlaying = isPlaying,
        isLoading = false,
        isBuffering = false,
        currentTime = currentTime,
        duration = duration,
        audioTracks = emptyList(),
        subtitleTracks = emptyList(),
        selectedAudioTrackId = null,
        selectedSubtitleTrackId = null,
        qualityOptions = emptyList(),
        activeQualityId = "auto",
        isQualitySwitching = false,
        playbackSpeed = 1.0,
        videoGravity = "fit",
        hdrEnabled = false,
        supportsVideoGravity = false,
        supportsHDRToggle = false,
        subtitleSyncMs = null,
        subtitlePosition = null,
        supportsSubtitleDelay = false,
        supportsSubtitlePosition = false,
        volume = 1.0,
        isMuted = false,
        hasNextEpisode = false,
        nextEpisodeTitle = null,
        error = null,
    )
}
```

- [ ] **Step 3: Run tests to verify RED**

Run:

```bash
./gradlew :shared:testDebugUnitTest --tests org.prairieserver.prairie.cast.PrairieCastMessageTest --tests org.prairieserver.prairie.cast.PrairieCastPlaybackClockTest
```

Expected: FAIL because PrairieCast models do not exist.

- [ ] **Step 4: Implement protocol and messages**

Create models using `@Serializable` and `@SerialName` exactly matching Apple:

```kotlin
package org.prairieserver.prairie.cast

object PrairieCastProtocol {
    const val version: Int = 1
    const val serviceType: String = "_prairiecast._tcp"
}
```

`PrairieCastMessage` must be a sealed `@Serializable` class with `@SerialName("hello")`, `@SerialName("launch")`, `@SerialName("control")`, `@SerialName("state")`, `@SerialName("error")`, `@SerialName("ping")`, `@SerialName("pong")`, and `@SerialName("close")`. If sealed-class JSON shape cannot match Apple directly, implement a custom serializer that emits:

```json
{"v":1,"type":"control","control":{"name":"play_pause"}}
```

not Kotlin's default polymorphic envelope.

- [ ] **Step 5: Implement playback clock**

Implement `PrairieCastPlaybackClock` with:
- `ingest(state: PrairieCastPlaybackState, nowMs: Long)`
- `displayTime(nowMs: Long): Double`
- `isPlaying(nowMs: Long): Boolean`
- `setOptimisticTime(seconds: Double, nowMs: Long)`
- `setOptimisticPlaying(isPlaying: Boolean, nowMs: Long)`

Use a 2-second optimistic window and clamp display time to `0.0..duration` when duration is positive.

- [ ] **Step 6: Run GREEN tests**

Run:

```bash
./gradlew --rerun-tasks --no-build-cache :shared:testDebugUnitTest --tests org.prairieserver.prairie.cast.PrairieCastMessageTest --tests org.prairieserver.prairie.cast.PrairieCastPlaybackClockTest
```

Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add shared/src/commonMain/kotlin/org/prairieserver/prairie/cast shared/src/commonTest/kotlin/org/prairieserver/prairie/cast
git commit -m "feat: add PrairieCast wire protocol"
```

---

### Task 6: Add Android TV PrairieCast Receiver

**Files:**
- Create: `android-shared/src/androidMain/kotlin/org/prairieserver/prairie/common/cast/PrairieCastFrame.kt`
- Create: `android-shared/src/androidMain/kotlin/org/prairieserver/prairie/common/cast/PrairieCastNsdAdvertiser.kt`
- Create: `androidTvApp/src/androidMain/kotlin/org/prairieserver/prairie/tv/cast/TvPrairieCastReceiver.kt`
- Create: `androidTvApp/src/androidMain/kotlin/org/prairieserver/prairie/tv/cast/TvPrairieCastPlayerAdapter.kt`
- Modify: `androidTvApp/src/androidMain/kotlin/org/prairieserver/prairie/tv/di/AndroidTvModule.kt`
- Modify: `androidTvApp/src/androidMain/kotlin/org/prairieserver/prairie/tv/ui/screens/player/TvPlayerScreen.kt`
- Test: `androidTvApp/src/androidUnitTest/kotlin/org/prairieserver/prairie/tv/cast/TvPrairieCastReceiverSourceTest.kt`

**Interfaces:**
- Consumes: Task 5 PrairieCast messages and existing `TvPlayerViewModel` player commands.
- Produces: Android TV `_prairiecast._tcp` receiver with one-controller policy and player command adapter.

- [ ] **Step 1: Write failing TV receiver source test**

```kotlin
package org.prairieserver.prairie.tv.cast

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

class TvPrairieCastReceiverSourceTest {
    @Test
    fun receiverAdvertisesPrairiecastAndAllowsNewestControllerToWin() {
        val receiver = File("src/androidMain/kotlin/org/prairieserver/prairie/tv/cast/TvPrairieCastReceiver.kt").takeIf { it.exists() }?.readText().orEmpty()
        val module = File("src/androidMain/kotlin/org/prairieserver/prairie/tv/di/AndroidTvModule.kt").readText()

        assertTrue(receiver.contains("_prairiecast._tcp") || receiver.contains("PrairieCastProtocol.serviceType"))
        assertTrue(receiver.contains("activeSession"))
        assertTrue(receiver.contains("closePreviousController"))
        assertTrue(module.contains("TvPrairieCastReceiver"))
    }

    @Test
    fun playerAdapterMapsCoreControls() {
        val adapter = File("src/androidMain/kotlin/org/prairieserver/prairie/tv/cast/TvPrairieCastPlayerAdapter.kt").takeIf { it.exists() }?.readText().orEmpty()
        listOf("playPause", "seek", "selectSubtitle", "selectAudio", "setPlaybackSpeed", "playNext").forEach {
            assertTrue(adapter.contains(it), "Adapter must map $it.")
        }
    }
}
```

- [ ] **Step 2: Run test to verify RED**

Run:

```bash
./gradlew :androidTvApp:testDebugUnitTest --tests org.prairieserver.prairie.tv.cast.TvPrairieCastReceiverSourceTest
```

Expected: FAIL because receiver/adapter files do not exist.

- [ ] **Step 3: Implement frame helpers**

Implement `PrairieCastFrame` in `android-shared` using the same 4-byte big-endian length prefix as `PairingFrame`, but in a `cast` package. Reuse parsing limits from pairing to avoid large-frame allocation.

- [ ] **Step 4: Implement NSD advertiser**

`PrairieCastNsdAdvertiser` should:
- use `NsdManager`
- advertise `PrairieCastProtocol.serviceType`
- include TXT attributes `v`, `name`, and `deviceId`
- own start/stop lifecycle

- [ ] **Step 5: Implement receiver**

`TvPrairieCastReceiver` should:
- start a `ServerSocket`
- advertise via `PrairieCastNsdAdvertiser`
- accept one connection at a time
- call `closePreviousController()` before accepting a new controller
- decode frames to `PrairieCastMessage`
- reply to `ping` with `pong`
- handle `launch` through a navigation/player launch callback
- handle `control` through `TvPrairieCastPlayerAdapter`
- periodically send `state` snapshots while connected

- [ ] **Step 6: Implement player adapter**

`TvPrairieCastPlayerAdapter` should wrap lambdas rather than depending directly on the whole VM in tests:

```kotlin
class TvPrairieCastPlayerAdapter(
    private val play: () -> Unit,
    private val pause: () -> Unit,
    private val playPause: () -> Unit,
    private val seek: (Double) -> Unit,
    private val stop: () -> Unit,
    private val selectAudio: (Long) -> Unit,
    private val selectSubtitle: (Long?) -> Unit,
    private val setPlaybackSpeed: (Double) -> Unit,
    private val setQuality: (String) -> Unit,
    private val setVideoGravity: (String) -> Unit,
    private val setHdrEnabled: (Boolean) -> Unit,
    private val setSubtitleSyncMs: (Int) -> Unit,
    private val setSubtitlePosition: (String) -> Unit,
    private val setVolume: (Double) -> Unit,
    private val setMuted: (Boolean) -> Unit,
    private val playNext: () -> Unit,
)
```

Add a `handle(command: PrairieCastControlCommand)` method that validates required fields and ignores unsupported commands gracefully.

- [ ] **Step 7: Wire TV DI/lifecycle**

Register receiver in `AndroidTvModule.kt`. Start/stop it from signed-in TV lifecycle or `MainTvActivity` after server/profile are active. Register/unregister the active player from `TvPlayerScreen.kt` so the receiver can report real state and apply controls.

- [ ] **Step 8: Run tests**

Run:

```bash
./gradlew :androidTvApp:testDebugUnitTest --tests org.prairieserver.prairie.tv.cast.TvPrairieCastReceiverSourceTest
./gradlew :shared:testDebugUnitTest --tests org.prairieserver.prairie.cast.PrairieCastMessageTest
```

Expected: PASS.

- [ ] **Step 9: Commit**

```bash
git add android-shared/src/androidMain/kotlin/org/prairieserver/prairie/common/cast androidTvApp/src/androidMain/kotlin/org/prairieserver/prairie/tv/cast androidTvApp/src/androidMain/kotlin/org/prairieserver/prairie/tv/di/AndroidTvModule.kt androidTvApp/src/androidMain/kotlin/org/prairieserver/prairie/tv/ui/screens/player/TvPlayerScreen.kt androidTvApp/src/androidUnitTest/kotlin/org/prairieserver/prairie/tv/cast
git commit -m "feat: add Android TV PrairieCast receiver"
```

---

### Task 7: Add Phone PrairieCast Browser, Remote, And Play-On-Device

**Files:**
- Create: `android-shared/src/androidMain/kotlin/org/prairieserver/prairie/common/cast/PrairieCastNsdBrowser.kt`
- Create: `androidApp/src/androidMain/kotlin/org/prairieserver/prairie/android/cast/PrairieCastController.kt`
- Create: `androidApp/src/androidMain/kotlin/org/prairieserver/prairie/android/ui/screens/cast/PrairieCastTargetPickerSheet.kt`
- Create: `androidApp/src/androidMain/kotlin/org/prairieserver/prairie/android/ui/screens/cast/PrairieCastMiniBar.kt`
- Create: `androidApp/src/androidMain/kotlin/org/prairieserver/prairie/android/ui/screens/cast/PrairieCastRemoteScreen.kt`
- Modify: `androidApp/src/androidMain/kotlin/org/prairieserver/prairie/android/ui/screens/detail/ItemDetailScreen.kt`
- Modify: `androidApp/src/androidMain/kotlin/org/prairieserver/prairie/android/ui/navigation/AppNavigation.kt`
- Modify: `androidApp/src/androidMain/kotlin/org/prairieserver/prairie/android/di/AndroidModule.kt`
- Test: `androidApp/src/androidUnitTest/kotlin/org/prairieserver/prairie/android/cast/PrairieCastPhoneSourceTest.kt`

**Interfaces:**
- Consumes: Task 5 messages and Task 6 receiver.
- Produces: phone target picker, remote UI, mini-bar, and Play on device entry.

- [ ] **Step 1: Write failing phone source test**

```kotlin
package org.prairieserver.prairie.android.cast

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

class PrairieCastPhoneSourceTest {
    @Test
    fun phoneHasBrowserControllerRemoteAndPlayOnDeviceEntrypoint() {
        val controller = File("src/androidMain/kotlin/org/prairieserver/prairie/android/cast/PrairieCastController.kt").takeIf { it.exists() }?.readText().orEmpty()
        val picker = File("src/androidMain/kotlin/org/prairieserver/prairie/android/ui/screens/cast/PrairieCastTargetPickerSheet.kt").takeIf { it.exists() }?.readText().orEmpty()
        val remote = File("src/androidMain/kotlin/org/prairieserver/prairie/android/ui/screens/cast/PrairieCastRemoteScreen.kt").takeIf { it.exists() }?.readText().orEmpty()
        val mini = File("src/androidMain/kotlin/org/prairieserver/prairie/android/ui/screens/cast/PrairieCastMiniBar.kt").takeIf { it.exists() }?.readText().orEmpty()
        val detail = File("src/androidMain/kotlin/org/prairieserver/prairie/android/ui/screens/detail/ItemDetailScreen.kt").readText()

        assertTrue(controller.contains("PrairieCastController"))
        assertTrue(controller.contains("PrairieCastNsdBrowser"))
        assertTrue(picker.contains("PrairieCastTargetPickerSheet"))
        assertTrue(remote.contains("PrairieCastRemoteScreen"))
        assertTrue(mini.contains("PrairieCastMiniBar"))
        assertTrue(detail.contains("Play on device"))
    }
}
```

- [ ] **Step 2: Run test to verify RED**

Run:

```bash
./gradlew :androidApp:testDebugUnitTest --tests org.prairieserver.prairie.android.cast.PrairieCastPhoneSourceTest
```

Expected: FAIL because phone PrairieCast files and detail action do not exist.

- [ ] **Step 3: Implement NSD browser**

`PrairieCastNsdBrowser` should:
- discover `PrairieCastProtocol.serviceType`
- expose `StateFlow<List<PrairieCastTarget>>`
- resolve host/port/name/deviceId/version TXT data
- stop browsing cleanly on lifecycle end

- [ ] **Step 4: Implement controller**

`PrairieCastController` should:
- browse/connect/disconnect
- send `hello`, `launch`, and `control`
- consume `state`, `error`, `ping`, `pong`, `close`
- expose `StateFlow<PrairieCastControllerState>`
- use `PrairieCastPlaybackClock` for optimistic play/seek

- [ ] **Step 5: Implement UI**

Target picker:
- shows discovered devices
- shows "Searching..." and empty states
- selects target and launches when a playback request exists

Mini-bar:
- visible when connected or remote state exists
- title, subtitle, play/pause, disconnect, open remote

Remote screen:
- transport buttons
- scrubber
- audio/subtitle/quality selectors where state lists options
- volume/mute if supported
- clean disabled/loading/error states

- [ ] **Step 6: Add Play on device**

In `ItemDetailScreen.kt`, add a secondary action beside existing play/download actions:
- label: `Play on device`
- opens `PrairieCastTargetPickerSheet`
- sends `PrairieCastLaunchRequest(serverId, playback)` with content/file/track/resume information matching the local play action

Do not add ebooks to TV targets.

- [ ] **Step 7: Run tests**

Run:

```bash
./gradlew :androidApp:testDebugUnitTest --tests org.prairieserver.prairie.android.cast.PrairieCastPhoneSourceTest
./gradlew :shared:testDebugUnitTest --tests org.prairieserver.prairie.cast.PrairieCastPlaybackClockTest
```

Expected: PASS.

- [ ] **Step 8: Commit**

```bash
git add android-shared/src/androidMain/kotlin/org/prairieserver/prairie/common/cast/PrairieCastNsdBrowser.kt androidApp/src/androidMain/kotlin/org/prairieserver/prairie/android/cast androidApp/src/androidMain/kotlin/org/prairieserver/prairie/android/ui/screens/cast androidApp/src/androidMain/kotlin/org/prairieserver/prairie/android/ui/screens/detail/ItemDetailScreen.kt androidApp/src/androidMain/kotlin/org/prairieserver/prairie/android/ui/navigation/AppNavigation.kt androidApp/src/androidMain/kotlin/org/prairieserver/prairie/android/di/AndroidModule.kt androidApp/src/androidUnitTest/kotlin/org/prairieserver/prairie/android/cast
git commit -m "feat: add phone PrairieCast remote"
```

---

### Task 8: Add Android Push Registration And Guarded Receiver

**Files:**
- Create: `shared/src/commonMain/kotlin/org/prairieserver/prairie/model/notifications/PushRegistrationModels.kt`
- Create: `shared/src/commonMain/kotlin/org/prairieserver/prairie/network/api/PushRegistrationApi.kt`
- Create: `shared/src/commonMain/kotlin/org/prairieserver/prairie/repository/PushRegistrationRepository.kt`
- Create: `androidApp/src/androidMain/kotlin/org/prairieserver/prairie/android/push/AndroidPushTokenProvider.kt`
- Create: `androidApp/src/androidMain/kotlin/org/prairieserver/prairie/android/push/PrairieFirebaseMessagingService.kt`
- Create: `androidApp/src/androidMain/kotlin/org/prairieserver/prairie/android/push/PushNotificationPresenter.kt`
- Modify: `androidApp/src/androidMain/AndroidManifest.xml`
- Modify: `androidApp/src/androidMain/kotlin/org/prairieserver/prairie/android/di/AndroidModule.kt`
- Test: `shared/src/commonTest/kotlin/org/prairieserver/prairie/network/api/PushRegistrationApiTest.kt`
- Test: `androidApp/src/androidUnitTest/kotlin/org/prairieserver/prairie/android/push/AndroidPushSourceTest.kt`
- Test: `androidTvApp/src/androidUnitTest/kotlin/org/prairieserver/prairie/tv/push/TvPushSurfaceSourceTest.kt`

**Interfaces:**
- Consumes: existing authenticated API client and notification repository/inbox paths.
- Produces: phone push token registration and data-only message handling without adding TV push setup.

- [ ] **Step 1: Write failing API/source tests**

`PushRegistrationApiTest.kt`:

```kotlin
package org.prairieserver.prairie.network.api

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.prairieserver.prairie.model.notifications.PushDeviceRegisterRequest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PushRegistrationApiTest {
    @Test
    fun registerAndroidDevicePostsOpaqueTokenPayload() = runTest {
        var captured: HttpRequestData? = null
        val client = HttpClient(MockEngine { request ->
            captured = request
            respond("{}", HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
        }) {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        }
        val api = DefaultPushRegistrationApi(client)

        api.register(PushDeviceRegisterRequest(platform = "android", token = "fcm-token", deviceId = "device", pushMode = "private_push"))

        assertEquals("/api/v1/notifications/push/devices", captured?.url?.encodedPath)
        assertTrue(captured.toString().contains("POST"))
    }
}
```

`AndroidPushSourceTest.kt`:

```kotlin
package org.prairieserver.prairie.android.push

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

class AndroidPushSourceTest {
    @Test
    fun phonePushHasTokenProviderMessagingServiceAndGenericPresenter() {
        val provider = File("src/androidMain/kotlin/org/prairieserver/prairie/android/push/AndroidPushTokenProvider.kt").takeIf { it.exists() }?.readText().orEmpty()
        val service = File("src/androidMain/kotlin/org/prairieserver/prairie/android/push/PrairieFirebaseMessagingService.kt").takeIf { it.exists() }?.readText().orEmpty()
        val presenter = File("src/androidMain/kotlin/org/prairieserver/prairie/android/push/PushNotificationPresenter.kt").takeIf { it.exists() }?.readText().orEmpty()
        val manifest = File("src/androidMain/AndroidManifest.xml").readText()

        assertTrue(provider.contains("AndroidPushTokenProvider"))
        assertTrue(service.contains("FirebaseMessagingService") || service.contains("PushMessageHandler"))
        assertTrue(service.contains("delivery_id"))
        assertTrue(presenter.contains("fetch") || presenter.contains("notificationsRepository"))
        assertTrue(manifest.contains("POST_NOTIFICATIONS"))
    }
}
```

`TvPushSurfaceSourceTest.kt`:

```kotlin
package org.prairieserver.prairie.tv.push

import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse

class TvPushSurfaceSourceTest {
    @Test
    fun tvDoesNotExposePushSetupSurface() {
        val files = File("src/androidMain/kotlin/org/prairieserver/prairie/tv").walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .joinToString("\n") { it.readText() }
        assertFalse(files.contains("PushNotificationSetup"))
        assertFalse(files.contains("FirebaseMessagingService"))
    }
}
```

- [ ] **Step 2: Run tests to verify RED**

Run:

```bash
./gradlew :shared:testDebugUnitTest --tests org.prairieserver.prairie.network.api.PushRegistrationApiTest
./gradlew :androidApp:testDebugUnitTest --tests org.prairieserver.prairie.android.push.AndroidPushSourceTest
./gradlew :androidTvApp:testDebugUnitTest --tests org.prairieserver.prairie.tv.push.TvPushSurfaceSourceTest
```

Expected: shared and phone tests fail because push files do not exist; TV test should pass or fail only if a push setup was accidentally added.

- [ ] **Step 3: Add shared request models/API**

Create `PushRegistrationModels.kt`:

```kotlin
package org.prairieserver.prairie.model.notifications

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class PushDeviceRegisterRequest(
    val platform: String,
    val token: String,
    @SerialName("device_id") val deviceId: String,
    @SerialName("push_mode") val pushMode: String = "private_push",
)

@Serializable
data class PushDeviceRegisterResponse(
    val id: String? = null,
    @SerialName("push_mode") val pushMode: String = "private_push",
)
```

Create `PushRegistrationApi.kt` with endpoint path `/api/v1/notifications/push/devices` and delete path `/api/v1/notifications/push/devices/{deviceId}`.

- [ ] **Step 4: Add phone push provider**

Implement `AndroidPushTokenProvider` as an interface plus no-op default:

```kotlin
interface AndroidPushTokenProvider {
    suspend fun token(): String?
}

class DisabledAndroidPushTokenProvider : AndroidPushTokenProvider {
    override suspend fun token(): String? = null
}
```

If Firebase config/dependency is present, add `FirebaseAndroidPushTokenProvider` in the same package. If not present, keep the disabled provider wired and document that remote push requires Firebase configuration.

- [ ] **Step 5: Add message handler/service**

Implement a handler that can be unit-tested without Firebase:

```kotlin
class PushMessageHandler(
    private val presenter: PushNotificationPresenter,
) {
    suspend fun handle(data: Map<String, String>): Boolean {
        val deliveryId = data["delivery_id"] ?: return false
        presenter.present(deliveryId)
        return true
    }
}
```

If Firebase dependency is present, `PrairieFirebaseMessagingService : FirebaseMessagingService` delegates `remoteMessage.data` to `PushMessageHandler`. If Firebase dependency is absent, keep a non-service `PrairieFirebaseMessagingService` wrapper out of the manifest and keep source tests adjusted to the handler.

- [ ] **Step 6: Add notification presenter**

`PushNotificationPresenter` should fetch notification metadata from existing repository/inbox APIs when possible. If fetch fails, post a generic notification:

```text
Prairie has a new notification
Open Prairie to view it.
```

No media titles, profile names, usernames, server URLs, or artwork should be rendered from FCM data.

- [ ] **Step 7: Manifest and permission**

Add `android.permission.POST_NOTIFICATIONS` to phone manifest. Add the service declaration only when the Firebase class compiles. Do not add TV push setup.

- [ ] **Step 8: Run tests**

Run:

```bash
./gradlew :shared:testDebugUnitTest --tests org.prairieserver.prairie.network.api.PushRegistrationApiTest
./gradlew :androidApp:testDebugUnitTest --tests org.prairieserver.prairie.android.push.AndroidPushSourceTest
./gradlew :androidTvApp:testDebugUnitTest --tests org.prairieserver.prairie.tv.push.TvPushSurfaceSourceTest
```

Expected: PASS.

- [ ] **Step 9: Commit**

```bash
git add shared/src/commonMain/kotlin/org/prairieserver/prairie/model/notifications/PushRegistrationModels.kt shared/src/commonMain/kotlin/org/prairieserver/prairie/network/api/PushRegistrationApi.kt shared/src/commonMain/kotlin/org/prairieserver/prairie/repository/PushRegistrationRepository.kt androidApp/src/androidMain/kotlin/org/prairieserver/prairie/android/push androidApp/src/androidMain/AndroidManifest.xml androidApp/src/androidMain/kotlin/org/prairieserver/prairie/android/di/AndroidModule.kt shared/src/commonTest/kotlin/org/prairieserver/prairie/network/api/PushRegistrationApiTest.kt androidApp/src/androidUnitTest/kotlin/org/prairieserver/prairie/android/push/AndroidPushSourceTest.kt androidTvApp/src/androidUnitTest/kotlin/org/prairieserver/prairie/tv/push/TvPushSurfaceSourceTest.kt
git commit -m "feat: add guarded Android push registration"
```

---

### Task 9: Documentation, Full Verification, And Issue Mapping

**Files:**
- Modify: `README.md`
- Modify: `docs/superpowers/specs/2026-07-06-open-issue-closure-design.md` only if implementation discovers a real contract correction
- Test: existing focused verification suite

**Interfaces:**
- Consumes: all previous tasks.
- Produces: updated docs and final verified issue state.

- [ ] **Step 1: Update README**

Document:
- Collections are browse-only in Android clients; authoring is web-only.
- Downloads support monitored subscriptions and Reclaim Watched.
- PrairieControl supports phone-to-TV remote/play-on-device.
- Android push requires server provider support and Firebase configuration for real FCM delivery.
- Requests/Admin/Watch Together remain hidden from menus.

- [ ] **Step 2: Run focused verification**

Run:

```bash
./gradlew --rerun-tasks --no-build-cache \
:shared:testDebugUnitTest \
:android-shared:testDebugUnitTest \
:androidApp:testDebugUnitTest \
:androidTvApp:testDebugUnitTest \
--tests org.prairieserver.prairie.android.ui.screens.collections.CollectionsBrowseOnlySourceTest \
--tests org.prairieserver.prairie.tv.ui.screens.collections.TvCollectionsBrowseOnlySourceTest \
--tests org.prairieserver.prairie.common.downloads.DownloadSubscriptionDaoTest \
--tests org.prairieserver.prairie.common.downloads.DownloadReclaimPlannerTest \
--tests org.prairieserver.prairie.common.downloads.DownloadSubscriptionEvaluatorTest \
--tests org.prairieserver.prairie.android.ui.screens.downloads.DownloadsMonitoringSourceTest \
--tests org.prairieserver.prairie.cast.PrairieCastMessageTest \
--tests org.prairieserver.prairie.cast.PrairieCastPlaybackClockTest \
--tests org.prairieserver.prairie.tv.cast.TvPrairieCastReceiverSourceTest \
--tests org.prairieserver.prairie.android.cast.PrairieCastPhoneSourceTest \
--tests org.prairieserver.prairie.network.api.PushRegistrationApiTest \
--tests org.prairieserver.prairie.android.push.AndroidPushSourceTest \
--tests org.prairieserver.prairie.tv.push.TvPushSurfaceSourceTest \
--tests org.prairieserver.prairie.android.ui.navigation.ClientSurfaceVisibilitySourceTest \
--tests org.prairieserver.prairie.tv.ui.navigation.TvClientSurfaceVisibilitySourceTest
```

Expected: PASS.

- [ ] **Step 3: Run compile verification**

Run:

```bash
./gradlew :androidApp:compileDebugKotlinAndroid :androidTvApp:compileDebugKotlinAndroid :android-shared:compileDebugKotlinAndroid :shared:compileDebugKotlinAndroid
```

Expected: PASS.

- [ ] **Step 4: Run PrairieControl device smoke when devices are available**

Manual/device checks:
- TV app advertises `_prairiecast._tcp`.
- Phone discovers TV target.
- "Play on device" launches a movie/episode on TV.
- Phone remote can play/pause/seek.
- Subtitle selection off/on works through remote.
- TV remains usable if phone disconnects.

- [ ] **Step 5: Run download smoke**

Manual/device checks:
- Create a monitored video subscription.
- Trigger evaluation.
- Confirm missing eligible files enqueue with selected quality.
- Mark one item watched/read/listened.
- Reclaim Watched shows correct count/bytes and deletes only selected completed items.

- [ ] **Step 6: Commit docs**

```bash
git add README.md docs/superpowers/specs/2026-07-06-open-issue-closure-design.md
git commit -m "docs: update Android feature status"
```

- [ ] **Step 7: Final issue status**

Report:
- #5/#6 corrected when browse-only tests pass and no authoring UI remains.
- #22 corrected when subscription/reclaim tests pass and mobile smoke is complete.
- #15/#16/#17 corrected when protocol tests pass and phone-to-TV smoke succeeds.
- #19 corrected only if Firebase/server endpoint smoke succeeds; otherwise mark client-side registration ready and server/Firebase delivery blocked.

---

## Self-Review

Spec coverage:
- #5/#6 are covered by Task 1.
- #22 is covered by Tasks 2, 3, 4, and 9.
- #15 is covered by Tasks 5, 6, and 9.
- #16/#17 are covered by Tasks 5, 7, and 9.
- #19 is covered by Task 8 and the explicit verification caveat in Task 9.
- #27 remains excluded and hidden by global constraint.

Type consistency:
- Download subscription target/media kind names are consistent across domain/entity/evaluator.
- PrairieCast protocol names mirror Apple snake-case command values.
- Push registration path is centralized in `PushRegistrationApi`.

Risk notes:
- If Firebase configuration is absent, #19 cannot be truthfully called real delivery complete. The implementation must make that visible rather than faking success.
- If PrairieCast TLS-PSK interop is blocked by Android runtime behavior, keep the protocol independent and document the transport limitation while retaining Android-to-Android functionality.
