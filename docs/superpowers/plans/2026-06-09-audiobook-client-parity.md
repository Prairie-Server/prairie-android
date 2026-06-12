# Audiobook Client Parity Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Finish Android audiobook client parity by making downloaded audiobooks openable by other apps, surfacing richer mobile audiobook metadata, and verifying TV audiobook/ebook filtering.

**Architecture:** Keep the existing mobile audiobook player and TV media path. Extend shared download open-target MIME handling, add mobile detail sections that read existing `AudiobookMetadata`, and only touch TV code if tests expose a real parity gap.

**Tech Stack:** Kotlin, Jetpack Compose, Android MediaStore/content URIs, Media3, Koin, Kotlin unit tests, Gradle.

---

## File Structure

- Modify `android-shared/src/androidMain/kotlin/com/continuum/app/common/downloads/DownloadOpenTarget.kt`
  - Owns media-open display name and MIME mapping for externally opened downloads.
- Modify `android-shared/src/androidUnitTest/kotlin/com/continuum/app/common/downloads/DownloadOpenTargetTest.kt`
  - Covers ebook and audiobook MIME/open-target behavior.
- Modify `androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/downloads/DownloadEntryRows.kt`
  - Shows `Open with` for completed audiobook rows that have a local URI, while preserving ebook `Read` behavior.
- Modify `androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/audiobook/AudiobookDetailContent.kt`
  - Shows existing server audiobook metadata fields and optional related sections.
- Modify `androidTvApp/src/androidUnitTest/kotlin/com/continuum/app/tv/ui/util/TvMediaTypeFiltersTest.kt`
  - Strengthens tests proving audiobooks remain visible and ebooks remain hidden.
- Modify TV source files only if the strengthened tests fail.

---

### Task 1: Audiobook Download Open-With Support

**Files:**
- Modify: `android-shared/src/androidUnitTest/kotlin/com/continuum/app/common/downloads/DownloadOpenTargetTest.kt`
- Modify: `android-shared/src/androidMain/kotlin/com/continuum/app/common/downloads/DownloadOpenTarget.kt`
- Modify: `androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/downloads/DownloadEntryRows.kt`

- [ ] **Step 1: Add failing shared MIME/open-target tests**

Replace `DownloadOpenTargetTest` with:

```kotlin
package com.continuum.app.common.downloads

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DownloadOpenTargetTest {
    @Test
    fun `ebook mime types preserve reader formats`() {
        assertEquals("application/epub+zip", mimeTypeForDownloadName("novel.epub", "epub"))
        assertEquals("application/pdf", mimeTypeForDownloadName("manual.pdf", "pdf"))
        assertEquals("application/vnd.comicbook+zip", mimeTypeForDownloadName("comic.cbz", "cbz"))
        assertEquals("application/vnd.comicbook-rar", mimeTypeForDownloadName("comic.cbr", "cbr"))
    }

    @Test
    fun `audiobook mime types preserve original audio formats`() {
        assertEquals("audio/mp4", mimeTypeForDownloadName("book.m4b", "m4b"))
        assertEquals("audio/mp4", mimeTypeForDownloadName("book.m4a", "m4a"))
        assertEquals("audio/mpeg", mimeTypeForDownloadName("book.mp3", "mp3"))
        assertEquals("audio/aac", mimeTypeForDownloadName("book.aac", "aac"))
        assertEquals("audio/flac", mimeTypeForDownloadName("book.flac", "flac"))
        assertEquals("audio/ogg", mimeTypeForDownloadName("book.ogg", "ogg"))
        assertEquals("audio/opus", mimeTypeForDownloadName("book.opus", "opus"))
        assertEquals("audio/wav", mimeTypeForDownloadName("book.wav", "wav"))
    }

    @Test
    fun `mime type falls back to container when display name has no extension`() {
        assertEquals("audio/mp4", mimeTypeForDownloadName("42", "m4b"))
        assertEquals("application/epub+zip", mimeTypeForDownloadName("42", "epub"))
    }

    @Test
    fun `open target only exists for completed local uri`() {
        val complete = DownloadOpenTarget.from(
            isComplete = true,
            localUri = "content://downloads/1",
            displayName = "book.m4b",
            container = "m4b",
        )
        val incomplete = DownloadOpenTarget.from(
            isComplete = false,
            localUri = "content://downloads/1",
            displayName = "book.m4b",
            container = "m4b",
        )
        val missingUri = DownloadOpenTarget.from(
            isComplete = true,
            localUri = null,
            displayName = "book.m4b",
            container = "m4b",
        )

        assertTrue(complete != null)
        assertEquals("book.m4b", complete?.displayName)
        assertEquals("audio/mp4", complete?.mimeType)
        assertFalse(incomplete != null)
        assertFalse(missingUri != null)
    }

    @Test
    fun `open target fallback name keeps original container`() {
        val target = DownloadOpenTarget.from(
            isComplete = true,
            localUri = "content://downloads/2",
            displayName = null,
            container = "mp3",
        )

        assertEquals("download.mp3", target?.displayName)
        assertEquals("audio/mpeg", target?.mimeType)
    }
}
```

- [ ] **Step 2: Run the focused test and verify it fails**

Run:

```bash
./gradlew :android-shared:testDebugUnitTest --tests com.continuum.app.common.downloads.DownloadOpenTargetTest
```

Expected: FAIL because audio MIME mappings currently fall back to `application/octet-stream`, and fallback display names are ebook-shaped.

- [ ] **Step 3: Implement media-aware open target MIME mapping**

In `DownloadOpenTarget.kt`, replace the file with:

```kotlin
package com.continuum.app.common.downloads

import android.webkit.MimeTypeMap

data class DownloadOpenTarget(
    val uriString: String,
    val displayName: String,
    val mimeType: String,
) {
    companion object {
        fun from(
            isComplete: Boolean,
            localUri: String?,
            displayName: String?,
            container: String?,
        ): DownloadOpenTarget? {
            if (!isComplete || localUri.isNullOrBlank()) return null
            val extension = container.orEmpty().removePrefix(".").lowercase()
            val safeName = displayName
                ?.takeIf { it.isNotBlank() }
                ?: "download.${extension.ifBlank { "download" }}"
            return DownloadOpenTarget(
                uriString = localUri,
                displayName = safeName,
                mimeType = mimeTypeForDownloadName(safeName, container),
            )
        }
    }
}

fun mimeTypeForDownloadName(displayName: String, container: String?): String {
    val extension = displayName.substringAfterLast('.', missingDelimiterValue = "")
        .ifBlank { container.orEmpty().removePrefix(".") }
        .lowercase()
    return when (extension) {
        "epub" -> "application/epub+zip"
        "pdf" -> "application/pdf"
        "cbz" -> "application/vnd.comicbook+zip"
        "cbr" -> "application/vnd.comicbook-rar"
        "mobi" -> "application/x-mobipocket-ebook"
        "azw", "azw3" -> "application/vnd.amazon.ebook"
        "m4b", "m4a" -> "audio/mp4"
        "mp3" -> "audio/mpeg"
        "aac" -> "audio/aac"
        "flac" -> "audio/flac"
        "ogg" -> "audio/ogg"
        "opus" -> "audio/opus"
        "wav" -> "audio/wav"
        else -> MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)
            ?: "application/octet-stream"
    }
}
```

- [ ] **Step 4: Show Open with for completed audiobook rows**

In `DownloadEntryRows.kt`, inside `SingleRow`, replace the ebook-only action block:

```kotlin
if (item.mediaType == DownloadMediaType.Ebook && item.isComplete) {
    Spacer(modifier = Modifier.height(8.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (canReadEbook) {
            TextButton(onClick = onReadEbook) {
                Text("Read")
            }
        }
        TextButton(
            onClick = onOpenExternal,
            enabled = item.localUri != null,
        ) {
            Text("Open with")
        }
    }
}
```

with:

```kotlin
val canOpenExternal = item.isComplete &&
    item.localUri != null &&
    (item.mediaType == DownloadMediaType.Ebook || item.mediaType == DownloadMediaType.Audiobook)

if ((item.mediaType == DownloadMediaType.Ebook && item.isComplete) || canOpenExternal) {
    Spacer(modifier = Modifier.height(8.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (canReadEbook) {
            TextButton(onClick = onReadEbook) {
                Text("Read")
            }
        }
        if (canOpenExternal) {
            TextButton(onClick = onOpenExternal) {
                Text("Open with")
            }
        }
    }
}
```

- [ ] **Step 5: Run focused shared tests**

Run:

```bash
./gradlew :android-shared:testDebugUnitTest --tests com.continuum.app.common.downloads.DownloadOpenTargetTest
```

Expected: PASS.

- [ ] **Step 6: Compile mobile after Compose row change**

Run:

```bash
./gradlew :androidApp:compileDebugKotlinAndroid
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 7: Commit Task 1**

Run:

```bash
git add android-shared/src/androidMain/kotlin/com/continuum/app/common/downloads/DownloadOpenTarget.kt \
  android-shared/src/androidUnitTest/kotlin/com/continuum/app/common/downloads/DownloadOpenTargetTest.kt \
  androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/downloads/DownloadEntryRows.kt
git commit -m "feat: open downloaded audiobooks externally"
```

---

### Task 2: Mobile Audiobook Detail Metadata

**Files:**
- Modify: `androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/audiobook/AudiobookDetailContent.kt`

- [ ] **Step 1: Inspect local model field names before editing**

Run:

```bash
sed -n '1,220p' shared/src/commonMain/kotlin/com/continuum/app/model/ebook/EbookMetadata.kt
sed -n '1,140p' shared/src/commonMain/kotlin/com/continuum/app/model/audiobook/AudiobookMetadata.kt
```

Expected: confirm `MediaSeriesGroup.entries`, `MediaRelatedContent.alsoByAuthor`, `MediaRelatedContent.similar`, `AudiobookNarration`, and `MediaPerson.name` field names.

- [ ] **Step 2: Add metadata sections to `AudiobookDetailContent.kt`**

Update imports to include:

```kotlin
import com.continuum.app.model.audiobook.AudiobookNarration
import com.continuum.app.model.ebook.MediaRelatedContent
import com.continuum.app.model.ebook.MediaSeriesGroup
```

After the overview block and before the chapter section, add these conditional sections:

```kotlin
meta?.publisher?.takeIf { it.isNotBlank() }?.let { publisher ->
    item {
        AudiobookInfoLine(label = "Publisher", value = publisher)
    }
}

meta?.series?.let { series ->
    item {
        AudiobookSeriesSection(series = series)
    }
}

if (!meta?.otherNarrations.isNullOrEmpty()) {
    item {
        Text(
            text = "Other Narrations",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
    }
    items(meta?.otherNarrations.orEmpty()) { narration ->
        OtherNarrationRow(narration = narration)
    }
}

meta?.related?.takeIf { related ->
    related.alsoByAuthor.isNotEmpty() || related.similar.isNotEmpty()
}?.let { related ->
    item {
        AudiobookRelatedSection(related = related)
    }
}
```

Add helper composables near `ChapterRow`:

```kotlin
@Composable
private fun AudiobookInfoLine(label: String, value: String) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun AudiobookSeriesSection(series: MediaSeriesGroup) {
    val title = series.name.takeIf { it.isNotBlank() } ?: return
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "Series",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        series.entries.take(3).forEach { entry ->
            val entryLine = listOfNotNull(
                entry.seriesIndex?.let { "Book ${formatSeriesIndex(it)}" },
                entry.title.takeIf { it.isNotBlank() },
            ).joinToString(" · ")
            if (entryLine.isNotBlank()) {
                Text(
                    text = entryLine,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun OtherNarrationRow(narration: AudiobookNarration) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
    ) {
        Text(
            text = narration.title,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        val subtitle = listOfNotNull(
            narration.year?.toString(),
            narration.narrators.takeIf { it.isNotEmpty() }?.joinToString(", "),
        ).joinToString(" · ")
        if (subtitle.isNotBlank()) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun AudiobookRelatedSection(related: MediaRelatedContent) {
    val groups = listOfNotNull(
        related.alsoByAuthor.takeIf { it.isNotEmpty() }?.let { "Also by author: ${it.joinToRelatedTitles()}" },
        related.similar.takeIf { it.isNotEmpty() }?.let { "Similar: ${it.joinToRelatedTitles()}" },
    )
    if (groups.isEmpty()) return
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "Related",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(modifier = Modifier.height(6.dp))
        groups.forEach { line ->
            Text(
                text = line,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private fun List<com.continuum.app.model.ebook.MediaRelatedItem>.joinToRelatedTitles(): String =
    take(3).joinToString(", ") { it.title }.takeIf { it.isNotBlank() }.orEmpty()

private fun formatSeriesIndex(value: Double): String =
    if (value % 1.0 == 0.0) value.toInt().toString() else value.toString()
```

- [ ] **Step 3: Compile mobile**

Run:

```bash
./gradlew :androidApp:compileDebugKotlinAndroid
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Manually scan the detail file for empty labels**

Run:

```bash
rg -n "\"Other Narrations\"|\"Related\"|\"Series\"|\"Publisher\"" androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/audiobook/AudiobookDetailContent.kt
```

Expected: labels exist only in conditional blocks and no empty section is rendered unconditionally.

- [ ] **Step 5: Commit Task 2**

Run:

```bash
git add androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/audiobook/AudiobookDetailContent.kt
git commit -m "feat: enrich mobile audiobook detail"
```

---

### Task 3: TV Filter Hardening And Full Verification

**Files:**
- Modify: `androidTvApp/src/androidUnitTest/kotlin/com/continuum/app/tv/ui/util/TvMediaTypeFiltersTest.kt`
- Modify TV source only if tests fail.

- [ ] **Step 1: Strengthen TV filter tests**

In `TvMediaTypeFiltersTest`, update `identifiesHiddenEbookTypes` to:

```kotlin
@Test
fun identifiesHiddenEbookTypes() {
    assertTrue(isTvHiddenMediaType("ebook"))
    assertTrue(isTvHiddenMediaType("ebooks"))
    assertTrue(isTvHiddenMediaType("EBOOK"))
    assertFalse(isTvHiddenMediaType("audiobook"))
    assertFalse(isTvHiddenMediaType("audiobooks"))
    assertFalse(isTvHiddenMediaType("movie"))
}
```

Add this test:

```kotlin
@Test
fun identifiesAudiobookTypesForTv() {
    assertTrue(isAudiobookMediaType("audiobook"))
    assertTrue(isAudiobookMediaType("audiobooks"))
    assertTrue(isAudiobookMediaType("AUDIOBOOK"))
    assertFalse(isAudiobookMediaType("ebook"))
    assertFalse(isAudiobookMediaType("movie"))
}
```

Update `mapsTvLibraryTypeToCatalogMediaType` to include singular audiobook:

```kotlin
@Test
fun mapsTvLibraryTypeToCatalogMediaType() {
    assertEquals("series", tvCatalogMediaTypeFor("shows"))
    assertEquals("audiobook", tvCatalogMediaTypeFor("audiobook"))
    assertEquals("audiobook", tvCatalogMediaTypeFor("audiobooks"))
    assertEquals("movie", tvCatalogMediaTypeFor("movies"))
    assertEquals("movie", tvCatalogMediaTypeFor("ebooks"))
}
```

- [ ] **Step 2: Run TV focused tests**

Run:

```bash
./gradlew :androidTvApp:testDebugUnitTest --tests com.continuum.app.tv.ui.util.TvMediaTypeFiltersTest
```

Expected: PASS. If this fails, fix `TvMediaTypeFilters.kt` so only ebook/ebooks are hidden and audiobook/audiobooks map to `audiobook`.

- [ ] **Step 3: Run shared download/storage tests**

Run:

```bash
./gradlew :android-shared:testDebugUnitTest --tests com.continuum.app.common.downloads.DownloadOpenTargetTest --tests com.continuum.app.common.downloads.DownloadStorageTest
```

Expected: PASS.

- [ ] **Step 4: Run full required verification**

Run:

```bash
./gradlew :android-shared:testDebugUnitTest :androidApp:compileDebugKotlinAndroid :androidTvApp:testDebugUnitTest :androidTvApp:compileDebugKotlinAndroid
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Scan TV source for accidental ebook exposure**

Run:

```bash
rg -n "ebook|ebooks|BookReader|ReaderScreen" androidTvApp/src/androidMain/kotlin/com/continuum/app/tv -S
```

Expected: only intentional filter references, currently `TvMediaTypeFilters.kt`, or no matches outside filtering.

- [ ] **Step 6: Commit Task 3**

Run:

```bash
git add androidTvApp/src/androidUnitTest/kotlin/com/continuum/app/tv/ui/util/TvMediaTypeFiltersTest.kt
git commit -m "test: harden tv audiobook filters"
```

---

## Final Review Checklist

- [ ] `git diff --check` reports no whitespace errors.
- [ ] `git status --short --branch` shows a clean tree after the final commit.
- [ ] Downloaded audiobook rows expose `Open with` only when complete and backed by a local URI.
- [ ] Audio MIME mapping covers `m4b`, `m4a`, `mp3`, `aac`, `flac`, `ogg`, `opus`, and `wav`.
- [ ] Mobile audiobook detail shows richer metadata only when data exists.
- [ ] TV still hides ebooks and still shows audiobooks.
