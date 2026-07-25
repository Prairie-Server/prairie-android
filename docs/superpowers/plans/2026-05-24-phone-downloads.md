# Phone offline downloads — v1

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ship working "tap to download, play offline" on the phone (`androidApp/`). Wire the existing UI scaffolding (`DownloadsScreen`, `DownloadButton`, `DownloadItemRow`) to the already-functional server download API + a WorkManager-driven local storage layer.

**Scope decision (2026-05-24, user-approved):**
- **Placement:** New 4th bottom-nav tab "Downloads" (Home / Libraries / Recommendations / Downloads). iOS doesn't ship a Downloads tab today either; both clients are building this from a stub.
- **No hard storage cap** in v1 — header shows usage; user manages capacity by deleting items.
- **Per-version downloads** — download whichever `FileVersion` the user has selected at tap time.
- **Wi-Fi-only default ON**, with a settings toggle to allow cellular.
- **No "offline mode" toggle** — v2.
- **No automatic LRU eviction** — v2.
- **No "Download all episodes"** as a single explicit affordance in v1 (server's `series: true` POST works, but UI for it is v2). Per-episode downloads only in v1.

**Server (truth-of-state):** Already shipping at `/opt/prairie-server/internal/api/handlers/downloads.go`:
- `POST /api/v1/downloads` body `{content_id, episode_id?, file_id?, series?}` → `downloadResponse`
- `GET /api/v1/downloads` → `{downloads: [downloadResponse]}`
- `DELETE /api/v1/downloads/{id}` → 204 (cancel queued or delete completed)
- `GET /api/v1/downloads/{id}/file` → file bytes (transitions queued→downloading→completed server-side)

Wire shape (verbatim mirror — `downloads.go:9-22`):
```kotlin
@Serializable
data class DownloadRecord(
    val id: String,
    @SerialName("content_id") val contentId: String,
    @SerialName("episode_id") val episodeId: String? = null,
    @SerialName("batch_id") val batchId: String? = null,
    @SerialName("media_file_id") val mediaFileId: Int,
    @SerialName("file_size") val fileSize: Long,
    @SerialName("bytes_sent") val bytesSent: Long,
    val kind: String,       // "direct" or "queued"
    val status: String,     // "queued" | "downloading" | "completed" | "failed" | "cancelled"
    @SerialName("created_at") val createdAt: String,
    @SerialName("completed_at") val completedAt: String? = null,
)
```

**Architecture:**
- `shared/commonMain` — pure model + Ktor API client + repository (sync server state into a `StateFlow<List<DownloadRecord>>`).
- `android-shared` — Android-only `DownloadStorage` (file paths + write/delete/list), `DownloadWorker` (`CoroutineWorker` that streams `/file` to disk with progress), and the DataStore-backed local mapping `fileId → localPath`.
- `androidApp` — wire `DownloadsScreen` + `DownloadsViewModel` to the repo. Wire `DownloadButton` on item detail. Add 4th nav tab. Wifi-only settings toggle. Player buildMediaItem detects local file → `file://` URI.

**Tech stack:** Kotlin 2.1, Ktor (existing), WorkManager 2.10 (already pulled in via Watch Next), DataStore preferences (already pulled in), Material 3, Compose.

**No new dependencies.** WorkManager + DataStore + Ktor are all already in `gradle/libs.versions.toml`.

**File storage layout:**
```
context.filesDir/downloads/<serverId>/<profileId>/<fileId>.<ext>
```
Per-(server, profile) scoping so profile switches don't expose someone else's bytes. `<ext>` derived from the server-supplied content type or from the MediaFile's container — v1 just uses `.bin` to keep the path stable; the player reads the bytes regardless of extension.

**Testing posture:** Pure-Kotlin model serialization tests (commonMain Json round-trip). `DownloadsRepository` sync logic gets a unit test with a fake API. `DownloadStorage` path-mapping logic gets a small unit test. UI and live download against a real server: manual verification on the Pixel.

**Reference:**
- Server handler: `/opt/prairie-server/internal/api/handlers/downloads.go`
- Server schema: `/opt/prairie-server/migrations/042_downloads.up.sql`
- Existing Android UI: `androidApp/.../ui/screens/downloads/DownloadsScreen.kt`, `DownloadsViewModel.kt`, `DownloadItemRow.kt`; `androidApp/.../ui/components/DownloadButton.kt`
- iOS stub (no functional implementation): `/opt/prairie-apple/iosApp/iosApp/Components/DownloadButton.swift`

---

### Task 1: Shared model + API client + serialization test

**Files:**
- Create: `shared/src/commonMain/kotlin/com/continuum/app/model/download/DownloadModels.kt`
- Create: `shared/src/commonMain/kotlin/com/continuum/app/network/api/DownloadsApi.kt`
- Create: `shared/src/commonTest/kotlin/com/continuum/app/model/download/DownloadRecordSerializationTest.kt`

**Why:** Mirror the server's `downloadResponse` exactly and expose Ktor wrappers. No client logic yet — just data + transport.

- [ ] **Step 1:** `DownloadModels.kt` — `DownloadRecord` data class (shape above) + helper enums `DownloadStatus` (`Queued`, `Downloading`, `Completed`, `Failed`, `Cancelled`, `Unknown`) and `DownloadKind` (`Direct`, `Queued`). Status enum has `fromWire(String): DownloadStatus` that maps to `Unknown` on miss (mirrors the device-login pattern). `DownloadRequest` data class for POST body: `contentId`, `episodeId?`, `fileId?`, `series` (default false).

- [ ] **Step 2:** `DownloadsApi.kt` — Ktor wrappers matching `SectionApi` shape:
  ```kotlin
  class DownloadsApi(private val client: HttpClient) {
      suspend fun list(): ApiResult<DownloadsListResponse> = safeApiCall {
          client.get("/api/v1/downloads")
      }
      suspend fun create(req: DownloadRequest): ApiResult<DownloadRecord> = safeApiCall {
          client.post("/api/v1/downloads") { contentType(ContentType.Application.Json); setBody(req) }
      }
      suspend fun delete(id: String): ApiResult<Unit> = safeApiCall {
          client.delete("/api/v1/downloads/$id")
      }
      // file() is NOT wrapped — the streaming download goes through OkHttp /
      // raw HttpClient in DownloadWorker so we can track byte progress.
  }
  ```
  Plus `DownloadsListResponse(downloads: List<DownloadRecord>)`.

- [ ] **Step 3:** Serialization test — 3 cases: parse the server-shape sample, parse with optional fields absent (no `episode_id`, no `completed_at`), parse status enum via `fromWire`.

- [ ] **Step 4:** Build + test.
  ```bash
  ./gradlew :shared:compileDebugKotlinAndroid :shared:testDebugUnitTest --tests "com.continuum.app.model.download.*"
  ```

- [ ] **Step 5:** Commit.

---

### Task 2: Shared `DownloadsRepository` + Koin registration

**Files:**
- Create: `shared/src/commonMain/kotlin/com/continuum/app/repository/DownloadsRepository.kt`
- Modify: `shared/src/commonMain/kotlin/com/continuum/app/di/SharedModules.kt` (or the equivalent — find via `grep -rn "DeviceLoginApi" shared/src/commonMain`)

**Why:** Single source of remote state. ViewModels + the worker subscribe to a `StateFlow<List<DownloadRecord>>` rather than polling the API directly.

- [ ] **Step 1:** `DownloadsRepository.kt`:
  ```kotlin
  class DownloadsRepository(private val api: DownloadsApi) {
      private val _records = MutableStateFlow<List<DownloadRecord>>(emptyList())
      val records: StateFlow<List<DownloadRecord>> = _records.asStateFlow()

      suspend fun refresh(): ApiResult<Unit> { /* api.list() → update _records */ }
      suspend fun create(req: DownloadRequest): ApiResult<DownloadRecord> { /* api.create → upsert into _records */ }
      suspend fun delete(id: String): ApiResult<Unit> { /* api.delete → remove from _records */ }

      // Helpers
      fun recordForFile(fileId: Int): DownloadRecord? = _records.value.firstOrNull { it.mediaFileId == fileId }
      fun upsertLocal(record: DownloadRecord) { /* used by the worker to update progress without an extra GET */ }
  }
  ```

- [ ] **Step 2:** Register in shared Koin module alongside `SectionRepository` / `CatalogRepository`.

- [ ] **Step 3:** Add `DownloadsRepositoryTest` (commonTest) — fake `DownloadsApi`, verify refresh populates state; create upserts; delete removes; recordForFile lookup.

- [ ] **Step 4:** Build + test + commit.

---

### Task 3: Android `DownloadStorage` (file paths + filesystem ops)

**Files:**
- Create: `android-shared/src/androidMain/kotlin/com/continuum/app/common/downloads/DownloadStorage.kt`
- Create: `android-shared/src/androidUnitTest/kotlin/com/continuum/app/common/downloads/DownloadStorageTest.kt`

**Why:** All filesystem reads/writes for downloaded media. Path generation + delete + list + size are pure functions over `File` — easy to test without Robolectric (use a `tmp` dir).

- [ ] **Step 1:**
  ```kotlin
  class DownloadStorage(private val baseDir: File) {

      fun localFile(serverId: String, profileId: String, fileId: Int): File =
          File(baseDir, "downloads/$serverId/$profileId/$fileId.bin")

      /** Ensures the parent directory exists; returns the target file. */
      fun prepareWrite(serverId: String, profileId: String, fileId: Int): File =
          localFile(serverId, profileId, fileId).also { it.parentFile?.mkdirs() }

      fun exists(serverId: String, profileId: String, fileId: Int): Boolean =
          localFile(serverId, profileId, fileId).exists()

      fun delete(serverId: String, profileId: String, fileId: Int): Boolean =
          localFile(serverId, profileId, fileId).delete()

      fun deleteAllForProfile(serverId: String, profileId: String): Boolean { /* rm -r the profile dir */ }

      fun totalBytesUsed(): Long { /* walk filesDir/downloads and sum */ }

      fun usableSpaceBytes(): Long = baseDir.usableSpace
  }
  ```

- [ ] **Step 2:** Koin reg in `androidApp/.../di/AndroidModule.kt`:
  `single { DownloadStorage(androidContext().filesDir) }`

- [ ] **Step 3:** Unit tests using `@TempDir`-style scratch dirs. Verify path layout, exists/delete round-trip, profile-scoped delete leaves other profiles untouched, totalBytesUsed sums correctly.

- [ ] **Step 4:** Build + test + commit.

---

### Task 4: `DownloadWorker` — streamed file download with progress

**Files:**
- Create: `android-shared/src/androidMain/kotlin/com/continuum/app/common/downloads/DownloadWorker.kt`
- Modify: TBD Koin module — register the worker via `worker { ... }` like the existing `WatchNextSyncWorker`.

**Why:** Reliable, kill-survives, foreground-service-capable download with progress reporting. WorkManager is already in the project via Watch Next.

- [ ] **Step 1:** `DownloadWorker(context, params)` extends `CoroutineWorker`. Inputs (via `Data`): `download_id` (String), `file_id` (Int), `server_id` (String), `profile_id` (String), `server_url` (String).

- [ ] **Step 2:** `doWork()`:
  1. `setForeground(makeForegroundInfo(progress = 0))` — required on Android 12+ for user-initiated WM work.
  2. Open Ktor `HttpClient` (Koin-injected, with `AuthInterceptor`) and stream `GET <serverUrl>/api/v1/downloads/<download_id>/file` with `Accept: application/octet-stream`. Use `client.prepareGet { ... }.execute { resp -> resp.bodyAsChannel() }` and copy bytes into the file at `storage.prepareWrite(...)`.
  3. Every ~200ms or every 1 MB (whichever first), call `setProgressAsync(workDataOf("bytes" to written, "total" to total))` and `setForeground(updated)`.
  4. On success: `repository.refresh()` so the UI sees `completed`. Return `Result.success(progressData)`.
  5. On `IOException` (network drop): `Result.retry()` with exponential backoff up to N tries.
  6. On any other exception: delete the partial file via `storage.delete(...)` (no resume in v1) and return `Result.failure()`.

- [ ] **Step 3:** Constraint setup helper — `enqueueDownload(...)` adds a `OneTimeWorkRequestBuilder<DownloadWorker>()` with:
  - `.setConstraints(Constraints.Builder().setRequiredNetworkType(if (wifiOnly) UNMETERED else CONNECTED).build())`
  - `.setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)` so it starts quickly when possible
  - Tag `"download_$downloadId"` so we can cancel via `WorkManager.cancelAllWorkByTag`.

- [ ] **Step 4:** `makeForegroundInfo(progress: Int): ForegroundInfo` — media-style notification with progress, title from the download record's content (via repository lookup), cancel action wired to `WorkManager.cancelAllWorkByTag("download_$id")`. Channel ID `"continuum_downloads"` — register the channel in `ContinuumApplication.onCreate`.

- [ ] **Step 5:** Manifest — already covered: `FOREGROUND_SERVICE` + `FOREGROUND_SERVICE_MEDIA_PLAYBACK` permissions exist. Add `<service android:name="androidx.work.impl.foreground.SystemForegroundService" android:foregroundServiceType="dataSync" />` if WorkManager's default isn't sufficient on API 34+ (verify via build/run).

- [ ] **Step 6:** Build (no test — worker logic is harder to unit-test usefully; manual verification on Pixel).

- [ ] **Step 7:** Commit.

---

### Task 5: Wire `DownloadsScreen` + `DownloadsViewModel`

**Files:**
- Modify: `androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/downloads/DownloadsViewModel.kt`
- Modify: `androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/downloads/DownloadsScreen.kt`
- Modify: `androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/downloads/DownloadItemRow.kt` (if it lacks needed fields)

**Why:** Replace the empty-state stub with real, server-backed data.

- [ ] **Step 1:** `DownloadsViewModel`:
  ```kotlin
  class DownloadsViewModel(
      private val repository: DownloadsRepository,
      private val storage: DownloadStorage,
      private val catalogRepository: CatalogRepository,  // for title lookups via contentId
  ) : ViewModel() {
      data class UiState(
          val active: List<UiDownloadItem> = emptyList(),
          val ready: List<UiDownloadItem> = emptyList(),
          val failed: List<UiDownloadItem> = emptyList(),
          val totalBytesUsed: Long = 0,
          val isLoading: Boolean = false,
          val error: String? = null,
      )
      // Combine repository.records + WorkManager progress + cached titles → UiState
      // On init: refresh + observe forever.
      fun removeDownload(id: String) { /* repository.delete then refresh; storage.delete */ }
  }
  ```

- [ ] **Step 2:** `DownloadsScreen` — render header card (counts + storage used), Active section (`UiDownloadItem` with progress bar), Ready section (with delete ⋮), Failed section (collapsed).

- [ ] **Step 3:** Update Koin reg for `DownloadsViewModel` (currently takes no params; add the new deps).

- [ ] **Step 4:** Build + commit.

---

### Task 6: 4th bottom-nav tab "Downloads"

**Files:**
- Modify: `androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/navigation/BottomNavBar.kt` — add `Downloads` to the `Tab` enum.
- Modify: `androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/MainScreen.kt` — add a `Tab.Downloads -> DownloadsScreen(...)` branch.
- Choose icon: `Icons.Outlined.Download` / `Icons.Filled.Download`.

- [ ] Single edit pass, build, commit.

---

### Task 7: Wire `DownloadButton` on item detail

**Files:**
- Modify: `androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/detail/ItemDetailScreen.kt` (find by `grep -l ItemDetailScreen`)
- Modify: `androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/detail/ItemDetailViewModel.kt`

**Why:** Make the existing `DownloadButton` component actually do something. The button takes the selected version + content/episode IDs and POSTs to the server, then enqueues the worker.

- [ ] **Step 1:** `ItemDetailViewModel` injects `DownloadsRepository` + (Android-only via Koin) a `DownloadEnqueuer` helper that wraps the WorkManager enqueue. New methods:
  ```kotlin
  fun onDownloadTapped(versionIndex: Int)
  fun onCancelDownloadTapped(downloadId: String)
  ```

- [ ] **Step 2:** Detail screen renders `DownloadButton` near the Play button. State derived from `repository.records` + the selected version's `fileId`. On tap → onDownloadTapped → create record + enqueue worker.

- [ ] Build + commit.

---

### Task 8: Wi-Fi-only settings toggle

**Files:**
- Modify: `androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/settings/SettingsScreen.kt` (find by `grep -l SettingsScreen`)
- Modify: `android-shared/src/androidMain/kotlin/com/continuum/app/common/settings/AndroidPlayerSettingsStore.kt` — add `downloadsWifiOnlyFlow` + `setDownloadsWifiOnly`. Per-profile semantics already in place.
- Modify: `DownloadWorker` enqueue path to read the current flow value before building the constraint.

**Why:** Surface the wifi-only choice; default ON.

- [ ] Plumb flow, expose in Settings, wire into the enqueue path. Commit.

---

### Task 9: Player integration — prefer local file when present

**Files:**
- Modify: `android-shared/src/androidMain/kotlin/com/continuum/app/common/player/ContinuumPlayerFactory.kt`
- Modify: `androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/player/PlayerScreen.kt`

**Why:** When the user taps Play on a downloaded item, play from disk instead of streaming.

- [ ] **Step 1:** `ContinuumPlayerFactory.buildMediaItem` already takes a `streamUrl`. The caller passes whatever URI it wants. Caller-side change: before constructing the MediaItem, query `DownloadStorage.localFile(serverId, profileId, fileId)` — if exists, swap to `file://${absolutePath}`. No factory change needed.

- [ ] **Step 2:** Phone `PlayerScreen` reads `DownloadStorage` (Koin-injected) and computes the URI in the existing `LaunchedEffect` that calls `buildMediaItem`. Pass the local URI if present; else the streamed one.

- [ ] **Step 3:** Document that subtitle sidecars stay remote in v1 (download-sidecars is v2). Build + commit.

---

### Task 10: Polish + manual verification

- [ ] Verify on Pixel: tap download from item detail → record appears in Downloads tab → progress updates → completed → tap → plays from disk (turn off wifi to confirm).
- [ ] Verify delete from Downloads tab removes both server record and local file.
- [ ] Verify wifi-only setting prevents start on cellular.
- [ ] Verify foreground notification shows during active download.

---

## Self-Review

**Sequencing:** T1 (model/API) → T2 (repo) → T3 (storage) → T4 (worker) → T5 (Downloads screen) → T6 (4th tab) → T7 (button wire) → T8 (settings) → T9 (player) → T10 (verify). T1→T2→T3 are pure data, parallel-shippable. T4 depends on T3. T5 depends on T2 and T4. T7 depends on T2 and T4. T9 depends on T3.

**Out of scope (v2):**
- Resume of partial downloads
- "Download all episodes" UI affordance (server-side already supports `series: true`)
- Offline-mode toggle
- LRU eviction / hard storage cap
- Subtitle sidecar download
- Charging-only constraint
- Per-profile download quota UI
- Downloaded-content thumbnails offline (we lazily fetch from server today)

**Risks:**
- Foreground service rules on Android 14+ are finicky. If `CoroutineWorker.setForeground` misbehaves on Pixel, fall back to a dedicated `Service`. Mitigation: test on real device early.
- Server `GET /downloads/{id}/file` is bandwidth-throttled per user. Worker must tolerate slow throughput; no fast timeouts.
- Local file path uses fileId as the filename. If the server's MediaFile is replaced (re-encode at same fileId), the on-disk file becomes stale and silently mismatched. v1 acceptable; v2 should include a file hash or version stamp.

**Placeholders:** Two — Koin module file name in T2 (need to find), `ItemDetailScreen` exact file path in T7 (need to find). Both surface naturally during T2/T7.
