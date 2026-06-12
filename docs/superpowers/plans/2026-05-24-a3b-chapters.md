# A.3b — Chapters wiring (server-sourced, HUD pane + scrubber ticks)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Surface per-version chapters in the HUD Chapters pane (currently a placeholder empty-state) and as tick marks on `TvPlayerScrubber`. Source the data from the existing server API — no Media3 chapter extraction, no HLS marker parsing, no local file probing.

**Source-of-truth investigation result (2026-05-24, cross-repo survey):**
- Server already returns `chapters: []VersionChapter` on `FileVersion` under the `/items/{id}` ItemDetail response. Server file: `/opt/silo-server/internal/catalog/detail.go:231` (field) + `:258-266` (struct shape). Populated during ingest via FFprobe `-show_chapters` (`/opt/silo-server/internal/scanner/probe.go:118,235`), persisted to `MediaFile.Chapters` (JSONB), normalized for overlaps and out-of-order entries.
- Apple consumes this same field at `/opt/silo-apple/iosApp/iosApp/Networking/Models.swift:537-545` (`VersionChapter`) and renders into the tvOS HUD + scrubber via `ApplePlaybackRoutePlanner.chapterInfoList(from: FileVersion)` (`/opt/silo-apple/iosApp/iosApp/Screens/Player/ApplePlaybackRoutePlanner.swift:904`).
- Android side has the scaffolding ready: `TvPlayerScrubber.ChapterInfo(timeSec, title?)` at `androidTvApp/.../TvPlayerScrubber.kt:79-82`; the scrubber accepts `chapters: List<ChapterInfo>` at `:86`; call site at `TvPlayerScreen.kt:625` passes `emptyList()`. HUD placeholder at `TvPlayerHud.kt:182`. **No `chapters` field on the Android `FileVersion` data class** (`shared/.../CatalogModels.kt:158-175`) — that's the missing link.
- Media3 1.10.0 has **no first-class chapter API** on `MediaItem.MediaMetadata` (the earlier spec hint was wrong). Server data is the only viable path, and conveniently it's also what Apple uses — exact API parity.

**Architecture:**
- Add `VersionChapter` data class + `chapters: List<VersionChapter>? = null` field to Android's `FileVersion` (`shared/commonMain`). Field shape matches the server's JSON 1:1 (snake_case via `@SerialName`).
- `TvPlayerViewModel.loadContent` already selects a `FileVersion` (around `:376`); after selection, copy `version.chapters` into `UiState.chapters` (new field, mirrors how `intro` / `credits` already live in `UiState`).
- `TvPlayerScreen` maps `UiState.chapters` → existing `TvPlayerScrubber.ChapterInfo(timeSec, title)` for tick rendering AND passes the full `List<VersionChapter>` to `TvPlayerHud` for the new pane.
- `HudChaptersPane` renders the list using `HudPickerPane` (same focus-driven row pattern as audio/subtitle pickers); each row carries the chapter title + formatted start time, with a thumbhash thumbnail when `thumbnailUrl` is present (existing `ThumbhashImage` composable at `android-shared/.../ui/components/ThumbhashImage.kt`).
- Selecting a chapter (focus-driven, like other HUD pickers) seeks the player to `startSeconds`.

**Tech stack:** Kotlin, Media3 1.10.0, existing per-profile DataStore. No new dependencies.

**Reference:**
- Spec A.3b row at `/opt/silo-android/docs/superpowers/specs/2026-05-23-android-tv-parity-rework-design.md`.
- Server model: `/opt/silo-server/internal/catalog/detail.go:258-266`.
- Apple consumer: `/opt/silo-apple/iosApp/iosApp/Networking/Models.swift:537-545`, `/opt/silo-apple/iosApp/iosApp/Screens/Player/ApplePlaybackRoutePlanner.swift:904`.
- Existing Android scaffolding: `TvPlayerScrubber.kt:79-86`, `TvPlayerHud.kt:182`.

**Testing posture:** Pure-Kotlin VersionChapter JSON deserialization gets a unit test (round-trip + snake_case mapping). HUD pane + scrubber ticks verified manually on a chapter-bearing file (MP4/MKV with embedded chapters — any normal Blu-ray rip).

---

### Task 1: Shared model — `VersionChapter` + `FileVersion.chapters` + test

**Files:**
- Modify: `/opt/silo-android/shared/src/commonMain/kotlin/com/continuum/app/model/catalog/CatalogModels.kt`
- Create: `/opt/silo-android/shared/src/commonTest/kotlin/com/continuum/app/model/catalog/VersionChapterSerializationTest.kt`

**Why:** The Android `FileVersion` is missing the field entirely; the server has been sending it. Adding the field is what flips the data path on — Apple already proves the API contract works.

- [ ] **Step 1: Add `VersionChapter` data class to `CatalogModels.kt`**

Place adjacent to `VideoTrack` / `AudioTrack` / `SubtitleTrack` (around line 196, before or after the track classes):

```kotlin
/**
 * One chapter on a playable file version. Populated server-side by FFprobe
 * during ingest (`source = "embedded"` for chapters read out of MP4/MKV
 * metadata). The server normalizes overlaps, sorts out-of-order entries,
 * strips zero-length chapters, and generates fallback titles when needed —
 * the client renders whatever it receives.
 *
 * Shape mirrors the server's `VersionChapter` struct at
 * `silo-server/internal/catalog/detail.go:258-266` and Apple's
 * `VersionChapter` at `iosApp/Networking/Models.swift:537-545` exactly.
 */
@Serializable
data class VersionChapter(
    val index: Int = 0,
    val title: String = "",
    @SerialName("start_seconds") val startSeconds: Double = 0.0,
    @SerialName("end_seconds") val endSeconds: Double = 0.0,
    val source: String? = null,
    @SerialName("thumbnail_url") val thumbnailUrl: String? = null,
    @SerialName("thumbnail_thumbhash") val thumbnailThumbhash: String? = null,
)
```

- [ ] **Step 2: Add `chapters` field to `FileVersion`**

```kotlin
@Serializable
data class FileVersion(
    @SerialName("file_id") val fileId: Int,
    // ... existing fields unchanged ...
    @SerialName("video_tracks") val videoTracks: List<VideoTrack>? = null,
    @SerialName("audio_tracks") val audioTracks: List<AudioTrack>? = null,
    @SerialName("subtitle_tracks") val subtitleTracks: List<SubtitleTrack>? = null,
    val chapters: List<VersionChapter>? = null,
)
```

Place after `subtitleTracks` so the field order matches the server's struct line order. Nullable + default `null` so existing fixture data and older server builds without the field deserialize cleanly (matches the pattern used for `videoTracks` / `audioTracks` / `subtitleTracks`).

- [ ] **Step 3: Add serialization test**

```kotlin
package com.continuum.app.model.catalog

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class VersionChapterSerializationTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `VersionChapter round-trips snake_case fields`() {
        val source = """
            {
              "index": 2,
              "title": "Chapter 3",
              "start_seconds": 412.5,
              "end_seconds": 612.0,
              "source": "embedded",
              "thumbnail_url": "/api/v1/chapters/thumb/abc.jpg",
              "thumbnail_thumbhash": "abc123"
            }
        """.trimIndent()

        val ch = json.decodeFromString<VersionChapter>(source)

        assertEquals(2, ch.index)
        assertEquals("Chapter 3", ch.title)
        assertEquals(412.5, ch.startSeconds)
        assertEquals(612.0, ch.endSeconds)
        assertEquals("embedded", ch.source)
        assertEquals("/api/v1/chapters/thumb/abc.jpg", ch.thumbnailUrl)
        assertEquals("abc123", ch.thumbnailThumbhash)
    }

    @Test
    fun `FileVersion with chapters decodes`() {
        val source = """
            {
              "file_id": 7,
              "chapters": [
                {"index": 0, "title": "Open", "start_seconds": 0.0, "end_seconds": 120.0},
                {"index": 1, "title": "Act 1", "start_seconds": 120.0, "end_seconds": 800.0}
              ]
            }
        """.trimIndent()

        val v = json.decodeFromString<FileVersion>(source)
        assertNotNull(v.chapters)
        assertEquals(2, v.chapters!!.size)
        assertEquals("Open", v.chapters!![0].title)
        assertEquals(800.0, v.chapters!![1].endSeconds)
    }

    @Test
    fun `FileVersion without chapters decodes to null`() {
        val source = """{ "file_id": 7 }"""
        val v = json.decodeFromString<FileVersion>(source)
        assertNull(v.chapters)
    }
}
```

- [ ] **Step 4: Build + test**

```bash
cd /opt/silo-android && ./gradlew :shared:compileDebugKotlinAndroid
cd /opt/silo-android && ./gradlew :shared:testDebugUnitTest --tests "com.continuum.app.model.catalog.VersionChapterSerializationTest"
```

(Confirm the test runs against the Android test variant of `:shared`. If the project uses `:shared:test` for commonTest, adjust the target — the file lives in `commonTest` so it's reachable from whichever target Android tests run under.)

- [ ] **Step 5: Commit**

```bash
git -C /opt/silo-android add \
  shared/src/commonMain/kotlin/com/continuum/app/model/catalog/CatalogModels.kt \
  shared/src/commonTest/kotlin/com/continuum/app/model/catalog/VersionChapterSerializationTest.kt

git -c user.name="rxwatcher" -c user.email="rxwatcher@users.noreply.github.com" -C /opt/silo-android commit -m "feat(shared-catalog): VersionChapter + FileVersion.chapters field (A.3b)

Mirrors silo-server's VersionChapter struct (catalog/detail.go:258-266)
and Apple's equivalent verbatim — snake_case @SerialName for
start_seconds / end_seconds / thumbnail_url / thumbnail_thumbhash.
Source field carries 'embedded' for chapters read from MP4/MKV by
FFprobe at ingest time.

FileVersion.chapters is nullable + defaults to null so existing
fixtures and pre-feature server builds deserialize cleanly. Field
ordered to match the server struct line-for-line.

Plumbing into the HUD Chapters pane + scrubber ticks lands in the
next two commits."
```

---

### Task 2: ViewModel + screen wiring (chapters → UiState → scrubber + HUD pass-through)

**Files:**
- Modify: `/opt/silo-android/androidTvApp/src/androidMain/kotlin/com/continuum/app/tv/ui/screens/player/TvPlayerViewModel.kt`
- Modify: `/opt/silo-android/androidTvApp/src/androidMain/kotlin/com/continuum/app/tv/ui/screens/player/TvPlayerScreen.kt`

**Why:** Get the data from the selected version into the screen, so the scrubber and the HUD both have a list to render. Tap-to-seek for chapter rows lives here too — the screen owns the `MediaController`.

- [ ] **Step 1: Add `chapters` to `TvPlayerViewModel.UiState`**

After the existing `credits: TimeRange? = null,` line in the `UiState` data class:

```kotlin
// Chapters from the selected FileVersion (server-extracted via FFprobe
// at ingest). null on first load until loadContent picks a version;
// empty list when the file has no embedded chapters. The HUD Chapters
// pane renders this directly; the scrubber maps the same list to its
// lightweight ChapterInfo for tick rendering.
val chapters: List<com.continuum.app.model.catalog.VersionChapter> = emptyList(),
```

(Use the FQN inline to avoid touching the existing import block in the same edit. A cleaner final pass can lift the import.)

- [ ] **Step 2: Populate `chapters` in `loadContent`**

In the `_uiState.update { it.copy(... ) }` block around the existing `intro = watchDetail.intro, credits = watchDetail.credits,` lines, add:

```kotlin
chapters = version.chapters.orEmpty(),
```

This sources from the picked `version`, NOT from `watchDetail` directly — because chapters are per-file and a single title can have a 4K version with different chapter cuts than the 1080p version (rare but possible).

- [ ] **Step 3: Add a seek handler for chapter taps**

Below the existing `onSkipIntroNow(): Double?` method:

```kotlin
/**
 * User picked a chapter row in the HUD. Returns the seek target in
 * seconds; the screen owns the MediaController and performs the seek.
 * Returns null only if the supplied index is somehow out of range.
 */
fun onSeekToChapter(chapterIndex: Int): Double? =
    _uiState.value.chapters.getOrNull(chapterIndex)?.startSeconds
```

- [ ] **Step 4: Pass chapters through `TvPlayerScreen` to the scrubber**

Map UiState.chapters to scrubber's `ChapterInfo` at the scrubber call site (currently `chapters = emptyList(),` at `TvPlayerScreen.kt:625`):

```kotlin
chapters = state.chapters.map {
    com.continuum.app.tv.ui.screens.player.TvPlayerScrubber.ChapterInfo(
        timeSec = it.startSeconds,
        title = it.title.ifBlank { null },
    )
},
```

If the FQN reference to `TvPlayerScrubber.ChapterInfo` is awkward (same package — likely just `ChapterInfo` resolves), simplify on the second pass. The functional content is the map.

- [ ] **Step 5: Pass chapters + seek handler to `TvPlayerHud`**

At the existing `TvPlayerHud(...)` call site, add two parameters to be consumed by T3:

```kotlin
chapters = state.chapters,
onSelectChapter = { idx ->
    viewModel.onSeekToChapter(idx)?.let { sec ->
        mediaController?.seekTo((sec * 1000).toLong())
    }
},
```

The `TvPlayerHud` signature change happens in T3; this commit will not compile in isolation, so combine T2 step 5 with T3 step 1 or temporarily comment out the new HUD params and uncomment in T3. **Recommended:** finish T2 through Step 4, build (scrubber ticks already work), then push T2's HUD-call additions into T3's commit so both halves of the API change land together.

- [ ] **Step 6: Build**

```bash
cd /opt/silo-android && ./gradlew :androidTvApp:compileDebugKotlin
```

Scrubber should now show chapter tick marks on files with embedded chapters. HUD pane still placeholder (until T3).

- [ ] **Step 7: Commit**

```bash
git -C /opt/silo-android add \
  androidTvApp/src/androidMain/kotlin/com/continuum/app/tv/ui/screens/player/TvPlayerViewModel.kt \
  androidTvApp/src/androidMain/kotlin/com/continuum/app/tv/ui/screens/player/TvPlayerScreen.kt

git -c user.name="rxwatcher" -c user.email="rxwatcher@users.noreply.github.com" -C /opt/silo-android commit -m "feat(tv-player): plumb FileVersion.chapters into UiState + scrubber ticks (A.3b)

TvPlayerViewModel.UiState gains a chapters list, populated from the
selected version (per-file — different versions of a title can
have different chapter cuts). TvPlayerScreen maps the list to
TvPlayerScrubber's ChapterInfo so existing tick-mark rendering
lights up on chapter-bearing files.

New onSeekToChapter(idx) returns the start time so the screen can
drive the MediaController seek (T3 uses this from the HUD pane).

HUD Chapters pane wiring lands in T3."
```

---

### Task 3: HUD Chapters pane

**Files:**
- Modify: `/opt/silo-android/androidTvApp/src/androidMain/kotlin/com/continuum/app/tv/ui/screens/player/TvPlayerHud.kt`
- Modify: `/opt/silo-android/androidTvApp/src/androidMain/kotlin/com/continuum/app/tv/ui/screens/player/TvPlayerScreen.kt` (HUD call site — params added in T2 Step 5 land here)

**Why:** UI counterpart. Replaces the `"No chapters in this title"` placeholder with a real list when the data is present; placeholder stays as the empty-state fallback.

- [ ] **Step 1: Add `chapters` + `onSelectChapter` params to `TvPlayerHud`**

After the existing `onHdrEnabledChanged` param:

```kotlin
chapters: List<com.continuum.app.model.catalog.VersionChapter>,
onSelectChapter: (Int) -> Unit,
```

- [ ] **Step 2: Replace the Chapters tab branch**

```kotlin
HudTab.Chapters -> HudChaptersPane(
    chapters = chapters,
    onSelectChapter = onSelectChapter,
)
```

- [ ] **Step 3: Implement `HudChaptersPane`**

Place near `HudPickerPane` (it reuses the same picker scaffold for keyboard focus and selection):

```kotlin
/**
 * Chapters pane — renders [com.continuum.app.model.catalog.VersionChapter]s
 * from the active FileVersion as a focus-driven picker. Selecting a chapter
 * seeks the player to its start time. When the file has no chapters
 * (single-row "No chapters in this title" placeholder); when it does, each
 * row shows the chapter title, formatted start time, and (when the server
 * supplied them) a thumbhash-backed thumbnail.
 */
@Composable
private fun HudChaptersPane(
    chapters: List<com.continuum.app.model.catalog.VersionChapter>,
    onSelectChapter: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (chapters.isEmpty()) {
        HudEmptyStatePane("No chapters in this title", modifier)
        return
    }
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        itemsIndexed(chapters, key = { _, c -> c.index }) { idx, ch ->
            HudChapterRow(
                chapter = ch,
                onFocused = { onSelectChapter(idx) },
            )
        }
    }
}

@Composable
private fun HudChapterRow(
    chapter: com.continuum.app.model.catalog.VersionChapter,
    onFocused: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    LaunchedEffect(isFocused) { if (isFocused) onFocused() }

    val bg = if (isFocused) Color.White.copy(alpha = 0.12f) else Color.Transparent
    val fg = if (isFocused) Color.White else Color.White.copy(alpha = 0.86f)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(bg)
            .focusable(interactionSource = interactionSource)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = formatTime(chapter.startSeconds),
            color = fg.copy(alpha = 0.72f),
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            text = chapter.title.ifBlank { "Chapter ${chapter.index + 1}" },
            color = fg,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
        )
    }
}
```

**Stretch (skip if it pushes the commit out of bounds):** Add a `ThumbhashImage` leading the row when `chapter.thumbnailUrl != null && chapter.thumbnailThumbhash != null`. The composable lives at `android-shared/src/androidMain/kotlin/com/continuum/app/common/ui/components/ThumbhashImage.kt`; verify its parameter list and add. Size: 96×54 dp (16:9). Skip on first cut if the existing API doesn't accept a URL + thumbhash pair cleanly — text-only rows are still a complete shipping pane (matches Apple's fallback when art is unavailable).

- [ ] **Step 4: Land the HUD call-site params from T2 Step 5**

In `TvPlayerScreen.kt`, the `TvPlayerHud(...)` call already had the new params added at the end of T2 step 5 (or to be added now if T2 deferred them):

```kotlin
chapters = state.chapters,
onSelectChapter = { idx ->
    viewModel.onSeekToChapter(idx)?.let { sec ->
        mediaController?.seekTo((sec * 1000).toLong())
    }
},
```

- [ ] **Step 5: Build**

```bash
cd /opt/silo-android && ./gradlew :androidTvApp:compileDebugKotlin
```

- [ ] **Step 6: Commit**

```bash
git -C /opt/silo-android add \
  androidTvApp/src/androidMain/kotlin/com/continuum/app/tv/ui/screens/player/TvPlayerHud.kt \
  androidTvApp/src/androidMain/kotlin/com/continuum/app/tv/ui/screens/player/TvPlayerScreen.kt

git -c user.name="rxwatcher" -c user.email="rxwatcher@users.noreply.github.com" -C /opt/silo-android commit -m "feat(tv-player): HUD Chapters pane renders server-supplied chapters (A.3b)

Replaces the 'No chapters in this title' placeholder with a real
picker when the active FileVersion has chapters. Each row shows
the formatted start time + title; the placeholder remains as the
empty-state fallback for files without embedded chapters.

Focus-driven selection (matches audio / subtitle pickers): moving
focus to a row commits via onSelectChapter, which the screen wires
to MediaController.seekTo via viewModel.onSeekToChapter."
```

---

## Self-Review

**Spec coverage** (A.3b row in spec status table):
- Chapters pane wiring → T3 ✓
- Source: server `chapters` field (already populated) → T1 ✓
- Scrubber ticks → T2 ✓ (existing scaffolding now receives data)
- Seek on selection → T2 + T3 ✓

**Placeholder scan:** No "TBD." Two verification gates flagged in-task: (1) `:shared` test target name for `commonTest` (`testDebugUnitTest` may need a `:shared:jvmTest` or similar swap depending on KMP setup), (2) `ThumbhashImage` parameter shape for the stretch thumbnail rendering.

**Sequencing:** T1 (model + tests) → T2 (UiState plumbing + scrubber + ViewModel seek) → T3 (HUD pane). T2 builds in isolation (scrubber works); T3 closes the HUD half. The split keeps the diff reviewable.

**Risk:** None substantive. The API contract is already proven (Apple in production). The Media3 chapter API gap is sidestepped entirely — chapters are pure data, the player doesn't need to know.

**What this plan does NOT cover:**
- Chapter thumbnails as fully-loaded images — stretch only; text rows ship by default.
- Phone HUD — phone player has no Chapters tab today (out of TV scope).
- Server-side chapter editing / overrides — out of client scope.
- HLS / DASH chapter sources — server doesn't extract them; sticking to embedded-only matches both server reality and Apple's surface.
