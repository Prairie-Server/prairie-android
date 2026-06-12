# Downloads Production Polish Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Preserve original downloaded file formats, make public storage/open-with behavior consistent across video/audio/reading files, and protect it with Android-shared tests.

**Architecture:** The existing download path remains intact: `DownloadStorage` writes public media bytes and private sidecars, while mobile Downloads uses `DownloadOpenTarget` to launch external apps. This pass removes MIME mapping drift by using the existing shared `mimeTypeForDownloadName()` resolver from both Open With and MediaStore creation, then expands tests for literary/audio/video formats and public collection routing.

**Tech Stack:** Kotlin, Android MediaStore, Android `MimeTypeMap`, Kotlin test, Gradle Android unit tests.

---

## File Structure

- Modify `android-shared/src/androidMain/kotlin/com/continuum/app/common/downloads/DownloadOpenTarget.kt`
  - Owns `DownloadOpenTarget.from()`.
  - Owns the shared `mimeTypeForDownloadName(displayName, container)` resolver used by Downloads and MediaStore.
- Modify `android-shared/src/androidMain/kotlin/com/continuum/app/common/downloads/DownloadStorage.kt`
  - Keeps public file creation/deletion.
  - Uses `mimeTypeForDownloadName()` in `MediaStorePublicDownloadStore.create()`.
  - Keeps `PublicDownloadCollection.forMediaType()` as the collection routing authority.
- Modify `android-shared/src/androidUnitTest/kotlin/com/continuum/app/common/downloads/DownloadOpenTargetTest.kt`
  - Extends existing MIME and open-target coverage.
- Create `android-shared/src/androidUnitTest/kotlin/com/continuum/app/common/downloads/PublicDownloadCollectionTest.kt`
  - Tests public collection routing by persisted download media type.

---

### Task 1: Expand Download MIME And Open-Target Tests

**Files:**
- Modify: `android-shared/src/androidUnitTest/kotlin/com/continuum/app/common/downloads/DownloadOpenTargetTest.kt`

- [ ] **Step 1: Add missing literary format assertions**

Add these assertions inside `ebook mime types preserve reader formats`:

```kotlin
assertEquals("application/x-mobipocket-ebook", mimeTypeForDownloadName("book.mobi", "mobi"))
assertEquals("application/vnd.amazon.ebook", mimeTypeForDownloadName("book.azw", "azw"))
assertEquals("application/vnd.amazon.ebook", mimeTypeForDownloadName("book.azw3", "azw3"))
assertEquals("application/x-fictionbook+xml", mimeTypeForDownloadName("book.fb2", "fb2"))
assertEquals("application/x-fictionbook+xml", mimeTypeForDownloadName("book.fbz", "fbz"))
assertEquals("text/markdown", mimeTypeForDownloadName("notes.md", "md"))
```

- [ ] **Step 2: Add container fallback assertions for dotted/uppercase containers**

Add to `mime type falls back to container when display name has no extension`:

```kotlin
assertEquals("video/x-matroska", mimeTypeForDownloadName("42", ".MKV"))
assertEquals("text/markdown", mimeTypeForDownloadName("42", "MD"))
```

- [ ] **Step 3: Add an open-target assertion for external video playback**

Add a test:

```kotlin
@Test
fun `open target supports completed video downloads`() {
    val target = DownloadOpenTarget.from(
        isComplete = true,
        localUri = "content://media/external/video/media/7",
        displayName = "episode.mkv",
        container = "mkv",
    )

    assertEquals("content://media/external/video/media/7", target?.uriString)
    assertEquals("episode.mkv", target?.displayName)
    assertEquals("video/x-matroska", target?.mimeType)
}
```

- [ ] **Step 4: Run tests and verify the missing formats fail before implementation**

Run:

```bash
./gradlew :android-shared:testDebugUnitTest --tests "com.continuum.app.common.downloads.DownloadOpenTargetTest"
```

Expected: fail on the new `fb2`, `fbz`, and `md` MIME assertions until the resolver is expanded.

---

### Task 2: Add Public Collection Routing Tests

**Files:**
- Create: `android-shared/src/androidUnitTest/kotlin/com/continuum/app/common/downloads/PublicDownloadCollectionTest.kt`

- [ ] **Step 1: Create routing tests**

Create the file with:

```kotlin
package com.continuum.app.common.downloads

import com.continuum.app.model.download.DownloadMediaType
import kotlin.test.Test
import kotlin.test.assertEquals

class PublicDownloadCollectionTest {

    @Test
    fun `video media routes to public movies collection`() {
        assertEquals(PublicDownloadCollection.Video, PublicDownloadCollection.forMediaType(DownloadMediaType.Movie.wire))
        assertEquals(PublicDownloadCollection.Video, PublicDownloadCollection.forMediaType(DownloadMediaType.TvShow.wire))
    }

    @Test
    fun `audiobooks route to public music collection`() {
        assertEquals(PublicDownloadCollection.Audio, PublicDownloadCollection.forMediaType(DownloadMediaType.Audiobook.wire))
    }

    @Test
    fun `reading and unknown media route to public downloads collection`() {
        assertEquals(PublicDownloadCollection.Downloads, PublicDownloadCollection.forMediaType(DownloadMediaType.Ebook.wire))
        assertEquals(PublicDownloadCollection.Downloads, PublicDownloadCollection.forMediaType(DownloadMediaType.Unknown.wire))
        assertEquals(PublicDownloadCollection.Downloads, PublicDownloadCollection.forMediaType("comic"))
        assertEquals(PublicDownloadCollection.Downloads, PublicDownloadCollection.forMediaType(null))
    }
}
```

- [ ] **Step 2: Run the new test**

Run:

```bash
./gradlew :android-shared:testDebugUnitTest --tests "com.continuum.app.common.downloads.PublicDownloadCollectionTest"
```

Expected: pass, proving collection routing already matches the design.

---

### Task 3: Unify MIME Resolution

**Files:**
- Modify: `android-shared/src/androidMain/kotlin/com/continuum/app/common/downloads/DownloadOpenTarget.kt`
- Modify: `android-shared/src/androidMain/kotlin/com/continuum/app/common/downloads/DownloadStorage.kt`

- [ ] **Step 1: Expand the shared MIME resolver**

In `mimeTypeForDownloadName()`, normalize the extension and add the missing literary mappings:

```kotlin
val extension = displayName.substringAfterLast('.', missingDelimiterValue = "")
    .ifBlank { container.orEmpty().removePrefix(".") }
    .lowercase()
```

The `when` block should include:

```kotlin
"fb2", "fbz" -> "application/x-fictionbook+xml"
"md", "markdown" -> "text/markdown"
```

- [ ] **Step 2: Wire MediaStore to the shared resolver**

In `MediaStorePublicDownloadStore.create()`, replace:

```kotlin
put(MediaStore.MediaColumns.MIME_TYPE, mimeTypeFor(displayName, container))
```

with:

```kotlin
put(MediaStore.MediaColumns.MIME_TYPE, mimeTypeForDownloadName(displayName, container))
```

- [ ] **Step 3: Remove the duplicate private resolver and unused import**

Delete `private fun mimeTypeFor(displayName: String, container: String?): String` from `MediaStorePublicDownloadStore`.

Remove this import from `DownloadStorage.kt`:

```kotlin
import android.webkit.MimeTypeMap
```

- [ ] **Step 4: Run focused Android-shared tests**

Run:

```bash
./gradlew :android-shared:testDebugUnitTest --tests "com.continuum.app.common.downloads.DownloadOpenTargetTest" --tests "com.continuum.app.common.downloads.PublicDownloadCollectionTest"
```

Expected: pass.

- [ ] **Step 5: Commit implementation**

Run:

```bash
git add android-shared/src/androidMain/kotlin/com/continuum/app/common/downloads/DownloadOpenTarget.kt android-shared/src/androidMain/kotlin/com/continuum/app/common/downloads/DownloadStorage.kt android-shared/src/androidUnitTest/kotlin/com/continuum/app/common/downloads/DownloadOpenTargetTest.kt android-shared/src/androidUnitTest/kotlin/com/continuum/app/common/downloads/PublicDownloadCollectionTest.kt
git commit -m "feat: polish public download open targets"
```

---

### Task 4: Full Verification

**Files:**
- No edits.

- [ ] **Step 1: Run diff whitespace check**

Run:

```bash
git diff --check
```

Expected: no output and exit 0.

- [ ] **Step 2: Run full suite**

Run:

```bash
./gradlew :shared:testDebugUnitTest :android-shared:testDebugUnitTest :androidApp:testDebugUnitTest :androidApp:compileDebugKotlinAndroid :androidTvApp:testDebugUnitTest :androidTvApp:compileDebugKotlinAndroid
```

Expected: all tasks complete successfully.

- [ ] **Step 3: Confirm status**

Run:

```bash
git status --short
```

Expected: no unstaged implementation changes after commit.

---

## Self-Review

- Spec coverage: original-format MIME behavior is covered by Task 1 and Task 3; Open With target behavior is covered by Task 1; public collection routing is covered by Task 2; TV is protected by Task 4 compile/test without adding ebook UI to TV.
- Placeholder scan: no `TBD`, `TODO`, `implement later`, or unspecified test work remains.
- Type consistency: the plan uses existing `DownloadOpenTarget`, `mimeTypeForDownloadName`, `MediaStorePublicDownloadStore`, `PublicDownloadCollection`, and `DownloadMediaType` symbols from the current codebase.
