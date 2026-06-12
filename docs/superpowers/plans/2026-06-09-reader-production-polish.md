# Reader Production Polish Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make mobile reader format support honest and harden CBZ archive loading without adding CBR/RAR rendering or ebooks to Android TV.

**Architecture:** Keep format truth in shared helpers and capabilities: only EPUB/PDF/CBZ are in-app readable; external-only formats remain downloadable/openable by other apps. Add a small Android reader archive helper used by `ComicReader` so CBZ page discovery is testable and archive errors render as reader states instead of escaping composition.

**Tech Stack:** Kotlin, Jetpack Compose, Android unit tests, `java.util.zip.ZipFile`, Gradle.

---

## File Structure

- Modify `androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/reader/ComicReader.kt`
  - Add testable CBZ page loading result types and helper.
  - Render loading/error/empty/loaded states explicitly.
- Modify `androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/reader/ReaderScreen.kt`
  - Dispatch only `BookFormat.Cbz` to `ComicReader`; let `BookFormat.Cbr` fall through to external-only unsupported copy.
- Create `androidApp/src/androidUnitTest/kotlin/com/continuum/app/android/ui/screens/reader/ComicArchiveLoaderTest.kt`
  - Test CBZ page discovery, empty archives, and invalid archives.
- Modify `shared/src/commonTest/kotlin/com/continuum/app/model/ebook/EbookVersionSelectionTest.kt`
  - Add explicit CBR format truth assertions if current coverage is not direct enough.
- Modify `android-shared/src/androidUnitTest/kotlin/com/continuum/app/common/ebook/ReaderControlsTest.kt`
  - Keep/extend external-only CBR capability coverage if needed.

---

### Task 1: Add CBZ Archive Loader Tests

**Files:**
- Create: `androidApp/src/androidUnitTest/kotlin/com/continuum/app/android/ui/screens/reader/ComicArchiveLoaderTest.kt`

- [ ] **Step 1: Write failing tests**

Create:

```kotlin
package com.continuum.app.android.ui.screens.reader

import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class ComicArchiveLoaderTest {

    @Test
    fun `loads image pages in lexicographic order`() {
        val file = createZip(
            "pages/002.png" to byteArrayOf(2),
            "pages/001.jpg" to byteArrayOf(1),
            "notes/info.txt" to byteArrayOf(0),
        )

        val result = loadComicArchivePages(file)

        val loaded = assertIs<ComicArchiveLoadResult.Loaded>(result)
        assertEquals(listOf("pages/001.jpg", "pages/002.png"), loaded.pages.map { it.entryName })
        assertEquals(listOf(0, 1), loaded.pages.map { it.index })
    }

    @Test
    fun `empty archive returns empty result`() {
        val file = createZip("notes/info.txt" to byteArrayOf(0))

        assertIs<ComicArchiveLoadResult.Empty>(loadComicArchivePages(file))
    }

    @Test
    fun `invalid archive returns error result`() {
        val file = File.createTempFile("invalid-comic", ".cbz").apply {
            writeText("not a zip")
            deleteOnExit()
        }

        val result = loadComicArchivePages(file)

        assertIs<ComicArchiveLoadResult.Error>(result)
    }

    private fun createZip(vararg entries: Pair<String, ByteArray>): File {
        val file = File.createTempFile("comic", ".cbz")
        file.deleteOnExit()
        ZipOutputStream(file.outputStream()).use { zip ->
            entries.forEach { (name, bytes) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(bytes)
                zip.closeEntry()
            }
        }
        return file
    }
}
```

- [ ] **Step 2: Run tests to verify red**

Run:

```bash
./gradlew :androidApp:testDebugUnitTest --tests "com.continuum.app.android.ui.screens.reader.ComicArchiveLoaderTest"
```

Expected: compile failure because `loadComicArchivePages`, `ComicArchiveLoadResult`, and `ComicArchivePage` do not exist.

---

### Task 2: Implement Testable CBZ Archive Loading

**Files:**
- Modify: `androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/reader/ComicReader.kt`

- [ ] **Step 1: Add loader result types and helper**

Add near the bottom of `ComicReader.kt`:

```kotlin
internal data class ComicArchivePage(
    val index: Int,
    val entryName: String,
)

internal sealed interface ComicArchiveLoadResult {
    data class Loaded(val pages: List<ComicArchivePage>) : ComicArchiveLoadResult
    data object Empty : ComicArchiveLoadResult
    data class Error(val message: String) : ComicArchiveLoadResult
}

internal fun loadComicArchivePages(file: File): ComicArchiveLoadResult =
    runCatching {
        val entries = listSortedImageEntries(file)
        if (entries.isEmpty()) {
            ComicArchiveLoadResult.Empty
        } else {
            ComicArchiveLoadResult.Loaded(
                entries.mapIndexed { index, entryName ->
                    ComicArchivePage(index = index, entryName = entryName)
                },
            )
        }
    }.getOrElse { throwable ->
        ComicArchiveLoadResult.Error(
            throwable.message?.takeIf { it.isNotBlank() } ?: "Could not open comic archive.",
        )
    }
```

- [ ] **Step 2: Run loader tests**

Run:

```bash
./gradlew :androidApp:testDebugUnitTest --tests "com.continuum.app.android.ui.screens.reader.ComicArchiveLoaderTest"
```

Expected: pass.

---

### Task 3: Render CBZ Loader States And Remove CBR Reader Dispatch

**Files:**
- Modify: `androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/reader/ComicReader.kt`
- Modify: `androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/reader/ReaderScreen.kt`

- [ ] **Step 1: Update `ComicReader` to use loader result**

Replace direct `listSortedImageEntries(file)` use with:

```kotlin
val archiveState by produceState<ComicArchiveLoadResult?>(initialValue = null, file) {
    value = withContext(Dispatchers.IO) { loadComicArchivePages(file) }
}
when (val result = archiveState) {
    null -> {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }
    is ComicArchiveLoadResult.Error -> {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Could not open this comic archive.")
        }
        return
    }
    ComicArchiveLoadResult.Empty -> {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No readable images found in this comic archive.")
        }
        return
    }
    is ComicArchiveLoadResult.Loaded -> {
        val pages = result.pages
        LaunchedEffect(pages.size) {
            onPageCountKnown(pages.size)
        }
        // Pager uses pages[pageIndex].entryName
    }
}
```

Keep pager behavior identical except replacing string entry names with `ComicArchivePage.entryName`.

- [ ] **Step 2: Remove the CBR placeholder from `ComicReader`**

Delete the early `if (format == BookFormat.Cbr)` branch and remove `format` from `ComicReader` parameters. `ComicReader` should be a CBZ renderer only.

- [ ] **Step 3: Update `ReaderScreen` dispatch**

Change:

```kotlin
BookFormat.Cbz, BookFormat.Cbr -> ComicReader(
    fileUrl = state.fileUrl!!,
    title = state.title,
    format = state.format,
    initialPage = state.currentPage,
    onPageChanged = viewModel::onPageChanged,
    onPageCountKnown = viewModel::onPageCountKnown,
)
```

to:

```kotlin
BookFormat.Cbz -> ComicReader(
    fileUrl = state.fileUrl!!,
    title = state.title,
    initialPage = state.currentPage,
    onPageChanged = viewModel::onPageChanged,
    onPageCountKnown = viewModel::onPageCountKnown,
)
```

CBR falls into the existing unsupported-format branch.

- [ ] **Step 4: Compile mobile**

Run:

```bash
./gradlew :androidApp:compileDebugKotlinAndroid
```

Expected: pass.

---

### Task 4: Verify Reader Format Truth

**Files:**
- Modify only tests if current coverage needs one more explicit assertion.

- [ ] **Step 1: Run focused reader/version tests**

Run:

```bash
./gradlew :shared:testDebugUnitTest --tests "com.continuum.app.model.ebook.EbookVersionSelectionTest" :android-shared:testDebugUnitTest --tests "com.continuum.app.common.ebook.ReaderControlsTest" :androidApp:testDebugUnitTest --tests "com.continuum.app.android.ui.screens.reader.ComicArchiveLoaderTest"
```

Expected: pass.

- [ ] **Step 2: Run full verification**

Run:

```bash
git diff --check && ./gradlew :shared:testDebugUnitTest :android-shared:testDebugUnitTest :androidApp:testDebugUnitTest :androidApp:compileDebugKotlinAndroid :androidTvApp:testDebugUnitTest :androidTvApp:compileDebugKotlinAndroid
```

Expected: pass.

- [ ] **Step 3: Commit**

Run:

```bash
git add androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/reader/ComicReader.kt androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/reader/ReaderScreen.kt androidApp/src/androidUnitTest/kotlin/com/continuum/app/android/ui/screens/reader/ComicArchiveLoaderTest.kt
git commit -m "fix: harden comic reader format handling"
```

---

## Self-Review

- Spec coverage: CBR remains external-only through existing shared tests and the ReaderScreen dispatch change; CBZ gets loader tests and explicit error/empty states; TV remains untouched except final compile/test.
- Placeholder scan: no incomplete steps, `TODO`, or unspecified tests are present.
- Type consistency: all new symbols are defined in Task 2 before use by tests and UI.
