# Android Media Surfaces Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add server-contract-aligned audiobook support to Android mobile and Android TV, add server-synced ebook reading to Android mobile, and hide ebooks from Android TV.

**Architecture:** Start with shared Kotlin models and API wrappers that match the deployed `prairie-server` contract. Then adapt existing mobile audiobook/reader screens to use `versions` and reader APIs, and add defensive Android TV ebook filtering plus audiobook-aware labels/playback. Each task leaves the repo in a compilable, testable state.

**Tech Stack:** Kotlin Multiplatform shared module, kotlinx.serialization, Ktor client, Koin, Jetpack Compose mobile, Compose for TV, Media3 playback session infrastructure, Gradle JVM/unit tests.

---

## File Structure

- Modify `shared/src/commonMain/kotlin/com/continuum/app/model/audiobook/AudiobookMetadata.kt`: replace stale `file_url`-oriented contract with current server audiobook extension models and display helpers.
- Create `shared/src/commonMain/kotlin/com/continuum/app/model/ebook/EbookMetadata.kt`: current server ebook extension models and shared person/series/related shapes reused with audiobook where practical.
- Create `shared/src/commonMain/kotlin/com/continuum/app/model/ebook/EbookReaderModels.kt`: progress, config, annotation, and bookmark request/response models.
- Create `shared/src/commonMain/kotlin/com/continuum/app/model/ebook/EbookVersionSelection.kt`: pure helper for supported ebook version detection and selection.
- Modify `shared/src/commonMain/kotlin/com/continuum/app/model/catalog/CatalogModels.kt`: add `ItemDetail.ebook` and keep `book` only as legacy fallback.
- Create `shared/src/commonMain/kotlin/com/continuum/app/network/api/EbookReaderApi.kt`: Ktor API wrapper for `/api/v1/ebooks`.
- Create `shared/src/commonMain/kotlin/com/continuum/app/repository/EbookReaderRepository.kt`: repository facade for mobile reader code.
- Modify `shared/src/commonMain/kotlin/com/continuum/app/di/NetworkModule.kt`: register `EbookReaderApi`.
- Modify `shared/src/commonMain/kotlin/com/continuum/app/di/RepositoryModule.kt`: register `EbookReaderRepository`.
- Add common tests under `shared/src/commonTest/kotlin/com/continuum/app/model/...`: serialization and version-selection coverage.
- Modify `androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/audiobook/AudiobookDetailContent.kt`: render new metadata and select versions.
- Modify `androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/audiobook/AudiobookPlayerViewModel.kt`: remove `fileUrl` dependency, load from detail `versions`, and start a playback session for the selected `fileId`.
- Modify `androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/audiobook/AudiobookPlayerScreen.kt`: accept stream/session state produced by the updated view model.
- Modify `androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/book/BookDetailContent.kt`: route ebook details to the reader using `versions`.
- Modify `androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/detail/ItemDetailScreen.kt`: treat `ebook` as ebook, not legacy book.
- Modify `androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/navigation/AppNavigation.kt`: pass optional `fileId` query parameter into the reader route.
- Modify `androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/navigation/Routes.kt`: add `BookReader(contentId, fileId)` route builder.
- Modify `androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/reader/ReaderViewModel.kt`: load ebook detail, choose a version, build read URL, restore/save progress, and expose bookmark actions.
- Modify `androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/reader/ReaderScreen.kt`: render unsupported/sync states and bookmark action.
- Modify `androidApp/src/androidMain/kotlin/com/continuum/app/android/di/AndroidModule.kt`: inject `EbookReaderRepository` into `ReaderViewModel`.
- Create `androidTvApp/src/androidMain/kotlin/com/continuum/app/tv/ui/util/TvMediaTypeFilters.kt`: pure TV helpers for hiding ebooks and detecting audiobooks.
- Add tests under `androidTvApp/src/androidUnitTest/kotlin/com/continuum/app/tv/ui/util/TvMediaTypeFiltersTest.kt`.
- Modify `androidTvApp/src/androidMain/kotlin/com/continuum/app/tv/ui/screens/libraries/TvLibrariesViewModel.kt`: remove ebook libraries.
- Modify `androidTvApp/src/androidMain/kotlin/com/continuum/app/tv/ui/screens/library/TvLibraryDetailViewModel.kt`: support audiobook media type and filter ebook section/browse items.
- Modify `androidTvApp/src/androidMain/kotlin/com/continuum/app/tv/ui/screens/search/TvSearchViewModel.kt`: add audiobook search filter and filter ebooks from all results.
- Modify `androidTvApp/src/androidMain/kotlin/com/continuum/app/tv/ui/screens/detail/TvItemDetailViewModel.kt`: fail closed for ebook details and allow audiobook related queries.
- Modify `androidTvApp/src/androidMain/kotlin/com/continuum/app/tv/ui/screens/detail/TvDetailMetadata.kt`: add audiobook type labels and metadata tokens.
- Modify `androidTvApp/src/androidMain/kotlin/com/continuum/app/tv/ui/screens/detail/TvItemDetailScreen.kt`: hide video-only controls that do not apply to audiobooks and keep Play wired to selected `fileId`.
- Modify `androidTvApp/src/androidMain/kotlin/com/continuum/app/tv/ui/screens/libraries/TvLibrariesScreen.kt` and `androidTvApp/src/androidMain/kotlin/com/continuum/app/tv/ui/screens/library/TvLibraryDetailScreen.kt`: audiobook labels/icons.

---

### Task 1: Shared Audiobook and Ebook Contract Models

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/continuum/app/model/audiobook/AudiobookMetadata.kt`
- Create: `shared/src/commonMain/kotlin/com/continuum/app/model/ebook/EbookMetadata.kt`
- Modify: `shared/src/commonMain/kotlin/com/continuum/app/model/catalog/CatalogModels.kt`
- Test: `shared/src/commonTest/kotlin/com/continuum/app/model/catalog/MediaSurfaceContractSerializationTest.kt`

- [ ] **Step 1: Write failing serialization tests**

Create `shared/src/commonTest/kotlin/com/continuum/app/model/catalog/MediaSurfaceContractSerializationTest.kt`:

```kotlin
package com.continuum.app.model.catalog

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlinx.serialization.json.Json

class MediaSurfaceContractSerializationTest {
    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    @Test
    fun decodesCurrentAudiobookExtension() {
        val detail = json.decodeFromString<ItemDetail>(
            """
            {
              "content_id": "aud-1",
              "type": "audiobook",
              "title": "The Long Listen",
              "versions": [
                {
                  "file_id": 11,
                  "file_name": "long-listen.m4b",
                  "container": "m4b",
                  "duration": 3600,
                  "chapters": [
                    { "index": 0, "title": "Opening", "start_seconds": 0, "end_seconds": 120 }
                  ]
                }
              ],
              "audiobook": {
                "authors": [
                  { "person_id": "p1", "name": "Ada Author", "photo_url": "/img/ada.jpg", "photo_thumbhash": "abc" }
                ],
                "narrators": [
                  { "person_id": "p2", "name": "Nia Narrator" }
                ],
                "publisher": "Prairie Press",
                "total_duration_seconds": 3600,
                "series": {
                  "name": "Prairie Stories",
                  "entries": [
                    { "content_id": "aud-1", "title": "The Long Listen", "year": 2026, "poster_url": "/p.jpg", "series_index": 1.0 }
                  ]
                },
                "other_narrations": [
                  { "content_id": "aud-2", "title": "The Long Listen", "year": 2025, "narrators": ["Other Voice"] }
                ],
                "related": {
                  "also_by_author": [
                    { "content_id": "aud-3", "title": "Another Listen", "year": 2024 }
                  ],
                  "similar": [
                    { "content_id": "aud-4", "title": "Close Enough", "year": 2023 }
                  ]
                }
              }
            }
            """.trimIndent(),
        )

        assertEquals("audiobook", detail.type)
        assertNotNull(detail.audiobook)
        assertEquals("Ada Author", detail.audiobook?.authorNames)
        assertEquals("Nia Narrator", detail.audiobook?.narratorNames)
        assertEquals(3600, detail.audiobook?.totalDurationSeconds)
        assertEquals("Prairie Stories", detail.audiobook?.series?.name)
        assertEquals(11, detail.versions.single().fileId)
    }

    @Test
    fun decodesCurrentEbookExtensionWithoutLegacyBook() {
        val detail = json.decodeFromString<ItemDetail>(
            """
            {
              "content_id": "ebook-1",
              "type": "ebook",
              "title": "Readable Things",
              "versions": [
                { "file_id": 44, "file_name": "readable.epub", "container": "epub", "file_size": 1000 }
              ],
              "ebook": {
                "authors": [
                  { "person_id": "p7", "name": "Eve Writer" }
                ],
                "publisher": "Prairie Press",
                "series": {
                  "name": "Readable Set",
                  "entries": [
                    { "content_id": "ebook-1", "title": "Readable Things", "year": 2026, "series_index": 2.0 }
                  ]
                },
                "related": {
                  "also_by_author": [],
                  "similar": [
                    { "content_id": "ebook-2", "title": "More Readable Things", "year": 2026 }
                  ]
                }
              }
            }
            """.trimIndent(),
        )

        assertEquals("ebook", detail.type)
        assertNotNull(detail.ebook)
        assertNull(detail.book)
        assertEquals("Eve Writer", detail.ebook?.authorNames)
        assertEquals("Prairie Press", detail.ebook?.publisher)
        assertEquals(44, detail.versions.single().fileId)
    }
}
```

- [ ] **Step 2: Run the failing contract tests**

Run:

```bash
./gradlew :shared:commonTest --tests com.continuum.app.model.catalog.MediaSurfaceContractSerializationTest
```

Expected: FAIL because `ItemDetail.ebook`, current audiobook fields, and helper properties do not exist.

- [ ] **Step 3: Add ebook metadata models**

Create `shared/src/commonMain/kotlin/com/continuum/app/model/ebook/EbookMetadata.kt`:

```kotlin
package com.continuum.app.model.ebook

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class MediaPerson(
    @SerialName("person_id") val personId: String? = null,
    val name: String = "",
    @SerialName("photo_url") val photoUrl: String? = null,
    @SerialName("photo_thumbhash") val photoThumbhash: String? = null,
)

@Serializable
data class MediaRelatedItem(
    @SerialName("content_id") val contentId: String,
    val title: String,
    val year: Int? = null,
    @SerialName("poster_url") val posterUrl: String? = null,
    @SerialName("series_index") val seriesIndex: Double? = null,
)

@Serializable
data class MediaSeriesGroup(
    val name: String = "",
    val entries: List<MediaRelatedItem> = emptyList(),
)

@Serializable
data class MediaRelatedContent(
    @SerialName("also_by_author") val alsoByAuthor: List<MediaRelatedItem> = emptyList(),
    val similar: List<MediaRelatedItem> = emptyList(),
)

@Serializable
data class EbookMetadata(
    val authors: List<MediaPerson> = emptyList(),
    val publisher: String? = null,
    val series: MediaSeriesGroup? = null,
    val related: MediaRelatedContent = MediaRelatedContent(),
) {
    val authorNames: String?
        get() = authors.mapNotNull { it.name.takeIf(String::isNotBlank) }
            .joinToString(", ")
            .takeIf(String::isNotBlank)
}
```

- [ ] **Step 4: Replace stale audiobook metadata with current contract**

Modify `shared/src/commonMain/kotlin/com/continuum/app/model/audiobook/AudiobookMetadata.kt` so it contains:

```kotlin
package com.continuum.app.model.audiobook

import com.continuum.app.model.ebook.MediaPerson
import com.continuum.app.model.ebook.MediaRelatedContent
import com.continuum.app.model.ebook.MediaSeriesGroup
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class AudiobookNarration(
    @SerialName("content_id") val contentId: String,
    val title: String,
    val year: Int? = null,
    val narrators: List<String> = emptyList(),
)

@Serializable
data class AudiobookMetadata(
    val authors: List<MediaPerson> = emptyList(),
    val narrators: List<MediaPerson> = emptyList(),
    val publisher: String? = null,
    @SerialName("total_duration_seconds") val totalDurationSeconds: Int? = null,
    val series: MediaSeriesGroup? = null,
    @SerialName("other_narrations") val otherNarrations: List<AudiobookNarration> = emptyList(),
    val related: MediaRelatedContent = MediaRelatedContent(),
) {
    val authorNames: String?
        get() = authors.mapNotNull { it.name.takeIf(String::isNotBlank) }
            .joinToString(", ")
            .takeIf(String::isNotBlank)

    val narratorNames: String?
        get() = narrators.mapNotNull { it.name.takeIf(String::isNotBlank) }
            .joinToString(", ")
            .takeIf(String::isNotBlank)
}
```

Audiobook UI that imports `AudiobookChapter` must switch to `VersionChapter` from `CatalogModels.kt`; this plan removes `AudiobookChapter` with the stale metadata contract.

- [ ] **Step 5: Add `ItemDetail.ebook`**

Modify `shared/src/commonMain/kotlin/com/continuum/app/model/catalog/CatalogModels.kt`:

```kotlin
    val audiobook: com.continuum.app.model.audiobook.AudiobookMetadata? = null,
    /** Legacy fallback for older servers that emitted book-like metadata. */
    val book: com.continuum.app.model.book.BookMetadata? = null,
    /** Populated only when [type] is "ebook" on current servers. */
    val ebook: com.continuum.app.model.ebook.EbookMetadata? = null,
)
```

- [ ] **Step 6: Run the contract tests**

Run:

```bash
./gradlew :shared:commonTest --tests com.continuum.app.model.catalog.MediaSurfaceContractSerializationTest
```

Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add shared/src/commonMain/kotlin/com/continuum/app/model/audiobook/AudiobookMetadata.kt \
  shared/src/commonMain/kotlin/com/continuum/app/model/ebook/EbookMetadata.kt \
  shared/src/commonMain/kotlin/com/continuum/app/model/catalog/CatalogModels.kt \
  shared/src/commonTest/kotlin/com/continuum/app/model/catalog/MediaSurfaceContractSerializationTest.kt
git commit -m "feat: align media surface catalog models"
```

---

### Task 2: Ebook Reader API and Version Selection

**Files:**
- Create: `shared/src/commonMain/kotlin/com/continuum/app/model/ebook/EbookReaderModels.kt`
- Create: `shared/src/commonMain/kotlin/com/continuum/app/model/ebook/EbookVersionSelection.kt`
- Create: `shared/src/commonMain/kotlin/com/continuum/app/network/api/EbookReaderApi.kt`
- Create: `shared/src/commonMain/kotlin/com/continuum/app/repository/EbookReaderRepository.kt`
- Modify: `shared/src/commonMain/kotlin/com/continuum/app/di/NetworkModule.kt`
- Modify: `shared/src/commonMain/kotlin/com/continuum/app/di/RepositoryModule.kt`
- Test: `shared/src/commonTest/kotlin/com/continuum/app/model/ebook/EbookVersionSelectionTest.kt`

- [ ] **Step 1: Write failing version-selection tests**

Create `shared/src/commonTest/kotlin/com/continuum/app/model/ebook/EbookVersionSelectionTest.kt`:

```kotlin
package com.continuum.app.model.ebook

import com.continuum.app.model.catalog.FileVersion
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class EbookVersionSelectionTest {
    @Test
    fun choosesRequestedSupportedFileFirst() {
        val versions = listOf(
            FileVersion(fileId = 1, fileName = "book.pdf", container = "pdf"),
            FileVersion(fileId = 2, fileName = "book.epub", container = "epub"),
        )

        assertEquals(1, chooseEbookVersion(versions, requestedFileId = 1)?.fileId)
    }

    @Test
    fun prefersEpubThenPdfWhenNoRequest() {
        val versions = listOf(
            FileVersion(fileId = 1, fileName = "book.pdf", container = "pdf"),
            FileVersion(fileId = 2, fileName = "book.epub", container = "epub"),
        )

        assertEquals(2, chooseEbookVersion(versions, requestedFileId = null)?.fileId)
    }

    @Test
    fun fallsBackToSupportedContainerOrExtension() {
        val versions = listOf(
            FileVersion(fileId = 1, fileName = "notes.txt", container = "txt"),
            FileVersion(fileId = 2, fileName = "book.fb2.zip", container = null),
        )

        assertEquals(2, chooseEbookVersion(versions, requestedFileId = null)?.fileId)
    }

    @Test
    fun returnsNullWhenNothingReadable() {
        val versions = listOf(FileVersion(fileId = 1, fileName = "cover.jpg", container = "jpg"))

        assertNull(chooseEbookVersion(versions, requestedFileId = null))
    }
}
```

- [ ] **Step 2: Run the failing tests**

Run:

```bash
./gradlew :shared:commonTest --tests com.continuum.app.model.ebook.EbookVersionSelectionTest
```

Expected: FAIL because `chooseEbookVersion` does not exist.

- [ ] **Step 3: Add reader models**

Create `shared/src/commonMain/kotlin/com/continuum/app/model/ebook/EbookReaderModels.kt`:

```kotlin
package com.continuum.app.model.ebook

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

@Serializable
data class EbookReaderProgress(
    @SerialName("content_id") val contentId: String,
    @SerialName("file_id") val fileId: Int,
    val location: String,
    val progress: Double = 0.0,
    @SerialName("updated_at") val updatedAt: String? = null,
)

@Serializable
data class SaveEbookProgressRequest(
    @SerialName("file_id") val fileId: Int,
    val location: String,
    val progress: Double,
)

@Serializable
data class EbookReaderConfig(
    @SerialName("content_id") val contentId: String? = null,
    val config: JsonObject = JsonObject(emptyMap()),
    @SerialName("updated_at") val updatedAt: String? = null,
)

@Serializable
data class SaveEbookReaderConfigRequest(
    val config: JsonObject,
)

@Serializable
data class EbookAnnotationListResponse(
    val items: List<EbookAnnotation> = emptyList(),
)

@Serializable
data class EbookAnnotation(
    val id: String,
    @SerialName("content_id") val contentId: String,
    val kind: String,
    @SerialName("cfi_range") val cfiRange: String? = null,
    val location: String? = null,
    @SerialName("selected_text") val selectedText: String? = null,
    val note: String? = null,
    val style: String? = null,
    val color: String? = null,
    val metadata: JsonElement? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
)

@Serializable
data class SaveEbookAnnotationRequest(
    val kind: String,
    @SerialName("cfi_range") val cfiRange: String? = null,
    val location: String? = null,
    @SerialName("selected_text") val selectedText: String? = null,
    val note: String? = null,
    val style: String? = null,
    val color: String? = null,
    val metadata: JsonElement? = null,
)
```

- [ ] **Step 4: Add ebook version selection helper**

Create `shared/src/commonMain/kotlin/com/continuum/app/model/ebook/EbookVersionSelection.kt`:

```kotlin
package com.continuum.app.model.ebook

import com.continuum.app.model.catalog.FileVersion

private val supportedEbookExtensions = setOf(
    "epub",
    "pdf",
    "mobi",
    "azw",
    "azw3",
    "fb2",
    "fbz",
    "cbz",
    "cbr",
    "md",
)

private val preferredOrder = listOf("epub", "pdf", "cbz", "cbr", "fb2", "fbz", "mobi", "azw3", "azw", "md")

fun FileVersion.ebookFormatKey(): String? {
    val containerKey = container
        ?.trim()
        ?.lowercase()
        ?.removePrefix(".")
        ?.takeIf { it in supportedEbookExtensions }
    if (containerKey != null) return containerKey

    val name = fileName?.lowercase().orEmpty()
    if (name.endsWith(".fb2.zip")) return "fb2"
    return name.substringAfterLast('.', missingDelimiterValue = "")
        .takeIf { it in supportedEbookExtensions }
}

fun FileVersion.isSupportedEbookVersion(): Boolean = ebookFormatKey() != null

fun chooseEbookVersion(
    versions: List<FileVersion>,
    requestedFileId: Int?,
): FileVersion? {
    if (requestedFileId != null) {
        versions.firstOrNull { it.fileId == requestedFileId && it.isSupportedEbookVersion() }?.let { return it }
    }

    val supported = versions.filter { it.isSupportedEbookVersion() }
    if (supported.isEmpty()) return null

    return supported.minBy { version ->
        preferredOrder.indexOf(version.ebookFormatKey()).takeIf { it >= 0 } ?: Int.MAX_VALUE
    }
}
```

- [ ] **Step 5: Add API wrapper and repository**

Create `shared/src/commonMain/kotlin/com/continuum/app/network/api/EbookReaderApi.kt`:

```kotlin
package com.continuum.app.network.api

import com.continuum.app.model.ebook.EbookAnnotation
import com.continuum.app.model.ebook.EbookAnnotationListResponse
import com.continuum.app.model.ebook.EbookReaderConfig
import com.continuum.app.model.ebook.EbookReaderProgress
import com.continuum.app.model.ebook.SaveEbookAnnotationRequest
import com.continuum.app.model.ebook.SaveEbookProgressRequest
import com.continuum.app.model.ebook.SaveEbookReaderConfigRequest
import com.continuum.app.network.ApiResult
import io.ktor.client.HttpClient
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.http.encodeURLPathPart

class EbookReaderApi(private val client: HttpClient) {
    fun readPath(contentId: String, fileId: Int): String =
        "/api/v1/ebooks/${contentId.encodeURLPathPart()}/files/$fileId/read"

    suspend fun getProgress(contentId: String): ApiResult<EbookReaderProgress> = safeApiCall {
        client.get("/api/v1/ebooks/${contentId.encodeURLPathPart()}/progress")
    }

    suspend fun saveProgress(
        contentId: String,
        request: SaveEbookProgressRequest,
    ): ApiResult<EbookReaderProgress> = safeApiCall {
        client.put("/api/v1/ebooks/${contentId.encodeURLPathPart()}/progress") {
            setBody(request)
        }
    }

    suspend fun getReaderConfig(contentId: String): ApiResult<EbookReaderConfig> = safeApiCall {
        client.get("/api/v1/ebooks/${contentId.encodeURLPathPart()}/reader-config")
    }

    suspend fun saveReaderConfig(
        contentId: String,
        request: SaveEbookReaderConfigRequest,
    ): ApiResult<EbookReaderConfig> = safeApiCall {
        client.put("/api/v1/ebooks/${contentId.encodeURLPathPart()}/reader-config") {
            setBody(request)
        }
    }

    suspend fun listAnnotations(contentId: String): ApiResult<EbookAnnotationListResponse> = safeApiCall {
        client.get("/api/v1/ebooks/${contentId.encodeURLPathPart()}/annotations")
    }

    suspend fun createAnnotation(
        contentId: String,
        request: SaveEbookAnnotationRequest,
    ): ApiResult<EbookAnnotation> = safeApiCall {
        client.post("/api/v1/ebooks/${contentId.encodeURLPathPart()}/annotations") {
            setBody(request)
        }
    }

    suspend fun updateAnnotation(
        contentId: String,
        annotationId: String,
        request: SaveEbookAnnotationRequest,
    ): ApiResult<EbookAnnotation> = safeApiCall {
        client.patch("/api/v1/ebooks/${contentId.encodeURLPathPart()}/annotations/${annotationId.encodeURLPathPart()}") {
            setBody(request)
        }
    }

    suspend fun deleteAnnotation(contentId: String, annotationId: String): ApiResult<Unit> = safeApiCall {
        client.delete("/api/v1/ebooks/${contentId.encodeURLPathPart()}/annotations/${annotationId.encodeURLPathPart()}")
    }
}
```

Create `shared/src/commonMain/kotlin/com/continuum/app/repository/EbookReaderRepository.kt`:

```kotlin
package com.continuum.app.repository

import com.continuum.app.model.ebook.SaveEbookAnnotationRequest
import com.continuum.app.model.ebook.SaveEbookProgressRequest
import com.continuum.app.model.ebook.SaveEbookReaderConfigRequest
import com.continuum.app.network.api.EbookReaderApi

class EbookReaderRepository(private val api: EbookReaderApi) {
    fun readPath(contentId: String, fileId: Int): String =
        api.readPath(contentId, fileId)

    suspend fun getProgress(contentId: String) =
        api.getProgress(contentId)

    suspend fun saveProgress(contentId: String, request: SaveEbookProgressRequest) =
        api.saveProgress(contentId, request)

    suspend fun getReaderConfig(contentId: String) =
        api.getReaderConfig(contentId)

    suspend fun saveReaderConfig(contentId: String, request: SaveEbookReaderConfigRequest) =
        api.saveReaderConfig(contentId, request)

    suspend fun listAnnotations(contentId: String) =
        api.listAnnotations(contentId)

    suspend fun createBookmark(contentId: String, location: String) =
        api.createAnnotation(
            contentId = contentId,
            request = SaveEbookAnnotationRequest(kind = "bookmark", location = location),
        )

    suspend fun updateAnnotation(contentId: String, annotationId: String, request: SaveEbookAnnotationRequest) =
        api.updateAnnotation(contentId, annotationId, request)

    suspend fun deleteAnnotation(contentId: String, annotationId: String) =
        api.deleteAnnotation(contentId, annotationId)
}
```

- [ ] **Step 6: Register API and repository**

Modify `shared/src/commonMain/kotlin/com/continuum/app/di/NetworkModule.kt`:

```kotlin
    single { EbookReaderApi(get()) }
```

Modify `shared/src/commonMain/kotlin/com/continuum/app/di/RepositoryModule.kt` imports and module:

```kotlin
import com.continuum.app.repository.EbookReaderRepository
```

```kotlin
    single { EbookReaderRepository(get()) }
```

- [ ] **Step 7: Run shared tests**

Run:

```bash
./gradlew :shared:commonTest --tests com.continuum.app.model.ebook.EbookVersionSelectionTest
```

Expected: PASS.

Then run:

```bash
./gradlew :shared:commonTest
```

Expected: PASS.

- [ ] **Step 8: Commit**

```bash
git add shared/src/commonMain/kotlin/com/continuum/app/model/ebook \
  shared/src/commonMain/kotlin/com/continuum/app/network/api/EbookReaderApi.kt \
  shared/src/commonMain/kotlin/com/continuum/app/repository/EbookReaderRepository.kt \
  shared/src/commonMain/kotlin/com/continuum/app/di/NetworkModule.kt \
  shared/src/commonMain/kotlin/com/continuum/app/di/RepositoryModule.kt \
  shared/src/commonTest/kotlin/com/continuum/app/model/ebook/EbookVersionSelectionTest.kt
git commit -m "feat: add ebook reader API contract"
```

---

### Task 3: Mobile Ebook Reader Wiring

**Files:**
- Modify: `androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/navigation/Routes.kt`
- Modify: `androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/navigation/AppNavigation.kt`
- Modify: `androidApp/src/androidMain/kotlin/com/continuum/app/android/di/AndroidModule.kt`
- Modify: `androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/detail/ItemDetailScreen.kt`
- Modify: `androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/book/BookDetailContent.kt`
- Modify: `androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/reader/ReaderViewModel.kt`
- Modify: `androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/reader/ReaderScreen.kt`

- [ ] **Step 1: Add reader route file-id support**

Modify `androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/navigation/Routes.kt`:

```kotlin
data class BookReader(val contentId: String, val fileId: Int? = null) : Route(
    "reader/$contentId" + fileId?.let { "?fileId=$it" }.orEmpty(),
) {
    companion object {
        const val ROUTE = "reader/{contentId}?fileId={fileId}"
        const val ARG_CONTENT_ID = "contentId"
        const val ARG_FILE_ID = "fileId"
    }
}
```

Keep the existing route name if it is already nested inside a sealed class; only replace the `BookReader` declaration and companion constants.

- [ ] **Step 2: Pass the optional file id into reader navigation**

Modify the `BookReader` composable in `androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/navigation/AppNavigation.kt` so the nav argument exists:

```kotlin
composable(
    route = Route.BookReader.ROUTE,
    arguments = listOf(
        navArgument(Route.BookReader.ARG_CONTENT_ID) { type = NavType.StringType },
        navArgument(Route.BookReader.ARG_FILE_ID) {
            type = NavType.StringType
            nullable = true
            defaultValue = null
        },
    ),
) {
    ReaderScreen(onBackClick = { navController.popBackStack() })
}
```

- [ ] **Step 3: Inject ebook repository into reader view model**

Modify the `ReaderViewModel` Koin registration in `androidApp/src/androidMain/kotlin/com/continuum/app/android/di/AndroidModule.kt`:

```kotlin
viewModel { (handle: SavedStateHandle) ->
    ReaderViewModel(
        catalogRepository = get(),
        ebookReaderRepository = get(),
        savedStateHandle = handle,
    )
}
```

The current local registration is `viewModel { ReaderViewModel(get(), get()) }`; replace that exact line with the constructor above.

- [ ] **Step 4: Update mobile detail routing for ebooks**

In `androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/detail/ItemDetailScreen.kt`, route `ebook` to `BookDetailContent` and pass a read callback:

```kotlin
"ebook" -> {
    BookDetailContent(
        detail = detail,
        onReadClick = { fileId ->
            onReadBook(detail.contentId, fileId)
        },
    )
}
```

Keep legacy `"book"`, `"comic"`, and `"manga"` behavior only if the current app still needs it for older servers.

- [ ] **Step 5: Choose ebook version in detail read action**

Modify `androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/book/BookDetailContent.kt` to select from `versions`:

```kotlin
import com.continuum.app.model.ebook.chooseEbookVersion
```

```kotlin
val selectedVersion = remember(detail.versions) {
    chooseEbookVersion(detail.versions, requestedFileId = null)
}

Button(
    onClick = { selectedVersion?.let { onReadClick(it.fileId) } },
    enabled = selectedVersion != null,
) {
    Text(if (selectedVersion == null) "Unsupported format" else "Read")
}
```

Set the callback signature to:

```kotlin
onReadClick: (fileId: Int?) -> Unit
```

- [ ] **Step 6: Update `ReaderViewModel` for current ebook APIs**

Modify `androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/reader/ReaderViewModel.kt`:

```kotlin
class ReaderViewModel(
    private val catalogRepository: CatalogRepository,
    private val ebookReaderRepository: EbookReaderRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {
    private val contentId: String = savedStateHandle.get<String>("contentId") ?: ""
    private val requestedFileId: Int? = savedStateHandle.get<String>("fileId")?.toIntOrNull()
```

Update `ReaderUiState`:

```kotlin
data class ReaderUiState(
    val isLoading: Boolean = true,
    val title: String = "",
    val author: String? = null,
    val format: BookFormat = BookFormat.Unknown,
    val fileUrl: String? = null,
    val fileId: Int? = null,
    val pageCount: Int? = null,
    val currentPage: Int = 0,
    val progressLocation: String? = null,
    val progressPercent: Double = 0.0,
    val bookmarks: List<EbookAnnotation> = emptyList(),
    val isSyncing: Boolean = false,
    val syncError: String? = null,
    val error: String? = null,
)
```

Replace detail loading with:

```kotlin
private fun loadDetail() {
    viewModelScope.launch {
        when (val r = catalogRepository.getItemDetail(contentId)) {
            is ApiResult.Success -> {
                val d = r.data
                val version = chooseEbookVersion(d.versions, requestedFileId)
                if (version == null) {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            title = d.title,
                            author = d.ebook?.authorNames ?: d.book?.author,
                            error = "No supported ebook file is available.",
                        )
                    }
                    return@launch
                }
                val format = BookFormat.fromPath(version.fileName)
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        title = d.title,
                        author = d.ebook?.authorNames ?: d.book?.author,
                        format = format,
                        fileUrl = ebookReaderRepository.readPath(d.contentId, version.fileId),
                        fileId = version.fileId,
                        pageCount = d.book?.pageCount,
                    )
                }
                loadReaderState(version.fileId)
            }
            is ApiResult.Error -> _uiState.update { it.copy(isLoading = false, error = r.message) }
            is ApiResult.NetworkError -> _uiState.update { it.copy(isLoading = false, error = r.exception.message) }
        }
    }
}

private fun loadReaderState(fileId: Int) {
    viewModelScope.launch {
        when (val progress = ebookReaderRepository.getProgress(contentId)) {
            is ApiResult.Success -> {
                if (progress.data.fileId == fileId) {
                    _uiState.update {
                        it.copy(
                            progressLocation = progress.data.location,
                            progressPercent = progress.data.progress.coerceIn(0.0, 1.0),
                        )
                    }
                }
            }
            else -> Unit
        }

        when (val annotations = ebookReaderRepository.listAnnotations(contentId)) {
            is ApiResult.Success -> _uiState.update {
                it.copy(bookmarks = annotations.data.items.filter { annotation -> annotation.kind == "bookmark" })
            }
            else -> Unit
        }
    }
}
```

Update page changes and bookmark action:

```kotlin
fun onPageChanged(page: Int) {
    val fileId = _uiState.value.fileId ?: return
    val location = "page:$page"
    _uiState.update {
        it.copy(
            currentPage = page.coerceAtLeast(0),
            progressLocation = location,
            progressPercent = page.coerceAtLeast(0) / 100.0,
        )
    }
    viewModelScope.launch {
        _uiState.update { it.copy(isSyncing = true, syncError = null) }
        when (val result = ebookReaderRepository.saveProgress(
            contentId,
            SaveEbookProgressRequest(
                fileId = fileId,
                location = location,
                progress = _uiState.value.progressPercent.coerceIn(0.0, 1.0),
            ),
        )) {
            is ApiResult.Success -> _uiState.update { it.copy(isSyncing = false) }
            else -> _uiState.update { it.copy(isSyncing = false, syncError = "Reading progress could not sync.") }
        }
    }
}

fun addBookmark() {
    val location = _uiState.value.progressLocation ?: "page:${_uiState.value.currentPage}"
    viewModelScope.launch {
        when (val result = ebookReaderRepository.createBookmark(contentId, location)) {
            is ApiResult.Success -> _uiState.update { it.copy(bookmarks = it.bookmarks + result.data, syncError = null) }
            else -> _uiState.update { it.copy(syncError = "Bookmark could not sync.") }
        }
    }
}
```

- [ ] **Step 7: Add reader UI sync/bookmark affordance**

Modify `androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/reader/ReaderScreen.kt` top row:

```kotlin
IconButton(onClick = viewModel::addBookmark, enabled = state.fileId != null) {
    Icon(Icons.Default.BookmarkAdd, contentDescription = "Add bookmark")
}
```

Below the top row, show non-blocking sync state:

```kotlin
state.syncError != null -> Text(
    text = state.syncError ?: "",
    color = MaterialTheme.colorScheme.error,
    modifier = Modifier.padding(horizontal = 16.dp),
)
state.isSyncing -> Text(
    text = "Syncing reading progress",
    color = MaterialTheme.colorScheme.onSurfaceVariant,
    modifier = Modifier.padding(horizontal = 16.dp),
)
```

Keep existing EPUB/PDF/Comic dispatch, but it now receives the server read path from `state.fileUrl`.

- [ ] **Step 8: Compile mobile app**

Run:

```bash
./gradlew :androidApp:compileDebugKotlinAndroid
```

Expected: PASS.

- [ ] **Step 9: Commit**

```bash
git add androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/navigation/Routes.kt \
  androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/navigation/AppNavigation.kt \
  androidApp/src/androidMain/kotlin/com/continuum/app/android/di/AndroidModule.kt \
  androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/detail/ItemDetailScreen.kt \
  androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/book/BookDetailContent.kt \
  androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/reader/ReaderViewModel.kt \
  androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/reader/ReaderScreen.kt
git commit -m "feat: wire mobile ebook reader state"
```

---

### Task 4: Mobile Audiobook Version-Based Playback

**Files:**
- Modify: `androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/audiobook/AudiobookDetailContent.kt`
- Modify: `androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/audiobook/AudiobookPlayerViewModel.kt`
- Modify: `androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/audiobook/AudiobookPlayerScreen.kt`
- Modify: `androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/detail/ItemDetailScreen.kt`

- [ ] **Step 1: Replace old metadata reads in detail UI**

In `AudiobookDetailContent.kt`, replace old `meta.author`, `meta.narrator`, `meta.durationSeconds`, `meta.fileUrl`, and `meta.chapters` usage with:

```kotlin
val meta = detail.audiobook
val authorText = meta?.authorNames
val narratorText = meta?.narratorNames
val durationSeconds = meta?.totalDurationSeconds
    ?: detail.versions.maxOfOrNull { it.duration.toInt() }
val chapters = detail.versions.firstOrNull()?.chapters.orEmpty()
val playableVersion = detail.versions.firstOrNull()
```

Wire the Play button:

```kotlin
Button(
    onClick = { onPlayClick(playableVersion?.fileId) },
    enabled = playableVersion != null,
) {
    Text(if (playableVersion == null) "Unavailable" else "Play")
}
```

Set the callback signature to:

```kotlin
onPlayClick: (fileId: Int?) -> Unit
```

- [ ] **Step 2: Navigate audiobook play with selected file id**

In `ItemDetailScreen.kt`, update the audiobook branch:

```kotlin
AudiobookDetailContent(
    detail = detail,
    onPlayClick = { fileId ->
        onPlay(detail.contentId, fileId)
    },
)
```

- [ ] **Step 3: Add `fileId` to the audiobook player route**

Modify `Routes.kt`:

```kotlin
data class AudiobookPlayer(val contentId: String, val fileId: Int? = null) : Route(
    "audiobook/$contentId" + fileId?.let { "?fileId=$it" }.orEmpty(),
) {
    companion object {
        const val ROUTE = "audiobook/{contentId}?fileId={fileId}"
        const val ARG_CONTENT_ID = "contentId"
        const val ARG_FILE_ID = "fileId"
    }
}
```

Modify the audiobook composable in `AppNavigation.kt`:

```kotlin
composable(
    route = Route.AudiobookPlayer.ROUTE,
    arguments = listOf(
        navArgument(Route.AudiobookPlayer.ARG_CONTENT_ID) { type = NavType.StringType },
        navArgument(Route.AudiobookPlayer.ARG_FILE_ID) {
            type = NavType.StringType
            nullable = true
            defaultValue = null
        },
    ),
) {
    AudiobookPlayerScreen(onBackClick = { navController.popBackStack() })
}
```

- [ ] **Step 4: Update player view model to derive chapters and duration from versions**

In `AudiobookPlayerViewModel.kt`, replace `AudiobookChapter` and stale `AudiobookMetadata.fileUrl` usage with `VersionChapter` and selected `FileVersion`:

```kotlin
private val requestedFileId: Int? = savedStateHandle.get<String>("fileId")?.toIntOrNull()

val selectedVersion = d.versions.firstOrNull { it.fileId == requestedFileId }
    ?: d.versions.firstOrNull()

if (selectedVersion == null) {
    _uiState.update { it.copy(isLoading = false, error = "No playable audiobook file is available.") }
    return@launch
}

_uiState.update {
    it.copy(
        isLoading = false,
        title = d.title,
        author = d.audiobook?.authorNames,
        narrator = d.audiobook?.narratorNames,
        durationSeconds = d.audiobook?.totalDurationSeconds ?: selectedVersion.duration.toInt(),
        chapters = selectedVersion.chapters.orEmpty(),
        selectedFileId = selectedVersion.fileId,
    )
}
```

Update `AudiobookPlayerUiState` so chapters use `VersionChapter` and the selected file is tracked:

```kotlin
val chapters: List<VersionChapter> = emptyList(),
val selectedFileId: Int? = null,
```

Replace `jumpToChapter(chapter: AudiobookChapter)` with:

```kotlin
fun jumpToChapter(chapter: VersionChapter) {
    seekTo(chapter.startSeconds)
}
```

- [ ] **Step 5: Start playback through the server playback session**

Add `PlaybackSessionManager` and `PlaybackCapabilityDetector` to `AudiobookPlayerViewModel` constructor and Koin registration:

```kotlin
class AudiobookPlayerViewModel(
    private val catalogRepository: CatalogRepository,
    private val playbackSessionManager: PlaybackSessionManager,
    private val capabilityDetector: PlaybackCapabilityDetector,
    private val bookmarksStore: AudiobookBookmarksStore,
    private val positionStore: AudiobookPositionStore,
    private val serverRegistry: ServerRegistry,
    private val profileRepository: ProfileRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel()
```

```kotlin
playbackSessionManager = get(),
capabilityDetector = get(),
```

After selecting the version, call the same playback session manager used by `PlayerViewModel` with the selected `fileId`. Set `streamUrl` from the returned session stream URL:

```kotlin
val profileId = profileRepository.getActiveProfileId()
if (profileId == null) {
    _uiState.update { it.copy(error = "No active profile") }
    return@launch
}

val capabilities = capabilityDetector.detect()
when (val playback = playbackSessionManager.startSession(
    fileId = selectedVersion.fileId,
    profileId = profileId,
    capabilities = capabilities,
    startPosition = _resumePosition.value ?: 0.0,
)) {
    is ApiResult.Success -> _uiState.update { it.copy(streamUrl = playback.data.streamUrl) }
    is ApiResult.Error -> _uiState.update { it.copy(error = playback.message.ifBlank { "Audiobook playback failed" }) }
    is ApiResult.NetworkError -> _uiState.update { it.copy(error = playback.exception.message ?: "Network error") }
}
```

- [ ] **Step 6: Compile mobile app**

Run:

```bash
./gradlew :androidApp:compileDebugKotlinAndroid
```

Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/audiobook/AudiobookDetailContent.kt \
  androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/audiobook/AudiobookPlayerViewModel.kt \
  androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/audiobook/AudiobookPlayerScreen.kt \
  androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/detail/ItemDetailScreen.kt \
  androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/navigation/Routes.kt \
  androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/navigation/AppNavigation.kt
git commit -m "feat: play mobile audiobooks from versions"
```

---

### Task 5: Android TV Ebook Hiding Helpers

**Files:**
- Create: `androidTvApp/src/androidMain/kotlin/com/continuum/app/tv/ui/util/TvMediaTypeFilters.kt`
- Test: `androidTvApp/src/androidUnitTest/kotlin/com/continuum/app/tv/ui/util/TvMediaTypeFiltersTest.kt`

- [ ] **Step 1: Write failing TV filter tests**

Create `androidTvApp/src/androidUnitTest/kotlin/com/continuum/app/tv/ui/util/TvMediaTypeFiltersTest.kt`:

```kotlin
package com.continuum.app.tv.ui.util

import com.continuum.app.model.catalog.BrowseItem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TvMediaTypeFiltersTest {
    @Test
    fun identifiesHiddenEbookTypes() {
        assertTrue(isTvHiddenMediaType("ebook"))
        assertTrue(isTvHiddenMediaType("ebooks"))
        assertFalse(isTvHiddenMediaType("audiobook"))
        assertFalse(isTvHiddenMediaType("movie"))
    }

    @Test
    fun filtersBrowseItemsForTv() {
        val items = listOf(
            BrowseItem(contentId = "m1", type = "movie", title = "Movie"),
            BrowseItem(contentId = "e1", type = "ebook", title = "Book"),
            BrowseItem(contentId = "a1", type = "audiobook", title = "Audio"),
        )

        assertEquals(listOf("m1", "a1"), items.visibleOnTv().map { it.contentId })
    }

    @Test
    fun mapsTvLibraryTypeToCatalogMediaType() {
        assertEquals("series", tvCatalogMediaTypeFor("shows"))
        assertEquals("audiobook", tvCatalogMediaTypeFor("audiobooks"))
        assertEquals("movie", tvCatalogMediaTypeFor("movies"))
        assertEquals("movie", tvCatalogMediaTypeFor("ebooks"))
    }
}
```

- [ ] **Step 2: Run failing tests**

Run:

```bash
./gradlew :androidTvApp:testDebugUnitTest --tests com.continuum.app.tv.ui.util.TvMediaTypeFiltersTest
```

Expected: FAIL because the helper file does not exist.

- [ ] **Step 3: Add TV filter helpers**

Create `androidTvApp/src/androidMain/kotlin/com/continuum/app/tv/ui/util/TvMediaTypeFilters.kt`:

```kotlin
package com.continuum.app.tv.ui.util

import com.continuum.app.model.catalog.BrowseItem
import com.continuum.app.model.personal.UserLibrary
import com.continuum.app.model.section.ResolvedSection

fun isTvHiddenMediaType(type: String?): Boolean =
    type?.lowercase() in setOf("ebook", "ebooks")

fun isAudiobookMediaType(type: String?): Boolean =
    type?.lowercase() in setOf("audiobook", "audiobooks")

fun Iterable<BrowseItem>.visibleOnTv(): List<BrowseItem> =
    filterNot { isTvHiddenMediaType(it.type) }

fun Iterable<UserLibrary>.visibleOnTv(): List<UserLibrary> =
    filterNot { isTvHiddenMediaType(it.type) }

fun Iterable<ResolvedSection>.visibleOnTv(): List<ResolvedSection> =
    mapNotNull { section ->
        val visibleItems = section.items.filterNot { isTvHiddenMediaType(it.type) }
        section.takeIf { visibleItems.isNotEmpty() }?.copy(items = visibleItems)
    }

fun tvCatalogMediaTypeFor(type: String): String = when (type.lowercase()) {
    "series", "shows", "tv" -> "series"
    "audiobook", "audiobooks" -> "audiobook"
    else -> "movie"
}
```

`UserLibrary.type` exists in `shared/src/commonMain/kotlin/com/continuum/app/model/personal/PersonalDataModels.kt`, so no model adaptation is needed for this helper.

- [ ] **Step 4: Run TV filter tests**

Run:

```bash
./gradlew :androidTvApp:testDebugUnitTest --tests com.continuum.app.tv.ui.util.TvMediaTypeFiltersTest
```

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add androidTvApp/src/androidMain/kotlin/com/continuum/app/tv/ui/util/TvMediaTypeFilters.kt \
  androidTvApp/src/androidUnitTest/kotlin/com/continuum/app/tv/ui/util/TvMediaTypeFiltersTest.kt
git commit -m "feat: add tv media type filters"
```

---

### Task 6: Android TV Libraries, Search, and Detail Behavior

**Files:**
- Modify: `androidTvApp/src/androidMain/kotlin/com/continuum/app/tv/ui/screens/libraries/TvLibrariesViewModel.kt`
- Modify: `androidTvApp/src/androidMain/kotlin/com/continuum/app/tv/ui/screens/library/TvLibraryDetailViewModel.kt`
- Modify: `androidTvApp/src/androidMain/kotlin/com/continuum/app/tv/ui/screens/search/TvSearchViewModel.kt`
- Modify: `androidTvApp/src/androidMain/kotlin/com/continuum/app/tv/ui/screens/detail/TvItemDetailViewModel.kt`
- Modify: `androidTvApp/src/androidMain/kotlin/com/continuum/app/tv/ui/screens/detail/TvDetailMetadata.kt`
- Modify: `androidTvApp/src/androidMain/kotlin/com/continuum/app/tv/ui/screens/detail/TvItemDetailScreen.kt`
- Modify: `androidTvApp/src/androidMain/kotlin/com/continuum/app/tv/ui/screens/libraries/TvLibrariesScreen.kt`
- Modify: `androidTvApp/src/androidMain/kotlin/com/continuum/app/tv/ui/screens/library/TvLibraryDetailScreen.kt`

- [ ] **Step 1: Hide ebook libraries**

Modify `TvLibrariesViewModel.kt`:

```kotlin
import com.continuum.app.tv.ui.util.visibleOnTv
```

Replace:

```kotlin
val libraries = result.data.sortedBy { lib -> lib.sortOrder }
```

with:

```kotlin
val libraries = result.data
    .visibleOnTv()
    .sortedBy { lib -> lib.sortOrder }
```

- [ ] **Step 2: Support audiobook library browsing and filter ebook sections**

Modify `TvLibraryDetailViewModel.kt` imports:

```kotlin
import com.continuum.app.tv.ui.util.tvCatalogMediaTypeFor
import com.continuum.app.tv.ui.util.visibleOnTv
```

When storing recommended sections:

```kotlin
sections = resolved.visibleOnTv()
```

When storing browse items:

```kotlin
browseItems = if (reset) response.items.visibleOnTv() else it.browseItems + response.items.visibleOnTv()
```

Replace the private `mediaTypeFor` function with:

```kotlin
private fun mediaTypeFor(type: String): String = tvCatalogMediaTypeFor(type)
```

- [ ] **Step 3: Add audiobook to TV search and filter ebooks**

Modify `TvSearchViewModel.kt`:

```kotlin
enum class TvSearchMediaType(val label: String, val wire: String?) {
    All("All", null),
    Movies("Movies", "movie"),
    Series("Series", "series"),
    Audiobooks("Audiobooks", "audiobook"),
}
```

Import:

```kotlin
import com.continuum.app.tv.ui.util.visibleOnTv
```

When storing search results:

```kotlin
val visibleItems = response.items.visibleOnTv()
_uiState.update {
    it.copy(
        isLoading = false,
        isLoadingMore = false,
        items = if (reset) visibleItems else it.items + visibleItems,
        total = if (requestedMediaType == TvSearchMediaType.All) visibleItems.size else response.total,
        hasMore = response.hasMore,
        error = null,
    )
}
```

- [ ] **Step 4: Fail closed for TV ebook detail and include audiobook related browsing**

Modify `TvItemDetailViewModel.kt`:

```kotlin
import com.continuum.app.tv.ui.util.isTvHiddenMediaType
```

After loading detail:

```kotlin
if (isTvHiddenMediaType(detail.type)) {
    _uiState.update {
        it.copy(
            isLoading = false,
            detail = null,
            error = "This title is not available on Android TV.",
        )
    }
    return@launch
}
```

Update related query media type:

```kotlin
val mediaType = detail.type.takeIf { it in setOf("movie", "series", "episode", "audiobook") }
```

- [ ] **Step 5: Add TV audiobook labels**

Modify `TvDetailMetadata.kt` type label logic:

```kotlin
private fun typeLabel(detail: ItemDetail): String = when (detail.type.lowercase()) {
    "movie" -> "Movie"
    "series" -> "Series"
    "season" -> "Season"
    "episode" -> "Episode"
    "audiobook" -> "Audiobook"
    else -> detail.type.replaceFirstChar { it.titlecase() }
}
```

Add audiobook source tokens:

```kotlin
"audiobook" -> listOfNotNull(
    detail.audiobook?.publisher,
    detail.audiobook?.narratorNames?.let { "Narrated by $it" },
)
```

- [ ] **Step 6: Keep TV audiobook play action but hide video-only shelves**

In `TvItemDetailScreen.kt`, introduce:

```kotlin
val isAudiobook = detail.type.equals("audiobook", ignoreCase = true)
```

Use it to skip video-only sections:

```kotlin
if (!isAudiobook) {
    TvCastCrewSection(...)
}
```

Keep the main Play button enabled when `detail.versions` has at least one file:

```kotlin
val selectedFileId = state.selectedFileId ?: detail.versions.firstOrNull()?.fileId
onPlay(detail.contentId, selectedFileId)
```

- [ ] **Step 7: Add audiobook labels/icons in library screens**

In `TvLibrariesScreen.kt` and `TvLibraryDetailScreen.kt`, add audiobook branches to local label/icon helpers:

```kotlin
"audiobook", "audiobooks" -> "Audiobooks"
```

Use the existing headphones or music icon if available:

```kotlin
"audiobook", "audiobooks" -> Icons.Default.Headphones
```

- [ ] **Step 8: Compile and test TV app**

Run:

```bash
./gradlew :androidTvApp:testDebugUnitTest :androidTvApp:compileDebugKotlinAndroid
```

Expected: PASS.

- [ ] **Step 9: Commit**

```bash
git add androidTvApp/src/androidMain/kotlin/com/continuum/app/tv/ui/screens/libraries/TvLibrariesViewModel.kt \
  androidTvApp/src/androidMain/kotlin/com/continuum/app/tv/ui/screens/library/TvLibraryDetailViewModel.kt \
  androidTvApp/src/androidMain/kotlin/com/continuum/app/tv/ui/screens/search/TvSearchViewModel.kt \
  androidTvApp/src/androidMain/kotlin/com/continuum/app/tv/ui/screens/detail/TvItemDetailViewModel.kt \
  androidTvApp/src/androidMain/kotlin/com/continuum/app/tv/ui/screens/detail/TvDetailMetadata.kt \
  androidTvApp/src/androidMain/kotlin/com/continuum/app/tv/ui/screens/detail/TvItemDetailScreen.kt \
  androidTvApp/src/androidMain/kotlin/com/continuum/app/tv/ui/screens/libraries/TvLibrariesScreen.kt \
  androidTvApp/src/androidMain/kotlin/com/continuum/app/tv/ui/screens/library/TvLibraryDetailScreen.kt
git commit -m "feat: support tv audiobooks and hide ebooks"
```

---

### Task 7: Final Verification

**Files:**
- Verify all modified files.

- [ ] **Step 1: Run shared tests**

```bash
./gradlew :shared:commonTest
```

Expected: PASS.

- [ ] **Step 2: Run mobile compile**

```bash
./gradlew :androidApp:compileDebugKotlinAndroid
```

Expected: PASS.

- [ ] **Step 3: Run TV tests and compile**

```bash
./gradlew :androidTvApp:testDebugUnitTest :androidTvApp:compileDebugKotlinAndroid
```

Expected: PASS.

- [ ] **Step 4: Run repository status check**

```bash
git status --short --branch
```

Expected: branch `feature/android-parity-and-media-surfaces` with a clean worktree after all task commits.

- [ ] **Step 5: Manual smoke test against `root@silo-new` server**

Use a debug build pointed at the Prairie server that matches `root@silo-new:/opt/prairie-server`.

Verify:

- Android mobile audiobook detail shows author/narrator metadata and plays from a selected version.
- Android mobile ebook detail opens the reader, streams through `/api/v1/ebooks/{content_id}/files/{file_id}/read`, saves progress, and creates a bookmark.
- Android TV shows audiobook libraries/search results/detail and can launch playback.
- Android TV does not show ebook libraries, ebook search results, ebook rails, or ebook detail screens.

- [ ] **Step 6: Final commit if verification required fixes**

If verification required code changes:

```bash
git add <changed-files>
git commit -m "fix: stabilize android media surfaces"
```

If verification required no code changes, do not create an empty commit.
