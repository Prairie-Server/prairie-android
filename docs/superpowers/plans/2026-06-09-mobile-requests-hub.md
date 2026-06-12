# Mobile Requests Hub Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make Android mobile Requests easy to find from app chrome and polish the existing movie/series Requests, Request Detail, and My Requests screens into a coherent hub.

**Architecture:** Reuse the existing shared request stack and mobile request screens. This is a mobile UI/product-fit slice: add a Requests entry point to the global app chrome, then refine existing Compose screens and components around search-first discovery, clear request state, and queue readability. Do not change the server contract or add ebook/audiobook requests.

**Tech Stack:** Kotlin, Jetpack Compose Material 3, Android navigation compose, Koin view models, existing KMP shared request models/view models.

---

## File Structure

Modify:

- `androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/components/MainAppTopBar.kt`
  - Add an optional `onRequestsClick` callback and a `Requests` profile-menu item.
- `androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/MainScreen.kt`
  - Pass `Route.Requests.route` navigation into `MainAppTopBar`.
- `androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/requests/RequestsScreen.kt`
  - Convert the existing functional search/discover page into a search-first hub.
- `androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/requests/RequestDetailScreen.kt`
  - Polish detail layout and request action state.
- `androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/requests/MyRequestsScreen.kt`
  - Polish queue layout and remove redundant manual refresh button.
- `androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/requests/RequestComponents.kt`
  - Add small reusable request status/action helpers only where needed.

Do not modify:

- Bottom navigation tabs.
- Shared request media types.
- Server request APIs.
- Admin request screens.

## Task 1: Add Mobile Requests Entry Point

**Files:**
- Modify: `androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/components/MainAppTopBar.kt`
- Modify: `androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/MainScreen.kt`

- [ ] **Step 1: Add an optional Requests callback to `MainAppTopBar`**

In `MainAppTopBar.kt`, add the callback after `onPersonalListsClick`:

```kotlin
fun MainAppTopBar(
    activeProfile: Profile?,
    isProfileLoading: Boolean,
    onSearchClick: () -> Unit,
    onPersonalListsClick: () -> Unit,
    onRequestsClick: (() -> Unit)? = null,
    onSettingsClick: () -> Unit,
    onSwitchProfileClick: () -> Unit,
    onSwitchServerClick: () -> Unit,
    leadingContent: @Composable () -> Unit = {
        ContinuumWordmark()
    },
)
```

- [ ] **Step 2: Add the profile-menu item**

In the `DropdownMenu`, place this after `Favorites & Watchlist` and before `Settings`:

```kotlin
if (onRequestsClick != null) {
    DropdownMenuItem(
        text = { Text("Requests") },
        onClick = {
            menuExpanded = false
            onRequestsClick()
        },
    )
}
```

- [ ] **Step 3: Wire global chrome navigation**

In `MainScreen.kt`, update the `MainAppTopBar` call:

```kotlin
MainAppTopBar(
    activeProfile = headerState.activeProfile,
    isProfileLoading = headerState.isLoading,
    onSearchClick = { navController.navigate(Route.Search.route) },
    onPersonalListsClick = { navController.navigate(Route.PersonalLists.route) },
    onRequestsClick = { navController.navigate(Route.Requests.route) },
    onSettingsClick = { navController.navigate(Route.Settings.route) },
    onSwitchProfileClick = {
        navController.navigate(Route.ProfileSelection.route)
    },
    onSwitchServerClick = {
        navController.navigate(Route.ServerList.route)
    },
)
```

Do not add Requests to `Tab` in `BottomNavBar.kt`.

- [ ] **Step 4: Verify compile**

Run:

```bash
./gradlew :androidApp:compileDebugKotlinAndroid
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Commit**

```bash
git add androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/components/MainAppTopBar.kt androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/MainScreen.kt
git commit -m "feat: surface mobile requests entry"
```

## Task 2: Polish Requests Hub Search And Discovery

**Files:**
- Modify: `androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/requests/RequestsScreen.kt`
- Modify: `androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/requests/RequestComponents.kt` only if helper visibility is needed.

- [ ] **Step 1: Add derived screen state**

Inside `RequestsScreen`, after collecting states, add:

```kotlin
val hasSubmittedQuery = searchState.query.isNotBlank()
val hasSearchResults = searchState.results.isNotEmpty()
val showDiscoverRows = !hasSubmittedQuery || (searchState.error != null && state.sections.any { it.results.isNotEmpty() })
```

- [ ] **Step 2: Keep disabled full-screen state only when there is no content**

Update the disabled/error branch so cached/discover content can remain visible:

```kotlin
state.error != null && state.sections.isEmpty() && !state.isEnabled && !hasSubmittedQuery -> {
    RequestErrorState(
        message = state.error ?: "Requests are unavailable.",
        onRetry = viewModel::load,
        modifier = Modifier.padding(padding),
    )
}
```

- [ ] **Step 3: Make search panel search-first and self-contained**

Change `RequestSearchPanel` parameters to include `hasSubmittedQuery`:

```kotlin
private fun RequestSearchPanel(
    query: String,
    selectedType: String?,
    isLoading: Boolean,
    error: String?,
    totalResults: Int,
    results: List<RequestMediaResult>,
    hasSubmittedQuery: Boolean,
    onQueryChange: (String) -> Unit,
    onTypeChange: (String?) -> Unit,
    onSearch: () -> Unit,
    onMediaClick: (RequestMediaResult) -> Unit,
    onLibraryItemClick: (String) -> Unit,
)
```

Pass it from the call site:

```kotlin
hasSubmittedQuery = hasSubmittedQuery,
```

- [ ] **Step 4: Add explicit search status text helper**

Add this private helper at the bottom of `RequestsScreen.kt`:

```kotlin
private fun requestSearchSummary(
    query: String,
    totalResults: Int,
    isLoading: Boolean,
    hasSubmittedQuery: Boolean,
    hasResults: Boolean,
    error: String?,
): String? = when {
    query.isBlank() -> "Search movies and series to request them."
    isLoading -> "Searching..."
    error != null -> null
    hasSubmittedQuery && !hasResults -> "No matches for \"$query\"."
    hasResults && totalResults == 1 -> "1 result"
    hasResults -> "$totalResults results"
    else -> null
}
```

- [ ] **Step 5: Use the helper inside `RequestSearchPanel`**

Before rendering result rows in `RequestSearchPanel`, compute:

```kotlin
val summary = requestSearchSummary(
    query = query,
    totalResults = totalResults,
    isLoading = isLoading,
    hasSubmittedQuery = hasSubmittedQuery,
    hasResults = results.isNotEmpty(),
    error = error,
)
```

Replace the current `if (results.isNotEmpty()) { Text("$totalResults results") ... }` block with:

```kotlin
summary?.let {
    Text(
        text = it,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
if (results.isNotEmpty()) {
    RequestMediaRow(
        items = results,
        onMediaClick = onMediaClick,
        onLibraryItemClick = onLibraryItemClick,
    )
}
```

- [ ] **Step 6: Search again when filters change on nonblank query**

At the call site, replace `onTypeChange = searchViewModel::onMediaTypeChanged` with:

```kotlin
onTypeChange = { type ->
    searchViewModel.onMediaTypeChanged(type)
    if (searchState.query.isNotBlank()) {
        searchViewModel.search()
    }
},
```

- [ ] **Step 7: Show discover rows only when intended**

Replace the section rendering branch with:

```kotlin
if (state.sections.isEmpty() && !hasSubmittedQuery) {
    item {
        EmptyStateView(
            title = "No request suggestions yet",
            subtitle = "Search for movies or series to request.",
            icon = Icons.Outlined.Movie,
        )
    }
} else if (showDiscoverRows) {
    items(state.sections, key = { it.key }) { section ->
        RequestSectionRow(
            title = section.title,
            items = section.results,
            onMediaClick = onMediaClick,
            onLibraryItemClick = onLibraryItemClick,
        )
    }
}
```

- [ ] **Step 8: Verify compile**

Run:

```bash
./gradlew :androidApp:compileDebugKotlinAndroid
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 9: Commit**

```bash
git add androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/requests/RequestsScreen.kt androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/requests/RequestComponents.kt
git commit -m "feat: polish mobile requests hub"
```

## Task 3: Polish Request Detail Actions

**Files:**
- Modify: `androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/requests/RequestDetailScreen.kt`
- Modify: `androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/requests/RequestComponents.kt`

- [ ] **Step 1: Add a visible status row helper**

In `RequestDetailScreen.kt`, add this helper near `RequestActions`:

```kotlin
@Composable
private fun RequestDetailStatus(detail: RequestMediaDetail) {
    val status = detail.request.reason
        .takeIf { it.isNotBlank() }
        ?: detail.request.status
        ?: detail.availability.label()
    Text(
        text = status,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
    )
}
```

- [ ] **Step 2: Make CTA order single-primary**

Replace `RequestActions` with:

```kotlin
@Composable
private fun RequestActions(
    detail: RequestMediaDetail,
    isSubmitting: Boolean,
    onRequest: () -> Unit,
    onLibraryItemClick: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        val libraryContentId = detail.libraryContentId?.takeIf { it.isNotBlank() }
        when {
            libraryContentId != null -> {
                Button(onClick = { onLibraryItemClick(libraryContentId) }) {
                    Text("Open Library Item")
                }
            }
            detail.request.requestable -> {
                Button(
                    onClick = onRequest,
                    enabled = !isSubmitting,
                ) {
                    Text(if (isSubmitting) "Requesting..." else "Request")
                }
            }
            else -> {
                OutlinedButton(
                    onClick = {},
                    enabled = false,
                ) {
                    Text(
                        text = detail.request.status?.replaceFirstChar { it.uppercase() } ?: "Unavailable",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        RequestDetailStatus(detail)
    }
}
```

- [ ] **Step 3: Make recommendation routing robust for empty library IDs**

In the recommendations `RequestMediaCard` click handler, replace:

```kotlin
val libraryId = item.libraryContentId
if (libraryId != null) onLibraryItemClick(libraryId) else onMediaClick(item)
```

with:

```kotlin
val libraryId = item.libraryContentId?.takeIf { it.isNotBlank() }
if (libraryId != null) onLibraryItemClick(libraryId) else onMediaClick(item)
```

- [ ] **Step 4: Verify compile**

Run:

```bash
./gradlew :androidApp:compileDebugKotlinAndroid
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Commit**

```bash
git add androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/requests/RequestDetailScreen.kt androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/requests/RequestComponents.kt
git commit -m "fix: clarify mobile request detail actions"
```

## Task 4: Polish My Requests Queue

**Files:**
- Modify: `androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/requests/MyRequestsScreen.kt`
- Modify: `androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/requests/RequestComponents.kt`

- [ ] **Step 1: Remove redundant manual Refresh button**

Delete this `LazyColumn` item from `MyRequestsScreen.kt`:

```kotlin
item {
    Button(onClick = viewModel::refresh) {
        Text("Refresh")
    }
}
```

Also remove the unused `Button` import if it becomes unused.

- [ ] **Step 2: Improve queue empty copy**

Replace the empty state subtitle with:

```kotlin
subtitle = "Movies and series you request will show up here with their status.",
```

- [ ] **Step 3: Keep cancel button text bounded**

In the cancel trailing button, change the text to:

```kotlin
Text(
    text = if (state.actionInFlightId == request.id) "Canceling" else "Cancel",
    maxLines = 1,
)
```

- [ ] **Step 4: Make request list status chips tolerant of blank strings**

In `RequestComponents.kt`, update the status row in `RequestListItem`:

```kotlin
Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
    request.status.takeIf { it.isNotBlank() }?.let { RequestBadge(text = it.replaceFirstChar { c -> c.uppercase() }) }
    request.outcome.takeIf { it.isNotBlank() }?.let { RequestBadge(text = it.replaceFirstChar { c -> c.uppercase() }) }
}
```

- [ ] **Step 5: Verify compile**

Run:

```bash
./gradlew :androidApp:compileDebugKotlinAndroid
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 6: Commit**

```bash
git add androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/requests/MyRequestsScreen.kt androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/requests/RequestComponents.kt
git commit -m "fix: polish mobile request queue"
```

## Task 5: Focused Verification And Tests

**Files:**
- Modify tests only if a pure helper is moved into a testable location.

- [ ] **Step 1: Inspect whether production helper logic moved into shared/testable code**

Run:

```bash
git diff --name-only HEAD~4..HEAD
```

Expected: request UI files and chrome files only.

- [ ] **Step 2: Do not add brittle Compose screenshot tests**

If only Compose UI composition changed and no shared/view-model logic changed, do not add a new test file. Existing shared request view-model tests remain the behavioral coverage.

- [ ] **Step 3: Run primary verification**

Run:

```bash
./gradlew :shared:testDebugUnitTest :androidApp:compileDebugKotlinAndroid
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 4: Commit only if tests were added**

If no tests were added, do not create a commit for this task. If a pure helper test was added, replace `shared/src/commonTest/kotlin/com/continuum/app/viewmodel/RequestsViewModelTest.kt` below with the actual test file path and commit:

```bash
git add shared/src/commonTest/kotlin/com/continuum/app/viewmodel/RequestsViewModelTest.kt
git commit -m "test: cover mobile request helpers"
```

## Task 6: Final Verification

**Files:** none.

- [ ] **Step 1: Run final verification**

Run:

```bash
git diff --check
./gradlew :shared:testDebugUnitTest :androidApp:compileDebugKotlinAndroid :androidTvApp:testDebugUnitTest :androidTvApp:compileDebugKotlinAndroid
git status --short --branch
```

Expected:

- No whitespace errors.
- Gradle prints `BUILD SUCCESSFUL`.
- Branch is `feature/android-parity-and-media-surfaces`.
- Worktree is clean except intentional committed changes.

- [ ] **Step 2: Manual QA checklist**

Use an emulator/device if available:

- Global profile menu includes `Requests`.
- Tapping `Requests` opens `Route.Requests`.
- Bottom nav still has no permanent Requests tab.
- Requests screen blank query shows helper copy.
- Search shows loading, results count, no-results, and errors coherently.
- Movies/Series/All chips update search and re-run nonblank searches.
- Available results open library detail.
- Missing/already-requested results open request detail.
- Detail CTA shows exactly one primary path.
- My Requests keeps pull-to-refresh and no redundant manual Refresh button.
- Cancel remains available only for pending active requests.
