# Mobile Ebook Upgrade Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Upgrade Android mobile ebooks with reader controls, richer ebook entry points, and public-file `Open with` handoff while keeping Android TV ebooks hidden.

**Architecture:** Keep `ReaderViewModel` as the reader coordinator and add small, testable models for reader capabilities/settings and downloaded-file export. Existing EPUB/PDF/comic renderers stay format-specific; `ReaderScreen` hosts common controls driven by capability flags. Downloads stay public/original-format, and external handoff uses the public download URI already stored in sidecars.

**Tech Stack:** Kotlin Multiplatform shared models, Android shared download storage, Android Compose Material 3, Android intents, kotlin-test/JUnit, Gradle.

---

## File Structure

Shared/common:

- Modify `shared/src/commonMain/kotlin/com/continuum/app/model/ebook/EbookVersionSelection.kt`: add display helpers for supported/in-app-readable ebook formats.
- Modify `shared/src/commonTest/kotlin/com/continuum/app/model/ebook/EbookVersionSelectionTest.kt`: cover display helpers and requested file behavior.

Android shared:

- Create `android-shared/src/androidMain/kotlin/com/continuum/app/common/ebook/ReaderControls.kt`: reader capability/settings models and pure format mapping.
- Test `android-shared/src/androidUnitTest/kotlin/com/continuum/app/common/ebook/ReaderControlsTest.kt`.
- Create `android-shared/src/androidMain/kotlin/com/continuum/app/common/downloads/DownloadOpenTarget.kt`: pure helpers for downloaded ebook external-open metadata.
- Test `android-shared/src/androidUnitTest/kotlin/com/continuum/app/common/downloads/DownloadOpenTargetTest.kt`.

Android mobile:

- Modify `androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/reader/ReaderViewModel.kt`: expose capabilities/settings, bookmark delete, jump support, and open-with target state.
- Modify `androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/reader/ReaderScreen.kt`: add controls sheet/overlay, bookmark list, jump controls, settings sheet, and open-with action for local downloaded files.
- Modify `androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/reader/EpubReader.kt`: apply EPUB display settings and expose chapter labels where available.
- Modify `androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/reader/PdfReader.kt`: report page count from renderer to `ReaderViewModel`.
- Modify `androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/reader/ComicReader.kt`: report page count from archive page list to `ReaderViewModel`.
- Modify `androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/book/BookDetailContent.kt`: enrich metadata/version display and unsupported states.
- Modify `androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/downloads/DownloadsViewModel.kt`: include media type, file id, display name, local uri, and format labels in `DownloadItem`.
- Modify `androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/downloads/DownloadEntryRows.kt`: show ebook format/original filename and add `Read` / `Open with` actions for completed ebooks.
- Modify `androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/downloads/DownloadsScreen.kt`: accept read/open callbacks and launch Android open intent.
- Modify `androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/MainScreen.kt` and `androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/navigation/AppNavigation.kt` only if Downloads callback threading requires it.
- Modify `androidApp/src/androidMain/AndroidManifest.xml` and create `androidApp/src/androidMain/res/xml/file_paths.xml` only if `file://` download URIs need FileProvider conversion for Android 7-9 external open intents.

---

### Task 1: Reader Capability and Format Helpers

**Files:**
- Create: `android-shared/src/androidMain/kotlin/com/continuum/app/common/ebook/ReaderControls.kt`
- Test: `android-shared/src/androidUnitTest/kotlin/com/continuum/app/common/ebook/ReaderControlsTest.kt`
- Modify: `shared/src/commonMain/kotlin/com/continuum/app/model/ebook/EbookVersionSelection.kt`
- Test: `shared/src/commonTest/kotlin/com/continuum/app/model/ebook/EbookVersionSelectionTest.kt`

- [ ] **Step 1: Add failing reader-controls tests**

Create `android-shared/src/androidUnitTest/kotlin/com/continuum/app/common/ebook/ReaderControlsTest.kt`:

```kotlin
package com.continuum.app.common.ebook

import com.continuum.app.model.book.BookFormat
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ReaderControlsTest {
    @Test
    fun `epub supports text settings and sections`() {
        val caps = ReaderCapabilities.forFormat(BookFormat.Epub)

        assertTrue(caps.supportsTheme)
        assertTrue(caps.supportsTextSize)
        assertTrue(caps.supportsMargins)
        assertTrue(caps.supportsSections)
        assertTrue(caps.supportsBookmarks)
        assertFalse(caps.supportsExternalOnly)
    }

    @Test
    fun `pdf and comic use page controls without text reflow settings`() {
        val pdf = ReaderCapabilities.forFormat(BookFormat.Pdf)
        val cbz = ReaderCapabilities.forFormat(BookFormat.Cbz)

        assertTrue(pdf.supportsPageJump)
        assertTrue(cbz.supportsPageJump)
        assertFalse(pdf.supportsTextSize)
        assertFalse(cbz.supportsMargins)
    }

    @Test
    fun `unknown format is external only`() {
        val caps = ReaderCapabilities.forFormat(BookFormat.Unknown)

        assertTrue(caps.supportsExternalOnly)
        assertFalse(caps.supportsBookmarks)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

```bash
./gradlew :android-shared:testDebugUnitTest --tests com.continuum.app.common.ebook.ReaderControlsTest
```

Expected: fails to compile because `ReaderCapabilities` does not exist.

- [ ] **Step 3: Implement reader capability models**

Create `android-shared/src/androidMain/kotlin/com/continuum/app/common/ebook/ReaderControls.kt`:

```kotlin
package com.continuum.app.common.ebook

import com.continuum.app.model.book.BookFormat

enum class ReaderTheme { System, Light, Dark, Sepia }

data class ReaderDisplaySettings(
    val theme: ReaderTheme = ReaderTheme.System,
    val textScale: Float = 1f,
    val marginScale: Float = 1f,
) {
    fun normalized(): ReaderDisplaySettings = copy(
        textScale = textScale.coerceIn(0.8f, 1.6f),
        marginScale = marginScale.coerceIn(0.75f, 1.5f),
    )
}

data class ReaderSection(
    val index: Int,
    val title: String,
    val location: String,
)

data class ReaderCapabilities(
    val supportsBookmarks: Boolean,
    val supportsPageJump: Boolean,
    val supportsSections: Boolean,
    val supportsTheme: Boolean,
    val supportsTextSize: Boolean,
    val supportsMargins: Boolean,
    val supportsExternalOnly: Boolean = false,
) {
    companion object {
        fun forFormat(format: BookFormat): ReaderCapabilities = when (format) {
            BookFormat.Epub -> ReaderCapabilities(
                supportsBookmarks = true,
                supportsPageJump = true,
                supportsSections = true,
                supportsTheme = true,
                supportsTextSize = true,
                supportsMargins = true,
            )
            BookFormat.Pdf, BookFormat.Cbz, BookFormat.Cbr -> ReaderCapabilities(
                supportsBookmarks = true,
                supportsPageJump = true,
                supportsSections = false,
                supportsTheme = false,
                supportsTextSize = false,
                supportsMargins = false,
            )
            else -> ReaderCapabilities(
                supportsBookmarks = false,
                supportsPageJump = false,
                supportsSections = false,
                supportsTheme = false,
                supportsTextSize = false,
                supportsMargins = false,
                supportsExternalOnly = true,
            )
        }
    }
}
```

- [ ] **Step 4: Add failing shared format-helper tests**

Append to `shared/src/commonTest/kotlin/com/continuum/app/model/ebook/EbookVersionSelectionTest.kt`:

```kotlin
@Test
fun `ebook format labels are user readable`() {
    assertEquals("EPUB", "epub".ebookFormatDisplayName())
    assertEquals("PDF", "pdf".ebookFormatDisplayName())
    assertEquals("Comic Book ZIP", "cbz".ebookFormatDisplayName())
    assertEquals("MOBI", "mobi".ebookFormatDisplayName())
}

@Test
fun `in app readable excludes external only formats`() {
    assertTrue("epub".isInAppReadableEbookFormat())
    assertTrue("pdf".isInAppReadableEbookFormat())
    assertTrue("cbz".isInAppReadableEbookFormat())
    assertFalse("mobi".isInAppReadableEbookFormat())
    assertFalse("azw3".isInAppReadableEbookFormat())
}
```

- [ ] **Step 5: Run shared test to verify it fails**

```bash
./gradlew :shared:testDebugUnitTest --tests com.continuum.app.model.ebook.EbookVersionSelectionTest
```

Expected: fails to compile because the helper functions do not exist.

- [ ] **Step 6: Implement shared format helpers**

Add to `shared/src/commonMain/kotlin/com/continuum/app/model/ebook/EbookVersionSelection.kt`:

```kotlin
private val inAppReadableEbookExtensions = setOf("epub", "pdf", "cbz", "cbr")

fun String.ebookFormatDisplayName(): String = when (trim().lowercase().removePrefix(".")) {
    "epub" -> "EPUB"
    "pdf" -> "PDF"
    "cbz" -> "Comic Book ZIP"
    "cbr" -> "Comic Book RAR"
    "fb2", "fbz" -> "FictionBook"
    "mobi" -> "MOBI"
    "azw", "azw3" -> "Kindle"
    "md" -> "Markdown"
    "" -> "Unknown"
    else -> trim().uppercase()
}

fun String.isInAppReadableEbookFormat(): Boolean =
    trim().lowercase().removePrefix(".") in inAppReadableEbookExtensions

fun FileVersion.ebookFormatDisplayName(): String =
    ebookFormatKey().orEmpty().ebookFormatDisplayName()

fun FileVersion.isInAppReadableEbookVersion(): Boolean =
    ebookFormatKey().orEmpty().isInAppReadableEbookFormat()
```

- [ ] **Step 7: Run tests**

```bash
./gradlew :shared:testDebugUnitTest --tests com.continuum.app.model.ebook.EbookVersionSelectionTest :android-shared:testDebugUnitTest --tests com.continuum.app.common.ebook.ReaderControlsTest
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 8: Commit**

```bash
git add shared/src/commonMain/kotlin/com/continuum/app/model/ebook/EbookVersionSelection.kt shared/src/commonTest/kotlin/com/continuum/app/model/ebook/EbookVersionSelectionTest.kt android-shared/src/androidMain/kotlin/com/continuum/app/common/ebook/ReaderControls.kt android-shared/src/androidUnitTest/kotlin/com/continuum/app/common/ebook/ReaderControlsTest.kt
git commit -m "feat: add ebook reader capability models"
```

### Task 2: Reader Controls, Bookmarks, and Settings

**Files:**
- Modify: `android-shared/src/androidMain/kotlin/com/continuum/app/common/ebook/EbookLocalStateStore.kt`
- Test: `android-shared/src/androidUnitTest/kotlin/com/continuum/app/common/ebook/EbookLocalStateStoreTest.kt`
- Modify: `androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/reader/ReaderViewModel.kt`
- Modify: `androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/reader/ReaderScreen.kt`
- Modify: `androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/reader/EpubReader.kt`
- Modify: `androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/reader/PdfReader.kt`
- Modify: `androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/reader/ComicReader.kt`

- [ ] **Step 1: Add failing local bookmark delete test**

Append to `android-shared/src/androidUnitTest/kotlin/com/continuum/app/common/ebook/EbookLocalStateStoreTest.kt`:

```kotlin
@Test
fun `bookmark can be removed without touching others`() {
    val store = EbookLocalStateStore(tmp.newFolder("filesDir"))

    val keep = store.addBookmark("srv", "prof", "book", "page:1", createdAtMs = 10L)
    val remove = store.addBookmark("srv", "prof", "book", "page:2", createdAtMs = 20L)

    store.removeBookmark("srv", "prof", "book", remove.id)

    assertEquals(listOf(keep.id), store.listBookmarks("srv", "prof", "book").map { it.id })
}
```

- [ ] **Step 2: Run test to verify it fails**

```bash
./gradlew :android-shared:testDebugUnitTest --tests com.continuum.app.common.ebook.EbookLocalStateStoreTest
```

Expected: fails to compile because `removeBookmark` does not exist.

- [ ] **Step 3: Implement local bookmark delete**

Add to `EbookLocalStateStore.kt`:

```kotlin
fun removeBookmark(
    serverId: String,
    profileId: String,
    contentId: String,
    bookmarkId: String,
): List<BookmarkSnapshot> {
    val updated = listBookmarks(serverId, profileId, contentId)
        .filterNot { it.id == bookmarkId }
    writeAtomic(bookmarksFile(serverId, profileId, contentId), json.encodeToString(updated))
    return updated
}
```

- [ ] **Step 4: Run local-state test**

```bash
./gradlew :android-shared:testDebugUnitTest --tests com.continuum.app.common.ebook.EbookLocalStateStoreTest
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Extend reader state and actions**

Modify `ReaderUiState` in `ReaderViewModel.kt`:

```kotlin
data class ReaderUiState(
    val isLoading: Boolean = true,
    val title: String = "",
    val author: String? = null,
    val format: BookFormat = BookFormat.Unknown,
    val fileUrl: String? = null,
    val localUri: String? = null,
    val localDisplayName: String? = null,
    val fileId: Int? = null,
    val pageCount: Int? = null,
    val currentPage: Int = 0,
    val progressLocation: String? = null,
    val progressPercent: Double = 0.0,
    val bookmarks: List<EbookAnnotation> = emptyList(),
    val capabilities: ReaderCapabilities = ReaderCapabilities.forFormat(BookFormat.Unknown),
    val displaySettings: ReaderDisplaySettings = ReaderDisplaySettings(),
    val sections: List<ReaderSection> = emptyList(),
    val isSyncing: Boolean = false,
    val syncError: String? = null,
    val error: String? = null,
)
```

Add imports:

```kotlin
import com.continuum.app.common.ebook.ReaderCapabilities
import com.continuum.app.common.ebook.ReaderDisplaySettings
import com.continuum.app.common.ebook.ReaderSection
```

- [ ] **Step 6: Populate local/open and capability state**

In the successful detail load `_uiState.update`, include:

```kotlin
val format = version.bookFormatFromEbookVersion()
it.copy(
    isLoading = false,
    title = d.title,
    author = d.ebook?.authorNames ?: d.book?.author,
    format = format,
    fileUrl = offlineMedia?.fileUrl ?: ebookReaderRepository.readPath(d.contentId, version.fileId),
    localUri = offlineMedia?.uriString,
    localDisplayName = offlineMedia?.displayName,
    fileId = version.fileId,
    pageCount = d.book?.pageCount,
    capabilities = ReaderCapabilities.forFormat(format),
    currentPage = readerState.currentPage ?: it.currentPage,
    progressLocation = readerState.progressLocation,
    progressPercent = readerState.progressPercent ?: it.progressPercent,
    bookmarks = readerState.bookmarks ?: it.bookmarks,
)
```

In `loadOfflineOnly`, include the same `localUri`, `localDisplayName`, and `capabilities` values from `media`.

- [ ] **Step 7: Add reader action methods**

Add to `ReaderViewModel`:

```kotlin
fun onPageCountKnown(count: Int) {
    if (count <= 0) return
    _uiState.update { it.copy(pageCount = count) }
}

fun jumpToPage(page: Int) {
    onPageChanged(page.coerceAtLeast(0))
}

fun setDisplaySettings(settings: ReaderDisplaySettings) {
    _uiState.update { it.copy(displaySettings = settings.normalized()) }
}

fun setSections(sections: List<ReaderSection>) {
    _uiState.update { it.copy(sections = sections) }
}

fun deleteBookmark(bookmark: EbookAnnotation) {
    viewModelScope.launch {
        val (serverId, profileId) = resolveScope()
        withContext(Dispatchers.IO) {
            localStateStore.removeBookmark(serverId, profileId, contentId, bookmark.id)
        }
        _uiState.update { state ->
            state.copy(bookmarks = state.bookmarks.filterNot { it.id == bookmark.id }, syncError = null)
        }
        if (!bookmark.id.startsWith("local-")) {
            when (ebookReaderRepository.deleteAnnotation(contentId, bookmark.id)) {
                is ApiResult.Success -> Unit
                else -> _uiState.update { it.copy(syncError = "Bookmark delete could not sync.") }
            }
        }
    }
}
```

- [ ] **Step 8: Update renderer signatures**

Update `EpubReader` signature:

```kotlin
fun EpubReader(
    fileUrl: String,
    title: String,
    initialPage: Int = 0,
    settings: ReaderDisplaySettings,
    onPageChanged: (Int) -> Unit,
    onPageCountKnown: (Int) -> Unit,
    onSectionsKnown: (List<ReaderSection>) -> Unit,
)
```

After `EpubBook.open`, call:

```kotlin
LaunchedEffect(b.spine) {
    onPageCountKnown(b.spine.size)
    onSectionsKnown(b.spine.mapIndexed { index, href ->
        ReaderSection(index = index, title = href.substringAfterLast('/').substringBeforeLast('.'), location = "page:$index")
    })
}
```

In `EpubChapter`, apply basic CSS using `settings` by wrapping HTML:

```kotlin
val html = book.readChapterHtml(href)?.withReaderCss(settings) ?: return@AndroidView
```

Add helper:

```kotlin
private fun String.withReaderCss(settings: ReaderDisplaySettings): String {
    val normalized = settings.normalized()
    val marginEm = normalized.marginScale * 1.2f
    val fontPercent = (normalized.textScale * 100).toInt()
    return """
        <html>
        <head><style>
        body { font-size: ${fontPercent}%; margin: ${marginEm}em; line-height: 1.55; }
        img { max-width: 100%; height: auto; }
        </style></head>
        <body>$this</body>
        </html>
    """.trimIndent()
}
```

Update `PdfReader` and `ComicReader` signatures to include:

```kotlin
onPageCountKnown: (Int) -> Unit,
```

Call `onPageCountKnown(renderer.pageCount)` for PDF and `onPageCountKnown(pages.size)` for comic once the count is known.

- [ ] **Step 9: Replace ReaderScreen chrome with controls**

In `ReaderScreen.kt`, add local sheet state:

```kotlin
var showBookmarks by remember { mutableStateOf(false) }
var showSettings by remember { mutableStateOf(false) }
var jumpText by remember { mutableStateOf("") }
```

Add top-row actions:

```kotlin
IconButton(onClick = { showBookmarks = true }, enabled = state.capabilities.supportsBookmarks) {
    Icon(Icons.Default.Bookmarks, contentDescription = "Bookmarks")
}
IconButton(onClick = { showSettings = true }, enabled = !state.capabilities.supportsExternalOnly) {
    Icon(Icons.Default.Tune, contentDescription = "Reader settings")
}
IconButton(onClick = viewModel::addBookmark, enabled = state.fileId != null && state.capabilities.supportsBookmarks) {
    Icon(Icons.Default.BookmarkAdd, contentDescription = "Add bookmark")
}
```

Add progress text below the top row:

```kotlin
Text(
    text = "${(state.progressPercent * 100).toInt()}% · Page ${state.currentPage + 1}${state.pageCount?.let { " of $it" }.orEmpty()}",
    style = MaterialTheme.typography.labelMedium,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
    modifier = Modifier.padding(horizontal = 16.dp),
)
```

Pass new renderer params:

```kotlin
EpubReader(
    fileUrl = state.fileUrl!!,
    title = state.title,
    initialPage = state.currentPage,
    settings = state.displaySettings,
    onPageChanged = viewModel::onPageChanged,
    onPageCountKnown = viewModel::onPageCountKnown,
    onSectionsKnown = viewModel::setSections,
)
```

Use equivalent `onPageCountKnown` for PDF/comic.

- [ ] **Step 10: Add bookmarks and settings sheets**

Create private composables in `ReaderScreen.kt`:

```kotlin
@Composable
private fun BookmarkSheet(
    bookmarks: List<EbookAnnotation>,
    onJumpTo: (EbookAnnotation) -> Unit,
    onDelete: (EbookAnnotation) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Text("Bookmarks", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(16.dp))
        if (bookmarks.isEmpty()) {
            Text("No bookmarks yet", modifier = Modifier.padding(16.dp))
        } else {
            LazyColumn {
                items(bookmarks, key = { it.id }) { bookmark ->
                    ListItem(
                        headlineContent = { Text(bookmark.location) },
                        supportingContent = { Text(bookmark.createdAt) },
                        modifier = Modifier.clickable { onJumpTo(bookmark) },
                        trailingContent = {
                            IconButton(onClick = { onDelete(bookmark) }) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete bookmark")
                            }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun ReaderSettingsSheet(
    settings: ReaderDisplaySettings,
    capabilities: ReaderCapabilities,
    onSettingsChange: (ReaderDisplaySettings) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Text("Reader settings", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(16.dp))
        if (capabilities.supportsTextSize) {
            Slider(
                value = settings.textScale,
                onValueChange = { onSettingsChange(settings.copy(textScale = it).normalized()) },
                valueRange = 0.8f..1.6f,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }
        if (capabilities.supportsMargins) {
            Slider(
                value = settings.marginScale,
                onValueChange = { onSettingsChange(settings.copy(marginScale = it).normalized()) },
                valueRange = 0.75f..1.5f,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }
    }
}
```

Imports include `ModalBottomSheet`, `ListItem`, `LazyColumn`, `items`, `Slider`, `Icons.Default.Bookmarks`, `Icons.Default.Tune`, `Icons.Default.Delete`, and `mutableStateOf`.

- [ ] **Step 11: Run mobile compile**

```bash
./gradlew :android-shared:testDebugUnitTest --tests com.continuum.app.common.ebook.EbookLocalStateStoreTest :androidApp:compileDebugKotlinAndroid
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 12: Commit**

```bash
git add androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/reader android-shared/src/androidMain/kotlin/com/continuum/app/common/ebook/EbookLocalStateStore.kt
git commit -m "feat: add mobile ebook reader controls"
```

### Task 3: Ebook Detail and Entry-Point Polish

**Files:**
- Modify: `androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/book/BookDetailContent.kt`
- Modify: `androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/detail/ItemDetailScreen.kt`
- Modify: `androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/navigation/AppNavigation.kt` only if route callback signatures need adjustment.

- [ ] **Step 1: Enrich BookDetailContent metadata**

In `BookDetailContent.kt`, add imports:

```kotlin
import androidx.compose.foundation.lazy.items
import com.continuum.app.model.ebook.ebookFormatDisplayName
```

Add `versionsSummary` near `format`:

```kotlin
val versionSummaries = detail.versions
    .mapNotNull { version -> version.ebookFormatKey()?.let { key -> version.fileId to key.ebookFormatDisplayName() } }
    .distinctBy { it.first }
```

After publisher text, add series:

```kotlin
ebook?.series?.name?.takeIf { it.isNotBlank() }?.let { seriesName ->
    Spacer(modifier = Modifier.height(6.dp))
    Text(
        text = "Series: $seriesName",
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
```

After the `Read` button, add unsupported copy:

```kotlin
if (selectedVersion == null) {
    Text(
        text = "Silo can read EPUB, PDF, and CBZ in-app. Download this file to open other ebook formats in another reader.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
```

- [ ] **Step 2: Add version list section**

Add a `Versions` section:

```kotlin
if (versionSummaries.isNotEmpty()) {
    item {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = "Versions",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            versionSummaries.forEach { (fileId, label) ->
                Text(
                    text = "$label · file $fileId",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
```

- [ ] **Step 3: Add related ebook rows**

After overview, add:

```kotlin
ebook?.related?.alsoByAuthor?.takeIf { it.isNotEmpty() }?.let { related ->
    item {
        Text(
            text = "Also by this author",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
    }
    items(related, key = { it.contentId }) { item ->
        Text(
            text = listOfNotNull(item.title, item.year?.toString()).joinToString(" · "),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}
```

- [ ] **Step 4: Verify detail read routing**

Inspect `ItemDetailScreen.kt`; confirm ebook branch still calls:

```kotlin
onReadClick = { fileId -> onBookReadClick(detail.contentId, fileId) }
```

If missing or changed, restore it exactly.

- [ ] **Step 5: Compile mobile**

```bash
./gradlew :androidApp:compileDebugKotlinAndroid
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 6: Commit**

```bash
git add androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/book/BookDetailContent.kt androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/detail/ItemDetailScreen.kt androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/navigation/AppNavigation.kt
git commit -m "feat: polish mobile ebook detail"
```

### Task 4: Downloads Ebook Read and Open With

**Files:**
- Create: `android-shared/src/androidMain/kotlin/com/continuum/app/common/downloads/DownloadOpenTarget.kt`
- Test: `android-shared/src/androidUnitTest/kotlin/com/continuum/app/common/downloads/DownloadOpenTargetTest.kt`
- Modify: `androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/downloads/DownloadsViewModel.kt`
- Modify: `androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/downloads/DownloadEntryRows.kt`
- Modify: `androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/downloads/DownloadsScreen.kt`
- Modify: `androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/MainScreen.kt`
- Modify: `androidApp/src/androidMain/AndroidManifest.xml`
- Create: `androidApp/src/androidMain/res/xml/file_paths.xml`

- [ ] **Step 1: Write failing open-target tests**

Create `android-shared/src/androidUnitTest/kotlin/com/continuum/app/common/downloads/DownloadOpenTargetTest.kt`:

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
    }

    @Test
    fun `ebook open target only exists for completed local uri`() {
        val complete = DownloadOpenTarget.from(
            isComplete = true,
            localUri = "content://downloads/1",
            displayName = "book.epub",
            container = "epub",
        )
        val incomplete = DownloadOpenTarget.from(
            isComplete = false,
            localUri = "content://downloads/1",
            displayName = "book.epub",
            container = "epub",
        )

        assertTrue(complete != null)
        assertFalse(incomplete != null)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

```bash
./gradlew :android-shared:testDebugUnitTest --tests com.continuum.app.common.downloads.DownloadOpenTargetTest
```

Expected: fails to compile because `DownloadOpenTarget` does not exist.

- [ ] **Step 3: Implement open target helper**

Create `android-shared/src/androidMain/kotlin/com/continuum/app/common/downloads/DownloadOpenTarget.kt`:

```kotlin
package com.continuum.app.common.downloads

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
            val safeName = displayName?.takeIf { it.isNotBlank() } ?: "ebook.${container.orEmpty().ifBlank { "download" }}"
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
        else -> "application/octet-stream"
    }
}
```

- [ ] **Step 4: Extend DownloadItem**

In `DownloadsViewModel.kt`, add fields:

```kotlin
val mediaType: DownloadMediaType = DownloadMediaType.Unknown,
val fileId: Int? = null,
val localUri: String? = null,
val displayName: String? = null,
val container: String? = null,
```

In `DownloadRecord.toItem()`, compute:

```kotlin
val mediaType = resolveMediaType()
val located = storage.locateSidecarByFileId(mediaFileId)?.let { (serverId, profileId, _) ->
    storage.locateLocalMedia(serverId, profileId, mediaFileId)
}
```

Then include:

```kotlin
mediaType = mediaType,
fileId = mediaFileId,
localUri = meta?.localUri ?: located?.uriString,
displayName = located?.displayName ?: meta?.fileName,
container = meta?.container,
```

- [ ] **Step 5: Add ebook row actions**

Modify `renderSection` and `renderEntry` in `DownloadEntryRows.kt` to accept:

```kotlin
onReadEbook: (DownloadItem) -> Unit,
onOpenExternal: (DownloadItem) -> Unit,
```

Pass these callbacks down to `SingleRow`.

In `SingleRow`, below the size/progress block, add:

```kotlin
if (item.mediaType == DownloadMediaType.Ebook && item.isComplete) {
    Spacer(modifier = Modifier.height(8.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TextButton(onClick = { onReadEbook(item) }) {
            Text("Read")
        }
        TextButton(
            onClick = { onOpenExternal(item) },
            enabled = item.localUri != null,
        ) {
            Text("Open with")
        }
    }
}
```

Also show format/original filename in the subtitle area:

```kotlin
val ebookDetail = if (item.mediaType == DownloadMediaType.Ebook) {
    listOfNotNull(item.displayName, item.container?.uppercase()).joinToString(" · ")
} else {
    item.subtitle
}
```

Use `ebookDetail` instead of `item.subtitle`.

- [ ] **Step 6: Thread Downloads callbacks**

In `DownloadsScreen.kt`, update signature:

```kotlin
fun DownloadsScreen(
    onItemClick: (String) -> Unit,
    onReadEbook: (String, Int?) -> Unit = { _, _ -> },
    showTopBar: Boolean = false,
    onBackClick: (() -> Unit)? = null,
    contentTopPadding: Dp = 0.dp,
    viewModel: DownloadsViewModel = koinViewModel(),
)
```

Inside composition:

```kotlin
val context = LocalContext.current
```

Pass callbacks to `renderSection`:

```kotlin
onReadEbook = { item -> onReadEbook(item.contentId, item.fileId) },
onOpenExternal = { item -> context.openDownloadExternally(item) },
```

Add helper in `DownloadsScreen.kt`:

```kotlin
private fun Context.openDownloadExternally(item: DownloadItem) {
    val target = DownloadOpenTarget.from(
        isComplete = item.isComplete,
        localUri = item.localUri,
        displayName = item.displayName,
        container = item.container,
    ) ?: return
    val uri = if (target.uriString.startsWith("file://")) {
        androidx.core.content.FileProvider.getUriForFile(
            this,
            "$packageName.fileprovider",
            java.io.File(target.uriString.removePrefix("file://")),
        )
    } else {
        Uri.parse(target.uriString)
    }
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, target.mimeType)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    startActivity(Intent.createChooser(intent, "Open ${target.displayName} with"))
}
```

Imports:

```kotlin
import android.content.Intent
import android.net.Uri
import com.continuum.app.common.downloads.DownloadOpenTarget
```

- [ ] **Step 7: Add FileProvider fallback for pre-Q file URIs**

Create `androidApp/src/androidMain/res/xml/file_paths.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<paths xmlns:android="http://schemas.android.com/apk/res/android">
    <external-path
        name="silo_downloads"
        path="Download/Silo/" />
    <external-path
        name="silo_movies"
        path="Movies/Silo/" />
    <external-path
        name="silo_music"
        path="Music/Silo/" />
</paths>
```

Add inside `<application>` in `androidApp/src/androidMain/AndroidManifest.xml`:

```xml
<provider
    android:name="androidx.core.content.FileProvider"
    android:authorities="${applicationId}.fileprovider"
    android:exported="false"
    android:grantUriPermissions="true">
    <meta-data
        android:name="android.support.FILE_PROVIDER_PATHS"
        android:resource="@xml/file_paths" />
</provider>
```

This provider is only used when the stored URI is `file://`. Android 10+ MediaStore downloads continue to use their existing `content://` URI.

- [ ] **Step 8: Wire MainScreen Downloads tab**

In `MainScreen.kt`, where `DownloadsScreen` is called, add:

```kotlin
onReadEbook = { contentId, fileId ->
    navController.navigate(Route.BookReader(contentId, fileId).route)
},
```

- [ ] **Step 9: Run tests and compile**

```bash
./gradlew :android-shared:testDebugUnitTest --tests com.continuum.app.common.downloads.DownloadOpenTargetTest :androidApp:compileDebugKotlinAndroid
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 10: Commit**

```bash
git add android-shared/src/androidMain/kotlin/com/continuum/app/common/downloads/DownloadOpenTarget.kt android-shared/src/androidUnitTest/kotlin/com/continuum/app/common/downloads/DownloadOpenTargetTest.kt androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/downloads androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/MainScreen.kt androidApp/src/androidMain/AndroidManifest.xml androidApp/src/androidMain/res/xml/file_paths.xml
git commit -m "feat: add ebook download open actions"
```

### Task 5: Final Verification and Follow-Up Capture

**Files:**
- Modify docs only if implementation reveals a real correction.

- [ ] **Step 1: Run full verification**

```bash
./gradlew :shared:testDebugUnitTest :android-shared:testDebugUnitTest :androidApp:compileDebugKotlinAndroid :androidTvApp:testDebugUnitTest :androidTvApp:compileDebugKotlinAndroid
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 2: Confirm TV still hides ebooks**

```bash
rg -n "ebook|ebooks|BookReader|ReaderScreen" androidTvApp/src/androidMain/kotlin/com/continuum/app/tv -S
```

Expected: only defensive filtering/metadata mentions; no TV reader route or ebook surface.

- [ ] **Step 3: Inspect worktree**

```bash
git status --short --branch
```

Expected: clean worktree on `feature/android-parity-and-media-surfaces`.

- [ ] **Step 4: Capture follow-ups in final response**

Mention deferred items if not implemented:

- Highlight/note annotations.
- Rich EPUB table-of-contents parsing beyond spine-derived entries.
- Advanced typography presets.
- PDF reflow/crop controls.
- Cross-device sync conflict UI.

---

## Self-Review

- Spec coverage: Task 1 covers capabilities/format helpers; Task 2 covers reader controls, bookmarks, settings, progress/page controls; Task 3 covers detail/library entry polish; Task 4 covers downloads, original filename/format visibility, public-file `Read`, and external open; Task 5 covers verification and follow-ups.
- Scope check: Android TV ebook support is not added. Admin remains untouched. `.bin` fallback is not reintroduced.
- Type consistency: `ReaderCapabilities`, `ReaderDisplaySettings`, `ReaderSection`, `EbookLocalStateStore.removeBookmark`, and `DownloadOpenTarget` are introduced before any mobile code consumes them.
