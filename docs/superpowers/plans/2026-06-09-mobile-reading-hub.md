# Mobile Reading Hub Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the generic mobile Reading tab with a dedicated mobile-only hub for ebook and audiobook libraries.

**Architecture:** Add small shared helpers for reading library types, then create a focused `ReadingHubViewModel`/`ReadingHubScreen` that reuses the existing library catalog/section APIs while filtering visible libraries to literary types. Wire only the mobile `Reading` tab to the new hub; Android TV remains unchanged and continues hiding Reading.

**Tech Stack:** Kotlin Multiplatform shared helpers/tests, Android Jetpack Compose, Koin ViewModel injection, existing `PersonalDataRepository`, `SectionRepository`, and `CatalogRepository`.

---

## File Structure

- Modify `shared/src/commonMain/kotlin/com/continuum/app/model/navigation/MediaMode.kt`
  - Add shared reading-library helper functions.
- Modify `shared/src/commonTest/kotlin/com/continuum/app/model/navigation/MediaModeTest.kt`
  - Cover reading library filtering.
- Create `androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/reading/ReadingHubViewModel.kt`
  - Loads only reading libraries and selected-library content.
- Create `androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/reading/ReadingHubScreen.kt`
  - Mobile Reading UI with library selector, format chips, and Recommended/Browse/Collections tabs.
- Modify `androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/MainScreen.kt`
  - Render `ReadingHubScreen` for `Tab.Reading`.
- Modify `androidApp/src/androidMain/kotlin/com/continuum/app/android/di/AndroidModule.kt`
  - Register `ReadingHubViewModel`.

## Task 1: Reading Library Helpers

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/continuum/app/model/navigation/MediaMode.kt`
- Modify: `shared/src/commonTest/kotlin/com/continuum/app/model/navigation/MediaModeTest.kt`

- [ ] **Step 1: Add failing shared tests**

Add tests to `MediaModeTest.kt`:

```kotlin
@Test
fun identifiesReadingLibraryTypes() {
    val readingTypes = listOf("audiobook", "audiobooks", "ebook", "ebooks", "book", "books", "comic", "comics", "manga", "reading")

    assertEquals(readingTypes, readingTypes.filter(::isReadingLibraryType))
}

@Test
fun filtersReadingLibrariesOnly() {
    val libraries = listOf(
        userLibrary(id = 1, type = "movies"),
        userLibrary(id = 2, type = "audiobooks"),
        userLibrary(id = 3, type = "ebooks"),
        userLibrary(id = 4, type = "music"),
    )

    assertEquals(listOf(2, 3), libraries.readingLibraries().map { it.id })
}
```

- [ ] **Step 2: Run the failing tests**

Run:

```bash
./gradlew :shared:testDebugUnitTest --tests com.continuum.app.model.navigation.MediaModeTest
```

Expected: FAIL because `isReadingLibraryType` and `readingLibraries` do not exist.

- [ ] **Step 3: Implement helpers**

Add to `MediaMode.kt`:

```kotlin
fun isReadingLibraryType(type: String?): Boolean =
    type?.trim()?.lowercase() in setOf(
        "audiobook",
        "audiobooks",
        "ebook",
        "ebooks",
        "book",
        "books",
        "comic",
        "comics",
        "manga",
        "reading",
    )

fun Iterable<UserLibrary>.readingLibraries(): List<UserLibrary> =
    filter { isReadingLibraryType(it.type) }
```

- [ ] **Step 4: Run shared tests**

Run:

```bash
./gradlew :shared:testDebugUnitTest --tests com.continuum.app.model.navigation.MediaModeTest
```

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add shared/src/commonMain/kotlin/com/continuum/app/model/navigation/MediaMode.kt \
  shared/src/commonTest/kotlin/com/continuum/app/model/navigation/MediaModeTest.kt
git commit -m "feat: add reading library helpers"
```

## Task 2: Reading Hub ViewModel And Screen

**Files:**
- Create: `androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/reading/ReadingHubViewModel.kt`
- Create: `androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/reading/ReadingHubScreen.kt`

- [ ] **Step 1: Create ViewModel**

Create `ReadingHubViewModel.kt` using the existing `LibrariesViewModel` behavior as the template, but filter libraries with `readingLibraries()` before selection:

```kotlin
package com.continuum.app.android.ui.screens.reading

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.continuum.app.android.ui.screens.libraries.LibrariesSubtab
import com.continuum.app.android.ui.screens.libraries.LibraryBrowseSort
import com.continuum.app.model.catalog.BrowseItem
import com.continuum.app.model.navigation.readingLibraries
import com.continuum.app.model.personal.UserLibrary
import com.continuum.app.model.section.LibraryCollection
import com.continuum.app.model.section.ResolvedSection
import com.continuum.app.network.ApiResult
import com.continuum.app.repository.CatalogRepository
import com.continuum.app.repository.PersonalDataRepository
import com.continuum.app.repository.SectionRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ReadingHubUiState(
    val isLoadingLibraries: Boolean = true,
    val libraries: List<UserLibrary> = emptyList(),
    val selectedLibraryId: Int? = null,
    val selectedTab: LibrariesSubtab = LibrariesSubtab.Recommended,
    val isLoadingSections: Boolean = false,
    val sections: List<ResolvedSection> = emptyList(),
    val sectionsError: String? = null,
    val isLoadingCatalog: Boolean = false,
    val isLoadingMoreCatalog: Boolean = false,
    val catalogItems: List<BrowseItem> = emptyList(),
    val catalogTotal: Int = 0,
    val catalogHasMore: Boolean = false,
    val browseGenres: List<String> = emptyList(),
    val selectedBrowseGenre: String? = null,
    val browseSort: LibraryBrowseSort = LibraryBrowseSort.RecentlyAdded,
    val catalogError: String? = null,
    val isLoadingCollections: Boolean = false,
    val collections: List<LibraryCollection> = emptyList(),
    val collectionsError: String? = null,
    val librariesError: String? = null,
)
```

Implement methods equivalent to `LibrariesViewModel.refresh`, `selectLibrary`, `selectTab`, `selectBrowseGenre`, `selectBrowseSort`, `loadMoreCatalog`, `showBrowseFromRecommended`, and `retryCurrentTab`, with these differences:

```kotlin
val libraries = result.data
    .readingLibraries()
    .sortedBy { library -> library.sortOrder }
```

Use `CatalogRepository.browse(libraryId = libraryId, ...)`, `SectionRepository.getLibrarySections(libraryId)`, and `SectionRepository.getLibraryCollections(libraryId)` exactly as the generic library screen does.

- [ ] **Step 2: Create ReadingHubScreen**

Create `ReadingHubScreen.kt`. Keep it compact and reuse existing components:

```kotlin
package com.continuum.app.android.ui.screens.reading

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.continuum.app.android.ui.components.EmptyStateView
import com.continuum.app.android.ui.components.ErrorView
import com.continuum.app.android.ui.screens.browse.CatalogGrid
import com.continuum.app.android.ui.screens.home.HomeSectionRow
import com.continuum.app.android.ui.screens.libraries.LibrariesSubtab
import com.continuum.app.android.ui.screens.libraries.LibraryBrowseSort
import com.continuum.app.model.profile.Profile
import org.koin.compose.viewmodel.koinViewModel
```

Public composable:

```kotlin
@Composable
fun ReadingHubScreen(
    onItemClick: (String) -> Unit,
    onCollectionClick: (String, Int) -> Unit,
    activeProfile: Profile?,
    onSearchClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onSwitchProfileClick: () -> Unit,
    onSwitchServerClick: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ReadingHubViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val selectedLibrary = state.libraries.firstOrNull { it.id == state.selectedLibraryId }

    Scaffold(
        topBar = {
            ReadingHubTopBar(
                selectedLibraryName = selectedLibrary?.name,
                canSwitchLibrary = state.libraries.size > 1,
                onSearchClick = onSearchClick,
                onSettingsClick = onSettingsClick,
            )
        },
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        modifier = modifier,
    ) { padding ->
        ReadingHubContent(
            state = state,
            contentPadding = padding,
            onTabSelected = viewModel::selectTab,
            onLibrarySelected = viewModel::selectLibrary,
            onItemClick = onItemClick,
            onCollectionClick = onCollectionClick,
            onRetry = viewModel::retryCurrentTab,
            onShowBrowse = viewModel::showBrowseFromRecommended,
            onLoadMore = viewModel::loadMoreCatalog,
            onGenreChanged = viewModel::selectBrowseGenre,
            onSortChanged = viewModel::selectBrowseSort,
        )
    }
}
```

The content must include:

- Loading spinner while reading libraries load.
- Error state with retry when loading fails.
- Empty state title `No reading libraries`.
- Header/title `Reading`.
- Mode chips `All`, `Ebooks`, `Audiobooks` as visible presentation only for v1.
- Tabs `Recommended`, `Browse`, `Collections`.
- Recommended tab renders `HomeSectionRow` for sections and a `Continue Reading & Listening` label if a section title contains `continue` or `progress`.
- Browse tab renders `CatalogGrid`.
- Collections tab renders a simple list/grid of collection names. It can be simpler than `LibrariesScreen` as long as collection click works.

Do not add unified `Ebook + Audiobook` badges.

- [ ] **Step 3: Compile mobile**

Run:

```bash
./gradlew :androidApp:compileDebugKotlinAndroid
```

Expected: PASS.

- [ ] **Step 4: Commit**

```bash
git add androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/reading/ReadingHubViewModel.kt \
  androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/reading/ReadingHubScreen.kt
git commit -m "feat: add mobile reading hub surface"
```

## Task 3: Wire Reading Hub Into Mobile

**Files:**
- Modify: `androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/MainScreen.kt`
- Modify: `androidApp/src/androidMain/kotlin/com/continuum/app/android/di/AndroidModule.kt`

- [ ] **Step 1: Register ViewModel**

In `AndroidModule.kt`, import and register:

```kotlin
import com.continuum.app.android.ui.screens.reading.ReadingHubViewModel
```

Add near the other mobile ViewModels:

```kotlin
viewModel { ReadingHubViewModel(get(), get(), get()) }
```

- [ ] **Step 2: Render ReadingHubScreen for Tab.Reading**

In `MainScreen.kt`, import:

```kotlin
import com.continuum.app.android.ui.screens.reading.ReadingHubScreen
```

Replace the `Tab.Reading` branch with:

```kotlin
Tab.Reading -> {
    ReadingHubScreen(
        onItemClick = { contentId ->
            navController.navigate(Route.ItemDetail(contentId).route)
        },
        onCollectionClick = { collectionId, libraryId ->
            navController.navigate(Route.CollectionDetail(collectionId, libraryId).route)
        },
        activeProfile = headerState.activeProfile,
        onSearchClick = { navController.navigate(Route.Search.route) },
        onSettingsClick = { navController.navigate(Route.Settings.route) },
        onSwitchProfileClick = {
            navController.navigate(Route.ProfileSelection.route)
        },
        onSwitchServerClick = {
            navController.navigate(Route.ServerList.route)
        },
    )
}
```

Leave `Tab.Audio` on the existing `LibrariesScreen`. Ensure `librariesViewModel` and `LibrariesSelectorSheet` are no longer needed for `Tab.Reading`; they should be Audio-only:

```kotlin
val librariesViewModel = if (currentTab == Tab.Audio) {
    koinViewModel<LibrariesViewModel>()
} else {
    null
}
```

And:

```kotlin
if (currentTab == Tab.Audio && librariesState != null && showLibrarySelector) { ... }
```

- [ ] **Step 3: Run verification**

Run:

```bash
git diff --check && ./gradlew :shared:testDebugUnitTest \
  :androidApp:testDebugUnitTest \
  :androidApp:compileDebugKotlinAndroid \
  :androidTvApp:testDebugUnitTest \
  :androidTvApp:compileDebugKotlinAndroid
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 4: Commit**

```bash
git add androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/MainScreen.kt \
  androidApp/src/androidMain/kotlin/com/continuum/app/android/di/AndroidModule.kt
git commit -m "feat: wire mobile reading hub"
```

## Final Verification

Run:

```bash
git diff --check && ./gradlew :shared:testDebugUnitTest \
  :androidApp:testDebugUnitTest \
  :androidApp:compileDebugKotlinAndroid \
  :androidTvApp:testDebugUnitTest \
  :androidTvApp:compileDebugKotlinAndroid \
  && git status --short --branch
```

Expected: `BUILD SUCCESSFUL` and a clean worktree.
