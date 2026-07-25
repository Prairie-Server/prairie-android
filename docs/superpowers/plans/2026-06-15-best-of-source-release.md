# Best-Of-Source Release Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Land the P0 correctness quick-wins and the Reading-phase upgrades (ebook typography/locator/reading-time, comic/manga reader rework, PDF memory + zoom), borrowing patterns from the reference apps per the borrow map.

**Architecture:** Four independently-shippable parts in one document. Part 0 is small, cross-subsystem bug fixes (several close confirmed branch-review bugs). Parts A/B/C upgrade the three mobile reading engines, keeping Prairie's existing `ReaderEngineHost` dispatch, WebView reflow engine, and per-(server,profile) settings store. TDD rigor lands on the pure-logic seams (sort, scale math, progress math, CSS generation, version selection); Compose/Android-graphics wiring uses the repo's established source-assertion tests plus mandatory device QA.

**Tech Stack:** Kotlin Multiplatform (single `androidTarget`), Jetpack Compose 1.7.x, Media3 1.10, `kotlin.test` + JUnit4 runner, Robolectric 4.13 (for `android.graphics`/`PdfRenderer` tests), kotlinx-coroutines-test. Android 7 / API 24 floor.

**Test commands:**
- shared: `./gradlew :shared:testDebugUnitTest`
- android-shared: `./gradlew :android-shared:testDebugUnitTest`
- androidApp: `./gradlew :androidApp:testDebugUnitTest`
- Single class: append `--tests "fully.qualified.ClassName"`

**Conventions:**
- Tests use `kotlin.test` (`import kotlin.test.Test`, `assertEquals`, `assertTrue`, `assertNull`, `assertIs`); backtick method names are normal.
- Param builders that need an Android `Context`, and `@Composable` bodies, are NOT unit-testable. The repo's pattern for those is a **source-assertion test** that reads the `.kt`/`.js`/`.html` file via `java.io.File("src/androidMain/...")` (relative to the module root) and asserts on its text. We follow that pattern where noted.
- Commit after each task. Branch is `feature/production-playback-architecture`; commit directly onto it.

---

# Part 0 — P0 Quick-Wins / Stabilization

Independent small fixes. Order is not load-bearing except Task 2/3 (EPUB) unblock Part A.

---

### Task 1: Stop track-selection presets from force-disabling subtitles

**Files:**
- Modify: `android-shared/src/androidMain/kotlin/com/continuum/app/common/player/TrackSelectionPresets.kt:69` and `:108`
- Test: `android-shared/src/androidUnitTest/kotlin/com/continuum/app/common/player/TrackSelectionPresetsSubtitleSourceTest.kt` (new)

**Context:** `buildTvParameters` and `buildPhoneParameters` both call `.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)` and never apply the `preferredTextLanguage` parameter. Because the `applyTrackSelection` effect re-runs on every audio-caps/HDR change, this wipes the user's active subtitle override mid-playback. The builders need a `Context`, so we use the repo's source-assertion test pattern.

- [ ] **Step 1: Write the failing test**

```kotlin
package com.continuum.app.common.player

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TrackSelectionPresetsSubtitleSourceTest {
    private val source =
        File("src/androidMain/kotlin/com/continuum/app/common/player/TrackSelectionPresets.kt").readText()

    @Test
    fun `presets never force-disable the text track type`() {
        assertTrue(
            !source.contains("setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)"),
            "Presets must not force-disable text tracks; that wipes the user's subtitle override.",
        )
    }

    @Test
    fun `presets apply the preferred text language`() {
        val occurrences = Regex("setPreferredTextLanguage\\(").findAll(source).count()
        assertEquals(
            2,
            occurrences,
            "Both buildTvParameters and buildPhoneParameters must apply preferredTextLanguage.",
        )
    }
}
```

- [ ] **Step 2: Run it; verify it fails**

Run: `./gradlew :android-shared:testDebugUnitTest --tests "com.continuum.app.common.player.TrackSelectionPresetsSubtitleSourceTest"`
Expected: FAIL — source still contains the disable call and zero `setPreferredTextLanguage(`.

- [ ] **Step 3: Edit `buildTvParameters` (remove the disable, apply text language)**

In the `builder` chain, delete the line `.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)`. Then, just below the existing `preferredAudioLanguage` block, add:

```kotlin
        preferredTextLanguage?.takeIf { it.isNotBlank() }
            ?.let { builder.setPreferredTextLanguage(it) }
```

- [ ] **Step 4: Edit `buildPhoneParameters` identically**

Delete `.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)` from its `builder` chain, and add the same `preferredTextLanguage` block below the `preferredAudioLanguage` block.

- [ ] **Step 5: Run the test; verify it passes**

Run: `./gradlew :android-shared:testDebugUnitTest --tests "com.continuum.app.common.player.TrackSelectionPresetsSubtitleSourceTest"`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add android-shared/src/androidMain/kotlin/com/continuum/app/common/player/TrackSelectionPresets.kt \
        android-shared/src/androidUnitTest/kotlin/com/continuum/app/common/player/TrackSelectionPresetsSubtitleSourceTest.kt
git commit -m "fix(player): stop presets disabling subtitles; apply preferred text language"
```

---

### Task 2: Resolve EPUB chapter assets against the OPF directory

**Files:**
- Modify: `androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/reader/EpubBook.kt` (expose `opfDir`)
- Modify: `androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/reader/reflow/EpubReflowSource.kt:30`
- Modify: `androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/reader/EpubReader.kt:187`
- Test: `androidApp/src/androidUnitTest/kotlin/com/continuum/app/android/ui/screens/reader/reflow/EpubReflowSourceTest.kt` (add a case)

**Context:** `baseUrl()` and `loadDataWithBaseURL` use `readerDirectoryBaseUrl(book.unpackedRoot)`, but spine hrefs are relative to the OPF directory (`EpubBook.opfDir`, currently `private`). For an EPUB whose OPF is under `OEBPS/`, chapter-relative `<img>`/CSS break.

- [ ] **Step 1: Write the failing test (add to `EpubReflowSourceTest`)**

```kotlin
    @Test
    fun `epub base url points at the opf directory for nested opf layouts`() = runTest {
        val epub = tmp.newFile("nested.epub")
        writeNestedOpfEpub(epub) // OPF at OEBPS/content.opf, chapter at OEBPS/Text/ch1.xhtml
        val book = EpubBook.open(epub, tmp.newFolder("unpack-nested"))
        val source = EpubReflowSource(book)
        val base = source.baseUrl(0)
        assertTrue(base.contains("/OEBPS/"), "base url must resolve relative assets against the OPF dir, was: $base")
    }

    private fun writeNestedOpfEpub(epub: File) {
        ZipOutputStream(epub.outputStream()).use { zip ->
            zip.writeEntry("mimetype", "application/epub+zip")
            zip.writeEntry(
                "META-INF/container.xml",
                """<?xml version="1.0"?><container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container"><rootfiles><rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/></rootfiles></container>""",
            )
            zip.writeEntry(
                "OEBPS/content.opf",
                """<?xml version="1.0"?><package xmlns="http://www.idpf.org/2007/opf" version="3.0"><manifest><item id="c1" href="Text/ch1.xhtml" media-type="application/xhtml+xml"/></manifest><spine><itemref idref="c1"/></spine></package>""",
            )
            zip.writeEntry("OEBPS/Text/ch1.xhtml", "<html><body><p>Chapter one</p></body></html>")
        }
    }
```

(Reuse the existing `ZipOutputStream.writeEntry(name, content)` helper already in this test file.)

- [ ] **Step 2: Run it; verify it fails**

Run: `./gradlew :androidApp:testDebugUnitTest --tests "com.continuum.app.android.ui.screens.reader.reflow.EpubReflowSourceTest"`
Expected: FAIL — base url contains the unpacked root, not `/OEBPS/`.

- [ ] **Step 3: Expose `opfDir` on `EpubBook`**

In `EpubBook.kt`, change the constructor property from `private val opfDir: File` to `val opfDir: File`.

- [ ] **Step 4: Use `opfDir` in `EpubReflowSource.baseUrl`**

```kotlin
    override fun baseUrl(index: Int): String =
        readerDirectoryBaseUrl(book.opfDir)
```

- [ ] **Step 5: Use `opfDir` in `EpubReader` `loadDataWithBaseURL`**

In `EpubReader.kt`, change `val base = readerDirectoryBaseUrl(book.unpackedRoot)` to `val base = readerDirectoryBaseUrl(book.opfDir)`.

- [ ] **Step 6: Run tests; verify pass**

Run: `./gradlew :androidApp:testDebugUnitTest --tests "com.continuum.app.android.ui.screens.reader.reflow.EpubReflowSourceTest"`
Expected: PASS (new case + existing cases).

- [ ] **Step 7: Commit**

```bash
git add androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/reader/EpubBook.kt \
        androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/reader/reflow/EpubReflowSource.kt \
        androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/reader/EpubReader.kt \
        androidApp/src/androidUnitTest/kotlin/com/continuum/app/android/ui/screens/reader/reflow/EpubReflowSourceTest.kt
git commit -m "fix(reader): resolve EPUB chapter assets against the OPF directory"
```

---

### Task 3: `chooseReaderVersion` falls through to a readable version

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/continuum/app/model/ebook/EbookVersionSelection.kt:176-190`
- Test: `shared/src/commonTest/kotlin/com/continuum/app/model/ebook/EbookVersionSelectionTest.kt` (add a case)

**Context:** When `requestedFileId` points at an `Unsupported` format, `chooseReaderVersion` returns `null` even if another readable version exists; `chooseEbookVersion` already falls through. Make them consistent.

- [ ] **Step 1: Write the failing test**

```kotlin
    @Test
    fun chooseReaderVersionFallsThroughWhenRequestedIsUnsupported() {
        val versions = listOf(
            FileVersion(fileId = 1, fileName = "book.mobi", container = "mobi"),
            FileVersion(fileId = 2, fileName = "book.epub", container = "epub"),
        )
        val target = chooseReaderVersion(versions, requestedFileId = 1)
        assertEquals(2, target?.version?.fileId, "should fall through to the readable EPUB")
    }
```

(If `.mobi` is actually in-app readable in this codebase, swap container `"mobi"` for a genuinely unsupported one — verify against `ebookFormatSupport()`/`EbookFormatSupportTest`.)

- [ ] **Step 2: Run it; verify it fails**

Run: `./gradlew :shared:testDebugUnitTest --tests "com.continuum.app.model.ebook.EbookVersionSelectionTest"`
Expected: FAIL — returns null.

- [ ] **Step 3: Edit `chooseReaderVersion`**

```kotlin
fun chooseReaderVersion(
    versions: List<FileVersion>,
    requestedFileId: Int?,
): ReaderVersionTarget? {
    val targets = versions.mapNotNull { it.readerTargetOrNull() }

    if (requestedFileId != null) {
        targets.firstOrNull { it.version.fileId == requestedFileId }?.let { return it }
    }

    return targets.preferredReaderTarget(EbookReadMode.InApp)
        ?: targets.preferredReaderTarget(EbookReadMode.ExternalOnly)
}
```

- [ ] **Step 4: Run tests; verify pass**

Run: `./gradlew :shared:testDebugUnitTest --tests "com.continuum.app.model.ebook.EbookVersionSelectionTest"`
Expected: PASS (new + existing cases — the requested-and-readable case still returns the requested one).

- [ ] **Step 5: Commit**

```bash
git add shared/src/commonMain/kotlin/com/continuum/app/model/ebook/EbookVersionSelection.kt \
        shared/src/commonTest/kotlin/com/continuum/app/model/ebook/EbookVersionSelectionTest.kt
git commit -m "fix(ebook): chooseReaderVersion falls through to a readable version"
```

---

### Task 4: Watch-Together treats any `room_closed` as terminal

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/continuum/app/repository/WatchTogetherRepository.kt:268-303`
- Test: `shared/src/commonTest/kotlin/com/continuum/app/repository/WatchTogetherRepositoryTest.kt` (add a case)

**Context:** `connect()` only treats a `Closed` event as terminal when `reason != null`. A `{"type":"room_closed"}` with no reason decodes to `Closed(null)` and triggers infinite reconnect against a dead room.

- [ ] **Step 1: Write the failing test (mirror the existing `reconnect loop stops after room_closed`)**

```kotlin
    @Test
    fun `reconnect loop stops after room_closed with no reason`() = runTest {
        val realtime = FakeRealtime()
        val r = repo(realtime = realtime)
        r.createRoom(CreateRoomRequest())
        val job = launch { r.connect("room-1") }
        advanceUntilIdle()
        assertEquals(1, realtime.connectCount)
        realtime.events.emit(RoomRealtimeEvent.Closed(null))
        advanceUntilIdle()
        assertEquals(1, realtime.connectCount)
        assertTrue(job.isCompleted || job.isCancelled)
    }
```

- [ ] **Step 2: Run it; verify it fails**

Run: `./gradlew :shared:testDebugUnitTest --tests "com.continuum.app.repository.WatchTogetherRepositoryTest"`
Expected: FAIL — `connectCount` becomes 2 (reconnected) because `Closed(null)` is treated as transient.

- [ ] **Step 3: Edit the terminal check in `connect()`**

Change the inner condition so any `Closed` is terminal:

```kotlin
                client.connect(roomId, roomToken).collect { event ->
                    if (event is RoomRealtimeEvent.Closed) {
                        // Any server close (host_left / room_closed / reasonless) is terminal.
                        closedByServer = true
                        _roomClosedReason.value = event.reason
                        _roomSnapshot.value = null
                        throw ServerClosed
                    } else {
                        backoffIndex = 0 // healthy traffic resets backoff
                    }
                    fold(event)
                }
```

- [ ] **Step 4: Run tests; verify pass**

Run: `./gradlew :shared:testDebugUnitTest --tests "com.continuum.app.repository.WatchTogetherRepositoryTest"`
Expected: PASS (new case + the existing `host_left` case).

- [ ] **Step 5: Commit**

```bash
git add shared/src/commonMain/kotlin/com/continuum/app/repository/WatchTogetherRepository.kt \
        shared/src/commonTest/kotlin/com/continuum/app/repository/WatchTogetherRepositoryTest.kt
git commit -m "fix(watch-together): treat any room_closed as terminal"
```

---

### Task 5: Cap Watch-Together transient reconnect attempts

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/continuum/app/repository/WatchTogetherRepository.kt:268-306`
- Test: `shared/src/commonTest/kotlin/com/continuum/app/repository/WatchTogetherRepositoryTest.kt` (add a case + extend `FakeRealtime`)

**Context:** The `catch (_: Throwable)` path retries forever (auth failure, dead token). Add a max-consecutive-failure cap; reset it on healthy traffic.

- [ ] **Step 1: Extend `FakeRealtime` to allow a connect failure**

In the test file's `FakeRealtime`, add (keeping existing members):

```kotlin
        var failConnect = false
        // inside connect(...): if (failConnect) throw IllegalStateException("boom")
```

Wire `failConnect` into its `connect()` body so it throws before/instead of emitting.

- [ ] **Step 2: Write the failing test**

```kotlin
    @Test
    fun `reconnect loop gives up after the max consecutive failures`() = runTest {
        val realtime = FakeRealtime().apply { failConnect = true }
        val r = repo(realtime = realtime)
        r.createRoom(CreateRoomRequest())
        val job = launch { r.connect("room-1") }
        advanceUntilIdle()
        assertTrue(job.isCompleted, "loop must stop after exceeding the reconnect cap")
        assertEquals("connection_lost", r.roomClosedReason.value)
    }
```

- [ ] **Step 3: Run it; verify it fails**

Run: `./gradlew :shared:testDebugUnitTest --tests "com.continuum.app.repository.WatchTogetherRepositoryTest"`
Expected: FAIL — job never completes (infinite retry) / `roomClosedReason` not set.

- [ ] **Step 4: Add the cap to `connect()`**

Add a constant near `BACKOFF_MS`: `private const val MAX_RECONNECT_ATTEMPTS = 6`. In `connect()`, track failures:

```kotlin
        var backoffIndex = 0
        var failures = 0
        while (true) {
            var closedByServer = false
            var healthy = false
            try {
                client.connect(roomId, roomToken).collect { event ->
                    if (event is RoomRealtimeEvent.Closed) {
                        closedByServer = true
                        _roomClosedReason.value = event.reason
                        _roomSnapshot.value = null
                        throw ServerClosed
                    } else {
                        healthy = true
                        backoffIndex = 0
                        failures = 0
                    }
                    fold(event)
                }
            } catch (e: CancellationException) {
                realtime = null
                throw e
            } catch (_: ServerClosed) {
            } catch (_: Throwable) {
                if (!healthy) failures++
            }
            if (closedByServer) break
            if (failures >= MAX_RECONNECT_ATTEMPTS) {
                _roomClosedReason.value = "connection_lost"
                break
            }
            delay(BACKOFF_MS[backoffIndex])
            backoffIndex = (backoffIndex + 1).coerceAtMost(BACKOFF_MS.lastIndex)
        }
        realtime = null
```

- [ ] **Step 5: Run tests; verify pass**

Run: `./gradlew :shared:testDebugUnitTest --tests "com.continuum.app.repository.WatchTogetherRepositoryTest"`
Expected: PASS (new case + Tasks 4 cases + existing).

- [ ] **Step 6: Commit**

```bash
git add shared/src/commonMain/kotlin/com/continuum/app/repository/WatchTogetherRepository.kt \
        shared/src/commonTest/kotlin/com/continuum/app/repository/WatchTogetherRepositoryTest.kt
git commit -m "fix(watch-together): cap transient reconnect attempts"
```

---

### Task 6: Notifications — atomic state updates

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/continuum/app/repository/NotificationsRepository.kt` (`publish`/fold call sites)
- Test: `shared/src/commonTest/kotlin/com/continuum/app/repository/NotificationsRepositoryTest.kt` (add a case)

**Context:** Realtime fold and `markRead`/`markAllRead`/`loadMore` all do read-modify-write on `_state.value` with no atomicity, losing updates under concurrency. Route forward folds through an atomic `updateAndGet`.

- [ ] **Step 1: Write the failing test (atomic fold helper exists and re-derives flows)**

```kotlin
    @Test
    fun `mutate applies atomically and keeps rows and unread in sync`() = runTest {
        val repo = repo() // existing test factory
        repo.seed( // existing seam used by other tests to set initial NotificationsState
            NotificationsState(
                rows = listOf(row("a", "2026-06-12T09:00:00Z"), row("b", "2026-06-12T08:00:00Z")),
                unreadCount = 2,
            ),
        )
        repo.applyForTest(NotificationRealtimeEvent.Read("a")) // calls mutate { applyEvent(it, event) }
        assertEquals(1, repo.unreadCount.first())
        assertEquals(repo.rows.first().count { !it.isRead }, repo.unreadCount.first())
    }
```

(If `seed`/`applyForTest` don't exist, add minimal internal test seams mirroring how existing tests inject state, or assert on the new `mutate` helper directly. Keep them `internal`.)

- [ ] **Step 2: Run it; verify it fails** (compile error / no `mutate`)

Run: `./gradlew :shared:testDebugUnitTest --tests "com.continuum.app.repository.NotificationsRepositoryTest"`
Expected: FAIL.

- [ ] **Step 3: Add an atomic `mutate` helper and route folds through it**

```kotlin
    private fun mutate(transform: (NotificationsState) -> NotificationsState) {
        val next = _state.updateAndGet(transform)
        _rows.value = next.rows
        _unreadCount.value = next.unreadCount
    }
```

In `connectRealtime`, replace `publish(applyEvent(_state.value, event))` with `mutate { applyEvent(it, event) }`. In `loadMore`, replace the `publish(_state.value.copy(...))` with `mutate { it.copy(rows = merged, unreadCount = recomputeUnread(merged)) }` (compute `merged` from `it.rows`). In `markRead`/`markAllRead`, replace the optimistic `publish(applyEvent(before, ...))` with `mutate { applyEvent(it, ...) }`; keep `publish(before)` for the revert path (snapshot revert is acceptable). `publish` stays for `reset()`.

- [ ] **Step 4: Run tests; verify pass**

Run: `./gradlew :shared:testDebugUnitTest --tests "com.continuum.app.repository.NotificationsRepositoryTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add shared/src/commonMain/kotlin/com/continuum/app/repository/NotificationsRepository.kt \
        shared/src/commonTest/kotlin/com/continuum/app/repository/NotificationsRepositoryTest.kt
git commit -m "fix(notifications): atomic state updates via updateAndGet"
```

---

### Task 7: Notifications — readable blank-`createdAt` rows + backoff reset on connect

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/continuum/app/repository/NotificationsRepository.kt` (`applyEvent` Read/ReadAll; `connectRealtime`)
- Test: `shared/src/commonTest/kotlin/com/continuum/app/repository/NotificationsRepositoryTest.kt`

**Context:** `applyEvent(Read)` sets `readAt = it.createdAt`; a blank `createdAt` leaves `isRead == false` forever. Use a non-blank sentinel. Also reset realtime backoff when a connection is established, not only on traffic.

- [ ] **Step 1: Write the failing test**

```kotlin
    @Test
    fun `read marks a blank-createdAt row as read`() {
        val state = NotificationsState(
            rows = listOf(row("a", createdAt = "")),
            unreadCount = 1,
        )
        val after = applyEvent(state, NotificationRealtimeEvent.Read("a"))
        assertTrue(after.rows.first { it.id == "a" }.isRead)
        assertEquals(0, after.unreadCount)
    }
```

- [ ] **Step 2: Run it; verify it fails**

Run: `./gradlew :shared:testDebugUnitTest --tests "com.continuum.app.repository.NotificationsRepositoryTest"`
Expected: FAIL — `isRead` stays false because `readAt = ""`.

- [ ] **Step 3: Use a sentinel in `applyEvent` Read/ReadAll**

Add near the repository top: `private const val READ_SENTINEL = "1970-01-01T00:00:00Z"`. In the Read branch change `it.copy(readAt = it.createdAt)` to:

```kotlin
                    if (it.id == event.id && !it.isRead) {
                        it.copy(readAt = it.createdAt.ifBlank { READ_SENTINEL })
                    } else it
```

Apply the same `ifBlank { READ_SENTINEL }` in the `ReadAll` branch.

- [ ] **Step 4: Reset backoff on connect, and stop on persistent auth failure**

In `connectRealtime`, add a local `var established = false` before the `collect`; inside the `collect` lambda, on the first event of each connection set `backoffMs = INITIAL_BACKOFF_MS` and `established = true` so a healthy-then-clean-close cycle doesn't escalate backoff.

Then make auth-class closes terminal (mirrors the Watch-Together cap in Task 5). Add a helper and break the `while` loop on an auth close:

```kotlin
    private fun NotificationRealtimeEvent.isAuthClose(): Boolean =
        this is NotificationRealtimeEvent.Closed &&
            (reason?.contains("401") == true || reason?.contains("403") == true ||
                reason?.contains("auth", ignoreCase = true) == true)
```

In the loop, after `client.connect().collect { ... }` returns (or in the `catch`), check a captured flag and `break` when the last event was an auth close, setting a disconnected state the UI can read (reuse `_capability`/an existing disconnected signal, or add `private val _realtimeFatal = MutableStateFlow(false)` exposed as a read-only flow). Concretely, inside the `collect` lambda: `if (event.isAuthClose()) { _realtimeFatal.value = true; return@collect }`, and after the `collect` returns, `if (_realtimeFatal.value) return@launch`.

- [ ] **Step 5: Run tests; verify pass**

Run: `./gradlew :shared:testDebugUnitTest --tests "com.continuum.app.repository.NotificationsRepositoryTest"`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add shared/src/commonMain/kotlin/com/continuum/app/repository/NotificationsRepository.kt \
        shared/src/commonTest/kotlin/com/continuum/app/repository/NotificationsRepositoryTest.kt
git commit -m "fix(notifications): mark blank-createdAt rows read; reset backoff on connect"
```

---

### Task 8: Admin — clearing the library field must not revoke all libraries

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/continuum/app/viewmodel/AdminUserForm.kt` (add a nullable parser) and `shared/src/commonMain/kotlin/com/continuum/app/viewmodel/AdminUserEditViewModel.kt:130-144`
- Test: `shared/src/commonTest/kotlin/com/continuum/app/viewmodel/AdminUserEditViewModelTest.kt`

**Context:** `update()` always sends `libraryIds = parseLibraryIds(libraryIdsText)` which is `emptyList()` when blank — serialized as `[]`, revoking all libraries. Send `null` (omit) when the field is blank.

- [ ] **Step 1: Write the failing test (mirror the existing `FakeEditApi.lastUpdate` pattern)**

```kotlin
    @Test
    fun `editing with a blank library field omits libraryIds`() = runTest(dispatcher) {
        val api = FakeEditApi()
        val vm = AdminUserEditViewModel(AdminRepository(api))
        vm.load(userId = 7) // edit mode
        vm.onLibraryIdsChange("")
        vm.submit()
        assertNull(api.lastUpdate?.libraryIds, "blank library field must omit libraryIds, not send []")
    }
```

- [ ] **Step 2: Run it; verify it fails**

Run: `./gradlew :shared:testDebugUnitTest --tests "com.continuum.app.viewmodel.AdminUserEditViewModelTest"`
Expected: FAIL — `lastUpdate.libraryIds == []`.

- [ ] **Step 3: Add a nullable parser in `AdminUserForm.kt`**

```kotlin
fun parseLibraryIdsOrNull(raw: String): List<Int>? =
    if (raw.isBlank()) null else parseLibraryIds(raw)
```

- [ ] **Step 4: Use it in `AdminUserEditViewModel.update()`**

Change `libraryIds = parseLibraryIds(libraryIdsText)` to `libraryIds = parseLibraryIdsOrNull(libraryIdsText)`.

- [ ] **Step 5: Run tests; verify pass**

Run: `./gradlew :shared:testDebugUnitTest --tests "com.continuum.app.viewmodel.AdminUserEditViewModelTest"`
Expected: PASS (new case + existing create case still sends `[1,2]`).

- [ ] **Step 6: Commit**

```bash
git add shared/src/commonMain/kotlin/com/continuum/app/viewmodel/AdminUserForm.kt \
        shared/src/commonMain/kotlin/com/continuum/app/viewmodel/AdminUserEditViewModel.kt \
        shared/src/commonTest/kotlin/com/continuum/app/viewmodel/AdminUserEditViewModelTest.kt
git commit -m "fix(admin): omit libraryIds when the edit field is blank"
```

---

### Task 9: Requests — clear `_mine` on profile/logout switch

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/continuum/app/repository/RequestsRepository.kt`
- Test: `shared/src/commonTest/kotlin/com/continuum/app/repository/RequestsRepositoryTest.kt` (add a case)
- Wire: the existing profile-switch path that already calls `NotificationsRepository.reset()` (grep for `.reset()` call sites; add `requestsRepository.reset()` beside it)

**Context:** The DI-singleton `RequestsRepository._mine` is never cleared, so a new user briefly sees the previous user's requests.

- [ ] **Step 1: Write the failing test**

```kotlin
    @Test
    fun `reset clears mine`() = runTest {
        val repo = RequestsRepository(FakeRequestsApi()) // existing fake
        repo.refreshMine() // populates _mine from the fake
        assertTrue(repo.mine.value.isNotEmpty())
        repo.reset()
        assertTrue(repo.mine.value.isEmpty())
    }
```

- [ ] **Step 2: Run it; verify it fails** (no `reset`)

Run: `./gradlew :shared:testDebugUnitTest --tests "com.continuum.app.repository.RequestsRepositoryTest"`
Expected: FAIL — unresolved `reset`.

- [ ] **Step 3: Add `reset()`**

```kotlin
    fun reset() {
        _mine.value = emptyList()
    }
```

- [ ] **Step 4: Call it from the profile-switch path**

Find the call site that invokes `NotificationsRepository.reset()` (grep `\.reset()` in the auth/profile-switch code). Inject/obtain `RequestsRepository` there and call `requestsRepository.reset()` immediately after. (No new test needed for the wiring if it's a single call beside an existing one; verify by reading the file.)

- [ ] **Step 5: Run tests; verify pass**

Run: `./gradlew :shared:testDebugUnitTest --tests "com.continuum.app.repository.RequestsRepositoryTest"`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add shared/src/commonMain/kotlin/com/continuum/app/repository/RequestsRepository.kt \
        shared/src/commonTest/kotlin/com/continuum/app/repository/RequestsRepositoryTest.kt
# plus the profile-switch file you edited
git commit -m "fix(requests): clear mine on profile/logout switch"
```

---

### Task 10: Requests — generation-gate `MyRequestsViewModel`

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/continuum/app/viewmodel/RequestsViewModels.kt` (`MyRequestsViewModel`)
- Test: `shared/src/commonTest/kotlin/com/continuum/app/viewmodel/RequestsViewModelTest.kt` (add a case)

**Context:** `load()`/`refresh()` lack the `loadGeneration` guard the Admin VMs have, so a slow stale fetch can overwrite fresh data.

- [ ] **Step 1: Write the failing test**

```kotlin
    @Test
    fun `a stale refresh does not overwrite a newer one`() = runTest(dispatcher) {
        val api = FakeRequestsApi() // supports per-call latency/result control
        val repo = RequestsRepository(api)
        val vm = MyRequestsViewModel(repo)
        api.nextMineDelayMs = 100; api.nextMine = listOf(request("old"))
        vm.refresh()
        api.nextMineDelayMs = 0; api.nextMine = listOf(request("new"))
        vm.refresh()
        advanceUntilIdle()
        assertEquals(listOf("new"), vm.uiState.value.requests.map { it.id })
    }
```

(If `FakeRequestsApi` has no latency knob, add `nextMineDelayMs`/`nextMine` to it.)

- [ ] **Step 2: Run it; verify it fails**

Run: `./gradlew :shared:testDebugUnitTest --tests "com.continuum.app.viewmodel.RequestsViewModelTest"`
Expected: FAIL — stale "old" overwrites "new".

- [ ] **Step 3: Add generation gating (mirror `AdminUsersViewModel`)**

Add `private var loadGeneration = 0` to `MyRequestsViewModel`. In `load()`/`refresh()`, capture `val generation = ++loadGeneration` before launching, and in `refreshMine()` add `if (generation != loadGeneration) return` immediately after the `repository.refreshMine()` call returns, before touching `_uiState`. Thread `generation` into `refreshMine(generation: Int)`.

- [ ] **Step 4: Run tests; verify pass**

Run: `./gradlew :shared:testDebugUnitTest --tests "com.continuum.app.viewmodel.RequestsViewModelTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add shared/src/commonMain/kotlin/com/continuum/app/viewmodel/RequestsViewModels.kt \
        shared/src/commonTest/kotlin/com/continuum/app/viewmodel/RequestsViewModelTest.kt
git commit -m "fix(requests): generation-gate MyRequestsViewModel loads"
```

---

### Task 11: Natural/numeric comic page sort

**Files:**
- Modify: `androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/reader/ComicReader.kt:340-358` (`listComicArchivePages`)
- Test: `androidApp/src/androidUnitTest/kotlin/com/continuum/app/android/ui/screens/reader/ComicArchiveLoaderTest.kt` (add a case)

**Context:** `.sorted()` is lexicographic, so `page10.png` sorts before `page2.png`. Add a natural comparator.

- [ ] **Step 1: Write the failing test**

```kotlin
    @Test
    fun `pages sort numerically not lexicographically`() {
        val file = createZip(
            "page2.png" to byteArrayOf(2),
            "page10.png" to byteArrayOf(10),
            "page1.png" to byteArrayOf(1),
        )
        val loaded = assertIs<ComicArchiveLoadResult.Loaded>(loadComicArchivePages(file))
        assertEquals(listOf("page1.png", "page2.png", "page10.png"), loaded.pages.map { it.entryName })
    }
```

- [ ] **Step 2: Run it; verify it fails**

Run: `./gradlew :androidApp:testDebugUnitTest --tests "com.continuum.app.android.ui.screens.reader.ComicArchiveLoaderTest"`
Expected: FAIL — order is `page1, page10, page2`.

- [ ] **Step 3: Add a natural comparator and use it**

Add to `ComicReader.kt`:

```kotlin
internal val naturalPageComparator: Comparator<String> = Comparator { a, b ->
    val pa = naturalChunks(a); val pb = naturalChunks(b)
    var i = 0
    while (i < pa.size && i < pb.size) {
        val ca = pa[i]; val cb = pb[i]
        val both = ca.toLongOrNull() != null && cb.toLongOrNull() != null
        val cmp = if (both) ca.toLong().compareTo(cb.toLong()) else ca.compareTo(cb)
        if (cmp != 0) return@Comparator cmp
        i++
    }
    pa.size.compareTo(pb.size)
}

private fun naturalChunks(s: String): List<String> =
    Regex("\\d+|\\D+").findAll(s.lowercase()).map { it.value }.toList()
```

In `listComicArchivePages`, replace `.sorted()` with `.sortedWith(naturalPageComparator)`.

- [ ] **Step 4: Run tests; verify pass**

Run: `./gradlew :androidApp:testDebugUnitTest --tests "com.continuum.app.android.ui.screens.reader.ComicArchiveLoaderTest"`
Expected: PASS (new case; the existing `pages/001.jpg` < `pages/002.png` case still holds).

- [ ] **Step 5: Commit**

```bash
git add androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/reader/ComicReader.kt \
        androidApp/src/androidUnitTest/kotlin/com/continuum/app/android/ui/screens/reader/ComicArchiveLoaderTest.kt
git commit -m "fix(comic): natural/numeric page sort"
```

---

### Task 12: Move MPV auth-header fetch off the player-build thread

**Files:**
- Modify: `android-shared/src/androidMain/kotlin/com/continuum/app/common/player/ContinuumPlayerFactory.kt:172-191`
- Test: source-assertion test `android-shared/src/androidUnitTest/kotlin/com/continuum/app/common/player/ContinuumPlayerFactoryMpvAuthSourceTest.kt` (new)

**Context:** `buildMpvHttpHeaderFields()` calls suspend token getters inside `runBlocking` on the player-build path — an ANR risk on slow A7 CPUs. Pre-fetch the headers once on a background dispatcher before building the player and pass a plain snapshot to the provider.

- [ ] **Step 1: Write the failing source-assertion test**

```kotlin
package com.continuum.app.common.player

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

class ContinuumPlayerFactoryMpvAuthSourceTest {
    private val source =
        File("src/androidMain/kotlin/com/continuum/app/common/player/ContinuumPlayerFactory.kt").readText()

    @Test
    fun `mpv header fetch does not runBlocking on the build thread`() {
        val builder = source.substringAfter("fun createMpvPlayer").substringBefore("\n    }")
        assertTrue(
            !builder.contains("runBlocking"),
            "createMpvPlayer must not runBlocking; pre-fetch auth headers on a background dispatcher.",
        )
    }
}
```

- [ ] **Step 2: Run it; verify it fails**

Run: `./gradlew :android-shared:testDebugUnitTest --tests "com.continuum.app.common.player.ContinuumPlayerFactoryMpvAuthSourceTest"`
Expected: FAIL — `runBlocking` reachable from the build path.

- [ ] **Step 3: Make `createMpvPlayer` suspend and pre-fetch headers**

Convert the header build to a suspend pre-fetch and capture an immutable snapshot:

```kotlin
    suspend fun createMpvPlayer(): Player {
        val headers = buildMpvHttpHeaderFields()
        return MpvPlayer.Builder(context)
            .setHttpHeaderFieldsProvider { headers }
            .setSeekBackIncrementMs(10_000)
            .setSeekForwardIncrementMs(30_000)
            .build()
    }

    private suspend fun buildMpvHttpHeaderFields(): List<Pair<String, String>> =
        buildList {
            tokenManager.getAccessToken()?.takeIf { it.isNotBlank() }?.let { add("Authorization" to "Bearer $it") }
            tokenManager.getProfileId()?.takeIf { it.isNotBlank() }?.let { add("X-Profile-Id" to it) }
            tokenManager.getProfileToken()?.takeIf { it.isNotBlank() }?.let { add("X-Profile-Token" to it) }
        }
```

Update the single caller of `createMpvPlayer()` to call it from a coroutine (the player creation already happens off-main during mount; confirm the call site is `suspend` or wrap in the existing mount coroutine). If the caller cannot be suspend, instead keep `createMpvPlayer()` non-suspend but compute `headers` via `withContext(Dispatchers.IO)` at the existing async player-creation seam and pass the snapshot in.

- [ ] **Step 4: Run the test; verify it passes**

Run: `./gradlew :android-shared:testDebugUnitTest --tests "com.continuum.app.common.player.ContinuumPlayerFactoryMpvAuthSourceTest"`
Expected: PASS. Also run `./gradlew :android-shared:testDebugUnitTest` to confirm no regressions.

- [ ] **Step 5: Commit**

```bash
git add android-shared/src/androidMain/kotlin/com/continuum/app/common/player/ContinuumPlayerFactory.kt \
        android-shared/src/androidUnitTest/kotlin/com/continuum/app/common/player/ContinuumPlayerFactoryMpvAuthSourceTest.kt
git commit -m "fix(player): pre-fetch MPV auth headers off the build thread"
```

---

### Task 13: PDF render survives OutOfMemoryError

**Files:**
- Modify: `androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/reader/PdfReader.kt` (`renderPdfPageBitmap`, and the `readerLoadResult` helper if it only catches `Exception`)
- Test: `androidApp/src/androidUnitTest/kotlin/com/continuum/app/android/ui/screens/reader/ReaderLoadResultTest.kt` (add a case) or a new `PdfRenderMemoryTest`

**Context:** A render `OutOfMemoryError` is an `Error`, not an `Exception`. If `readerLoadResult` catches only `Exception`, the OOM escapes and crashes; even if caught, we should free memory. We make the render path catch `OutOfMemoryError`, return a failure Result, and not leak the partial bitmap.

- [ ] **Step 1: Confirm `readerLoadResult`'s catch scope** (read `ReaderLoadResult.kt`). If it catches `Throwable`, OOM is already captured as failure; if it catches `Exception`, widen it.

- [ ] **Step 2: Write the failing test**

If `readerLoadResult` currently catches only `Exception`:

```kotlin
    @Test
    fun `reader result captures OutOfMemoryError as failure`() {
        val result = readerLoadResult<String> { throw OutOfMemoryError("page too big") }
        assertTrue(result.isFailure)
        assertIs<OutOfMemoryError>(result.exceptionOrNull())
    }
```

- [ ] **Step 3: Run it; verify it fails** (OOM escapes)

Run: `./gradlew :androidApp:testDebugUnitTest --tests "com.continuum.app.android.ui.screens.reader.ReaderLoadResultTest"`
Expected: FAIL — the `OutOfMemoryError` is thrown out of `readerLoadResult` rather than captured.

- [ ] **Step 4: Widen the catch in `readerLoadResult`**

Make `readerLoadResult` (and `requiredReaderLoadResult`) catch `Throwable` (or explicitly add `catch (e: OutOfMemoryError)`), returning `Result.failure(e)`. In `renderPdfPageBitmap`, on failure ensure the partial `bmp` is eligible for GC (it is local; no extra work needed) — add a comment noting the OOM is surfaced as a page-level error.

- [ ] **Step 5: Run the test; verify it passes**

Run: `./gradlew :androidApp:testDebugUnitTest --tests "com.continuum.app.android.ui.screens.reader.ReaderLoadResultTest"`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/reader/
git commit -m "fix(pdf): capture OutOfMemoryError as a page-level failure"
```

---

# Part A — Ebook / Reflow Reader Upgrade

Adds font-family + line-height controls, accurate book-progress, real reading-time, and richer TOC labels. Builds on the OPF fix (Task 2). Each new `ReaderDisplaySettings` field is `@Serializable` with a default, so the existing JSON store stays forward/backward compatible.

---

### Task 14: Add `fontFamily` and `lineHeight` to `ReaderDisplaySettings`

**Files:**
- Modify: `android-shared/src/androidMain/kotlin/com/continuum/app/common/ebook/ReaderControls.kt`
- Test: `android-shared/src/androidUnitTest/kotlin/com/continuum/app/common/ebook/ReaderDisplaySettingsTest.kt` (new)

- [ ] **Step 1: Write the failing test**

```kotlin
package com.continuum.app.common.ebook

import kotlin.test.Test
import kotlin.test.assertEquals

class ReaderDisplaySettingsTest {
    @Test fun `defaults are serif and 1_5 line height`() {
        val s = ReaderDisplaySettings()
        assertEquals(ReaderFontFamily.Serif, s.fontFamily)
        assertEquals(1.5f, s.lineHeight)
    }

    @Test fun `normalized clamps line height`() {
        assertEquals(2.2f, ReaderDisplaySettings(lineHeight = 5f).normalized().lineHeight)
        assertEquals(1.1f, ReaderDisplaySettings(lineHeight = 0.1f).normalized().lineHeight)
    }
}
```

- [ ] **Step 2: Run it; verify it fails**

Run: `./gradlew :android-shared:testDebugUnitTest --tests "com.continuum.app.common.ebook.ReaderDisplaySettingsTest"`
Expected: FAIL — unresolved `ReaderFontFamily`/`fontFamily`/`lineHeight`.

- [ ] **Step 3: Add the enum and fields**

```kotlin
@Serializable
enum class ReaderFontFamily { Serif, SansSerif, Slab, Dyslexic }

@Serializable
data class ReaderDisplaySettings(
    val theme: ReaderTheme = ReaderTheme.System,
    val textScale: Float = 1f,
    val marginScale: Float = 1f,
    val fontFamily: ReaderFontFamily = ReaderFontFamily.Serif,
    val lineHeight: Float = 1.5f,
) {
    fun normalized(): ReaderDisplaySettings = copy(
        textScale = textScale.coerceIn(0.6f, 3.0f),
        marginScale = marginScale.coerceIn(0.75f, 1.5f),
        lineHeight = lineHeight.coerceIn(1.1f, 2.2f),
    )
}
```

- [ ] **Step 4: Run the test; verify it passes**

Run: `./gradlew :android-shared:testDebugUnitTest --tests "com.continuum.app.common.ebook.ReaderDisplaySettingsTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add android-shared/src/androidMain/kotlin/com/continuum/app/common/ebook/ReaderControls.kt \
        android-shared/src/androidUnitTest/kotlin/com/continuum/app/common/ebook/ReaderDisplaySettingsTest.kt
git commit -m "feat(reader): add fontFamily and lineHeight to display settings"
```

---

### Task 15: Thread `fontFamily` + `lineHeight` into reflow CSS

**Files:**
- Modify: `androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/reader/reflow/ReflowStyle.kt`
- Test: `androidApp/src/androidUnitTest/kotlin/com/continuum/app/android/ui/screens/reader/reflow/ReflowStyleTest.kt` (add cases)

**Context:** `ReflowStyle` hardcodes `font-family:Georgia,...serif` and `line-height` default `1.55`; `toReflowStyle` never sets line height. Map the new settings into the CSS.

- [ ] **Step 1: Write the failing tests**

```kotlin
    @Test fun `sans-serif font family maps to a sans stack`() {
        val css = ReaderDisplaySettings(fontFamily = ReaderFontFamily.SansSerif)
            .toReflowStyle(systemDark = false).toCss()
        assertTrue(css.contains("font-family:"))
        assertTrue(css.contains("sans-serif"))
        assertFalse(css.contains("Georgia"))
    }

    @Test fun `line height flows from settings`() {
        val css = ReaderDisplaySettings(lineHeight = 1.9f)
            .toReflowStyle(systemDark = false).toCss()
        assertTrue(css.contains("line-height:1.9"))
    }
```

(Add `import com.continuum.app.common.ebook.ReaderFontFamily`.)

- [ ] **Step 2: Run; verify fail**

Run: `./gradlew :androidApp:testDebugUnitTest --tests "com.continuum.app.android.ui.screens.reader.reflow.ReflowStyleTest"`
Expected: FAIL.

- [ ] **Step 3: Add a font stack + thread settings**

In `ReflowStyle.kt`, add a `fontStack` field and map it:

```kotlin
data class ReflowStyle(
    val theme: ReflowTheme,
    val fontScalePercent: Int,
    val marginEm: Double,
    val lineHeight: Double = 1.5,
    val fontStack: String = "Georgia,\"Times New Roman\",serif",
) {
    fun toCss(): String =
        """
        #reflow-root{
          color:${theme.color};
          background:${theme.background};
          font-family:$fontStack;
          font-size: $fontScalePercent%;
          padding:${marginEm}em;
          box-sizing:border-box;
          line-height:$lineHeight;
          overflow-wrap:break-word;
          word-break:normal;
          text-rendering:optimizeLegibility;
          -webkit-font-smoothing:antialiased;
        }
""".trimIndent() + STYLE_TAIL // keep the existing p/h1/img/blockquote/a rules in a private const
}

private fun ReaderFontFamily.toCssStack(): String = when (this) {
    ReaderFontFamily.Serif -> "Georgia,\"Times New Roman\",serif"
    ReaderFontFamily.SansSerif -> "system-ui,\"Roboto\",\"Helvetica Neue\",Arial,sans-serif"
    ReaderFontFamily.Slab -> "\"Roboto Slab\",Georgia,serif"
    ReaderFontFamily.Dyslexic -> "\"OpenDyslexic\",Verdana,sans-serif"
}
```

Update `toReflowStyle`:

```kotlin
    return ReflowStyle(
        theme = theme,
        fontScalePercent = (n.textScale * 100).toInt(),
        marginEm = (n.marginScale * 1.2),
        lineHeight = n.lineHeight.toDouble(),
        fontStack = n.fontFamily.toCssStack(),
    )
```

(Extract the unchanged CSS tail — the `#reflow-root p`, headings, img, blockquote, a rules — into a `private const val STYLE_TAIL` so the existing `ReflowStyleTest` substring assertions for those rules still pass.)

- [ ] **Step 4: Run; verify pass** (new + existing ReflowStyleTest cases)

Run: `./gradlew :androidApp:testDebugUnitTest --tests "com.continuum.app.android.ui.screens.reader.reflow.ReflowStyleTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/reader/reflow/ReflowStyle.kt \
        androidApp/src/androidUnitTest/kotlin/com/continuum/app/android/ui/screens/reader/reflow/ReflowStyleTest.kt
git commit -m "feat(reader): drive reflow font family and line height from settings"
```

---

### Task 16: Surface font + line-height controls in the settings sheet

**Files:**
- Modify: `androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/reader/ReaderShell.kt` (`ReaderSettingsSheet`)
- Test: source-assertion in `androidApp/src/androidUnitTest/kotlin/com/continuum/app/android/ui/screens/reader/ReaderScreenStructureTest.kt` (add a case)

**Context:** `ReaderSettingsSheet` is `@Composable` (device-verified), so we add a source-assertion that the controls are wired, plus mandatory device QA.

- [ ] **Step 1: Write the failing source-assertion**

```kotlin
    @Test
    fun `settings sheet wires font family and line height controls`() {
        val shell = File("src/androidMain/kotlin/com/continuum/app/android/ui/screens/reader/ReaderShell.kt").readText()
        assertTrue(shell.contains("fontFamily ="), "settings sheet must let the user change fontFamily")
        assertTrue(shell.contains("lineHeight ="), "settings sheet must let the user change lineHeight")
    }
```

- [ ] **Step 2: Run; verify fail**

Run: `./gradlew :androidApp:testDebugUnitTest --tests "com.continuum.app.android.ui.screens.reader.ReaderScreenStructureTest"`
Expected: FAIL.

- [ ] **Step 3: Add the controls in `ReaderSettingsSheet`**

Below the Margins slider (and gated by `capabilities.supportsTextSize`, since font/line-height apply to reflowable text), add a font-family `FilterChip` row and a line-height slider:

```kotlin
            if (capabilities.supportsTextSize) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Font", style = MaterialTheme.typography.titleMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ReaderFontFamily.entries.forEach { family ->
                            FilterChip(
                                selected = settings.fontFamily == family,
                                onClick = { onSettingsChange(settings.copy(fontFamily = family).normalized()) },
                                label = { Text(family.name) },
                            )
                        }
                    }
                }
                ReaderSettingSlider(
                    label = "Line spacing",
                    valueLabel = settings.lineHeight.readerPercentLabel(),
                    value = settings.lineHeight,
                    valueRange = 1.1f..2.2f,
                    onValueChange = { onSettingsChange(settings.copy(lineHeight = it).normalized()) },
                )
            }
```

(Add `import com.continuum.app.common.ebook.ReaderFontFamily`.)

- [ ] **Step 4: Run; verify pass**

Run: `./gradlew :androidApp:testDebugUnitTest --tests "com.continuum.app.android.ui.screens.reader.ReaderScreenStructureTest"`
Expected: PASS.

- [ ] **Step 5: Device QA**

Build & run the phone app; open an EPUB; change font family and line spacing in the settings sheet; confirm the text reflows live and the choice persists across reopen.

- [ ] **Step 6: Commit**

```bash
git add androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/reader/ReaderShell.kt \
        androidApp/src/androidUnitTest/kotlin/com/continuum/app/android/ui/screens/reader/ReaderScreenStructureTest.kt
git commit -m "feat(reader): font family + line spacing controls in settings sheet"
```

---

### Task 17: Cumulative char-offset book-progress map

**Files:**
- Modify: `androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/reader/reflow/SectionWeights.kt`
- Test: `androidApp/src/androidUnitTest/kotlin/com/continuum/app/android/ui/screens/reader/reflow/SectionWeightsTest.kt` (new)

**Context:** Book progress is estimated per-section. Borrowing readest's location-map idea, make `bookProgression` use cumulative char offsets so progress across uneven sections is accurate. (The current code already sums chars; this task adds explicit tests and tightens the zero-length handling.)

- [ ] **Step 1: Write the failing test**

```kotlin
package com.continuum.app.android.ui.screens.reader.reflow

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SectionWeightsTest {
    @Test fun `progress weights by cumulative char offset`() {
        val w = SectionWeights(listOf(100, 300)) // section 0 is 25% of the book
        assertEquals(0.0, w.bookProgression(0, 0.0), 1e-9)
        assertEquals(0.25, w.bookProgression(1, 0.0), 1e-9)   // start of section 1 == 25%
        assertEquals(0.25, w.bookProgression(0, 1.0), 1e-9)   // end of section 0 == 25%
        assertEquals(1.0, w.bookProgression(1, 1.0), 1e-9)
    }

    @Test fun `clamps out-of-range section index`() {
        val w = SectionWeights(listOf(100, 100))
        assertTrue(w.bookProgression(9, 0.5) in 0.0..1.0)
    }
}
```

- [ ] **Step 2: Run; verify pass-or-fail**

Run: `./gradlew :androidApp:testDebugUnitTest --tests "com.continuum.app.android.ui.screens.reader.reflow.SectionWeightsTest"`
Expected: The first test FAILS if `span` mishandles the multi-section case (current code uses `weight` for span, which is correct here, but the `weight <= 0.0` single-section guard is the only special case) — confirm. If the current implementation already passes, treat this task as **adding the missing regression tests** (still valuable) and skip Step 3.

- [ ] **Step 3: Tighten `bookProgression` only if a test fails**

Ensure zero-length sections contribute zero span without breaking the cumulative base:

```kotlin
    fun bookProgression(sectionIndex: Int, pageProgression: Double): Double {
        if (approxChars.isEmpty()) return pageProgression.coerceIn(0.0, 1.0)
        val i = sectionIndex.coerceIn(0, approxChars.lastIndex)
        val before = cumulativeBefore[i].toDouble() / total
        val span = approxChars[i].toDouble() / total
        return (before + span * pageProgression.coerceIn(0.0, 1.0)).coerceIn(0.0, 1.0)
    }
```

- [ ] **Step 4: Run; verify pass**

Run: `./gradlew :androidApp:testDebugUnitTest --tests "com.continuum.app.android.ui.screens.reader.reflow.SectionWeightsTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/reader/reflow/SectionWeights.kt \
        androidApp/src/androidUnitTest/kotlin/com/continuum/app/android/ui/screens/reader/reflow/SectionWeightsTest.kt
git commit -m "test(reader): lock cumulative char-offset book progress"
```

---

### Task 18: Reading-time estimate

**Files:**
- Create: `androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/reader/reflow/ReadingTime.kt`
- Test: `androidApp/src/androidUnitTest/kotlin/com/continuum/app/android/ui/screens/reader/reflow/ReadingTimeTest.kt` (new)
- Modify (display): `ReaderShell.kt` `readerBottomChromeLabel`/sections sheet (optional surface; covered by device QA)

**Context:** Borrow readest's reading-time idea: minutes remaining from remaining chars at a WPM constant (~200 wpm, ~5.5 chars/word).

- [ ] **Step 1: Write the failing test**

```kotlin
package com.continuum.app.android.ui.screens.reader.reflow

import kotlin.test.Test
import kotlin.test.assertEquals

class ReadingTimeTest {
    @Test fun `minutes remaining from total chars and progress`() {
        // 110_000 chars total ≈ 20_000 words ≈ 100 min at 200 wpm; halfway → ~50 min
        assertEquals(50, estimateMinutesRemaining(totalChars = 110_000, bookProgression = 0.5))
    }

    @Test fun `finished book is zero minutes`() {
        assertEquals(0, estimateMinutesRemaining(totalChars = 110_000, bookProgression = 1.0))
    }
}
```

- [ ] **Step 2: Run; verify fail**

Run: `./gradlew :androidApp:testDebugUnitTest --tests "com.continuum.app.android.ui.screens.reader.reflow.ReadingTimeTest"`
Expected: FAIL — unresolved `estimateMinutesRemaining`.

- [ ] **Step 3: Implement**

```kotlin
package com.continuum.app.android.ui.screens.reader.reflow

import kotlin.math.roundToInt

private const val WORDS_PER_MINUTE = 200.0
private const val CHARS_PER_WORD = 5.5

fun estimateMinutesRemaining(totalChars: Int, bookProgression: Double): Int {
    val remainingFraction = (1.0 - bookProgression).coerceIn(0.0, 1.0)
    val remainingWords = (totalChars * remainingFraction) / CHARS_PER_WORD
    return (remainingWords / WORDS_PER_MINUTE).roundToInt().coerceAtLeast(0)
}
```

- [ ] **Step 4: Run; verify pass**

Run: `./gradlew :androidApp:testDebugUnitTest --tests "com.continuum.app.android.ui.screens.reader.reflow.ReadingTimeTest"`
Expected: PASS.

- [ ] **Step 5: (Optional) surface in chrome** — pass `source.sections.sumOf { it.approxChars }` and current `bookProgression` to `estimateMinutesRemaining` and append e.g. `"· 50 min left"` to the reflow branch of `readerBottomChromeLabel`. Device-verified.

- [ ] **Step 6: Commit**

```bash
git add androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/reader/reflow/ReadingTime.kt \
        androidApp/src/androidUnitTest/kotlin/com/continuum/app/android/ui/screens/reader/reflow/ReadingTimeTest.kt
git commit -m "feat(reader): reading-time-remaining estimate"
```

---

# Part B — Comic / Manga Reader Rework

Splits reading direction/fit/zoom out of the `ComicReader.kt` monolith into pure, tested config + a small set of integration steps. Comics/manga stay mobile-only (`ReaderEngineKind.ComicManga`); TV is untouched.

---

### Task 19: Comic reader config (direction, fit mode, tap layout)

**Files:**
- Create: `androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/reader/ComicReaderConfig.kt`
- Test: `androidApp/src/androidUnitTest/kotlin/com/continuum/app/android/ui/screens/reader/ComicReaderConfigTest.kt` (new)

**Context:** Borrowing mihon's Config/Navigation split: a value type holding `ReadingDirection`, `ComicFitMode`, and a tap-region mapping, independent of Compose.

- [ ] **Step 1: Write the failing test**

```kotlin
package com.continuum.app.android.ui.screens.reader

import kotlin.test.Test
import kotlin.test.assertEquals

class ComicReaderConfigTest {
    @Test fun `LTR maps left third to previous, right third to next`() {
        val cfg = ComicReaderConfig(direction = ReadingDirection.LeftToRight)
        assertEquals(ComicTapAction.Previous, cfg.tapAction(0.1f))
        assertEquals(ComicTapAction.ToggleChrome, cfg.tapAction(0.5f))
        assertEquals(ComicTapAction.Next, cfg.tapAction(0.9f))
    }

    @Test fun `RTL inverts left and right thirds`() {
        val cfg = ComicReaderConfig(direction = ReadingDirection.RightToLeft)
        assertEquals(ComicTapAction.Next, cfg.tapAction(0.1f))
        assertEquals(ComicTapAction.Previous, cfg.tapAction(0.9f))
    }
}
```

- [ ] **Step 2: Run; verify fail**

Run: `./gradlew :androidApp:testDebugUnitTest --tests "com.continuum.app.android.ui.screens.reader.ComicReaderConfigTest"`
Expected: FAIL — unresolved symbols.

- [ ] **Step 3: Implement the config**

```kotlin
package com.continuum.app.android.ui.screens.reader

enum class ReadingDirection { LeftToRight, RightToLeft, Vertical }
enum class ComicFitMode { Width, Height, Screen, Original }
enum class ComicTapAction { Previous, Next, ToggleChrome }

data class ComicReaderConfig(
    val direction: ReadingDirection = ReadingDirection.LeftToRight,
    val fitMode: ComicFitMode = ComicFitMode.Screen,
) {
    fun tapAction(xFraction: Float): ComicTapAction {
        val x = xFraction.coerceIn(0f, 1f)
        val zone = when {
            x < 1f / 3f -> ComicTapAction.Previous
            x > 2f / 3f -> ComicTapAction.Next
            else -> ComicTapAction.ToggleChrome
        }
        return if (direction == ReadingDirection.RightToLeft) zone.invertHorizontal() else zone
    }
}

private fun ComicTapAction.invertHorizontal(): ComicTapAction = when (this) {
    ComicTapAction.Previous -> ComicTapAction.Next
    ComicTapAction.Next -> ComicTapAction.Previous
    ComicTapAction.ToggleChrome -> ComicTapAction.ToggleChrome
}
```

- [ ] **Step 4: Run; verify pass**

Run: `./gradlew :androidApp:testDebugUnitTest --tests "com.continuum.app.android.ui.screens.reader.ComicReaderConfigTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/reader/ComicReaderConfig.kt \
        androidApp/src/androidUnitTest/kotlin/com/continuum/app/android/ui/screens/reader/ComicReaderConfigTest.kt
git commit -m "feat(comic): reading-direction + fit-mode config with tap mapping"
```

---

### Task 20: Fit-mode scale calculation

**Files:**
- Create: `androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/reader/ComicFit.kt`
- Test: `androidApp/src/androidUnitTest/kotlin/com/continuum/app/android/ui/screens/reader/ComicFitTest.kt` (new)

**Context:** Pure math mapping (pageW, pageH, viewW, viewH, fitMode) → a content scale, so the Compose layer can apply it deterministically.

- [ ] **Step 1: Write the failing test**

```kotlin
package com.continuum.app.android.ui.screens.reader

import kotlin.test.Test
import kotlin.test.assertEquals

class ComicFitTest {
    @Test fun `fit width scales to viewport width`() {
        assertEquals(2.0f, comicFitScale(1000, 2000, 2000, 3000, ComicFitMode.Width), 1e-4f)
    }
    @Test fun `fit height scales to viewport height`() {
        assertEquals(1.5f, comicFitScale(1000, 2000, 2000, 3000, ComicFitMode.Height), 1e-4f)
    }
    @Test fun `fit screen uses the smaller axis`() {
        assertEquals(1.5f, comicFitScale(1000, 2000, 2000, 3000, ComicFitMode.Screen), 1e-4f)
    }
    @Test fun `original is unscaled`() {
        assertEquals(1.0f, comicFitScale(1000, 2000, 2000, 3000, ComicFitMode.Original), 1e-4f)
    }
}
```

- [ ] **Step 2: Run; verify fail**

Run: `./gradlew :androidApp:testDebugUnitTest --tests "com.continuum.app.android.ui.screens.reader.ComicFitTest"`
Expected: FAIL.

- [ ] **Step 3: Implement**

```kotlin
package com.continuum.app.android.ui.screens.reader

import kotlin.math.min

fun comicFitScale(pageW: Int, pageH: Int, viewW: Int, viewH: Int, mode: ComicFitMode): Float {
    if (pageW <= 0 || pageH <= 0 || viewW <= 0 || viewH <= 0) return 1f
    val sw = viewW.toFloat() / pageW
    val sh = viewH.toFloat() / pageH
    return when (mode) {
        ComicFitMode.Width -> sw
        ComicFitMode.Height -> sh
        ComicFitMode.Screen -> min(sw, sh)
        ComicFitMode.Original -> 1f
    }
}
```

- [ ] **Step 4: Run; verify pass**

Run: `./gradlew :androidApp:testDebugUnitTest --tests "com.continuum.app.android.ui.screens.reader.ComicFitTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/reader/ComicFit.kt \
        androidApp/src/androidUnitTest/kotlin/com/continuum/app/android/ui/screens/reader/ComicFitTest.kt
git commit -m "feat(comic): fit-mode scale calculation"
```

---

### Task 21: Adjacent-page prefetch range with RAM gate

**Files:**
- Create: `androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/reader/ComicPrefetch.kt`
- Test: `androidApp/src/androidUnitTest/kotlin/com/continuum/app/android/ui/screens/reader/ComicPrefetchTest.kt` (new)

**Context:** Borrowing Kotatsu's bounded, RAM-gated prefetch: pure function deciding which page indices to prefetch around the current page, returning empty under low free RAM.

- [ ] **Step 1: Write the failing test**

```kotlin
package com.continuum.app.android.ui.screens.reader

import kotlin.test.Test
import kotlin.test.assertEquals

class ComicPrefetchTest {
    @Test fun `prefetches a bounded window ahead and behind`() {
        assertEquals(listOf(4, 6, 3, 7), comicPrefetchTargets(current = 5, pageCount = 20, radius = 2, freeRamMb = 256))
    }
    @Test fun `clamps at the edges`() {
        assertEquals(listOf(1), comicPrefetchTargets(current = 0, pageCount = 2, radius = 2, freeRamMb = 256))
    }
    @Test fun `low RAM disables prefetch`() {
        assertEquals(emptyList(), comicPrefetchTargets(current = 5, pageCount = 20, radius = 2, freeRamMb = 60))
    }
}
```

- [ ] **Step 2: Run; verify fail**

Run: `./gradlew :androidApp:testDebugUnitTest --tests "com.continuum.app.android.ui.screens.reader.ComicPrefetchTest"`
Expected: FAIL.

- [ ] **Step 3: Implement**

```kotlin
package com.continuum.app.android.ui.screens.reader

private const val PREFETCH_MIN_FREE_RAM_MB = 80

fun comicPrefetchTargets(current: Int, pageCount: Int, radius: Int, freeRamMb: Int): List<Int> {
    if (freeRamMb < PREFETCH_MIN_FREE_RAM_MB || pageCount <= 1) return emptyList()
    val out = mutableListOf<Int>()
    for (d in 1..radius) {
        val ahead = current + d
        val behind = current - d
        if (ahead in 0 until pageCount) out += ahead
        if (behind in 0 until pageCount) out += behind
    }
    return out
}
```

- [ ] **Step 4: Run; verify pass**

Run: `./gradlew :androidApp:testDebugUnitTest --tests "com.continuum.app.android.ui.screens.reader.ComicPrefetchTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/reader/ComicPrefetch.kt \
        androidApp/src/androidUnitTest/kotlin/com/continuum/app/android/ui/screens/reader/ComicPrefetchTest.kt
git commit -m "feat(comic): RAM-gated adjacent-page prefetch targets"
```

---

### Task 22: Wire config + fit + RTL into `ComicReader` (integration)

**Files:**
- Modify: `androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/reader/ComicReader.kt`
- Test: source-assertion in `androidApp/src/androidUnitTest/kotlin/com/continuum/app/android/ui/screens/reader/ReaderEngineHostSourceTest.kt` (add a case) + device QA

**Context:** `ComicReader` is `@Composable` (device-verified). Replace the inline thirds logic with `ComicReaderConfig.tapAction(...)`, set the `HorizontalPager` `reverseLayout` from the direction (RTL), and apply `comicFitScale` to the page `graphicsLayer`. Reading direction comes from book metadata when present, default LTR (comic) — keep a `ComicReaderConfig` `remember` seeded from a (future) metadata source; for this task default LTR and add a manga override via the settings sheet in a follow-up.

- [ ] **Step 1: Write the failing source-assertion**

```kotlin
    @Test
    fun `comic reader uses config-driven tap actions and reverse layout`() {
        val src = File("src/androidMain/kotlin/com/continuum/app/android/ui/screens/reader/ComicReader.kt").readText()
        assertTrue(src.contains("ComicReaderConfig"), "ComicReader must use ComicReaderConfig")
        assertTrue(src.contains("tapAction("), "tap handling must route through config.tapAction")
        assertTrue(src.contains("reverseLayout ="), "pager must honor RTL via reverseLayout")
    }
```

- [ ] **Step 2: Run; verify fail**

Run: `./gradlew :androidApp:testDebugUnitTest --tests "com.continuum.app.android.ui.screens.reader.ReaderEngineHostSourceTest"`
Expected: FAIL.

- [ ] **Step 3: Edit `ComicReader`**

Add `val config = remember { ComicReaderConfig() }`. Replace the `onPageTap` body with:

```kotlin
    val onPageTap: (Float) -> Unit = { xFraction ->
        when (config.tapAction(xFraction)) {
            ComicTapAction.Previous ->
                if (pagerState.currentPage > 0) scope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) } else onToggleChrome()
            ComicTapAction.Next ->
                if (pagerState.currentPage < pages.lastIndex) scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) } else onToggleChrome()
            ComicTapAction.ToggleChrome -> onToggleChrome()
        }
    }
```

Set `reverseLayout = config.direction == ReadingDirection.RightToLeft` on the `HorizontalPager`. In `ComicPage`, when `config.fitMode != ComicFitMode.Screen`, apply `Modifier.graphicsLayer { val s = comicFitScale(bmp.width, bmp.height, constraints…); scaleX = s; scaleY = s }` (use `BoxWithConstraints` to get the viewport size); keep `ContentScale.Fit` for `Screen`.

- [ ] **Step 4: Run; verify pass**

Run: `./gradlew :androidApp:testDebugUnitTest --tests "com.continuum.app.android.ui.screens.reader.ReaderEngineHostSourceTest"`
Expected: PASS.

- [ ] **Step 5: Device QA**

Open a CBZ; verify tap zones page correctly; flip a test build's default to `RightToLeft` and confirm pages advance right-to-left; verify fit modes change page sizing.

- [ ] **Step 6: Commit**

```bash
git add androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/reader/ComicReader.kt \
        androidApp/src/androidUnitTest/kotlin/com/continuum/app/android/ui/screens/reader/ReaderEngineHostSourceTest.kt
git commit -m "feat(comic): config-driven tap zones, RTL layout, fit modes"
```

---

# Part C — PDF Memory + Zoom

Fixes the API24 OOM liability (the `2×/2000px ARGB_8888` hardcode) with a memory-derived render budget, then adds pinch/double-tap zoom. (Task 13 already made the render path OOM-safe.)

---

### Task 23: Memory-aware page render budget

**Files:**
- Create: `androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/reader/PdfRenderBudget.kt`
- Test: `androidApp/src/androidUnitTest/kotlin/com/continuum/app/android/ui/screens/reader/PdfRenderBudgetTest.kt` (new)

**Context:** Replace the hardcoded `width*2`, cap `2000`, `ARGB_8888` with a budget derived from the device memory class: choose a max width and a `Bitmap.Config` (RGB_565 on low-RAM) so a page bitmap can't blow the heap.

- [ ] **Step 1: Write the failing test**

```kotlin
package com.continuum.app.android.ui.screens.reader

import android.graphics.Bitmap
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PdfRenderBudgetTest {
    @Test fun `low memory devices cap width hard and use RGB_565`() {
        val b = pdfRenderBudget(pageWidth = 1200, pageHeight = 1600, memoryClassMb = 48)
        assertTrue(b.targetWidth <= 1200, "must not upscale on low-RAM devices")
        assertEquals(Bitmap.Config.RGB_565, b.config)
    }
    @Test fun `high memory devices allow 2x up to the cap and ARGB_8888`() {
        val b = pdfRenderBudget(pageWidth = 1000, pageHeight = 1400, memoryClassMb = 256)
        assertEquals(2000, b.targetWidth)
        assertEquals(Bitmap.Config.ARGB_8888, b.config)
    }
    @Test fun `never returns a non-positive width`() {
        assertTrue(pdfRenderBudget(0, 0, 16).targetWidth >= 1)
    }
}
```

- [ ] **Step 2: Run; verify fail**

Run: `./gradlew :androidApp:testDebugUnitTest --tests "com.continuum.app.android.ui.screens.reader.PdfRenderBudgetTest"`
Expected: FAIL — unresolved `pdfRenderBudget`. (Bitmap.Config resolves under Robolectric.)

- [ ] **Step 3: Implement**

```kotlin
package com.continuum.app.android.ui.screens.reader

import android.graphics.Bitmap

data class PdfRenderBudget(val targetWidth: Int, val config: Bitmap.Config)

fun pdfRenderBudget(pageWidth: Int, pageHeight: Int, memoryClassMb: Int): PdfRenderBudget {
    val lowMem = memoryClassMb <= 96
    val config = if (lowMem) Bitmap.Config.RGB_565 else Bitmap.Config.ARGB_8888
    val safeW = pageWidth.coerceAtLeast(1)
    val target = if (lowMem) {
        safeW.coerceAtMost(1200) // no upscaling on constrained heaps
    } else {
        (safeW * 2).coerceAtMost(2000)
    }
    return PdfRenderBudget(targetWidth = target.coerceAtLeast(1), config = config)
}
```

- [ ] **Step 4: Run; verify pass**

Run: `./gradlew :androidApp:testDebugUnitTest --tests "com.continuum.app.android.ui.screens.reader.PdfRenderBudgetTest"`
Expected: PASS.

- [ ] **Step 5: Use the budget in `renderPdfPageBitmap`**

Thread the device memory class in (compute once via `(context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).memoryClass` at the `PdfReader` top and pass to `PdfPage`/render). Replace the hardcoded block:

```kotlin
                val budget = pdfRenderBudget(page.width, page.height, memoryClassMb)
                val scale = budget.targetWidth.toFloat() / page.width
                val targetHeight = (page.height * scale).toInt().coerceAtLeast(1)
                val bmp = Bitmap.createBitmap(budget.targetWidth, targetHeight, budget.config)
                bmp.eraseColor(Color.WHITE)
                page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                bmp
```

- [ ] **Step 6: Run module tests; verify pass; device QA**

Run: `./gradlew :androidApp:testDebugUnitTest`
Device QA: open a large multi-page PDF on a low-RAM (API 24) emulator; page rapidly; confirm no OOM / no crash.

- [ ] **Step 7: Commit**

```bash
git add androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/reader/PdfRenderBudget.kt \
        androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/reader/PdfReader.kt \
        androidApp/src/androidUnitTest/kotlin/com/continuum/app/android/ui/screens/reader/PdfRenderBudgetTest.kt
git commit -m "fix(pdf): memory-aware render budget (RGB_565 + width cap on low RAM)"
```

---

### Task 24: Zoom scale clamp + pinch/double-tap (integration)

**Files:**
- Create: `androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/reader/PdfZoom.kt`
- Test: `androidApp/src/androidUnitTest/kotlin/com/continuum/app/android/ui/screens/reader/PdfZoomTest.kt` (new)
- Modify: `androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/reader/PdfReader.kt` (`PdfPage`)

**Context:** Add a pure zoom-state reducer (clamp + double-tap toggle), unit-test it, then wire Compose `transformable` + `detectTapGestures(onDoubleTap)` in `PdfPage`. Pan/zoom is page-local; the pager stays for page turns at scale 1.

- [ ] **Step 1: Write the failing test**

```kotlin
package com.continuum.app.android.ui.screens.reader

import kotlin.test.Test
import kotlin.test.assertEquals

class PdfZoomTest {
    @Test fun `zoom clamps between 1x and 5x`() {
        assertEquals(5f, clampPdfZoom(10f))
        assertEquals(1f, clampPdfZoom(0.2f))
        assertEquals(2.5f, clampPdfZoom(2.5f))
    }
    @Test fun `double-tap toggles between 1x and 2_5x`() {
        assertEquals(2.5f, nextDoubleTapZoom(1f))
        assertEquals(1f, nextDoubleTapZoom(2.5f))
        assertEquals(1f, nextDoubleTapZoom(4f)) // any zoomed state collapses to fit
    }
}
```

- [ ] **Step 2: Run; verify fail**

Run: `./gradlew :androidApp:testDebugUnitTest --tests "com.continuum.app.android.ui.screens.reader.PdfZoomTest"`
Expected: FAIL.

- [ ] **Step 3: Implement**

```kotlin
package com.continuum.app.android.ui.screens.reader

const val PDF_MIN_ZOOM = 1f
const val PDF_MAX_ZOOM = 5f
private const val PDF_DOUBLE_TAP_ZOOM = 2.5f

fun clampPdfZoom(scale: Float): Float = scale.coerceIn(PDF_MIN_ZOOM, PDF_MAX_ZOOM)

fun nextDoubleTapZoom(current: Float): Float =
    if (current > PDF_MIN_ZOOM + 0.01f) PDF_MIN_ZOOM else PDF_DOUBLE_TAP_ZOOM
```

- [ ] **Step 4: Run; verify pass**

Run: `./gradlew :androidApp:testDebugUnitTest --tests "com.continuum.app.android.ui.screens.reader.PdfZoomTest"`
Expected: PASS.

- [ ] **Step 5: Wire gestures in `PdfPage`**

Add zoom/offset state and apply via `graphicsLayer`:

```kotlin
    var scale by remember(pageIndex) { mutableStateOf(1f) }
    var offset by remember(pageIndex) { mutableStateOf(Offset.Zero) }
    val transformState = rememberTransformableState { zoomChange, panChange, _ ->
        scale = clampPdfZoom(scale * zoomChange)
        offset = if (scale > 1f) offset + panChange else Offset.Zero
    }
```

On the page `Box`/`Image`, add `.transformable(transformState)` and `.graphicsLayer { scaleX = scale; scaleY = scale; translationX = offset.x; translationY = offset.y }`, and add `detectTapGestures(onDoubleTap = { scale = nextDoubleTapZoom(scale); offset = Offset.Zero }, onTap = { /* existing thirds via onPageTap */ })`. Keep the existing single-tap thirds behavior only while `scale == 1f` so zoomed panning doesn't page-turn.

- [ ] **Step 6: Device QA**

Open a PDF; pinch-zoom; pan while zoomed; double-tap to toggle; confirm page-turn still works at fit scale and is suppressed while zoomed.

- [ ] **Step 7: Commit**

```bash
git add androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/reader/PdfZoom.kt \
        androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/reader/PdfReader.kt \
        androidApp/src/androidUnitTest/kotlin/com/continuum/app/android/ui/screens/reader/PdfZoomTest.kt
git commit -m "feat(pdf): pinch + double-tap zoom with pan"
```

---

## Final verification

- [ ] Run the full unit suite:

```bash
./gradlew :shared:testDebugUnitTest :android-shared:testDebugUnitTest :androidApp:testDebugUnitTest
```

Expected: all green.

- [ ] Device QA pass (mandatory per roadmap): EPUB open/resume/typography; CBZ paging/direction/fit; PDF paging/zoom on an API 24 emulator; subtitles still display in the video player (Task 1); watch-together survives a host-leave without reconnect storm (Tasks 4–5).

---

## Notes / deferred (out of scope for this plan)

These borrow-map Reading-P1 items were intentionally deferred to keep this combined plan executable; each warrants its own focused plan. Flagged here so coverage is explicit, not silent.

- **Element-level locator (CSS-selector/text-quote anchor) + full bookmarks/highlights/notes:** large; sits on top of Part A (Task 17 delivers the cumulative-offset progress map, which is the testable subset). Bookmarks today persist only a `location` string via `EbookLocalStateStore`; rich annotations need the element anchor first. Plan separately.
- **Nested TOC drawer with auto-expand:** the current `EpubReflowSource` synthesizes a *flat* TOC from spine sections and `SectionsSheet` labels them `"Page N"`. A real nested TOC needs parsing the EPUB `nav.xhtml`/NCX in `EpubReflowSource` (new work) plus an indented drawer. Deferred; a cheap interim improvement is correcting the `SectionsSheet` supporting label for reflow sections.
- **Comic SubsamplingScaleImageView tile rendering, webtoon (vertical) viewer, double-page spreads:** defer to a comic-engine v2 plan. Part B's config already models the `Vertical` direction and `Original` fit mode for that follow-up; Task 22 ships RTL + paged fit without the tiled image view.
- **PDF LRU bitmap cache / pool, adjacent prefetch, fit modes (width/height/original), thumbnails grid:** defer; the memory budget (Task 23) + OOM safety (Task 13) + zoom (Task 24) are the critical A7 fixes. PDF fit modes can reuse the `comicFitScale` shape when added.
- **MOBI/AZW via libmobi:** separate native-packaging decision per the borrow map.
