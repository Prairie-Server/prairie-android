# Person Detail Parity Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Bring Android mobile and Android TV person-detail behavior up to Apple/server parity: selecting a person opens a full profile with metadata, biography, paginated works, and surface-appropriate filters.

**Architecture:** Keep server contracts in the shared `CatalogApi`/`CatalogRepository`, add shared dependency-free person presentation helpers, then update mobile and TV view models/screens independently. Mobile may show reading works; TV must always filter reading media with `visibleOnTv()`.

**Tech Stack:** Kotlin Multiplatform shared module, Jetpack Compose mobile, Compose for TV, Ktor `MockEngine`, kotlinx.coroutines test, source-token tests for UI parity.

## Global Constraints

- Android TV must not expose ebooks, comics, manga, or reading media.
- Use `/api/v1/people/{id}` for profile data.
- Use `/api/v1/catalog?source=person&person_id=<id>&sort=year&order=desc` for works.
- Keep Android's existing server paging parameter name `snapshot`; do not send `snapshot_at`.
- Page size is 60.
- Audiobook works render with square cover art on both mobile and TV, not 2:3 movie-poster geometry.
- Do not add Android admin person editing in this pass.
- Do not change server schema or server routes.
- Write failing tests before production changes.
- Match prairie-server web UI artwork geometry: `ItemCard.tsx` and `SectionItemCard.tsx` use `item.type === "audiobook" ? "aspect-square" : "aspect-[2/3]"`.

---

## File Structure

- Modify `shared/src/commonMain/kotlin/com/continuum/app/network/api/CatalogApi.kt`
  - Add `snapshotAt` to `getPersonItems` and pass it as query key `snapshot`.
- Modify `shared/src/commonMain/kotlin/com/continuum/app/repository/CatalogRepository.kt`
  - Thread `snapshotAt` through `getPersonItems`.
- Create `shared/src/commonMain/kotlin/com/continuum/app/model/catalog/PersonPresentation.kt`
  - Shared person initials, date formatting, age, metadata badge, works-count, and media-filter helpers.
- Create `shared/src/commonTest/kotlin/com/continuum/app/model/catalog/PersonPresentationTest.kt`
  - Tests for formatting helpers and mobile/TV filter definitions.
- Modify `shared/src/commonTest/kotlin/com/continuum/app/network/api/CatalogApiTest.kt`
  - Tests person catalog requests include `snapshot` and never `snapshot_at`.
- Modify `androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/people/PersonDetailViewModel.kt`
  - Add total, hasMore, next offset, snapshot, paging error, and `loadMoreIfNeeded()`.
- Modify `androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/people/PersonDetailScreen.kt`
  - Use shared metadata/count helpers, render external profile details, add works filters, keep audiobook works square, and call load-more from the grid.
- Modify `androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/components/MediaCard.kt`
  - Add a defaulted `artworkAspectRatio` parameter so person works can render audiobook covers square without changing existing poster cards.
- Create `androidApp/src/androidUnitTest/kotlin/com/continuum/app/android/ui/screens/people/PersonDetailViewModelTest.kt`
  - Mobile paging/filter reset/stale response tests.
- Create or modify `androidApp/src/androidUnitTest/kotlin/com/continuum/app/android/ui/screens/people/PersonDetailSourceTest.kt`
  - Source guards for shared helpers, load-more hook, and external detail section.
- Modify `androidTvApp/src/androidMain/kotlin/com/continuum/app/tv/ui/screens/people/TvPersonDetailViewModel.kt`
  - Add total, hasMore, next offset, snapshot, paging error, and TV-safe filtering.
- Modify `androidTvApp/src/androidMain/kotlin/com/continuum/app/tv/ui/screens/people/TvPersonDetailScreen.kt`
  - Use shared metadata/count helpers, make sizing tvOS-aligned, keep audiobook works square, add load-more and retry footer.
- Modify `androidTvApp/src/androidMain/kotlin/com/continuum/app/tv/ui/components/TvMediaCard.kt`
  - Add a defaulted `artworkAspectRatio` parameter preserving existing 2:3 TV poster geometry.
- Modify `androidTvApp/src/androidMain/kotlin/com/continuum/app/tv/ui/components/TvCatalogGrid.kt`
  - Add a defaulted `artworkAspectRatioForItem` callback and pass it into `TvMediaCard`.
- Modify `androidTvApp/src/androidMain/kotlin/com/continuum/app/tv/ui/screens/detail/TvCastCrewSection.kt`
  - Remove stale comment saying person detail is not wired.
- Create `androidTvApp/src/androidUnitTest/kotlin/com/continuum/app/tv/ui/screens/people/TvPersonDetailViewModelTest.kt`
  - TV paging/filter reset and reading-media exclusion tests.
- Create `androidTvApp/src/androidUnitTest/kotlin/com/continuum/app/tv/ui/screens/people/TvPersonDetailSourceTest.kt`
  - Source guards for compact TV sizing, wired cast routing, and no stale comment.

---

### Task 1: Shared Person API Paging And Presentation Helpers

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/continuum/app/network/api/CatalogApi.kt`
- Modify: `shared/src/commonMain/kotlin/com/continuum/app/repository/CatalogRepository.kt`
- Create: `shared/src/commonMain/kotlin/com/continuum/app/model/catalog/PersonPresentation.kt`
- Modify: `shared/src/commonTest/kotlin/com/continuum/app/network/api/CatalogApiTest.kt`
- Create: `shared/src/commonTest/kotlin/com/continuum/app/model/catalog/PersonPresentationTest.kt`

**Interfaces:**
- Produces: `CatalogApi.getPersonItems(personId, mediaType, offset, limit, snapshotAt)`.
- Produces: `CatalogRepository.getPersonItems(personId, mediaType, offset, limit, snapshotAt)`.
- Produces: `PersonWorksFilter`, `personWorksFiltersForMobile()`, `personWorksFiltersForTv()`, `personMetadataBadges(person, todayIso)`, `personInitials(name)`, `personWorksCountLabel(total, loaded, hasMore)`, `isReadingMediaType(type)`.
- Consumed by: mobile and TV person view models/screens.

- [ ] **Step 1: Add failing API test for person snapshot paging**

Add this test to `shared/src/commonTest/kotlin/com/continuum/app/network/api/CatalogApiTest.kt`:

```kotlin
@Test
fun `getPersonItems with snapshotAt sends query param named snapshot not snapshot_at`() = runTest {
    val (api, captured) = api()

    val result = api.getPersonItems(
        personId = 42,
        mediaType = "movie",
        offset = 60,
        limit = 60,
        snapshotAt = "2026-06-19T10:00:00Z",
    )

    assertEquals("/api/v1/catalog", captured.path)
    assertEquals("person", captured.query["source"])
    assertEquals("42", captured.query["person_id"])
    assertEquals("movie", captured.query["type"])
    assertEquals("60", captured.query["offset"])
    assertEquals("60", captured.query["limit"])
    assertEquals("year", captured.query["sort"])
    assertEquals("desc", captured.query["order"])
    assertEquals("2026-06-19T10:00:00Z", captured.query["snapshot"])
    assertFalse("snapshot_at" in captured.query.keys)
    assertIs<ApiResult.Success<*>>(result)
}
```

- [ ] **Step 2: Run test to verify it fails**

Run:

```bash
./gradlew :shared:testDebugUnitTest --tests 'com.continuum.app.network.api.CatalogApiTest.getPersonItems with snapshotAt sends query param named snapshot not snapshot_at'
```

Expected: compile failure because `snapshotAt` is not defined on `getPersonItems`.

- [ ] **Step 3: Add failing presentation tests**

Create `shared/src/commonTest/kotlin/com/continuum/app/model/catalog/PersonPresentationTest.kt`:

```kotlin
package com.continuum.app.model.catalog

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PersonPresentationTest {
    @Test
    fun metadataBadgesFormatDatesAgeAndBirthplace() {
        val person = Person(
            id = 1,
            name = "Andy Weir",
            birthDate = "1972-06-16",
            birthplace = "Davis, California",
        )

        assertEquals(
            listOf("Born Jun 16, 1972", "54 years old", "Davis, California"),
            personMetadataBadges(person, todayIso = "2026-06-19"),
        )
    }

    @Test
    fun metadataBadgesFormatDeathAge() {
        val person = Person(
            id = 2,
            name = "Example Person",
            birthDate = "1950-01-10",
            deathDate = "2020-01-09",
        )

        assertEquals(
            listOf("Born Jan 10, 1950", "Died Jan 9, 2020 (age 69)"),
            personMetadataBadges(person, todayIso = "2026-06-19"),
        )
    }

    @Test
    fun initialsUseFirstTwoNameParts() {
        assertEquals("AW", personInitials("Andy Weir"))
        assertEquals("P", personInitials("Prince"))
        assertEquals("?", personInitials("   "))
    }

    @Test
    fun worksCountLabelDistinguishesLoadedTotalAndMore() {
        assertEquals("12 titles", personWorksCountLabel(total = 12, loaded = 12, hasMore = false))
        assertEquals("60 of 120 titles", personWorksCountLabel(total = 120, loaded = 60, hasMore = true))
        assertEquals("60+ titles", personWorksCountLabel(total = 0, loaded = 60, hasMore = true))
        assertEquals(null, personWorksCountLabel(total = 0, loaded = 0, hasMore = false))
    }

    @Test
    fun tvFiltersExcludeReading() {
        assertTrue(personWorksFiltersForMobile().any { it.key == "reading" })
        assertFalse(personWorksFiltersForTv().any { it.key == "reading" })
        assertTrue(isReadingMediaType("ebook"))
        assertTrue(isReadingMediaType("comic"))
        assertTrue(isReadingMediaType("manga"))
        assertFalse(isReadingMediaType("audiobook"))
    }
}
```

- [ ] **Step 4: Run presentation test to verify it fails**

Run:

```bash
./gradlew :shared:testDebugUnitTest --tests 'com.continuum.app.model.catalog.PersonPresentationTest'
```

Expected: compile failure because helper functions do not exist.

- [ ] **Step 5: Implement shared API threading**

In `CatalogApi.getPersonItems`, change the signature and add the parameter:

```kotlin
suspend fun getPersonItems(
    personId: Int,
    mediaType: String? = null,
    offset: Int? = null,
    limit: Int? = null,
    snapshotAt: String? = null,
): ApiResult<CatalogResponse> = safeApiCall {
    client.get("/api/v1/catalog") {
        parameter("source", "person")
        parameter("person_id", personId.toString())
        parameter("sort", "year")
        parameter("order", "desc")
        mediaType?.let { parameter("type", it) }
        offset?.let { parameter("offset", it) }
        limit?.let { parameter("limit", it) }
        snapshotAt?.let { parameter("snapshot", it) }
    }
}
```

In `CatalogRepository.getPersonItems`, change the signature and call:

```kotlin
suspend fun getPersonItems(
    personId: Int,
    mediaType: String? = null,
    offset: Int? = null,
    limit: Int? = null,
    snapshotAt: String? = null,
): ApiResult<CatalogResponse> =
    catalogApi.getPersonItems(personId, mediaType, offset, limit, snapshotAt)
```

- [ ] **Step 6: Implement shared presentation helpers**

Create `shared/src/commonMain/kotlin/com/continuum/app/model/catalog/PersonPresentation.kt`:

```kotlin
package com.continuum.app.model.catalog

import com.continuum.app.util.IsoDate

data class PersonWorksFilter(
    val key: String,
    val title: String,
    val serverMediaType: String?,
    val clientTypes: Set<String> = emptySet(),
)

private val monthNames = arrayOf(
    "Jan", "Feb", "Mar", "Apr", "May", "Jun",
    "Jul", "Aug", "Sep", "Oct", "Nov", "Dec",
)

private val readingTypes = setOf("ebook", "ebooks", "book", "books", "comic", "comics", "manga", "reading")

fun personWorksFiltersForMobile(): List<PersonWorksFilter> = listOf(
    PersonWorksFilter(key = "all", title = "All", serverMediaType = null),
    PersonWorksFilter(key = "movie", title = "Movies", serverMediaType = "movie"),
    PersonWorksFilter(key = "series", title = "TV", serverMediaType = "series"),
    PersonWorksFilter(key = "audiobook", title = "Audiobooks", serverMediaType = "audiobook"),
    PersonWorksFilter(key = "music", title = "Music", serverMediaType = "music"),
    PersonWorksFilter(key = "reading", title = "Reading", serverMediaType = null, clientTypes = readingTypes),
)

fun personWorksFiltersForTv(): List<PersonWorksFilter> = listOf(
    PersonWorksFilter(key = "all", title = "All", serverMediaType = null),
    PersonWorksFilter(key = "movie", title = "Movies", serverMediaType = "movie"),
    PersonWorksFilter(key = "series", title = "TV", serverMediaType = "series"),
    PersonWorksFilter(key = "audiobook", title = "Audiobooks", serverMediaType = "audiobook"),
    PersonWorksFilter(key = "music", title = "Music", serverMediaType = "music"),
)

fun isReadingMediaType(type: String?): Boolean =
    type?.trim()?.lowercase() in readingTypes

fun personInitials(name: String): String {
    val initials = name.trim()
        .split(Regex("\\s+"))
        .filter { it.isNotBlank() }
        .take(2)
        .mapNotNull { it.firstOrNull()?.uppercaseChar() }
        .joinToString("")
    return initials.ifBlank { "?" }
}

fun personMetadataBadges(person: Person, todayIso: String? = null): List<String> {
    val badges = mutableListOf<String>()
    formattedPersonDate(person.birthDate)?.let { badges += "Born $it" }
    if (person.deathDate.isNullOrBlank()) {
        personAge(person.birthDate, null, todayIso)?.let { badges += "$it years old" }
    } else {
        val deathDate = formattedPersonDate(person.deathDate)
        val age = personAge(person.birthDate, person.deathDate, todayIso)
        if (deathDate != null && age != null) badges += "Died $deathDate (age $age)"
        else if (deathDate != null) badges += "Died $deathDate"
    }
    person.birthplace?.trim()?.takeIf { it.isNotBlank() }?.let { badges += it }
    return badges
}

fun formattedPersonDate(raw: String?): String? {
    val value = raw?.trim()?.takeIf { it.isNotBlank() } ?: return null
    val parts = value.split("-")
    if (parts.size != 3) return value
    val year = parts[0].toIntOrNull() ?: return value
    val month = parts[1].toIntOrNull() ?: return value
    val day = parts[2].toIntOrNull() ?: return value
    if (month !in 1..12 || day !in 1..31) return value
    return "${monthNames[month - 1]} $day, $year"
}

fun personAge(birthValue: String?, deathValue: String?, todayIso: String?): Int? {
    val birth = parseIsoDateParts(birthValue) ?: return null
    val end = parseIsoDateParts(deathValue) ?: parseIsoDateParts(todayIso) ?: return null
    var age = end.year - birth.year
    if (end.month < birth.month || (end.month == birth.month && end.day < birth.day)) age -= 1
    return age.takeIf { it >= 0 }
}

fun personWorksCountLabel(total: Int, loaded: Int, hasMore: Boolean): String? = when {
    total > 0 && hasMore -> "$loaded of $total titles"
    total > 0 -> if (total == 1) "1 title" else "$total titles"
    loaded > 0 && hasMore -> "$loaded+ titles"
    loaded > 0 -> if (loaded == 1) "1 title" else "$loaded titles"
    else -> null
}

private data class IsoDateParts(val year: Int, val month: Int, val day: Int)

private fun parseIsoDateParts(value: String?): IsoDateParts? {
    val raw = value?.trim()?.takeIf { it.length >= 10 }?.substring(0, 10) ?: return null
    return runCatching {
        IsoDate.toEpochDay(raw)
        val parts = raw.split("-")
        IsoDateParts(parts[0].toInt(), parts[1].toInt(), parts[2].toInt())
    }.getOrNull()
}
```

- [ ] **Step 7: Run shared tests to verify green**

Run:

```bash
./gradlew :shared:testDebugUnitTest --tests 'com.continuum.app.network.api.CatalogApiTest' --tests 'com.continuum.app.model.catalog.PersonPresentationTest'
```

Expected: PASS.

- [ ] **Step 8: Commit**

```bash
git add shared/src/commonMain/kotlin/com/continuum/app/network/api/CatalogApi.kt \
  shared/src/commonMain/kotlin/com/continuum/app/repository/CatalogRepository.kt \
  shared/src/commonMain/kotlin/com/continuum/app/model/catalog/PersonPresentation.kt \
  shared/src/commonTest/kotlin/com/continuum/app/network/api/CatalogApiTest.kt \
  shared/src/commonTest/kotlin/com/continuum/app/model/catalog/PersonPresentationTest.kt
git commit -m "feat: add person works paging helpers"
```

---

### Task 2: Mobile Person ViewModel Pagination

**Files:**
- Modify: `androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/people/PersonDetailViewModel.kt`
- Create: `androidApp/src/androidUnitTest/kotlin/com/continuum/app/android/ui/screens/people/PersonDetailViewModelTest.kt`

**Interfaces:**
- Consumes: `CatalogRepository.getPersonItems(... snapshotAt)`, `PersonWorksFilter`, `personWorksFiltersForMobile()`, `isReadingMediaType`.
- Produces: mobile `PersonDetailUiState.totalItems`, `hasMore`, `pagingError`, `availableFilters`, `selectedFilter`, `loadMoreIfNeeded()`.

- [ ] **Step 1: Write failing mobile pagination tests**

Create `androidApp/src/androidUnitTest/kotlin/com/continuum/app/android/ui/screens/people/PersonDetailViewModelTest.kt` with a `MockEngine` repository and these tests:

```kotlin
package com.continuum.app.android.ui.screens.people

import androidx.lifecycle.SavedStateHandle
import com.continuum.app.network.ContinuumJson
import com.continuum.app.network.api.CatalogApi
import com.continuum.app.repository.CatalogRepository
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class PersonDetailViewModelTest {
    @Test
    fun loadMoreAppendsSecondPageUsingSnapshot() = runTest {
        val paths = mutableListOf<String>()
        val queries = mutableListOf<Map<String, String?>>()
        val repository = repositoryFor(paths, queries)
        val viewModel = PersonDetailViewModel(repository, SavedStateHandle(mapOf("personId" to 7)))
        advanceUntilIdle()

        viewModel.loadMoreIfNeeded()
        advanceUntilIdle()

        assertEquals(listOf("movie-1", "movie-2"), viewModel.uiState.value.items.map { it.contentId })
        assertEquals(120, viewModel.uiState.value.totalItems)
        assertTrue(viewModel.uiState.value.hasMore)
        assertEquals("snap-1", queries.last()["snapshot"])
        assertEquals("60", queries.last()["offset"])
    }

    @Test
    fun filterChangeResetsItemsSnapshotAndOffset() = runTest {
        val paths = mutableListOf<String>()
        val queries = mutableListOf<Map<String, String?>>()
        val repository = repositoryFor(paths, queries)
        val viewModel = PersonDetailViewModel(repository, SavedStateHandle(mapOf("personId" to 7)))
        advanceUntilIdle()

        viewModel.applyFilter(PersonMediaFilter.Movies)
        advanceUntilIdle()

        assertEquals(listOf("movie-filtered"), viewModel.uiState.value.items.map { it.contentId })
        assertEquals("movie", queries.last()["type"])
        assertEquals("0", queries.last()["offset"])
        assertFalse("snapshot" in queries.last().keys)
    }

    private fun repositoryFor(
        paths: MutableList<String>,
        queries: MutableList<Map<String, String?>>,
    ): CatalogRepository {
        var catalogCalls = 0
        val client = HttpClient(
            MockEngine { request ->
                paths += request.url.encodedPath
                queries += request.url.parameters.names().associateWith { request.url.parameters[it] }
                when (request.url.encodedPath) {
                    "/api/v1/people/7" -> respond(
                        """{"id":7,"name":"Person","birth_date":"1972-06-16"}""",
                        HttpStatusCode.OK,
                        headersOf(HttpHeaders.ContentType, "application/json"),
                    )
                    "/api/v1/catalog" -> {
                        catalogCalls += 1
                        val body = when {
                            request.url.parameters["type"] == "movie" -> """{"total":1,"has_more":false,"snapshot":"snap-m","items":[{"content_id":"movie-filtered","title":"Movie Filtered","type":"movie"}]}"""
                            catalogCalls == 1 -> """{"total":120,"has_more":true,"snapshot":"snap-1","items":[{"content_id":"movie-1","title":"Movie 1","type":"movie"}]}"""
                            else -> """{"total":120,"has_more":true,"snapshot":"snap-1","items":[{"content_id":"movie-2","title":"Movie 2","type":"movie"}]}"""
                        }
                        respond(body, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
                    }
                    else -> error("Unexpected path ${request.url.encodedPath}")
                }
            },
        ) {
            install(ContentNegotiation) { json(ContinuumJson) }
        }
        return CatalogRepository(CatalogApi(client))
    }
}
```

- [ ] **Step 2: Run mobile tests to verify failure**

Run:

```bash
./gradlew :androidApp:testDebugUnitTest --tests 'com.continuum.app.android.ui.screens.people.PersonDetailViewModelTest'
```

Expected: compile failure because `loadMoreIfNeeded`, `totalItems`, and `hasMore` are missing.

- [ ] **Step 3: Implement mobile state and paging**

In `PersonDetailViewModel.kt`:

```kotlin
private const val PersonWorksPageSize = 60

data class PersonDetailUiState(
    val isLoading: Boolean = true,
    val person: Person? = null,
    val items: List<BrowseItem> = emptyList(),
    val isLoadingItems: Boolean = false,
    val selectedFilter: PersonMediaFilter = PersonMediaFilter.All,
    val availableFilters: List<PersonMediaFilter> = PersonMediaFilter.entries,
    val totalItems: Int = 0,
    val hasMore: Boolean = false,
    val pagingError: String? = null,
    val error: String? = null,
)
```

Update enum:

```kotlin
enum class PersonMediaFilter(val title: String, val mediaType: String?, val clientPredicate: (BrowseItem) -> Boolean = { true }) {
    All("All", null),
    Movies("Movies", "movie"),
    Series("TV", "series"),
    Audiobooks("Audiobooks", "audiobook"),
    Music("Music", "music"),
    Reading("Reading", null, { com.continuum.app.model.catalog.isReadingMediaType(it.type) }),
}
```

Add fields and methods:

```kotlin
private var itemsGeneration = 0
private var nextOffset = 0
private var snapshotAt: String? = null

fun loadMoreIfNeeded() {
    val state = _uiState.value
    if (!state.hasMore || state.isLoadingItems) return
    loadItems(state.selectedFilter, reset = false)
}

private fun resetPaging() {
    nextOffset = 0
    snapshotAt = null
}
```

Replace `loadItems(filter)` with:

```kotlin
private fun loadItems(filter: PersonMediaFilter, reset: Boolean = true) {
    val gen = if (reset) ++itemsGeneration else itemsGeneration
    if (reset) resetPaging()
    viewModelScope.launch {
        _uiState.update {
            it.copy(
                isLoadingItems = true,
                pagingError = null,
                items = if (reset) emptyList() else it.items,
                totalItems = if (reset) 0 else it.totalItems,
                hasMore = if (reset) false else it.hasMore,
            )
        }
        val result = catalogRepository.getPersonItems(
            personId = personId,
            mediaType = filter.mediaType,
            offset = nextOffset,
            limit = PersonWorksPageSize,
            snapshotAt = snapshotAt,
        )
        if (gen != itemsGeneration) return@launch
        when (result) {
            is ApiResult.Success -> {
                if (snapshotAt == null) snapshotAt = result.data.snapshot
                nextOffset += result.data.items.size
                val visibleItems = result.data.items.filter(filter.clientPredicate)
                _uiState.update {
                    it.copy(
                        isLoadingItems = false,
                        items = if (reset) visibleItems else it.items + visibleItems,
                        totalItems = result.data.total,
                        hasMore = result.data.hasMore,
                        pagingError = null,
                    )
                }
            }
            is ApiResult.Error -> _uiState.update {
                it.copy(isLoadingItems = false, pagingError = result.message.ifBlank { "Failed to load works" })
            }
            is ApiResult.NetworkError -> _uiState.update {
                it.copy(isLoadingItems = false, pagingError = "Network error. Check your connection.")
            }
        }
    }
}
```

Update `applyFilter` to clear pagination:

```kotlin
fun applyFilter(filter: PersonMediaFilter) {
    if (filter == _uiState.value.selectedFilter) return
    _uiState.update { it.copy(selectedFilter = filter, items = emptyList()) }
    loadItems(filter, reset = true)
}
```

- [ ] **Step 4: Run mobile ViewModel tests to verify green**

Run:

```bash
./gradlew :androidApp:testDebugUnitTest --tests 'com.continuum.app.android.ui.screens.people.PersonDetailViewModelTest'
```

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/people/PersonDetailViewModel.kt \
  androidApp/src/androidUnitTest/kotlin/com/continuum/app/android/ui/screens/people/PersonDetailViewModelTest.kt
git commit -m "feat: page mobile person works"
```

---

### Task 3: Mobile Person Detail UI

**Files:**
- Modify: `androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/components/MediaCard.kt`
- Modify: `androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/people/PersonDetailScreen.kt`
- Create: `androidApp/src/androidUnitTest/kotlin/com/continuum/app/android/ui/screens/people/PersonDetailSourceTest.kt`

**Interfaces:**
- Consumes: mobile `PersonDetailUiState.totalItems`, `hasMore`, `pagingError`, `loadMoreIfNeeded()`.
- Consumes: shared `personMetadataBadges`, `personInitials`, `personWorksCountLabel`.

- [ ] **Step 1: Write failing mobile source test**

Create `androidApp/src/androidUnitTest/kotlin/com/continuum/app/android/ui/screens/people/PersonDetailSourceTest.kt`:

```kotlin
package com.continuum.app.android.ui.screens.people

import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PersonDetailSourceTest {
    private val source = File(
        "src/androidMain/kotlin/com/continuum/app/android/ui/screens/people/PersonDetailScreen.kt",
    ).readText()

    private val mediaCardSource = File(
        "src/androidMain/kotlin/com/continuum/app/android/ui/components/MediaCard.kt",
    ).readText()

    @Test
    fun usesSharedPersonPresentationAndPaginationHooks() {
        assertTrue(source.contains("personMetadataBadges("))
        assertTrue(source.contains("personInitials("))
        assertTrue(source.contains("personWorksCountLabel("))
        assertTrue(source.contains("onLoadMore = viewModel::loadMoreIfNeeded"))
        assertTrue(source.contains("ExternalProfileSection("))
        assertTrue(source.contains("personWorkCardAspectRatio(item)"))
        assertTrue(mediaCardSource.contains("artworkAspectRatio: Float = 2f / 3.3f"))
        assertTrue(mediaCardSource.contains(".aspectRatio(artworkAspectRatio)"))
        assertTrue(source.contains("item.type == \"audiobook\""))
        assertFalse(source.contains("person.birthDate?.takeIf"))
        assertFalse(source.contains("personInitials(person.name)"))
    }
}
```

- [ ] **Step 2: Run source test to verify it fails**

Run:

```bash
./gradlew :androidApp:testDebugUnitTest --tests 'com.continuum.app.android.ui.screens.people.PersonDetailSourceTest'
```

Expected: FAIL because UI still has inline metadata/initials and no load-more hook.

- [ ] **Step 3: Update `PersonDetailScreen` signature path**

Thread new state through:

```kotlin
PersonDetailContent(
    person = state.person!!,
    items = state.items,
    isLoadingItems = state.isLoadingItems,
    selectedFilter = state.selectedFilter,
    totalItems = state.totalItems,
    hasMore = state.hasMore,
    pagingError = state.pagingError,
    onFilterSelected = { viewModel.applyFilter(it) },
    onLoadMore = viewModel::loadMoreIfNeeded,
    onItemClick = onItemClick,
)
```

Update `PersonDetailContent` parameters and add this footer trigger after the `items(...)` block:

```kotlin
if (hasMore || isLoadingItems || pagingError != null) {
    item(span = { GridItemSpan(maxLineSpan) }) {
        LaunchedEffect(items.size, hasMore) {
            if (hasMore && !isLoadingItems) onLoadMore()
        }
        PagingFooter(
            isLoading = isLoadingItems,
            error = pagingError,
            onRetry = onLoadMore,
        )
    }
}
```

- [ ] **Step 4: Replace inline metadata with shared helpers**

In `PersonHeader`, replace the inline badge build and initials call:

```kotlin
val badges = personMetadataBadges(person, todayIso = java.time.LocalDate.now().toString())
```

In `PersonPortrait`, replace fallback text:

```kotlin
text = personInitials(person.name)
```

- [ ] **Step 5: Add external details section**

Under bio in `PersonHeader`, render:

```kotlin
ExternalProfileSection(person = person)
```

Add:

```kotlin
@Composable
private fun ExternalProfileSection(person: Person) {
    val rows = buildList {
        person.homepage?.trim()?.takeIf { it.isNotBlank() }?.let { add("Homepage" to it) }
        person.tmdbId?.trim()?.takeIf { it.isNotBlank() }?.let { add("TMDB" to it) }
        person.imdbId?.trim()?.takeIf { it.isNotBlank() }?.let { add("IMDb" to it) }
        person.tvdbId?.trim()?.takeIf { it.isNotBlank() }?.let { add("TVDB" to it) }
        person.plexGuid?.trim()?.takeIf { it.isNotBlank() }?.let { add("Plex" to it) }
    }
    if (rows.isEmpty()) return
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        rows.forEach { (label, value) ->
            Surface(shape = PillShape, color = ContinuumSurfaceElevated.copy(alpha = 0.55f)) {
                Text(
                    text = "$label: $value",
                    fontSize = 11.sp,
                    color = ContinuumSecondaryText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                )
            }
        }
    }
}
```

- [ ] **Step 6: Use count label in `FilmographyHeader`**

Rename the on-screen section to "Works" and use:

```kotlin
personWorksCountLabel(total = totalItems, loaded = totalLoaded, hasMore = hasMore)?.let { label ->
    Text(
        text = label,
        fontSize = 12.sp,
        fontWeight = FontWeight.Normal,
        color = ContinuumSecondaryText,
    )
}
```

- [ ] **Step 7: Add paging footer**

Add:

```kotlin
@Composable
private fun PagingFooter(
    isLoading: Boolean,
    error: String?,
    onRetry: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(72.dp),
        contentAlignment = Alignment.Center,
    ) {
        when {
            isLoading -> CircularProgressIndicator(color = ContinuumOnSurface)
            error != null -> Text(
                text = error,
                color = ContinuumSecondaryText,
                modifier = Modifier.clickable(onClick = onRetry),
            )
        }
    }
}
```

- [ ] **Step 8: Add mobile square audiobook artwork**

In `MediaCard.kt`, add a defaulted parameter:

```kotlin
fun MediaCard(
    title: String,
    posterUrl: String?,
    posterThumbhash: String?,
    year: Int? = null,
    type: String? = null,
    userState: MediaItemUserState? = null,
    progress: Float? = null,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    width: Dp = 120.dp,
    artworkAspectRatio: Float = 2f / 3.3f,
    overlay: OverlayData? = null,
    actions: MediaCardActions = MediaCardActions(),
)
```

Replace the fixed poster ratio:

```kotlin
.aspectRatio(artworkAspectRatio)
```

In `PersonDetailScreen.kt`, add:

```kotlin
private fun personWorkCardAspectRatio(item: BrowseItem): Float =
    if (item.type == "audiobook") {
        1f
    } else {
        2f / 3.3f
    }
```

Pass it into person works cards:

```kotlin
artworkAspectRatio = personWorkCardAspectRatio(item),
```

- [ ] **Step 9: Run mobile tests**

Run:

```bash
./gradlew :androidApp:testDebugUnitTest --tests 'com.continuum.app.android.ui.screens.people.PersonDetailSourceTest' --tests 'com.continuum.app.android.ui.screens.people.PersonDetailViewModelTest'
```

Expected: PASS.

- [ ] **Step 10: Commit**

```bash
git add androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/components/MediaCard.kt \
  androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/people/PersonDetailScreen.kt \
  androidApp/src/androidUnitTest/kotlin/com/continuum/app/android/ui/screens/people/PersonDetailSourceTest.kt
git commit -m "feat: polish mobile person detail"
```

---

### Task 4: TV Person ViewModel Pagination And Reading Exclusion

**Files:**
- Modify: `androidTvApp/src/androidMain/kotlin/com/continuum/app/tv/ui/screens/people/TvPersonDetailViewModel.kt`
- Create: `androidTvApp/src/androidUnitTest/kotlin/com/continuum/app/tv/ui/screens/people/TvPersonDetailViewModelTest.kt`

**Interfaces:**
- Consumes: `CatalogRepository.getPersonItems(... snapshotAt)`, `personWorksFiltersForTv()`, `visibleOnTv()`.
- Produces: TV `TvPersonDetailUiState.totalItems`, `hasMore`, `pagingError`, `availableFilters`, and `loadMoreIfNeeded()`.

- [ ] **Step 1: Write failing TV ViewModel tests**

Create `androidTvApp/src/androidUnitTest/kotlin/com/continuum/app/tv/ui/screens/people/TvPersonDetailViewModelTest.kt`:

```kotlin
package com.continuum.app.tv.ui.screens.people

import com.continuum.app.network.ContinuumJson
import com.continuum.app.network.api.CatalogApi
import com.continuum.app.repository.CatalogRepository
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

@OptIn(ExperimentalCoroutinesApi::class)
class TvPersonDetailViewModelTest {
    @Test
    fun tvFiltersReadingMediaOutOfAllWorks() = runTest {
        val queries = mutableListOf<Map<String, String?>>()
        val viewModel = TvPersonDetailViewModel(repositoryFor(queries), personId = 9)
        advanceUntilIdle()

        assertEquals(listOf("movie-1", "audio-1"), viewModel.uiState.value.items.map { it.contentId })
        assertFalse(viewModel.uiState.value.items.any { it.type == "ebook" || it.type == "comic" })
    }

    @Test
    fun tvLoadMoreUsesSnapshotAndAppendsVisibleItems() = runTest {
        val queries = mutableListOf<Map<String, String?>>()
        val viewModel = TvPersonDetailViewModel(repositoryFor(queries), personId = 9)
        advanceUntilIdle()

        viewModel.loadMoreIfNeeded()
        advanceUntilIdle()

        assertEquals(listOf("movie-1", "audio-1", "series-2"), viewModel.uiState.value.items.map { it.contentId })
        assertEquals("snap-tv", queries.last()["snapshot"])
        assertEquals("60", queries.last()["offset"])
    }

    private fun repositoryFor(queries: MutableList<Map<String, String?>>): CatalogRepository {
        var catalogCalls = 0
        val client = HttpClient(
            MockEngine { request ->
                queries += request.url.parameters.names().associateWith { request.url.parameters[it] }
                when (request.url.encodedPath) {
                    "/api/v1/people/9" -> respond(
                        """{"id":9,"name":"TV Person"}""",
                        HttpStatusCode.OK,
                        headersOf(HttpHeaders.ContentType, "application/json"),
                    )
                    "/api/v1/catalog" -> {
                        catalogCalls += 1
                        val body = if (catalogCalls == 1) {
                            """{"total":4,"has_more":true,"snapshot":"snap-tv","items":[{"content_id":"movie-1","title":"Movie","type":"movie"},{"content_id":"ebook-1","title":"Book","type":"ebook"},{"content_id":"audio-1","title":"Audio","type":"audiobook"}]}"""
                        } else {
                            """{"total":4,"has_more":false,"snapshot":"snap-tv","items":[{"content_id":"series-2","title":"Series","type":"series"},{"content_id":"comic-2","title":"Comic","type":"comic"}]}"""
                        }
                        respond(body, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
                    }
                    else -> error("Unexpected path ${request.url.encodedPath}")
                }
            },
        ) {
            install(ContentNegotiation) { json(ContinuumJson) }
        }
        return CatalogRepository(CatalogApi(client))
    }
}
```

- [ ] **Step 2: Run TV ViewModel tests to verify failure**

Run:

```bash
./gradlew :androidTvApp:testDebugUnitTest --tests 'com.continuum.app.tv.ui.screens.people.TvPersonDetailViewModelTest'
```

Expected: compile failure because `loadMoreIfNeeded`, `totalItems`, and `hasMore` are missing.

- [ ] **Step 3: Implement TV paging state**

In `TvPersonDetailViewModel.kt`, mirror Task 2 with TV naming:

```kotlin
private const val TvPersonWorksPageSize = 60

data class TvPersonDetailUiState(
    val isLoading: Boolean = true,
    val person: Person? = null,
    val items: List<BrowseItem> = emptyList(),
    val isLoadingItems: Boolean = false,
    val selectedFilter: TvPersonMediaFilter = TvPersonMediaFilter.All,
    val totalItems: Int = 0,
    val hasMore: Boolean = false,
    val pagingError: String? = null,
    val error: String? = null,
)
```

Update enum:

```kotlin
enum class TvPersonMediaFilter(val title: String, val mediaType: String?) {
    All("All", null),
    Movies("Movies", "movie"),
    Series("TV", "series"),
    Audiobooks("Audiobooks", "audiobook"),
    Music("Music", "music"),
}
```

Add paging fields and `loadMoreIfNeeded()` exactly as in mobile, but always apply:

```kotlin
val visibleItems = result.data.items.visibleOnTv()
```

before assigning/appending to state.

- [ ] **Step 4: Run TV ViewModel tests to verify green**

Run:

```bash
./gradlew :androidTvApp:testDebugUnitTest --tests 'com.continuum.app.tv.ui.screens.people.TvPersonDetailViewModelTest'
```

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add androidTvApp/src/androidMain/kotlin/com/continuum/app/tv/ui/screens/people/TvPersonDetailViewModel.kt \
  androidTvApp/src/androidUnitTest/kotlin/com/continuum/app/tv/ui/screens/people/TvPersonDetailViewModelTest.kt
git commit -m "feat: page tv person works"
```

---

### Task 5: TV Person Detail UI And Cast Comment Cleanup

**Files:**
- Modify: `androidTvApp/src/androidMain/kotlin/com/continuum/app/tv/ui/screens/people/TvPersonDetailScreen.kt`
- Modify: `androidTvApp/src/androidMain/kotlin/com/continuum/app/tv/ui/screens/detail/TvCastCrewSection.kt`
- Create: `androidTvApp/src/androidUnitTest/kotlin/com/continuum/app/tv/ui/screens/people/TvPersonDetailSourceTest.kt`

**Interfaces:**
- Consumes: TV `TvPersonDetailUiState.totalItems`, `hasMore`, `pagingError`, `loadMoreIfNeeded()`.
- Consumes: shared `personMetadataBadges`, `personWorksCountLabel`, `personInitials`.

- [ ] **Step 1: Write failing TV source test**

Create `androidTvApp/src/androidUnitTest/kotlin/com/continuum/app/tv/ui/screens/people/TvPersonDetailSourceTest.kt`:

```kotlin
package com.continuum.app.tv.ui.screens.people

import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TvPersonDetailSourceTest {
    private val personSource = File(
        "src/androidMain/kotlin/com/continuum/app/tv/ui/screens/people/TvPersonDetailScreen.kt",
    ).readText()

    private val castSource = File(
        "src/androidMain/kotlin/com/continuum/app/tv/ui/screens/detail/TvCastCrewSection.kt",
    ).readText()

    private val mediaCardSource = File(
        "src/androidMain/kotlin/com/continuum/app/tv/ui/components/TvMediaCard.kt",
    ).readText()

    private val catalogGridSource = File(
        "src/androidMain/kotlin/com/continuum/app/tv/ui/components/TvCatalogGrid.kt",
    ).readText()

    @Test
    fun tvPersonDetailUsesSharedPresentationAndLoadMore() {
        assertTrue(personSource.contains("personMetadataBadges("))
        assertTrue(personSource.contains("personWorksCountLabel("))
        assertTrue(personSource.contains("onLoadMore = viewModel::loadMoreIfNeeded"))
        assertTrue(personSource.contains("personWorkCardAspectRatio(item)"))
        assertTrue(personSource.contains("item.type == \"audiobook\""))
        assertTrue(mediaCardSource.contains("artworkAspectRatio: Float? = null"))
        assertTrue(catalogGridSource.contains("artworkAspectRatioForItem: (BrowseItem) -> Float? = { null }"))
        assertTrue(personSource.contains("fontSize = 46.sp"))
        assertTrue(personSource.contains("fontSize = 12.sp"))
        assertFalse(personSource.contains("fontSize = 72.sp"))
        assertFalse(personSource.contains("MaterialTheme.typography.bodyLarge"))
    }

    @Test
    fun castSectionNoLongerClaimsPersonDetailIsUnwired() {
        assertFalse(castSource.contains("no-op"))
        assertFalse(castSource.contains("person detail isn't wired"))
        assertTrue(castSource.contains("onCastMemberClick(member)"))
    }
}
```

- [ ] **Step 2: Run TV source test to verify failure**

Run:

```bash
./gradlew :androidTvApp:testDebugUnitTest --tests 'com.continuum.app.tv.ui.screens.people.TvPersonDetailSourceTest'
```

Expected: FAIL due old inline metadata/comment and missing load-more hook.

- [ ] **Step 3: Update TV screen state wiring**

In `TvPersonDetailScreen`, pass:

```kotlin
onFilterSelected = viewModel::applyFilter,
onLoadMore = viewModel::loadMoreIfNeeded,
```

Update `TvPersonDetailContent` to accept `onLoadMore: () -> Unit` and pass it to `TvCatalogGrid`:

```kotlin
hasMore = state.hasMore,
onLoadMore = onLoadMore,
```

`TvCatalogGrid` already triggers pagination from `hasMore` and `onLoadMore`, so do not add another `LaunchedEffect` inside `TvPersonDetailScreen`. Render `state.pagingError` in the header area below the filters so a failed page load is visible while loaded works remain on screen:

```kotlin
state.pagingError?.let { error ->
    Text(
        text = error,
        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.sp, lineHeight = 15.sp),
        color = Color.White.copy(alpha = 0.62f),
    )
}
```

- [ ] **Step 4: Replace TV metadata and sizing**

In `PersonHeader`:

```kotlin
fontSize = 46.sp,
lineHeight = 50.sp,
```

For biography:

```kotlin
style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.sp, lineHeight = 15.sp)
```

For badges:

```kotlin
val badges = personMetadataBadges(person, todayIso = java.time.LocalDate.now().toString())
```

For section count:

```kotlin
personWorksCountLabel(total = state.totalItems, loaded = state.items.size, hasMore = state.hasMore)
```

- [ ] **Step 5: Remove stale cast comment**

Replace the `TvCastCrewSection` KDoc sentence:

```kotlin
 * Cards lift on focus and invoke [onCastMemberClick] when selected. The caller
 * decides whether a member has a routable `person_id`; members without one can
 * still render as display-only credits.
```

- [ ] **Step 6: Add TV square audiobook artwork**

In `TvMediaCard.kt`, add a defaulted optional parameter:

```kotlin
fun TvMediaCard(
    title: String,
    posterUrl: String?,
    posterThumbhash: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    year: Int? = null,
    userState: MediaItemUserState? = null,
    progress: Float? = null,
    width: Dp = TvCardWidth,
    fillWidth: Boolean = false,
    artworkAspectRatio: Float? = null,
    focusRequester: FocusRequester? = null,
    cardModifier: Modifier = Modifier,
    overlay: OverlayData? = null,
    actions: TvMediaCardActions = TvMediaCardActions(),
)
```

Use the override for both fill-width and fixed-size branches:

```kotlin
val effectiveAspectRatio = artworkAspectRatio ?: (2f / 3f)
val height = width / effectiveAspectRatio
```

For fill-width cards:

```kotlin
.aspectRatio(effectiveAspectRatio)
```

In `TvCatalogGrid.kt`, add a defaulted callback:

```kotlin
artworkAspectRatioForItem: (BrowseItem) -> Float? = { null },
```

Pass it to `TvMediaCard`:

```kotlin
artworkAspectRatio = artworkAspectRatioForItem(item),
```

In `TvPersonDetailScreen.kt`, add:

```kotlin
private fun personWorkCardAspectRatio(item: BrowseItem): Float? =
    if (item.type == "audiobook") 1f else null
```

Pass it to `TvCatalogGrid`:

```kotlin
artworkAspectRatioForItem = ::personWorkCardAspectRatio,
```

- [ ] **Step 7: Run TV person tests**

Run:

```bash
./gradlew :androidTvApp:testDebugUnitTest --tests 'com.continuum.app.tv.ui.screens.people.TvPersonDetailSourceTest' --tests 'com.continuum.app.tv.ui.screens.people.TvPersonDetailViewModelTest'
```

Expected: PASS.

- [ ] **Step 8: Commit**

```bash
git add androidTvApp/src/androidMain/kotlin/com/continuum/app/tv/ui/screens/people/TvPersonDetailScreen.kt \
  androidTvApp/src/androidMain/kotlin/com/continuum/app/tv/ui/screens/detail/TvCastCrewSection.kt \
  androidTvApp/src/androidMain/kotlin/com/continuum/app/tv/ui/components/TvMediaCard.kt \
  androidTvApp/src/androidMain/kotlin/com/continuum/app/tv/ui/components/TvCatalogGrid.kt \
  androidTvApp/src/androidUnitTest/kotlin/com/continuum/app/tv/ui/screens/people/TvPersonDetailSourceTest.kt
git commit -m "feat: polish tv person detail"
```

---

### Task 6: Build And Device Verification

**Files:**
- No source files unless verification reveals a regression.

**Interfaces:**
- Consumes: all previous tasks.
- Produces: build artifacts and manual verification notes.

- [ ] **Step 1: Run focused tests**

Run:

```bash
./gradlew :shared:testDebugUnitTest \
  --tests 'com.continuum.app.network.api.CatalogApiTest' \
  --tests 'com.continuum.app.model.catalog.PersonPresentationTest' \
  :androidApp:testDebugUnitTest \
  --tests 'com.continuum.app.android.ui.screens.people.PersonDetailViewModelTest' \
  --tests 'com.continuum.app.android.ui.screens.people.PersonDetailSourceTest' \
  :androidTvApp:testDebugUnitTest \
  --tests 'com.continuum.app.tv.ui.screens.people.TvPersonDetailViewModelTest' \
  --tests 'com.continuum.app.tv.ui.screens.people.TvPersonDetailSourceTest'
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 2: Build both debug apps**

Run:

```bash
./gradlew :androidApp:assembleDebug :androidTvApp:assembleDebug
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Install to available Android devices/emulators**

Use connected device IDs from `adb devices`. Example:

```bash
adb -s <mobile-device-id> install -r -d androidApp/build/outputs/apk/debug/androidApp-debug.apk
adb -s <tv-device-id> install -r -d androidTvApp/build/outputs/apk/debug/androidTvApp-arm64-v8a-debug.apk
```

Expected: `Success` for each install. If only emulators are available, install there and note that Shield/Pixel verification is pending.

- [ ] **Step 4: Manual smoke test**

On mobile:

```text
Open a movie/series detail -> select a cast member -> verify Person page opens -> verify portrait/name/badges/bio -> scroll Works -> verify more pages load -> tap a work -> verify item detail opens -> Back returns to person.
```

On TV:

```text
Open a movie/series detail -> move focus to Cast -> select a cast member -> verify Person page opens -> verify readable tvOS-scale header -> verify Works grid -> verify reading media is absent -> scroll near end -> verify more pages load -> Back returns to prior detail.
```

- [ ] **Step 5: Final status**

Run:

```bash
git status --short --branch
```

Expected: only pre-existing unrelated dirty files remain, or a clean worktree if all task commits were created from a clean branch.
