# Phase 1 · Track B — Offline-First Data Foundation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make a local Room database the source of truth for the home/library browse + resume + downloads + user-state paths, with a typed dirty-operations outbox that round-trips state to the server — replacing today's network-first reads, with zero data loss from existing file/sidecar + scoped-JSON storage.

**Architecture (converged Claude↔Codex, see `docs/superpowers/notes/architecture-debate.md` "Track B decisions"):**
- **Room lives in `android-shared/androidMain`** (Android-only). Repository **ports/interfaces** for the migrated paths go in `shared/commonMain`; Android implementations compose the existing Ktor APIs + Room. No Room-KMP, no data-layer KMP teardown now (D1 is later hygiene).
- **Hybrid sync:** Room is the local read source and optimistic write target; every user mutation writes a projection row **and** a typed `dirty_operations` outbox row; a sync engine drains idempotent ops, refreshes the server projection, and resolves conflicts by **per-field policy** (position/CFI = LWW + optional furthest-monotonic; watched/ratings/favorites = optimistic until ack then server projection; track selections = client-LWW by fingerprint).
- **Migration:** idempotent import of existing sidecars + scoped-JSON into Room, **dual-write** for one release window, legacy files never deleted until Room is proven equivalent.

**Tech Stack:** Room (`androidx.room`, KSP), WorkManager (already used by `DownloadWorker`), Koin DI, `kotlin.test` + JUnit4, **Robolectric 4.13** for Room DAO tests (in-memory DB), kotlinx-coroutines-test. Module `:android-shared`, source set `androidUnitTest`.

**Test command:** `./gradlew :android-shared:testDebugUnitTest`

**Scope guard (per Codex):** Track B covers **only** home/library browse + resume + downloads + user-state. The other ~15 repositories stay network-first until the first offline round-trip works. Do not convert all 19 repositories.

**Server-contract dependency (flagged risk):** rigorous LWW needs per-field `updated_at` / idempotency keys the server may not expose yet (`PersonalDataApi`, `PlaybackApi`). Track B **degrades gracefully**: "local-optimistic, server projection authoritative after ack, conflicts logged." If per-field timestamps are needed, raise a **server PR** (do not block Track B on it; do not push server changes directly to main).

---

## File structure

- `android-shared/.../data/db/PrairieDatabase.kt` — Room database (Android-only).
- `android-shared/.../data/db/entity/` — `UserItemStateEntity`, `DirtyOperationEntity`, `LegacyImportEntity`, `DownloadEntity`.
- `android-shared/.../data/db/dao/` — `UserItemStateDao`, `DirtyOperationDao`, `DownloadDao`, `LegacyImportDao`.
- `android-shared/.../data/sync/OutboxOperation.kt` — pure typed op model + coalescing.
- `android-shared/.../data/sync/ConflictPolicy.kt` — pure per-field resolution functions.
- `android-shared/.../data/sync/SyncEngine.kt` + `SyncWorker.kt` — drain outbox, refresh projection.
- `android-shared/.../data/repository/` — Android Room-backed implementations of the migrated ports.
- `shared/.../repository/port/` — repository interfaces for the migrated paths (resume/user-state/downloads/browse).
- `android-shared/.../data/migration/LegacyImporter.kt` — idempotent sidecar + scoped-JSON import.
- Tests mirror each under `android-shared/src/androidUnitTest/...`.

---

## Task 1: Room foundation — database, entities, DAOs

**Files:**
- Create: `android-shared/.../data/db/entity/UserItemStateEntity.kt`, `DirtyOperationEntity.kt`, `LegacyImportEntity.kt`, `DownloadEntity.kt`
- Create: `android-shared/.../data/db/dao/UserItemStateDao.kt`, `DirtyOperationDao.kt`, `DownloadDao.kt`, `LegacyImportDao.kt`
- Create: `android-shared/.../data/db/PrairieDatabase.kt`
- Modify: `android-shared/build.gradle.kts` (Room + KSP)
- Test: `android-shared/src/androidUnitTest/.../data/db/PrairieDatabaseDaoTest.kt`

- [ ] **Step 1: Add Room + KSP to `android-shared/build.gradle.kts`** (per Codex review — KMP-specific):
  - Add KSP + Room aliases to `gradle/libs.versions.toml` (none exist today).
  - `android-shared` is a **KMP module with an `androidTarget`**, so use the **Android-target KSP configuration**, not generic `ksp(...)`: `dependencies { add("kspAndroid", libs.androidx.room.compiler) }`. Add `room-runtime` + `room-ktx` to the `androidMain` source-set deps.
  - Room schema export dir `room.schemaLocation` → `android-shared/schemas`.
  - The DAO test needs Robolectric: add `robolectric` + `androidx.test.core` (for `ApplicationProvider`) to the `androidUnitTest` source-set deps (currently only kotlin-test/JUnit/coroutines-test).

- [ ] **Step 2: Write the failing DAO test** (Robolectric + in-memory DB)

```kotlin
package com.continuum.app.common.data.db

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.continuum.app.common.data.db.entity.UserItemStateEntity
import kotlinx.coroutines.test.runTest
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
class PrairieDatabaseDaoTest {
    private val db = Room.inMemoryDatabaseBuilder(
        ApplicationProvider.getApplicationContext(),
        PrairieDatabase::class.java,
    ).allowMainThreadQueries().build()

    @AfterTest fun tearDown() = db.close()

    @Test fun userItemStateUpsertAndReadByScope() = runTest {
        val dao = db.userItemStateDao()
        val row = UserItemStateEntity(
            profileId = "p1", contentId = "c1", fileId = 7,
            positionSeconds = 120.0, durationSeconds = 3600.0,
            watched = false, ratingValue = null, favorite = false,
            audioFingerprint = null, subtitleFingerprint = null,
            cfi = null, readProgress = null,
            clientUpdatedAtMs = 1000L, serverUpdatedAtMs = null,
        )
        dao.upsert(row)
        assertEquals(120.0, dao.get("p1", "c1", 7)?.positionSeconds)
    }
}
```

- [ ] **Step 3: Run to verify it fails**

Run: `./gradlew :android-shared:testDebugUnitTest --tests "com.continuum.app.common.data.db.PrairieDatabaseDaoTest"`
Expected: FAIL — entities/DAO/database do not exist.

- [ ] **Step 4: Implement the entities** (per Codex's schema)

```kotlin
// UserItemStateEntity.kt
package com.continuum.app.common.data.db.entity

import androidx.room.Entity

@Entity(tableName = "user_item_state", primaryKeys = ["profileId", "contentId", "fileId"])
data class UserItemStateEntity(
    val profileId: String,
    val contentId: String,
    val fileId: Int,
    val positionSeconds: Double,
    val durationSeconds: Double?,
    val watched: Boolean,
    val ratingValue: Int?,
    val favorite: Boolean,
    // Stable selection fingerprints: (index|language|codec|title|forced), not raw UI index.
    val audioFingerprint: String?,
    val subtitleFingerprint: String?,
    val cfi: String?,
    val readProgress: Double?,
    val clientUpdatedAtMs: Long,
    val serverUpdatedAtMs: Long?,
)
```

```kotlin
// DirtyOperationEntity.kt
package com.continuum.app.common.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

// Typed, ordered, idempotent outbox. coalesceKey lets newer ops of the same
// kind+target replace older un-synced ones (e.g. repeated SET_POSITION).
@Entity(tableName = "dirty_operations")
data class DirtyOperationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val opKind: String,        // SET_POSITION, SET_WATCHED, SET_RATING, SET_FAVORITE, SET_TRACK_SELECTION, SET_CFI
    val coalesceKey: String,   // e.g. "p1|c1|7|SET_POSITION"
    val payloadJson: String,
    val createdAtMs: Long,
    val attemptCount: Int = 0,
)
```

```kotlin
// DownloadEntity.kt — Room projection of DownloadSidecar (see model/download/DownloadSidecar.kt for fields).
package com.continuum.app.common.data.db.entity

import androidx.room.Entity

@Entity(tableName = "downloads", primaryKeys = ["serverId", "profileId", "mediaFileId"])
data class DownloadEntity(
    val serverId: String,
    val profileId: String,
    val mediaFileId: Int,
    val recordId: String,
    val title: String,
    val posterUrl: String?,
    val fileName: String,
    val container: String?,
    val mediaType: String,
    val localUri: String?,
    // Mirror DownloadStatus wire values (lowercase): queued/downloading/completed/
    // failed/cancelled (see model/download/DownloadModels.kt:59); plus a local-only
    // "stale" for imported rows whose bytes are missing (Task 6).
    val status: String,
    val updatedAtMs: Long,
)
```

```kotlin
// LegacyImportEntity.kt — lets the migration import re-run idempotently.
package com.continuum.app.common.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "legacy_imports")
data class LegacyImportEntity(
    @PrimaryKey val sourcePath: String,
    val sourceHash: String,
    val sourceMtimeMs: Long,
    val importedAtMs: Long,
)
```

- [ ] **Step 5: Implement the DAOs** (upsert + scoped reads + outbox + downloads)

```kotlin
// UserItemStateDao.kt
package com.continuum.app.common.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.continuum.app.common.data.db.entity.UserItemStateEntity

@Dao
interface UserItemStateDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(row: UserItemStateEntity)

    @Query("SELECT * FROM user_item_state WHERE profileId = :p AND contentId = :c AND fileId = :f")
    suspend fun get(p: String, c: String, f: Int): UserItemStateEntity?
}
```

```kotlin
// DirtyOperationDao.kt
package com.continuum.app.common.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.continuum.app.common.data.db.entity.DirtyOperationEntity

@Dao
interface DirtyOperationDao {
    @Insert suspend fun insert(op: DirtyOperationEntity): Long

    @Query("DELETE FROM dirty_operations WHERE coalesceKey = :key AND id < :keepId")
    suspend fun coalesceOlderThan(key: String, keepId: Long)

    @Query("SELECT * FROM dirty_operations ORDER BY id ASC LIMIT :limit")
    suspend fun nextBatch(limit: Int): List<DirtyOperationEntity>

    @Query("DELETE FROM dirty_operations WHERE id = :id")
    suspend fun delete(id: Long)
}
```

Add `DownloadDao` (upsert + `getAll(serverId, profileId)`) and `LegacyImportDao` (upsert + `get(sourcePath)`) following the same shape.

- [ ] **Step 6: Implement `PrairieDatabase`**

```kotlin
package com.continuum.app.common.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.continuum.app.common.data.db.dao.*
import com.continuum.app.common.data.db.entity.*

@Database(
    entities = [UserItemStateEntity::class, DirtyOperationEntity::class, DownloadEntity::class, LegacyImportEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class PrairieDatabase : RoomDatabase() {
    abstract fun userItemStateDao(): UserItemStateDao
    abstract fun dirtyOperationDao(): DirtyOperationDao
    abstract fun downloadDao(): DownloadDao
    abstract fun legacyImportDao(): LegacyImportDao
}
```

- [ ] **Step 7: Run to verify the DAO test passes; commit**

Run: `./gradlew :android-shared:testDebugUnitTest --tests "com.continuum.app.common.data.db.PrairieDatabaseDaoTest"`
Expected: PASS.
```bash
git add android-shared/src/androidMain/kotlin/com/continuum/app/common/data/db/ \
        android-shared/src/androidUnitTest/kotlin/com/continuum/app/common/data/db/PrairieDatabaseDaoTest.kt \
        android-shared/build.gradle.kts gradle/libs.versions.toml android-shared/schemas
git commit -m "Room foundation: PrairieDatabase + user-state/outbox/downloads/legacy entities + DAOs"
```

---

## Task 2: Outbox operation model + coalescing (pure TDD)

**Files:**
- Create: `android-shared/.../data/sync/OutboxOperation.kt`
- Create: `android-shared/src/androidUnitTest/.../data/sync/OutboxOperationTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
package com.continuum.app.common.data.sync

import kotlin.test.Test
import kotlin.test.assertEquals

class OutboxOperationTest {
    @Test fun coalesceKeyIsStablePerTargetAndKind() {
        val op = OutboxOperation.setPosition(profileId = "p1", contentId = "c1", fileId = 7, positionSeconds = 30.0, atMs = 1L)
        assertEquals("p1|c1|7|SET_POSITION", op.coalesceKey)
        assertEquals("SET_POSITION", op.kind)
    }

    @Test fun favoriteAndRatingCoalesceSeparately() {
        val fav = OutboxOperation.setFavorite("p1", "c1", true, 1L)
        val rate = OutboxOperation.setRating("p1", "c1", 5, 1L)
        assertEquals("p1|c1|SET_FAVORITE", fav.coalesceKey)
        assertEquals("p1|c1|SET_RATING", rate.coalesceKey)
    }
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `./gradlew :android-shared:testDebugUnitTest --tests "com.continuum.app.common.data.sync.OutboxOperationTest"`
Expected: FAIL — `OutboxOperation` does not exist.

- [ ] **Step 3: Implement the pure op model** — a sealed/factory model producing `kind`, `coalesceKey`, and a serializable `payloadJson` (kotlinx.serialization). **Outbox is limited to the four server-supported ops** (per Codex review): `SET_POSITION`, `SET_WATCHED`, `SET_RATING`, `SET_FAVORITE`. Factory functions as in the test; `coalesceKey` = `"$profileId|$contentId[|$fileId]|$KIND"`.
  - **`SET_TRACK_SELECTION` is NOT in the outbox** — no server API persists track preference (`changeAudio` needs a live session; subtitle choice has no API). Track selection is stored in the Room projection **local-only** until a server projection API exists (defer to a server PR / later phase).
  - **CFI / ebook progress is NOT in this outbox** — it already round-trips via the existing `EbookProgressSyncer` + ebook progress endpoint; Track B does not duplicate it. (Reading lives in Phase 4.)

- [ ] **Step 4: Run to verify it passes; commit**

Run: `./gradlew :android-shared:testDebugUnitTest --tests "com.continuum.app.common.data.sync.OutboxOperationTest"`
Expected: PASS.
```bash
git add android-shared/src/androidMain/kotlin/com/continuum/app/common/data/sync/OutboxOperation.kt \
        android-shared/src/androidUnitTest/kotlin/com/continuum/app/common/data/sync/OutboxOperationTest.kt
git commit -m "Outbox: typed sync operations with stable coalesce keys"
```

---

## Task 3: Per-field conflict policy (pure TDD)

**Files:**
- Create: `android-shared/.../data/sync/ConflictPolicy.kt`
- Create: `android-shared/src/androidUnitTest/.../data/sync/ConflictPolicyTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
package com.continuum.app.common.data.sync

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ConflictPolicyTest {
    @Test fun lastWriteWinsByTimestamp() {
        assertTrue(ConflictPolicy.localWins(localUpdatedMs = 200L, serverUpdatedMs = 100L))
        assertTrue(!ConflictPolicy.localWins(localUpdatedMs = 100L, serverUpdatedMs = 200L))
    }

    @Test fun furthestPositionIsMonotonicMax() {
        assertEquals(300.0, ConflictPolicy.furthest(localSeconds = 300.0, serverSeconds = 250.0))
        assertEquals(400.0, ConflictPolicy.furthest(localSeconds = 120.0, serverSeconds = 400.0))
    }
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `./gradlew :android-shared:testDebugUnitTest --tests "com.continuum.app.common.data.sync.ConflictPolicyTest"`
Expected: FAIL — `ConflictPolicy` does not exist.

- [ ] **Step 3: Implement the pure policies**

```kotlin
package com.continuum.app.common.data.sync

object ConflictPolicy {
    // LWW for current resume position / CFI / track selections.
    fun localWins(localUpdatedMs: Long, serverUpdatedMs: Long?): Boolean =
        serverUpdatedMs == null || localUpdatedMs >= serverUpdatedMs

    // Monotonic furthest for continue-watching ranking / furthest-read.
    fun furthest(localSeconds: Double, serverSeconds: Double): Double =
        maxOf(localSeconds, serverSeconds)
}
```

- [ ] **Step 4: Run to verify it passes; commit**

Run: `./gradlew :android-shared:testDebugUnitTest --tests "com.continuum.app.common.data.sync.ConflictPolicyTest"`
Expected: PASS.
```bash
git add android-shared/src/androidMain/kotlin/com/continuum/app/common/data/sync/ConflictPolicy.kt \
        android-shared/src/androidUnitTest/kotlin/com/continuum/app/common/data/sync/ConflictPolicyTest.kt
git commit -m "Sync: per-field conflict policy (LWW + furthest-monotonic)"
```

---

## Task 4: Repository port + Room-backed resume/user-state impl (strangler)

**Files:**
- Create: `shared/.../repository/port/UserItemStatePort.kt` (interface, commonMain)
- Create: `android-shared/.../data/repository/RoomUserItemStateRepository.kt`
- Create test: `android-shared/src/androidUnitTest/.../data/repository/RoomUserItemStateRepositoryTest.kt`
- Modify: `shared/.../di/RepositoryModule.kt` and the Android DI module (Koin override)

**DI reality (per Codex review):** `RepositoryModule` binds **final** concrete repos (`PersonalDataRepository`, `PlaybackRepository`, `DownloadsRepository`) in `commonMain`, and consumers depend on those concrete classes — so a same-key Koin rebind cannot intercept them. The strangler therefore introduces an explicit **port** and **repoints the migrated consumers at the port**, with the Room-backed impl bound in the **platform module loaded after `sharedModules()`**: `androidModule` (`ContinuumApplication`) and `androidTvModule` (`ContinuumTvApplication`) — the documented load-order override point (see the `TokenManager` override in `AndroidModule.kt:90`). Not `RepositoryModule`.

- [ ] **Step 1: Write the failing test** (Robolectric + in-memory DB + a fake `PersonalDataApi`)

Test that `setPosition` writes the Room projection row **and** enqueues a `SET_POSITION` outbox op (optimistic local-first), reading both back via the DAOs.

```kotlin
@Test fun setPositionWritesProjectionAndOutbox() = runTest {
    repo.setPosition(profileId = "p1", contentId = "c1", fileId = 7, positionSeconds = 90.0, durationSeconds = 3600.0, atMs = 1000L)
    assertEquals(90.0, userItemStateDao.get("p1", "c1", 7)?.positionSeconds)
    assertEquals(1, dirtyOperationDao.nextBatch(10).count { it.opKind == "SET_POSITION" })
}
```

- [ ] **Step 2: Run to verify it fails**, then **Step 3: implement** `UserItemStatePort` (interface in `shared/commonMain`: `observeResume(...)`, `setPosition`, `setWatched`, `setRating`, `setFavorite` — **no `setTrackSelection` in the synced port; track selection is a local-only projection write**) and `RoomUserItemStateRepository` (android-shared) writing projection + outbox in one transaction (coalescing older ops via `DirtyOperationDao.coalesceOlderThan`).

- [ ] **Step 4: Repoint consumers + bind in the platform module.** Because the concrete repos are final, change the **resume/user-state consumers** (the ViewModels/use cases that today call `PersonalDataRepository`/`PlaybackRepository` for these paths) to depend on `UserItemStatePort`, and bind `RoomUserItemStateRepository` as the port in `androidModule` **and** `androidTvModule` (after `sharedModules()`). Limit the repointing to this slice; leave the other repositories' consumers untouched. Run tests green.

- [ ] **Step 5: commit.**

```bash
git commit -m "Strangler: Room-backed UserItemState port (optimistic local-first + outbox), bound in platform module"
```

---

## Task 5: Sync engine + WorkManager worker

**Files:**
- Create: `android-shared/.../data/sync/SyncEngine.kt` (+ test with a fake API)
- Create: `android-shared/.../data/sync/SyncWorker.kt` (WorkManager, mirrors `DownloadWorker` patterns)

**Sync reality (per Codex review):** today's mutation APIs return `Unit` (no server state on ack), and there is **no per-field `updated_at`** (only item-level progress `updatedAt` and rating `rated_at`). So `drain()` **cannot** conflict-resolve on the mutation ack. The implementable model is: **send op → on success, delete the op; conflict resolution happens on the READ/refresh path**, where a server GET (`listProgress`) is reconciled with local via `ConflictPolicy` using the timestamps that exist (progress `updatedAt`; furthest-position for resume). The op→API mapping uses the real method names:

| Op | Server call |
|---|---|
| `SET_POSITION` | `PersonalDataApi.syncProgress(SyncProgressRequest([SyncProgressItem(mediaItemId, position, duration, forceOverwrite)]))` — **not** `PlaybackApi.updateProgress`, which needs a live `sessionId` and can't drain a restarted offline outbox |
| `SET_WATCHED` | `markWatched(itemId)` / `markUnwatched(itemId)` |
| `SET_RATING` | `setRating(itemId, value)` / `deleteRating(itemId)` |
| `SET_FAVORITE` | `addFavorite(itemId)` / `removeFavorite(itemId)` |

- [ ] **Step 1: Write the failing test** — given outbox ops, `SyncEngine.drain()` maps each to the correct API call (above) in order, deletes acked ops; on send failure, increments `attemptCount` and stops (retry later). Use a fake `PersonalDataApi`. Assert `SET_POSITION` routes to `syncProgress` (not a session call).

- [ ] **Step 2–3:** Run-fail, then implement `SyncEngine.drain()` (batch from `DirtyOperationDao.nextBatch`, map op→`PersonalDataApi` call per the table, delete on ack). **Graceful degrade:** mutations return `Unit`, so after ack, a follow-up `listProgress` refresh reconciles the projection — `ConflictPolicy.localWins`/`furthest` applied on that read using progress `updatedAt`; watched/favorite (no timestamp) trust the server projection after ack; **log a conflict line** when local and server disagree (observability). **Step 4:** wrap in `SyncWorker` scheduled on connectivity (WorkManager constraints) and after each optimistic write. **Step 5:** run tests; commit.

```bash
git commit -m "Sync engine + worker: drain outbox, resolve conflicts, refresh projection"
```

---

## Task 6: Legacy migration import (sidecars + scoped JSON), dual-write

**Files:**
- Create: `android-shared/.../data/migration/LegacyImporter.kt` (+ test)
- Modify: download enqueue/worker/delete paths for dual-write (`DownloadEnqueuer.kt`, `DownloadWorker.kt`, `DownloadsViewModel.kt`)

**Exact symbols (per Codex review):** `DownloadStorage.listAllSidecarsWithScope(): List<Triple<String, String, DownloadSidecar>>` (the two strings are `serverId`, `profileId`). Download fields live under **`sidecar.record.*`** (`record: DownloadRecord` with `id`, `contentId`, `mediaFileId`, `fileSize`, `bytesSent`, `status`); `sidecar.fileName` is **nullable**. Status is the `DownloadStatus` enum, wire values **lowercase** (`queued/downloading/completed/failed/cancelled`). `EbookLocalStateStore.listAllProgress(): List<ProgressEntry>` with a **nested** `snapshot: ProgressSnapshot`; `AudiobookPositionStore.listAll(): List<Entry>` with a **nested** `Snapshot`.

- [ ] **Step 1: Write the failing test** — given fake `Triple(serverId, profileId, DownloadSidecar)` records and scoped-JSON `ProgressEntry`/`Entry` items, `LegacyImporter.run()` upserts Room rows keyed by `(serverId, profileId, record.mediaFileId)`, records `legacy_imports`, is **idempotent** (second run inserts nothing new), validates bytes (missing → status `"stale"`, not deleted), and creates outbox rows for local state newer than the last server projection.

- [ ] **Step 2–3:** Run-fail, then implement `LegacyImporter`: read `listAllSidecarsWithScope()` (map `sidecar.record.*` + nullable `sidecar.fileName` → `DownloadEntity`), scoped JSON via `listAllProgress()`/`listAll()` (read the **nested** `snapshot`/`Snapshot`); add import-only walkers for ebook + audiobook **bookmarks** which lack global enumerators (`AudiobookBookmarksStore`/`EbookLocalStateStore` expose only scoped reads). Validate bytes via `DownloadStorage.locateLocalMedia`/`exists`. Write `LegacyImportEntity` (path+hash+mtime) so reruns skip unchanged. **Never delete legacy files.**

- [ ] **Step 4: Dual-write + Room-first-read cutover** — Room-backed repos read Room first, fall back to legacy on miss and backfill in the same transaction; download enqueue/complete/fail/delete **write both** Room and the sidecar/JSON. Add a `// Track B dual-write` comment at each site.

- [ ] **Step 5: run tests; commit.**

```bash
git commit -m "Migration: idempotent legacy import (sidecars + scoped JSON) + dual-write boundary"
```

---

## Task 7: Verification — offline→online round-trip + migration equivalence

- [ ] **Step 1: Offline round-trip (the success criterion)** — install debug, go offline (airplane mode), change position/watched/rating on a title, confirm UI reflects it (Room read), go online, confirm the `SyncWorker` drains the outbox and the server reflects the change; reopen on another client/profile and confirm continuity. Record in a findings note.

- [ ] **Step 2: Migration equivalence** — on a build with existing downloads + reading/audiobook progress, confirm `LegacyImporter` produced a Room row per valid legacy record, completed downloads resolve to real bytes, and no import-created outbox rows remain unsynced. Confirm legacy files are untouched.

- [ ] **Step 3: Commit the findings note**

```bash
git add docs/superpowers/notes/2026-06-16-track-b-offline-roundtrip-findings.md
git commit -m "Track B findings: offline round-trip + migration equivalence verified"
```

---

## Self-review notes (revised per Codex Track B plan review)
- Pure-logic deliverables (outbox coalescing, conflict policy) and Room DAO behavior (Robolectric in-memory) are full failing-test-first TDD.
- **Corrections folded in from Codex's review** (all symbol claims verified against the tree): KSP uses the **`kspAndroid`** config (KMP module) + Robolectric/androidx-test-core added to `androidUnitTest`; DI override is in **`androidModule`/`androidTvModule`** after `sharedModules()` (not `RepositoryModule`), and because the concrete repos are **final**, migrated **consumers are repointed at `UserItemStatePort`** (a rebind alone can't intercept); the **outbox is limited to the four server-supported ops** (`SET_POSITION` via `syncProgress`, `SET_WATCHED`, `SET_RATING`, `SET_FAVORITE`); **`SET_TRACK_SELECTION` is local-only** (no server API) and **CFI stays with the existing `EbookProgressSyncer`**; sync resolves conflicts on the **read/refresh path** (mutations return `Unit`), using real method names and lowercase `DownloadStatus` wire values + `sidecar.record.*`.
- Integration is implemented against the **existing** stores (`DownloadStorage.listAllSidecarsWithScope: List<Triple<String,String,DownloadSidecar>>`, `EbookLocalStateStore.listAllProgress: List<ProgressEntry>`, `AudiobookPositionStore.listAll: List<Entry>`, `EbookProgressSyncer`, `DownloadsViewModel` boot path) and proven by Task 7 device verification.
- Scope held to browse + resume + downloads + user-state; other repositories stay network-first.
- Server-contract risk (per-field `updated_at`/idempotency; no track-selection projection API) is explicit with a graceful-degrade path; any server change goes via PR, not direct push.
- Type consistency: scope key `(profileId, contentId, fileId)` and `coalesceKey` format identical across entities, outbox, conflict policy, repository.
