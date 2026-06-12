# Phone downloads — v1.1 rewrite (disk-as-truth)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the Downloads feature actually work offline. v1 (plan `docs/superpowers/plans/2026-05-24-phone-downloads.md`) shipped the happy online path but six concrete bugs broke the offline experience, all rooted in treating the server as the single source of truth. This rewrite flips that: **disk is the truth for what's playable offline; server is the truth for what's downloadable**.

**Bugs being fixed** (from the deep-dive 2026-05-24):

1. Server's `DELETE /downloads/{id}` is two-phase. On queued/downloading rows it just flips status to `cancelled` (server `service.go:399-414`); the row stays. Next refresh re-shows it. → "ghost downloads I deleted already."
2. Sidecar JSON is only written on completion. In-progress rows and legacy `.bin` files from before the sidecar landed have no sidecar → disk seed returns empty → offline boot shows "Nothing downloaded" with 2 GB on disk.
3. Stale sidecars become permanent ghosts because `removeDownload` only deletes the sidecar when the in-memory record is still present.
4. `tryLocalPlayback` walks the in-memory records list, which (because of Bug 2) is empty offline → player falls through to server path → "can't find server."
5. `DownloadsRepository.refresh()` hard-overwrites the cache, instantly undoing any client-side optimistic removal.
6. Empty-state copy says "Nothing downloaded yet" even when bytes exist on disk.

**Non-goals**: server-side change to the cancel/delete semantics; resume of partial downloads; LRU eviction; per-profile fanout beyond the active profile.

---

### Task 1: Sidecar always-write + legacy backfill + metadata stash + merge refresh

**Files:**
- Modify: `android-shared/.../downloads/DownloadStorage.kt`
- Modify: `android-shared/.../downloads/DownloadWorker.kt`
- Modify: `android-shared/.../downloads/DownloadEnqueuer.kt`
- Modify: `shared/commonMain/.../model/download/DownloadModels.kt` (extend sidecar shape — keep wire shape backward-compatible)
- Modify: `shared/commonMain/.../repository/DownloadsRepository.kt`
- Modify: `androidApp/.../ui/screens/downloads/DownloadsViewModel.kt`

**Why:** The disk needs to be a complete record of every download the user owns — title, poster, status, all in one JSON sidecar next to the bytes. That solves Bugs 2, 5, and partially 6.

- [ ] **Step 1: Extend the sidecar model.** Introduce a new `DownloadSidecar` data class (NOT the wire `DownloadRecord`) in `shared/commonMain/.../model/download/DownloadSidecar.kt`. Shape:

  ```kotlin
  @Serializable
  data class DownloadSidecar(
      val record: DownloadRecord,
      val title: String,
      val subtitle: String? = null,
      val posterUrl: String? = null,
      val posterThumbhash: String? = null,
      val year: Int? = null,
      val seriesTitle: String? = null,
      val seasonNumber: Int? = null,
      val episodeNumber: Int? = null,
      // Wall-clock time we last touched this sidecar — used to debug stale files.
      val updatedAtMs: Long,
  )
  ```

  Keep `DownloadRecord` untouched (it's the wire shape; can't add fields without breaking the API contract).

- [ ] **Step 2: Storage gains sidecar read/write/list/delete.**

  ```kotlin
  fun writeSidecar(serverId: String, profileId: String, sidecar: DownloadSidecar)
  fun readSidecar(serverId: String, profileId: String, fileId: Int): DownloadSidecar?
  fun deleteSidecar(serverId: String, profileId: String, fileId: Int): Boolean
  fun listAllSidecars(): List<DownloadSidecar>            // walk + parse
  fun listOrphanBinFiles(): List<File>                    // .bin without sibling .record.json
  ```

  Replace existing `recordFile` / `writeRecord` / `loadAllRecords` with the new sidecar versions. Path layout unchanged — same `<server>/<profile>/<fileId>.record.json`.

- [ ] **Step 3: Enqueuer captures metadata at start.** `DownloadEnqueuer.start(...)` already takes `displayTitle`. Add a `catalogRepository.getItemDetail(contentId)` lookup at start, then `storage.writeSidecar(...)` with the server record + the catalog fields (title/subtitle/posterUrl/etc.). Best-effort: if `getItemDetail` fails, fall back to a sidecar with just `title = displayTitle`. The sidecar exists from the moment the download is enqueued.

- [ ] **Step 4: Worker rewrites the sidecar on every status change.** Replace the single `writeRecord` call in the success branch with three writes:
  - On `doWork` entry (status = downloading)
  - In the progress block (status = downloading, updated bytesSent/fileSize) — same cadence as the existing setProgress, no extra IO budget needed
  - On success (status = completed)
  - On failure (status = failed)
  - On cancellation: the worker's coroutine gets cancelled; the sidecar should be left as-is so the user can see "Failed" state if they care. (Alternative: write status=cancelled in a `finally` block; do this if the test surfaces "frozen at downloading" rows.)

- [ ] **Step 5: One-shot legacy backfill.** New `DownloadStorage.backfillLegacyBins(activeServerId: String, activeProfileId: String): Int` — walks `listOrphanBinFiles()`, for each one synthesises a stub sidecar:

  ```kotlin
  DownloadSidecar(
      record = DownloadRecord(
          id = "local_${fileId}",
          contentId = "unknown_${fileId}",
          mediaFileId = fileId,
          fileSize = file.length(),
          bytesSent = file.length(),
          kind = "queued",
          status = "completed",
          createdAt = file.lastModified().toIso(),
          completedAt = file.lastModified().toIso(),
      ),
      title = "Downloaded video",
      updatedAtMs = System.currentTimeMillis(),
  )
  ```

  Call this once per app launch from `DownloadsViewModel.init` (cheap; no-op when no orphans). Recovers the existing 2.5 GB file the user already has.

- [ ] **Step 6: Repository: merge instead of overwrite.** Replace `refresh()`'s hard `_records.value = …` with:

  ```kotlin
  suspend fun refresh(): ApiResult<Unit> = when (val r = api.list()) {
      is ApiResult.Success -> {
          val server = r.data.downloads.associateBy { it.id }
          val merged = _records.value.associateBy { it.id } + server
          // Drop pendingDelete ids unconditionally.
          val final = merged.values.filterNot { it.id in pendingDelete.value }
          _records.value = final
          ApiResult.Success(Unit)
      }
      // …Error / NetworkError unchanged
  }
  ```

  Plus a `private val pendingDelete = MutableStateFlow<Set<String>>(emptySet())` that T2 will use.

- [ ] **Step 7: Repository: hydrate from sidecars on first observe.** Add `fun seedFromSidecars(sidecars: List<DownloadSidecar>)` that upserts every sidecar's `record` into the in-memory list. Called by `DownloadsViewModel.init` before the first refresh.

- [ ] **Step 8: ViewModel uses sidecar metadata directly.** Stop calling `catalogRepository.getItemDetail` from the ViewModel. Instead, when building `DownloadItem`, pull title/poster/subtitle from the sidecar's stashed fields. Online → server record's status wins; offline → sidecar values are all we have.

- [ ] **Step 9: Build + commit.**

```bash
cd /opt/silo-android && ./gradlew :shared:compileDebugKotlinAndroid :android-shared:compileDebugKotlinAndroid :androidApp:compileDebugKotlin

git -c user.name="rxwatcher" -c user.email="rxwatcher@users.noreply.github.com" -C /opt/silo-android commit -m "feat(downloads): disk-as-truth — sidecar always-write + backfill + merge refresh (T1)

(message body summarising the design + which bugs from the deep-dive
this closes)"
```

---

### Task 2: Two-phase delete + pendingDelete set

**Files:**
- Modify: `shared/commonMain/.../repository/DownloadsRepository.kt`
- Modify: `androidApp/.../ui/screens/downloads/DownloadsViewModel.kt`

**Why:** Server cancels active downloads in one DELETE call but only marks them `cancelled`; takes a second DELETE to actually remove. Without this the UI keeps re-fetching ghosts. Fixes Bugs 1 and 3.

- [ ] **Step 1: Repository: pendingDelete state + two-phase delete.**

  ```kotlin
  private val _pendingDelete = MutableStateFlow<Set<String>>(emptySet())

  suspend fun delete(id: String): ApiResult<Unit> {
      _pendingDelete.update { it + id }
      _records.update { it.filterNot { rec -> rec.id == id } }
      val first = api.delete(id)
      if (first is ApiResult.Error || first is ApiResult.NetworkError) {
          _pendingDelete.update { it - id }
          // Local removal stays; user sees row gone. Caller can choose to surface error.
          return first.asUnit()
      }
      // First DELETE succeeded → may have been a "cancel only" on an active record.
      // Refresh server, see if the id resurfaces.
      refresh()
      val survived = _records.value.any { it.id == id }
      if (survived) {
          // Second DELETE on the now-cancelled record.
          val second = api.delete(id)
          _records.update { it.filterNot { rec -> rec.id == id } }
          _pendingDelete.update { it - id }
          return second.asUnit()
      }
      _pendingDelete.update { it - id }
      return ApiResult.Success(Unit)
  }
  ```

  Helper `ApiResult<T>.asUnit()` — small extension that maps Success to Unit.

- [ ] **Step 2: ViewModel: unconditional sidecar cleanup.** `removeDownload` no longer gates the `storage.deleteSidecar(...)` call on `record != null`. We always know `fileId` from one of: in-memory record OR a separate lookup by id in the disk-sidecar list. Simpler: change repository.delete to additionally take a `(serverId, profileId, fileId)` triple OR the sidecar; or have a separate `storage.deleteEverythingForRecordId(id)` that walks sidecars looking for a match.

- [ ] **Step 3: Build + commit.**

---

### Task 3: Player offline-first checks disk, not memory

**Files:**
- Modify: `androidApp/.../ui/screens/player/PlayerViewModel.kt`
- Modify: `shared/commonMain/.../repository/DownloadsRepository.kt` (expose a sidecar lookup helper)

**Why:** `tryLocalPlayback` currently asks the in-memory records list. With Bug 2 fixed (sidecars from day one), in-memory is reliable — but for legacy backfilled records the contentId is the synthetic `unknown_<fileId>`, so contentId-keyed lookup fails. Switch the player to a disk-walk by fileId OR by the original `Player(contentId).route` argument matched against the sidecar's stored contentId.

- [ ] **Step 1: Storage: `findSidecarByContentId(contentId): DownloadSidecar?`** Walks `listAllSidecars()` and matches.

- [ ] **Step 2: `tryLocalPlayback`** uses sidecar lookup. If sidecar found and `storage.exists(...)` for its fileId, build UiState from sidecar (title, subtitle, poster, fileSize). No server call needed.

- [ ] **Step 3: Build + commit.**

---

### Task 4: Three-state empty state

**Files:**
- Modify: `androidApp/.../ui/screens/downloads/DownloadsScreen.kt`
- Modify: `androidApp/.../ui/screens/downloads/DownloadsViewModel.kt` (expose disk-byte count + online flag if not already)

**Why:** Bug 6 — "Nothing downloaded" message is wrong when bytes exist but the metadata is unknown.

- [ ] **Step 1: Three cases.**
  - Online & records empty: existing "No downloads yet" copy
  - Offline & no `.bin` files on disk: existing "You're offline / Nothing downloaded" amber copy
  - Offline & `.bin` files exist: show the list — populated from the backfilled stub sidecars — with "Downloaded video" titles. No special empty-state; the list is the answer.

- [ ] **Step 2: Build + commit.**

---

### Task 5: Manual verification on Pixel

- [ ] Tap the 2.5 GB file's row (after backfill) — it shows in the list with a generic title; airplane mode + tap → plays from disk.
- [ ] Start a fresh download — sidecar appears on disk immediately (`ls files/downloads/.../*.record.json`). Force-quit → relaunch → row still there.
- [ ] Tap delete on a downloading row — row vanishes; sidecar removed; server-side record actually gone after second DELETE.
- [ ] Toggle airplane on/off — empty state copy / list switches reactively without restart.

---

## Self-review

**Sequencing:** T1 (foundation) → T2 (delete) → T3 (player) → T4 (UX polish) → T5 (verify). T1 is the big one; everything else is small.

**Placeholders:** One: in T2 Step 2, the cleanest way to thread `(serverId, profileId, fileId)` into the unconditional sidecar cleanup is still to be decided in code. Two reasonable shapes: a `storage.deleteAllForRecordId(id)` that walks, or threading the triple through `removeDownload`. Pick at edit time.

**Risk:** Backfill on app start walks `<filesDir>/downloads/...`. Each `*.bin` file gets a `stat` + a small JSON write on first launch after deploy. Cheap; one-shot per file.

**Out of scope:** Server fix to delete-on-first-DELETE. Resume of partial bytes. LRU eviction. "Download all episodes" UI. macOS / TV downloads.
