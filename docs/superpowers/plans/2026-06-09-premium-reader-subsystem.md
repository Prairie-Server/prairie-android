# Premium Reader Subsystem Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Rebuild the Android mobile reader into a premium dedicated reading subsystem for EPUB, PDF, and comics while keeping ebooks off Android TV.

**Architecture:** Add a shared reader shell, engine contracts, format engines, and local-first state helpers. The shell owns chrome, gestures, sheets, and progress UI; engines own format parsing/rendering; shared resolver/auth code hides offline vs remote files from engines.

**Tech Stack:** Kotlin Multiplatform Android target, Jetpack Compose, Material 3, Android `PdfRenderer`, WebView for EPUB rendering, OkHttp/Koin, kotlin-test/JUnit.

---

## File Structure

- `androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/reader/ReaderEngine.kt`
  Shared reader models, engine contract, command and capability types.
- `androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/reader/ReaderChromeState.kt`
  Pure reducer/state for chrome visibility and auto-hide behavior.
- `androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/reader/ReaderFileResolver.kt`
  Shared reader URL/file resolver. Keep and extend the existing helper.
- `androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/reader/ReaderScaffold.kt`
  Premium immersive shell with overlays, tap zones, sheets, and shared loading/error states.
- `androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/reader/ReaderScreen.kt`
  Thin coordinator: reads `ReaderViewModel`, selects engine, passes state into shell.
- `androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/reader/EpubReaderEngine.kt`
  EPUB load/parse/render engine.
- `androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/reader/PdfReaderEngine.kt`
  PDF page engine around `PdfRenderer`.
- `androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/reader/ComicReaderEngine.kt`
  CBZ page engine around archive loading and image rendering.
- `androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/reader/EpubReader.kt`
  Replace current top-level reader implementation with engine render internals or remove once engine owns rendering.
- `androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/reader/PdfReader.kt`
  Replace current top-level reader implementation with engine render internals or remove once engine owns rendering.
- `androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/reader/ComicReader.kt`
  Replace current top-level reader implementation with engine render internals or remove once engine owns rendering.
- `android-shared/src/androidMain/kotlin/com/continuum/app/common/player/MediaAuthInterceptor.kt`
  Preserve profile header behavior for reader/media requests.
- Tests:
  - `androidApp/src/androidUnitTest/kotlin/com/continuum/app/android/ui/screens/reader/ReaderChromeStateTest.kt`
  - `androidApp/src/androidUnitTest/kotlin/com/continuum/app/android/ui/screens/reader/ReaderEngineSelectionTest.kt`
  - `androidApp/src/androidUnitTest/kotlin/com/continuum/app/android/ui/screens/reader/ReaderFileResolverTest.kt`
  - `androidApp/src/androidUnitTest/kotlin/com/continuum/app/android/ui/screens/reader/ReaderProgressLocationTest.kt`
  - `androidApp/src/androidUnitTest/kotlin/com/continuum/app/android/ui/screens/reader/ComicArchiveLoaderTest.kt`
  - `android-shared/src/androidUnitTest/kotlin/com/continuum/app/common/player/MediaAuthInterceptorTest.kt`

---

### Task 1: Lock Reader Foundation Regressions

**Files:**
- Modify: `android-shared/src/androidMain/kotlin/com/continuum/app/common/di/PlayerModule.kt`
- Modify: `android-shared/src/androidMain/kotlin/com/continuum/app/common/player/MediaAuthInterceptor.kt`
- Modify: `androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/reader/ReaderFileResolver.kt`
- Test: `android-shared/src/androidUnitTest/kotlin/com/continuum/app/common/di/PlayerModuleTest.kt`
- Test: `android-shared/src/androidUnitTest/kotlin/com/continuum/app/common/player/MediaAuthInterceptorTest.kt`
- Test: `androidApp/src/androidUnitTest/kotlin/com/continuum/app/android/ui/screens/reader/ReaderFileResolverTest.kt`

- [ ] **Step 1: Verify focused regression tests exist**

Ensure `PlayerModuleTest` contains:

```kotlin
@Test
fun `provides unqualified OkHttpClient for readers`() {
    val koin = startKoin {
        modules(
            module { single<TokenManager> { TokenManagerImpl() } },
            playerModule,
        )
    }.koin

    assertIs<OkHttpClient>(koin.get<OkHttpClient>())
}
```

Ensure `MediaAuthInterceptorTest` contains:

```kotlin
@Test
fun `adds auth and active profile headers to media requests`() {
    val tokenManager = TokenManagerImpl()
    runBlocking {
        tokenManager.saveTokens("access-token", "refresh-token", expiresIn = 3600)
        tokenManager.setProfileId("profile-1")
        tokenManager.setProfileToken("profile-token")
    }
    val chain = CapturingChain(
        Request.Builder()
            .url("https://lib.strm.cafe/api/v1/ebooks/book/files/7/read")
            .build(),
    )

    MediaAuthInterceptor(tokenManager).intercept(chain)

    val request = chain.capturedRequest ?: error("request was not captured")
    assertEquals("Bearer access-token", request.header("Authorization"))
    assertEquals("profile-1", request.header("X-Profile-Id"))
    assertEquals("profile-token", request.header("X-Profile-Token"))
}
```

Ensure `ReaderFileResolverTest` contains:

```kotlin
@Test
fun `resolves server relative reader paths against active server url`() {
    assertEquals(
        "https://lib.strm.cafe/api/v1/ebooks/book-1/files/7/read",
        resolveReaderRequestUrl(
            url = "/api/v1/ebooks/book-1/files/7/read",
            serverUrl = "https://lib.strm.cafe/",
        ),
    )
}
```

- [ ] **Step 2: Run regression tests**

Run:

```bash
./gradlew :android-shared:testDebugUnitTest --tests com.continuum.app.common.di.PlayerModuleTest
./gradlew :android-shared:testDebugUnitTest --tests com.continuum.app.common.player.MediaAuthInterceptorTest
./gradlew :androidApp:testDebugUnitTest --tests com.continuum.app.android.ui.screens.reader.ReaderFileResolverTest
```

Expected: all three commands pass.

- [ ] **Step 3: Commit existing reader foundation fixes**

Stage only these files:

```bash
git add \
  android-shared/src/androidMain/kotlin/com/continuum/app/common/di/PlayerModule.kt \
  android-shared/src/androidMain/kotlin/com/continuum/app/common/player/MediaAuthInterceptor.kt \
  android-shared/src/androidUnitTest/kotlin/com/continuum/app/common/di/PlayerModuleTest.kt \
  android-shared/src/androidUnitTest/kotlin/com/continuum/app/common/player/MediaAuthInterceptorTest.kt \
  androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/reader/ReaderFileResolver.kt \
  androidApp/src/androidUnitTest/kotlin/com/continuum/app/android/ui/screens/reader/ReaderFileResolverTest.kt
git commit -m "Fix authenticated reader file loading"
```

Expected: commit succeeds without staging TV player files.

---

### Task 2: Add Reader Engine Contract And Engine Selection

**Files:**
- Create: `androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/reader/ReaderEngine.kt`
- Test: `androidApp/src/androidUnitTest/kotlin/com/continuum/app/android/ui/screens/reader/ReaderEngineSelectionTest.kt`

- [ ] **Step 1: Write failing engine selection tests**

Create `ReaderEngineSelectionTest.kt`:

```kotlin
package com.continuum.app.android.ui.screens.reader

import com.continuum.app.model.book.BookFormat
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ReaderEngineSelectionTest {

    @Test
    fun `selects in app engines for supported formats`() {
        assertEquals(ReaderEngineKind.Epub, readerEngineKindFor(BookFormat.Epub))
        assertEquals(ReaderEngineKind.Pdf, readerEngineKindFor(BookFormat.Pdf))
        assertEquals(ReaderEngineKind.Comic, readerEngineKindFor(BookFormat.Cbz))
    }

    @Test
    fun `selects external handler for supported non native formats`() {
        assertEquals(ReaderEngineKind.External, readerEngineKindFor(BookFormat.Cbr))
        assertEquals(ReaderEngineKind.External, readerEngineKindFor(BookFormat.Mobi))
        assertEquals(ReaderEngineKind.External, readerEngineKindFor(BookFormat.Azw3))
        assertTrue(ReaderEngineCapabilities.forFormat(BookFormat.Cbr).isExternalReadable)
        assertTrue(ReaderEngineCapabilities.forFormat(BookFormat.Mobi).isExternalReadable)
        assertTrue(ReaderEngineCapabilities.forFormat(BookFormat.Azw3).isExternalReadable)
        assertFalse(ReaderEngineCapabilities.forFormat(BookFormat.Cbr).isInAppReadable)
    }

    @Test
    fun `capabilities expose format specific controls`() {
        assertTrue(ReaderEngineCapabilities.forFormat(BookFormat.Epub).supportsTypography)
        assertTrue(ReaderEngineCapabilities.forFormat(BookFormat.Pdf).supportsFitMode)
        assertTrue(ReaderEngineCapabilities.forFormat(BookFormat.Cbz).supportsReadingDirection)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run:

```bash
./gradlew :androidApp:testDebugUnitTest --tests com.continuum.app.android.ui.screens.reader.ReaderEngineSelectionTest
```

Expected: compile fails because `ReaderEngineKind`, `ReaderEngineCapabilities`, and `readerEngineKindFor` do not exist.

- [ ] **Step 3: Implement minimal engine contract**

Create `ReaderEngine.kt`:

```kotlin
package com.continuum.app.android.ui.screens.reader

import androidx.compose.runtime.Composable
import com.continuum.app.common.ebook.ReaderDisplaySettings
import com.continuum.app.common.ebook.ReaderSection
import com.continuum.app.model.book.BookFormat

internal enum class ReaderEngineKind {
    Epub,
    Pdf,
    Comic,
    External,
}

internal data class ReaderEngineCapabilities(
    val isInAppReadable: Boolean,
    val isExternalReadable: Boolean = false,
    val supportsTypography: Boolean = false,
    val supportsFitMode: Boolean = false,
    val supportsReadingDirection: Boolean = false,
    val supportsSections: Boolean = false,
    val supportsBookmarks: Boolean = true,
) {
    companion object {
        fun forFormat(format: BookFormat): ReaderEngineCapabilities =
            when (format) {
                BookFormat.Epub -> ReaderEngineCapabilities(
                    isInAppReadable = true,
                    supportsTypography = true,
                    supportsSections = true,
                )
                BookFormat.Pdf -> ReaderEngineCapabilities(
                    isInAppReadable = true,
                    supportsFitMode = true,
                )
                BookFormat.Cbz -> ReaderEngineCapabilities(
                    isInAppReadable = true,
                    supportsFitMode = true,
                    supportsReadingDirection = true,
                )
                BookFormat.Cbr,
                BookFormat.Mobi,
                BookFormat.Azw3,
                BookFormat.Txt -> ReaderEngineCapabilities(
                    isInAppReadable = false,
                    isExternalReadable = true,
                    supportsBookmarks = false,
                )
                else -> ReaderEngineCapabilities(isInAppReadable = false, supportsBookmarks = false)
            }
    }
}

internal fun readerEngineKindFor(format: BookFormat): ReaderEngineKind? =
    when (format) {
        BookFormat.Epub -> ReaderEngineKind.Epub
        BookFormat.Pdf -> ReaderEngineKind.Pdf
        BookFormat.Cbz -> ReaderEngineKind.Comic
        BookFormat.Cbr,
        BookFormat.Mobi,
        BookFormat.Azw3,
        BookFormat.Txt -> ReaderEngineKind.External
        else -> null
    }

internal data class ReaderLocation(
    val value: String,
    val pageIndex: Int,
    val progress: Double,
)

internal data class ReaderEngineState(
    val isLoading: Boolean = true,
    val error: String? = null,
    val location: ReaderLocation = ReaderLocation("page:0", 0, 0.0),
    val pageCount: Int? = null,
    val sections: List<ReaderSection> = emptyList(),
)

internal interface ReaderEngineController {
    val state: ReaderEngineState
    val capabilities: ReaderEngineCapabilities
    fun next()
    fun previous()
    fun goTo(location: String)
}

internal data class ReaderEngineInput(
    val fileUrl: String,
    val title: String,
    val initialPage: Int,
    val settings: ReaderDisplaySettings,
    val onLocationChanged: (ReaderLocation) -> Unit,
    val onPageCountKnown: (Int) -> Unit,
    val onSectionsKnown: (List<ReaderSection>) -> Unit,
)

internal typealias ReaderEngineContent = @Composable (ReaderEngineInput) -> Unit
```

- [ ] **Step 4: Run test to verify it passes**

Run:

```bash
./gradlew :androidApp:testDebugUnitTest --tests com.continuum.app.android.ui.screens.reader.ReaderEngineSelectionTest
```

Expected: pass.

- [ ] **Step 5: Commit**

```bash
git add \
  androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/reader/ReaderEngine.kt \
  androidApp/src/androidUnitTest/kotlin/com/continuum/app/android/ui/screens/reader/ReaderEngineSelectionTest.kt
git commit -m "Add reader engine contract"
```

---

### Task 3: Add Reader Progress Location Mapping

**Files:**
- Modify: `androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/reader/ReaderEngine.kt`
- Test: `androidApp/src/androidUnitTest/kotlin/com/continuum/app/android/ui/screens/reader/ReaderProgressLocationTest.kt`

- [ ] **Step 1: Write failing progress mapping tests**

Create `ReaderProgressLocationTest.kt`:

```kotlin
package com.continuum.app.android.ui.screens.reader

import com.continuum.app.model.book.BookFormat
import kotlin.test.Test
import kotlin.test.assertEquals

class ReaderProgressLocationTest {

    @Test
    fun `pdf and comic map page indexes to page locations`() {
        assertEquals(
            ReaderLocation("page:4", pageIndex = 4, progress = 0.5),
            readerLocationForPage(BookFormat.Pdf, pageIndex = 4, pageCount = 8, spineHref = null),
        )
        assertEquals(
            ReaderLocation("page:2", pageIndex = 2, progress = 0.25),
            readerLocationForPage(BookFormat.Cbz, pageIndex = 2, pageCount = 8, spineHref = null),
        )
    }

    @Test
    fun `epub uses spine location when available`() {
        assertEquals(
            ReaderLocation("epub:chapter1.xhtml", pageIndex = 0, progress = 0.0),
            readerLocationForPage(BookFormat.Epub, pageIndex = 0, pageCount = 4, spineHref = "chapter1.xhtml"),
        )
    }

    @Test
    fun `progress clamps to valid range`() {
        assertEquals(0.0, readerProgressForPage(pageIndex = -1, pageCount = 10))
        assertEquals(1.0, readerProgressForPage(pageIndex = 12, pageCount = 10))
        assertEquals(0.0, readerProgressForPage(pageIndex = 0, pageCount = 0))
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run:

```bash
./gradlew :androidApp:testDebugUnitTest --tests com.continuum.app.android.ui.screens.reader.ReaderProgressLocationTest
```

Expected: compile fails because mapping helpers do not exist.

- [ ] **Step 3: Add mapping helpers**

Append to `ReaderEngine.kt`:

```kotlin
internal fun readerProgressForPage(pageIndex: Int, pageCount: Int?): Double {
    val count = pageCount ?: return 0.0
    if (count <= 1) return 0.0
    return (pageIndex.toDouble() / count.toDouble()).coerceIn(0.0, 1.0)
}

internal fun readerLocationForPage(
    format: BookFormat,
    pageIndex: Int,
    pageCount: Int?,
    spineHref: String?,
): ReaderLocation {
    val normalizedPage = pageIndex.coerceAtLeast(0)
    val location = when {
        format == BookFormat.Epub && !spineHref.isNullOrBlank() -> "epub:$spineHref"
        else -> "page:$normalizedPage"
    }
    return ReaderLocation(
        value = location,
        pageIndex = normalizedPage,
        progress = readerProgressForPage(normalizedPage, pageCount),
    )
}
```

- [ ] **Step 4: Run test to verify it passes**

Run:

```bash
./gradlew :androidApp:testDebugUnitTest --tests com.continuum.app.android.ui.screens.reader.ReaderProgressLocationTest
```

Expected: pass.

- [ ] **Step 5: Commit**

```bash
git add \
  androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/reader/ReaderEngine.kt \
  androidApp/src/androidUnitTest/kotlin/com/continuum/app/android/ui/screens/reader/ReaderProgressLocationTest.kt
git commit -m "Add reader progress location mapping"
```

---

### Task 4: Add Reader Chrome State Reducer

**Files:**
- Create: `androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/reader/ReaderChromeState.kt`
- Test: `androidApp/src/androidUnitTest/kotlin/com/continuum/app/android/ui/screens/reader/ReaderChromeStateTest.kt`

- [ ] **Step 1: Write failing chrome state tests**

Create `ReaderChromeStateTest.kt`:

```kotlin
package com.continuum.app.android.ui.screens.reader

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ReaderChromeStateTest {

    @Test
    fun `tap toggles chrome visibility`() {
        val hidden = ReaderChromeState(isVisible = false)
        val shown = reduceReaderChrome(hidden, ReaderChromeAction.Toggle)
        assertTrue(shown.isVisible)

        val hiddenAgain = reduceReaderChrome(shown, ReaderChromeAction.Toggle)
        assertFalse(hiddenAgain.isVisible)
    }

    @Test
    fun `opening a sheet keeps chrome visible`() {
        val state = reduceReaderChrome(
            ReaderChromeState(isVisible = false),
            ReaderChromeAction.OpenSheet(ReaderSheet.Sections),
        )

        assertTrue(state.isVisible)
        assertEquals(ReaderSheet.Sections, state.activeSheet)
    }

    @Test
    fun `auto hide closes chrome but not active sheet`() {
        val withSheet = ReaderChromeState(isVisible = true, activeSheet = ReaderSheet.Display)
        assertEquals(withSheet, reduceReaderChrome(withSheet, ReaderChromeAction.AutoHide))

        val noSheet = ReaderChromeState(isVisible = true, activeSheet = null)
        assertFalse(reduceReaderChrome(noSheet, ReaderChromeAction.AutoHide).isVisible)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run:

```bash
./gradlew :androidApp:testDebugUnitTest --tests com.continuum.app.android.ui.screens.reader.ReaderChromeStateTest
```

Expected: compile fails because reducer types do not exist.

- [ ] **Step 3: Implement reducer**

Create `ReaderChromeState.kt`:

```kotlin
package com.continuum.app.android.ui.screens.reader

internal enum class ReaderSheet {
    Sections,
    Bookmarks,
    Display,
    More,
}

internal data class ReaderChromeState(
    val isVisible: Boolean = false,
    val activeSheet: ReaderSheet? = null,
)

internal sealed interface ReaderChromeAction {
    data object Toggle : ReaderChromeAction
    data object Show : ReaderChromeAction
    data object Hide : ReaderChromeAction
    data object AutoHide : ReaderChromeAction
    data object CloseSheet : ReaderChromeAction
    data class OpenSheet(val sheet: ReaderSheet) : ReaderChromeAction
}

internal fun reduceReaderChrome(
    state: ReaderChromeState,
    action: ReaderChromeAction,
): ReaderChromeState =
    when (action) {
        ReaderChromeAction.Toggle -> state.copy(isVisible = !state.isVisible)
        ReaderChromeAction.Show -> state.copy(isVisible = true)
        ReaderChromeAction.Hide -> state.copy(isVisible = false, activeSheet = null)
        ReaderChromeAction.AutoHide -> if (state.activeSheet == null) state.copy(isVisible = false) else state
        ReaderChromeAction.CloseSheet -> state.copy(activeSheet = null)
        is ReaderChromeAction.OpenSheet -> state.copy(isVisible = true, activeSheet = action.sheet)
    }
```

- [ ] **Step 4: Run test to verify it passes**

Run:

```bash
./gradlew :androidApp:testDebugUnitTest --tests com.continuum.app.android.ui.screens.reader.ReaderChromeStateTest
```

Expected: pass.

- [ ] **Step 5: Commit**

```bash
git add \
  androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/reader/ReaderChromeState.kt \
  androidApp/src/androidUnitTest/kotlin/com/continuum/app/android/ui/screens/reader/ReaderChromeStateTest.kt
git commit -m "Add immersive reader chrome state"
```

---

### Task 5: Build Premium Reader Scaffold

**Files:**
- Create: `androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/reader/ReaderScaffold.kt`
- Modify: `androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/reader/ReaderScreen.kt`
- Test: run existing reader tests and compile.

- [ ] **Step 1: Create scaffold composable**

Create `ReaderScaffold.kt`:

```kotlin
package com.continuum.app.android.ui.screens.reader

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

@Composable
internal fun ReaderScaffold(
    title: String,
    subtitle: String?,
    progress: Double,
    pageLabel: String,
    isLoading: Boolean,
    error: String?,
    capabilities: ReaderEngineCapabilities,
    onBackClick: () -> Unit,
    onBookmarkClick: () -> Unit,
    onSectionsClick: () -> Unit,
    onDisplayClick: () -> Unit,
    onMoreClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    var chrome by remember { mutableStateOf(ReaderChromeState(isVisible = false)) }

    LaunchedEffect(chrome.isVisible, chrome.activeSheet) {
        if (chrome.isVisible && chrome.activeSheet == null) {
            delay(3500)
            chrome = reduceReaderChrome(chrome, ReaderChromeAction.AutoHide)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .clickable { chrome = reduceReaderChrome(chrome, ReaderChromeAction.Toggle) },
    ) {
        when {
            isLoading -> ReaderCenteredStatus { CircularProgressIndicator() }
            error != null -> ReaderCenteredStatus { Text(error, color = Color.White, modifier = Modifier.padding(32.dp)) }
            else -> content()
        }

        AnimatedVisibility(chrome.isVisible, modifier = Modifier.align(Alignment.TopCenter)) {
            ReaderTopOverlay(
                title = title,
                subtitle = subtitle,
                capabilities = capabilities,
                onBackClick = onBackClick,
                onBookmarkClick = onBookmarkClick,
                onSectionsClick = {
                    chrome = reduceReaderChrome(chrome, ReaderChromeAction.OpenSheet(ReaderSheet.Sections))
                    onSectionsClick()
                },
                onDisplayClick = {
                    chrome = reduceReaderChrome(chrome, ReaderChromeAction.OpenSheet(ReaderSheet.Display))
                    onDisplayClick()
                },
                onMoreClick = {
                    chrome = reduceReaderChrome(chrome, ReaderChromeAction.OpenSheet(ReaderSheet.More))
                    onMoreClick()
                },
            )
        }

        AnimatedVisibility(chrome.isVisible, modifier = Modifier.align(Alignment.BottomCenter)) {
            ReaderBottomOverlay(progress = progress, pageLabel = pageLabel)
        }
    }

    when (chrome.activeSheet) {
        ReaderSheet.Sections -> ReaderPlaceholderSheet("Sections") {
            chrome = reduceReaderChrome(chrome, ReaderChromeAction.CloseSheet)
        }
        ReaderSheet.Bookmarks -> ReaderPlaceholderSheet("Bookmarks") {
            chrome = reduceReaderChrome(chrome, ReaderChromeAction.CloseSheet)
        }
        ReaderSheet.Display -> ReaderPlaceholderSheet("Display") {
            chrome = reduceReaderChrome(chrome, ReaderChromeAction.CloseSheet)
        }
        ReaderSheet.More -> ReaderPlaceholderSheet("More") {
            chrome = reduceReaderChrome(chrome, ReaderChromeAction.CloseSheet)
        }
        null -> Unit
    }
}

@Composable
private fun ReaderCenteredStatus(content: @Composable () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        content()
    }
}

@Composable
private fun ReaderTopOverlay(
    title: String,
    subtitle: String?,
    capabilities: ReaderEngineCapabilities,
    onBackClick: () -> Unit,
    onBookmarkClick: () -> Unit,
    onSectionsClick: () -> Unit,
    onDisplayClick: () -> Unit,
    onMoreClick: () -> Unit,
) {
    Surface(color = Color.Transparent) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.78f), Color.Transparent)))
                .padding(horizontal = 8.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBackClick) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(title, color = Color.White, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                subtitle?.let {
                    Text(it, color = Color.White.copy(alpha = 0.72f), style = MaterialTheme.typography.labelMedium, maxLines = 1)
                }
            }
            IconButton(onClick = onBookmarkClick, enabled = capabilities.supportsBookmarks) {
                Icon(Icons.Default.BookmarkAdd, contentDescription = "Add bookmark", tint = Color.White)
            }
            IconButton(onClick = onSectionsClick, enabled = capabilities.supportsSections) {
                Icon(Icons.AutoMirrored.Filled.MenuBook, contentDescription = "Sections", tint = Color.White)
            }
            IconButton(onClick = onDisplayClick) {
                Icon(Icons.Default.Tune, contentDescription = "Display", tint = Color.White)
            }
            IconButton(onClick = onMoreClick) {
                Icon(Icons.Default.MoreVert, contentDescription = "More", tint = Color.White)
            }
        }
    }
}

@Composable
private fun ReaderBottomOverlay(progress: Double, pageLabel: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.82f))))
            .padding(horizontal = 20.dp, vertical = 18.dp),
    ) {
        Text(pageLabel, color = Color.White.copy(alpha = 0.82f), style = MaterialTheme.typography.labelMedium)
        Spacer(Modifier.height(8.dp))
        LinearProgressIndicator(
            progress = { progress.toFloat().coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth().height(3.dp),
        )
    }
}

@Composable
private fun ReaderPlaceholderSheet(title: String, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(20.dp))
        Spacer(Modifier.size(24.dp))
    }
}
```

- [ ] **Step 2: Compile to catch Compose issues**

Run:

```bash
./gradlew :androidApp:compileDebugKotlinAndroid
```

Expected: compile passes. If it fails due `LinearProgressIndicator` signature mismatch, replace the call with the Material 3 overload used elsewhere in the project.

- [ ] **Step 3: Commit**

```bash
git add androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/reader/ReaderScaffold.kt
git commit -m "Add premium reader scaffold"
```

---

### Task 6: Convert ReaderScreen To Shell Coordinator

**Files:**
- Modify: `androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/reader/ReaderScreen.kt`

- [ ] **Step 1: Replace permanent chrome with scaffold**

In `ReaderScreen.kt`, keep bookmark/settings/section sheet functions for reuse, but replace the top-level `Column` with `ReaderScaffold`.

Use this structure inside `ReaderScreen`:

```kotlin
val engineKind = readerEngineKindFor(state.format)
val capabilities = ReaderEngineCapabilities.forFormat(state.format)
val pageLabel = "${(state.progressPercent * 100).toInt()}% · Page ${state.currentPage + 1}" +
    state.pageCount?.let { " of $it" }.orEmpty()

ReaderScaffold(
    title = state.title.ifBlank { "Reader" },
    subtitle = state.author,
    progress = state.progressPercent,
    pageLabel = pageLabel,
    isLoading = state.isLoading,
    error = state.error ?: when {
        state.fileUrl.isNullOrBlank() && !state.isLoading -> "No file available for this book."
        engineKind == null && !state.isLoading -> "Format not supported in the Prairie reader."
        else -> null
    },
    capabilities = capabilities,
    onBackClick = onBackClick,
    onBookmarkClick = viewModel::addBookmark,
    onSectionsClick = { showSections = true },
    onDisplayClick = { showSettings = true },
    onMoreClick = { },
) {
    when (engineKind) {
        ReaderEngineKind.Epub -> EpubReader(
            fileUrl = state.fileUrl!!,
            title = state.title,
            initialPage = state.currentPage,
            settings = state.displaySettings,
            onPageChanged = viewModel::onPageChanged,
            onPageCountKnown = viewModel::onPageCountKnown,
            onSectionsKnown = viewModel::setSections,
        )
        ReaderEngineKind.Pdf -> PdfReader(
            fileUrl = state.fileUrl!!,
            title = state.title,
            initialPage = state.currentPage,
            onPageChanged = viewModel::onPageChanged,
            onPageCountKnown = viewModel::onPageCountKnown,
        )
        ReaderEngineKind.Comic -> ComicReader(
            fileUrl = state.fileUrl!!,
            title = state.title,
            initialPage = state.currentPage,
            onPageChanged = viewModel::onPageChanged,
            onPageCountKnown = viewModel::onPageCountKnown,
        )
        null -> Unit
    }
}
```

Remove the old permanent `Row`, debug `Text`, and inline loading/error dispatch.

- [ ] **Step 2: Compile**

Run:

```bash
./gradlew :androidApp:compileDebugKotlinAndroid
```

Expected: compile passes.

- [ ] **Step 3: Commit**

```bash
git add androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/reader/ReaderScreen.kt
git commit -m "Use immersive shell for reader screen"
```

---

### Task 7: Extract EPUB Engine Internals

**Files:**
- Create: `androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/reader/EpubReaderEngine.kt`
- Modify: `androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/reader/EpubReader.kt`

- [ ] **Step 1: Move EPUB book parsing into engine file**

Create `EpubReaderEngine.kt` by moving `EpubBook`, `withReaderCss`, `resolveEpubFile`, and `sha1EpubUrl` from `EpubReader.kt`.

Ensure `resolveEpubFile` has this signature:

```kotlin
internal fun resolveEpubFile(
    context: Context,
    okHttp: OkHttpClient,
    url: String,
    serverUrl: String,
): File
```

Ensure errors are not swallowed:

```kotlin
val requestUrl = resolveReaderRequestUrl(url, serverUrl)
val req = Request.Builder().url(requestUrl).build()
okHttp.newCall(req).execute().use { resp ->
    if (!resp.isSuccessful) error("HTTP ${resp.code} fetching $requestUrl")
    val body = resp.body ?: error("Empty body for $requestUrl")
    FileOutputStream(target).use { out -> body.byteStream().copyTo(out) }
}
```

- [ ] **Step 2: Update `EpubReader.kt` imports**

Keep `EpubReader` as the render composable. It should call the moved helpers and still emit visible errors through `readerLoadErrorMessage`.

- [ ] **Step 3: Compile**

Run:

```bash
./gradlew :androidApp:compileDebugKotlinAndroid
```

Expected: compile passes.

- [ ] **Step 4: Commit**

```bash
git add \
  androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/reader/EpubReader.kt \
  androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/reader/EpubReaderEngine.kt
git commit -m "Extract EPUB reader engine internals"
```

---

### Task 8: Extract PDF Engine Internals

**Files:**
- Create: `androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/reader/PdfReaderEngine.kt`
- Modify: `androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/reader/PdfReader.kt`

- [ ] **Step 1: Move PDF file and renderer helpers**

Create `PdfReaderEngine.kt` and move:

```kotlin
internal fun openPdfRenderer(file: File): PdfRenderer

internal fun resolvePdfFile(
    context: Context,
    okHttp: OkHttpClient,
    url: String,
    serverUrl: String,
): File
```

The resolver must use `resolveReaderRequestUrl(url, serverUrl)` for remote requests and must preserve `file://` and `content://` handling.

- [ ] **Step 2: Update `PdfReader.kt`**

Replace calls to old private `openRenderer` and `resolveToLocalFile` with the new internal helpers.

- [ ] **Step 3: Compile**

Run:

```bash
./gradlew :androidApp:compileDebugKotlinAndroid
```

Expected: compile passes.

- [ ] **Step 4: Commit**

```bash
git add \
  androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/reader/PdfReader.kt \
  androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/reader/PdfReaderEngine.kt
git commit -m "Extract PDF reader engine internals"
```

---

### Task 9: Extract Comic Engine Internals

**Files:**
- Create: `androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/reader/ComicReaderEngine.kt`
- Modify: `androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/reader/ComicReader.kt`
- Test: `androidApp/src/androidUnitTest/kotlin/com/continuum/app/android/ui/screens/reader/ComicArchiveLoaderTest.kt`

- [ ] **Step 1: Move archive helper types**

Move these from `ComicReader.kt` to `ComicReaderEngine.kt`:

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

internal fun loadComicArchivePages(file: File): ComicArchiveLoadResult
```

Also move `resolveComicFile` and `sha1Comic`.

- [ ] **Step 2: Ensure existing archive tests still target moved helpers**

`ComicArchiveLoaderTest.kt` should still pass with:

```kotlin
assertIs<ComicArchiveLoadResult.Loaded>(loadComicArchivePages(file))
assertIs<ComicArchiveLoadResult.Empty>(loadComicArchivePages(file))
assertIs<ComicArchiveLoadResult.Error>(loadComicArchivePages(file))
```

- [ ] **Step 3: Run test**

Run:

```bash
./gradlew :androidApp:testDebugUnitTest --tests com.continuum.app.android.ui.screens.reader.ComicArchiveLoaderTest
```

Expected: pass.

- [ ] **Step 4: Compile**

Run:

```bash
./gradlew :androidApp:compileDebugKotlinAndroid
```

Expected: compile passes.

- [ ] **Step 5: Commit**

```bash
git add \
  androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/reader/ComicReader.kt \
  androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/reader/ComicReaderEngine.kt \
  androidApp/src/androidUnitTest/kotlin/com/continuum/app/android/ui/screens/reader/ComicArchiveLoaderTest.kt
git commit -m "Extract comic reader engine internals"
```

---

### Task 10: Wire Real Sheets Into Reader Scaffold

**Files:**
- Modify: `androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/reader/ReaderScaffold.kt`
- Modify: `androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/reader/ReaderScreen.kt`

- [ ] **Step 1: Add sheet slot parameters**

Change `ReaderScaffold` signature to accept sheet content:

```kotlin
sectionsSheet: @Composable (onDismiss: () -> Unit) -> Unit,
bookmarksSheet: @Composable (onDismiss: () -> Unit) -> Unit,
displaySheet: @Composable (onDismiss: () -> Unit) -> Unit,
moreSheet: @Composable (onDismiss: () -> Unit) -> Unit,
```

Replace temporary sheet calls with:

```kotlin
ReaderSheet.Sections -> sectionsSheet {
    chrome = reduceReaderChrome(chrome, ReaderChromeAction.CloseSheet)
}
ReaderSheet.Bookmarks -> bookmarksSheet {
    chrome = reduceReaderChrome(chrome, ReaderChromeAction.CloseSheet)
}
ReaderSheet.Display -> displaySheet {
    chrome = reduceReaderChrome(chrome, ReaderChromeAction.CloseSheet)
}
ReaderSheet.More -> moreSheet {
    chrome = reduceReaderChrome(chrome, ReaderChromeAction.CloseSheet)
}
```

- [ ] **Step 2: Pass existing reader sheets from `ReaderScreen`**

In `ReaderScreen`, pass:

```kotlin
sectionsSheet = { onDismiss ->
    SectionsSheet(
        sections = state.sections,
        onJumpTo = { section ->
            ebookPageNumberFromProgressLocation(section.location)?.let(viewModel::jumpToPage)
            onDismiss()
        },
        onDismiss = onDismiss,
    )
},
bookmarksSheet = { onDismiss ->
    BookmarkSheet(
        bookmarks = state.bookmarks,
        onJumpTo = { bookmark ->
            ebookPageNumberFromProgressLocation(bookmark.location)?.let(viewModel::jumpToPage)
            onDismiss()
        },
        onDelete = viewModel::deleteBookmark,
        onDismiss = onDismiss,
    )
},
displaySheet = { onDismiss ->
    ReaderSettingsSheet(
        settings = state.displaySettings,
        capabilities = state.capabilities,
        onSettingsChange = viewModel::setDisplaySettings,
        onDismiss = onDismiss,
    )
},
moreSheet = { onDismiss ->
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Text("More", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(16.dp))
        Text("Open with another app and manage downloads will live here.", modifier = Modifier.padding(16.dp))
    }
},
```

- [ ] **Step 3: Compile**

Run:

```bash
./gradlew :androidApp:compileDebugKotlinAndroid
```

Expected: compile passes.

- [ ] **Step 4: Commit**

```bash
git add \
  androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/reader/ReaderScaffold.kt \
  androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/reader/ReaderScreen.kt
git commit -m "Wire reader sheets into immersive shell"
```

---

### Task 11: Add EPUB Typography Defaults

**Files:**
- Modify: `androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/reader/EpubReaderEngine.kt`
- Modify: `androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/reader/ReaderScreen.kt`

- [ ] **Step 1: Set EPUB CSS to premium defaults**

In `String.withReaderCss(settings)`, use readable defaults:

```kotlin
val style = """
    <style>
    html, body { $colors }
    body {
      font-size: ${fontPercent}%;
      margin: ${marginEm}em;
      line-height: 1.6;
      font-family: Georgia, "Noto Serif", serif;
      text-rendering: optimizeLegibility;
      -webkit-font-smoothing: antialiased;
    }
    p { margin: 0 0 1em 0; }
    img { max-width: 100%; height: auto; }
    </style>
""".trimIndent()
```

- [ ] **Step 2: Ensure dark app chrome does not force dark EPUB pages**

Keep EPUB colors controlled by `ReaderTheme`:

```kotlin
ReaderTheme.System, ReaderTheme.Light -> "color: #1f1b16; background: #fbf5e8;"
ReaderTheme.Sepia -> "color: #2b2118; background: #f4ecd8;"
ReaderTheme.Dark -> "color: #e6e1e5; background: #151316;"
```

- [ ] **Step 3: Compile**

Run:

```bash
./gradlew :androidApp:compileDebugKotlinAndroid
```

Expected: compile passes.

- [ ] **Step 4: Commit**

```bash
git add androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/reader/EpubReaderEngine.kt
git commit -m "Improve EPUB reader typography"
```

---

### Task 12: Mobile Device Verification

**Files:**
- No code changes expected unless verification finds issues.

- [ ] **Step 1: Install debug build on Pixel**

Run:

```bash
ANDROID_SERIAL=58211FDCQ000CU ./gradlew :androidApp:installDebug
```

Expected: install succeeds on Pixel 10 Pro XL.

- [ ] **Step 2: Start filtered logcat**

Run:

```bash
adb -s 58211FDCQ000CU logcat -c
adb -s 58211FDCQ000CU logcat -v time | rg --line-buffered "FATAL EXCEPTION|AndroidRuntime|com\\.continuum\\.app|reader|Reader|epub|Epub|ebook|Ebook|HTTP|Could not open"
```

Expected: command stays running without immediate app crash output.

- [ ] **Step 3: Verify EPUB**

On device:

1. Open Prairie.
2. Go to Reading.
3. Pick an EPUB.
4. Tap Read.
5. Confirm content opens.
6. Tap center; confirm chrome appears.
7. Wait 4 seconds; confirm chrome hides.
8. Open Display sheet.
9. Change text size/theme.
10. Back out and reopen; confirm progress is preserved.

Expected: no crash, no infinite spinner, no permanent toolbar, readable premium layout.

- [ ] **Step 4: Verify PDF**

On device:

1. Open a PDF ebook.
2. Confirm pages render.
3. Swipe pages.
4. Toggle chrome.
5. Confirm bottom progress changes.

Expected: no crash and no stuck loading state.

- [ ] **Step 5: Verify CBZ**

On device:

1. Open a CBZ/comic.
2. Confirm page images render.
3. Swipe pages.
4. Toggle chrome.
5. Confirm progress changes.

Expected: no crash and no stuck loading state.

- [ ] **Step 6: Commit verification fixes if needed**

If verification required code changes:

```bash
git status --short
git add <changed-reader-files>
git commit -m "Polish premium reader verification issues"
```

Expected: only reader-related files are staged.

---

### Task 13: Final Test Sweep

**Files:**
- No code changes expected.

- [ ] **Step 1: Run focused reader and auth tests**

Run:

```bash
./gradlew :android-shared:testDebugUnitTest --tests com.continuum.app.common.di.PlayerModuleTest
./gradlew :android-shared:testDebugUnitTest --tests com.continuum.app.common.player.MediaAuthInterceptorTest
./gradlew :androidApp:testDebugUnitTest --tests 'com.continuum.app.android.ui.screens.reader.*'
```

Expected: all pass.

- [ ] **Step 2: Run app builds**

Run:

```bash
./gradlew :androidApp:assembleDebug :androidTvApp:assembleDebug
```

Expected: both assemble successfully. TV must not gain ebook reader code or navigation.

- [ ] **Step 3: Review git diff**

Run:

```bash
git status --short
git diff --stat
```

Expected: changes are limited to mobile reader, shared auth/foundation fixes, tests, and docs. Existing unrelated TV player changes should remain separate unless intentionally committed earlier.

- [ ] **Step 4: Final commit if needed**

If final verification touched files:

```bash
git add <reader-related-files>
git commit -m "Complete premium mobile reader subsystem"
```

Expected: clean logical commit.

---

## Self-Review Notes

- Spec coverage: plan covers the shell, engine interface, EPUB/PDF/comic engines, file/network foundation, progress mapping, state-facing behavior, tests, and device verification.
- Ebooks on TV: plan only assembles TV for regression coverage and does not add ebook UI to TV.
- Known sequencing: Task 1 commits already-discovered foundation fixes before the larger reader rebuild, so implementation can proceed from a stable baseline.
