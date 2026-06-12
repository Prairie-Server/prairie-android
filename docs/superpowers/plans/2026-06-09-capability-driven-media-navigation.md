# Capability-Driven Media Navigation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make Android mobile and Android TV show top-level media destinations only for media modes the current user actually has, while keeping Reading mobile-only.

**Architecture:** Add a small shared media-mode normalization layer backed by `/api/v1/user/libraries`, then have mobile and TV derive visible navigation entries from those shared capabilities. Keep existing screens as the first destination implementations; this slice establishes the app spine without rebuilding every rail.

**Tech Stack:** Kotlin Multiplatform shared models/tests, Jetpack Compose, Navigation Compose, Koin, kotlinx.coroutines `produceState`, existing Gradle test tasks.

---

## File Structure

- Create `shared/src/commonMain/kotlin/com/continuum/app/model/navigation/MediaMode.kt`
  - Defines `MediaMode`, library-type normalization, mobile/TV platform filtering, and first-mode helpers.
- Create `shared/src/commonTest/kotlin/com/continuum/app/model/navigation/MediaModeTest.kt`
  - Pure tests for normalization and platform filtering.
- Modify `androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/navigation/BottomNavBar.kt`
  - Replace fixed content tabs with media-mode-aware tab definitions while keeping `Downloads`.
- Create `androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/navigation/MobileMediaTabs.kt`
  - Holds mobile visible-tab derivation so `MainScreen` stays readable.
- Create `androidApp/src/androidUnitTest/kotlin/com/continuum/app/android/ui/navigation/MobileMediaTabsTest.kt`
  - JVM tests for mobile tab derivation.
- Modify `androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/navigation/Routes.kt`
  - Add stable routes for `video`, `audio`, and `reading`.
- Modify `androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/navigation/AppNavigation.kt`
  - Add composables for new main tab routes and make post-login/profile selection land on the selected start mode.
- Modify `androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/MainScreen.kt`
  - Load user libraries, derive visible tabs, preserve Downloads visibility, and redirect away from hidden current tabs.
- Create `androidTvApp/src/androidMain/kotlin/com/continuum/app/tv/ui/shell/TvMediaDestinations.kt`
  - Holds TV visible-destination derivation and route mapping.
- Create `androidTvApp/src/androidUnitTest/kotlin/com/continuum/app/tv/ui/shell/TvMediaDestinationsTest.kt`
  - JVM tests for TV destination derivation, including Reading-only fallback.
- Modify `androidTvApp/src/androidMain/kotlin/com/continuum/app/tv/ui/navigation/TvRoute.kt`
  - Adds `main/video` and `main/audio` routes while keeping legacy routes.
- Modify `androidTvApp/src/androidMain/kotlin/com/continuum/app/tv/ui/shell/TvTopMenuBar.kt`
  - Render a supplied destination list instead of always rendering Home/Libraries/For You/Requests.
- Modify `androidTvApp/src/androidMain/kotlin/com/continuum/app/tv/ui/shell/TvMainShell.kt`
  - Load capabilities, choose a start route, pass visible destinations to the top menu, and redirect hidden routes.
- Modify `androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/search/SearchViewModel.kt`
  - Add mobile search media modes and a media-type parameter.
- Modify `androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/search/SearchScreen.kt`
  - Show mode chips only for visible mobile modes.
- Modify `androidTvApp/src/androidMain/kotlin/com/continuum/app/tv/ui/screens/search/TvSearchViewModel.kt`
  - Keep TV search filters aligned with visible TV modes and continue excluding Reading.

## Task 1: Shared Media Mode Model

**Files:**
- Create: `shared/src/commonMain/kotlin/com/continuum/app/model/navigation/MediaMode.kt`
- Create: `shared/src/commonTest/kotlin/com/continuum/app/model/navigation/MediaModeTest.kt`

- [ ] **Step 1: Write failing shared tests**

Create `MediaModeTest.kt`:

```kotlin
package com.continuum.app.model.navigation

import com.continuum.app.model.personal.UserLibrary
import kotlin.test.Test
import kotlin.test.assertEquals

class MediaModeTest {
    @Test
    fun mapsKnownVideoTypesToVideo() {
        val modes = listOf("movie", "movies", "series", "show", "shows", "tv", "video")
            .mapNotNull(::mediaModeForLibraryType)
            .toSet()

        assertEquals(setOf(MediaMode.Video), modes)
    }

    @Test
    fun mapsKnownAudioTypesToAudio() {
        val modes = listOf("audiobook", "audiobooks", "music", "album", "albums", "artist", "artists", "audio")
            .mapNotNull(::mediaModeForLibraryType)
            .toSet()

        assertEquals(setOf(MediaMode.Audio), modes)
    }

    @Test
    fun mapsKnownReadingTypesToReading() {
        val modes = listOf("ebook", "ebooks", "book", "books", "comic", "comics", "manga", "reading")
            .mapNotNull(::mediaModeForLibraryType)
            .toSet()

        assertEquals(setOf(MediaMode.Reading), modes)
    }

    @Test
    fun ignoresBlankAndUnknownTypes() {
        val modes = listOf("", " ", "podcast", "photo").mapNotNull(::mediaModeForLibraryType)

        assertEquals(emptyList(), modes)
    }

    @Test
    fun derivesCapabilitiesInStableOrder() {
        val libraries = listOf(
            userLibrary(id = 1, type = "ebooks"),
            userLibrary(id = 2, type = "movies"),
            userLibrary(id = 3, type = "audiobook"),
        )

        assertEquals(
            listOf(MediaMode.Video, MediaMode.Audio, MediaMode.Reading),
            libraries.mediaModeCapabilities().modes,
        )
    }

    @Test
    fun tvCapabilitiesExcludeReading() {
        val libraries = listOf(
            userLibrary(id = 1, type = "ebooks"),
            userLibrary(id = 2, type = "audiobooks"),
        )

        assertEquals(
            listOf(MediaMode.Audio),
            libraries.mediaModeCapabilities().tvModes(),
        )
    }

    private fun userLibrary(id: Int, type: String): UserLibrary =
        UserLibrary(id = id, name = "Library $id", type = type)
}
```

- [ ] **Step 2: Run shared tests and verify they fail**

Run:

```bash
./gradlew :shared:testDebugUnitTest --tests com.continuum.app.model.navigation.MediaModeTest
```

Expected: FAIL because `MediaMode`, `mediaModeForLibraryType`, `mediaModeCapabilities`, and `tvModes` do not exist.

- [ ] **Step 3: Implement shared model**

Create `MediaMode.kt`:

```kotlin
package com.continuum.app.model.navigation

import com.continuum.app.model.personal.UserLibrary

enum class MediaMode(val label: String) {
    Video("Video"),
    Audio("Audio"),
    Reading("Reading"),
}

data class MediaModeCapabilities(
    val modes: List<MediaMode> = emptyList(),
) {
    val hasVideo: Boolean get() = MediaMode.Video in modes
    val hasAudio: Boolean get() = MediaMode.Audio in modes
    val hasReading: Boolean get() = MediaMode.Reading in modes

    fun mobileModes(): List<MediaMode> = modes
    fun tvModes(): List<MediaMode> = modes.filterNot { it == MediaMode.Reading }
    fun firstMobileMode(): MediaMode? = mobileModes().firstOrNull()
    fun firstTvMode(): MediaMode? = tvModes().firstOrNull()
}

fun mediaModeForLibraryType(type: String?): MediaMode? = when (type?.trim()?.lowercase()) {
    "movie", "movies", "series", "show", "shows", "tv", "video" -> MediaMode.Video
    "audiobook", "audiobooks", "music", "album", "albums", "artist", "artists", "audio" -> MediaMode.Audio
    "ebook", "ebooks", "book", "books", "comic", "comics", "manga", "reading" -> MediaMode.Reading
    else -> null
}

fun Iterable<UserLibrary>.mediaModeCapabilities(): MediaModeCapabilities {
    val present = mapNotNull { mediaModeForLibraryType(it.type) }.toSet()
    return MediaModeCapabilities(
        modes = listOf(MediaMode.Video, MediaMode.Audio, MediaMode.Reading).filter { it in present },
    )
}
```

- [ ] **Step 4: Run shared tests and verify they pass**

Run:

```bash
./gradlew :shared:testDebugUnitTest --tests com.continuum.app.model.navigation.MediaModeTest
```

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add shared/src/commonMain/kotlin/com/continuum/app/model/navigation/MediaMode.kt \
  shared/src/commonTest/kotlin/com/continuum/app/model/navigation/MediaModeTest.kt
git commit -m "feat: add media mode capabilities"
```

## Task 2: Mobile Visible Tab Derivation

**Files:**
- Modify: `androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/navigation/Routes.kt`
- Modify: `androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/navigation/BottomNavBar.kt`
- Create: `androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/navigation/MobileMediaTabs.kt`
- Create: `androidApp/src/androidUnitTest/kotlin/com/continuum/app/android/ui/navigation/MobileMediaTabsTest.kt`

- [ ] **Step 1: Write failing mobile tab tests**

Create `MobileMediaTabsTest.kt`:

```kotlin
package com.continuum.app.android.ui.navigation

import com.continuum.app.model.navigation.MediaMode
import com.continuum.app.model.navigation.MediaModeCapabilities
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MobileMediaTabsTest {
    @Test
    fun videoOnlyAccountShowsVideoAndDownloads() {
        val tabs = visibleMobileTabs(
            capabilities = MediaModeCapabilities(listOf(MediaMode.Video)),
            showDownloads = true,
        )

        assertEquals(listOf(Tab.Video, Tab.Downloads), tabs)
    }

    @Test
    fun audioOnlyAccountShowsAudioAndDownloads() {
        val tabs = visibleMobileTabs(
            capabilities = MediaModeCapabilities(listOf(MediaMode.Audio)),
            showDownloads = true,
        )

        assertEquals(listOf(Tab.Audio, Tab.Downloads), tabs)
    }

    @Test
    fun readingOnlyAccountShowsReadingAndDownloads() {
        val tabs = visibleMobileTabs(
            capabilities = MediaModeCapabilities(listOf(MediaMode.Reading)),
            showDownloads = true,
        )

        assertEquals(listOf(Tab.Reading, Tab.Downloads), tabs)
    }

    @Test
    fun allModesKeepStableOrder() {
        val tabs = visibleMobileTabs(
            capabilities = MediaModeCapabilities(listOf(MediaMode.Video, MediaMode.Audio, MediaMode.Reading)),
            showDownloads = true,
        )

        assertEquals(listOf(Tab.Video, Tab.Audio, Tab.Reading, Tab.Downloads), tabs)
    }

    @Test
    fun downloadsCanStayHiddenWhenNoDownloadsExist() {
        val tabs = visibleMobileTabs(
            capabilities = MediaModeCapabilities(listOf(MediaMode.Video, MediaMode.Audio)),
            showDownloads = false,
        )

        assertEquals(listOf(Tab.Video, Tab.Audio), tabs)
        assertFalse(Tab.Downloads in tabs)
    }

    @Test
    fun choosesFirstVisibleMediaTabBeforeDownloads() {
        assertEquals(
            Tab.Audio,
            fallbackMobileTab(
                visibleTabs = listOf(Tab.Audio, Tab.Downloads),
                defaultTab = Tab.Video,
            ),
        )
    }

    @Test
    fun keepsCurrentTabWhenStillVisible() {
        assertEquals(
            Tab.Downloads,
            fallbackMobileTab(
                visibleTabs = listOf(Tab.Audio, Tab.Downloads),
                defaultTab = Tab.Downloads,
            ),
        )
        assertTrue(Tab.Downloads.isUtilityTab)
    }
}
```

- [ ] **Step 2: Run mobile tab tests and verify they fail**

Run:

```bash
./gradlew :androidApp:testDebugUnitTest --tests com.continuum.app.android.ui.navigation.MobileMediaTabsTest
```

Expected: FAIL because the new tabs and helper functions do not exist.

- [ ] **Step 3: Add mode routes**

In `Routes.kt`, replace the existing main-tab routes with mode routes while keeping old routes available only if needed for deep-link compatibility:

```kotlin
// --- Main tabs (inside bottom nav scaffold) ---
data object Video : Route("video")
data object Audio : Route("audio")
data object Reading : Route("reading")
data object Downloads : Route("downloads")
data object Search : Route("search")
data object Settings : Route("settings")

// Legacy tab routes retained so old saved back stacks do not crash.
data object Home : Route("home")
data object Libraries : Route("libraries")
data object Recommendations : Route("recommendations")
```

- [ ] **Step 4: Update mobile Tab enum**

In `BottomNavBar.kt`, replace the existing enum values with:

```kotlin
enum class Tab(
    val route: String,
    val label: String,
    val icon: ImageVector,
    val selectedIcon: ImageVector,
) {
    Video(Route.Video.route, "Video", Icons.Outlined.LiveTv, Icons.Filled.LiveTv),
    Audio(Route.Audio.route, "Audio", Icons.Outlined.Headphones, Icons.Filled.Headphones),
    Reading(Route.Reading.route, "Reading", Icons.Outlined.MenuBook, Icons.Filled.MenuBook),
    Downloads(Route.Downloads.route, "Downloads", Icons.Outlined.Download, Icons.Filled.Download),
}
```

Add the needed imports:

```kotlin
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.outlined.Headphones
import androidx.compose.material.icons.outlined.LiveTv
import androidx.compose.material.icons.outlined.MenuBook
```

- [ ] **Step 5: Add mobile tab derivation helpers**

Create `MobileMediaTabs.kt`:

```kotlin
package com.continuum.app.android.ui.navigation

import com.continuum.app.model.navigation.MediaMode
import com.continuum.app.model.navigation.MediaModeCapabilities

val Tab.isUtilityTab: Boolean
    get() = this == Tab.Downloads

fun tabForMediaMode(mode: MediaMode): Tab = when (mode) {
    MediaMode.Video -> Tab.Video
    MediaMode.Audio -> Tab.Audio
    MediaMode.Reading -> Tab.Reading
}

fun visibleMobileTabs(
    capabilities: MediaModeCapabilities,
    showDownloads: Boolean,
): List<Tab> = buildList {
    capabilities.mobileModes().forEach { add(tabForMediaMode(it)) }
    if (showDownloads) add(Tab.Downloads)
}

fun fallbackMobileTab(
    visibleTabs: List<Tab>,
    defaultTab: Tab,
): Tab? {
    if (defaultTab in visibleTabs) return defaultTab
    return visibleTabs.firstOrNull { !it.isUtilityTab } ?: visibleTabs.firstOrNull()
}
```

- [ ] **Step 6: Run mobile tab tests and verify they pass**

Run:

```bash
./gradlew :androidApp:testDebugUnitTest --tests com.continuum.app.android.ui.navigation.MobileMediaTabsTest
```

Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/navigation/Routes.kt \
  androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/navigation/BottomNavBar.kt \
  androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/navigation/MobileMediaTabs.kt \
  androidApp/src/androidUnitTest/kotlin/com/continuum/app/android/ui/navigation/MobileMediaTabsTest.kt
git commit -m "feat: derive mobile media tabs"
```

## Task 3: Wire Mobile Shell To User Libraries

**Files:**
- Modify: `androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/navigation/AppNavigation.kt`
- Modify: `androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/MainScreen.kt`

- [ ] **Step 1: Update mobile main routes**

In `AppNavigation.kt`, add composables for `Route.Video`, `Route.Audio`, and `Route.Reading`. Keep legacy route composables temporarily mapped to the closest new tab:

```kotlin
composable(Route.Video.route) {
    MainScreen(navController, Tab.Video)
}
composable(Route.Audio.route) {
    MainScreen(navController, Tab.Audio)
}
composable(Route.Reading.route) {
    MainScreen(navController, Tab.Reading)
}
composable(Route.Downloads.route) {
    MainScreen(navController, Tab.Downloads)
}
composable(Route.Home.route) {
    MainScreen(navController, Tab.Video)
}
composable(Route.Libraries.route) {
    MainScreen(navController, Tab.Video)
}
composable(Route.Recommendations.route) {
    MainScreen(navController, Tab.Video)
}
```

Change auth/profile success navigation targets from `Route.Home.route` to `Route.Video.route` as the safe initial route. `MainScreen` will redirect to Audio or Reading if Video is hidden.

- [ ] **Step 2: Load capabilities in MainScreen**

In `MainScreen.kt`, inject `PersonalDataRepository`, load `UserLibrary` capabilities, and derive visible tabs:

```kotlin
val personalDataRepository: com.continuum.app.repository.PersonalDataRepository = org.koin.compose.koinInject()
val mediaCapabilities by produceState(
    initialValue = com.continuum.app.model.navigation.MediaModeCapabilities(
        listOf(
            com.continuum.app.model.navigation.MediaMode.Video,
            com.continuum.app.model.navigation.MediaMode.Audio,
            com.continuum.app.model.navigation.MediaMode.Reading,
        ),
    ),
    personalDataRepository,
) {
    value = when (val result = personalDataRepository.listUserLibraries()) {
        is com.continuum.app.network.ApiResult.Success -> result.data.mediaModeCapabilities()
        else -> value
    }
}
```

Add imports:

```kotlin
import androidx.compose.runtime.produceState
import com.continuum.app.android.ui.navigation.fallbackMobileTab
import com.continuum.app.android.ui.navigation.visibleMobileTabs
import com.continuum.app.model.navigation.MediaMode
import com.continuum.app.model.navigation.MediaModeCapabilities
import com.continuum.app.model.navigation.mediaModeCapabilities
```

- [ ] **Step 3: Combine media tabs with Downloads visibility**

Replace the old `visibleTabs` calculation with:

```kotlin
val visibleTabs = remember(mediaCapabilities, downloadRecords, hasLocalDownloads) {
    val hasAnyDownload = downloadRecords.isNotEmpty() || hasLocalDownloads
    visibleMobileTabs(
        capabilities = mediaCapabilities,
        showDownloads = hasAnyDownload,
    )
}
```

- [ ] **Step 4: Redirect hidden current tabs**

Replace the old redirect block with:

```kotlin
LaunchedEffect(currentTab, visibleTabs) {
    if (currentTab !in visibleTabs) {
        val fallback = fallbackMobileTab(visibleTabs, currentTab) ?: Tab.Video
        navController.navigate(fallback.route) {
            popUpTo(Route.Video.route) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }
}
```

- [ ] **Step 5: Route existing content for new tabs**

In the `when (currentTab)` body:

```kotlin
Tab.Video -> {
    val homeViewModel = koinViewModel<HomeViewModel>()
    HomeScreen(
        onItemClick = { contentId -> navController.navigate(Route.ItemDetail(contentId).route) },
        onPlayClick = { contentId -> navController.navigate(Route.Player(contentId).route) },
        onSeeAllClick = { navController.navigate(Route.Browse().route) },
        viewModel = homeViewModel,
        activeProfile = headerState.activeProfile,
        onSearchClick = { navController.navigate(Route.Search.route) },
        onPersonalListsClick = { navController.navigate(Route.PersonalLists.route) },
        onSettingsClick = { navController.navigate(Route.Settings.route) },
        onSwitchProfileClick = { navController.navigate(Route.ProfileSelection.route) },
        onSwitchServerClick = { navController.navigate(Route.ServerList.route) },
    )
}
Tab.Audio -> {
    LibrariesScreen(
        onItemClick = { contentId -> navController.navigate(Route.ItemDetail(contentId).route) },
        onPlayClick = { contentId -> navController.navigate(Route.Player(contentId).route) },
        onCollectionClick = { collectionId, libraryId ->
            navController.navigate(Route.CollectionDetail(collectionId, libraryId).route)
        },
        viewModel = requireNotNull(librariesViewModel),
        activeProfile = headerState.activeProfile,
        onLibrarySelectorClick = { showLibrarySelector = true },
        onSearchClick = { navController.navigate(Route.Search.route) },
        onPersonalListsClick = { navController.navigate(Route.PersonalLists.route) },
        onSettingsClick = { navController.navigate(Route.Settings.route) },
        onSwitchProfileClick = { navController.navigate(Route.ProfileSelection.route) },
        onSwitchServerClick = { navController.navigate(Route.ServerList.route) },
    )
}
Tab.Reading -> {
    LibrariesScreen(
        onItemClick = { contentId -> navController.navigate(Route.ItemDetail(contentId).route) },
        onPlayClick = { contentId -> navController.navigate(Route.Player(contentId).route) },
        onCollectionClick = { collectionId, libraryId ->
            navController.navigate(Route.CollectionDetail(collectionId, libraryId).route)
        },
        viewModel = requireNotNull(librariesViewModel),
        activeProfile = headerState.activeProfile,
        onLibrarySelectorClick = { showLibrarySelector = true },
        onSearchClick = { navController.navigate(Route.Search.route) },
        onPersonalListsClick = { navController.navigate(Route.PersonalLists.route) },
        onSettingsClick = { navController.navigate(Route.Settings.route) },
        onSwitchProfileClick = { navController.navigate(Route.ProfileSelection.route) },
        onSwitchServerClick = { navController.navigate(Route.ServerList.route) },
    )
}
```

Also change the `librariesViewModel` creation condition:

```kotlin
val librariesViewModel = if (currentTab == Tab.Audio || currentTab == Tab.Reading) {
    koinViewModel<LibrariesViewModel>()
} else {
    null
}
```

- [ ] **Step 6: Run mobile compile and tests**

Run:

```bash
./gradlew :androidApp:testDebugUnitTest --tests com.continuum.app.android.ui.navigation.MobileMediaTabsTest \
  :androidApp:compileDebugKotlinAndroid
```

Expected: PASS / BUILD SUCCESSFUL.

- [ ] **Step 7: Commit**

```bash
git add androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/navigation/AppNavigation.kt \
  androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/MainScreen.kt
git commit -m "feat: wire mobile media navigation"
```

## Task 4: TV Visible Destination Derivation And Shell Wiring

**Files:**
- Create: `androidTvApp/src/androidMain/kotlin/com/continuum/app/tv/ui/shell/TvMediaDestinations.kt`
- Create: `androidTvApp/src/androidUnitTest/kotlin/com/continuum/app/tv/ui/shell/TvMediaDestinationsTest.kt`
- Modify: `androidTvApp/src/androidMain/kotlin/com/continuum/app/tv/ui/navigation/TvRoute.kt`
- Modify: `androidTvApp/src/androidMain/kotlin/com/continuum/app/tv/ui/shell/TvTopMenuBar.kt`
- Modify: `androidTvApp/src/androidMain/kotlin/com/continuum/app/tv/ui/shell/TvMainShell.kt`

- [ ] **Step 1: Write failing TV destination tests**

Create `TvMediaDestinationsTest.kt`:

```kotlin
package com.continuum.app.tv.ui.shell

import com.continuum.app.model.navigation.MediaMode
import com.continuum.app.model.navigation.MediaModeCapabilities
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class TvMediaDestinationsTest {
    @Test
    fun videoOnlyShowsVideoUtilities() {
        val destinations = visibleTvDestinations(MediaModeCapabilities(listOf(MediaMode.Video)))

        assertEquals(
            listOf(TvRootDestination.Search, TvRootDestination.Video, TvRootDestination.Requests),
            destinations,
        )
    }

    @Test
    fun audioOnlyShowsAudioUtilities() {
        val destinations = visibleTvDestinations(MediaModeCapabilities(listOf(MediaMode.Audio)))

        assertEquals(
            listOf(TvRootDestination.Search, TvRootDestination.Audio, TvRootDestination.Requests),
            destinations,
        )
    }

    @Test
    fun videoAndAudioKeepStableOrder() {
        val destinations = visibleTvDestinations(
            MediaModeCapabilities(listOf(MediaMode.Video, MediaMode.Audio)),
        )

        assertEquals(
            listOf(TvRootDestination.Search, TvRootDestination.Video, TvRootDestination.Audio, TvRootDestination.Requests),
            destinations,
        )
    }

    @Test
    fun readingOnlyDoesNotShowReadingOnTv() {
        val destinations = visibleTvDestinations(MediaModeCapabilities(listOf(MediaMode.Reading)))

        assertEquals(listOf(TvRootDestination.Search, TvRootDestination.Requests), destinations)
        assertFalse(destinations.any { it.name == "Reading" })
    }

    @Test
    fun firstTvContentRoutePrefersVideoThenAudioThenSearch() {
        assertEquals(
            TvMainRoute.Video.route,
            firstTvRoute(MediaModeCapabilities(listOf(MediaMode.Video, MediaMode.Audio))),
        )
        assertEquals(
            TvMainRoute.Audio.route,
            firstTvRoute(MediaModeCapabilities(listOf(MediaMode.Audio))),
        )
        assertEquals(
            TvMainRoute.Search.route,
            firstTvRoute(MediaModeCapabilities(listOf(MediaMode.Reading))),
        )
    }
}
```

- [ ] **Step 2: Run TV destination tests and verify they fail**

Run:

```bash
./gradlew :androidTvApp:testDebugUnitTest --tests com.continuum.app.tv.ui.shell.TvMediaDestinationsTest
```

Expected: FAIL because `Video`, `Audio`, `visibleTvDestinations`, and `firstTvRoute` do not exist.

- [ ] **Step 3: Add TV media routes and destination helpers**

In `TvRoute.kt`, add:

```kotlin
data object Video : TvMainRoute("main/video")
data object Audio : TvMainRoute("main/audio")
```

Create `TvMediaDestinations.kt`:

```kotlin
package com.continuum.app.tv.ui.shell

import com.continuum.app.model.navigation.MediaMode
import com.continuum.app.model.navigation.MediaModeCapabilities
import com.continuum.app.tv.ui.navigation.TvMainRoute

fun visibleTvDestinations(capabilities: MediaModeCapabilities): List<TvRootDestination> = buildList {
    add(TvRootDestination.Search)
    capabilities.tvModes().forEach { mode ->
        when (mode) {
            MediaMode.Video -> add(TvRootDestination.Video)
            MediaMode.Audio -> add(TvRootDestination.Audio)
            MediaMode.Reading -> Unit
        }
    }
    add(TvRootDestination.Requests)
}

fun firstTvRoute(capabilities: MediaModeCapabilities): String =
    when (capabilities.firstTvMode()) {
        MediaMode.Video -> TvMainRoute.Video.route
        MediaMode.Audio -> TvMainRoute.Audio.route
        MediaMode.Reading,
        null -> TvMainRoute.Search.route
    }
```

- [ ] **Step 4: Update TV root destination enum and labels**

In `TvTopMenuBar.kt`, change:

```kotlin
enum class TvRootDestination { Home, Search, Libraries, ForYou, Requests }
```

to:

```kotlin
enum class TvRootDestination { Search, Video, Audio, Requests }
```

Add a `destinations` parameter:

```kotlin
destinations: List<TvRootDestination> = TvRootDestination.entries,
```

Replace fixed Home/Libraries/For You button rendering with text buttons driven by `destinations`:

```kotlin
destinations.forEach { destination ->
    when (destination) {
        TvRootDestination.Search -> TvTopMenuIconButton(
            icon = Icons.Outlined.Search,
            contentDescription = "Search",
            width = TvTopMenuLayout.searchButtonWidth,
            isSelected = selectedRoot == TvRootDestination.Search,
            isFocused = focusedButton == TvTopMenuFocus.Search,
            focusRequester = searchFocusRequester,
            onFocusChanged = { hasFocus ->
                focusedButton = if (hasFocus) TvTopMenuFocus.Search else focusedButton.takeUnless { it == TvTopMenuFocus.Search }
            },
            onClick = { onSelectRoot(TvRootDestination.Search) },
            extraModifier = Modifier.focusProperties { left = profileFocusRequester },
        )
        TvRootDestination.Video -> TvTopMenuTextButton(
            label = "Video",
            width = TvTopMenuLayout.homeButtonWidth,
            isSelected = selectedRoot == TvRootDestination.Video,
            isFocused = focusedButton == TvTopMenuFocus.Video,
            focusRequester = homeFocusRequester,
            onFocusChanged = { hasFocus ->
                focusedButton = if (hasFocus) TvTopMenuFocus.Video else focusedButton.takeUnless { it == TvTopMenuFocus.Video }
            },
            onClick = { onSelectRoot(TvRootDestination.Video) },
        )
        TvRootDestination.Audio -> TvTopMenuTextButton(
            label = "Audio",
            width = TvTopMenuLayout.librariesButtonWidth,
            isSelected = selectedRoot == TvRootDestination.Audio,
            isFocused = focusedButton == TvTopMenuFocus.Audio,
            focusRequester = librariesFocusRequester,
            onFocusChanged = { hasFocus ->
                focusedButton = if (hasFocus) TvTopMenuFocus.Audio else focusedButton.takeUnless { it == TvTopMenuFocus.Audio }
            },
            onClick = { onSelectRoot(TvRootDestination.Audio) },
        )
        TvRootDestination.Requests -> TvTopMenuTextButton(
            label = "Requests",
            width = TvTopMenuLayout.requestsButtonWidth,
            isSelected = selectedRoot == TvRootDestination.Requests,
            isFocused = focusedButton == TvTopMenuFocus.Requests,
            focusRequester = requestsFocusRequester,
            onFocusChanged = { hasFocus ->
                focusedButton = if (hasFocus) TvTopMenuFocus.Requests else focusedButton.takeUnless { it == TvTopMenuFocus.Requests }
            },
            onClick = { onSelectRoot(TvRootDestination.Requests) },
        )
    }
}
```

Update `TvTopMenuFocus` to `Search`, `Video`, `Audio`, and `Requests`.

- [ ] **Step 5: Wire TV shell capabilities**

In `TvMainShell.kt`, inject `PersonalDataRepository` and derive capabilities:

```kotlin
val personalDataRepository: com.continuum.app.repository.PersonalDataRepository = koinInject()
val mediaCapabilities by produceState(
    initialValue = com.continuum.app.model.navigation.MediaModeCapabilities(
        listOf(com.continuum.app.model.navigation.MediaMode.Video, com.continuum.app.model.navigation.MediaMode.Audio),
    ),
    personalDataRepository,
) {
    value = when (val result = personalDataRepository.listUserLibraries()) {
        is ApiResult.Success -> result.data.mediaModeCapabilities()
        else -> value
    }
}
val visibleDestinations = remember(mediaCapabilities) { visibleTvDestinations(mediaCapabilities) }
```

Add imports:

```kotlin
import com.continuum.app.model.navigation.MediaMode
import com.continuum.app.model.navigation.MediaModeCapabilities
import com.continuum.app.model.navigation.mediaModeCapabilities
```

Change `NavHost(startDestination = TvMainRoute.Home.route)` to:

```kotlin
NavHost(
    navController = nestedNav,
    startDestination = firstTvRoute(mediaCapabilities),
    modifier = Modifier.fillMaxSize(),
)
```

Add route composables:

```kotlin
composable(TvMainRoute.Video.route) {
    TvHomeScreen(
        onItemClick = onOpenItemDetail,
        onInitialContentFocus = { profileMenuOpen = false },
        focusRequest = contentFocusRequest,
    )
}
composable(TvMainRoute.Audio.route) {
    TvLibrariesScreen(
        onItemClick = onOpenItemDetail,
        onLibraryCollectionClick = onOpenLibraryCollectionDetail,
        onInitialContentFocus = { profileMenuOpen = false },
    )
}
```

Keep legacy `Home`, `Libraries`, and `ForYou` routes mapped to `Video`/`Audio` content until old saved navigation stacks disappear.

Pass `destinations = visibleDestinations` into `TvTopMenuBar`.

- [ ] **Step 6: Redirect hidden TV media routes after capabilities load**

Add a helper in `TvMediaDestinations.kt`:

```kotlin
fun TvRootDestination.isVisibleIn(destinations: List<TvRootDestination>): Boolean =
    this in destinations
```

In `TvMainShell.kt`, add:

```kotlin
LaunchedEffect(currentRoute, visibleDestinations, mediaCapabilities) {
    val selected = mapRouteToRoot(currentRoute)
    if (!selected.isVisibleIn(visibleDestinations)) {
        navigateToRoute(firstTvRoute(mediaCapabilities))
    }
}
```

This handles Reading-only accounts after `/api/v1/user/libraries` returns by moving the TV shell away from `Video`/`Audio` and onto `Search`.

- [ ] **Step 7: Update TV route mapping**

Replace `mapRouteToRoot` and `TvRootDestination.toRoute()` with:

```kotlin
private fun mapRouteToRoot(route: String): TvRootDestination = when (route) {
    TvMainRoute.Search.route -> TvRootDestination.Search
    TvMainRoute.Audio.route,
    TvMainRoute.Libraries.route -> TvRootDestination.Audio
    TvMainRoute.Requests.route,
    TvMainRoute.MyRequests.route -> TvRootDestination.Requests
    else -> TvRootDestination.Video
}

private fun TvRootDestination.toRoute(): String = when (this) {
    TvRootDestination.Search -> TvMainRoute.Search.route
    TvRootDestination.Video -> TvMainRoute.Video.route
    TvRootDestination.Audio -> TvMainRoute.Audio.route
    TvRootDestination.Requests -> TvMainRoute.Requests.route
}
```

- [ ] **Step 8: Run TV tests and compile**

Run:

```bash
./gradlew :androidTvApp:testDebugUnitTest --tests com.continuum.app.tv.ui.shell.TvMediaDestinationsTest \
  :androidTvApp:compileDebugKotlinAndroid
```

Expected: PASS / BUILD SUCCESSFUL.

- [ ] **Step 9: Commit**

```bash
git add androidTvApp/src/androidMain/kotlin/com/continuum/app/tv/ui/navigation/TvRoute.kt \
  androidTvApp/src/androidMain/kotlin/com/continuum/app/tv/ui/shell/TvMediaDestinations.kt \
  androidTvApp/src/androidUnitTest/kotlin/com/continuum/app/tv/ui/shell/TvMediaDestinationsTest.kt \
  androidTvApp/src/androidMain/kotlin/com/continuum/app/tv/ui/shell/TvTopMenuBar.kt \
  androidTvApp/src/androidMain/kotlin/com/continuum/app/tv/ui/shell/TvMainShell.kt
git commit -m "feat: wire tv media navigation"
```

## Task 5: Scope Search And Request Entrypoints To Visible Modes

**Files:**
- Modify: `androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/search/SearchViewModel.kt`
- Modify: `androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/search/SearchScreen.kt`
- Modify: `androidTvApp/src/androidMain/kotlin/com/continuum/app/tv/ui/screens/search/TvSearchViewModel.kt`
- Modify: `androidTvApp/src/androidMain/kotlin/com/continuum/app/tv/ui/screens/search/TvSearchScreen.kt`
- Test: existing shared/mobile/TV tests from prior tasks

- [ ] **Step 1: Add mobile search media mode state**

In `SearchViewModel.kt`, add:

```kotlin
enum class MobileSearchMediaType(val label: String, val wire: String?) {
    All("All", null),
    Video("Video", null),
    Audio("Audio", "audiobook"),
    Reading("Reading", "ebook"),
}
```

Add to `SearchUiState`:

```kotlin
val mediaType: MobileSearchMediaType = MobileSearchMediaType.All,
val availableMediaTypes: List<MobileSearchMediaType> = MobileSearchMediaType.entries,
```

Add methods:

```kotlin
fun setAvailableModes(modes: List<com.continuum.app.model.navigation.MediaMode>) {
    val types = buildList {
        add(MobileSearchMediaType.All)
        if (com.continuum.app.model.navigation.MediaMode.Video in modes) add(MobileSearchMediaType.Video)
        if (com.continuum.app.model.navigation.MediaMode.Audio in modes) add(MobileSearchMediaType.Audio)
        if (com.continuum.app.model.navigation.MediaMode.Reading in modes) add(MobileSearchMediaType.Reading)
    }
    _uiState.update { state ->
        state.copy(
            availableMediaTypes = types,
            mediaType = state.mediaType.takeIf { it in types } ?: MobileSearchMediaType.All,
        )
    }
}

fun onMediaTypeChanged(mediaType: MobileSearchMediaType) {
    _uiState.update { it.copy(mediaType = mediaType) }
    if (_uiState.value.query.isNotBlank()) {
        viewModelScope.launch { performSearch(_uiState.value.query, reset = true) }
    }
}
```

Pass `mediaType = currentState.mediaType.wire` into `catalogRepository.browse`.

- [ ] **Step 2: Add mobile search chips**

In `SearchScreen.kt`, below `SearchBar`, render chips when more than one type is available:

```kotlin
if (state.availableMediaTypes.size > 1) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = 20.dp),
    ) {
        items(state.availableMediaTypes) { type ->
            FilterChip(
                selected = state.mediaType == type,
                onClick = { viewModel.onMediaTypeChanged(type) },
                label = { Text(type.label) },
            )
        }
    }
}
```

- [ ] **Step 3: Keep TV search filters mode-aligned**

In `TvSearchViewModel.kt`, keep `TvSearchMediaType` without Reading:

```kotlin
enum class TvSearchMediaType(val label: String, val wire: String?) {
    All("All", null),
    Video("Video", null),
    Audiobooks("Audiobooks", "audiobook"),
}
```

If `TvSearchScreen.kt` has empty copy that says "Movies and Series", change it to "Video and Audiobooks".

- [ ] **Step 4: Ensure request entrypoints remain video-first**

In mobile and TV request screens, do not add ebook request chips in this task. Confirm any request media type chips still only expose server-supported media types. If a copied capability list is introduced in request UI, filter it so TV never includes Reading:

```kotlin
val requestModes = visibleModes.filterNot { it == MediaMode.Reading }
```

- [ ] **Step 5: Run final verification**

Run:

```bash
git diff --check && ./gradlew :shared:testDebugUnitTest \
  :androidApp:testDebugUnitTest \
  :androidApp:compileDebugKotlinAndroid \
  :androidTvApp:testDebugUnitTest \
  :androidTvApp:compileDebugKotlinAndroid
```

Expected: PASS / BUILD SUCCESSFUL.

- [ ] **Step 6: Commit**

```bash
git add androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/search/SearchViewModel.kt \
  androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/search/SearchScreen.kt \
  androidTvApp/src/androidMain/kotlin/com/continuum/app/tv/ui/screens/search/TvSearchViewModel.kt \
  androidTvApp/src/androidMain/kotlin/com/continuum/app/tv/ui/screens/search/TvSearchScreen.kt
git commit -m "feat: scope search to media modes"
```

## Final Verification

After all tasks are complete, run:

```bash
git diff --check && ./gradlew :shared:testDebugUnitTest \
  :androidApp:testDebugUnitTest \
  :androidApp:compileDebugKotlinAndroid \
  :androidTvApp:testDebugUnitTest \
  :androidTvApp:compileDebugKotlinAndroid \
  && git status --short --branch
```

Expected: all Gradle tasks pass and `git status` shows a clean worktree on `feature/android-parity-and-media-surfaces`.
