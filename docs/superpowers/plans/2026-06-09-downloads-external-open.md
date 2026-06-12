# Downloads External Open Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Expose Android external `Open` for every completed mobile download with a local URI, including movies and episodes, while preserving primary in-app tap behavior.

**Architecture:** Reuse the existing `DownloadOpenTarget` and `DownloadsScreen.openDownloadExternally` path. Add explicit video MIME mappings and tests in `android-shared`, then loosen the Downloads grouped-row `canOpenExternal` predicate from ebook/audiobook-only to complete-with-local-URI.

**Tech Stack:** Kotlin, Android `ACTION_VIEW` intents, MediaStore/FileProvider-backed URIs, Jetpack Compose Material 3, Android unit tests.

---

## File Structure

Modify:

- `android-shared/src/androidUnitTest/kotlin/com/continuum/app/common/downloads/DownloadOpenTargetTest.kt`
  - Add focused tests for video MIME mappings and video fallback names.
- `android-shared/src/androidMain/kotlin/com/continuum/app/common/downloads/DownloadOpenTarget.kt`
  - Add explicit video MIME mapping.
- `androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/downloads/DownloadEntryRows.kt`
  - Show `Open` for any complete row with a nonblank local URI.

Do not modify:

- Download storage location.
- Primary row tap behavior.
- Offline playback/reader logic.
- TV UI.
- Server APIs.

## Task 1: Add Video MIME Tests

**Files:**
- Test: `android-shared/src/androidUnitTest/kotlin/com/continuum/app/common/downloads/DownloadOpenTargetTest.kt`

- [ ] **Step 1: Add failing video MIME mapping test**

In `DownloadOpenTargetTest`, after `audiobook mime types preserve original audio formats`, add:

```kotlin
@Test
fun `video mime types preserve original video formats`() {
    assertEquals("video/mp4", mimeTypeForDownloadName("movie.mp4", "mp4"))
    assertEquals("video/mp4", mimeTypeForDownloadName("movie.m4v", "m4v"))
    assertEquals("video/x-matroska", mimeTypeForDownloadName("movie.mkv", "mkv"))
    assertEquals("video/webm", mimeTypeForDownloadName("movie.webm", "webm"))
    assertEquals("video/x-msvideo", mimeTypeForDownloadName("movie.avi", "avi"))
    assertEquals("video/quicktime", mimeTypeForDownloadName("movie.mov", "mov"))
    assertEquals("video/mp2t", mimeTypeForDownloadName("movie.ts", "ts"))
}
```

- [ ] **Step 2: Add video fallback-name test**

After `open target fallback name keeps original container`, add:

```kotlin
@Test
fun `open target fallback name keeps video container`() {
    val target = DownloadOpenTarget.from(
        isComplete = true,
        localUri = "content://downloads/video",
        displayName = null,
        container = "mkv",
    )

    assertEquals("download.mkv", target?.displayName)
    assertEquals("video/x-matroska", target?.mimeType)
}
```

- [ ] **Step 3: Run the targeted test and confirm failure**

Run:

```bash
./gradlew :android-shared:testDebugUnitTest --tests com.continuum.app.common.downloads.DownloadOpenTargetTest
```

Expected before implementation: fails because at least `mkv` maps to a platform/default MIME or `application/octet-stream` instead of `video/x-matroska`.

- [ ] **Step 4: Commit nothing yet**

Do not commit this red state by itself. Task 2 will make the tests pass and commit both test and implementation.

## Task 2: Implement Video MIME Mapping

**Files:**
- Modify: `android-shared/src/androidMain/kotlin/com/continuum/app/common/downloads/DownloadOpenTarget.kt`
- Test: `android-shared/src/androidUnitTest/kotlin/com/continuum/app/common/downloads/DownloadOpenTargetTest.kt`

- [ ] **Step 1: Add explicit video mappings**

In `mimeTypeForDownloadName`, add these cases after the audio mappings and before the `else`:

```kotlin
"mp4", "m4v" -> "video/mp4"
"mkv" -> "video/x-matroska"
"webm" -> "video/webm"
"avi" -> "video/x-msvideo"
"mov" -> "video/quicktime"
"ts" -> "video/mp2t"
```

The full `when` tail should look like:

```kotlin
"m4b", "m4a" -> "audio/mp4"
"mp3" -> "audio/mpeg"
"aac" -> "audio/aac"
"flac" -> "audio/flac"
"ogg" -> "audio/ogg"
"opus" -> "audio/opus"
"wav" -> "audio/wav"
"mp4", "m4v" -> "video/mp4"
"mkv" -> "video/x-matroska"
"webm" -> "video/webm"
"avi" -> "video/x-msvideo"
"mov" -> "video/quicktime"
"ts" -> "video/mp2t"
else -> MimeTypeMap.getSingleton()?.getMimeTypeFromExtension(extension)
    ?: "application/octet-stream"
```

- [ ] **Step 2: Run targeted test**

Run:

```bash
./gradlew :android-shared:testDebugUnitTest --tests com.continuum.app.common.downloads.DownloadOpenTargetTest
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Commit**

```bash
git add android-shared/src/androidMain/kotlin/com/continuum/app/common/downloads/DownloadOpenTarget.kt android-shared/src/androidUnitTest/kotlin/com/continuum/app/common/downloads/DownloadOpenTargetTest.kt
git commit -m "test: cover external video download targets"
```

## Task 3: Show External Open For Completed Video Downloads

**Files:**
- Modify: `androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/downloads/DownloadEntryRows.kt`

- [ ] **Step 1: Update `canOpenExternal` predicate**

In `SingleRow`, replace:

```kotlin
val canOpenExternal = item.isComplete &&
    !item.localUri.isNullOrBlank() &&
    (item.mediaType == DownloadMediaType.Ebook || item.mediaType == DownloadMediaType.Audiobook)
```

with:

```kotlin
val canOpenExternal = item.isComplete && !item.localUri.isNullOrBlank()
```

This keeps `Open` hidden for incomplete, failed, or missing-local-URI rows, and makes it visible for completed video/audio/book rows.

- [ ] **Step 2: Verify mobile compile**

Run:

```bash
./gradlew :androidApp:compileDebugKotlinAndroid
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Commit**

```bash
git add androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/downloads/DownloadEntryRows.kt
git commit -m "feat: open video downloads externally"
```

## Task 4: Final Verification

**Files:** none.

- [ ] **Step 1: Run final verification**

Run:

```bash
git diff --check
./gradlew :android-shared:testDebugUnitTest :androidApp:compileDebugKotlinAndroid :androidTvApp:testDebugUnitTest :androidTvApp:compileDebugKotlinAndroid
git status --short --branch
```

Expected:

- No whitespace errors.
- Gradle prints `BUILD SUCCESSFUL`.
- Branch is `feature/android-parity-and-media-surfaces`.
- Worktree is clean except intentional committed changes.

- [ ] **Step 2: Manual QA checklist**

On an Android mobile build with completed downloads:

- Completed movie download shows `Open`.
- Completed episode download shows `Open`.
- Completed audiobook download still shows `Open`.
- Completed ebook download still shows `Open`, and readable ebooks still show `Read`.
- Queued/downloading/failed rows do not show `Open`.
- Rows with blank `localUri` do not show `Open`.
- Row tap still performs the existing in-app behavior.
- `Open` launches Android chooser and grants read access.
