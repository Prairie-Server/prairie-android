# TV Requests Hub Implementation Plan

> **For Jim:** REQUIRED SUB-SKILL: Use `superpowers:executing-plans` to execute this plan.

**Goal:** Convert the Android TV Requests tab from a discover-first suggestions page into an elegant search-first Requests Hub for the server's current movie/series request contract.

**Approved design:** `docs/superpowers/specs/2026-06-09-tv-requests-hub-design.md`

**Important scope limit:** This slice does not add audiobook or ebook requests. The server currently models requests around `movie` / `series` and `tmdb_id`, so TV audiobook requests and mobile ebook/audiobook requests wait for a later provider-aware server contract.

## Current Shape

- `shared` already has the request API, repository, models, `RequestsViewModel`, `RequestSearchViewModel`, `RequestDetailViewModel`, and `MyRequestsViewModel`.
- `shared/src/commonTest/kotlin/com/continuum/app/viewmodel/RequestsViewModelTest.kt` already tests `RequestSearchViewModel` success and stale-search cancellation behavior.
- `androidTvApp` currently wires `RequestsViewModel` and `MyRequestsViewModel`, but not `RequestSearchViewModel`.
- `TvRequestsScreen` currently renders discover sections and submits requests directly through `RequestsRepository`.
- `TvSearchScreen` already has the best local pattern for TV search field focus, IME search behavior, filter chips, and empty feedback.

## Desired UX

- Opening Requests lands focus in a large search field.
- Header says Requests and keeps `My Requests` nearby.
- Search supports media chips: `All`, `Movies`, `Series`.
- Blank state shows quiet prompt and optional discover rows below.
- Submitted search renders request cards as the primary content.
- Selecting an available result with `library_content_id` opens the existing TV library detail.
- Selecting a missing and requestable result opens the existing confirmation dialog, then creates a request.
- Already requested and non-requestable results show status/reason and do not duplicate-submit.
- Errors are inline and recoverable.

## Step 1: Wire TV Search ViewModel

**Files:**
- `androidTvApp/src/androidMain/kotlin/com/continuum/app/tv/di/AndroidTvModule.kt`

Add the missing import and Koin binding:

```kotlin
import com.continuum.app.viewmodel.RequestSearchViewModel
```

```kotlin
viewModel { RequestSearchViewModel(get()) }
```

Place the binding next to the other request view models:

```kotlin
viewModel { RequestsViewModel(get()) }
viewModel { RequestSearchViewModel(get()) }
viewModel { MyRequestsViewModel(get()) }
```

**Verify:**

```bash
./gradlew :androidTvApp:compileDebugKotlinAndroid
```

**Commit:**

```bash
git add androidTvApp/src/androidMain/kotlin/com/continuum/app/tv/di/AndroidTvModule.kt
git commit -m "feat: wire tv request search"
```

## Step 2: Add Search-First Header And Filters

**Files:**
- `androidTvApp/src/androidMain/kotlin/com/continuum/app/tv/ui/screens/requests/TvRequestsScreen.kt`

Update `TvRequestsScreen` parameters to use both discover and search view models:

```kotlin
fun TvRequestsScreen(
    onOpenLibraryItem: (contentId: String) -> Unit,
    onOpenMyRequests: () -> Unit,
    onInitialContentFocus: () -> Unit = {},
    viewModel: RequestsViewModel = koinViewModel(),
    searchViewModel: RequestSearchViewModel = koinViewModel(),
    repository: RequestsRepository = koinInject(),
)
```

Collect both states:

```kotlin
val state by viewModel.uiState.collectAsState()
val searchState by searchViewModel.uiState.collectAsState()
```

Replace the current header with a search-stage header based on the TV Search screen pattern:

```kotlin
RequestsHeader(
    query = searchState.query,
    mediaType = searchState.mediaType ?: RequestMediaType.All,
    resultStatus = requestSearchStatusText(
        query = searchState.query,
        total = searchState.totalResults,
        isSearching = searchState.isLoading,
        error = searchState.error,
    ),
    isRefreshing = state.isRefreshing || searchState.isLoading || submittingKey != null,
    searchFieldFocusRequester = searchFieldFocusRequester,
    firstFilterChipFocusRequester = firstFilterChipFocusRequester,
    firstResultFocusRequester = firstResultFocusRequester,
    hasSearchResults = searchState.results.isNotEmpty(),
    onQueryChanged = searchViewModel::onQueryChanged,
    onSearchSubmitted = { searchViewModel.search() },
    onMediaTypeChanged = {
        searchViewModel.onMediaTypeChanged(it)
        if (searchState.query.isNotBlank()) searchViewModel.search()
    },
    onRefresh = ::refreshRequests,
    onOpenMyRequests = onOpenMyRequests,
)
```

Implementation notes:

- Create separate focus requesters:

```kotlin
val searchFieldFocusRequester = remember { FocusRequester() }
val firstFilterChipFocusRequester = remember { FocusRequester() }
val firstResultFocusRequester = remember { FocusRequester() }
```

- Request initial focus on `searchFieldFocusRequester`, then call `onInitialContentFocus()`.
- Use `OutlinedTextField` from `androidx.compose.material3` with `KeyboardOptions(imeAction = ImeAction.Search)`.
- Use `TvFilterChip` from `androidTvApp/src/androidMain/kotlin/com/continuum/app/tv/ui/components/TvChip.kt`.
- Import `RequestMediaType` and `RequestSearchViewModel`.
- Keep header width constrained, similar to `TvSearchScreen`, so text and chips do not sprawl on wide TV layouts.

Add local helpers in `TvRequestsScreen.kt`:

```kotlin
private val requestMediaFilters = listOf(
    RequestMediaType.All to "All",
    RequestMediaType.Movie to "Movies",
    RequestMediaType.Series to "Series",
)

private fun requestSearchStatusText(
    query: String,
    total: Int,
    isSearching: Boolean,
    error: String?,
): String? = when {
    query.isBlank() -> null
    isSearching -> "Searching..."
    error != null && total == 0 -> "Search unavailable"
    total == 0 -> "No results"
    total == 1 -> "1 result"
    else -> "$total results"
}
```

**Verify:**

```bash
./gradlew :androidTvApp:compileDebugKotlinAndroid
```

**Commit:**

```bash
git add androidTvApp/src/androidMain/kotlin/com/continuum/app/tv/ui/screens/requests/TvRequestsScreen.kt
git commit -m "feat: add tv request search controls"
```

## Step 3: Render Search Results Above Discover Rows

**Files:**
- `androidTvApp/src/androidMain/kotlin/com/continuum/app/tv/ui/screens/requests/TvRequestsScreen.kt`

Restructure the content body so search results become primary when a query exists:

```kotlin
val hasSubmittedQuery = searchState.query.isNotBlank()
val hasSearchResults = searchState.results.isNotEmpty()
```

Render order inside `LazyColumn`:

1. Notice row for `actionError`, `searchState.error`, `state.error`, or `actionMessage`.
2. Search empty/prompt item when needed.
3. Search result row when `hasSearchResults`.
4. Discover rows when `!hasSubmittedQuery`, or below search when the approved design wants ambient suggestions.

Add a reusable search result row:

```kotlin
RequestResultRow(
    title = "Search Results",
    results = searchState.results,
    firstItemFocusRequester = firstResultFocusRequester.takeIf { searchState.results.isNotEmpty() },
    submittingKey = submittingKey,
    onItemClick = ::handleRequestItemClick,
)
```

Extract item selection into one local handler so discover and search use identical behavior:

```kotlin
fun handleRequestItemClick(item: RequestMediaResult) {
    if (submittingKey == item.requestKey()) return
    when {
        item.canOpenLibraryDetail() -> onOpenLibraryItem(item.libraryContentId.orEmpty())
        item.canRequest() -> pendingRequest = item
        else -> {
            actionMessage = item.nonActionableMessage()
            actionError = null
        }
    }
}
```

Add the non-actionable helper:

```kotlin
private fun RequestMediaResult.nonActionableMessage(): String = when {
    availability == RequestAvailability.Available -> "This title is already in your library."
    request.status?.isNotBlank() == true -> "${title} is already ${request.status}."
    request.reason.isNotBlank() -> request.reason
    else -> "This title cannot be requested right now."
}
```

After a successful create:

```kotlin
actionMessage = "Request submitted."
viewModel.refresh()
if (searchState.query.isNotBlank()) {
    searchViewModel.search(searchState.page)
}
```

Keep the confirmation dialog copy movie/series-neutral:

```kotlin
text = { Text("This will submit a ${item.mediaType} request for your profile.") }
```

**Verify:**

```bash
./gradlew :androidTvApp:compileDebugKotlinAndroid
```

**Commit:**

```bash
git add androidTvApp/src/androidMain/kotlin/com/continuum/app/tv/ui/screens/requests/TvRequestsScreen.kt
git commit -m "feat: show tv request search results"
```

## Step 4: Polish Focus, Empty, And Disabled States

**Files:**
- `androidTvApp/src/androidMain/kotlin/com/continuum/app/tv/ui/screens/requests/TvRequestsScreen.kt`
- `androidTvApp/src/androidMain/kotlin/com/continuum/app/tv/ui/screens/requests/TvRequestComponents.kt`

Focus behavior:

- Search field gets first focus when the screen opens.
- `DOWN` from search goes to first chip.
- `DOWN` from first chip goes to first result only when results exist.
- `UP` from first result returns to the first chip.
- IME Search runs search, hides keyboard, and moves to first result when available.

Empty states:

- Blank query: "Search movies and series to request them."
- No results: "No matches for \"query\"."
- Search error: show inline notice and leave discover rows available when present.
- Discover disabled/error with no sections: retain `TvErrorScreen` / `RequestsEmptyState`.

Card/action polish:

- Keep `TvRequestCard` dimensions stable.
- Continue using chip text from `cardChipText()`.
- For non-requestable results, selection should show a short notice instead of silently doing nothing.
- Avoid adding ebook/audiobook labels to the request filter because the server contract is not ready.

If `TvRequestComponents.kt` needs helper visibility changes, make the smallest change possible. Prefer keeping `requestKey()` in `TvRequestsScreen.kt` unless another file truly needs it.

**Verify:**

```bash
./gradlew :androidTvApp:compileDebugKotlinAndroid
```

**Commit:**

```bash
git add androidTvApp/src/androidMain/kotlin/com/continuum/app/tv/ui/screens/requests/TvRequestsScreen.kt androidTvApp/src/androidMain/kotlin/com/continuum/app/tv/ui/screens/requests/TvRequestComponents.kt
git commit -m "fix: polish tv request hub states"
```

## Step 5: Add Focused Tests Where Practical

**Files:**
- `shared/src/commonTest/kotlin/com/continuum/app/viewmodel/RequestsViewModelTest.kt`
- optional TV test file if existing TV Compose tests are present

Shared tests are already good for `RequestSearchViewModel`, but add a regression test if implementation changes shared behavior:

```kotlin
@Test
fun `search view model treats all media type as no media type filter`() = runTest(dispatcher) {
    val api = FakeRequestsApi(searchResult = ApiResult.Success(RequestMediaPage()))
    val viewModel = RequestSearchViewModel(RequestsRepository(api))

    viewModel.onQueryChanged("alien")
    viewModel.onMediaTypeChanged(RequestMediaType.All)
    viewModel.search()

    assertEquals(listOf(SearchCall("alien", null, 1)), api.searchCalls)
}
```

Do not add brittle Compose screenshot tests unless the project already has a lightweight TV Compose test pattern nearby.

**Verify:**

```bash
./gradlew :shared:testDebugUnitTest :androidTvApp:testDebugUnitTest :androidTvApp:compileDebugKotlinAndroid
```

**Commit:**

```bash
git add shared/src/commonTest/kotlin/com/continuum/app/viewmodel/RequestsViewModelTest.kt
git commit -m "test: cover request search filters"
```

Skip this commit if no shared code changed and existing tests already cover the behavior.

## Step 6: Final Verification

Run:

```bash
git diff --check
./gradlew :shared:testDebugUnitTest :androidTvApp:testDebugUnitTest :androidTvApp:compileDebugKotlinAndroid
git status --short --branch
```

Expected:

- No whitespace errors.
- Gradle build succeeds.
- Branch remains `feature/android-parity-and-media-surfaces`.
- Worktree is clean except intentional committed changes.

## Manual QA Checklist

- Requests tab opens with focus in search field.
- Typing a blank query and pressing Search does not crash; shows quiet prompt.
- Searching a movie/series title returns cards.
- `All`, `Movies`, `Series` chips update the search filter and stay navigable by remote.
- Available result opens TV detail.
- Missing requestable result opens confirmation and then submits.
- Already-requested result shows status and does not submit duplicate request.
- `My Requests` pill still opens the existing My Requests screen.
- Refresh still updates ambient/discover content.
- Network/server failures show inline copy and leave the page usable.
