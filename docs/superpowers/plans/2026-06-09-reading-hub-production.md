# Reading Hub Production Pass Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the mobile Reading hub production-ready with real ebook/audiobook format filtering, stable scoped loading, better empty states, and a Reading-scoped search entry point.

**Architecture:** Add shared reading-format classification next to the existing media-mode helpers, then have `ReadingHubViewModel` derive available formats and filtered libraries from those helpers. Keep all content calls library-scoped, and add a small optional route argument so the existing mobile search screen can preselect Reading without changing search semantics.

**Tech Stack:** Kotlin Multiplatform shared models/tests, Android Jetpack Compose, Koin ViewModels, Navigation Compose, coroutines `StateFlow`, existing repository APIs.

---

## File Structure

- Modify `shared/src/commonMain/kotlin/com/continuum/app/model/navigation/MediaMode.kt`
  - Owns normalized library-type classification, `ReadingFormatFilter`, and reusable list filters.
- Modify `shared/src/commonTest/kotlin/com/continuum/app/model/navigation/MediaModeTest.kt`
  - Covers reading format classification and list filtering.
- Modify `androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/reading/ReadingHubViewModel.kt`
  - Adds selected format state, available formats, filtered libraries, and guarded content resets.
- Modify `androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/reading/ReadingHubScreen.kt`
  - Adds visible functional format chips, passes filtered library data to the selector, and improves format-specific empty copy.
- Modify `androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/navigation/Routes.kt`
  - Converts Search into an optional-argument route builder.
- Modify `androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/navigation/AppNavigation.kt`
  - Registers the optional search media-type argument and applies it to the Search ViewModel.
- Modify `androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/search/SearchViewModel.kt`
  - Adds route value parsing and one-shot initial media type selection.
- Modify `androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/search/SearchScreen.kt`
  - Accepts an initial media type and applies it after available modes are loaded.
- Modify `androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/MainScreen.kt`
  - Navigates Reading search to `Route.Search("reading").route` and updates other Search callers to `Route.Search().route`.

---

### Task 1: Shared Reading Format Model

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/continuum/app/model/navigation/MediaMode.kt`
- Test: `shared/src/commonTest/kotlin/com/continuum/app/model/navigation/MediaModeTest.kt`

- [ ] **Step 1: Add failing tests for reading format classification**

Append these tests before the private `userLibrary()` helper:

```kotlin
@Test
fun classifiesEbookLikeLibraryTypes() {
    val ebookTypes = listOf("ebook", "ebooks", "book", "books", "comic", "comics", "manga", "reading")

    assertEquals(ebookTypes, ebookTypes.filter(::isEbookLikeLibraryType))
    assertEquals(emptyList(), listOf("audiobook", "audiobooks", "music", "movies").filter(::isEbookLikeLibraryType))
}

@Test
fun classifiesAudiobookLikeLibraryTypes() {
    val audiobookTypes = listOf("audiobook", "audiobooks")

    assertEquals(audiobookTypes, audiobookTypes.filter(::isAudiobookLikeLibraryType))
    assertEquals(emptyList(), listOf("ebook", "music", "movies", "reading").filter(::isAudiobookLikeLibraryType))
}

@Test
fun filtersReadingLibrariesByFormat() {
    val libraries = listOf(
        userLibrary(id = 1, type = "movies"),
        userLibrary(id = 2, type = "audiobooks"),
        userLibrary(id = 3, type = "ebooks"),
        userLibrary(id = 4, type = "manga"),
        userLibrary(id = 5, type = "music"),
    )

    assertEquals(listOf(2, 3, 4), libraries.filterByReadingFormat(ReadingFormatFilter.All).map { it.id })
    assertEquals(listOf(3, 4), libraries.filterByReadingFormat(ReadingFormatFilter.Ebooks).map { it.id })
    assertEquals(listOf(2), libraries.filterByReadingFormat(ReadingFormatFilter.Audiobooks).map { it.id })
}

@Test
fun derivesAvailableReadingFormatsFromLibraries() {
    val mixedLibraries = listOf(
        userLibrary(id = 1, type = "ebooks"),
        userLibrary(id = 2, type = "audiobooks"),
    )
    val ebookLibraries = listOf(userLibrary(id = 3, type = "manga"))

    assertEquals(
        listOf(ReadingFormatFilter.All, ReadingFormatFilter.Ebooks, ReadingFormatFilter.Audiobooks),
        mixedLibraries.availableReadingFormatFilters(),
    )
    assertEquals(
        listOf(ReadingFormatFilter.Ebooks),
        ebookLibraries.availableReadingFormatFilters(),
    )
}
```

- [ ] **Step 2: Run shared test and verify it fails**

Run:

```bash
./gradlew :shared:testDebugUnitTest --tests "com.continuum.app.model.navigation.MediaModeTest"
```

Expected: FAIL because `ReadingFormatFilter`, `isEbookLikeLibraryType`, `isAudiobookLikeLibraryType`, `filterByReadingFormat`, and `availableReadingFormatFilters` do not exist.

- [ ] **Step 3: Implement shared reading format helpers**

In `MediaMode.kt`, add this enum and replace the repeated reading string set with helper functions:

```kotlin
enum class ReadingFormatFilter(val label: String) {
    All("All"),
    Ebooks("Ebooks"),
    Audiobooks("Audiobooks"),
}

private val ebookLikeLibraryTypes = setOf(
    "ebook",
    "ebooks",
    "book",
    "books",
    "comic",
    "comics",
    "manga",
    "reading",
)

private val audiobookLikeLibraryTypes = setOf(
    "audiobook",
    "audiobooks",
)

private fun normalizedLibraryType(type: String?): String? =
    type?.trim()?.lowercase()?.takeIf { it.isNotEmpty() }

fun isEbookLikeLibraryType(type: String?): Boolean =
    normalizedLibraryType(type) in ebookLikeLibraryTypes

fun isAudiobookLikeLibraryType(type: String?): Boolean =
    normalizedLibraryType(type) in audiobookLikeLibraryTypes

fun isReadingLibraryType(type: String?): Boolean =
    isEbookLikeLibraryType(type) || isAudiobookLikeLibraryType(type)

fun UserLibrary.matchesReadingFormat(format: ReadingFormatFilter): Boolean =
    when (format) {
        ReadingFormatFilter.All -> isReadingLibraryType(type)
        ReadingFormatFilter.Ebooks -> isEbookLikeLibraryType(type)
        ReadingFormatFilter.Audiobooks -> isAudiobookLikeLibraryType(type)
    }

fun Iterable<UserLibrary>.filterByReadingFormat(format: ReadingFormatFilter): List<UserLibrary> =
    filter { it.matchesReadingFormat(format) }

fun Iterable<UserLibrary>.availableReadingFormatFilters(): List<ReadingFormatFilter> {
    val libraries = toList()
    val hasEbooks = libraries.any { isEbookLikeLibraryType(it.type) }
    val hasAudiobooks = libraries.any { isAudiobookLikeLibraryType(it.type) }
    return buildList {
        if (hasEbooks && hasAudiobooks) add(ReadingFormatFilter.All)
        if (hasEbooks) add(ReadingFormatFilter.Ebooks)
        if (hasAudiobooks) add(ReadingFormatFilter.Audiobooks)
    }
}
```

Update the existing mappers to call the helper functions where it keeps behavior identical:

```kotlin
fun mobileMediaModeForLibraryType(type: String?): MediaMode? = when {
    normalizedLibraryType(type) in setOf("movie", "movies", "series", "show", "shows", "tv", "video") -> MediaMode.Video
    normalizedLibraryType(type) in setOf("music", "album", "albums", "artist", "artists", "audio") -> MediaMode.Audio
    isReadingLibraryType(type) -> MediaMode.Reading
    else -> null
}

fun Iterable<UserLibrary>.readingLibraries(): List<UserLibrary> =
    filterByReadingFormat(ReadingFormatFilter.All)
```

Keep `tvMediaModeForLibraryType()` mapping audiobooks to `MediaMode.Audio` and ebook-like types to `null`.

- [ ] **Step 4: Run shared test and verify it passes**

Run:

```bash
./gradlew :shared:testDebugUnitTest --tests "com.continuum.app.model.navigation.MediaModeTest"
```

Expected: PASS.

- [ ] **Step 5: Commit shared helpers**

```bash
git add shared/src/commonMain/kotlin/com/continuum/app/model/navigation/MediaMode.kt shared/src/commonTest/kotlin/com/continuum/app/model/navigation/MediaModeTest.kt
git commit -m "feat: add reading format helpers"
```

---

### Task 2: Reading Hub Format State

**Files:**
- Modify: `androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/reading/ReadingHubViewModel.kt`

- [ ] **Step 1: Add format fields to UI state**

Import the new helpers:

```kotlin
import com.continuum.app.model.navigation.ReadingFormatFilter
import com.continuum.app.model.navigation.availableReadingFormatFilters
import com.continuum.app.model.navigation.filterByReadingFormat
import com.continuum.app.model.navigation.matchesReadingFormat
```

Add these fields to `ReadingHubUiState`:

```kotlin
val selectedFormat: ReadingFormatFilter = ReadingFormatFilter.All,
val availableFormats: List<ReadingFormatFilter> = emptyList(),
val filteredLibraries: List<UserLibrary> = emptyList(),
```

- [ ] **Step 2: Derive filtered libraries on refresh**

In the success branch of `refresh()`, replace selected-library derivation with this pattern:

```kotlin
val libraries = result.data
    .readingLibraries()
    .sortedBy { library -> library.sortOrder }
val availableFormats = libraries.availableReadingFormatFilters()
val selectedFormat = _uiState.value.selectedFormat
    .takeIf { it in availableFormats }
    ?: availableFormats.firstOrNull()
    ?: ReadingFormatFilter.All
val filteredLibraries = libraries.filterByReadingFormat(selectedFormat)
val selectedLibraryId = _uiState.value.selectedLibraryId
    ?.takeIf { currentId -> filteredLibraries.any { it.id == currentId } }
    ?: filteredLibraries.firstOrNull()?.id
```

Include these values in the state update:

```kotlin
availableFormats = availableFormats,
selectedFormat = selectedFormat,
filteredLibraries = filteredLibraries,
selectedLibraryId = selectedLibraryId,
```

- [ ] **Step 3: Add format selection method**

Add this public method:

```kotlin
fun selectFormat(format: ReadingFormatFilter) {
    val state = _uiState.value
    if (format == state.selectedFormat || format !in state.availableFormats) return

    val filteredLibraries = state.libraries.filterByReadingFormat(format)
    val selectedLibraryId = state.selectedLibraryId
        ?.takeIf { currentId -> filteredLibraries.any { it.id == currentId } }
        ?: filteredLibraries.firstOrNull()?.id
    val libraryChanged = selectedLibraryId != state.selectedLibraryId

    if (libraryChanged) {
        recommendedLoadedLibraryId = null
        browseLoadedLibraryId = null
        collectionsLoadedLibraryId = null
    }

    _uiState.update {
        it.copy(
            selectedFormat = format,
            filteredLibraries = filteredLibraries,
            selectedLibraryId = selectedLibraryId,
            sections = if (libraryChanged) emptyList() else it.sections,
            sectionsError = if (libraryChanged) null else it.sectionsError,
            isLoadingSections = if (libraryChanged) false else it.isLoadingSections,
            catalogItems = if (libraryChanged) emptyList() else it.catalogItems,
            catalogTotal = if (libraryChanged) 0 else it.catalogTotal,
            catalogHasMore = if (libraryChanged) false else it.catalogHasMore,
            catalogError = if (libraryChanged) null else it.catalogError,
            isLoadingCatalog = if (libraryChanged) false else it.isLoadingCatalog,
            isLoadingMoreCatalog = if (libraryChanged) false else it.isLoadingMoreCatalog,
            browseGenres = if (libraryChanged) emptyList() else it.browseGenres,
            selectedBrowseGenre = if (libraryChanged) null else it.selectedBrowseGenre,
            collections = if (libraryChanged) emptyList() else it.collections,
            collectionsError = if (libraryChanged) null else it.collectionsError,
            isLoadingCollections = if (libraryChanged) false else it.isLoadingCollections,
        )
    }

    selectedLibraryId?.let { loadCurrentTab(it, force = libraryChanged) }
}
```

- [ ] **Step 4: Guard manual library selection against filtered-out libraries**

At the top of `selectLibrary(libraryId: Int)`, add:

```kotlin
if (_uiState.value.filteredLibraries.none { it.id == libraryId }) return
```

- [ ] **Step 5: Include selected format in async guards**

Update catalog request guards to capture the requested format:

```kotlin
val requestedFormat = state.selectedFormat
```

Pass it to a revised guard:

```kotlin
private fun isCurrentCatalogRequest(
    libraryId: Int,
    format: ReadingFormatFilter,
    genre: String?,
    sort: LibraryBrowseSort,
): Boolean {
    val state = _uiState.value
    return state.selectedLibraryId == libraryId &&
        state.selectedFormat == format &&
        state.selectedBrowseGenre == genre &&
        state.browseSort == sort
}
```

For recommended and collections, keep `isSelectedLibrary()` and add format validity:

```kotlin
private fun isSelectedLibrary(libraryId: Int): Boolean {
    val state = _uiState.value
    return state.selectedLibraryId == libraryId &&
        state.filteredLibraries.any { it.id == libraryId && it.matchesReadingFormat(state.selectedFormat) }
}
```

- [ ] **Step 6: Compile the Android app module**

Run:

```bash
./gradlew :androidApp:compileDebugKotlinAndroid
```

Expected: PASS.

- [ ] **Step 7: Commit ViewModel format state**

```bash
git add androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/reading/ReadingHubViewModel.kt
git commit -m "feat: add reading hub format state"
```

---

### Task 3: Reading Hub Format UI And Empty States

**Files:**
- Modify: `androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/reading/ReadingHubScreen.kt`

- [ ] **Step 1: Import `ReadingFormatFilter`**

```kotlin
import com.continuum.app.model.navigation.ReadingFormatFilter
```

- [ ] **Step 2: Pass format callbacks from `ReadingHubScreen`**

When calling `ReadingHubTopBar`, pass:

```kotlin
onFormatSelected = viewModel::selectFormat,
```

- [ ] **Step 3: Update `ReadingHubTopBar` signature and selector data**

Change the signature to include:

```kotlin
onFormatSelected: (ReadingFormatFilter) -> Unit,
```

Use `state.filteredLibraries` for the library-menu visibility and rows:

```kotlin
val visibleLibraries = state.filteredLibraries
```

Replace `state.libraries.size > 1` checks with `visibleLibraries.size > 1`, and replace the dropdown loop with:

```kotlin
visibleLibraries.forEach { library ->
    DropdownMenuItem(
        text = { Text(library.name) },
        onClick = {
            libraryMenuExpanded = false
            onLibrarySelected(library.id)
        },
    )
}
```

- [ ] **Step 4: Add format chips below the top row**

Still inside `ReadingHubTopBar`, after the library dropdown, add:

```kotlin
if (state.availableFormats.size > 1) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(top = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        state.availableFormats.forEach { format ->
            FilterChip(
                selected = state.selectedFormat == format,
                onClick = { onFormatSelected(format) },
                label = { Text(format.label) },
            )
        }
    }
}
```

- [ ] **Step 5: Add format-specific empty state helper**

Add this helper near the bottom of the file:

```kotlin
private fun ReadingFormatFilter.emptyTitle(): String = when (this) {
    ReadingFormatFilter.All -> "No reading libraries"
    ReadingFormatFilter.Ebooks -> "No ebooks in this profile"
    ReadingFormatFilter.Audiobooks -> "No audiobooks in this profile"
}

private fun ReadingFormatFilter.emptySubtitle(): String = when (this) {
    ReadingFormatFilter.All -> "Ebooks and audiobooks visible to this profile will show up here"
    ReadingFormatFilter.Ebooks -> "Ebook, comic, and manga libraries visible to this profile will show up here"
    ReadingFormatFilter.Audiobooks -> "Audiobook libraries visible to this profile will show up here"
}
```

- [ ] **Step 6: Use format-specific empty states in `ReadingHubContent`**

Replace the `state.selectedLibraryId == null` branch with:

```kotlin
state.selectedLibraryId == null -> {
    EmptyStateView(
        title = state.selectedFormat.emptyTitle(),
        subtitle = state.selectedFormat.emptySubtitle(),
        icon = Icons.AutoMirrored.Filled.MenuBook,
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding),
    )
}
```

- [ ] **Step 7: Improve Browse count copy**

Replace:

```kotlin
text = "${state.catalogTotal} items",
```

with:

```kotlin
text = buildString {
    append(state.catalogTotal)
    append(if (state.catalogTotal == 1) " item" else " items")
    if (state.availableFormats.size > 1) {
        append(" in ")
        append(state.selectedFormat.label.lowercase())
    }
},
```

- [ ] **Step 8: Compile the Android app module**

Run:

```bash
./gradlew :androidApp:compileDebugKotlinAndroid
```

Expected: PASS.

- [ ] **Step 9: Commit Reading Hub UI**

```bash
git add androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/reading/ReadingHubScreen.kt
git commit -m "feat: add reading hub format filters"
```

---

### Task 4: Reading-Scoped Search Route

**Files:**
- Modify: `androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/navigation/Routes.kt`
- Modify: `androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/navigation/AppNavigation.kt`
- Modify: `androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/search/SearchViewModel.kt`
- Modify: `androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/search/SearchScreen.kt`
- Modify: `androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/MainScreen.kt`

- [ ] **Step 1: Convert Search route to optional argument**

In `Routes.kt`, replace:

```kotlin
data object Search : Route("search")
```

with:

```kotlin
data class Search(
    val mediaType: String? = null,
) : Route(
    mediaType
        ?.takeIf { it.isNotBlank() }
        ?.let { "search?mediaType=${Uri.encode(it)}" }
        ?: "search",
) {
    companion object {
        const val ROUTE = "search?mediaType={mediaType}"
    }
}
```

- [ ] **Step 2: Add Search ViewModel route parsing**

In `SearchViewModel.kt`, add this companion to `MobileSearchMediaType`:

```kotlin
companion object {
    fun fromRouteValue(value: String?): MobileSearchMediaType? =
        when (value?.trim()?.lowercase()) {
            "video" -> Video
            "audio" -> Audio
            "reading" -> Reading
            "all" -> All
            else -> null
        }
}
```

Add this ViewModel method:

```kotlin
fun selectInitialMediaType(mediaType: MobileSearchMediaType?) {
    if (mediaType == null) return
    val current = _uiState.value
    if (current.query.isNotBlank()) return
    if (mediaType !in current.availableMediaTypes) return
    _uiState.update { it.copy(mediaType = mediaType) }
}
```

- [ ] **Step 3: Accept initial media type in SearchScreen**

Add a parameter:

```kotlin
initialMediaType: MobileSearchMediaType? = null,
```

After the existing `LaunchedEffect(availableModes)` block, add:

```kotlin
LaunchedEffect(initialMediaType, state.availableMediaTypes) {
    viewModel.selectInitialMediaType(initialMediaType)
}
```

- [ ] **Step 4: Register optional route argument in AppNavigation**

Import Navigation Compose argument helpers if they are not already present:

```kotlin
import androidx.navigation.NavType
import androidx.navigation.navArgument
```

Replace the Search composable registration with:

```kotlin
composable(
    route = Route.Search.ROUTE,
    arguments = listOf(
        navArgument("mediaType") {
            type = NavType.StringType
            nullable = true
            defaultValue = null
        },
    ),
) { backStackEntry ->
    val searchViewModel = koinViewModel<SearchViewModel>()
    SearchScreen(
        onItemClick = { contentId ->
            navController.navigate(Route.ItemDetail(contentId).route)
        },
        onBackClick = { navController.popBackStack() },
        initialMediaType = MobileSearchMediaType.fromRouteValue(
            backStackEntry.arguments?.getString("mediaType"),
        ),
        viewModel = searchViewModel,
    )
}
```

Also import:

```kotlin
import com.continuum.app.android.ui.screens.search.MobileSearchMediaType
```

- [ ] **Step 5: Update all Search navigations in MainScreen**

Replace generic callers:

```kotlin
navController.navigate(Route.Search.route)
```

with:

```kotlin
navController.navigate(Route.Search().route)
```

For the Reading hub caller, use:

```kotlin
onSearchClick = { navController.navigate(Route.Search("reading").route) },
```

- [ ] **Step 6: Search for stale object-route usage**

Run:

```bash
rg -n "Route\\.Search\\.route|Route\\.Search\\)" androidApp/src/androidMain/kotlin
```

Expected: no `Route.Search.route` matches. `Route.Search()` and `Route.Search("reading")` matches are expected.

- [ ] **Step 7: Compile the Android app module**

Run:

```bash
./gradlew :androidApp:compileDebugKotlinAndroid
```

Expected: PASS.

- [ ] **Step 8: Commit scoped search route**

```bash
git add androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/navigation/Routes.kt androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/navigation/AppNavigation.kt androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/search/SearchViewModel.kt androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/search/SearchScreen.kt androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/MainScreen.kt
git commit -m "feat: scope reading hub search"
```

---

### Task 5: Full Verification And Review

**Files:**
- Verify all modified files.

- [ ] **Step 1: Run formatting and whitespace check**

Run:

```bash
git diff --check
```

Expected: no output.

- [ ] **Step 2: Run shared and Android unit tests**

Run:

```bash
./gradlew :shared:testDebugUnitTest :androidApp:testDebugUnitTest :androidTvApp:testDebugUnitTest
```

Expected: PASS.

- [ ] **Step 3: Run mobile and TV Kotlin compilation**

Run:

```bash
./gradlew :androidApp:compileDebugKotlinAndroid :androidTvApp:compileDebugKotlinAndroid
```

Expected: PASS. This is the guard that ebooks/Reading changes did not break TV.

- [ ] **Step 4: Inspect the final diff**

Run:

```bash
git diff --stat HEAD~4..HEAD
git diff HEAD~4..HEAD -- shared/src/commonMain/kotlin/com/continuum/app/model/navigation/MediaMode.kt androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/reading/ReadingHubViewModel.kt androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/reading/ReadingHubScreen.kt androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/navigation/Routes.kt androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/navigation/AppNavigation.kt androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/search/SearchViewModel.kt androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/search/SearchScreen.kt androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/MainScreen.kt
```

Expected review points:

- Android TV still filters out ebook-like libraries from visible modes.
- Android TV still maps audiobooks to Audio.
- Mobile Reading hub uses `filteredLibraries` for the selector.
- Format chips appear only when more than one format is available.
- Search from Reading navigates with `mediaType=reading`; other search entries stay generic.

- [ ] **Step 5: Commit verification fixes if needed**

If verification reveals a defect, fix it with the smallest scoped patch, rerun the failed command, and commit:

```bash
git add shared/src/commonMain/kotlin/com/continuum/app/model/navigation/MediaMode.kt shared/src/commonTest/kotlin/com/continuum/app/model/navigation/MediaModeTest.kt androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/reading/ReadingHubViewModel.kt androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/reading/ReadingHubScreen.kt androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/navigation/Routes.kt androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/navigation/AppNavigation.kt androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/search/SearchViewModel.kt androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/search/SearchScreen.kt androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/MainScreen.kt
git commit -m "fix: stabilize reading hub production pass"
```

If no fixes are needed, leave the branch at the commits from Tasks 1 through 4.
