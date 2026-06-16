# Tiered Downloads — Design

**Status:** draft for review (Codex + user). 2026-06-16.
**Goal:** A tiered (drill-down) Downloads experience + rolled-up "downloaded" indicators on detail screens, across all media types — and fix the current Downloads-tab mis-render bugs.

## 1. Taxonomy (what we tier)

Planning assumption: silo-server PR **#138 (manga)** merges; PR **#107 (unified literary works)** does **not** — so books group by existing `author`/`seriesTitle` metadata, not a "work" model.

| Media (`DownloadMediaType`) | Tiers (parent → … → leaf) | Leaf playable unit |
|---|---|---|
| Movie | *(flat)* | movie file |
| TV (`series`/`episode`) | series → season → episode | episode file |
| Manga (`type=manga` series, `type=ebook` chapters) | series → chapter | chapter file |
| Audiobook | author → [series] → book | book file |
| Ebook (incl. cbz/cbr comics) | author → [series] → book | book file |
| Music *(coming)* | artist → album → track | track file |

Manga's series→chapter is structurally identical to TV series→episode (a manga series download is just chapters grouped under a series). So one generic parent→child grouping mechanism covers all five.

## 2. Root cause of the current bugs (must-fix)

`DownloadsViewModel` builds the tab from **server `DownloadRecord`s joined to Room sidecar metadata** by `recordId`, *scoped to the active (serverId, profileId)* in `reloadSidecarMetadata`. When the join misses (scope mismatch / first-emission timing), `toItem()` degrades:
- `title = meta?.title ?: contentId` → shows raw `series-tvdb-476189`
- `resolveMediaType()` with no sidecar → **Movie** → wrong section (MOVIES)
- `scopeByFileId` miss → `locateLocalMedia` null → **"Missing file"** on a complete file
- header size from `storage.totalBytesUsed` (MediaStore RELATIVE_PATH query) → **0 B**

**Write contract (Codex):** a queued download MUST get its Room row *before* the worker can stream — today `DownloadEnqueuer` logs a sidecar-write failure and enqueues anyway, which under a Room-sourced tab creates an *invisible* download. Treat the Room write as required (fail/rollback the enqueue if it fails).

**Progress after process death (Codex):** the worker pushes byte ticks into `DownloadsRepository.records` (in-memory), not continuously into Room. The overlay is fine for live UI, but after process death progress must fall back to WorkManager progress / persisted `bytesSent` / the actual partial file size at `localUri`.

**Fix (architectural):** make the **Room `downloads` table the source of truth** for the Downloads tab. It already carries title, posterUrl, mediaType, seriesTitle, seasonNumber, episodeNumber, author, narrator, status, fileSize, localUri (post downloads→Room migration). The tab lists Room rows directly — no fragile join, correct title/type/poster always. Live byte-progress for *in-flight* rows is overlaid from `DownloadsRepository.records` (keyed by recordId) — an optional overlay, never load-bearing for identity/metadata. Server `refresh()` keeps updating Room + the in-memory records as today.

## 3. Data: what we group on (already present in `DownloadEntity`)

`DownloadEntity` already has: `mediaType`, `seriesTitle`, `seasonNumber`, `episodeNumber`, `author`, `title`, `posterUrl`, `contentId`, `mediaFileId`, `status`, `fileSize`, `localUri`. Gaps to fill at enqueue time (capture in `DownloadEnqueuer` + carry on `DownloadSidecar`/`DownloadEntity`):
- **Books:** `author` (audiobook has it; ebook needs author captured), optional book-`seriesTitle`.
- **Music (when it lands):** `artist`, `album`, `albumTitle`/`trackNumber` — new fields (additive migration).
- **Manga:** series uses `seriesTitle` = manga series title; chapter index → reuse `episodeNumber` or add `chapterIndex`. (Confirm against #138's chapter fields once merged.)

A new generic enqueue helper captures the right grouping fields per media type so the leaf row always knows its parents.

## 4. Grouping + roll-up (pure, in the ViewModel)

**Per-type grouping STRATEGIES feeding one shared `Group`/`Leaf` tree** (Codex: generic *rendering* is fine, generic *data extraction* is where it gets brittle — TV seasons, manga chapters, book series, albums have different ids/totals/ordering). Each strategy maps Room rows → tree:
- Movies → flat list of leaves.
- TV/Manga → group by **series contentId** (not title — renames/collisions) → `seasonNumber` (manga: skip the season level) → leaves ordered by episode/chapter index.
- Books → group by **author** (stable author id where available, else normalized name) → `seriesTitle?` → leaves.
- Music → group by **artist id** → **album id** → leaves ordered by track number.

`DownloadEntry.Series/Season/Single` generalize to `Group`(parent, with stable key) / `Leaf`. **Grouping keys must be stable IDs, not display titles.** This requires restoring `episodeId`/series id on the download metadata — `DownloadSidecarMapping` currently drops `episodeId`/`batchId` (round-trips as null); Phase 1 must carry the series/episode (and book-series/album) **content ids** so grouping + roll-up are rename-safe.

**Count unique content-leaves, not file rows.** `DownloadEntity` is keyed by media *file*; two downloaded versions of one episode/book must count once. Roll-up counts distinct leaf contentIds; storage/delete still operate per file row.

**Roll-up download state** (computed, not stored) — and the denominator problem (Codex, the key correction):
- Downloaded rows alone CANNOT prove a parent is *complete* (one downloaded episode would look "done"). The total `m` (expected leaves) is **not** in the downloads table.
- So: `m` comes from **loaded catalog** on detail screens (seasons/episodes/chapter list/album tracks), or a **persisted expected-set snapshot** on the Downloads tab (Phase 3). 
- Rendering rule: only show a parent **✓** or **"n of m"** when `m` is *known and complete*; otherwise show **"n downloaded"** (count only). Never imply completeness from downloaded rows alone.
- States: `Downloaded` (all m local), `Partial(n/m)` (m known), `CountOnly(n)` (m unknown), `Downloading` (any in flight).

## 5. Downloads tab UI (drill-down)

- Top level: one row per **top parent** (series / author / artist) or movie. Shows poster, title, rolled-up state ("3 of 8 episodes · 2.1 GB"), aggregate size, delete-all.
- Tap a parent → push the children (seasons / [series] / albums), same row chrome.
- Tap a leaf-parent → episodes / chapters / books / tracks — each playable, openable, deletable.
- Reuse the existing `ExpandableAggregateRow` pattern, or switch to navigation push (decide in review — expandable is less work, push reads cleaner for deep trees).
- Header/aggregate size: prefer **verified local bytes** for completed rows and `bytesSent`/partial-file size for in-flight; don't blindly sum `fileSize` (overstates queued/failed/missing/partial — Codex). Fixes the 0 B bug without overcounting.

## 6. Detail-screen indicators (roll-up)

- Movie: already done (`DetailDownloadState` → ✓).
- Episode row (`EpisodeList`): show per-episode state from the Room rows (downloaded ✓ / progress / download arrow) — replace the always-arrow + `isDownloaded=false` hardcode.
- Season header + series hero: rolled-up ✓ / "n of m".
- Books/music detail: same indicator at author/artist/album/series level.
- `ItemDetailViewModel` exposes a per-content download-state map (contentId/fileId → state) derived from Room rows; the detail composables read it.

## 7. "Missing file" correctness

A completed Room row is "missing local" only if `storage.locateLocalMedia(serverId, profileId, fileId)` is null *using the row's own scope* (not a mismatched active scope). With Room as source, the row carries its scope, so the check is reliable.

## 8. Out of scope (this slice)

- #107 literary-works "work" model (not merging) — books stay author/series grouped.
- Music end-to-end (server type is partial) — design the tier + leave fields/rendering ready; wire when music downloads exist.
- Sorting/search within downloads, storage-by-tier breakdown — later.

## 9. Testing

- Pure grouping + roll-up: unit tests over synthetic Room rows for each hierarchy (TV multi-season, manga, books by author/series, music, movies, mixed; roll-up all/partial/none).
- ViewModel: Room-sourced list + live-progress overlay; the regression that caused contentId-title / MOVIES / missing-file.
- Device: download an episode → correct section/title/poster/size + drill-down + episode ✓ on detail; series roll-up; offline.

## 10. Phased rollout (Codex-recommended)

- **Phase 1 (this slice):** Downloads tab sourced from Room rows + live-progress overlay; fix the 4 mis-render bugs; per-type grouping strategies (movie/TV/manga/books) into the shared tree; parent labels as **"n downloaded"** (no denominator yet); restore stable series/episode content ids on the metadata; guaranteed Room write before enqueue; episode/movie detail indicators from the **loaded catalog** (which knows the totals); detail delete routed through the same local delete path as the tab.
- **Phase 2:** `n of m` + ✓ roll-up on detail screens from loaded catalog (seasons/episodes/chapters/album tracks).
- **Phase 3:** persisted expected-set snapshot (download a series/season/album → record expected leaf ids) so the Downloads tab can show real completeness offline; music end-to-end when server music + downloads exist; manga series batch-download pending the server batch path (PR #138 `CreateQueuedBatch` is TV-only today).

Manga: stamp leaves as `DownloadMediaType.Manga` (new enum value) from parent context at enqueue; add a neutral `chapterIndex`/`partIndex` rather than overloading `episodeNumber`. Detail cancel/delete must clear Room + local bytes via the same download-manager path the Downloads tab uses (not just `DownloadsRepository.delete`).

Codex reviews this design (done — corrections folded in above), then the implementation diff. Device-validate on the Pixel.
