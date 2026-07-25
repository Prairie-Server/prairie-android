# Reading Foundation Best-Of-Source Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the first best-of-source Reading slice: a premium mobile-only reader foundation with formal engine policy, immersive shell state, and first-class external-original handling.

**Architecture:** Keep the working Prairie-native renderers, but separate target selection, engine dispatch, and reader chrome. `ReaderViewModel` owns book state and progress; `ReaderEngineHost` mounts the existing reflow/PDF/CBZ engines or an external-open panel; `ReaderShell` owns immersive chrome, sheets, gestures, and shared controls.

**Tech Stack:** Kotlin Multiplatform shared models, Android/Kotlin shared reader controls, Jetpack Compose Material3, Android WebView reflow engine, Android `PdfRenderer`, ZIP-backed CBZ reader, kotlin-test/JUnit unit tests.

---

## Constraints

- Work on branch `feature/production-playback-architecture`.
- Keep Android 7 support. New APIs must be guarded or avoided.
- Keep ebooks, comics, manga, and Reading out of Android TV.
- Do not change video playback internals in this phase.
- Keep completed downloads public, discoverable, and original-format.
- MOBI, AZW, AZW3, and CBR are not native readers in this phase; they are external-original reader targets.

## File Structure

- Modify `shared/src/commonMain/kotlin/com/continuum/app/model/ebook/EbookVersionSelection.kt`
  - Add `ReaderVersionTarget` and `chooseReaderVersion()` so reader routing can select both in-app and external-only original files.
- Modify `shared/src/commonTest/kotlin/com/continuum/app/model/ebook/EbookVersionSelectionTest.kt`
  - Lock selection behavior for requested external-only versions and no-in-app libraries.
- Create `android-shared/src/androidMain/kotlin/com/continuum/app/common/ebook/ReaderEnginePolicy.kt`
  - Own `ReaderEngineKind`, `ReaderContentClass`, and policy mapping from format/read mode to engine.
- Modify `android-shared/src/androidMain/kotlin/com/continuum/app/common/ebook/ReaderControls.kt`
  - Add engine policy to `ReaderCapabilities` without changing existing capability semantics.
- Create `android-shared/src/androidUnitTest/kotlin/com/continuum/app/common/ebook/ReaderEnginePolicyTest.kt`
  - Lock EPUB/FB2/TXT/MD as reflow, PDF as fixed document, CBZ as comic/manga, MOBI/AZW/AZW3/CBR as external.
- Create `android-shared/src/androidMain/kotlin/com/continuum/app/common/ebook/ReaderShellState.kt`
  - Pure reducer for immersive chrome and active sheet state.
- Create `android-shared/src/androidUnitTest/kotlin/com/continuum/app/common/ebook/ReaderShellStateTest.kt`
  - Test chrome toggles, sheet opening, and auto-hide behavior.
- Modify `androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/reader/ReaderViewModel.kt`
  - Use `chooseReaderVersion()` and expose enough state for external reader targets.
- Create `androidApp/src/androidUnitTest/kotlin/com/continuum/app/android/ui/screens/reader/ReaderViewModelReaderTargetSourceTest.kt`
  - Source guard that prevents falling back to in-app-only target selection.
- Create `androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/reader/ReaderEngineHost.kt`
  - Dispatch to `ReflowableReader`, `PdfReader`, `ComicReader`, or `ExternalReadingPanel`.
- Create `androidApp/src/androidUnitTest/kotlin/com/continuum/app/android/ui/screens/reader/ReaderEngineHostSourceTest.kt`
  - Source guard for engine dispatch and external-open behavior.
- Create `androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/reader/ReaderShell.kt`
  - Immersive reader chrome, top overlay, bottom progress overlay, sheets, tap-zone wiring.
- Modify `androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/reader/ReaderScreen.kt`
  - Reduce to state collection plus `ReaderShell` and `ReaderEngineHost`.
- Create `androidApp/src/androidUnitTest/kotlin/com/continuum/app/android/ui/screens/reader/ReaderScreenStructureTest.kt`
  - Source guard that keeps `ReaderScreen` thin.

## Task 1: Reader Target Selection And Engine Policy

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/continuum/app/model/ebook/EbookVersionSelection.kt`
- Modify: `shared/src/commonTest/kotlin/com/continuum/app/model/ebook/EbookVersionSelectionTest.kt`
- Create: `android-shared/src/androidMain/kotlin/com/continuum/app/common/ebook/ReaderEnginePolicy.kt`
- Modify: `android-shared/src/androidMain/kotlin/com/continuum/app/common/ebook/ReaderControls.kt`
- Create: `android-shared/src/androidUnitTest/kotlin/com/continuum/app/common/ebook/ReaderEnginePolicyTest.kt`

- [ ] **Step 1: Add failing reader-target tests**

Append these tests to `shared/src/commonTest/kotlin/com/continuum/app/model/ebook/EbookVersionSelectionTest.kt`. Add imports for `kotlin.test.assertNotNull` and `kotlin.test.assertFalse` if missing.

```kotlin
@Test
fun requestedExternalOnlyVersionIsAReaderTarget() {
    val target = chooseReaderVersion(
        versions = listOf(FileVersion(fileId = 7, fileName = "book.mobi", container = "mobi")),
        requestedFileId = 7,
    )

    assertNotNull(target)
    assertEquals(7, target.version.fileId)
    assertEquals(BookFormat.Mobi, target.format)
    assertEquals(EbookReadMode.ExternalOnly, target.support.readMode)
    assertFalse(target.support.canReadInApp)
}

@Test
fun readerTargetFallsBackToExternalOriginalWhenNoInAppFormatExists() {
    val target = chooseReaderVersion(
        versions = listOf(
            FileVersion(fileId = 3, fileName = "book.azw3", container = "azw3"),
            FileVersion(fileId = 4, fileName = "book.mobi", container = "mobi"),
        ),
        requestedFileId = null,
    )

    assertNotNull(target)
    assertEquals(4, target.version.fileId)
    assertEquals(BookFormat.Mobi, target.format)
    assertEquals(EbookReadMode.ExternalOnly, target.support.readMode)
}

@Test
fun readerTargetStillPrefersInAppFormatsWhenAvailable() {
    val target = chooseReaderVersion(
        versions = listOf(
            FileVersion(fileId = 3, fileName = "book.mobi", container = "mobi"),
            FileVersion(fileId = 4, fileName = "book.epub", container = "epub"),
        ),
        requestedFileId = null,
    )

    assertNotNull(target)
    assertEquals(4, target.version.fileId)
    assertEquals(BookFormat.Epub, target.format)
    assertEquals(EbookReadMode.InApp, target.support.readMode)
}
```

- [ ] **Step 2: Run shared tests and verify they fail**

Run:

```bash
./gradlew :shared:testDebugUnitTest --tests com.continuum.app.model.ebook.EbookVersionSelectionTest
```

Expected: fails because `chooseReaderVersion` and `ReaderVersionTarget` do not exist.

- [ ] **Step 3: Implement reader target selection**

In `shared/src/commonMain/kotlin/com/continuum/app/model/ebook/EbookVersionSelection.kt`, add this data class and function after `EbookFormatSupport`:

```kotlin
data class ReaderVersionTarget(
    val version: FileVersion,
    val support: EbookFormatSupport,
    val format: BookFormat,
)

fun chooseReaderVersion(
    versions: List<FileVersion>,
    requestedFileId: Int?,
): ReaderVersionTarget? {
    if (requestedFileId != null) {
        versions.firstOrNull { version ->
            version.fileId == requestedFileId && version.isSupportedEbookVersion()
        }?.let { return it.toReaderVersionTarget() }
    }

    val supported = versions.filter { it.isSupportedEbookVersion() }
    if (supported.isEmpty()) return null

    val preferredPool = supported
        .filter { it.ebookFormatSupport().canReadInApp }
        .ifEmpty { supported }

    return preferredPool
        .minBy { version ->
            preferredOrder.indexOf(version.ebookFormatKey()).takeIf { it >= 0 } ?: Int.MAX_VALUE
        }
        .toReaderVersionTarget()
}

private fun FileVersion.toReaderVersionTarget(): ReaderVersionTarget =
    ReaderVersionTarget(
        version = this,
        support = ebookFormatSupport(),
        format = bookFormatFromEbookVersion(),
    )
```

- [ ] **Step 4: Add failing engine-policy tests**

Create `android-shared/src/androidUnitTest/kotlin/com/continuum/app/common/ebook/ReaderEnginePolicyTest.kt`:

```kotlin
package com.continuum.app.common.ebook

import com.continuum.app.model.book.BookFormat
import com.continuum.app.model.ebook.EbookReadMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ReaderEnginePolicyTest {
    @Test
    fun reflowableFormatsUseReflowEngine() {
        listOf(BookFormat.Epub, BookFormat.Fb2, BookFormat.Fbz, BookFormat.Txt, BookFormat.Markdown)
            .forEach { format ->
                val policy = readerEnginePolicyFor(format, EbookReadMode.InApp)

                assertEquals(ReaderEngineKind.Reflowable, policy.engineKind)
                assertEquals(ReaderContentClass.Ebook, policy.contentClass)
                assertTrue(policy.supportsInAppReading)
            }
    }

    @Test
    fun pdfUsesFixedDocumentEngine() {
        val policy = readerEnginePolicyFor(BookFormat.Pdf, EbookReadMode.InApp)

        assertEquals(ReaderEngineKind.FixedDocument, policy.engineKind)
        assertEquals(ReaderContentClass.Document, policy.contentClass)
        assertTrue(policy.supportsInAppReading)
    }

    @Test
    fun cbzUsesComicMangaEngine() {
        val policy = readerEnginePolicyFor(BookFormat.Cbz, EbookReadMode.InApp)

        assertEquals(ReaderEngineKind.ComicManga, policy.engineKind)
        assertEquals(ReaderContentClass.ComicManga, policy.contentClass)
        assertTrue(policy.supportsInAppReading)
    }

    @Test
    fun externalOnlyFormatsUseExternalEngine() {
        listOf(BookFormat.Cbr, BookFormat.Mobi, BookFormat.Azw, BookFormat.Azw3, BookFormat.Unknown)
            .forEach { format ->
                val policy = readerEnginePolicyFor(format, EbookReadMode.ExternalOnly)

                assertEquals(ReaderEngineKind.External, policy.engineKind)
                assertEquals(ReaderContentClass.ExternalOriginal, policy.contentClass)
                assertFalse(policy.supportsInAppReading)
            }
    }

    @Test
    fun capabilitiesExposeEngineKind() {
        assertEquals(ReaderEngineKind.Reflowable, ReaderCapabilities.forFormat(BookFormat.Epub).engineKind)
        assertEquals(ReaderEngineKind.FixedDocument, ReaderCapabilities.forFormat(BookFormat.Pdf).engineKind)
        assertEquals(ReaderEngineKind.ComicManga, ReaderCapabilities.forFormat(BookFormat.Cbz).engineKind)
        assertEquals(ReaderEngineKind.External, ReaderCapabilities.forFormat(BookFormat.Mobi).engineKind)
    }
}
```

- [ ] **Step 5: Run android-shared policy test and verify it fails**

Run:

```bash
./gradlew :android-shared:testDebugUnitTest --tests com.continuum.app.common.ebook.ReaderEnginePolicyTest
```

Expected: fails because `ReaderEnginePolicy.kt` does not exist and `ReaderCapabilities.engineKind` does not exist.

- [ ] **Step 6: Implement engine policy**

Create `android-shared/src/androidMain/kotlin/com/continuum/app/common/ebook/ReaderEnginePolicy.kt`:

```kotlin
package com.continuum.app.common.ebook

import com.continuum.app.model.book.BookFormat
import com.continuum.app.model.ebook.EbookReadMode

enum class ReaderEngineKind {
    Reflowable,
    FixedDocument,
    ComicManga,
    External,
}

enum class ReaderContentClass {
    Ebook,
    Document,
    ComicManga,
    ExternalOriginal,
}

data class ReaderEnginePolicy(
    val engineKind: ReaderEngineKind,
    val contentClass: ReaderContentClass,
    val supportsInAppReading: Boolean,
)

fun readerEnginePolicyFor(
    format: BookFormat,
    readMode: EbookReadMode,
): ReaderEnginePolicy {
    if (readMode != EbookReadMode.InApp) {
        return ReaderEnginePolicy(
            engineKind = ReaderEngineKind.External,
            contentClass = ReaderContentClass.ExternalOriginal,
            supportsInAppReading = false,
        )
    }

    return when (format) {
        BookFormat.Epub,
        BookFormat.Fb2,
        BookFormat.Fbz,
        BookFormat.Txt,
        BookFormat.Markdown -> ReaderEnginePolicy(
            engineKind = ReaderEngineKind.Reflowable,
            contentClass = ReaderContentClass.Ebook,
            supportsInAppReading = true,
        )
        BookFormat.Pdf -> ReaderEnginePolicy(
            engineKind = ReaderEngineKind.FixedDocument,
            contentClass = ReaderContentClass.Document,
            supportsInAppReading = true,
        )
        BookFormat.Cbz -> ReaderEnginePolicy(
            engineKind = ReaderEngineKind.ComicManga,
            contentClass = ReaderContentClass.ComicManga,
            supportsInAppReading = true,
        )
        BookFormat.Cbr,
        BookFormat.Mobi,
        BookFormat.Azw,
        BookFormat.Azw3,
        BookFormat.Unknown -> ReaderEnginePolicy(
            engineKind = ReaderEngineKind.External,
            contentClass = ReaderContentClass.ExternalOriginal,
            supportsInAppReading = false,
        )
    }
}
```

Modify `ReaderCapabilities` in `android-shared/src/androidMain/kotlin/com/continuum/app/common/ebook/ReaderControls.kt`:

```kotlin
data class ReaderCapabilities(
    val supportsBookmarks: Boolean,
    val supportsPageJump: Boolean,
    val supportsSections: Boolean,
    val supportsTheme: Boolean,
    val supportsTextSize: Boolean,
    val supportsMargins: Boolean,
    val supportsExternalOnly: Boolean = false,
    val engineKind: ReaderEngineKind = ReaderEngineKind.External,
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
                engineKind = ReaderEngineKind.Reflowable,
            )
            BookFormat.Pdf -> ReaderCapabilities(
                supportsBookmarks = true,
                supportsPageJump = true,
                supportsSections = false,
                supportsTheme = false,
                supportsTextSize = false,
                supportsMargins = false,
                engineKind = ReaderEngineKind.FixedDocument,
            )
            BookFormat.Cbz -> ReaderCapabilities(
                supportsBookmarks = true,
                supportsPageJump = true,
                supportsSections = false,
                supportsTheme = false,
                supportsTextSize = false,
                supportsMargins = false,
                engineKind = ReaderEngineKind.ComicManga,
            )
            BookFormat.Txt, BookFormat.Markdown, BookFormat.Fb2, BookFormat.Fbz -> ReaderCapabilities(
                supportsBookmarks = true,
                supportsPageJump = true,
                supportsSections = false,
                supportsTheme = true,
                supportsTextSize = true,
                supportsMargins = true,
                engineKind = ReaderEngineKind.Reflowable,
            )
            else -> ReaderCapabilities(
                supportsBookmarks = false,
                supportsPageJump = false,
                supportsSections = false,
                supportsTheme = false,
                supportsTextSize = false,
                supportsMargins = false,
                supportsExternalOnly = true,
                engineKind = ReaderEngineKind.External,
            )
        }
    }
}
```

- [ ] **Step 7: Run Task 1 tests and verify they pass**

Run:

```bash
./gradlew :shared:testDebugUnitTest --tests com.continuum.app.model.ebook.EbookVersionSelectionTest
./gradlew :android-shared:testDebugUnitTest --tests com.continuum.app.common.ebook.ReaderEnginePolicyTest
./gradlew :android-shared:testDebugUnitTest --tests com.continuum.app.common.ebook.ReaderControlsTest
```

Expected: all selected tests pass.

- [ ] **Step 8: Commit Task 1**

```bash
git add shared/src/commonMain/kotlin/com/continuum/app/model/ebook/EbookVersionSelection.kt \
  shared/src/commonTest/kotlin/com/continuum/app/model/ebook/EbookVersionSelectionTest.kt \
  android-shared/src/androidMain/kotlin/com/continuum/app/common/ebook/ReaderEnginePolicy.kt \
  android-shared/src/androidMain/kotlin/com/continuum/app/common/ebook/ReaderControls.kt \
  android-shared/src/androidUnitTest/kotlin/com/continuum/app/common/ebook/ReaderEnginePolicyTest.kt
git commit -m "Add reader target and engine policy"
```

## Task 2: Reader ViewModel Handles External Reader Targets

**Files:**
- Modify: `androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/reader/ReaderViewModel.kt`
- Create: `androidApp/src/androidUnitTest/kotlin/com/continuum/app/android/ui/screens/reader/ReaderViewModelReaderTargetSourceTest.kt`

- [ ] **Step 1: Add failing source guard**

Create `androidApp/src/androidUnitTest/kotlin/com/continuum/app/android/ui/screens/reader/ReaderViewModelReaderTargetSourceTest.kt`:

```kotlin
package com.continuum.app.android.ui.screens.reader

import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ReaderViewModelReaderTargetSourceTest {
    private val source = File(
        "src/androidMain/kotlin/com/continuum/app/android/ui/screens/reader/ReaderViewModel.kt",
    ).readText()

    @Test
    fun viewModelUsesReaderTargetSelectionInsteadOfInAppOnlySelection() {
        assertTrue(source.contains("chooseReaderVersion("))
        assertFalse(source.contains("chooseEbookVersion("))
    }

    @Test
    fun externalOnlyTargetsRemainVisibleInReaderState() {
        assertTrue(source.contains("EbookReadMode.ExternalOnly"))
        assertTrue(source.contains("readMode = target.support.readMode"))
        assertTrue(source.contains("Download this original to open it with another reader."))
    }
}
```

- [ ] **Step 2: Run source guard and verify it fails**

Run:

```bash
./gradlew :androidApp:testDebugUnitTest --tests com.continuum.app.android.ui.screens.reader.ReaderViewModelReaderTargetSourceTest
```

Expected: fails because `ReaderViewModel` still imports and calls `chooseEbookVersion`.

- [ ] **Step 3: Extend `ReaderUiState`**

In `ReaderViewModel.kt`, add imports:

```kotlin
import com.continuum.app.model.ebook.EbookReadMode
import com.continuum.app.model.ebook.chooseReaderVersion
import com.continuum.app.model.ebook.ebookFormatSupport
```

Remove imports:

```kotlin
import com.continuum.app.model.ebook.chooseEbookVersion
import com.continuum.app.model.ebook.isInAppReadableEbookVersion
```

Add fields to `ReaderUiState`:

```kotlin
val readMode: EbookReadMode = EbookReadMode.Unsupported,
val formatDisplayName: String = "",
```

- [ ] **Step 4: Replace online target selection**

Inside `loadDetail()`, replace:

```kotlin
val version = chooseEbookVersion(d.versions, requestedFileId)
if (version == null) {
```

with:

```kotlin
val target = chooseReaderVersion(d.versions, requestedFileId)
val version = target?.version
if (target == null || version == null) {
```

After `val format = version.bookFormatFromEbookVersion()`, add:

```kotlin
val externalOnlyWithoutDownload =
    target.support.readMode == EbookReadMode.ExternalOnly && offlineMedia == null
```

In the `_uiState.update` block for success, set:

```kotlin
format = format,
readMode = target.support.readMode,
formatDisplayName = target.support.displayName,
fileUrl = when {
    target.support.canReadInApp -> offlineMedia?.fileUrl ?: ebookReaderRepository.readPath(d.contentId, version.fileId)
    else -> offlineMedia?.fileUrl
},
localUri = offlineMedia?.uriString,
localDisplayName = offlineMedia?.displayName ?: version.fileName,
error = if (externalOnlyWithoutDownload) {
    "Download this original to open it with another reader."
} else {
    null
},
```

Keep the existing title, author, fileId, page count, capabilities, progress, bookmarks, and display settings assignments.

- [ ] **Step 5: Replace offline-only external handling**

In `loadOfflineOnly()`, remove the block that returns early for external-only formats:

```kotlin
if (!version.isInAppReadableEbookVersion() || format == BookFormat.Unknown) {
    _uiState.update {
        it.copy(
            isLoading = false,
            error = "This downloaded ebook format opens with another reader from Downloads.",
        )
    }
    return
}
```

Replace it with:

```kotlin
val support = version.ebookFormatSupport()
if (support.readMode == EbookReadMode.Unsupported || format == BookFormat.Unknown) {
    _uiState.update {
        it.copy(
            isLoading = false,
            error = "This file is not a supported reading format.",
        )
    }
    return
}
```

In the offline `_uiState.update` block, add:

```kotlin
readMode = support.readMode,
formatDisplayName = support.displayName,
```

- [ ] **Step 6: Run Task 2 tests and a reader compile gate**

Run:

```bash
./gradlew :androidApp:testDebugUnitTest --tests com.continuum.app.android.ui.screens.reader.ReaderViewModelReaderTargetSourceTest
./gradlew :androidApp:compileDebugKotlin
```

Expected: source guard passes and `:androidApp:compileDebugKotlin` exits 0.

- [ ] **Step 7: Commit Task 2**

```bash
git add androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/reader/ReaderViewModel.kt \
  androidApp/src/androidUnitTest/kotlin/com/continuum/app/android/ui/screens/reader/ReaderViewModelReaderTargetSourceTest.kt
git commit -m "Route reader targets through external-aware selection"
```

## Task 3: Reader Shell State Reducer

**Files:**
- Create: `android-shared/src/androidMain/kotlin/com/continuum/app/common/ebook/ReaderShellState.kt`
- Create: `android-shared/src/androidUnitTest/kotlin/com/continuum/app/common/ebook/ReaderShellStateTest.kt`

- [ ] **Step 1: Add failing reducer tests**

Create `android-shared/src/androidUnitTest/kotlin/com/continuum/app/common/ebook/ReaderShellStateTest.kt`:

```kotlin
package com.continuum.app.common.ebook

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ReaderShellStateTest {
    @Test
    fun centerTapTogglesChrome() {
        val shown = reduceReaderShellState(ReaderShellUiState(), ReaderShellEvent.ToggleChrome)
        val hidden = reduceReaderShellState(shown, ReaderShellEvent.ToggleChrome)

        assertTrue(shown.chromeVisible)
        assertFalse(hidden.chromeVisible)
    }

    @Test
    fun openingSheetShowsChromeAndStoresActiveSheet() {
        val state = reduceReaderShellState(
            ReaderShellUiState(chromeVisible = false),
            ReaderShellEvent.OpenSheet(ReaderSheet.Bookmarks),
        )

        assertTrue(state.chromeVisible)
        assertEquals(ReaderSheet.Bookmarks, state.activeSheet)
    }

    @Test
    fun dismissingSheetKeepsChromeVisible() {
        val state = reduceReaderShellState(
            ReaderShellUiState(chromeVisible = true, activeSheet = ReaderSheet.Settings),
            ReaderShellEvent.DismissSheet,
        )

        assertTrue(state.chromeVisible)
        assertEquals(ReaderSheet.None, state.activeSheet)
    }

    @Test
    fun autoHideDoesNotCloseOpenSheet() {
        val state = reduceReaderShellState(
            ReaderShellUiState(chromeVisible = true, activeSheet = ReaderSheet.Sections),
            ReaderShellEvent.AutoHideChrome,
        )

        assertTrue(state.chromeVisible)
        assertEquals(ReaderSheet.Sections, state.activeSheet)
    }

    @Test
    fun autoHideHidesChromeWhenNoSheetIsOpen() {
        val state = reduceReaderShellState(
            ReaderShellUiState(chromeVisible = true, activeSheet = ReaderSheet.None),
            ReaderShellEvent.AutoHideChrome,
        )

        assertFalse(state.chromeVisible)
    }
}
```

- [ ] **Step 2: Run reducer test and verify it fails**

Run:

```bash
./gradlew :android-shared:testDebugUnitTest --tests com.continuum.app.common.ebook.ReaderShellStateTest
```

Expected: fails because `ReaderShellState.kt` does not exist.

- [ ] **Step 3: Implement reducer**

Create `android-shared/src/androidMain/kotlin/com/continuum/app/common/ebook/ReaderShellState.kt`:

```kotlin
package com.continuum.app.common.ebook

enum class ReaderSheet {
    None,
    Sections,
    Bookmarks,
    Settings,
    More,
}

data class ReaderShellUiState(
    val chromeVisible: Boolean = false,
    val activeSheet: ReaderSheet = ReaderSheet.None,
)

sealed interface ReaderShellEvent {
    data object ToggleChrome : ReaderShellEvent
    data object ShowChrome : ReaderShellEvent
    data object HideChrome : ReaderShellEvent
    data object AutoHideChrome : ReaderShellEvent
    data object DismissSheet : ReaderShellEvent
    data class OpenSheet(val sheet: ReaderSheet) : ReaderShellEvent
}

fun reduceReaderShellState(
    state: ReaderShellUiState,
    event: ReaderShellEvent,
): ReaderShellUiState = when (event) {
    ReaderShellEvent.ToggleChrome -> state.copy(
        chromeVisible = if (state.activeSheet == ReaderSheet.None) !state.chromeVisible else true,
    )
    ReaderShellEvent.ShowChrome -> state.copy(chromeVisible = true)
    ReaderShellEvent.HideChrome -> if (state.activeSheet == ReaderSheet.None) {
        state.copy(chromeVisible = false)
    } else {
        state
    }
    ReaderShellEvent.AutoHideChrome -> if (state.activeSheet == ReaderSheet.None) {
        state.copy(chromeVisible = false)
    } else {
        state
    }
    ReaderShellEvent.DismissSheet -> state.copy(activeSheet = ReaderSheet.None)
    is ReaderShellEvent.OpenSheet -> state.copy(
        chromeVisible = true,
        activeSheet = event.sheet,
    )
}
```

- [ ] **Step 4: Run reducer test and verify it passes**

Run:

```bash
./gradlew :android-shared:testDebugUnitTest --tests com.continuum.app.common.ebook.ReaderShellStateTest
```

Expected: test passes.

- [ ] **Step 5: Commit Task 3**

```bash
git add android-shared/src/androidMain/kotlin/com/continuum/app/common/ebook/ReaderShellState.kt \
  android-shared/src/androidUnitTest/kotlin/com/continuum/app/common/ebook/ReaderShellStateTest.kt
git commit -m "Add reader shell state reducer"
```

## Task 4: Reader Engine Host And External Panel

**Files:**
- Create: `androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/reader/ReaderEngineHost.kt`
- Create: `androidApp/src/androidUnitTest/kotlin/com/continuum/app/android/ui/screens/reader/ReaderEngineHostSourceTest.kt`

- [ ] **Step 1: Add failing source test**

Create `androidApp/src/androidUnitTest/kotlin/com/continuum/app/android/ui/screens/reader/ReaderEngineHostSourceTest.kt`:

```kotlin
package com.continuum.app.android.ui.screens.reader

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

class ReaderEngineHostSourceTest {
    private val sourceFile = File(
        "src/androidMain/kotlin/com/continuum/app/android/ui/screens/reader/ReaderEngineHost.kt",
    )

    @Test
    fun hostDispatchesEveryReaderEngineKind() {
        val source = sourceFile.readText()

        assertTrue(source.contains("ReaderEngineKind.Reflowable"))
        assertTrue(source.contains("ReaderEngineKind.FixedDocument"))
        assertTrue(source.contains("ReaderEngineKind.ComicManga"))
        assertTrue(source.contains("ReaderEngineKind.External"))
    }

    @Test
    fun externalPanelUsesPublicOriginalOpenPath() {
        val source = sourceFile.readText()

        assertTrue(source.contains("DownloadOpenTarget.from("))
        assertTrue(source.contains("openDownloadTargetInExternalApp("))
        assertTrue(source.contains("Open with another reader"))
    }
}
```

- [ ] **Step 2: Run source test and verify it fails**

Run:

```bash
./gradlew :androidApp:testDebugUnitTest --tests com.continuum.app.android.ui.screens.reader.ReaderEngineHostSourceTest
```

Expected: fails because `ReaderEngineHost.kt` does not exist.

- [ ] **Step 3: Implement `ReaderEngineHost`**

Create `androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/reader/ReaderEngineHost.kt`:

```kotlin
package com.continuum.app.android.ui.screens.reader

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.continuum.app.android.ui.screens.downloads.openDownloadTargetInExternalApp
import com.continuum.app.android.ui.screens.reader.reflow.ReflowableReader
import com.continuum.app.common.downloads.DownloadOpenTarget
import com.continuum.app.common.ebook.ReaderEngineKind
import com.continuum.app.model.book.BookFormat

@Composable
fun ReaderEngineHost(
    state: ReaderUiState,
    onPageChanged: (Int) -> Unit,
    onPageCountKnown: (Int) -> Unit,
    onLocatorChanged: (String, Double) -> Unit,
    onSectionsKnown: (List<com.continuum.app.common.ebook.ReaderSection>) -> Unit,
    onTextScaleNudge: (Float) -> Unit,
    onJumpConsumed: () -> Unit,
) {
    val fileUrl = state.fileUrl
    when {
        state.isLoading -> CenteredReaderMessage("Loading book...")
        state.error != null && state.capabilities.engineKind != ReaderEngineKind.External ->
            CenteredReaderMessage(state.error)
        fileUrl.isNullOrBlank() && state.capabilities.engineKind != ReaderEngineKind.External ->
            CenteredReaderMessage("No file available for this book.")
        else -> when (state.capabilities.engineKind) {
            ReaderEngineKind.Reflowable -> ReflowableReader(
                format = state.format,
                fileUrl = fileUrl.orEmpty(),
                settings = state.displaySettings,
                initialLocator = state.progressLocation,
                onLocatorChanged = onLocatorChanged,
                onSectionsKnown = onSectionsKnown,
                onTextScaleNudge = onTextScaleNudge,
                jumpToLocation = state.pendingJumpLocation,
                onJumpConsumed = onJumpConsumed,
            )
            ReaderEngineKind.FixedDocument -> PdfReader(
                fileUrl = fileUrl.orEmpty(),
                title = state.title,
                initialPage = state.currentPage,
                onPageChanged = onPageChanged,
                onPageCountKnown = onPageCountKnown,
            )
            ReaderEngineKind.ComicManga -> ComicReader(
                fileUrl = fileUrl.orEmpty(),
                title = state.title,
                initialPage = state.currentPage,
                onPageChanged = onPageChanged,
                onPageCountKnown = onPageCountKnown,
            )
            ReaderEngineKind.External -> ExternalReadingPanel(state = state)
        }
    }
}

@Composable
private fun ExternalReadingPanel(state: ReaderUiState) {
    val context = LocalContext.current
    val target = remember(state.localUri, state.localDisplayName, state.format) {
        DownloadOpenTarget.from(
            isComplete = !state.localUri.isNullOrBlank(),
            localUri = state.localUri,
            displayName = state.localDisplayName ?: state.title,
            container = state.format.wire,
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = state.formatDisplayName.ifBlank { state.format.displayName },
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
        )
        Text(
            text = state.error ?: "This original file opens with a dedicated reader app.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 12.dp, bottom = 24.dp),
        )
        Button(
            enabled = target != null,
            onClick = {
                if (target == null || !openDownloadTargetInExternalApp(context, target)) {
                    Toast.makeText(context, "No app found to open this file.", Toast.LENGTH_LONG).show()
                }
            },
        ) {
            Text("Open with another reader")
        }
    }
}

@Composable
private fun CenteredReaderMessage(message: String?) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = message ?: "Reader unavailable.",
            textAlign = TextAlign.Center,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(32.dp),
        )
    }
}
```

- [ ] **Step 4: Run source test and compile**

Run:

```bash
./gradlew :androidApp:testDebugUnitTest --tests com.continuum.app.android.ui.screens.reader.ReaderEngineHostSourceTest
./gradlew :androidApp:compileDebugKotlin
```

Expected: test passes and compile exits 0.

- [ ] **Step 5: Commit Task 4**

```bash
git add androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/reader/ReaderEngineHost.kt \
  androidApp/src/androidUnitTest/kotlin/com/continuum/app/android/ui/screens/reader/ReaderEngineHostSourceTest.kt
git commit -m "Add reader engine host"
```

## Task 5: Immersive Reader Shell And Thin ReaderScreen

**Files:**
- Create: `androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/reader/ReaderShell.kt`
- Modify: `androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/reader/ReaderScreen.kt`
- Create: `androidApp/src/androidUnitTest/kotlin/com/continuum/app/android/ui/screens/reader/ReaderScreenStructureTest.kt`

- [ ] **Step 1: Add failing structure test**

Create `androidApp/src/androidUnitTest/kotlin/com/continuum/app/android/ui/screens/reader/ReaderScreenStructureTest.kt`:

```kotlin
package com.continuum.app.android.ui.screens.reader

import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ReaderScreenStructureTest {
    private val screen = File(
        "src/androidMain/kotlin/com/continuum/app/android/ui/screens/reader/ReaderScreen.kt",
    ).readText()
    private val shell = File(
        "src/androidMain/kotlin/com/continuum/app/android/ui/screens/reader/ReaderShell.kt",
    ).readText()

    @Test
    fun readerScreenDelegatesShellAndEngineWork() {
        assertTrue(screen.contains("ReaderShell("))
        assertTrue(screen.contains("ReaderEngineHost("))
        assertFalse(screen.contains("BookFormat.Pdf -> PdfReader("))
        assertFalse(screen.contains("BookFormat.Cbz -> ComicReader("))
        assertFalse(screen.contains("BookFormat.Epub, BookFormat.Fb2"))
    }

    @Test
    fun shellOwnsImmersiveChromeAndSheets() {
        assertTrue(shell.contains("reduceReaderShellState("))
        assertTrue(shell.contains("ReaderShellEvent.ToggleChrome"))
        assertTrue(shell.contains("ReaderSheet.Bookmarks"))
        assertTrue(shell.contains("ReaderSheet.Sections"))
        assertTrue(shell.contains("ReaderSheet.Settings"))
    }
}
```

- [ ] **Step 2: Run structure test and verify it fails**

Run:

```bash
./gradlew :androidApp:testDebugUnitTest --tests com.continuum.app.android.ui.screens.reader.ReaderScreenStructureTest
```

Expected: fails because `ReaderShell.kt` does not exist and `ReaderScreen` still dispatches engines directly.

- [ ] **Step 3: Create `ReaderShell`**

Create `androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/reader/ReaderShell.kt`. Move the existing `BookmarkSheet`, `SectionsSheet`, `ReaderSettingsSheet`, and `CenteredText` helper code from `ReaderScreen.kt` into this file. Add this composable at the top of the file:

```kotlin
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderShell(
    state: ReaderUiState,
    onBackClick: () -> Unit,
    onAddBookmark: () -> Unit,
    onDeleteBookmark: (EbookAnnotation) -> Unit,
    onJumpToBookmark: (EbookAnnotation) -> Unit,
    onJumpToSection: (ReaderSection) -> Unit,
    onSettingsChange: (ReaderDisplaySettings) -> Unit,
    content: @Composable () -> Unit,
) {
    var shellState by remember { mutableStateOf(ReaderShellUiState()) }
    val supportsSettings = state.capabilities.supportsTextSize ||
        state.capabilities.supportsMargins ||
        state.capabilities.supportsTheme

    fun send(event: ReaderShellEvent) {
        shellState = reduceReaderShellState(shellState, event)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .clickable { send(ReaderShellEvent.ToggleChrome) },
    ) {
        content()

        if (shellState.chromeVisible) {
            ReaderTopChrome(
                state = state,
                supportsSettings = supportsSettings,
                onBackClick = onBackClick,
                onBookmarksClick = { send(ReaderShellEvent.OpenSheet(ReaderSheet.Bookmarks)) },
                onSectionsClick = { send(ReaderShellEvent.OpenSheet(ReaderSheet.Sections)) },
                onSettingsClick = { send(ReaderShellEvent.OpenSheet(ReaderSheet.Settings)) },
                onAddBookmark = onAddBookmark,
            )
            ReaderBottomChrome(state = state)
        }
    }

    when (shellState.activeSheet) {
        ReaderSheet.Bookmarks -> BookmarkSheet(
            bookmarks = state.bookmarks,
            onJumpTo = {
                onJumpToBookmark(it)
                send(ReaderShellEvent.DismissSheet)
            },
            onDelete = onDeleteBookmark,
            onDismiss = { send(ReaderShellEvent.DismissSheet) },
        )
        ReaderSheet.Sections -> SectionsSheet(
            sections = state.sections,
            onJumpTo = {
                onJumpToSection(it)
                send(ReaderShellEvent.DismissSheet)
            },
            onDismiss = { send(ReaderShellEvent.DismissSheet) },
        )
        ReaderSheet.Settings -> ReaderSettingsSheet(
            settings = state.displaySettings,
            capabilities = state.capabilities,
            onSettingsChange = onSettingsChange,
            onDismiss = { send(ReaderShellEvent.DismissSheet) },
        )
        ReaderSheet.None,
        ReaderSheet.More -> Unit
    }
}
```

Add two private chrome helpers in the same file:

```kotlin
@Composable
private fun ReaderTopChrome(
    state: ReaderUiState,
    supportsSettings: Boolean,
    onBackClick: () -> Unit,
    onBookmarksClick: () -> Unit,
    onSectionsClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onAddBookmark: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.92f))
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBackClick) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
        }
        Text(
            text = state.title,
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onBookmarksClick, enabled = state.capabilities.supportsBookmarks) {
            Icon(Icons.Default.Bookmarks, contentDescription = "Bookmarks")
        }
        IconButton(
            onClick = onSectionsClick,
            enabled = state.capabilities.supportsSections && state.sections.isNotEmpty(),
        ) {
            Icon(Icons.AutoMirrored.Filled.MenuBook, contentDescription = "Sections")
        }
        IconButton(onClick = onSettingsClick, enabled = supportsSettings) {
            Icon(Icons.Default.Tune, contentDescription = "Reader settings")
        }
        IconButton(
            onClick = onAddBookmark,
            enabled = state.fileId != null && state.capabilities.supportsBookmarks,
        ) {
            Icon(Icons.Default.BookmarkAdd, contentDescription = "Add bookmark")
        }
    }
}

@Composable
private fun BoxScope.ReaderBottomChrome(state: ReaderUiState) {
    val progressLabel = when (state.capabilities.engineKind) {
        ReaderEngineKind.Reflowable -> "${(state.progressPercent * 100).toInt()}%"
        else -> "${(state.progressPercent * 100).toInt()}% · Page ${state.currentPage + 1}" +
            state.pageCount?.let { " of $it" }.orEmpty()
    }
    Column(
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.92f))
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Text(
            text = progressLabel,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (state.syncError != null) {
            Text(
                text = state.syncError,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.error,
            )
        } else if (state.isSyncing) {
            Text(
                text = "Syncing reading progress",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
```

Required imports include:

```kotlin
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.continuum.app.common.ebook.ReaderDisplaySettings
import com.continuum.app.common.ebook.ReaderEngineKind
import com.continuum.app.common.ebook.ReaderSection
import com.continuum.app.common.ebook.ReaderShellEvent
import com.continuum.app.common.ebook.ReaderShellUiState
import com.continuum.app.common.ebook.ReaderSheet
import com.continuum.app.common.ebook.reduceReaderShellState
import com.continuum.app.model.ebook.EbookAnnotation
```

- [ ] **Step 4: Replace `ReaderScreen` body**

Keep `ReaderScreen` as the top-level route, but reduce it to state collection, reflow/fixed jump behavior, and shell/host composition:

```kotlin
@Composable
fun ReaderScreen(
    onBackClick: () -> Unit,
    viewModel: ReaderViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val isReflowable = when (state.format) {
        BookFormat.Epub, BookFormat.Fb2, BookFormat.Fbz, BookFormat.Txt, BookFormat.Markdown -> true
        else -> false
    }

    ReaderShell(
        state = state,
        onBackClick = onBackClick,
        onAddBookmark = viewModel::addBookmark,
        onDeleteBookmark = viewModel::deleteBookmark,
        onJumpToBookmark = { bookmark ->
            if (isReflowable) {
                bookmark.location?.let(viewModel::jumpToLocation)
            } else {
                ebookPageNumberFromProgressLocation(bookmark.location)?.let(viewModel::jumpToPage)
            }
        },
        onJumpToSection = { section ->
            if (isReflowable) {
                viewModel.jumpToLocation(section.location)
            } else {
                ebookPageNumberFromProgressLocation(section.location)?.let(viewModel::jumpToPage)
            }
        },
        onSettingsChange = viewModel::setDisplaySettings,
    ) {
        ReaderEngineHost(
            state = state,
            onPageChanged = viewModel::onPageChanged,
            onPageCountKnown = viewModel::onPageCountKnown,
            onLocatorChanged = viewModel::onLocatorChanged,
            onSectionsKnown = viewModel::setSections,
            onTextScaleNudge = viewModel::nudgeTextScale,
            onJumpConsumed = viewModel::consumeJump,
        )
    }
}
```

Remove direct engine dispatch and old always-visible toolbar/progress text from `ReaderScreen.kt`. Keep or move the existing sheet composables so there is exactly one definition of `BookmarkSheet`, `SectionsSheet`, and `ReaderSettingsSheet`.

- [ ] **Step 5: Run structure test and compile**

Run:

```bash
./gradlew :androidApp:testDebugUnitTest --tests com.continuum.app.android.ui.screens.reader.ReaderScreenStructureTest
./gradlew :androidApp:compileDebugKotlin
```

Expected: test passes and compile exits 0.

- [ ] **Step 6: Commit Task 5**

```bash
git add androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/reader/ReaderShell.kt \
  androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/reader/ReaderScreen.kt \
  androidApp/src/androidUnitTest/kotlin/com/continuum/app/android/ui/screens/reader/ReaderScreenStructureTest.kt
git commit -m "Add immersive reader shell"
```

## Task 6: Reading Foundation Verification

**Files:**
- Verify: `androidTvApp/src/androidUnitTest/kotlin/com/continuum/app/tv/ui/TvUsabilityGuardTest.kt`
- Verify: all files changed by Tasks 1-5

- [ ] **Step 1: Run targeted reader and TV guards**

Run:

```bash
./gradlew :shared:testDebugUnitTest --tests com.continuum.app.model.ebook.EbookVersionSelectionTest
./gradlew :android-shared:testDebugUnitTest --tests com.continuum.app.common.ebook.ReaderEnginePolicyTest
./gradlew :android-shared:testDebugUnitTest --tests com.continuum.app.common.ebook.ReaderShellStateTest
./gradlew :androidApp:testDebugUnitTest --tests com.continuum.app.android.ui.screens.reader.ReaderViewModelReaderTargetSourceTest
./gradlew :androidApp:testDebugUnitTest --tests com.continuum.app.android.ui.screens.reader.ReaderEngineHostSourceTest
./gradlew :androidApp:testDebugUnitTest --tests com.continuum.app.android.ui.screens.reader.ReaderScreenStructureTest
./gradlew :androidTvApp:testDebugUnitTest --tests com.continuum.app.tv.ui.TvUsabilityGuardTest
```

Expected: all selected tests pass.

- [ ] **Step 2: Run full project gate**

Run:

```bash
./gradlew :androidApp:lintDebug :androidTvApp:lintDebug testDebugUnitTest :androidApp:assembleDebug :androidTvApp:assembleDebug
```

Expected: Gradle exits 0.

- [ ] **Step 3: Install and smoke-test mobile reader**

Run with the mobile emulator or connected phone:

```bash
adb devices
adb install -r androidApp/build/outputs/apk/debug/androidApp-debug.apk
adb shell monkey -p com.continuum.app.android 1
```

Expected: app launches. Manually verify:

- Opening an EPUB reaches the immersive reader.
- Center tap toggles reader chrome.
- Bookmarks, sections, and settings sheets open and dismiss.
- PDF opens and pages.
- CBZ opens and pages.
- A downloaded MOBI/AZW/CBR shows "Open with another reader" instead of a dead-end in-app reader.
- Android TV app still has no Reading tab or ebook request surface.

- [ ] **Step 4: Commit verification-only fixes if any were required**

If verification required code changes, commit them:

```bash
git status --short
git add <changed-files>
git commit -m "Harden reading foundation verification"
```

If no changes were required, do not create an empty commit.

## Completion Criteria

- `ReaderScreen` is thin and delegates to `ReaderShell` plus `ReaderEngineHost`.
- Existing EPUB/FB2/FBZ/TXT/MD reflow, PDF, and CBZ renderers are still used.
- External-only original ebook formats are valid reader targets and never produce a misleading native-reader failure.
- TV still excludes Reading, ebooks, comics, and manga.
- Full Gradle gate passes.
- Mobile smoke test confirms immersive chrome and existing reader engines still open.
