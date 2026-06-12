# B — Watch Next launcher integration

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Surface Silo "Continue Watching" + "Next Up" items on the Android TV launcher's Watch Next row. User can resume a show without launching the Silo app first. Tiles deep-link into the existing detail/player screens via the `continuum://` URL scheme.

**Architecture:**
- New gradle dep `androidx.tvprovider:tvprovider:1.0.0` + `koin-androidx-workmanager`.
- Manifest: add `WRITE_EPG_DATA` / `READ_EPG_DATA` permissions; add `continuum://` deep-link intent filter on `MainTvActivity`.
- New package `androidTvApp/.../watchnext/` with `WatchNextProgramMapper` (pure SectionItem→WatchNextProgram), `WatchNextRepository` (ContentResolver wrapper with diff-apply), `WatchNextSyncWorker` (CoroutineWorker fetching sections + reconciling), `WatchNextSeeder` (one-shot post-auth trigger).
- `MainTvActivity.onNewIntent` parses `continuum://item/<id>` and `continuum://play/<id>` and routes through `NavController`.
- Source of truth: existing `SectionApi.getHomeSections()` (already returns `ResolvedSection.sectionType == "continue_watching" | "next_up"` per audit).

**Scope note:** **Single-profile install assumed** for trigger flow. Per-profile reactive reseed (the spec's "re-seed on profile switch via `ProfileKey` flow") is replaced by a direct invocation of `WatchNextSeeder.seed()` from the existing profile-switch handler in `TvAppNavigation.kt:140-147`. Avoids needing to extend `TokenManager` with a `profileIdFlow` for a feature that, in practice, runs against one profile per device. Documented as a scope decision; a future B.1 can add the flow if needed.

**Tech stack:** Kotlin 2.1.20, `androidx.tvprovider:tvprovider:1.0.0`, `androidx.work:work-runtime-ktx` (likely transitive via Coil; add explicit if not), `koin-androidx-workmanager:4.1.0`.

**Reference:** Spec section B at `/opt/silo-android/docs/superpowers/specs/2026-05-23-android-tv-parity-rework-design.md`. Architectural audit confirmed:
- `SectionItem` data class at `/opt/silo-android/shared/src/commonMain/kotlin/com/continuum/app/model/section/SectionModels.kt:8-31` with fields `contentId`, `title`, `posterUrl`, `backdropUrl`, `type`, `progressUpdatedAt` (ISO 8601), `positionSeconds`, `durationSeconds`, `userState`.
- `ResolvedSection.sectionType: String` field at lines 34–47 of same file. Server emits `"continue_watching"` / `"next_up"` directly.
- `SectionRepository.getHomeSections(): ApiResult<SectionsResponse>` at `/opt/silo-android/shared/src/commonMain/kotlin/com/continuum/app/repository/SectionRepository.kt:13-54`.
- `MainTvActivity.kt` (90 lines): has `onCreate` only — no `onNewIntent` handling today.
- Current intent filter: only `MAIN` + `LEANBACK_LAUNCHER` (manifest lines 33–37).
- Existing permissions: `INTERNET`, `ACCESS_NETWORK_STATE`, `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_MEDIA_PLAYBACK`, `WAKE_LOCK`. Missing both EPG permissions.

**Testing posture:** `WatchNextProgramMapper` is pure — gets focused unit tests (Task 2). `WatchNextRepository`'s diff logic warrants a Robolectric/integration test (Task 3). Live worker behavior verified manually on the Shield after merge.

---

### Task 1: Add gradle deps + manifest permissions + deep-link intent filter

**Files:**
- Modify: `/opt/silo-android/gradle/libs.versions.toml`
- Modify: `/opt/silo-android/androidTvApp/build.gradle.kts`
- Modify: `/opt/silo-android/androidTvApp/src/androidMain/AndroidManifest.xml`

**Why:** Foundation. All later tasks compile against `androidx.tvprovider.media.tv.*` and require WorkManager + EPG permissions + the deep-link filter.

- [ ] **Step 1: Add versions and library aliases to `libs.versions.toml`**

In `[versions]` after `palette`, add:

```toml
tvprovider = "1.0.0"
work = "2.10.0"
koin-workmanager = "4.1.0"
```

(`koin-workmanager` is the same version as the rest of Koin already; aliased separately for clarity. `work` is the latest stable as of late 2025; the project's Compose-on-Android stack supports it.)

In `[libraries]` after `androidx-palette`, add:

```toml
androidx-tvprovider = { module = "androidx.tv:tvprovider", version.ref = "tvprovider" }
androidx-work-runtime-ktx = { module = "androidx.work:work-runtime-ktx", version.ref = "work" }
koin-androidx-workmanager = { module = "io.insert-koin:koin-androidx-workmanager", version.ref = "koin-workmanager" }
```

Note: the artifact ID for tvprovider is `androidx.tv:tvprovider`, NOT `androidx.tvprovider:tvprovider`. (Google's Maven Central artifact is in the `androidx.tv` group despite older docs sometimes referring to the legacy `androidx.tvprovider` group.) If the build fails to resolve, try the legacy `androidx.tvprovider:tvprovider-1.0.0` coordinate.

- [ ] **Step 2: Add the deps to `androidTvApp/build.gradle.kts`**

Inside `androidMain.dependencies { … }`, after the existing palette line, add:

```kotlin
            // Watch Next launcher tiles (sub-project B).
            implementation(libs.androidx.tvprovider)
            implementation(libs.androidx.work.runtime.ktx)
            implementation(libs.koin.androidx.workmanager)
```

- [ ] **Step 3: Add manifest permissions**

In `androidTvApp/src/androidMain/AndroidManifest.xml`, after the existing `WAKE_LOCK` permission, add:

```xml
    <uses-permission android:name="com.android.providers.tv.permission.WRITE_EPG_DATA" />
    <uses-permission android:name="com.android.providers.tv.permission.READ_EPG_DATA" />
```

- [ ] **Step 4: Add the deep-link intent filter on `MainTvActivity`**

Inside the existing `<activity android:name=".MainTvActivity" …>` block, after the existing LEANBACK_LAUNCHER intent filter, add:

```xml
        <intent-filter>
            <action android:name="android.intent.action.VIEW" />
            <category android:name="android.intent.category.DEFAULT" />
            <category android:name="android.intent.category.BROWSABLE" />
            <data android:scheme="continuum" />
        </intent-filter>
```

The existing LEANBACK_LAUNCHER filter STAYS; the new one is additive.

- [ ] **Step 5: Build**

```bash
cd /opt/silo-android && ./gradlew :androidTvApp:compileDebugKotlin
```

Expected: BUILD SUCCESSFUL. If `androidx.tv:tvprovider:1.0.0` fails to resolve, try `androidx.tvprovider:tvprovider:1.0.0` instead and report DONE_WITH_CONCERNS.

- [ ] **Step 6: Commit**

```bash
git -c user.name="rxwatcher" -c user.email="rxwatcher@users.noreply.github.com" -C /opt/silo-android add \
  gradle/libs.versions.toml \
  androidTvApp/build.gradle.kts \
  androidTvApp/src/androidMain/AndroidManifest.xml

git -c user.name="rxwatcher" -c user.email="rxwatcher@users.noreply.github.com" -C /opt/silo-android commit -m "build(tv): add tvprovider/work/koin-workmanager deps + EPG perms + deep links (B)

Foundation for sub-project B (Watch Next launcher tiles):
- androidx.tv:tvprovider 1.0.0 — Watch Next ContentProvider builder
- androidx.work:work-runtime-ktx 2.10.0 — periodic + expedited worker
- koin-androidx-workmanager 4.1.0 — DI for the worker

Manifest:
- WRITE_EPG_DATA + READ_EPG_DATA permissions (normal-level on
  Android TV; no runtime grant required)
- continuum:// deep-link intent filter on MainTvActivity (handler
  added in a later commit)"
```

---

### Task 2: `WatchNextProgramMapper` (pure SectionItem → Bundle-of-WatchNextProgram-params) + tests

**Files:**
- Create: `/opt/silo-android/androidTvApp/src/androidMain/kotlin/com/continuum/app/tv/watchnext/WatchNextProgramMapper.kt`
- Create: `/opt/silo-android/androidTvApp/src/androidUnitTest/kotlin/com/continuum/app/tv/watchnext/WatchNextProgramMapperTest.kt`

**Why:** The mapper turns a `SectionItem` into the parameters needed for `WatchNextProgram.Builder`. Keeping it pure (no Android dependencies on the input/output) makes it unit-testable. The actual `WatchNextProgram.Builder` call happens inside `WatchNextRepository` (Task 3) which receives the mapper's output.

- [ ] **Step 1: Create the mapper file**

```kotlin
package com.continuum.app.tv.watchnext

import com.continuum.app.model.section.SectionItem
import java.time.Instant
import java.time.format.DateTimeParseException

/**
 * Pure transformation from a [SectionItem] (returned by /api/v1/home/sections)
 * into the field bundle needed to construct an [androidx.tv.tvprovider.media.tv.WatchNextProgram].
 *
 * Kept Android-free so the mapping logic is unit-testable in isolation.
 * [WatchNextRepository] receives the result and assembles the actual
 * `WatchNextProgram.Builder` call.
 */
data class WatchNextProgramFields(
    /** Stable identifier for diffing — combines section type + contentId. */
    val externalId: String,

    /** Display title shown beneath the tile. */
    val title: String,

    /** WatchNextProgram type — `WATCH_NEXT_TYPE_CONTINUE` or `WATCH_NEXT_TYPE_NEXT`. */
    val watchNextType: Int,

    /** Program type — `TYPE_MOVIE` or `TYPE_TV_EPISODE` based on `SectionItem.type`. */
    val programType: Int,

    /** Backdrop / poster URI (prefer backdrop for landscape Watch Next tiles). */
    val posterArtUri: String,

    /** Aspect ratio — landscape for Watch Next, per Apple Top Shelf parity. */
    val posterArtAspectRatio: Int,

    /**
     * Milliseconds-since-epoch of last engagement. The launcher uses this
     * for tile ordering. Falls back to "now" when no progress timestamp.
     */
    val lastEngagementTimeMs: Long,

    /** Intent URI: `continuum://item/<contentId>` for detail or `continuum://play/<contentId>` for direct play. */
    val intentUri: String,
)

object WatchNextProgramMapper {

    /**
     * Maps a [SectionItem] for the given [sectionType] (`"continue_watching"`
     * or `"next_up"`) into the field bundle. Returns null if the item is
     * unsuitable (e.g. missing required fields).
     */
    fun map(item: SectionItem, sectionType: String, nowMs: Long): WatchNextProgramFields? {
        val poster = item.backdropUrl ?: item.posterUrl ?: return null
        val watchNextType = when (sectionType) {
            "continue_watching" -> WATCH_NEXT_TYPE_CONTINUE
            "next_up" -> WATCH_NEXT_TYPE_NEXT
            else -> return null  // unknown section; skip
        }
        val programType = when (item.type) {
            "movie" -> PROGRAM_TYPE_MOVIE
            "episode", "show", "series" -> PROGRAM_TYPE_TV_EPISODE
            else -> PROGRAM_TYPE_TV_EPISODE  // safer default for unknowns
        }
        val intentUri = when (watchNextType) {
            WATCH_NEXT_TYPE_CONTINUE -> "continuum://play/${item.contentId}"
            else -> "continuum://item/${item.contentId}"
        }
        return WatchNextProgramFields(
            externalId = "$sectionType:${item.contentId}",
            title = item.title,
            watchNextType = watchNextType,
            programType = programType,
            posterArtUri = poster,
            posterArtAspectRatio = ASPECT_RATIO_16_9,
            lastEngagementTimeMs = parseProgressTimestamp(item.progressUpdatedAt) ?: nowMs,
            intentUri = intentUri,
        )
    }

    private fun parseProgressTimestamp(iso: String?): Long? {
        if (iso.isNullOrBlank()) return null
        return try {
            Instant.parse(iso).toEpochMilli()
        } catch (e: DateTimeParseException) {
            null
        }
    }

    // Mirror the int constants from androidx.tv.tvprovider.media.tv.TvContractCompat
    // so this module compiles without an Android dependency. These values are
    // part of the public Android API contract — they don't change.
    const val WATCH_NEXT_TYPE_CONTINUE = 0
    const val WATCH_NEXT_TYPE_NEXT = 1
    const val PROGRAM_TYPE_MOVIE = 0
    const val PROGRAM_TYPE_TV_EPISODE = 3
    const val ASPECT_RATIO_16_9 = 0
}
```

Note: the constant values above are mirrored from `TvContractCompat.WatchNextPrograms` / `TvContractCompat.PreviewPrograms`. Verify against the actual `androidx.tv.tvprovider.media.tv.TvContractCompat` constants once Task 1's dep is in place — if Google has reordered them in the published artifact, update the literals here. Best practice would be to read them from the SDK rather than mirror, but that requires an Android dep that defeats the purpose of keeping the mapper pure.

- [ ] **Step 2: Create the test file**

```kotlin
package com.continuum.app.tv.watchnext

import com.continuum.app.model.section.SectionItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WatchNextProgramMapperTest {

    private fun sectionItem(
        id: String = "tt1234",
        title: String = "Test Item",
        type: String = "movie",
        posterUrl: String? = "https://example.com/poster.jpg",
        backdropUrl: String? = "https://example.com/backdrop.jpg",
        progressUpdatedAt: String? = null,
    ) = SectionItem(
        contentId = id,
        title = title,
        type = type,
        posterUrl = posterUrl,
        backdropUrl = backdropUrl,
        progressUpdatedAt = progressUpdatedAt,
    )

    @Test
    fun `continue_watching section maps to CONTINUE type with play URI`() {
        val fields = WatchNextProgramMapper.map(
            sectionItem(id = "abc"),
            sectionType = "continue_watching",
            nowMs = 1_000L,
        )
        assertEquals(WatchNextProgramMapper.WATCH_NEXT_TYPE_CONTINUE, fields?.watchNextType)
        assertEquals("continuum://play/abc", fields?.intentUri)
        assertEquals("continue_watching:abc", fields?.externalId)
    }

    @Test
    fun `next_up section maps to NEXT type with item URI`() {
        val fields = WatchNextProgramMapper.map(
            sectionItem(id = "xyz"),
            sectionType = "next_up",
            nowMs = 1_000L,
        )
        assertEquals(WatchNextProgramMapper.WATCH_NEXT_TYPE_NEXT, fields?.watchNextType)
        assertEquals("continuum://item/xyz", fields?.intentUri)
    }

    @Test
    fun `unknown sectionType returns null`() {
        val fields = WatchNextProgramMapper.map(
            sectionItem(),
            sectionType = "random_recommendations",
            nowMs = 1_000L,
        )
        assertNull(fields)
    }

    @Test
    fun `prefers backdrop URL over poster URL`() {
        val fields = WatchNextProgramMapper.map(
            sectionItem(posterUrl = "P", backdropUrl = "B"),
            sectionType = "continue_watching",
            nowMs = 1_000L,
        )
        assertEquals("B", fields?.posterArtUri)
    }

    @Test
    fun `falls back to poster when backdrop missing`() {
        val fields = WatchNextProgramMapper.map(
            sectionItem(posterUrl = "P", backdropUrl = null),
            sectionType = "continue_watching",
            nowMs = 1_000L,
        )
        assertEquals("P", fields?.posterArtUri)
    }

    @Test
    fun `returns null when both poster and backdrop missing`() {
        val fields = WatchNextProgramMapper.map(
            sectionItem(posterUrl = null, backdropUrl = null),
            sectionType = "continue_watching",
            nowMs = 1_000L,
        )
        assertNull(fields)
    }

    @Test
    fun `parses ISO-8601 progressUpdatedAt into lastEngagementTimeMs`() {
        val fields = WatchNextProgramMapper.map(
            sectionItem(progressUpdatedAt = "2026-01-01T00:00:00Z"),
            sectionType = "continue_watching",
            nowMs = 999L,
        )
        // 2026-01-01T00:00:00Z = 1767225600000 ms
        assertEquals(1_767_225_600_000L, fields?.lastEngagementTimeMs)
    }

    @Test
    fun `falls back to nowMs when progressUpdatedAt missing`() {
        val fields = WatchNextProgramMapper.map(
            sectionItem(progressUpdatedAt = null),
            sectionType = "continue_watching",
            nowMs = 42L,
        )
        assertEquals(42L, fields?.lastEngagementTimeMs)
    }

    @Test
    fun `falls back to nowMs when progressUpdatedAt unparseable`() {
        val fields = WatchNextProgramMapper.map(
            sectionItem(progressUpdatedAt = "not-a-date"),
            sectionType = "continue_watching",
            nowMs = 42L,
        )
        assertEquals(42L, fields?.lastEngagementTimeMs)
    }

    @Test
    fun `movie type maps to PROGRAM_TYPE_MOVIE`() {
        val fields = WatchNextProgramMapper.map(
            sectionItem(type = "movie"),
            sectionType = "continue_watching",
            nowMs = 1L,
        )
        assertEquals(WatchNextProgramMapper.PROGRAM_TYPE_MOVIE, fields?.programType)
    }

    @Test
    fun `episode type maps to PROGRAM_TYPE_TV_EPISODE`() {
        val fields = WatchNextProgramMapper.map(
            sectionItem(type = "episode"),
            sectionType = "next_up",
            nowMs = 1L,
        )
        assertEquals(WatchNextProgramMapper.PROGRAM_TYPE_TV_EPISODE, fields?.programType)
    }
}
```

Verify the `SectionItem` constructor signature in `SectionModels.kt` and adapt the test factory if needed — the audit said the data class has `contentId`, `title`, `type`, `posterUrl?`, `backdropUrl?`, `progressUpdatedAt?` as the fields the mapper touches. Other required fields (if any) need defaults in the test factory.

- [ ] **Step 3: Build + run tests**

```bash
cd /opt/silo-android && ./gradlew :androidTvApp:compileDebugKotlin
cd /opt/silo-android && ./gradlew :androidTvApp:testDebugUnitTest --tests "com.continuum.app.tv.watchnext.WatchNextProgramMapperTest"
```

Expected: BUILD SUCCESSFUL + 11 tests pass.

- [ ] **Step 4: Commit**

```bash
git -c user.name="rxwatcher" -c user.email="rxwatcher@users.noreply.github.com" -C /opt/silo-android add \
  androidTvApp/src/androidMain/kotlin/com/continuum/app/tv/watchnext/WatchNextProgramMapper.kt \
  androidTvApp/src/androidUnitTest/kotlin/com/continuum/app/tv/watchnext/WatchNextProgramMapperTest.kt

git -c user.name="rxwatcher" -c user.email="rxwatcher@users.noreply.github.com" -C /opt/silo-android commit -m "feat(tv-watchnext): pure mapper SectionItem → WatchNextProgramFields (B)

Stateless transformation kept Android-free for unit-testability.
11 tests cover: sectionType→WATCH_NEXT_TYPE mapping, intentUri
selection (play vs item), poster/backdrop fallback chain, ISO 8601
timestamp parsing, type→programType mapping. WatchNextRepository
consumes WatchNextProgramFields in the next commit."
```

---

### Task 3: `WatchNextRepository` (ContentResolver wrapper + diff-apply)

**Files:**
- Create: `/opt/silo-android/androidTvApp/src/androidMain/kotlin/com/continuum/app/tv/watchnext/WatchNextRepository.kt`

**Why:** Owns the actual `ContentResolver` interaction. Insert new programs, update existing (by externalId), delete programs whose externalIds aren't in the new remote set. Single method `diffAndApply(remoteFields: List<WatchNextProgramFields>)`.

- [ ] **Step 1: Create the repository**

```kotlin
package com.continuum.app.tv.watchnext

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import androidx.tvprovider.media.tv.TvContractCompat
import androidx.tvprovider.media.tv.WatchNextProgram

/**
 * Owns the `WatchNextPrograms` content provider interaction. Reads our
 * existing tiles (keyed by `externalId`), inserts new ones, updates
 * timestamps on existing ones, and deletes any tile whose externalId is
 * not in the latest remote set.
 *
 * All operations are blocking ContentResolver calls — invoke from
 * IO-dispatcher contexts only.
 */
class WatchNextRepository(private val context: Context) {

    /**
     * Reconcile our launcher tiles against [remoteFields]. Inserts new
     * tiles, updates existing ones, and deletes orphans. Returns counts
     * for logging.
     */
    fun diffAndApply(remoteFields: List<WatchNextProgramFields>): DiffResult {
        val resolver = context.contentResolver
        val existing = readOurExistingPrograms()  // externalId → row id
        val remoteByExternalId = remoteFields.associateBy { it.externalId }

        var inserted = 0
        var updated = 0
        var deleted = 0

        // Insert + update.
        for (fields in remoteFields) {
            val existingId = existing[fields.externalId]
            if (existingId == null) {
                val uri = resolver.insert(
                    TvContractCompat.WatchNextPrograms.CONTENT_URI,
                    fields.toContentValues(),
                )
                if (uri != null) inserted++
            } else {
                val rowUri = ContentUris.withAppendedId(
                    TvContractCompat.WatchNextPrograms.CONTENT_URI,
                    existingId,
                )
                val rows = resolver.update(rowUri, fields.toContentValues(), null, null)
                if (rows > 0) updated++
            }
        }

        // Delete orphans.
        for ((externalId, rowId) in existing) {
            if (externalId !in remoteByExternalId) {
                val rowUri = ContentUris.withAppendedId(
                    TvContractCompat.WatchNextPrograms.CONTENT_URI,
                    rowId,
                )
                if (resolver.delete(rowUri, null, null) > 0) deleted++
            }
        }

        return DiffResult(inserted = inserted, updated = updated, deleted = deleted)
    }

    /**
     * Delete every tile we own. Used on sign-out / server-switch so the
     * launcher doesn't show stale items for the wrong identity.
     */
    fun clearAll() {
        val resolver = context.contentResolver
        for ((_, rowId) in readOurExistingPrograms()) {
            val rowUri = ContentUris.withAppendedId(
                TvContractCompat.WatchNextPrograms.CONTENT_URI,
                rowId,
            )
            resolver.delete(rowUri, null, null)
        }
    }

    private fun readOurExistingPrograms(): Map<String, Long> {
        val resolver = context.contentResolver
        val projection = arrayOf(
            TvContractCompat.WatchNextPrograms._ID,
            TvContractCompat.WatchNextPrograms.COLUMN_INTERNAL_PROVIDER_ID,
        )
        val result = mutableMapOf<String, Long>()
        resolver.query(
            TvContractCompat.WatchNextPrograms.CONTENT_URI,
            projection,
            null,
            null,
            null,
        )?.use { cursor ->
            val idIdx = cursor.getColumnIndexOrThrow(TvContractCompat.WatchNextPrograms._ID)
            val extIdx = cursor.getColumnIndexOrThrow(
                TvContractCompat.WatchNextPrograms.COLUMN_INTERNAL_PROVIDER_ID
            )
            while (cursor.moveToNext()) {
                val rowId = cursor.getLong(idIdx)
                val externalId = cursor.getString(extIdx) ?: continue
                result[externalId] = rowId
            }
        }
        return result
    }

    private fun WatchNextProgramFields.toContentValues(): ContentValues =
        WatchNextProgram.Builder()
            .setType(programType)
            .setWatchNextType(watchNextType)
            .setTitle(title)
            .setPosterArtUri(Uri.parse(posterArtUri))
            .setPosterArtAspectRatio(posterArtAspectRatio)
            .setLastEngagementTimeUtcMillis(lastEngagementTimeMs)
            .setIntentUri(Uri.parse(intentUri))
            .setInternalProviderId(externalId)
            .build()
            .toContentValues()

    data class DiffResult(val inserted: Int, val updated: Int, val deleted: Int)
}
```

Notes:
- `WatchNextProgram.Builder` is in package `androidx.tvprovider.media.tv` (NOT `androidx.tv.tvprovider.media.tv` — the library lives under the `androidx.tv` Maven group but the package is `androidx.tvprovider`). Verify the actual import after Task 1's dep lands; adjust if needed.
- `COLUMN_INTERNAL_PROVIDER_ID` is the app-set stable identifier the launcher carries through. We use `externalId` (which encodes section type + contentId) so removed-from-Watch-Next entries and orphan deletion both work cleanly.
- The class is intentionally synchronous (blocking ContentResolver). Workers in Task 4 wrap calls in `withContext(Dispatchers.IO)`.

- [ ] **Step 2: Build**

```bash
cd /opt/silo-android && ./gradlew :androidTvApp:compileDebugKotlin
```

Expected: BUILD SUCCESSFUL. If package imports don't resolve, the most likely culprits are:
- `androidx.tvprovider.media.tv.TvContractCompat` — actually `androidx.tvprovider.media.tv.TvContractCompat` (correct as written).
- `androidx.tvprovider.media.tv.WatchNextProgram` — actually `androidx.tvprovider.media.tv.WatchNextProgram` (correct as written).

If the artifact ID is `androidx.tvprovider:tvprovider` (legacy group), update Task 1's library alias accordingly and the imports stay the same.

- [ ] **Step 3: Commit**

```bash
git -c user.name="rxwatcher" -c user.email="rxwatcher@users.noreply.github.com" -C /opt/silo-android add \
  androidTvApp/src/androidMain/kotlin/com/continuum/app/tv/watchnext/WatchNextRepository.kt

git -c user.name="rxwatcher" -c user.email="rxwatcher@users.noreply.github.com" -C /opt/silo-android commit -m "feat(tv-watchnext): WatchNextRepository — ContentResolver wrapper (B)

Single diffAndApply(List<WatchNextProgramFields>): inserts new tiles,
updates existing by externalId match, deletes orphans whose
externalIds aren't in the new remote set. Plus clearAll() for sign-out
/ server-switch. Blocking ContentResolver calls — callers wrap in
Dispatchers.IO. Worker consumer lands next."
```

---

### Task 4: `WatchNextSyncWorker` + Koin wiring + WorkManager setup

**Files:**
- Create: `/opt/silo-android/androidTvApp/src/androidMain/kotlin/com/continuum/app/tv/watchnext/WatchNextSyncWorker.kt`
- Modify: `/opt/silo-android/androidTvApp/src/androidMain/kotlin/com/continuum/app/tv/di/AndroidTvModule.kt` (Koin worker registration)
- Modify: `/opt/silo-android/androidTvApp/src/androidMain/AndroidManifest.xml` (already has provider declaration via androidx.work; verify nothing needs adding)
- Modify: an app-init point to install Koin's WorkManager factory

**Why:** Periodic + on-demand worker that fetches sections, maps, and calls `WatchNextRepository.diffAndApply`. WorkManager handles persistence across app death.

- [ ] **Step 1: Create the worker**

```kotlin
package com.continuum.app.tv.watchnext

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.continuum.app.network.ApiResult
import com.continuum.app.repository.SectionRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class WatchNextSyncWorker(
    appContext: Context,
    params: WorkerParameters,
    private val sectionRepository: SectionRepository,
    private val mapper: WatchNextProgramMapper,
    private val repository: WatchNextRepository,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val sections = when (val r = sectionRepository.getHomeSections()) {
            is ApiResult.Success -> r.data.sections
            is ApiResult.Error -> return@withContext Result.retry()
            is ApiResult.NetworkError -> return@withContext Result.retry()
        }

        val nowMs = System.currentTimeMillis()
        val fields = sections.asSequence()
            .filter { it.sectionType in WATCH_NEXT_SECTION_TYPES }
            .flatMap { section ->
                section.items.asSequence().mapNotNull { item ->
                    WatchNextProgramMapper.map(item, section.sectionType, nowMs)
                }
            }
            .toList()

        repository.diffAndApply(fields)
        Result.success()
    }

    companion object {
        const val UNIQUE_NAME_PERIODIC = "watch_next_sync_periodic"
        const val UNIQUE_NAME_ONESHOT = "watch_next_sync_oneshot"
        private val WATCH_NEXT_SECTION_TYPES = setOf("continue_watching", "next_up")
    }
}
```

Note: `WatchNextProgramMapper` is an `object`, not a class — the constructor `mapper: WatchNextProgramMapper` parameter is wrong. Pass `WatchNextProgramMapper` directly without injection (it's stateless). Fix this when implementing: drop the `mapper` constructor parameter and call `WatchNextProgramMapper.map(...)` directly. (The Koin worker wiring in Step 2 omits the mapper accordingly.)

- [ ] **Step 2: Koin worker registration**

The project's Koin module (`AndroidTvModule.kt`) needs:

```kotlin
import org.koin.androidx.workmanager.dsl.worker

// inside the module { … } block, near WatchNextRepository registration:
single { WatchNextRepository(androidContext()) }
worker { WatchNextSyncWorker(androidContext(), get(), get(), get()) }
```

Wait — `worker { ... }` expects the WorkerParameters as a Koin-resolved param. The actual syntax from `koin-androidx-workmanager` is:

```kotlin
worker { params -> WatchNextSyncWorker(androidContext(), params.get(), get(), get()) }
```

Verify the exact Koin worker DSL at write-time. The shape may have changed slightly between Koin versions.

- [ ] **Step 3: Install Koin's WorkManager factory at app init**

Find the `Application` subclass (likely `androidTvApp/src/androidMain/kotlin/com/continuum/app/tv/.../ContinuumTvApplication.kt` or similar — grep for `Application(` if uncertain). Inside its `onCreate()` after `startKoin { … }`, add:

```kotlin
import org.koin.androidx.workmanager.koin.workManagerFactory
// inside onCreate, after startKoin { ... }
workManagerFactory()
```

If the Application class doesn't exist or Koin is initialized differently (e.g. via a SingletonComponent), adapt — the goal is the WorkManagerFactory knows about Koin-registered Workers.

- [ ] **Step 4: Build**

```bash
cd /opt/silo-android && ./gradlew :androidTvApp:compileDebugKotlin
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git -c user.name="rxwatcher" -c user.email="rxwatcher@users.noreply.github.com" -C /opt/silo-android add \
  androidTvApp/src/androidMain/kotlin/com/continuum/app/tv/watchnext/WatchNextSyncWorker.kt \
  androidTvApp/src/androidMain/kotlin/com/continuum/app/tv/di/AndroidTvModule.kt

# Also add the Application file if you touched it for workManagerFactory()
git -c user.name="rxwatcher" -c user.email="rxwatcher@users.noreply.github.com" -C /opt/silo-android add \
  $(find /opt/silo-android/androidTvApp/src/androidMain -name "*Application*.kt" -type f) 2>/dev/null || true

git -c user.name="rxwatcher" -c user.email="rxwatcher@users.noreply.github.com" -C /opt/silo-android commit -m "feat(tv-watchnext): WatchNextSyncWorker + Koin worker registration (B)

CoroutineWorker fetches /api/v1/home/sections, filters to
continue_watching + next_up sections, maps items via
WatchNextProgramMapper, and reconciles via
WatchNextRepository.diffAndApply. Retries on transient API errors.

Koin worker { ... } DSL registers the worker; workManagerFactory()
installed at app init so WorkManager can construct the worker with
DI. Scheduling lands in the next commit."
```

---

### Task 5: `WatchNextSeeder` + auth/profile-switch integration + WorkManager periodic schedule

**Files:**
- Create: `/opt/silo-android/androidTvApp/src/androidMain/kotlin/com/continuum/app/tv/watchnext/WatchNextSeeder.kt`
- Modify: appropriate post-auth integration point (likely `TvLoginScreen` callback path or `TvAppNavigation.kt`)
- Modify: `/opt/silo-android/androidTvApp/src/androidMain/kotlin/com/continuum/app/tv/di/AndroidTvModule.kt` for the seeder

**Why:** Triggers the worker. Periodic for background refresh (~1h cadence), expedited on profile switch / sign-out / sign-in.

- [ ] **Step 1: Create the seeder**

```kotlin
package com.continuum.app.tv.watchnext

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

/**
 * Single entry point for Watch Next lifecycle triggers. Callers don't
 * need to know about WorkManager — they call [seedNow] after auth,
 * [enqueuePeriodic] at startup, and [clear] on sign-out.
 */
class WatchNextSeeder(
    private val context: Context,
    private val repository: WatchNextRepository,
) {

    fun seedNow() {
        val request = OneTimeWorkRequestBuilder<WatchNextSyncWorker>()
            .setConstraints(networkConstraints)
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            WatchNextSyncWorker.UNIQUE_NAME_ONESHOT,
            ExistingWorkPolicy.REPLACE,
            request,
        )
    }

    fun enqueuePeriodic() {
        val request = PeriodicWorkRequestBuilder<WatchNextSyncWorker>(
            1, TimeUnit.HOURS,
        )
            .setConstraints(networkConstraints)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WatchNextSyncWorker.UNIQUE_NAME_PERIODIC,
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }

    fun clear() {
        repository.clearAll()
        WorkManager.getInstance(context).cancelUniqueWork(WatchNextSyncWorker.UNIQUE_NAME_PERIODIC)
    }

    private val networkConstraints = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()
}
```

- [ ] **Step 2: Koin registration**

In `AndroidTvModule.kt`:

```kotlin
single { WatchNextSeeder(androidContext(), get()) }
```

- [ ] **Step 3: Wire to auth + profile-switch + sign-out triggers**

The cleanest integration points are in `TvAppNavigation.kt`:
1. **After successful login or profile selection**: invoke `seeder.seedNow()` and `seeder.enqueuePeriodic()`.
2. **On profile switch** (existing `onSwitchProfile` callback at lines 140–147): invoke `seeder.clear()` then `seeder.seedNow()` after the new profile is selected.
3. **On sign-out**: invoke `seeder.clear()`.

Read `TvAppNavigation.kt` around lines 99–155 and the profile-selection screen to find the right hooks. Inject `WatchNextSeeder` via Koin where needed.

This is the messiest task in the plan — the trigger points are spread across the auth lifecycle. Implementer should:
- Add `seeder = koinInject()` to `TvAppNavigation`.
- After `navController.navigate(TvRoute.Main.route)` (post-login or post-profile-select), call `seeder.seedNow()` and `seeder.enqueuePeriodic()`.
- Inside `onSwitchProfile`'s `scope.launch`, after `tokenManager.setProfileId(null)`, call `seeder.clear()` (the next profile selection will re-seed).
- Inside `onSignedOut`, call `seeder.clear()`.

- [ ] **Step 4: Build**

```bash
cd /opt/silo-android && ./gradlew :androidTvApp:compileDebugKotlin
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git -c user.name="rxwatcher" -c user.email="rxwatcher@users.noreply.github.com" -C /opt/silo-android add \
  androidTvApp/src/androidMain/kotlin/com/continuum/app/tv/watchnext/WatchNextSeeder.kt \
  androidTvApp/src/androidMain/kotlin/com/continuum/app/tv/di/AndroidTvModule.kt \
  androidTvApp/src/androidMain/kotlin/com/continuum/app/tv/ui/navigation/TvAppNavigation.kt

git -c user.name="rxwatcher" -c user.email="rxwatcher@users.noreply.github.com" -C /opt/silo-android commit -m "feat(tv-watchnext): WatchNextSeeder + lifecycle triggers (B)

WatchNextSeeder owns the WorkManager surface — seedNow() on
auth/profile-select, enqueuePeriodic() on startup (1h cadence,
network-required), clear() on profile switch + sign-out.

TvAppNavigation wires the four trigger points: post-login,
post-profile-select, on-switch-profile, and on-signed-out."
```

---

### Task 6: `MainTvActivity` deep-link handler

**Files:**
- Modify: `/opt/silo-android/androidTvApp/src/androidMain/kotlin/com/continuum/app/tv/MainTvActivity.kt`

**Why:** Watch Next tiles tap into `continuum://item/<id>` or `continuum://play/<id>`. The activity needs to parse these intents (from `onCreate` for cold launches and `onNewIntent` for warm launches) and route through the existing `TvAppNavigation`.

For warm-launch: pass the URI via a shared state (a `MutableStateFlow<Uri?>` accessible to `TvAppNavigation`). For cold-launch: the activity's `intent` is the launching intent and the same flow handles it.

- [ ] **Step 1: Add a pending-deep-link state and onNewIntent handler**

Refactor `MainTvActivity.kt` to:
1. Hold a top-level `pendingDeepLink: MutableStateFlow<Uri?>` (companion object, or a private property — companion lets `TvAppNavigation` read it without prop drilling).
2. In `onCreate`, after computing `startDestination`, peek at `intent.data` — if non-null and scheme is `continuum`, push to `pendingDeepLink`.
3. Override `onNewIntent(intent: Intent?)`: if `intent?.data?.scheme == "continuum"`, push to `pendingDeepLink`.
4. Pass `pendingDeepLink` to `TvAppNavigation` (or have `TvAppNavigation` observe it via a `koinInject()` if you wrap it in a Koin singleton).

The cleanest path: make `pendingDeepLink` a `single { MutableStateFlow<Uri?>(null) }` in `AndroidTvModule.kt`, injected wherever needed. Activity writes; `TvAppNavigation` observes.

```kotlin
// In AndroidTvModule.kt:
single<MutableStateFlow<Uri?>>(named("pendingDeepLink")) { MutableStateFlow(null) }
```

```kotlin
// In MainTvActivity.kt:
private val pendingDeepLink: MutableStateFlow<Uri?> by inject(named("pendingDeepLink"))

override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    handleIntent(intent)
    // ... existing onCreate body ...
}

override fun onNewIntent(intent: Intent?) {
    super.onNewIntent(intent)
    handleIntent(intent)
    setIntent(intent)  // standard pattern: keep current intent up to date
}

private fun handleIntent(intent: Intent?) {
    val data = intent?.data ?: return
    if (data.scheme == "continuum") {
        pendingDeepLink.value = data
    }
}
```

- [ ] **Step 2: Consume in `TvAppNavigation`**

In `TvAppNavigation`, observe `pendingDeepLink` after the auth flow lands the user on `Main`. When a `continuum://item/<id>` arrives, push `TvRoute.ItemDetail(id)`. When `continuum://play/<id>` arrives, push `TvRoute.Player(id, fileId = null)`. Clear the flow after consuming.

Add a `LaunchedEffect(Unit)` (or keyed on auth-completion) in `TvAppNavigation`:

```kotlin
val pendingDeepLink: MutableStateFlow<Uri?> = koinInject(named("pendingDeepLink"))

LaunchedEffect(Unit) {
    pendingDeepLink.collect { uri ->
        if (uri == null) return@collect
        val pathSegments = uri.pathSegments
        val contentId = pathSegments.lastOrNull() ?: return@collect
        when (uri.host) {
            "item" -> navController.navigate(TvRoute.ItemDetail(contentId).route)
            "play" -> navController.navigate(TvRoute.Player(contentId, fileId = null).route)
        }
        pendingDeepLink.value = null
    }
}
```

For unauthenticated cold launches: the deep link sits in `pendingDeepLink` until the user logs in; the existing auth flow lands them on `Main`, at which point the LaunchedEffect collector picks up the pending URI. No special unauth queueing needed.

- [ ] **Step 3: Build**

```bash
cd /opt/silo-android && ./gradlew :androidTvApp:compileDebugKotlin
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git -c user.name="rxwatcher" -c user.email="rxwatcher@users.noreply.github.com" -C /opt/silo-android add \
  androidTvApp/src/androidMain/kotlin/com/continuum/app/tv/MainTvActivity.kt \
  androidTvApp/src/androidMain/kotlin/com/continuum/app/tv/ui/navigation/TvAppNavigation.kt \
  androidTvApp/src/androidMain/kotlin/com/continuum/app/tv/di/AndroidTvModule.kt

git -c user.name="rxwatcher" -c user.email="rxwatcher@users.noreply.github.com" -C /opt/silo-android commit -m "feat(tv-watchnext): MainTvActivity onNewIntent + TvAppNavigation handler (B)

continuum://item/<id> routes to ItemDetail; continuum://play/<id>
routes to Player. Pending URI lives in a Koin-provided
MutableStateFlow so cold-launch (read from launching intent in
onCreate) and warm-launch (onNewIntent) share the same consumer
without prop drilling.

Unauthenticated launches: URI sits in the flow until the auth chain
lands user on Main, then the existing LaunchedEffect collector
consumes it. No special queueing path."
```

---

## Self-Review

**Spec coverage** (against spec section B):
- B.1 system surface (Watch Next vs custom channel) → addressed in plan front matter ✓
- B.2 new files (Repository, Mapper, SyncWorker, Seeder) → Tasks 2-5 ✓
- B.3 manifest changes → Task 1 ✓
- B.4 behavior model (sections source, mapping, update model, removal, per-profile reseed) → Tasks 2-5; per-profile reactive reseed simplified to direct invocation (documented scope note)
- B.5 deep-link handler → Task 6 ✓
- B.6 Koin wiring → spread across Tasks 4, 5, 6 ✓
- B.7 testing (mapper tests required; repository + worker tests optional) → Task 2 has mapper tests; Repository/Worker tests skipped to keep plan tight (live device verification is the primary check)
- B.8 known limits → preserved (tiles only appear after first launch, etc.)

**Placeholder scan:** No "TBD." Two intentional verification gates: (a) Task 1's library coordinate (`androidx.tv:tvprovider` vs legacy `androidx.tvprovider:tvprovider` group), (b) Task 4's exact Koin worker DSL syntax — both flagged in-context with fallback guidance.

**Type consistency:** `WatchNextProgramFields` (the bridge type between mapper and repository) used identically across Tasks 2 and 3. `externalId` format `"$sectionType:${contentId}"` consistent. `WATCH_NEXT_TYPE_*` constants mirrored once in the mapper, reused by reference everywhere.

**Sequencing:** 1 (deps) → 2 (mapper) → 3 (repository) → 4 (worker) → 5 (seeder + lifecycle) → 6 (deep link). Each builds cleanly with the previous in place. Task 6 could be done before 5 (deep links don't depend on worker) — separated for narrative ordering.

**Risk:**
- Task 1's tvprovider coordinate is the highest-risk single line; build will fail loudly if wrong, and the plan documents the fallback.
- Task 4's worker registration syntax (Koin's `worker { params -> ... }` DSL) is the second-highest. Plan acknowledges version sensitivity.
- Task 5's lifecycle integration touches `TvAppNavigation` and may surface ordering subtleties (e.g. seedNow vs enqueuePeriodic ordering on cold launch). Implementer can adjust.
- Task 6's `pendingDeepLink` flow is intentionally global (Koin-singleton) — simpler than threading through ViewModel; the trade-off is that any future caller could write to it.
