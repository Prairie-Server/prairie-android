# Android Requests Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ship Approach A's Requests feature: shared request contract, mobile user request flow, and a lightweight Android TV request/status affordance.

**Architecture:** Add a shared Requests feature slice with tolerant serializable models, Ktor API, repository, and shared ViewModels. Mobile renders the user request experience in new Compose screens; TV reuses the shared layer for a focused request + My Requests experience. Admin request moderation is intentionally skipped for this execution pass.

**Tech Stack:** Kotlin Multiplatform shared module, Ktor client, kotlinx.serialization, Koin DI, Compose Material 3 on mobile, AndroidX TV Compose on TV, kotlin-test + coroutine test.

---

## File Structure

Shared files:

- Create `shared/src/commonMain/kotlin/com/continuum/app/model/request/RequestModels.kt`: server wire models and request constants.
- Create `shared/src/commonMain/kotlin/com/continuum/app/network/api/RequestsApi.kt`: user request endpoints.
- Create `shared/src/commonMain/kotlin/com/continuum/app/repository/RequestsRepository.kt`: user request operations.
- Create `shared/src/commonMain/kotlin/com/continuum/app/viewmodel/RequestsViewModels.kt`: `RequestsViewModel`, `RequestSearchViewModel`, `RequestDetailViewModel`, `MyRequestsViewModel`.
- Modify `shared/src/commonMain/kotlin/com/continuum/app/di/NetworkModule.kt`: bind APIs.
- Modify `shared/src/commonMain/kotlin/com/continuum/app/di/RepositoryModule.kt`: bind repositories.
- Create tests under `shared/src/commonTest/kotlin/com/continuum/app/model/request/` and `shared/src/commonTest/kotlin/com/continuum/app/repository/`.

Mobile files:

- Create package `androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/requests/`.
- Create `RequestComponents.kt`, `RequestsScreen.kt`, `RequestDetailScreen.kt`, `MyRequestsScreen.kt`.
- Modify `androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/navigation/Routes.kt`: add request routes.
- Modify `androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/navigation/AppNavigation.kt`: add request destinations.
- Modify `androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/MainScreen.kt`: add header entry navigation as needed.
- Modify `androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/settings/AccountSection.kt` or `ServerInfoSection.kt`: add stable Requests entry point if header entry is too crowded.
- Modify `androidApp/src/androidMain/kotlin/com/continuum/app/android/di/AndroidModule.kt`: bind parameterized ViewModels if shared ViewModels need constructor params.

TV files:

- Create package `androidTvApp/src/androidMain/kotlin/com/continuum/app/tv/ui/screens/requests/`.
- Create `TvRequestsScreen.kt`, `TvMyRequestsScreen.kt`, `TvRequestComponents.kt`.
- Modify `androidTvApp/src/androidMain/kotlin/com/continuum/app/tv/ui/navigation/TvRoute.kt` and `TvAppNavigation.kt`: add TV routes.
- Modify `androidTvApp/src/androidMain/kotlin/com/continuum/app/tv/ui/shell/TvTopMenuBar.kt`: add Requests menu entry.
- Modify `androidTvApp/src/androidMain/kotlin/com/continuum/app/tv/di/AndroidTvModule.kt`: bind TV request ViewModels if needed.

---

### Task 1: Shared Request Models

**Files:**
- Create: `shared/src/commonMain/kotlin/com/continuum/app/model/request/RequestModels.kt`
- Test: `shared/src/commonTest/kotlin/com/continuum/app/model/request/RequestModelsSerializationTest.kt`

- [ ] **Step 1: Write the failing serialization test**

```kotlin
package com.continuum.app.model.request

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RequestModelsSerializationTest {
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

    @Test
    fun `decodes media detail with request state and recommendations`() {
        val payload = """
            {
              "media_type": "movie",
              "tmdb_id": 550,
              "imdb_id": "tt0137523",
              "title": "Fight Club",
              "year": 1999,
              "poster_path": "/poster.jpg",
              "availability": "missing",
              "request": {
                "status": "pending",
                "requestable": false,
                "reason": "Already requested",
                "request_id": "req_1"
              },
              "recommendations": [
                {
                  "media_type": "movie",
                  "tmdb_id": 13,
                  "title": "Forrest Gump",
                  "availability": "available",
                  "library_content_id": "movie-13",
                  "request": { "requestable": false, "reason": "Available" }
                }
              ]
            }
        """.trimIndent()

        val detail = json.decodeFromString(RequestMediaDetail.serializer(), payload)

        assertEquals(RequestMediaType.Movie, detail.mediaType)
        assertEquals(550, detail.tmdbId)
        assertFalse(detail.request.requestable)
        assertEquals("req_1", detail.request.requestId)
        assertEquals("movie-13", detail.recommendations.single().libraryContentId)
    }

    @Test
    fun `decodes request with fulfillment targets`() {
        val payload = """
            {
              "id": "req_1",
              "provider": "tmdb",
              "media_type": "series",
              "tmdb_id": 1399,
              "title": "Game of Thrones",
              "status": "queued",
              "outcome": "active",
              "targets": [
                {
                  "id": 7,
                  "request_id": "req_1",
                  "integration_kind": "sonarr",
                  "instance_name": "Sonarr 4K",
                  "quality": "2160p",
                  "status": "queued",
                  "created_at": "2026-06-09T00:00:00Z",
                  "updated_at": "2026-06-09T00:00:00Z"
                }
              ],
              "created_at": "2026-06-09T00:00:00Z",
              "updated_at": "2026-06-09T00:00:00Z"
            }
        """.trimIndent()

        val request = json.decodeFromString(MediaRequest.serializer(), payload)

        assertEquals(RequestMediaType.Series, request.mediaType)
        assertEquals(RequestStatus.Queued, request.status)
        assertEquals("Sonarr 4K", request.targets.single().instanceName)
        assertTrue(request.isActive)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run:

```bash
./gradlew :shared:testDebugUnitTest --tests com.continuum.app.model.request.RequestModelsSerializationTest
```

Expected: fails to compile because `RequestMediaDetail`, `RequestMediaType`, `MediaRequest`, and `RequestStatus` do not exist.

- [ ] **Step 3: Implement request models**

Add `RequestModels.kt` with this structure:

```kotlin
package com.continuum.app.model.request

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

object RequestMediaType {
    const val Movie = "movie"
    const val Series = "series"
    const val All = "all"
}

object RequestStatus {
    const val Pending = "pending"
    const val Approved = "approved"
    const val Queued = "queued"
    const val Downloading = "downloading"
    const val Completed = "completed"
    const val Failed = "failed"
}

object RequestOutcome {
    const val Active = "active"
    const val Declined = "declined"
    const val Cancelled = "cancelled"
    const val Failed = "failed"
}

object RequestAvailability {
    const val Missing = "missing"
    const val Available = "available"
}

@Serializable
data class RequestState(
    val status: String? = null,
    val requestable: Boolean = false,
    val reason: String = "",
    @SerialName("request_id") val requestId: String? = null,
)

@Serializable
data class RequestMediaResult(
    @SerialName("media_type") val mediaType: String,
    @SerialName("tmdb_id") val tmdbId: Int,
    val title: String,
    val year: Int? = null,
    val overview: String = "",
    @SerialName("poster_path") val posterPath: String? = null,
    @SerialName("backdrop_path") val backdropPath: String? = null,
    @SerialName("release_date") val releaseDate: String? = null,
    val popularity: Double? = null,
    @SerialName("vote_average") val voteAverage: Double? = null,
    val availability: String = RequestAvailability.Missing,
    @SerialName("library_content_id") val libraryContentId: String? = null,
    val request: RequestState = RequestState(),
)

@Serializable
data class RequestMediaPage(
    val page: Int = 1,
    @SerialName("total_pages") val totalPages: Int = 1,
    @SerialName("total_results") val totalResults: Int = 0,
    val results: List<RequestMediaResult> = emptyList(),
)

@Serializable
data class RequestDiscoverySection(
    val key: String,
    val title: String,
    val page: Int = 1,
    @SerialName("total_pages") val totalPages: Int = 1,
    @SerialName("total_results") val totalResults: Int = 0,
    val results: List<RequestMediaResult> = emptyList(),
)

@Serializable
data class RequestsDiscoverResponse(
    val sections: List<RequestDiscoverySection> = emptyList(),
)

@Serializable
data class RequestCastMember(
    val name: String,
    val character: String = "",
    @SerialName("profile_path") val profilePath: String? = null,
    val order: Int = 0,
)

@Serializable
data class RequestMediaDetail(
    @SerialName("media_type") val mediaType: String,
    @SerialName("tmdb_id") val tmdbId: Int,
    @SerialName("imdb_id") val imdbId: String = "",
    @SerialName("tvdb_id") val tvdbId: Int? = null,
    val title: String,
    @SerialName("original_title") val originalTitle: String = "",
    val tagline: String = "",
    val overview: String = "",
    @SerialName("poster_path") val posterPath: String? = null,
    @SerialName("backdrop_path") val backdropPath: String? = null,
    @SerialName("release_date") val releaseDate: String? = null,
    val year: Int? = null,
    val runtime: Int? = null,
    val genres: List<String> = emptyList(),
    @SerialName("vote_average") val voteAverage: Double? = null,
    @SerialName("vote_count") val voteCount: Int? = null,
    val status: String = "",
    val homepage: String = "",
    @SerialName("content_rating") val contentRating: String = "",
    @SerialName("production_companies") val productionCompanies: List<String> = emptyList(),
    @SerialName("number_of_seasons") val numberOfSeasons: Int? = null,
    @SerialName("number_of_episodes") val numberOfEpisodes: Int? = null,
    @SerialName("first_air_date") val firstAirDate: String? = null,
    @SerialName("last_air_date") val lastAirDate: String? = null,
    val networks: List<String> = emptyList(),
    val cast: List<RequestCastMember> = emptyList(),
    val director: String = "",
    val creators: List<String> = emptyList(),
    val recommendations: List<RequestMediaResult> = emptyList(),
    val availability: String = RequestAvailability.Missing,
    @SerialName("library_content_id") val libraryContentId: String? = null,
    val request: RequestState = RequestState(),
)

@Serializable
data class CreateMediaRequest(
    @SerialName("media_type") val mediaType: String,
    @SerialName("tmdb_id") val tmdbId: Int,
    @SerialName("tvdb_id") val tvdbId: Int? = null,
    @SerialName("imdb_id") val imdbId: String = "",
    val title: String,
    val year: Int? = null,
    val overview: String = "",
    @SerialName("poster_path") val posterPath: String? = null,
    @SerialName("backdrop_path") val backdropPath: String? = null,
)

@Serializable
data class RequestTarget(
    val id: Long,
    @SerialName("request_id") val requestId: String,
    @SerialName("integration_id") val integrationId: String = "",
    @SerialName("integration_kind") val integrationKind: String = "",
    @SerialName("instance_name") val instanceName: String = "",
    val quality: String = "",
    @SerialName("is_anime") val isAnime: Boolean = false,
    @SerialName("external_id") val externalId: String = "",
    @SerialName("external_status") val externalStatus: String = "",
    val status: String = RequestStatus.Pending,
    @SerialName("last_error") val lastError: String = "",
    @SerialName("created_at") val createdAt: String,
    @SerialName("updated_at") val updatedAt: String,
)

@Serializable
data class MediaRequest(
    val id: String,
    val provider: String = "",
    @SerialName("media_type") val mediaType: String,
    @SerialName("tmdb_id") val tmdbId: Int,
    @SerialName("tvdb_id") val tvdbId: Int? = null,
    @SerialName("imdb_id") val imdbId: String = "",
    val title: String,
    val year: Int? = null,
    val overview: String = "",
    @SerialName("poster_path") val posterPath: String? = null,
    @SerialName("backdrop_path") val backdropPath: String? = null,
    val status: String,
    val outcome: String,
    @SerialName("requested_by_user_id") val requestedByUserId: Int? = null,
    @SerialName("requested_by_profile_id") val requestedByProfileId: String = "",
    @SerialName("integration_kind") val integrationKind: String = "",
    @SerialName("is_anime") val isAnime: Boolean = false,
    val targets: List<RequestTarget> = emptyList(),
    @SerialName("external_id") val externalId: String = "",
    @SerialName("external_status") val externalStatus: String = "",
    @SerialName("library_content_id") val libraryContentId: String? = null,
    @SerialName("last_error") val lastError: String = "",
    @SerialName("created_at") val createdAt: String,
    @SerialName("updated_at") val updatedAt: String,
    @SerialName("approved_at") val approvedAt: String? = null,
    @SerialName("completed_at") val completedAt: String? = null,
) {
    val isActive: Boolean get() = outcome == RequestOutcome.Active
}

@Serializable
data class RequestsListResponse(val requests: List<MediaRequest> = emptyList())

@Serializable
data class RequestsFeatureStatus(@SerialName("requests_enabled") val requestsEnabled: Boolean)

@Serializable
data class RequestDecisionBody(val reason: String = "")
```

- [ ] **Step 4: Run model tests**

Run:

```bash
./gradlew :shared:testDebugUnitTest --tests com.continuum.app.model.request.RequestModelsSerializationTest
```

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add shared/src/commonMain/kotlin/com/continuum/app/model/request/RequestModels.kt shared/src/commonTest/kotlin/com/continuum/app/model/request/RequestModelsSerializationTest.kt
git commit -m "feat: add request wire models"
```

### Task 2: Shared User Requests API and Repository

**Files:**
- Create: `shared/src/commonMain/kotlin/com/continuum/app/network/api/RequestsApi.kt`
- Create: `shared/src/commonMain/kotlin/com/continuum/app/repository/RequestsRepository.kt`
- Modify: `shared/src/commonMain/kotlin/com/continuum/app/di/NetworkModule.kt`
- Modify: `shared/src/commonMain/kotlin/com/continuum/app/di/RepositoryModule.kt`
- Test: `shared/src/commonTest/kotlin/com/continuum/app/repository/RequestsRepositoryTest.kt`

- [ ] **Step 1: Write failing repository tests**

```kotlin
package com.continuum.app.repository

import com.continuum.app.model.request.CreateMediaRequest
import com.continuum.app.model.request.MediaRequest
import com.continuum.app.model.request.RequestMediaDetail
import com.continuum.app.model.request.RequestMediaPage
import com.continuum.app.model.request.RequestMediaType
import com.continuum.app.model.request.RequestOutcome
import com.continuum.app.model.request.RequestStatus
import com.continuum.app.model.request.RequestsFeatureStatus
import com.continuum.app.model.request.RequestsListResponse
import com.continuum.app.network.ApiResult
import com.continuum.app.network.api.RequestsApi
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class RequestsRepositoryTest {
    private fun request(id: String = "req_1") = MediaRequest(
        id = id,
        mediaType = RequestMediaType.Movie,
        tmdbId = 550,
        title = "Fight Club",
        status = RequestStatus.Pending,
        outcome = RequestOutcome.Active,
        createdAt = "2026-06-09T00:00:00Z",
        updatedAt = "2026-06-09T00:00:00Z",
    )

    @Test
    fun `mine refresh populates state flow`() = runTest {
        val repo = RequestsRepository(FakeRequestsApi(mine = ApiResult.Success(RequestsListResponse(listOf(request())))))

        val result = repo.refreshMine()

        assertIs<ApiResult.Success<RequestsListResponse>>(result)
        assertEquals("req_1", repo.mine.value.single().id)
    }

    @Test
    fun `create upserts returned request into mine`() = runTest {
        val repo = RequestsRepository(FakeRequestsApi(create = ApiResult.Success(request("req_new"))))

        val result = repo.create(CreateMediaRequest(RequestMediaType.Movie, 550, title = "Fight Club"))

        assertIs<ApiResult.Success<MediaRequest>>(result)
        assertEquals("req_new", repo.mine.value.single().id)
    }

    @Test
    fun `cancel replaces matching request in mine`() = runTest {
        val cancelled = request().copy(outcome = RequestOutcome.Cancelled)
        val repo = RequestsRepository(FakeRequestsApi(cancel = ApiResult.Success(cancelled)))
        repo.seedMineForTest(listOf(request()))

        val result = repo.cancel("req_1")

        assertIs<ApiResult.Success<MediaRequest>>(result)
        assertEquals(RequestOutcome.Cancelled, repo.mine.value.single().outcome)
    }
}

private class FakeRequestsApi(
    private val status: ApiResult<RequestsFeatureStatus> = ApiResult.Success(RequestsFeatureStatus(true)),
    private val discover: ApiResult<com.continuum.app.model.request.RequestsDiscoverResponse> =
        ApiResult.Success(com.continuum.app.model.request.RequestsDiscoverResponse()),
    private val search: ApiResult<RequestMediaPage> = ApiResult.Success(RequestMediaPage()),
    private val detail: ApiResult<RequestMediaDetail> = ApiResult.Error(404, "not_found", "Not found"),
    private val create: ApiResult<MediaRequest> = ApiResult.Error(400, "bad_request", "Bad request"),
    private val mine: ApiResult<RequestsListResponse> = ApiResult.Success(RequestsListResponse()),
    private val cancel: ApiResult<MediaRequest> = ApiResult.Error(400, "bad_request", "Bad request"),
) : RequestsApi {
    override suspend fun getStatus() = status
    override suspend fun discover() = discover
    override suspend fun discoverSection(section: String, page: Int) = ApiResult.Error(404, "not_found", "Not found")
    override suspend fun search(query: String, mediaType: String, page: Int) = search
    override suspend fun detail(mediaType: String, tmdbId: Int) = detail
    override suspend fun create(request: CreateMediaRequest) = create
    override suspend fun mine(status: String?, outcome: String?, limit: Int, offset: Int) = mine
    override suspend fun get(id: String) = ApiResult.Success(request(id))
    override suspend fun cancel(id: String) = cancel
}
```

- [ ] **Step 2: Run test to verify it fails**

Run:

```bash
./gradlew :shared:testDebugUnitTest --tests com.continuum.app.repository.RequestsRepositoryTest
```

Expected: fails to compile because `RequestsApi` and `RequestsRepository` do not exist.

- [ ] **Step 3: Implement user API**

Create `RequestsApi.kt`:

```kotlin
package com.continuum.app.network.api

import com.continuum.app.model.request.CreateMediaRequest
import com.continuum.app.model.request.MediaRequest
import com.continuum.app.model.request.RequestMediaDetail
import com.continuum.app.model.request.RequestMediaPage
import com.continuum.app.model.request.RequestMediaType
import com.continuum.app.model.request.RequestOutcome
import com.continuum.app.model.request.RequestStatus
import com.continuum.app.model.request.RequestsDiscoverResponse
import com.continuum.app.model.request.RequestsFeatureStatus
import com.continuum.app.model.request.RequestsListResponse
import com.continuum.app.network.ApiResult
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType

interface RequestsApi {
    suspend fun getStatus(): ApiResult<RequestsFeatureStatus>
    suspend fun discover(): ApiResult<RequestsDiscoverResponse>
    suspend fun discoverSection(section: String, page: Int = 1): ApiResult<com.continuum.app.model.request.RequestDiscoverySection>
    suspend fun search(query: String, mediaType: String = RequestMediaType.All, page: Int = 1): ApiResult<RequestMediaPage>
    suspend fun detail(mediaType: String, tmdbId: Int): ApiResult<RequestMediaDetail>
    suspend fun create(request: CreateMediaRequest): ApiResult<MediaRequest>
    suspend fun mine(status: String? = null, outcome: String? = null, limit: Int = 50, offset: Int = 0): ApiResult<RequestsListResponse>
    suspend fun get(id: String): ApiResult<MediaRequest>
    suspend fun cancel(id: String): ApiResult<MediaRequest>
}

class DefaultRequestsApi(private val client: HttpClient) : RequestsApi {
    override suspend fun getStatus() = safeApiCall<RequestsFeatureStatus> {
        client.get("/api/v1/requests/status")
    }

    override suspend fun discover() = safeApiCall<RequestsDiscoverResponse> {
        client.get("/api/v1/requests/discover")
    }

    override suspend fun discoverSection(section: String, page: Int) =
        safeApiCall<com.continuum.app.model.request.RequestDiscoverySection> {
            client.get("/api/v1/requests/discover/$section") { parameter("page", page) }
        }

    override suspend fun search(query: String, mediaType: String, page: Int) =
        safeApiCall<RequestMediaPage> {
            client.get("/api/v1/requests/search") {
                parameter("q", query)
                parameter("media_type", mediaType)
                parameter("page", page)
            }
        }

    override suspend fun detail(mediaType: String, tmdbId: Int) =
        safeApiCall<RequestMediaDetail> {
            client.get("/api/v1/requests/detail/$mediaType/$tmdbId")
        }

    override suspend fun create(request: CreateMediaRequest) = safeApiCall<MediaRequest> {
        client.post("/api/v1/requests/") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }
    }

    override suspend fun mine(status: String?, outcome: String?, limit: Int, offset: Int) =
        safeApiCall<RequestsListResponse> {
            client.get("/api/v1/requests/mine") {
                parameter("status", status)
                parameter("outcome", outcome)
                parameter("limit", limit)
                parameter("offset", offset)
            }
        }

    override suspend fun get(id: String) = safeApiCall<MediaRequest> {
        client.get("/api/v1/requests/$id")
    }

    override suspend fun cancel(id: String) = safeApiCall<MediaRequest> {
        client.post("/api/v1/requests/$id/cancel")
    }
}

```

- [ ] **Step 4: Implement repository**

Create `RequestsRepository.kt`:

```kotlin
package com.continuum.app.repository

import com.continuum.app.model.request.CreateMediaRequest
import com.continuum.app.model.request.MediaRequest
import com.continuum.app.model.request.RequestMediaType
import com.continuum.app.model.request.RequestOutcome
import com.continuum.app.model.request.RequestStatus
import com.continuum.app.network.ApiResult
import com.continuum.app.network.api.RequestsApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class RequestsRepository(private val api: RequestsApi) {
    private val _mine = MutableStateFlow<List<MediaRequest>>(emptyList())
    val mine: StateFlow<List<MediaRequest>> = _mine.asStateFlow()

    suspend fun status() = api.getStatus()
    suspend fun discover() = api.discover()
    suspend fun discoverSection(section: String, page: Int = 1) = api.discoverSection(section, page)
    suspend fun search(query: String, mediaType: String = RequestMediaType.All, page: Int = 1) =
        api.search(query, mediaType, page)
    suspend fun detail(mediaType: String, tmdbId: Int) = api.detail(mediaType, tmdbId)
    suspend fun get(id: String) = api.get(id)

    suspend fun refreshMine(
        status: String? = null,
        outcome: String? = null,
        limit: Int = 50,
        offset: Int = 0,
    ) = api.mine(status, outcome, limit, offset).also { result ->
        if (result is ApiResult.Success) _mine.value = result.data.requests
    }

    suspend fun create(request: CreateMediaRequest) = api.create(request).also(::upsertOnSuccess)
    suspend fun cancel(id: String) = api.cancel(id).also(::upsertOnSuccess)

    fun seedMineForTest(requests: List<MediaRequest>) {
        _mine.value = requests
    }

    private fun upsertOnSuccess(result: ApiResult<MediaRequest>) {
        if (result !is ApiResult.Success) return
        val updated = result.data
        _mine.value = _mine.value.filterNot { it.id == updated.id } + updated
    }
}
```

- [ ] **Step 5: Bind API and repository**

Modify `NetworkModule.kt`:

```kotlin
single<RequestsApi> { DefaultRequestsApi(get()) }
```

Modify `RepositoryModule.kt`:

```kotlin
single { RequestsRepository(get()) }
```

- [ ] **Step 6: Run tests**

Run:

```bash
./gradlew :shared:testDebugUnitTest --tests com.continuum.app.repository.RequestsRepositoryTest
```

Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add shared/src/commonMain/kotlin/com/continuum/app/network/api/RequestsApi.kt shared/src/commonMain/kotlin/com/continuum/app/repository/RequestsRepository.kt shared/src/commonMain/kotlin/com/continuum/app/di/NetworkModule.kt shared/src/commonMain/kotlin/com/continuum/app/di/RepositoryModule.kt shared/src/commonTest/kotlin/com/continuum/app/repository/RequestsRepositoryTest.kt
git commit -m "feat: add shared requests repository"
```

### Task 3: Shared Request ViewModels

**Files:**
- Create: `shared/src/commonMain/kotlin/com/continuum/app/viewmodel/RequestsViewModels.kt`
- Test: `shared/src/commonTest/kotlin/com/continuum/app/viewmodel/RequestsViewModelTest.kt`

- [ ] **Step 1: Write failing ViewModel tests**

```kotlin
package com.continuum.app.viewmodel

import com.continuum.app.model.request.MediaRequest
import com.continuum.app.model.request.RequestMediaType
import com.continuum.app.model.request.RequestOutcome
import com.continuum.app.model.request.RequestStatus
import com.continuum.app.model.request.RequestsDiscoverResponse
import com.continuum.app.model.request.RequestsListResponse
import com.continuum.app.network.ApiResult
import com.continuum.app.repository.RequestsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class RequestsViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @BeforeTest fun setUp() { Dispatchers.setMain(dispatcher) }
    @AfterTest fun tearDown() { Dispatchers.resetMain() }

    @Test
    fun `requests view model loads discover rows`() = runTest(dispatcher) {
        val repo = RequestsRepository(FakeRequestsApi(discover = ApiResult.Success(RequestsDiscoverResponse())))
        val vm = RequestsViewModel(repo)
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(false, vm.uiState.value.isLoading)
    }

    @Test
    fun `my requests view model loads mine queue`() = runTest(dispatcher) {
        val repo = RequestsRepository(FakeRequestsApi(mine = ApiResult.Success(RequestsListResponse(listOf(request())))))
        val vm = MyRequestsViewModel(repo)
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals("req_1", vm.uiState.value.requests.single().id)
    }

    private fun request(status: String = RequestStatus.Pending) = MediaRequest(
        id = "req_1",
        mediaType = RequestMediaType.Movie,
        tmdbId = 550,
        title = "Fight Club",
        status = status,
        outcome = RequestOutcome.Active,
        createdAt = "2026-06-09T00:00:00Z",
        updatedAt = "2026-06-09T00:00:00Z",
    )
}
```

Use the fake APIs from repository tests or duplicate them locally if package visibility requires it.

- [ ] **Step 2: Run test to verify it fails**

```bash
./gradlew :shared:testDebugUnitTest --tests com.continuum.app.viewmodel.RequestsViewModelTest
```

Expected: fails to compile because the ViewModels do not exist.

- [ ] **Step 3: Implement ViewModels**

Create state classes and ViewModels in `RequestsViewModels.kt`:

```kotlin
data class RequestsUiState(
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val enabled: Boolean = true,
    val sections: List<RequestDiscoverySection> = emptyList(),
    val error: String? = null,
)

data class RequestSearchUiState(
    val query: String = "",
    val mediaType: String = RequestMediaType.All,
    val isLoading: Boolean = false,
    val results: List<RequestMediaResult> = emptyList(),
    val error: String? = null,
)

data class RequestDetailUiState(
    val isLoading: Boolean = true,
    val detail: RequestMediaDetail? = null,
    val isSubmitting: Boolean = false,
    val error: String? = null,
    val notice: String? = null,
)

data class RequestListUiState(
    val isLoading: Boolean = true,
    val requests: List<MediaRequest> = emptyList(),
    val error: String? = null,
    val actionInFlightId: String? = null,
)
```

Implement:

- `RequestsViewModel(repository)` loads status then discover.
- `RequestSearchViewModel(repository)` exposes `onQueryChanged`, `onMediaTypeChanged`, `search`.
- `RequestDetailViewModel(repository, mediaType, tmdbId)` loads detail and creates request from detail.
- `MyRequestsViewModel(repository)` loads mine and cancels by id.

Use `viewModelScope.launch`, `ApiResult` handling, and existing ViewModel style from `RecommendationsViewModel`.

- [ ] **Step 4: Run ViewModel tests**

```bash
./gradlew :shared:testDebugUnitTest --tests com.continuum.app.viewmodel.RequestsViewModelTest
```

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add shared/src/commonMain/kotlin/com/continuum/app/viewmodel/RequestsViewModels.kt shared/src/commonTest/kotlin/com/continuum/app/viewmodel/RequestsViewModelTest.kt
git commit -m "feat: add request view models"
```

### Task 4: Mobile User Requests UI

**Files:**
- Create: `androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/requests/RequestComponents.kt`
- Create: `androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/requests/RequestsScreen.kt`
- Create: `androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/requests/RequestDetailScreen.kt`
- Create: `androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/requests/MyRequestsScreen.kt`
- Modify: `androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/navigation/Routes.kt`
- Modify: `androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/navigation/AppNavigation.kt`
- Modify: `androidApp/src/androidMain/kotlin/com/continuum/app/android/di/AndroidModule.kt`

- [ ] **Step 1: Add routes and ViewModel bindings**

Add routes to `Routes.kt`:

```kotlin
data object Requests : Route("requests")
data object MyRequests : Route("requests/mine")
data class RequestDetail(val mediaType: String, val tmdbId: Int) : Route("requests/detail/$mediaType/$tmdbId") {
    companion object {
        const val ROUTE = "requests/detail/{mediaType}/{tmdbId}"
        const val ARG_MEDIA_TYPE = "mediaType"
        const val ARG_TMDB_ID = "tmdbId"
    }
}
```

Bind parameterized detail ViewModel in `AndroidModule.kt`:

```kotlin
viewModel { params ->
    RequestDetailViewModel(
        repository = get(),
        mediaType = params.get(),
        tmdbId = params.get(),
    )
}
viewModel { RequestsViewModel(get()) }
viewModel { RequestSearchViewModel(get()) }
viewModel { MyRequestsViewModel(get()) }
```

- [ ] **Step 2: Create reusable request UI components**

`RequestComponents.kt` should include:

- `RequestPosterCard(result, onClick)`
- `RequestStatusBadge(status, outcome)`
- `RequestMediaTypeChips(selected, onSelected)`
- `RequestActionButton(label, enabled, isLoading, onClick)`

Use Material 3 `Card`, `AssistChip`, `Button`, `LazyRow`, and Coil `AsyncImage`. Poster URLs should render if `posterPath` is absolute; otherwise show a quiet empty artwork state until image URL resolution is formalized.

- [ ] **Step 3: Create `RequestsScreen.kt`**

Implement:

- Top title `Requests`.
- Buttons for `Search` and `My Requests`.
- Discover rows from `RequestsViewModel`.
- Inline search panel backed by `RequestSearchViewModel`.
- Card click routes to `Route.RequestDetail(mediaType, tmdbId)`.
- Available items route to `Route.ItemDetail(libraryContentId)`.

- [ ] **Step 4: Create `RequestDetailScreen.kt`**

Implement:

- Loading/error states.
- Poster/backdrop/title/year/overview/details.
- `Request` CTA when `detail.request.requestable`.
- Disabled CTA with `detail.request.reason` otherwise.
- `Open Library Item` action when `libraryContentId` exists.
- Recommendations rail using `detail.recommendations`.

- [ ] **Step 5: Create `MyRequestsScreen.kt`**

Implement:

- List of `MyRequestsViewModel.uiState.requests`.
- Status/outcome badges.
- Target summary expansion with quality, instance, external status, last error.
- Cancel action for active pending requests; server errors surface as row-level/global error.

- [ ] **Step 6: Wire destinations in `AppNavigation.kt`**

Add composables for `Route.Requests`, `Route.MyRequests`, and `Route.RequestDetail.ROUTE`. Navigation callbacks:

```kotlin
onRequestClick = { mediaType, tmdbId -> navController.navigate(Route.RequestDetail(mediaType, tmdbId).route) }
onLibraryItemClick = { contentId -> navController.navigate(Route.ItemDetail(contentId).route) }
onMineClick = { navController.navigate(Route.MyRequests.route) }
```

- [ ] **Step 7: Add visible mobile entry point**

Add a Requests row in Settings or the main header action area:

```kotlin
SettingsClickableRow(
    icon = Icons.Default.PlaylistAdd,
    label = "Requests",
    onClick = onRequestsClick,
)
```

Thread `onRequestsClick` from `SettingsScreen` through `AppNavigation` to `navController.navigate(Route.Requests.route)`.

- [ ] **Step 8: Compile mobile**

```bash
./gradlew :androidApp:compileDebugKotlinAndroid
```

Expected: PASS.

- [ ] **Step 9: Commit**

```bash
git add androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/requests androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/navigation/Routes.kt androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/navigation/AppNavigation.kt androidApp/src/androidMain/kotlin/com/continuum/app/android/di/AndroidModule.kt androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/settings
git commit -m "feat: add mobile request browsing"
```

### Task 5: Android TV Requests

**Files:**
- Create: `androidTvApp/src/androidMain/kotlin/com/continuum/app/tv/ui/screens/requests/TvRequestComponents.kt`
- Create: `androidTvApp/src/androidMain/kotlin/com/continuum/app/tv/ui/screens/requests/TvRequestsScreen.kt`
- Create: `androidTvApp/src/androidMain/kotlin/com/continuum/app/tv/ui/screens/requests/TvMyRequestsScreen.kt`
- Modify: `androidTvApp/src/androidMain/kotlin/com/continuum/app/tv/ui/navigation/TvRoute.kt`
- Modify: `androidTvApp/src/androidMain/kotlin/com/continuum/app/tv/ui/navigation/TvAppNavigation.kt`
- Modify: `androidTvApp/src/androidMain/kotlin/com/continuum/app/tv/ui/shell/TvTopMenuBar.kt`
- Modify: `androidTvApp/src/androidMain/kotlin/com/continuum/app/tv/di/AndroidTvModule.kt`

- [ ] **Step 1: Add TV routes and DI**

Routes:

```kotlin
data object Requests : TvRoute("requests")
data object MyRequests : TvRoute("requests/mine")
```

DI:

```kotlin
viewModel { RequestsViewModel(get()) }
viewModel { MyRequestsViewModel(get()) }
```

- [ ] **Step 2: Create TV components**

`TvRequestComponents.kt`:

- `TvRequestCard(result, onClick)`
- `TvRequestStatusChip(status, outcome)`
- `TvRequestActionPill(label, icon, onClick)`

Use existing TV components such as `TvMediaCard`, `TvChip`, and `TvHeroActionPill` where possible.

- [ ] **Step 3: Create `TvRequestsScreen.kt`**

Implement:

- D-pad navigable section rows from discover.
- Requestable cards show status chip.
- Pressing a requestable card requests directly only after a confirmation dialog.
- Available cards navigate to existing TV detail by `libraryContentId`.
- Include top action to `My Requests`.

- [ ] **Step 4: Create `TvMyRequestsScreen.kt`**

Implement:

- Vertical list/grid of user requests.
- Status and outcome chips.
- No admin actions.
- Optional cancel action for pending active user requests.

- [ ] **Step 5: Wire TV shell/navigation**

Add Requests menu item in `TvTopMenuBar.kt`; add routes to `TvAppNavigation.kt`.

- [ ] **Step 6: Compile and test TV**

```bash
./gradlew :androidTvApp:testDebugUnitTest :androidTvApp:compileDebugKotlinAndroid
```

Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add androidTvApp/src/androidMain/kotlin/com/continuum/app/tv/ui/screens/requests androidTvApp/src/androidMain/kotlin/com/continuum/app/tv/ui/navigation/TvRoute.kt androidTvApp/src/androidMain/kotlin/com/continuum/app/tv/ui/navigation/TvAppNavigation.kt androidTvApp/src/androidMain/kotlin/com/continuum/app/tv/ui/shell/TvTopMenuBar.kt androidTvApp/src/androidMain/kotlin/com/continuum/app/tv/di/AndroidTvModule.kt
git commit -m "feat: add tv request browsing"
```

### Task 6: Final Verification and Follow-Up Queue

**Files:**
- Modify: `docs/superpowers/specs/2026-06-09-android-requests-design.md` only if implementation reveals a real contract correction.

- [ ] **Step 1: Run full verification**

```bash
./gradlew :shared:testDebugUnitTest :android-shared:testDebugUnitTest :androidApp:compileDebugKotlinAndroid :androidTvApp:testDebugUnitTest :androidTvApp:compileDebugKotlinAndroid
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 2: Inspect git status**

```bash
git status --short --branch
```

Expected: clean worktree, branch ahead by the new request commits.

- [ ] **Step 3: Capture remaining request follow-ups**

If not implemented in Tasks 1-5, add issue notes to the final response:

- Deferred admin request moderation, settings, and integrations forms.
- Request user-limit editor.
- Studio/network/genre browse pages beyond the discover rows.
- Server/web custom app-link handoff for mobile device pairing.
- Next Approach A feature group: subtitle acquisition.

- [ ] **Step 4: Commit docs correction if needed**

Only if Step 3 required a spec correction:

```bash
git add docs/superpowers/specs/2026-06-09-android-requests-design.md
git commit -m "docs: update requests rollout notes"
```

---

## Self-Review

- Spec coverage: Tasks 1-2 cover shared contract and repositories; Task 3 covers shared ViewModels; Task 4 covers mobile user flow; Task 5 covers TV; Task 6 covers verification and follow-up queue.
- Completeness scan: no unresolved markers or empty implementation instructions remain.
- Type consistency: request model names are used consistently across API, repository, ViewModel, mobile, and TV tasks.
- Scope check: admin request moderation, settings, and integrations are intentionally deferred per user direction, not included in this implementation pass.
