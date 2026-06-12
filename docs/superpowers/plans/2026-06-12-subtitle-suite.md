# Subtitle Suite Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Subtitle provider search/download and AI translation/transcription in BOTH Android video players, per docs/superpowers/specs/2026-06-12-subtitle-suite-design.md.

**Architecture:** Shared KMP layer (wire models verified against the Go server source, SubtitlesApi, SubtitlesRepository with a device-login-style job poll loop, and a shared pure track-merge function) consumed by mobile bottom sheets and TV D-pad dialogs. Track refresh mirrors the web reference: refetch the per-file subtitle list and merge into the live session track list — no playback restart.

**Tech Stack:** Kotlin Multiplatform, Ktor, kotlinx.serialization, Koin, Jetpack Compose + TV Compose, Media3.

**Sections:** S = shared (S1-S4), M = mobile (M1-M4), T = TV (T1-T3). Order: S, then M, then T. Mobile/TV tasks carry "Dependencies" notes with the ASSUMED shared signatures — executors must verify against what actually landed and adapt.

---

## Section S: Shared layer (models, API, repository, merge)

### Task S1: Subtitle suite wire models + serialization tests

**Files:**
- Create: `shared/src/commonMain/kotlin/com/continuum/app/model/subtitles/SubtitleModels.kt`
- Test: `shared/src/commonTest/kotlin/com/continuum/app/model/subtitles/SubtitleModelsSerializationTest.kt`

Wire shapes verified against silo-server `main`: `internal/subtitles/types.go` (SubtitleResult `id` is a **string**, `score` float64, `upload_date` time.Time `omitempty`; DownloadedSubtitle `id` is **int**), `internal/subtitles/ai/job.go` (Job `id` is **int64**, `result_subtitle_id *int`, `error_message omitempty`), `internal/subtitles/ai/quota.go` (QuotaStatus zero-value `{"limited":false,...,"period":""}` for exempt callers), and `internal/api/handlers/subtitle_search.go` / `subtitle_ai.go` envelopes (`{"subtitle":…}`, `{"subtitles":…}`, `{"job":…}`, `{"jobs":…}`; search and quota responses are **unenveloped**). Request models live in the same models file, matching `model/playback/PlaybackModels.kt` (`StartPlaybackRequest` lives beside response models).

- [ ] **Step 1: Write the failing test**

```kotlin
// shared/src/commonTest/kotlin/com/continuum/app/model/subtitles/SubtitleModelsSerializationTest.kt
package com.continuum.app.model.subtitles

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SubtitleModelsSerializationTest {

    // Mirrors ContinuumJson (network/ContinuumHttpClientImpl.kt) so decode
    // behavior in tests matches production wire handling exactly.
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
        explicitNulls = false
        coerceInputValues = true
    }

    @Test
    fun `decodes search response with optional fields present and absent`() {
        val payload = """
            {
              "results": [
                {
                  "id": "os-9182736",
                  "provider": "opensubtitles",
                  "language": "en",
                  "release_name": "Dune.Part.Three.2026.2160p.WEB-DL.DDP5.1-FLUX",
                  "format": "srt",
                  "score": 91.5,
                  "downloads": 48210,
                  "hearing_impaired": false,
                  "upload_date": "2026-05-30T14:21:07Z"
                },
                {
                  "id": "sdl-555",
                  "provider": "subdl",
                  "language": "nl",
                  "release_name": "Dune Part Three WEBRip",
                  "format": "ass",
                  "score": 38,
                  "downloads": 112,
                  "hearing_impaired": true
                }
              ],
              "warnings": ["subsource: rate limited"]
            }
        """.trimIndent()

        val response = json.decodeFromString(SubtitleSearchResponse.serializer(), payload)

        assertEquals(2, response.results.size)
        val first = response.results[0]
        assertEquals("os-9182736", first.id)
        assertEquals(SubtitleProvider.OpenSubtitles, first.provider)
        assertEquals("en", first.language)
        assertEquals("Dune.Part.Three.2026.2160p.WEB-DL.DDP5.1-FLUX", first.releaseName)
        assertEquals("srt", first.format)
        assertEquals(91.5, first.score)
        assertEquals(48210, first.downloads)
        assertFalse(first.hearingImpaired)
        assertEquals("2026-05-30T14:21:07Z", first.uploadDate)

        val second = response.results[1]
        assertEquals(SubtitleProvider.Subdl, second.provider)
        assertEquals(38.0, second.score)  // integer score on the wire
        assertTrue(second.hearingImpaired)
        assertNull(second.uploadDate)     // upload_date absent

        assertEquals(listOf("subsource: rate limited"), response.warnings)
    }

    @Test
    fun `decodes search response without warnings`() {
        val response = json.decodeFromString(
            SubtitleSearchResponse.serializer(),
            """{"results": []}""",
        )
        assertTrue(response.results.isEmpty())
        assertTrue(response.warnings.isEmpty())
    }

    @Test
    fun `decodes download response envelope`() {
        val payload = """
            {
              "subtitle": {
                "id": 312,
                "media_file_id": 1048,
                "provider": "opensubtitles",
                "language": "en",
                "format": "srt",
                "release_name": "Dune.Part.Three.2026.2160p.WEB-DL.DDP5.1-FLUX",
                "score": 91.5,
                "hearing_impaired": false,
                "created_at": "2026-06-12T09:30:00Z"
              }
            }
        """.trimIndent()

        val sub = json.decodeFromString(SubtitleDownloadResponse.serializer(), payload).subtitle

        assertEquals(312, sub.id)
        assertEquals(1048, sub.mediaFileId)
        assertEquals("opensubtitles", sub.provider)
        assertEquals("en", sub.language)
        assertEquals("srt", sub.format)
        assertEquals("Dune.Part.Three.2026.2160p.WEB-DL.DDP5.1-FLUX", sub.releaseName)
        assertEquals(91.5, sub.score)
        assertFalse(sub.hearingImpaired)
        assertEquals("2026-06-12T09:30:00Z", sub.createdAt)
    }

    @Test
    fun `decodes subtitles list with null array from Go nil slice`() {
        // Go marshals a nil slice as JSON null; coerceInputValues maps it to the default.
        val response = json.decodeFromString(
            DownloadedSubtitlesResponse.serializer(),
            """{"subtitles": null}""",
        )
        assertTrue(response.subtitles.isEmpty())
    }

    @Test
    fun `decodes ai status both shapes`() {
        val on = json.decodeFromString(
            SubtitleAiStatus.serializer(),
            """{"enabled": true, "transcribe_enabled": false}""",
        )
        assertTrue(on.enabled)
        assertFalse(on.transcribeEnabled)

        // Disabled probe shape from handlers.WriteSubtitleAIDisabledStatus.
        val off = json.decodeFromString(
            SubtitleAiStatus.serializer(),
            """{"enabled": false, "transcribe_enabled": false}""",
        )
        assertFalse(off.enabled)
        assertFalse(off.transcribeEnabled)
    }

    @Test
    fun `decodes quota limited and exempt zero-value shapes`() {
        val limited = json.decodeFromString(
            SubtitleAiQuota.serializer(),
            """{"limited": true, "limit": 5, "used": 3, "remaining": 2, "period": "week"}""",
        )
        assertTrue(limited.limited)
        assertEquals(5, limited.limit)
        assertEquals(3, limited.used)
        assertEquals(2, limited.remaining)
        assertEquals("week", limited.period)

        // Exempt/admin callers get the Go zero value.
        val exempt = json.decodeFromString(
            SubtitleAiQuota.serializer(),
            """{"limited": false, "limit": 0, "used": 0, "remaining": 0, "period": ""}""",
        )
        assertFalse(exempt.limited)
        assertEquals("", exempt.period)
    }

    @Test
    fun `decodes pending job envelope with null result id and absent error`() {
        val payload = """
            {
              "job": {
                "id": 91,
                "media_file_id": 1048,
                "kind": "translate",
                "source_index": 2,
                "source_language": "en",
                "target_language": "nl",
                "engine": "openai",
                "model": "gpt-4o-mini",
                "status": "pending",
                "progress": 0,
                "progress_message": "",
                "result_subtitle_id": null,
                "created_at": "2026-06-12T10:00:00Z",
                "updated_at": "2026-06-12T10:00:00Z"
              }
            }
        """.trimIndent()

        val job = json.decodeFromString(SubtitleAiJobResponse.serializer(), payload).job

        assertEquals(91L, job.id)
        assertEquals(1048, job.mediaFileId)
        assertEquals(SubtitleAiJobKind.Translate, job.kind)
        assertEquals(2, job.sourceIndex)
        assertEquals("en", job.sourceLanguage)
        assertEquals("nl", job.targetLanguage)
        assertEquals("openai", job.engine)
        assertEquals("gpt-4o-mini", job.model)
        assertEquals(SubtitleAiJobStatus.Pending, job.status)
        assertEquals(0.0, job.progress)
        assertNull(job.resultSubtitleId)
        assertNull(job.errorMessage)
        assertFalse(job.isTerminal)
    }

    @Test
    fun `decodes jobs list and terminal status helpers`() {
        val payload = """
            {
              "jobs": [
                {"id": 91, "media_file_id": 1048, "kind": "translate", "source_index": 2,
                 "source_language": "en", "target_language": "nl", "engine": "openai",
                 "model": "gpt-4o-mini", "status": "completed", "progress": 1,
                 "progress_message": "Done", "result_subtitle_id": 313,
                 "created_at": "2026-06-12T10:00:00Z", "updated_at": "2026-06-12T10:04:00Z"},
                {"id": 92, "media_file_id": 1048, "kind": "transcribe", "source_index": 0,
                 "source_language": "", "target_language": "", "engine": "openai",
                 "model": "whisper-1", "status": "failed", "progress": 0.4,
                 "progress_message": "", "result_subtitle_id": null,
                 "error_message": "audio extraction failed",
                 "created_at": "2026-06-12T11:00:00Z", "updated_at": "2026-06-12T11:01:00Z"},
                {"id": 93, "media_file_id": 1048, "kind": "transcribe_translate", "source_index": 1,
                 "source_language": "", "target_language": "nl", "engine": "openai",
                 "model": "whisper-1", "status": "running", "progress": 0.25,
                 "progress_message": "Transcribing audio", "result_subtitle_id": null,
                 "created_at": "2026-06-12T11:30:00Z", "updated_at": "2026-06-12T11:31:00Z"}
              ]
            }
        """.trimIndent()

        val jobs = json.decodeFromString(SubtitleAiJobsResponse.serializer(), payload).jobs

        assertEquals(3, jobs.size)
        assertEquals(313, jobs[0].resultSubtitleId)
        assertTrue(jobs[0].isTerminal)
        assertEquals("audio extraction failed", jobs[1].errorMessage)
        assertTrue(jobs[1].isTerminal)
        assertEquals(SubtitleAiJobKind.TranscribeTranslate, jobs[2].kind)
        assertEquals(SubtitleAiJobStatus.Running, jobs[2].status)
        assertEquals(0.25, jobs[2].progress)
        assertFalse(jobs[2].isTerminal)
    }

    @Test
    fun `encodes search request with wire names`() {
        val encoded = json.encodeToString(
            SubtitleSearchRequest.serializer(),
            SubtitleSearchRequest(mediaFileId = 1048, languages = listOf("en", "nl")),
        )
        val obj = json.parseToJsonElement(encoded).jsonObject
        assertEquals(setOf("media_file_id", "languages"), obj.keys)
    }

    @Test
    fun `encodes download request echoing all search result fields`() {
        val encoded = json.encodeToString(
            SubtitleDownloadRequest.serializer(),
            SubtitleDownloadRequest(
                mediaFileId = 1048,
                provider = SubtitleProvider.OpenSubtitles,
                subtitleId = "os-9182736",
                language = "en",
                releaseName = "Dune.Part.Three.2026.2160p.WEB-DL.DDP5.1-FLUX",
                format = "srt",
                score = 91.5,
                hearingImpaired = false,
            ),
        )
        val obj = json.parseToJsonElement(encoded).jsonObject
        assertEquals(
            setOf(
                "media_file_id", "provider", "subtitle_id", "language",
                "release_name", "format", "score", "hearing_impaired",
            ),
            obj.keys,
        )
    }

    @Test
    fun `translate request omits nulls and never carries session_id`() {
        val encoded = json.encodeToString(
            SubtitleTranslateRequest.serializer(),
            SubtitleTranslateRequest(
                mediaFileId = 1048,
                sourceIndex = 2,
                targetLanguage = "nl",
                startPosition = 1234.5,
            ),
        )
        val obj = json.parseToJsonElement(encoded).jsonObject
        // Android polls instead of streaming live cues, so session_id is never sent.
        assertFalse("session_id" in obj.keys)
        assertFalse("source_language" in obj.keys)  // null → omitted (explicitNulls=false)
        assertEquals(
            setOf("media_file_id", "kind", "source_index", "target_language", "start_position"),
            obj.keys,
        )
    }

    @Test
    fun `plain transcribe request omits target language`() {
        val encoded = json.encodeToString(
            SubtitleTranslateRequest.serializer(),
            SubtitleTranslateRequest(
                mediaFileId = 1048,
                kind = SubtitleAiJobKind.Transcribe,
                sourceIndex = 1,  // audio track index for ASR kinds
            ),
        )
        val obj = json.parseToJsonElement(encoded).jsonObject
        assertFalse("target_language" in obj.keys)
        assertFalse("start_position" in obj.keys)
        assertEquals(setOf("media_file_id", "kind", "source_index"), obj.keys)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

```bash
./gradlew :shared:testDebugUnitTest --tests com.continuum.app.model.subtitles.SubtitleModelsSerializationTest
```

Expected: `> Task :shared:compileDebugUnitTestKotlinAndroid FAILED` with `Unresolved reference 'SubtitleSearchResponse'` (and siblings) — the models do not exist yet.

- [ ] **Step 3: Implementation**

```kotlin
// shared/src/commonMain/kotlin/com/continuum/app/model/subtitles/SubtitleModels.kt
package com.continuum.app.model.subtitles

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Provider ids the server may return for search results / downloaded subtitles. */
object SubtitleProvider {
    const val OpenSubtitles = "opensubtitles"
    const val Subdl = "subdl"
    const val Subsource = "subsource"
}

/** `kind` values for POST /api/v1/subtitles/ai/translate. */
object SubtitleAiJobKind {
    const val Translate = "translate"
    const val Transcribe = "transcribe"
    const val TranscribeTranslate = "transcribe_translate"
}

/** Job lifecycle statuses (internal/subtitles/ai/job.go). */
object SubtitleAiJobStatus {
    const val Pending = "pending"
    const val Running = "running"
    const val Completed = "completed"
    const val Failed = "failed"
    const val Cancelled = "cancelled"
}

/** Body for POST /api/v1/subtitles/search. */
@Serializable
data class SubtitleSearchRequest(
    @SerialName("media_file_id") val mediaFileId: Int,
    val languages: List<String>,
)

/** One provider hit. `id` is a provider-scoped string, echoed back on download. */
@Serializable
data class SubtitleResult(
    val id: String,
    val provider: String,
    val language: String,
    @SerialName("release_name") val releaseName: String,
    val format: String,
    /** Match score 0–100. */
    val score: Double = 0.0,
    val downloads: Int = 0,
    @SerialName("hearing_impaired") val hearingImpaired: Boolean = false,
    @SerialName("upload_date") val uploadDate: String? = null,
)

@Serializable
data class SubtitleSearchResponse(
    val results: List<SubtitleResult> = emptyList(),
    val warnings: List<String> = emptyList(),
)

/** Body for POST /api/v1/subtitles/download — echoes the chosen [SubtitleResult]. */
@Serializable
data class SubtitleDownloadRequest(
    @SerialName("media_file_id") val mediaFileId: Int,
    val provider: String,
    @SerialName("subtitle_id") val subtitleId: String,
    val language: String,
    @SerialName("release_name") val releaseName: String,
    val format: String,
    val score: Double,
    @SerialName("hearing_impaired") val hearingImpaired: Boolean,
)

/** A subtitle stored server-side, listed by GET /api/v1/subtitles/{media_file_id}. */
@Serializable
data class DownloadedSubtitle(
    val id: Int,
    @SerialName("media_file_id") val mediaFileId: Int,
    val provider: String,
    val language: String,
    val format: String,
    @SerialName("release_name") val releaseName: String,
    val score: Double = 0.0,
    @SerialName("hearing_impaired") val hearingImpaired: Boolean = false,
    @SerialName("created_at") val createdAt: String = "",
)

/** Envelope for POST /api/v1/subtitles/download. */
@Serializable
data class SubtitleDownloadResponse(val subtitle: DownloadedSubtitle)

/** Envelope for GET /api/v1/subtitles/{media_file_id}. */
@Serializable
data class DownloadedSubtitlesResponse(
    val subtitles: List<DownloadedSubtitle> = emptyList(),
)

/** GET /api/v1/subtitles/ai/status — both false when AI is unconfigured. */
@Serializable
data class SubtitleAiStatus(
    val enabled: Boolean = false,
    @SerialName("transcribe_enabled") val transcribeEnabled: Boolean = false,
)

/**
 * GET /api/v1/subtitles/ai/quota — transcribe-kind budget. Exempt callers
 * (admins) and disabled quotas get the zero value with `limited = false`;
 * the remaining fields are only meaningful when `limited` is true.
 */
@Serializable
data class SubtitleAiQuota(
    val limited: Boolean = false,
    val limit: Int = 0,
    val used: Int = 0,
    val remaining: Int = 0,
    /** "day" | "week" | "month"; empty when not limited. */
    val period: String = "",
)

/**
 * Body for POST /api/v1/subtitles/ai/translate.
 *
 * Deliberately has NO `session_id` field: the web sends one to receive live
 * cue streaming over the playback websocket; Android polls the job instead
 * (web/src/player/.../usePlaybackSession.ts vs. SubtitlesRepository.pollJob).
 *
 * [sourceIndex] is the combined subtitle index for `translate`, and the audio
 * track index for the transcribe kinds. [targetLanguage] is optional only for
 * a plain `transcribe` (acts as a language hint).
 */
@Serializable
data class SubtitleTranslateRequest(
    @SerialName("media_file_id") val mediaFileId: Int,
    val kind: String = SubtitleAiJobKind.Translate,
    @SerialName("source_index") val sourceIndex: Int,
    @SerialName("source_language") val sourceLanguage: String? = null,
    @SerialName("target_language") val targetLanguage: String? = null,
    @SerialName("start_position") val startPosition: Double? = null,
)

@Serializable
data class SubtitleAiJob(
    val id: Long,
    @SerialName("media_file_id") val mediaFileId: Int,
    val kind: String = SubtitleAiJobKind.Translate,
    @SerialName("source_index") val sourceIndex: Int = 0,
    @SerialName("source_language") val sourceLanguage: String = "",
    @SerialName("target_language") val targetLanguage: String = "",
    val engine: String = "",
    val model: String = "",
    val status: String,
    /** 0.0 .. 1.0 */
    val progress: Double = 0.0,
    @SerialName("progress_message") val progressMessage: String = "",
    @SerialName("result_subtitle_id") val resultSubtitleId: Int? = null,
    @SerialName("error_message") val errorMessage: String? = null,
    @SerialName("created_at") val createdAt: String = "",
    @SerialName("updated_at") val updatedAt: String = "",
) {
    val isTerminal: Boolean
        get() = status == SubtitleAiJobStatus.Completed ||
            status == SubtitleAiJobStatus.Failed ||
            status == SubtitleAiJobStatus.Cancelled
}

/** Envelope for translate (202) and GET /ai/jobs/{id}. */
@Serializable
data class SubtitleAiJobResponse(val job: SubtitleAiJob)

/** Envelope for GET /ai/jobs?media_file_id=N. */
@Serializable
data class SubtitleAiJobsResponse(val jobs: List<SubtitleAiJob> = emptyList())
```

- [ ] **Step 4: Run tests**

```bash
./gradlew :shared:testDebugUnitTest --tests com.continuum.app.model.subtitles.SubtitleModelsSerializationTest
```

- [ ] **Step 5: Commit**

```bash
git add \
  shared/src/commonMain/kotlin/com/continuum/app/model/subtitles/SubtitleModels.kt \
  shared/src/commonTest/kotlin/com/continuum/app/model/subtitles/SubtitleModelsSerializationTest.kt
git commit -m "Add subtitle suite wire models

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>"
```

---

### Task S2: SubtitlesApi + NetworkModule registration

**Files:**
- Create: `shared/src/commonMain/kotlin/com/continuum/app/network/api/SubtitlesApi.kt`
- Modify: `shared/src/commonMain/kotlin/com/continuum/app/di/NetworkModule.kt`
- Modify: `gradle/libs.versions.toml` (add `ktor-client-mock`)
- Modify: `shared/build.gradle.kts` (commonTest dependency)
- Test: `shared/src/commonTest/kotlin/com/continuum/app/network/api/SubtitlesApiTest.kt`

The shared module has no transport-level test precedent because none of the existing APIs had request-shaping logic worth pinning; here the translate body's null-omission and missing `session_id` are explicit contract requirements, so add `ktor-client-mock` (same `ktor = "3.1.2"` version ref) and test through Ktor's MockEngine with the production `ContinuumJson`.

- [ ] **Step 1: Write the failing test**

Add to `gradle/libs.versions.toml` under `[libraries]` (next to the other ktor entries):

```toml
ktor-client-mock = { module = "io.ktor:ktor-client-mock", version.ref = "ktor" }
```

Add to `shared/build.gradle.kts` in `commonTest.dependencies`:

```kotlin
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.ktor.client.mock)
        }
```

```kotlin
// shared/src/commonTest/kotlin/com/continuum/app/network/api/SubtitlesApiTest.kt
package com.continuum.app.network.api

import com.continuum.app.model.subtitles.SubtitleAiJobKind
import com.continuum.app.model.subtitles.SubtitleAiJobStatus
import com.continuum.app.model.subtitles.SubtitleDownloadRequest
import com.continuum.app.model.subtitles.SubtitleProvider
import com.continuum.app.model.subtitles.SubtitleSearchRequest
import com.continuum.app.model.subtitles.SubtitleTranslateRequest
import com.continuum.app.network.ApiResult
import com.continuum.app.network.ContinuumJson
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class SubtitlesApiTest {

    /** Captures the single request a test makes through the mock transport. */
    private class Captured {
        var method: HttpMethod? = null
        var path: String = ""
        var query: Map<String, String?> = emptyMap()
        var body: String = ""
    }

    private fun api(
        status: HttpStatusCode = HttpStatusCode.OK,
        responseBody: String = "{}",
        captured: Captured = Captured(),
    ): Pair<SubtitlesApi, Captured> {
        val client = HttpClient(
            MockEngine { request ->
                captured.method = request.method
                captured.path = request.url.encodedPath
                captured.query = request.url.parameters.names()
                    .associateWith { request.url.parameters[it] }
                captured.body = request.body.toByteArray().decodeToString()
                respond(
                    content = responseBody,
                    status = status,
                    headers = headersOf(HttpHeaders.ContentType, "application/json"),
                )
            },
        ) {
            install(ContentNegotiation) { json(ContinuumJson) }
        }
        return DefaultSubtitlesApi(client) to captured
    }

    @Test
    fun `search posts wire body and decodes results`() = runTest {
        val (api, captured) = api(
            responseBody = """
                {"results":[{"id":"os-1","provider":"opensubtitles","language":"en",
                  "release_name":"R","format":"srt","score":70,"downloads":9,
                  "hearing_impaired":false}],
                 "warnings":["subdl: timed out"]}
            """.trimIndent(),
        )

        val result = api.search(SubtitleSearchRequest(mediaFileId = 1048, languages = listOf("en")))

        assertEquals(HttpMethod.Post, captured.method)
        assertEquals("/api/v1/subtitles/search", captured.path)
        val sent = ContinuumJson.parseToJsonElement(captured.body).jsonObject
        assertEquals(setOf("media_file_id", "languages"), sent.keys)

        assertIs<ApiResult.Success<*>>(result)
        val data = (result as ApiResult.Success).data
        assertEquals("os-1", data.results.single().id)
        assertEquals(listOf("subdl: timed out"), data.warnings)
    }

    @Test
    fun `download posts echo body and unwraps subtitle envelope`() = runTest {
        val (api, captured) = api(
            responseBody = """
                {"subtitle":{"id":312,"media_file_id":1048,"provider":"opensubtitles",
                 "language":"en","format":"srt","release_name":"R","score":70,
                 "hearing_impaired":true,"created_at":"2026-06-12T09:30:00Z"}}
            """.trimIndent(),
        )

        val result = api.download(
            SubtitleDownloadRequest(
                mediaFileId = 1048,
                provider = SubtitleProvider.OpenSubtitles,
                subtitleId = "os-1",
                language = "en",
                releaseName = "R",
                format = "srt",
                score = 70.0,
                hearingImpaired = true,
            ),
        )

        assertEquals("/api/v1/subtitles/download", captured.path)
        assertIs<ApiResult.Success<*>>(result)
        assertEquals(312, (result as ApiResult.Success).data.subtitle.id)
    }

    @Test
    fun `list gets media file path and decodes subtitles`() = runTest {
        val (api, captured) = api(responseBody = """{"subtitles":[]}""")

        val result = api.list(mediaFileId = 1048)

        assertEquals(HttpMethod.Get, captured.method)
        assertEquals("/api/v1/subtitles/1048", captured.path)
        assertIs<ApiResult.Success<*>>(result)
    }

    @Test
    fun `aiStatus and aiQuota hit their paths`() = runTest {
        val (statusApi, statusCaptured) = api(
            responseBody = """{"enabled":true,"transcribe_enabled":true}""",
        )
        val status = statusApi.aiStatus()
        assertEquals("/api/v1/subtitles/ai/status", statusCaptured.path)
        assertIs<ApiResult.Success<*>>(status)
        assertTrue((status as ApiResult.Success).data.transcribeEnabled)

        val (quotaApi, quotaCaptured) = api(
            responseBody = """{"limited":true,"limit":5,"used":5,"remaining":0,"period":"month"}""",
        )
        val quota = quotaApi.aiQuota()
        assertEquals("/api/v1/subtitles/ai/quota", quotaCaptured.path)
        assertIs<ApiResult.Success<*>>(quota)
        assertEquals(0, (quota as ApiResult.Success).data.remaining)
    }

    @Test
    fun `translate posts body without session_id and decodes 202 job envelope`() = runTest {
        val (api, captured) = api(
            status = HttpStatusCode.Accepted,
            responseBody = """
                {"job":{"id":91,"media_file_id":1048,"kind":"translate","source_index":2,
                 "source_language":"en","target_language":"nl","engine":"openai",
                 "model":"gpt-4o-mini","status":"pending","progress":0,
                 "progress_message":"","result_subtitle_id":null,
                 "created_at":"2026-06-12T10:00:00Z","updated_at":"2026-06-12T10:00:00Z"}}
            """.trimIndent(),
        )

        val result = api.translate(
            SubtitleTranslateRequest(
                mediaFileId = 1048,
                kind = SubtitleAiJobKind.Translate,
                sourceIndex = 2,
                targetLanguage = "nl",
                startPosition = 845.2,
            ),
        )

        assertEquals("/api/v1/subtitles/ai/translate", captured.path)
        val sent = ContinuumJson.parseToJsonElement(captured.body).jsonObject
        assertFalse("session_id" in sent.keys)       // Android polls; never streams live cues
        assertFalse("source_language" in sent.keys)  // null omitted
        assertEquals(
            setOf("media_file_id", "kind", "source_index", "target_language", "start_position"),
            sent.keys,
        )
        assertIs<ApiResult.Success<*>>(result)
        assertEquals(SubtitleAiJobStatus.Pending, (result as ApiResult.Success).data.job.status)
    }

    @Test
    fun `listJobs passes media_file_id query and getJob hits job path`() = runTest {
        val (listApi, listCaptured) = api(responseBody = """{"jobs":[]}""")
        listApi.listJobs(mediaFileId = 1048)
        assertEquals("/api/v1/subtitles/ai/jobs", listCaptured.path)
        assertEquals("1048", listCaptured.query["media_file_id"])

        val (getApi, getCaptured) = api(
            responseBody = """
                {"job":{"id":91,"media_file_id":1048,"kind":"translate","source_index":2,
                 "status":"running","progress":0.5,"progress_message":"Translating",
                 "result_subtitle_id":null,
                 "created_at":"2026-06-12T10:00:00Z","updated_at":"2026-06-12T10:01:00Z"}}
            """.trimIndent(),
        )
        val result = getApi.getJob(jobId = 91L)
        assertEquals("/api/v1/subtitles/ai/jobs/91", getCaptured.path)
        assertIs<ApiResult.Success<*>>(result)
    }

    @Test
    fun `cancelJob posts to cancel path and maps 204 to Unit`() = runTest {
        val (api, captured) = api(status = HttpStatusCode.NoContent, responseBody = "")

        val result = api.cancelJob(jobId = 91L)

        assertEquals(HttpMethod.Post, captured.method)
        assertEquals("/api/v1/subtitles/ai/jobs/91/cancel", captured.path)
        assertEquals(ApiResult.Success(Unit), result)
    }

    @Test
    fun `server error surfaces as ApiResult Error with message`() = runTest {
        // 503 = AI engine unconfigured; 429 = quota exhausted — same mapping path.
        val (api, _) = api(
            status = HttpStatusCode.ServiceUnavailable,
            responseBody = """{"error":"ai_unavailable","message":"AI translation is not configured"}""",
        )

        val result = api.translate(
            SubtitleTranslateRequest(mediaFileId = 1048, sourceIndex = 0, targetLanguage = "nl"),
        )

        assertIs<ApiResult.Error>(result)
        assertEquals(503, result.code)
        assertEquals("AI translation is not configured", result.message)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

```bash
./gradlew :shared:testDebugUnitTest --tests com.continuum.app.network.api.SubtitlesApiTest
```

Expected: `> Task :shared:compileDebugUnitTestKotlinAndroid FAILED` with `Unresolved reference 'SubtitlesApi'` / `'DefaultSubtitlesApi'`.

- [ ] **Step 3: Implementation**

```kotlin
// shared/src/commonMain/kotlin/com/continuum/app/network/api/SubtitlesApi.kt
package com.continuum.app.network.api

import com.continuum.app.model.subtitles.DownloadedSubtitlesResponse
import com.continuum.app.model.subtitles.SubtitleAiJobResponse
import com.continuum.app.model.subtitles.SubtitleAiJobsResponse
import com.continuum.app.model.subtitles.SubtitleAiQuota
import com.continuum.app.model.subtitles.SubtitleAiStatus
import com.continuum.app.model.subtitles.SubtitleDownloadRequest
import com.continuum.app.model.subtitles.SubtitleDownloadResponse
import com.continuum.app.model.subtitles.SubtitleSearchRequest
import com.continuum.app.model.subtitles.SubtitleSearchResponse
import com.continuum.app.model.subtitles.SubtitleTranslateRequest
import com.continuum.app.network.ApiResult
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType

/**
 * Subtitle provider search/download + AI translation endpoints. Kept behind
 * an interface so repository and ViewModel tests can fake the transport,
 * matching the CalendarApi/RequestsApi shape.
 */
interface SubtitlesApi {

    /** POST /api/v1/subtitles/search — errors with server text when no providers are configured. */
    suspend fun search(request: SubtitleSearchRequest): ApiResult<SubtitleSearchResponse>

    /** POST /api/v1/subtitles/download — echoes the chosen search result back. */
    suspend fun download(request: SubtitleDownloadRequest): ApiResult<SubtitleDownloadResponse>

    /** GET /api/v1/subtitles/{media_file_id} — subtitles already stored server-side. */
    suspend fun list(mediaFileId: Int): ApiResult<DownloadedSubtitlesResponse>

    /** GET /api/v1/subtitles/ai/status — both flags false when AI is unconfigured. */
    suspend fun aiStatus(): ApiResult<SubtitleAiStatus>

    /** GET /api/v1/subtitles/ai/quota — transcribe-kind budget; admins are exempt. */
    suspend fun aiQuota(): ApiResult<SubtitleAiQuota>

    /** POST /api/v1/subtitles/ai/translate — 202 with the queued job; 429 quota; 503 unconfigured. */
    suspend fun translate(request: SubtitleTranslateRequest): ApiResult<SubtitleAiJobResponse>

    /** GET /api/v1/subtitles/ai/jobs?media_file_id=N */
    suspend fun listJobs(mediaFileId: Int): ApiResult<SubtitleAiJobsResponse>

    /** GET /api/v1/subtitles/ai/jobs/{id} — 404 once the job row is gone. */
    suspend fun getJob(jobId: Long): ApiResult<SubtitleAiJobResponse>

    /** POST /api/v1/subtitles/ai/jobs/{id}/cancel — 204 on success. */
    suspend fun cancelJob(jobId: Long): ApiResult<Unit>
}

class DefaultSubtitlesApi(private val client: HttpClient) : SubtitlesApi {

    override suspend fun search(request: SubtitleSearchRequest): ApiResult<SubtitleSearchResponse> =
        safeApiCall {
            client.post("/api/v1/subtitles/search") {
                contentType(ContentType.Application.Json)
                setBody(request)
            }
        }

    override suspend fun download(request: SubtitleDownloadRequest): ApiResult<SubtitleDownloadResponse> =
        safeApiCall {
            client.post("/api/v1/subtitles/download") {
                contentType(ContentType.Application.Json)
                setBody(request)
            }
        }

    override suspend fun list(mediaFileId: Int): ApiResult<DownloadedSubtitlesResponse> =
        safeApiCall {
            client.get("/api/v1/subtitles/$mediaFileId")
        }

    override suspend fun aiStatus(): ApiResult<SubtitleAiStatus> = safeApiCall {
        client.get("/api/v1/subtitles/ai/status")
    }

    override suspend fun aiQuota(): ApiResult<SubtitleAiQuota> = safeApiCall {
        client.get("/api/v1/subtitles/ai/quota")
    }

    override suspend fun translate(request: SubtitleTranslateRequest): ApiResult<SubtitleAiJobResponse> =
        safeApiCall {
            client.post("/api/v1/subtitles/ai/translate") {
                contentType(ContentType.Application.Json)
                setBody(request)
            }
        }

    override suspend fun listJobs(mediaFileId: Int): ApiResult<SubtitleAiJobsResponse> =
        safeApiCall {
            client.get("/api/v1/subtitles/ai/jobs") {
                parameter("media_file_id", mediaFileId)
            }
        }

    override suspend fun getJob(jobId: Long): ApiResult<SubtitleAiJobResponse> = safeApiCall {
        client.get("/api/v1/subtitles/ai/jobs/$jobId")
    }

    override suspend fun cancelJob(jobId: Long): ApiResult<Unit> = safeApiCall {
        client.post("/api/v1/subtitles/ai/jobs/$jobId/cancel")
    }
}
```

Register in `shared/src/commonMain/kotlin/com/continuum/app/di/NetworkModule.kt` (after the `EbookReaderApi` line, matching the `CalendarApi` interface+Default registration style):

```kotlin
    single { EbookReaderApi(get()) }
    single<SubtitlesApi> { DefaultSubtitlesApi(get()) }
```

- [ ] **Step 4: Run tests**

```bash
./gradlew :shared:testDebugUnitTest --tests com.continuum.app.network.api.SubtitlesApiTest
./gradlew :shared:testDebugUnitTest
```

- [ ] **Step 5: Commit**

```bash
git add \
  gradle/libs.versions.toml \
  shared/build.gradle.kts \
  shared/src/commonMain/kotlin/com/continuum/app/network/api/SubtitlesApi.kt \
  shared/src/commonMain/kotlin/com/continuum/app/di/NetworkModule.kt \
  shared/src/commonTest/kotlin/com/continuum/app/network/api/SubtitlesApiTest.kt
git commit -m "Add subtitles API client

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>"
```

---

### Task S3: SubtitlesRepository with pollJob loop + RepositoryModule registration

**Files:**
- Create: `shared/src/commonMain/kotlin/com/continuum/app/repository/SubtitlesRepository.kt`
- Modify: `shared/src/commonMain/kotlin/com/continuum/app/di/RepositoryModule.kt`
- Test: `shared/src/commonTest/kotlin/com/continuum/app/repository/SubtitlesRepositoryTest.kt`

Poll-loop semantics mirror `DeviceLoginRepository.runPollLoop` (`shared/src/commonMain/kotlin/com/continuum/app/repository/DeviceLoginRepository.kt:93-171`): poll immediately (no initial delay), `ApiResult.Error` 404 is terminal, any other `Error`/`NetworkError` is transient (delay + retry), `CancellationException` is rethrown so structured cancellation in `viewModelScope` works.

- [ ] **Step 1: Write the failing test**

```kotlin
// shared/src/commonTest/kotlin/com/continuum/app/repository/SubtitlesRepositoryTest.kt
package com.continuum.app.repository

import com.continuum.app.model.subtitles.DownloadedSubtitlesResponse
import com.continuum.app.model.subtitles.SubtitleAiJob
import com.continuum.app.model.subtitles.SubtitleAiJobResponse
import com.continuum.app.model.subtitles.SubtitleAiJobsResponse
import com.continuum.app.model.subtitles.SubtitleAiJobStatus
import com.continuum.app.model.subtitles.SubtitleAiQuota
import com.continuum.app.model.subtitles.SubtitleAiStatus
import com.continuum.app.model.subtitles.SubtitleDownloadRequest
import com.continuum.app.model.subtitles.SubtitleDownloadResponse
import com.continuum.app.model.subtitles.SubtitleSearchRequest
import com.continuum.app.model.subtitles.SubtitleSearchResponse
import com.continuum.app.model.subtitles.SubtitleTranslateRequest
import com.continuum.app.model.subtitles.DownloadedSubtitle
import com.continuum.app.network.ApiResult
import com.continuum.app.network.api.SubtitlesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class SubtitlesRepositoryTest {

    private fun job(
        status: String,
        progress: Double = 0.0,
        resultSubtitleId: Int? = null,
        errorMessage: String? = null,
    ) = SubtitleAiJob(
        id = 91L,
        mediaFileId = 1048,
        kind = "translate",
        sourceIndex = 2,
        sourceLanguage = "en",
        targetLanguage = "nl",
        engine = "openai",
        model = "gpt-4o-mini",
        status = status,
        progress = progress,
        progressMessage = "",
        resultSubtitleId = resultSubtitleId,
        errorMessage = errorMessage,
        createdAt = "2026-06-12T10:00:00Z",
        updatedAt = "2026-06-12T10:00:00Z",
    )

    /**
     * Scripted fake: getJob consumes [jobResults] in order and keeps
     * returning the last entry once exhausted (a terminal job stays terminal).
     */
    private class FakeSubtitlesApi(
        val jobResults: MutableList<ApiResult<SubtitleAiJobResponse>> = mutableListOf(),
    ) : SubtitlesApi {
        val calls = mutableListOf<String>()
        var getJobCount = 0

        var searchResult: ApiResult<SubtitleSearchResponse> =
            ApiResult.Success(SubtitleSearchResponse())
        var downloadResult: ApiResult<SubtitleDownloadResponse> =
            ApiResult.Error(code = 500, error = "unset", message = "unset")
        var listResult: ApiResult<DownloadedSubtitlesResponse> =
            ApiResult.Success(DownloadedSubtitlesResponse())
        var aiStatusResult: ApiResult<SubtitleAiStatus> =
            ApiResult.Success(SubtitleAiStatus(enabled = true, transcribeEnabled = true))
        var aiQuotaResult: ApiResult<SubtitleAiQuota> =
            ApiResult.Success(SubtitleAiQuota())
        var translateResult: ApiResult<SubtitleAiJobResponse> =
            ApiResult.Error(code = 500, error = "unset", message = "unset")
        var listJobsResult: ApiResult<SubtitleAiJobsResponse> =
            ApiResult.Success(SubtitleAiJobsResponse())
        var cancelJobResult: ApiResult<Unit> = ApiResult.Success(Unit)

        override suspend fun search(request: SubtitleSearchRequest): ApiResult<SubtitleSearchResponse> {
            calls += "search:${request.mediaFileId}:${request.languages.joinToString(",")}"
            return searchResult
        }
        override suspend fun download(request: SubtitleDownloadRequest): ApiResult<SubtitleDownloadResponse> {
            calls += "download:${request.provider}:${request.subtitleId}"
            return downloadResult
        }
        override suspend fun list(mediaFileId: Int): ApiResult<DownloadedSubtitlesResponse> {
            calls += "list:$mediaFileId"
            return listResult
        }
        override suspend fun aiStatus(): ApiResult<SubtitleAiStatus> {
            calls += "aiStatus"
            return aiStatusResult
        }
        override suspend fun aiQuota(): ApiResult<SubtitleAiQuota> {
            calls += "aiQuota"
            return aiQuotaResult
        }
        override suspend fun translate(request: SubtitleTranslateRequest): ApiResult<SubtitleAiJobResponse> {
            calls += "translate:${request.mediaFileId}:${request.kind}:${request.sourceIndex}"
            return translateResult
        }
        override suspend fun listJobs(mediaFileId: Int): ApiResult<SubtitleAiJobsResponse> {
            calls += "listJobs:$mediaFileId"
            return listJobsResult
        }
        override suspend fun getJob(jobId: Long): ApiResult<SubtitleAiJobResponse> {
            calls += "getJob:$jobId"
            getJobCount++
            return if (jobResults.size > 1) jobResults.removeAt(0) else jobResults.first()
        }
        override suspend fun cancelJob(jobId: Long): ApiResult<Unit> {
            calls += "cancelJob:$jobId"
            return cancelJobResult
        }
    }

    @Test
    fun `pass-through methods delegate to the api unchanged`() = runTest {
        val api = FakeSubtitlesApi()
        api.listResult = ApiResult.Success(
            DownloadedSubtitlesResponse(
                subtitles = listOf(
                    DownloadedSubtitle(
                        id = 312, mediaFileId = 1048, provider = "opensubtitles",
                        language = "en", format = "srt", releaseName = "R",
                    ),
                ),
            ),
        )
        val repository = SubtitlesRepository(api)

        val search = repository.search(SubtitleSearchRequest(mediaFileId = 1048, languages = listOf("en")))
        val list = repository.list(mediaFileId = 1048)
        val status = repository.aiStatus()
        val quota = repository.aiQuota()
        val jobs = repository.listJobs(mediaFileId = 1048)
        val cancel = repository.cancelJob(jobId = 91L)

        assertEquals(api.searchResult, search)
        assertEquals(api.listResult, list)
        assertEquals(api.aiStatusResult, status)
        assertEquals(api.aiQuotaResult, quota)
        assertEquals(api.listJobsResult, jobs)
        assertEquals(ApiResult.Success(Unit), cancel)
        assertEquals(
            listOf("search:1048:en", "list:1048", "aiStatus", "aiQuota", "listJobs:1048", "cancelJob:91"),
            api.calls,
        )
    }

    @Test
    fun `propagates api errors unchanged`() = runTest {
        val api = FakeSubtitlesApi()
        val error = ApiResult.Error(code = 500, error = "search_error", message = "Subtitle search failed")
        api.searchResult = error
        val repository = SubtitlesRepository(api)

        assertEquals(error, repository.search(SubtitleSearchRequest(1048, listOf("en"))))
    }

    @Test
    fun `pollJob completes after N polls reporting each update`() = runTest {
        val api = FakeSubtitlesApi(
            jobResults = mutableListOf(
                ApiResult.Success(SubtitleAiJobResponse(job(SubtitleAiJobStatus.Pending))),
                ApiResult.Success(SubtitleAiJobResponse(job(SubtitleAiJobStatus.Running, progress = 0.5))),
                ApiResult.Success(
                    SubtitleAiJobResponse(
                        job(SubtitleAiJobStatus.Completed, progress = 1.0, resultSubtitleId = 313),
                    ),
                ),
            ),
        )
        val repository = SubtitlesRepository(api)
        val updates = mutableListOf<SubtitleAiJob>()

        val outcome = repository.pollJob(jobId = 91L) { updates += it }

        assertEquals(SubtitlesRepository.SubtitleJobOutcome.Completed(resultSubtitleId = 313), outcome)
        assertEquals(3, api.getJobCount)
        assertEquals(
            listOf(SubtitleAiJobStatus.Pending, SubtitleAiJobStatus.Running, SubtitleAiJobStatus.Completed),
            updates.map { it.status },
        )
        // Two non-terminal polls → two 1000 ms virtual-time delays.
        assertEquals(2_000L, currentTime)
    }

    @Test
    fun `pollJob failure carries the job error message`() = runTest {
        val api = FakeSubtitlesApi(
            jobResults = mutableListOf(
                ApiResult.Success(
                    SubtitleAiJobResponse(
                        job(SubtitleAiJobStatus.Failed, errorMessage = "audio extraction failed"),
                    ),
                ),
            ),
        )
        val repository = SubtitlesRepository(api)

        val outcome = repository.pollJob(jobId = 91L)

        assertEquals(
            SubtitlesRepository.SubtitleJobOutcome.Failed(message = "audio extraction failed"),
            outcome,
        )
    }

    @Test
    fun `pollJob retries through transient errors then succeeds`() = runTest {
        val api = FakeSubtitlesApi(
            jobResults = mutableListOf(
                ApiResult.NetworkError(RuntimeException("offline")),
                ApiResult.Error(code = 500, error = "internal_error", message = "blip"),
                ApiResult.Success(
                    SubtitleAiJobResponse(
                        job(SubtitleAiJobStatus.Completed, progress = 1.0, resultSubtitleId = 313),
                    ),
                ),
            ),
        )
        val repository = SubtitlesRepository(api)

        val outcome = repository.pollJob(jobId = 91L)

        assertEquals(SubtitlesRepository.SubtitleJobOutcome.Completed(resultSubtitleId = 313), outcome)
        assertEquals(3, api.getJobCount)
        assertEquals(2_000L, currentTime)  // each transient error waited one interval
    }

    @Test
    fun `pollJob treats 404 as terminal failure`() = runTest {
        val api = FakeSubtitlesApi(
            jobResults = mutableListOf(
                ApiResult.Error(code = 404, error = "not_found", message = "Job not found"),
            ),
        )
        val repository = SubtitlesRepository(api)

        val outcome = repository.pollJob(jobId = 91L)

        assertEquals(
            SubtitlesRepository.SubtitleJobOutcome.Failed(message = "This job no longer exists on the server."),
            outcome,
        )
        assertEquals(1, api.getJobCount)
    }

    @Test
    fun `pollJob maps cancelled status to Cancelled outcome`() = runTest {
        val api = FakeSubtitlesApi(
            jobResults = mutableListOf(
                ApiResult.Success(SubtitleAiJobResponse(job(SubtitleAiJobStatus.Pending))),
                ApiResult.Success(SubtitleAiJobResponse(job(SubtitleAiJobStatus.Cancelled))),
            ),
        )
        val repository = SubtitlesRepository(api)

        val outcome = repository.pollJob(jobId = 91L)

        assertEquals(SubtitlesRepository.SubtitleJobOutcome.Cancelled, outcome)
    }

    @Test
    fun `pollJob honors coroutine cancellation`() = runTest {
        val api = FakeSubtitlesApi(
            jobResults = mutableListOf(
                ApiResult.Success(SubtitleAiJobResponse(job(SubtitleAiJobStatus.Pending))),
            ),
        )
        val repository = SubtitlesRepository(api)

        val polling = launch { repository.pollJob(jobId = 91L) }
        testScheduler.advanceTimeBy(3_500L)
        runCurrent()
        val pollsBeforeCancel = api.getJobCount
        assertTrue(pollsBeforeCancel >= 3)

        polling.cancel()
        polling.join()
        testScheduler.advanceTimeBy(10_000L)
        runCurrent()

        assertTrue(polling.isCancelled)
        assertEquals(pollsBeforeCancel, api.getJobCount)  // no polls after cancellation
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

```bash
./gradlew :shared:testDebugUnitTest --tests com.continuum.app.repository.SubtitlesRepositoryTest
```

Expected: `> Task :shared:compileDebugUnitTestKotlinAndroid FAILED` with `Unresolved reference 'SubtitlesRepository'`.

- [ ] **Step 3: Implementation**

```kotlin
// shared/src/commonMain/kotlin/com/continuum/app/repository/SubtitlesRepository.kt
package com.continuum.app.repository

import com.continuum.app.model.subtitles.DownloadedSubtitlesResponse
import com.continuum.app.model.subtitles.SubtitleAiJob
import com.continuum.app.model.subtitles.SubtitleAiJobResponse
import com.continuum.app.model.subtitles.SubtitleAiJobsResponse
import com.continuum.app.model.subtitles.SubtitleAiJobStatus
import com.continuum.app.model.subtitles.SubtitleAiQuota
import com.continuum.app.model.subtitles.SubtitleAiStatus
import com.continuum.app.model.subtitles.SubtitleDownloadRequest
import com.continuum.app.model.subtitles.SubtitleDownloadResponse
import com.continuum.app.model.subtitles.SubtitleSearchRequest
import com.continuum.app.model.subtitles.SubtitleSearchResponse
import com.continuum.app.model.subtitles.SubtitleTranslateRequest
import com.continuum.app.network.ApiResult
import com.continuum.app.network.api.SubtitlesApi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

/**
 * Subtitle provider search/download + AI translation. Thin pass-throughs over
 * [SubtitlesApi] plus [pollJob], a suspend loop modeled on
 * [DeviceLoginRepository.runPollLoop]:
 *  - polls immediately (don't wait the first interval)
 *  - swallows transient errors (non-404 [ApiResult.Error], [ApiResult.NetworkError])
 *    and retries after the interval
 *  - 404 = the job row is gone → terminal [SubtitleJobOutcome.Failed]
 *  - terminal job statuses (completed/failed/cancelled) end the loop
 *  - rethrows [CancellationException] so callers can cancel via structured
 *    concurrency (player exit cancels the viewModelScope job)
 */
class SubtitlesRepository(private val api: SubtitlesApi) {

    /** Terminal result of [pollJob]. */
    sealed class SubtitleJobOutcome {
        data class Completed(val resultSubtitleId: Int?) : SubtitleJobOutcome()
        data class Failed(val message: String?) : SubtitleJobOutcome()
        object Cancelled : SubtitleJobOutcome() {
            override fun toString(): String = "Cancelled"
        }
    }

    suspend fun search(request: SubtitleSearchRequest): ApiResult<SubtitleSearchResponse> =
        api.search(request)

    suspend fun download(request: SubtitleDownloadRequest): ApiResult<SubtitleDownloadResponse> =
        api.download(request)

    suspend fun list(mediaFileId: Int): ApiResult<DownloadedSubtitlesResponse> =
        api.list(mediaFileId)

    suspend fun aiStatus(): ApiResult<SubtitleAiStatus> = api.aiStatus()

    suspend fun aiQuota(): ApiResult<SubtitleAiQuota> = api.aiQuota()

    suspend fun translate(request: SubtitleTranslateRequest): ApiResult<SubtitleAiJobResponse> =
        api.translate(request)

    suspend fun listJobs(mediaFileId: Int): ApiResult<SubtitleAiJobsResponse> =
        api.listJobs(mediaFileId)

    suspend fun getJob(jobId: Long): ApiResult<SubtitleAiJobResponse> = api.getJob(jobId)

    suspend fun cancelJob(jobId: Long): ApiResult<Unit> = api.cancelJob(jobId)

    /**
     * Polls GET /ai/jobs/{id} every [intervalMs] until the job reaches a
     * terminal status, invoking [onUpdate] with every successfully fetched
     * snapshot (including the terminal one, so progress UIs can show 100%
     * before dismissing).
     */
    suspend fun pollJob(
        jobId: Long,
        intervalMs: Long = 1_000L,
        onUpdate: (SubtitleAiJob) -> Unit = {},
    ): SubtitleJobOutcome {
        while (true) {
            try {
                val job = when (val r = api.getJob(jobId)) {
                    is ApiResult.Success -> r.data.job
                    is ApiResult.Error -> {
                        if (r.code == 404) {
                            return SubtitleJobOutcome.Failed(
                                "This job no longer exists on the server.",
                            )
                        }
                        // Transient — keep trying.
                        delay(intervalMs)
                        continue
                    }
                    is ApiResult.NetworkError -> {
                        // Transient network blip — keep trying.
                        delay(intervalMs)
                        continue
                    }
                }

                onUpdate(job)

                when (job.status) {
                    SubtitleAiJobStatus.Completed ->
                        return SubtitleJobOutcome.Completed(job.resultSubtitleId)
                    SubtitleAiJobStatus.Failed ->
                        return SubtitleJobOutcome.Failed(job.errorMessage)
                    SubtitleAiJobStatus.Cancelled ->
                        return SubtitleJobOutcome.Cancelled
                    else ->
                        // pending / running (and forward-compatible unknowns).
                        delay(intervalMs)
                }
            } catch (e: CancellationException) {
                throw e
            }
        }
    }
}
```

Register in `shared/src/commonMain/kotlin/com/continuum/app/di/RepositoryModule.kt` — add the import and the singleton (after the `EbookReaderRepository` line):

```kotlin
import com.continuum.app.repository.SubtitlesRepository
```

```kotlin
    single { EbookReaderRepository(get()) }
    single { SubtitlesRepository(get()) }
```

- [ ] **Step 4: Run tests**

```bash
./gradlew :shared:testDebugUnitTest --tests com.continuum.app.repository.SubtitlesRepositoryTest
./gradlew :shared:testDebugUnitTest
```

- [ ] **Step 5: Commit**

```bash
git add \
  shared/src/commonMain/kotlin/com/continuum/app/repository/SubtitlesRepository.kt \
  shared/src/commonMain/kotlin/com/continuum/app/di/RepositoryModule.kt \
  shared/src/commonTest/kotlin/com/continuum/app/repository/SubtitlesRepositoryTest.kt
git commit -m "Add subtitles repository with job polling

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>"
```

---

### Task S4: Shared `mergeDownloadedSubtitles` pure function

**Files:**
- Create: `shared/src/commonMain/kotlin/com/continuum/app/model/playback/SubtitleTrackMerge.kt`
- Test: `shared/src/commonTest/kotlin/com/continuum/app/model/playback/SubtitleTrackMergeTest.kt`

**Web reference (pinned).** `silo-server/web/src/player/hooks/usePlaybackSession.ts`, `refreshSubtitles` (lines 473–514):

```ts
setState((prev) => {
  // Filter out any previously added downloaded tracks
  const existing = prev.subtitleUrls.filter((s) => s.source !== "downloaded");
  const baseIndex = existing.length > 0 ? Math.max(...existing.map((s) => s.index)) + 1 : 0;
  const token = config.getAccessToken();
  const newTracks: PlayerSubtitleInfo[] = downloaded.map((dl, i) => ({
    index: baseIndex + i,
    id: dl.id,
    language: dl.language,
    codec: dl.format,
    label: `${dl.release_name} (${dl.provider})`,
    source: "downloaded" as const,
    hearing_impaired: dl.hearing_impaired,
    url: buildPlayerStreamUrl(
      config.apiBaseUrl,
      `/stream/${sid}/subtitles/${baseIndex + i}`,
      token,
      "direct",
      0,
    ),
  }));
  return { ...prev, subtitleUrls: [...existing, ...newTracks] };
});
```

URL absolutization, `silo-server/web/src/player/stream-url.ts` `buildPlayerStreamUrl` (lines 32–55) — non-absolute paths are prefixed with `apiBaseUrl`, and the token rides as a query param (browsers can't set headers on track fetches); `playMethod` is `"direct"` and `initialPosition` is `0`, so no `seek` param is ever added:

```ts
const params = new URLSearchParams();
if (token) { params.set("token", token); }
if (playMethod === "remux" && initialPosition > 0) { params.set("seek", initialPosition.toFixed(3)); }
const query = params.toString();
const base =
  streamPath.startsWith("http://") || streamPath.startsWith("https://")
    ? streamPath
    : `${apiBaseUrl}${streamPath}`;
return `${base}${query ? `?${query}` : ""}`;
```

with `apiBaseUrl: "/api/v1"` (`silo-server/web/src/playback/WatchPlaybackChrome.tsx:443`). So the web's final URL is `/api/v1/stream/{sessionId}/subtitles/{index}?token=…`, site-relative.

**Android mapping decisions (each pinned to read code):**
- **Path:** identical — `/stream/{sessionId}/subtitles/{index}` under `/api/v1` (route confirmed at `silo-server/internal/api/router.go:1990` inside the `r.Route("/api/v1", …)` block at line 1346). The session id is required for the path, and `PlayerSubtitleInfo` does not carry one, so the function takes a `sessionId` parameter (the web closes over `sessionIdRef.current` the same way).
- **Made absolute against `serverUrl`:** the web is same-origin so `/api/v1/...` suffices; Android prefixes the server origin (`TokenManagerImpl.getServerUrl()` returns a trimmed origin). Emitting an absolute `http(s)` URL is safe through the existing pipeline because `SubtitleManager.resolveUrl` (`android-shared/.../player/SubtitleManager.kt:211-217`) passes absolute URLs through untouched.
- **No `?token=`:** Android subtitle fetches go through `AuthenticatedDataSourceFactory` → shared OkHttp client whose `MediaAuthInterceptor` injects `Authorization: Bearer` + profile headers and handles 401-refresh (`android-shared/.../player/MediaAuthInterceptor.kt`). A token query param would be redundant, would leak into logs/caches, and would break the function's purity. This is the same divergence the rest of the Android player already makes for `stream_url` itself.
- **Dedupe:** `PlayerSubtitleInfo` (`shared/.../model/playback/PlaybackModels.kt:37-46`) carries `index/language/codec/label/source/forced/url` — **no downloaded-subtitle id** — so identity-based dedupe is impossible and also not what the web does: the web strips every `source === "downloaded"` track and rebuilds them all from the fresh `GET /subtitles/{media_file_id}` list. We mirror that filter-and-replace exactly (it is also what the server itself emits for session-start track lists: `Source: "downloaded"`, `Label: dl.ReleaseName + " (" + dl.Provider + ")"` — `silo-server/internal/api/handlers/playback.go:1501-1512`).
- **Indices:** continue from `max(existing.index) + 1` (not list size — server-side burn-in skipping can leave gaps, see `playback.go:1484-1486`), exactly like the web's `Math.max(...)+1`. Index drives both the stream path the server resolves and selection: `SubtitleManager.selectSubtitle` walks TEXT track groups in order and sidecar configurations are appended in list order by `buildSubtitleConfigurations`, so appending new tracks at the end leaves existing selections stable. Auto-select after download: the track for `DownloadedSubtitle.id == X` sits at merged position `base.size + downloaded.indexOfFirst { it.id == X }` (callers in the mobile/TV viewmodel tasks rely on this documented ordering).

- [ ] **Step 1: Write the failing test**

```kotlin
// shared/src/commonTest/kotlin/com/continuum/app/model/playback/SubtitleTrackMergeTest.kt
package com.continuum.app.model.playback

import com.continuum.app.model.subtitles.DownloadedSubtitle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame

class SubtitleTrackMergeTest {

    private fun track(
        index: Int,
        source: String?,
        url: String = "/stream/sess-1/subtitles/$index.srt",
        label: String? = "Track $index",
    ) = PlayerSubtitleInfo(
        index = index,
        language = "en",
        codec = "subrip",
        label = label,
        source = source,
        forced = null,
        url = url,
    )

    private fun downloaded(
        id: Int,
        language: String = "en",
        format: String = "srt",
        releaseName: String = "Release.$id",
        provider: String = "opensubtitles",
    ) = DownloadedSubtitle(
        id = id,
        mediaFileId = 1048,
        provider = provider,
        language = language,
        format = format,
        releaseName = releaseName,
    )

    @Test
    fun `appends downloaded tracks after existing with web-matching fields`() {
        val existing = listOf(track(0, source = "external"), track(1, source = "embedded"))

        val merged = mergeDownloadedSubtitles(
            existing = existing,
            downloaded = listOf(
                downloaded(id = 312, language = "nl", format = "srt",
                    releaseName = "Dune.Part.Three.WEB-DL", provider = "opensubtitles"),
                downloaded(id = 313, language = "en", format = "ass",
                    releaseName = "Dune Part Three", provider = "subdl"),
            ),
            sessionId = "sess-1",
            serverUrl = "https://silo.example",
        )

        assertEquals(4, merged.size)
        assertEquals(existing, merged.take(2))  // existing tracks untouched, order preserved

        val first = merged[2]
        assertEquals(2, first.index)  // continues the existing sequence
        assertEquals("nl", first.language)
        assertEquals("srt", first.codec)
        assertEquals("Dune.Part.Three.WEB-DL (opensubtitles)", first.label)  // `${release_name} (${provider})`
        assertEquals("downloaded", first.source)
        assertNull(first.forced)
        // Web: buildPlayerStreamUrl(apiBaseUrl, `/stream/${sid}/subtitles/${index}`, …)
        // with apiBaseUrl "/api/v1", absolutized against the server origin on Android.
        assertEquals("https://silo.example/api/v1/stream/sess-1/subtitles/2", first.url)

        val second = merged[3]
        assertEquals(3, second.index)
        assertEquals("Dune Part Three (subdl)", second.label)
        assertEquals("https://silo.example/api/v1/stream/sess-1/subtitles/3", second.url)
    }

    @Test
    fun `empty downloaded list returns existing unchanged`() {
        val existing = listOf(track(0, source = "embedded"))

        val merged = mergeDownloadedSubtitles(
            existing = existing,
            downloaded = emptyList(),
            sessionId = "sess-1",
            serverUrl = "https://silo.example",
        )

        assertSame(existing, merged)
    }

    @Test
    fun `re-merge replaces previously merged downloaded tracks instead of duplicating`() {
        val firstMerge = mergeDownloadedSubtitles(
            existing = listOf(track(0, source = "embedded")),
            downloaded = listOf(downloaded(id = 312)),
            sessionId = "sess-1",
            serverUrl = "https://silo.example",
        )
        assertEquals(2, firstMerge.size)

        val secondMerge = mergeDownloadedSubtitles(
            existing = firstMerge,
            downloaded = listOf(downloaded(id = 312), downloaded(id = 313)),
            sessionId = "sess-1",
            serverUrl = "https://silo.example",
        )

        assertEquals(3, secondMerge.size)  // 1 embedded + 2 downloaded, no duplicate of 312
        assertEquals(listOf("embedded", "downloaded", "downloaded"), secondMerge.map { it.source })
        assertEquals(listOf(0, 1, 2), secondMerge.map { it.index })
    }

    @Test
    fun `base index continues from max existing index not list size`() {
        // Server-side burn-in skipping can leave index gaps (playback.go:1484).
        val existing = listOf(track(0, source = "external"), track(3, source = "embedded"))

        val merged = mergeDownloadedSubtitles(
            existing = existing,
            downloaded = listOf(downloaded(id = 312)),
            sessionId = "sess-1",
            serverUrl = "https://silo.example",
        )

        assertEquals(4, merged.last().index)  // max(0,3)+1, not size (2)
        assertEquals("https://silo.example/api/v1/stream/sess-1/subtitles/4", merged.last().url)
    }

    @Test
    fun `starts at index zero when there are no existing tracks`() {
        val merged = mergeDownloadedSubtitles(
            existing = emptyList(),
            downloaded = listOf(downloaded(id = 312)),
            sessionId = "sess-1",
            serverUrl = "https://silo.example",
        )

        assertEquals(0, merged.single().index)
        assertEquals("https://silo.example/api/v1/stream/sess-1/subtitles/0", merged.single().url)
    }

    @Test
    fun `trims trailing slash on server url`() {
        val merged = mergeDownloadedSubtitles(
            existing = emptyList(),
            downloaded = listOf(downloaded(id = 312)),
            sessionId = "sess-1",
            serverUrl = "https://silo.example/",
        )

        assertEquals("https://silo.example/api/v1/stream/sess-1/subtitles/0", merged.single().url)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

```bash
./gradlew :shared:testDebugUnitTest --tests com.continuum.app.model.playback.SubtitleTrackMergeTest
```

Expected: `> Task :shared:compileDebugUnitTestKotlinAndroid FAILED` with `Unresolved reference 'mergeDownloadedSubtitles'`.

- [ ] **Step 3: Implementation**

```kotlin
// shared/src/commonMain/kotlin/com/continuum/app/model/playback/SubtitleTrackMerge.kt
package com.continuum.app.model.playback

import com.continuum.app.model.subtitles.DownloadedSubtitle

/** `source` value the server (and web client) use for provider-downloaded tracks. */
const val SUBTITLE_SOURCE_DOWNLOADED = "downloaded"

/**
 * Merges server-stored downloaded subtitles into a playback session's track
 * list without restarting the session. Pure; shared by the mobile and TV
 * players' `refreshSubtitles(autoSelectSubtitleId)`.
 *
 * Mirrors the web reference exactly (web/src/player/hooks/usePlaybackSession.ts
 * `refreshSubtitles`, lines ~473-514):
 *  - strips every previously merged `source == "downloaded"` track and
 *    rebuilds them all from the fresh GET /subtitles/{media_file_id} list
 *    (PlayerSubtitleInfo carries no downloaded-subtitle id, and the web
 *    dedupes by source replacement, not identity)
 *  - new indices continue from `max(existing.index) + 1` — not the list size,
 *    since server-side burn-in skipping can leave index gaps — starting at 0
 *    when there are no remaining tracks
 *  - label is `"${release_name} (${provider})"`, codec is the stored format
 *  - the track URL is the web's `/stream/{sessionId}/subtitles/{index}` path
 *    under `/api/v1` (web/src/player/stream-url.ts `buildPlayerStreamUrl`
 *    with apiBaseUrl "/api/v1"), absolutized against [serverUrl] because the
 *    Android player is not same-origin. Unlike the web, no `?token=` query is
 *    appended: the player's OkHttp stack injects Authorization headers via
 *    MediaAuthInterceptor, and SubtitleManager.resolveUrl passes absolute
 *    URLs through untouched.
 *
 * Existing tracks keep their list positions, so the players' position-based
 * text-track selection (SubtitleManager.selectSubtitle) survives the merge.
 * The track for a given [DownloadedSubtitle.id] ends up at merged position
 * `mergedSize - downloaded.size + downloaded.indexOfFirst { it.id == id }`,
 * which is what auto-select-after-download callers rely on.
 */
fun mergeDownloadedSubtitles(
    existing: List<PlayerSubtitleInfo>,
    downloaded: List<DownloadedSubtitle>,
    sessionId: String,
    serverUrl: String,
): List<PlayerSubtitleInfo> {
    if (downloaded.isEmpty()) return existing

    val base = existing.filter { it.source != SUBTITLE_SOURCE_DOWNLOADED }
    val baseIndex = (base.maxOfOrNull { it.index } ?: -1) + 1
    val apiBase = serverUrl.trimEnd('/') + "/api/v1"

    val newTracks = downloaded.mapIndexed { i, dl ->
        val index = baseIndex + i
        PlayerSubtitleInfo(
            index = index,
            language = dl.language,
            codec = dl.format,
            label = "${dl.releaseName} (${dl.provider})",
            source = SUBTITLE_SOURCE_DOWNLOADED,
            forced = null,
            url = "$apiBase/stream/$sessionId/subtitles/$index",
        )
    }

    return base + newTracks
}
```

- [ ] **Step 4: Run tests**

```bash
./gradlew :shared:testDebugUnitTest --tests com.continuum.app.model.playback.SubtitleTrackMergeTest
./gradlew :shared:testDebugUnitTest
```

- [ ] **Step 5: Commit**

```bash
git add \
  shared/src/commonMain/kotlin/com/continuum/app/model/playback/SubtitleTrackMerge.kt \
  shared/src/commonTest/kotlin/com/continuum/app/model/playback/SubtitleTrackMergeTest.kt
git commit -m "Add shared downloaded-subtitle track merge

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>"
```

## Section M: Mobile player UI

### Dependencies (assumed shared layer — verify against what actually landed before executing)

The shared layer (sub-project tasks for `shared/`) is assumed to land first. The executor MUST open the landed files and adapt names/signatures if they differ. Assumed API:

```kotlin
// shared/src/commonMain/kotlin/com/continuum/app/model/subtitles/SubtitleModels.kt
@Serializable data class SubtitleResult(
    val id: String,                                   // provider-scoped id (string on the wire)
    val provider: String,                             // "opensubtitles" | "subdl" | "subsource"
    val language: String,
    @SerialName("release_name") val releaseName: String,
    val format: String,
    val score: Int,
    val downloads: Int = 0,
    @SerialName("hearing_impaired") val hearingImpaired: Boolean = false,
    @SerialName("upload_date") val uploadDate: String? = null,
)
@Serializable data class SubtitleSearchResponse(val results: List<SubtitleResult> = emptyList(), val warnings: List<String>? = null)
@Serializable data class DownloadedSubtitle(
    val id: Int,
    @SerialName("media_file_id") val mediaFileId: Int,
    val provider: String, val language: String, val format: String,
    @SerialName("release_name") val releaseName: String,
    val score: Int = 0,
    @SerialName("hearing_impaired") val hearingImpaired: Boolean = false,
    @SerialName("created_at") val createdAt: String? = null,
)
@Serializable data class AiStatus(val enabled: Boolean = false, @SerialName("transcribe_enabled") val transcribeEnabled: Boolean = false)
@Serializable data class AiQuota(val limited: Boolean = false, val limit: Int = 0, val used: Int = 0, val remaining: Int = 0, val period: String = "day")
@Serializable data class SubtitleAiJob(
    val id: Int, @SerialName("media_file_id") val mediaFileId: Int, val kind: String,
    @SerialName("source_index") val sourceIndex: Int,
    @SerialName("source_language") val sourceLanguage: String? = null,
    @SerialName("target_language") val targetLanguage: String? = null,
    val status: String,                               // pending|running|completed|failed|cancelled
    val progress: Double = 0.0,
    @SerialName("progress_message") val progressMessage: String? = null,
    @SerialName("result_subtitle_id") val resultSubtitleId: Int? = null,
    @SerialName("error_message") val errorMessage: String? = null,
)

// shared/src/commonMain/kotlin/com/continuum/app/repository/SubtitlesRepository.kt
class SubtitlesRepository(...) {
    suspend fun search(mediaFileId: Int, languages: List<String>): ApiResult<SubtitleSearchResponse>
    suspend fun download(mediaFileId: Int, result: SubtitleResult): ApiResult<DownloadedSubtitle>
    suspend fun listDownloaded(mediaFileId: Int): ApiResult<List<DownloadedSubtitle>>
    suspend fun aiStatus(): ApiResult<AiStatus>
    suspend fun aiQuota(): ApiResult<AiQuota>
    suspend fun translate(
        mediaFileId: Int, kind: String, sourceIndex: Int,
        sourceLanguage: String, targetLanguage: String, startPosition: Double,
    ): ApiResult<SubtitleAiJob>                       // 429 surfaces as ApiResult.Error(code = 429, ...)
    suspend fun cancelJob(jobId: Int): ApiResult<Unit>
    /** Suspends until terminal; transient errors retry; cancellable via coroutine cancellation. */
    suspend fun pollJob(jobId: Int, intervalMs: Long = 1_000, onUpdate: (SubtitleAiJob) -> Unit): SubtitleJobOutcome
}
sealed class SubtitleJobOutcome {
    data class Completed(val job: SubtitleAiJob) : SubtitleJobOutcome()
    data class Failed(val job: SubtitleAiJob?) : SubtitleJobOutcome()
    data class Cancelled(val job: SubtitleAiJob?) : SubtitleJobOutcome()
}

// Pure merge helper (spec says shared/ or android-shared/). The prompt named the third
// param `serverUrl`, but the web-pinned URL is RELATIVE and needs the SESSION id:
//   /stream/{sessionId}/subtitles/{index}.{ext}    (web: usePlaybackSession.ts refreshSubtitles,
//   server: handlers/playback.go buildSubtitleURLs — identical format incl. extension)
// Relative URLs resolve through SubtitleManager.resolveUrl exactly like server-provided
// session tracks, so auth (MediaAuthInterceptor bearer header) applies uniformly.
// If the landed helper takes serverUrl and emits absolute URLs instead, pass
// uiState.serverUrl and keep everything else identical.
fun mergeDownloadedSubtitles(
    existing: List<PlayerSubtitleInfo>,
    downloaded: List<DownloadedSubtitle>,
    sessionId: String,
): List<PlayerSubtitleInfo>
// Contract assumed (web parity): drops existing source=="downloaded" entries, keeps the rest,
// appends downloaded in listing order with index = max(existing.index)+1.., label
// "{releaseName} ({provider})", source = "downloaded", codec = format.
```

Verified mobile facts the tasks below build on (READ from the real code):
- `PlayerViewModel.PlayerUiState` holds `subtitleTracks: List<PlayerSubtitleInfo>`, `selectedSubtitleIndex` (position in that list, -1 = off), `sessionId`, `serverUrl`, `position` (seconds); the file id flows `WatchDetail.versions[selectedVersionIndex].fileId` (set by `applySessionToState` / `onSelectVersion`) — there is no stored `mediaFileId` today.
- Subtitle configs are **baked into the MediaItem** at start: `PlayerScreen`'s `LaunchedEffect(mediaController, uiState.streamUrl, uiState.playMethod)` → `playerFactory.buildMediaItem(..., subtitles = uiState.subtitleTracks)` → `SubtitleManager.buildSubtitleConfigurations`. Adding a track mid-playback therefore requires rebuilding the MediaItem and re-preparing at the current position (Task 2).
- Subtitle selection is applied by `LaunchedEffect(mediaController, uiState.selectedSubtitleIndex)` → `subtitleManager.selectSubtitle(controller, index)`, which positionally matches the Nth TEXT track group.
- `PlayerViewModel` is registered in `androidApp/.../di/AndroidModule.kt:125` as `factory { PlayerViewModel(get() × 10) }`.

Commands used throughout: `./gradlew :androidApp:compileDebugKotlinAndroid`, `./gradlew :androidApp:testDebugUnitTest`, `./gradlew :androidApp:assembleDebug`.

---

### Task M1: LanguageNames helper + score-badge bucketing

**Files:**
- Create: `androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/util/LanguageNames.kt`
- Create: `androidApp/src/androidUnitTest/kotlin/com/continuum/app/android/ui/util/LanguageNamesTest.kt`
- Create: `androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/player/SubtitleScoreBadge.kt`
- Create: `androidApp/src/androidUnitTest/kotlin/com/continuum/app/android/ui/screens/player/SubtitleScoreBadgeTest.kt`

- [ ] **Step 1: Write the failing test**

`androidApp/src/androidUnitTest/kotlin/com/continuum/app/android/ui/util/LanguageNamesTest.kt`:

```kotlin
package com.continuum.app.android.ui.util

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LanguageNamesTest {

    @Test
    fun knownTwoLetterCodesResolve() {
        assertEquals("English", LanguageNames.displayName("en"))
        assertEquals("German", LanguageNames.displayName("de"))
        assertEquals("Japanese", LanguageNames.displayName("ja"))
    }

    @Test
    fun knownThreeLetterCodesResolve() {
        // Embedded track metadata commonly carries ISO 639-2 codes.
        assertEquals("German", LanguageNames.displayName("ger"))
        assertEquals("German", LanguageNames.displayName("deu"))
        assertEquals("English", LanguageNames.displayName("eng"))
    }

    @Test
    fun unknownCodesFallBackToUppercasedCode() {
        assertEquals("XX", LanguageNames.displayName("xx"))
        assertEquals("ZZZ", LanguageNames.displayName("zzz"))
    }

    @Test
    fun blankAndNullAreUnknown() {
        assertEquals("Unknown", LanguageNames.displayName(null))
        assertEquals("Unknown", LanguageNames.displayName("  "))
    }

    @Test
    fun caseAndWhitespaceAreNormalized() {
        assertEquals("French", LanguageNames.displayName(" FR "))
    }

    @Test
    fun dropdownOptionsCoverCommonLanguagesSortedByLabel() {
        assertTrue(LanguageNames.dropdownOptions.size >= 30)
        val labels = LanguageNames.dropdownOptions.map { it.second }
        assertEquals(labels.sorted(), labels)
        assertTrue(LanguageNames.dropdownOptions.any { it.first == "en" && it.second == "English" })
    }

    @Test
    fun searchCodeNormalizesToTwoLetterApiCodes() {
        assertEquals("en", LanguageNames.searchCode("en"))
        assertEquals("de", LanguageNames.searchCode("ger"))
        assertEquals("en", LanguageNames.searchCode("eng"))
    }

    @Test
    fun searchCodeDefaultsToEnglish() {
        assertEquals("en", LanguageNames.searchCode(null))
        assertEquals("en", LanguageNames.searchCode(""))
        assertEquals("en", LanguageNames.searchCode("zz"))
    }
}
```

`androidApp/src/androidUnitTest/kotlin/com/continuum/app/android/ui/screens/player/SubtitleScoreBadgeTest.kt`:

```kotlin
package com.continuum.app.android.ui.screens.player

import kotlin.test.Test
import kotlin.test.assertEquals

class SubtitleScoreBadgeTest {

    @Test
    fun seventyAndAboveIsHigh() {
        assertEquals(ScoreBadgeBucket.High, scoreBadgeBucket(70))
        assertEquals(ScoreBadgeBucket.High, scoreBadgeBucket(100))
    }

    @Test
    fun fortyToSixtyNineIsMedium() {
        assertEquals(ScoreBadgeBucket.Medium, scoreBadgeBucket(40))
        assertEquals(ScoreBadgeBucket.Medium, scoreBadgeBucket(69))
    }

    @Test
    fun belowFortyIsLow() {
        assertEquals(ScoreBadgeBucket.Low, scoreBadgeBucket(39))
        assertEquals(ScoreBadgeBucket.Low, scoreBadgeBucket(0))
        assertEquals(ScoreBadgeBucket.Low, scoreBadgeBucket(-5))
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

```
./gradlew :androidApp:testDebugUnitTest --tests "com.continuum.app.android.ui.util.LanguageNamesTest" --tests "com.continuum.app.android.ui.screens.player.SubtitleScoreBadgeTest"
```
Expected: compilation failure (`LanguageNames` / `scoreBadgeBucket` unresolved).

- [ ] **Step 3: Implementation**

`androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/util/LanguageNames.kt`:

```kotlin
package com.continuum.app.android.ui.util

/**
 * ISO 639 language code → English display name, mirroring the web player's
 * `web/src/player/utils/languageNames.ts` so both clients label subtitle
 * languages identically. The 2-letter (639-1) codes are what the subtitle
 * search / AI translate APIs accept; the 3-letter (639-2) map exists because
 * embedded track metadata commonly carries "eng"/"ger"-style codes.
 */
object LanguageNames {

    private val twoLetter: Map<String, String> = mapOf(
        "en" to "English", "es" to "Spanish", "fr" to "French", "de" to "German",
        "it" to "Italian", "pt" to "Portuguese", "nl" to "Dutch", "pl" to "Polish",
        "ru" to "Russian", "zh" to "Chinese", "ja" to "Japanese", "ko" to "Korean",
        "ar" to "Arabic", "tr" to "Turkish", "sv" to "Swedish", "da" to "Danish",
        "no" to "Norwegian", "fi" to "Finnish", "hu" to "Hungarian", "cs" to "Czech",
        "ro" to "Romanian", "he" to "Hebrew", "th" to "Thai", "vi" to "Vietnamese",
        "el" to "Greek", "bg" to "Bulgarian", "hr" to "Croatian", "sk" to "Slovak",
        "sl" to "Slovenian", "uk" to "Ukrainian", "id" to "Indonesian", "ms" to "Malay",
        "hi" to "Hindi", "ta" to "Tamil", "te" to "Telugu", "bn" to "Bengali",
        "fa" to "Persian",
    )

    private val threeLetter: Map<String, String> = mapOf(
        "eng" to "English", "spa" to "Spanish", "fre" to "French", "fra" to "French",
        "ger" to "German", "deu" to "German", "ita" to "Italian", "por" to "Portuguese",
        "dut" to "Dutch", "nld" to "Dutch", "pol" to "Polish", "rus" to "Russian",
        "chi" to "Chinese", "zho" to "Chinese", "jpn" to "Japanese", "kor" to "Korean",
        "ara" to "Arabic", "tur" to "Turkish", "swe" to "Swedish", "dan" to "Danish",
        "nor" to "Norwegian", "fin" to "Finnish", "hun" to "Hungarian", "cze" to "Czech",
        "ces" to "Czech", "rum" to "Romanian", "ron" to "Romanian", "heb" to "Hebrew",
        "tha" to "Thai", "vie" to "Vietnamese", "gre" to "Greek", "ell" to "Greek",
        "bul" to "Bulgarian", "hrv" to "Croatian", "slo" to "Slovak", "slk" to "Slovak",
        "slv" to "Slovenian", "ukr" to "Ukrainian", "ind" to "Indonesian", "may" to "Malay",
        "msa" to "Malay", "hin" to "Hindi", "tam" to "Tamil", "tel" to "Telugu",
        "ben" to "Bengali", "per" to "Persian", "fas" to "Persian",
    )

    /**
     * Options for the language pickers (search + AI translate target):
     * (2-letter code, display name), sorted by display name — web parity.
     */
    val dropdownOptions: List<Pair<String, String>> =
        twoLetter.entries.map { it.key to it.value }.sortedBy { it.second }

    /**
     * Display name for any 2- or 3-letter code. Unknown codes fall back to
     * the uppercased code; null/blank renders "Unknown".
     */
    fun displayName(code: String?): String {
        val lower = code?.trim()?.lowercase().orEmpty()
        if (lower.isEmpty()) return "Unknown"
        return twoLetter[lower] ?: threeLetter[lower] ?: lower.uppercase()
    }

    /**
     * Normalizes a profile/track language code to a 2-letter code the
     * subtitle APIs accept. Unmappable codes default to "en" (web default).
     */
    fun searchCode(code: String?): String {
        val lower = code?.trim()?.lowercase().orEmpty()
        if (lower.isEmpty()) return "en"
        if (lower in twoLetter) return lower
        val name = threeLetter[lower] ?: return "en"
        return twoLetter.entries.firstOrNull { it.value == name }?.key ?: "en"
    }
}
```

`androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/player/SubtitleScoreBadge.kt`:

```kotlin
package com.continuum.app.android.ui.screens.player

/**
 * Web-parity score buckets for subtitle search results
 * (`web/src/player/components/SubtitleSearchModal.tsx` scoreColor):
 * ≥70 High (green #22c55e), ≥40 Medium (amber #eab308), else Low (red #ef4444).
 * Pure so it's unit-testable; the composable maps buckets to colors.
 */
enum class ScoreBadgeBucket { High, Medium, Low }

internal fun scoreBadgeBucket(score: Int): ScoreBadgeBucket = when {
    score >= 70 -> ScoreBadgeBucket.High
    score >= 40 -> ScoreBadgeBucket.Medium
    else -> ScoreBadgeBucket.Low
}
```

- [ ] **Step 4: Run tests**

```
./gradlew :androidApp:testDebugUnitTest --tests "com.continuum.app.android.ui.util.LanguageNamesTest" --tests "com.continuum.app.android.ui.screens.player.SubtitleScoreBadgeTest"
./gradlew :androidApp:compileDebugKotlinAndroid
```
Manual checklist: n/a (pure helpers, fully unit-tested).

- [ ] **Step 5: Commit**

```
git add androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/util/LanguageNames.kt androidApp/src/androidUnitTest/kotlin/com/continuum/app/android/ui/util/LanguageNamesTest.kt androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/player/SubtitleScoreBadge.kt androidApp/src/androidUnitTest/kotlin/com/continuum/app/android/ui/screens/player/SubtitleScoreBadgeTest.kt
git commit -m "Add language-name and subtitle-score helpers for mobile subtitle suite

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>"
```

---

### Task M2: PlayerViewModel subtitle tooling + mid-playback Media3 track refresh

**Files:**
- Modify: `androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/player/PlayerViewModel.kt`
- Modify: `androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/player/PlayerScreen.kt`
- Modify: `androidApp/src/androidMain/kotlin/com/continuum/app/android/di/AndroidModule.kt`
- Create: `androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/player/SubtitleTrackSelection.kt`
- Create: `androidApp/src/androidUnitTest/kotlin/com/continuum/app/android/ui/screens/player/SubtitleTrackSelectionTest.kt`

- [ ] **Step 1: Write the failing test**

`androidApp/src/androidUnitTest/kotlin/com/continuum/app/android/ui/screens/player/SubtitleTrackSelectionTest.kt` (adapt the `DownloadedSubtitle` constructor to the landed model):

```kotlin
package com.continuum.app.android.ui.screens.player

import com.continuum.app.model.playback.PlayerSubtitleInfo
import com.continuum.app.model.subtitles.DownloadedSubtitle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SubtitleTrackSelectionTest {

    private fun track(index: Int, source: String? = null) = PlayerSubtitleInfo(
        index = index,
        language = "en",
        codec = "srt",
        label = "T$index",
        source = source,
        forced = null,
        url = "/stream/s/subtitles/$index.vtt",
    )

    private fun dl(id: Int) = DownloadedSubtitle(
        id = id,
        mediaFileId = 7,
        provider = "opensubtitles",
        language = "en",
        format = "srt",
        releaseName = "Rel.$id",
    )

    @Test
    fun findsDownloadedTrackAfterExistingTracks() {
        // merged = 2 session tracks + 2 downloaded appended in listing order
        val merged = listOf(track(0), track(1), track(2, "downloaded"), track(3, "downloaded"))
        val downloaded = listOf(dl(11), dl(22))
        assertEquals(2, downloadedTrackIndex(merged, downloaded, subtitleId = 11))
        assertEquals(3, downloadedTrackIndex(merged, downloaded, subtitleId = 22))
    }

    @Test
    fun worksWhenAllTracksAreDownloaded() {
        val merged = listOf(track(0, "downloaded"), track(1, "downloaded"))
        val downloaded = listOf(dl(5), dl(6))
        assertEquals(0, downloadedTrackIndex(merged, downloaded, subtitleId = 5))
    }

    @Test
    fun unknownIdReturnsNull() {
        val merged = listOf(track(0), track(1, "downloaded"))
        val downloaded = listOf(dl(9))
        assertNull(downloadedTrackIndex(merged, downloaded, subtitleId = 999))
    }

    @Test
    fun emptyDownloadListReturnsNull() {
        assertNull(downloadedTrackIndex(listOf(track(0)), emptyList(), subtitleId = 1))
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

```
./gradlew :androidApp:testDebugUnitTest --tests "com.continuum.app.android.ui.screens.player.SubtitleTrackSelectionTest"
```
Expected: compilation failure (`downloadedTrackIndex` unresolved).

- [ ] **Step 3: Implementation**

**(a)** Create `androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/player/SubtitleTrackSelection.kt`:

```kotlin
package com.continuum.app.android.ui.screens.player

import com.continuum.app.model.playback.PlayerSubtitleInfo
import com.continuum.app.model.subtitles.DownloadedSubtitle

/**
 * Position (in the merged subtitleTracks list) of the downloaded subtitle
 * with [subtitleId], used for auto-select after a download / AI job completes.
 *
 * Relies on the mergeDownloadedSubtitles contract (web parity,
 * usePlaybackSession.ts refreshSubtitles): downloaded entries are appended in
 * listing order AFTER all non-downloaded tracks, so the merged position is
 * (merged.size - downloaded.size) + positionInDownloadedListing.
 */
internal fun downloadedTrackIndex(
    merged: List<PlayerSubtitleInfo>,
    downloaded: List<DownloadedSubtitle>,
    subtitleId: Int,
): Int? {
    val pos = downloaded.indexOfFirst { it.id == subtitleId }
    if (pos < 0) return null
    val start = merged.size - downloaded.size
    return (start + pos).takeIf { it in merged.indices }
}
```

**(b)** `PlayerViewModel.kt` changes.

Add imports:

```kotlin
import com.continuum.app.model.subtitles.AiQuota
import com.continuum.app.model.subtitles.AiStatus
import com.continuum.app.model.subtitles.SubtitleAiJob
import com.continuum.app.model.subtitles.SubtitleResult
import com.continuum.app.repository.SubtitleJobOutcome
import com.continuum.app.repository.SubtitlesRepository
import com.continuum.app.repository.mergeDownloadedSubtitles
```

Add constructor parameter (after `sleepTimer`):

```kotlin
    // Subtitle suite (search/download + AI translate):
    private val subtitlesRepository: SubtitlesRepository,
```

In `PlayerUiState`, add a field and a derived property:

```kotlin
        /**
         * Bumped whenever refreshSubtitles merges new downloaded tracks into
         * [subtitleTracks]. PlayerScreen watches this to rebuild the MediaItem
         * (subtitle configs are baked in at build time) and re-prepare at the
         * current position.
         */
        val subtitleRefreshNonce: Int = 0,
```

```kotlin
    // inside PlayerUiState body, after the constructor properties:
        /**
         * Media file id of the active version — the id the subtitle
         * search/download/AI endpoints key on. Flows from
         * WatchDetail.versions[selectedVersionIndex].fileId (set by
         * applySessionToState and onSelectVersion).
         */
        val mediaFileId: Int?
            get() = versions.getOrNull(selectedVersionIndex)?.fileId
```

Add the tools state + jobs (near the other `private var ... Job?` fields):

```kotlin
    /** UI state for the subtitle search + AI translate sheets. */
    data class SubtitleToolsUiState(
        /** null until probed (lazily, on first TracksSheet open); fetch failure → AiStatus(false, false). */
        val aiStatus: AiStatus? = null,
        val searchLoading: Boolean = false,
        val searchAttempted: Boolean = false,
        val searchResults: List<SubtitleResult> = emptyList(),
        val searchWarnings: List<String> = emptyList(),
        val searchError: String? = null,
        /** "{provider}:{id}" of the result currently downloading; null otherwise. */
        val downloadingKey: String? = null,
        /** One-shot: a download finished and was auto-selected — sheet dismisses on this. */
        val downloadCompleted: Boolean = false,
        /** Transcription quota; null = unlimited / not applicable / fetch failed (counter hidden). */
        val quota: AiQuota? = null,
        val translateSubmitting: Boolean = false,
        val translateError: String? = null,
        /** In-flight AI job with live progress; null when idle. */
        val activeJob: SubtitleAiJob? = null,
        /** One-shot: an AI job completed and its track was auto-selected — sheet dismisses on this. */
        val jobJustCompleted: Boolean = false,
    )

    private val _subtitleTools = MutableStateFlow(SubtitleToolsUiState())
    val subtitleTools: StateFlow<SubtitleToolsUiState> = _subtitleTools.asStateFlow()

    private var aiStatusFetched = false
    private var searchJob: Job? = null
    private var aiJobHandle: Job? = null
```

Add the new functions (after `onSelectAudio`):

```kotlin
    // ---- Subtitle suite: search / download / AI translate -----------------------

    /**
     * Lazy one-shot AI status probe, mirroring the web: fetched the first time
     * the TracksSheet opens; on failure both flags stay false and the
     * "Translate with AI…" row is hidden (no error surfaced).
     */
    fun onTracksSheetOpened() {
        if (aiStatusFetched) return
        aiStatusFetched = true
        viewModelScope.launch {
            val status = when (val r = subtitlesRepository.aiStatus()) {
                is ApiResult.Success -> r.data
                else -> AiStatus(enabled = false, transcribeEnabled = false)
            }
            _subtitleTools.update { it.copy(aiStatus = status) }
        }
    }

    /** Provider search for the active version's media file. */
    fun searchSubtitles(language: String) {
        val mediaFileId = _uiState.value.mediaFileId ?: return
        searchJob?.cancel()
        _subtitleTools.update {
            it.copy(
                searchLoading = true,
                searchAttempted = true,
                searchError = null,
                searchResults = emptyList(),
                searchWarnings = emptyList(),
            )
        }
        searchJob = viewModelScope.launch {
            when (val r = subtitlesRepository.search(mediaFileId, listOf(language))) {
                is ApiResult.Success -> _subtitleTools.update {
                    it.copy(
                        searchLoading = false,
                        searchResults = r.data.results,
                        searchWarnings = r.data.warnings.orEmpty(),
                    )
                }
                // No capability probe exists: "no providers configured" arrives
                // as a plain server error — surface its text verbatim.
                is ApiResult.Error -> _subtitleTools.update {
                    it.copy(searchLoading = false, searchError = r.message)
                }
                is ApiResult.NetworkError -> _subtitleTools.update {
                    it.copy(searchLoading = false, searchError = "Network error: ${r.exception.message}")
                }
            }
        }
    }

    /** Download a search result; on success merge + auto-select the new track. */
    fun downloadSubtitle(result: SubtitleResult) {
        val mediaFileId = _uiState.value.mediaFileId ?: return
        val key = "${result.provider}:${result.id}"
        _subtitleTools.update { it.copy(downloadingKey = key, searchError = null) }
        viewModelScope.launch {
            when (val r = subtitlesRepository.download(mediaFileId, result)) {
                is ApiResult.Success -> {
                    doRefreshSubtitles(autoSelectSubtitleId = r.data.id)
                    _subtitleTools.update { it.copy(downloadingKey = null, downloadCompleted = true) }
                }
                is ApiResult.Error -> _subtitleTools.update {
                    it.copy(downloadingKey = null, searchError = r.message)
                }
                is ApiResult.NetworkError -> _subtitleTools.update {
                    it.copy(downloadingKey = null, searchError = "Network error: ${r.exception.message}")
                }
            }
        }
    }

    /**
     * Web-parity track refresh (usePlaybackSession.ts refreshSubtitles): the
     * playback session is NOT restarted. We refetch the downloaded-subtitles
     * list, merge it into subtitleTracks via the shared pure helper, bump
     * subtitleRefreshNonce so PlayerScreen rebuilds the MediaItem in place,
     * and select the new track when [autoSelectSubtitleId] matches.
     */
    fun refreshSubtitles(autoSelectSubtitleId: Int? = null) {
        viewModelScope.launch { doRefreshSubtitles(autoSelectSubtitleId) }
    }

    private suspend fun doRefreshSubtitles(autoSelectSubtitleId: Int?) {
        val state = _uiState.value
        val mediaFileId = state.mediaFileId ?: return
        val sessionId = state.sessionId ?: return
        val downloaded = when (val r = subtitlesRepository.listDownloaded(mediaFileId)) {
            is ApiResult.Success -> r.data
            else -> return // best effort — refresh failure must not disrupt playback (web parity)
        }
        if (downloaded.isEmpty()) return
        val merged = mergeDownloadedSubtitles(
            existing = state.subtitleTracks,
            downloaded = downloaded,
            sessionId = sessionId,
        )
        val autoIndex = autoSelectSubtitleId?.let { id -> downloadedTrackIndex(merged, downloaded, id) }
        _uiState.update {
            it.copy(
                subtitleTracks = merged,
                subtitleRefreshNonce = it.subtitleRefreshNonce + 1,
                selectedSubtitleIndex = autoIndex ?: it.selectedSubtitleIndex,
            )
        }
    }

    /** Refresh the transcription quota; non-limited / failed lookups hide the counter (web parity). */
    fun refreshAiQuota() {
        viewModelScope.launch {
            val quota = when (val r = subtitlesRepository.aiQuota()) {
                is ApiResult.Success -> r.data.takeIf { it.limited }
                else -> null
            }
            _subtitleTools.update { it.copy(quota = quota) }
        }
    }

    /**
     * Start an AI job and poll it to a terminal state. Android passes the
     * current playhead as start_position and does NOT pass session_id — we
     * poll for completion instead of streaming live cues.
     */
    fun startAiJob(kind: String, sourceIndex: Int, sourceLanguage: String, targetLanguage: String) {
        val state = _uiState.value
        val mediaFileId = state.mediaFileId ?: return
        if (_subtitleTools.value.activeJob != null || _subtitleTools.value.translateSubmitting) return
        _subtitleTools.update { it.copy(translateSubmitting = true, translateError = null, jobJustCompleted = false) }
        aiJobHandle?.cancel()
        aiJobHandle = viewModelScope.launch {
            val result = subtitlesRepository.translate(
                mediaFileId = mediaFileId,
                kind = kind,
                sourceIndex = sourceIndex,
                sourceLanguage = sourceLanguage,
                targetLanguage = targetLanguage,
                startPosition = state.position,
            )
            when (result) {
                is ApiResult.Success -> {
                    _subtitleTools.update { it.copy(translateSubmitting = false, activeJob = result.data) }
                    val outcome = subtitlesRepository.pollJob(result.data.id) { update ->
                        _subtitleTools.update { it.copy(activeJob = update) }
                    }
                    when (outcome) {
                        is SubtitleJobOutcome.Completed -> {
                            doRefreshSubtitles(autoSelectSubtitleId = outcome.job.resultSubtitleId)
                            _subtitleTools.update { it.copy(activeJob = null, jobJustCompleted = true) }
                        }
                        is SubtitleJobOutcome.Failed -> _subtitleTools.update {
                            it.copy(activeJob = null, translateError = outcome.job?.errorMessage ?: "Job failed")
                        }
                        is SubtitleJobOutcome.Cancelled -> _subtitleTools.update { it.copy(activeJob = null) }
                    }
                }
                is ApiResult.Error -> {
                    // 429 = quota exhausted while our counter was stale — refresh
                    // so the banner and disabled button match the error shown.
                    if (result.code == 429) refreshAiQuota()
                    _subtitleTools.update { it.copy(translateSubmitting = false, translateError = result.message) }
                }
                is ApiResult.NetworkError -> _subtitleTools.update {
                    it.copy(translateSubmitting = false, translateError = "Network error: ${result.exception.message}")
                }
            }
        }
    }

    /** Cancel the in-flight AI job server-side; the poll loop then sees the terminal cancelled status. */
    fun cancelAiJob() {
        val job = _subtitleTools.value.activeJob ?: return
        viewModelScope.launch { subtitlesRepository.cancelJob(job.id) }
    }

    /** Search sheet dismissed — clear transient search state (results survive reopen). */
    fun onSearchSheetClosed() {
        searchJob?.cancel()
        _subtitleTools.update {
            it.copy(searchLoading = false, downloadingKey = null, downloadCompleted = false, searchError = null)
        }
    }

    /** Translate sheet dismissed — clear transient state. A running job keeps polling in the background. */
    fun onTranslateSheetClosed() {
        _subtitleTools.update {
            it.copy(translateSubmitting = false, translateError = null, jobJustCompleted = false)
        }
    }
```

In `onExit()`, add cancellation (player exit cancels in-flight poll — structured concurrency requirement from the spec):

```kotlin
    fun onExit() {
        viewModelScope.launch {
            sessionLifecycle.stop()
            controlsHideJob?.cancel()
            introObserverJob?.cancel()
            searchJob?.cancel()
            aiJobHandle?.cancel()
            introAutoSkipController.reset()
        }
    }
```

And in `onCleared()`, alongside the existing cancels: `searchJob?.cancel()` and `aiJobHandle?.cancel()`.

**(c)** `AndroidModule.kt` line 125 — add the eleventh dependency:

```kotlin
    factory { PlayerViewModel(get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get()) }
```

**(d)** `PlayerScreen.kt` — the Media3 mid-playback rebuild. This is the critical mechanism: subtitle configurations are baked into the MediaItem by `playerFactory.buildMediaItem(...)` in the `LaunchedEffect(mediaController, uiState.streamUrl, uiState.playMethod)` (lines 207–254), so new tracks can only appear by rebuilding the MediaItem and re-preparing. That effect is keyed on `streamUrl`/`playMethod` and will NOT re-run when only `subtitleTracks` changes (by design — it would reset to `startPosition`). Add a second effect keyed on the nonce, inserted directly after the existing media-item effect:

```kotlin
    // Mid-playback subtitle refresh (downloaded / AI-generated tracks).
    // Subtitle configs are baked into the MediaItem at build time, so when
    // refreshSubtitles merges new tracks it bumps subtitleRefreshNonce and we
    // rebuild the SAME stream's MediaItem with the enlarged subtitle list,
    // then re-prepare at the captured live position. setMediaItem(item, posMs)
    // preserves the playhead; playWhenReady is restored so a paused player
    // stays paused. The session is NOT restarted (web parity).
    LaunchedEffect(mediaController, uiState.subtitleRefreshNonce) {
        if (uiState.subtitleRefreshNonce == 0) return@LaunchedEffect
        val controller = mediaController ?: return@LaunchedEffect
        val streamUrl = uiState.streamUrl ?: return@LaunchedEffect
        val playMethod = uiState.playMethod ?: return@LaunchedEffect

        val resumePositionMs = controller.currentPosition
        val wasPlaying = controller.playWhenReady

        // Mirror the start effect's local-file preference so a rebuild never
        // silently switches a local-file playback back to the remote stream.
        val activeFileId = uiState.versions
            .getOrNull(uiState.selectedVersionIndex)
            ?.fileId
        val localUri: String? = activeFileId?.let { fileId ->
            val serverId = serverRegistry.activeServerId.value
                ?: com.continuum.app.common.downloads.DownloadEnqueuer.DEFAULT_SERVER_ID
            val profileId = profileRepository.getActiveProfileId()
                ?: com.continuum.app.common.downloads.DownloadEnqueuer.DEFAULT_PROFILE_ID
            downloadStorage.locateLocalMedia(serverId, profileId, fileId)?.uriString
        }
        val effectiveStreamUrl = localUri ?: streamUrl

        val mediaItem = playerFactory.buildMediaItem(
            streamUrl = effectiveStreamUrl,
            playMethod = if (localUri != null) com.continuum.app.model.playback.PlayMethod.DIRECT else playMethod,
            serverUrl = uiState.serverUrl,
            subtitles = uiState.subtitleTracks,
            title = uiState.title.ifBlank { null },
            subtitle = uiState.subtitle.ifBlank { null },
            artworkUrl = uiState.artworkUrl,
        )

        controller.setMediaItem(mediaItem, resumePositionMs)
        controller.prepare()
        controller.playWhenReady = wasPlaying
    }
```

Then make the auto-select land once the rebuilt item's text tracks resolve. The existing `LaunchedEffect(mediaController, uiState.selectedSubtitleIndex)` fires when the VM sets the new index — before the re-prepared player has resolved its tracks — and `SubtitleManager.selectSubtitle` no-ops when the group doesn't exist yet. Add `onTracksChanged` to the existing `Player.Listener` in the `DisposableEffect(mediaController)` block (after `onVideoSizeChanged`):

```kotlin
                override fun onTracksChanged(tracks: androidx.media3.common.Tracks) {
                    // Re-apply the subtitle selection once track groups resolve:
                    // after the subtitle-refresh rebuild the selection effect has
                    // already fired (against the OLD tracks), so without this the
                    // auto-selected downloaded/AI track never engages. Reads the
                    // live VM state — `uiState` here can be a stale closure capture.
                    subtitleManager.selectSubtitle(
                        controller,
                        viewModel.uiState.value.selectedSubtitleIndex,
                    )
                }
```

- [ ] **Step 4: Run tests**

```
./gradlew :androidApp:testDebugUnitTest --tests "com.continuum.app.android.ui.screens.player.SubtitleTrackSelectionTest"
./gradlew :androidApp:compileDebugKotlinAndroid
./gradlew :androidApp:assembleDebug
```

Manual checklist (regression — full feature checks come with the sheets in Tasks 3/4):
- [ ] Play a movie; playback, audio/subtitle selection in TracksSheet, version switch, seek, and exit all behave exactly as before (nonce stays 0, rebuild effect never fires).
- [ ] Offline (downloaded) playback still works — `refreshSubtitles` is inert without a session.

- [ ] **Step 5: Commit**

```
git add androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/player/PlayerViewModel.kt androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/player/PlayerScreen.kt androidApp/src/androidMain/kotlin/com/continuum/app/android/di/AndroidModule.kt androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/player/SubtitleTrackSelection.kt androidApp/src/androidUnitTest/kotlin/com/continuum/app/android/ui/screens/player/SubtitleTrackSelectionTest.kt
git commit -m "Wire mobile player for mid-playback subtitle track refresh

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>"
```

---

### Task M3: SubtitleSearchSheet + TracksSheet "Search subtitles…" row

**Files:**
- Create: `androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/player/SubtitleSearchSheet.kt`
- Modify: `androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/player/TracksSheet.kt`
- Modify: `androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/player/PlayerOverlay.kt`

- [ ] **Step 1: Write the failing test**

Compose UI — not unit-testable in this project (no Robolectric/compose-test harness in androidUnitTest); the pure presentation bits (score bucketing, language names) were extracted and tested in Task 1. Verification = compile gate (Step 2/4) + manual checklist (Step 4).

- [ ] **Step 2: Run test to verify it fails**

n/a (no automated test). `./gradlew :androidApp:compileDebugKotlinAndroid` currently fails to resolve `SubtitleSearchSheet` if you wire PlayerOverlay first — implement bottom-up instead.

- [ ] **Step 3: Implementation**

**(a)** Create `androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/player/SubtitleSearchSheet.kt`:

```kotlin
package com.continuum.app.android.ui.screens.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.unit.sp
import com.continuum.app.android.ui.util.LanguageNames
import com.continuum.app.model.subtitles.SubtitleResult

// Web-parity palette (SubtitleSearchModal.tsx providerInfo / scoreColor).
private val ScoreGreen = Color(0xFF22C55E)
private val ScoreAmber = Color(0xFFEAB308)
private val ScoreRed = Color(0xFFEF4444)

private data class ProviderBadge(val abbr: String, val color: Color)

private val ProviderBadges = mapOf(
    "opensubtitles" to ProviderBadge("OS", Color(0xFFEAB308)),
    "subdl" to ProviderBadge("SDL", Color(0xFF3B82F6)),
    "subsource" to ProviderBadge("SS", Color(0xFFEF4444)),
)

private fun scoreBadgeColor(score: Int): Color = when (scoreBadgeBucket(score)) {
    ScoreBadgeBucket.High -> ScoreGreen
    ScoreBadgeBucket.Medium -> ScoreAmber
    ScoreBadgeBucket.Low -> ScoreRed
}

/**
 * Provider subtitle search sheet, mirroring the web's SubtitleSearchModal:
 * language dropdown → Search → tappable result rows (score badge, release
 * name, provider/HI badges, downloads count) with inline download progress.
 * On a successful download the ViewModel merges + auto-selects the track and
 * flips downloadCompleted, which dismisses this sheet.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubtitleSearchSheet(
    tools: PlayerViewModel.SubtitleToolsUiState,
    defaultLanguage: String,
    onSearch: (String) -> Unit,
    onDownload: (SubtitleResult) -> Unit,
    onDismiss: () -> Unit,
) {
    var selectedLanguage by remember { mutableStateOf(defaultLanguage) }
    var languageMenuExpanded by remember { mutableStateOf(false) }

    LaunchedEffect(tools.downloadCompleted) {
        if (tools.downloadCompleted) onDismiss()
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Color.Transparent,
        contentColor = Color.White,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFF1F2937).copy(alpha = 0.95f),
                            Color.Black.copy(alpha = 0.92f),
                        ),
                    ),
                ),
        ) {
            Text(
                text = "Search Subtitles",
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 8.dp),
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(modifier = Modifier.weight(1f)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color.White.copy(alpha = 0.08f), RoundedCornerShape(8.dp))
                            .clickable { languageMenuExpanded = true }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = LanguageNames.displayName(selectedLanguage),
                            color = Color.White,
                            modifier = Modifier.weight(1f),
                        )
                        Icon(
                            imageVector = Icons.Filled.ArrowDropDown,
                            contentDescription = "Choose language",
                            tint = Color.White.copy(alpha = 0.7f),
                        )
                    }
                    DropdownMenu(
                        expanded = languageMenuExpanded,
                        onDismissRequest = { languageMenuExpanded = false },
                        modifier = Modifier.heightIn(max = 320.dp),
                    ) {
                        LanguageNames.dropdownOptions.forEach { (code, label) ->
                            DropdownMenuItem(
                                text = { Text(label) },
                                onClick = {
                                    selectedLanguage = code
                                    languageMenuExpanded = false
                                },
                            )
                        }
                    }
                }
                Button(
                    onClick = { onSearch(selectedLanguage) },
                    enabled = !tools.searchLoading,
                ) {
                    if (tools.searchLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = Color.White,
                        )
                    } else {
                        Text("Search")
                    }
                }
            }

            tools.searchError?.let { error ->
                Text(
                    text = error,
                    color = ScoreRed,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                )
            }
            tools.searchWarnings.forEach { warning ->
                Text(
                    text = warning,
                    color = ScoreAmber,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 2.dp),
                )
            }
            if (tools.searchAttempted && !tools.searchLoading &&
                tools.searchError == null && tools.searchResults.isEmpty()
            ) {
                Text(
                    text = "No subtitles found.",
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 14.sp,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                )
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp),
            ) {
                items(tools.searchResults, key = { "${it.provider}:${it.id}" }) { result ->
                    val key = "${result.provider}:${result.id}"
                    SubtitleResultRow(
                        result = result,
                        isDownloading = tools.downloadingKey == key,
                        enabled = tools.downloadingKey == null,
                        onClick = { onDownload(result) },
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SubtitleResultRow(
    result: SubtitleResult,
    isDownloading: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled && !isDownloading, onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        val scoreColor = scoreBadgeColor(result.score)
        Box(
            modifier = Modifier
                .background(scoreColor.copy(alpha = 0.18f), RoundedCornerShape(6.dp))
                .padding(horizontal = 8.dp, vertical = 4.dp),
        ) {
            Text(
                text = "${result.score}",
                color = scoreColor,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = result.releaseName,
                color = Color.White,
                fontSize = 14.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Row(
                modifier = Modifier.padding(top = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                val badge = ProviderBadges[result.provider]
                    ?: ProviderBadge(result.provider.take(3).uppercase(), Color.Gray)
                BadgeText(text = badge.abbr, color = badge.color)
                Text(
                    text = LanguageNames.displayName(result.language),
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 12.sp,
                )
                if (result.hearingImpaired) {
                    BadgeText(text = "HI", color = Color.White.copy(alpha = 0.7f))
                }
                Text(
                    text = "${result.downloads} downloads",
                    color = Color.White.copy(alpha = 0.45f),
                    fontSize = 12.sp,
                )
            }
        }
        if (isDownloading) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                strokeWidth = 2.dp,
                color = Color.White,
            )
        }
    }
}

@Composable
private fun BadgeText(text: String, color: Color) {
    Text(
        text = text,
        color = color,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier
            .background(color.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
            .padding(horizontal = 5.dp, vertical = 1.dp),
    )
}
```

**(b)** `TracksSheet.kt` — add the non-selecting action rows. New parameters (defaulted so existing call sites compile):

```kotlin
fun TracksSheet(
    isVisible: Boolean,
    audioTracks: List<AudioTrack>,
    selectedAudioIndex: Int,
    subtitles: List<PlayerSubtitleInfo>,
    selectedSubtitleIndex: Int,
    onSelectAudio: (Int) -> Unit,
    onSelectSubtitle: (Int) -> Unit,
    onDismiss: () -> Unit,
    showSearchAction: Boolean = false,
    showTranslateAction: Boolean = false,
    onSearchSubtitles: () -> Unit = {},
    onTranslateWithAi: () -> Unit = {},
) {
```

After the `subtitles.forEachIndexed { ... }` loop (before the trailing `Spacer`), insert:

```kotlin
            // Non-selecting action rows (web SubtitleMenu parity). Each
            // dismisses this sheet first — Material 3 sheets can't nest —
            // then PlayerOverlay opens the target sheet.
            if (showSearchAction) {
                ActionRow(
                    icon = Icons.Filled.Search,
                    label = "Search subtitles…",
                    onClick = {
                        scope.launch { sheetState.hide() }
                        onDismiss()
                        onSearchSubtitles()
                    },
                )
            }
            if (showTranslateAction) {
                ActionRow(
                    icon = Icons.Filled.Translate,
                    label = "Translate with AI…",
                    onClick = {
                        scope.launch { sheetState.hide() }
                        onDismiss()
                        onTranslateWithAi()
                    },
                )
            }
```

Add imports `androidx.compose.material.icons.filled.Search`, `androidx.compose.material.icons.filled.Translate`, `androidx.compose.foundation.layout.size`, and the helper at file scope:

```kotlin
@Composable
private fun ActionRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp),
        )
        Text(
            text = label,
            color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}
```

**(c)** `PlayerOverlay.kt` — hoist sheet state like the other sheets. Add to the visibility bools:

```kotlin
    var subtitleSearchVisible by remember { mutableStateOf(false) }
    var aiTranslateVisible by remember { mutableStateOf(false) }
```

After the existing `collectAsState()` block:

```kotlin
    val subtitleTools by viewModel.subtitleTools.collectAsState()

    // Lazy one-shot AI status probe on first TracksSheet open (web parity).
    LaunchedEffect(tracksSheetVisible) {
        if (tracksSheetVisible) viewModel.onTracksSheetOpened()
    }

    // Subtitle tooling needs a live server session (media_file_id + session
    // stream URLs); hidden for offline/local playback.
    val subtitleToolsAvailable = state.sessionId != null && state.mediaFileId != null
```

Update the `TracksSheet(...)` call:

```kotlin
    TracksSheet(
        isVisible = tracksSheetVisible,
        audioTracks = state.audioTracks,
        selectedAudioIndex = state.selectedAudioIndex,
        subtitles = state.subtitleTracks,
        selectedSubtitleIndex = state.selectedSubtitleIndex,
        onSelectAudio = onSelectAudio,
        onSelectSubtitle = onSelectSubtitle,
        onDismiss = { tracksSheetVisible = false },
        showSearchAction = subtitleToolsAvailable,
        showTranslateAction = subtitleToolsAvailable &&
            subtitleTools.aiStatus?.let { it.enabled || it.transcribeEnabled } == true,
        onSearchSubtitles = {
            tracksSheetVisible = false
            subtitleSearchVisible = true
        },
        onTranslateWithAi = {
            tracksSheetVisible = false
            aiTranslateVisible = true
        },
    )

    if (subtitleSearchVisible) {
        SubtitleSearchSheet(
            tools = subtitleTools,
            defaultLanguage = com.continuum.app.android.ui.util.LanguageNames
                .searchCode(state.preferredTextLanguage),
            onSearch = viewModel::searchSubtitles,
            onDownload = viewModel::downloadSubtitle,
            onDismiss = {
                subtitleSearchVisible = false
                viewModel.onSearchSheetClosed()
            },
        )
    }
```

(`aiTranslateVisible` is consumed in Task 4; until then add `@Suppress("UNUSED_VARIABLE")` is unnecessary — Kotlin allows unused `var`, and the `onTranslateWithAi` row only shows once `aiTranslateVisible` gets its sheet; gating already requires aiStatus, but the row would no-op visibly. To keep Task 3 shippable, pass `showTranslateAction = false` in this task and flip it to the real gate in Task 4.) Add import `androidx.compose.runtime.LaunchedEffect`.

- [ ] **Step 4: Run tests**

```
./gradlew :androidApp:compileDebugKotlinAndroid
./gradlew :androidApp:testDebugUnitTest
./gradlew :androidApp:assembleDebug
```

Manual checklist (install on device/emulator against a server with at least one subtitle provider configured):
- [ ] Open TracksSheet during streaming playback → "Search subtitles…" row appears under the subtitle list; tapping it closes TracksSheet and opens the search sheet.
- [ ] Offline/local playback → no "Search subtitles…" row.
- [ ] Default language matches the profile subtitle language (else English); dropdown lists ~37 languages sorted by name.
- [ ] Search shows spinner, then result rows: score badge green ≥70 / amber 40–69 / red <40; provider badges OS (yellow) / SDL (blue) / SS (red); HI badge only on hearing-impaired results; downloads count shown.
- [ ] Server without providers → server error text shown inline; warnings (partial provider failures) shown in amber.
- [ ] Tap a result → inline spinner on that row, other rows disabled; on success the sheet dismisses, playback continues at the same position (no restart), and the new track is selected and rendering cues; it also appears in TracksSheet labeled "{release} ({provider})".
- [ ] Download failure (e.g. kill network) → inline error, sheet stays open, playback unaffected.

- [ ] **Step 5: Commit**

```
git add androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/player/SubtitleSearchSheet.kt androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/player/TracksSheet.kt androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/player/PlayerOverlay.kt
git commit -m "Add mobile subtitle search and download sheet

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>"
```

---

### Task M4: AiTranslateSheet + "Translate with AI…" row

**Files:**
- Create: `androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/player/SubtitleAiSupport.kt`
- Create: `androidApp/src/androidUnitTest/kotlin/com/continuum/app/android/ui/screens/player/SubtitleAiSupportTest.kt`
- Create: `androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/player/AiTranslateSheet.kt`
- Modify: `androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/player/PlayerOverlay.kt`

- [ ] **Step 1: Write the failing test**

`androidApp/src/androidUnitTest/kotlin/com/continuum/app/android/ui/screens/player/SubtitleAiSupportTest.kt`:

```kotlin
package com.continuum.app.android.ui.screens.player

import com.continuum.app.model.playback.PlayerSubtitleInfo
import com.continuum.app.model.subtitles.AiQuota
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SubtitleAiSupportTest {

    private fun track(codec: String?, source: String?) = PlayerSubtitleInfo(
        index = 0, language = "en", codec = codec, label = null,
        source = source, forced = null, url = "/u",
    )

    @Test
    fun embeddedTextTracksAreTranslatable() {
        assertTrue(isTranslatableSource(track("subrip", "embedded")))
        assertTrue(isTranslatableSource(track("ass", "embedded"))) // ffmpeg-extractable
    }

    @Test
    fun embeddedBitmapTracksAreNot() {
        assertFalse(isTranslatableSource(track("hdmv_pgs_subtitle", "embedded")))
        assertFalse(isTranslatableSource(track("dvd_subtitle", "embedded")))
    }

    @Test
    fun externalAndDownloadedMustBeParseableText() {
        assertTrue(isTranslatableSource(track("srt", "external")))
        assertTrue(isTranslatableSource(track("vtt", "downloaded")))
        assertFalse(isTranslatableSource(track("ass", "external"))) // server can't parse external ASS
        assertFalse(isTranslatableSource(track(null, "external")))
    }

    @Test
    fun transcribeKindMatchesWebSemantics() {
        assertEquals("transcribe", transcribeKindFor("en", "en"))
        assertEquals("transcribe_translate", transcribeKindFor("ja", "en"))
        assertEquals("transcribe_translate", transcribeKindFor(null, "en"))
    }

    @Test
    fun quotaLineShowsRemaining() {
        val q = AiQuota(limited = true, limit = 10, used = 3, remaining = 7, period = "week")
        assertEquals("7 of 10 transcriptions left for the last 7 days.", quotaLineText(q))
    }

    @Test
    fun quotaLineShowsExhausted() {
        val q = AiQuota(limited = true, limit = 5, used = 5, remaining = 0, period = "day")
        assertEquals(
            "You've used all 5 transcriptions for the last 24 hours. Try again later.",
            quotaLineText(q),
        )
    }

    @Test
    fun unknownPeriodFallsThrough() {
        val q = AiQuota(limited = true, limit = 2, used = 0, remaining = 2, period = "fortnight")
        assertEquals("2 of 2 transcriptions left for the last fortnight.", quotaLineText(q))
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

```
./gradlew :androidApp:testDebugUnitTest --tests "com.continuum.app.android.ui.screens.player.SubtitleAiSupportTest"
```
Expected: compilation failure (helpers unresolved).

- [ ] **Step 3: Implementation**

**(a)** Create `androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/player/SubtitleAiSupport.kt`:

```kotlin
package com.continuum.app.android.ui.screens.player

import com.continuum.app.model.playback.PlayerSubtitleInfo
import com.continuum.app.model.subtitles.AiQuota

// Mirrors web/src/player/components/SubtitleTranslateModal.tsx: external /
// downloaded sources must be a server-parseable text format, while embedded
// non-bitmap tracks can be extracted to text via ffmpeg.
private val TRANSLATABLE_TEXT_CODECS = setOf("srt", "subrip", "vtt", "webvtt")
private val BITMAP_CODECS = setOf(
    "pgs", "hdmv_pgs_subtitle", "dvd_subtitle", "dvdsub", "dvb_subtitle", "dvbsub",
)

/** True when the server can use [track] as an AI translation source. */
internal fun isTranslatableSource(track: PlayerSubtitleInfo): Boolean {
    val codec = track.codec?.lowercase().orEmpty()
    return if (track.source == "embedded") {
        codec !in BITMAP_CODECS
    } else {
        codec in TRANSLATABLE_TEXT_CODECS
    }
}

/**
 * Audio-mode job kind: same target as the audio language → plain
 * transcription; otherwise transcribe-then-translate (web parity).
 */
internal fun transcribeKindFor(audioLanguage: String?, targetLanguage: String): String =
    if ((audioLanguage ?: "") == targetLanguage) "transcribe" else "transcribe_translate"

/** Quota counter line, wording pinned to the web modal + its period labels. */
internal fun quotaLineText(quota: AiQuota): String {
    val window = when (quota.period) {
        "day" -> "24 hours"
        "week" -> "7 days"
        "month" -> "30 days"
        else -> quota.period
    }
    return if (quota.remaining <= 0) {
        "You've used all ${quota.limit} transcriptions for the last $window. Try again later."
    } else {
        "${quota.remaining} of ${quota.limit} transcriptions left for the last $window."
    }
}
```

**(b)** Create `androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/player/AiTranslateSheet.kt`:

```kotlin
package com.continuum.app.android.ui.screens.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.unit.sp
import com.continuum.app.android.ui.util.LanguageNames
import com.continuum.app.model.catalog.AudioTrack
import com.continuum.app.model.playback.PlayerSubtitleInfo

private val WarnAmber = Color(0xFFEAB308)
private val ErrorRed = Color(0xFFEF4444)

private enum class AiMode { Subtitles, Audio }

/**
 * AI subtitle generation sheet, mirroring the web's SubtitleTranslateModal:
 * mode tabs (From subtitles / From audio, per availability), source picker,
 * target language dropdown, transcription quota line, and in-sheet job
 * progress with Cancel. Submission goes through PlayerViewModel.startAiJob
 * with start_position = current playhead; completion auto-selects the result
 * track (jobJustCompleted) and dismisses.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiTranslateSheet(
    tools: PlayerViewModel.SubtitleToolsUiState,
    subtitleTracks: List<PlayerSubtitleInfo>,
    audioTracks: List<AudioTrack>,
    defaultTargetLanguage: String,
    onRefreshQuota: () -> Unit,
    onSubmit: (kind: String, sourceIndex: Int, sourceLanguage: String, targetLanguage: String) -> Unit,
    onCancelJob: () -> Unit,
    onDismiss: () -> Unit,
) {
    val aiStatus = tools.aiStatus
    val sourceTracks = remember(subtitleTracks) { subtitleTracks.filter(::isTranslatableSource) }
    val canTranslate = aiStatus?.enabled == true && sourceTracks.isNotEmpty()
    val canTranscribe = aiStatus?.transcribeEnabled == true && audioTracks.isNotEmpty()

    var mode by remember(canTranslate, canTranscribe) {
        mutableStateOf(if (canTranslate) AiMode.Subtitles else AiMode.Audio)
    }
    var sourceTrackPos by remember { mutableIntStateOf(0) }   // position in sourceTracks
    var audioPos by remember { mutableIntStateOf(0) }          // position in audioTracks (web's audioIndex)
    var targetLanguage by remember { mutableStateOf(defaultTargetLanguage) }

    val quotaExhausted = tools.quota != null && tools.quota.remaining <= 0

    // Quota refreshed on every open so the counter is current before submit.
    LaunchedEffect(Unit) {
        if (canTranscribe) onRefreshQuota()
    }
    LaunchedEffect(tools.jobJustCompleted) {
        if (tools.jobJustCompleted) onDismiss()
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Color.Transparent,
        contentColor = Color.White,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFF1F2937).copy(alpha = 0.95f),
                            Color.Black.copy(alpha = 0.92f),
                        ),
                    ),
                ),
        ) {
            Text(
                text = "Translate with AI",
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 8.dp),
            )

            when {
                tools.activeJob != null -> {
                    val job = tools.activeJob
                    Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
                        LinearProgressIndicator(
                            progress = { job.progress.toFloat().coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Text(
                            text = "${(job.progress * 100).toInt()}%" +
                                (job.progressMessage?.takeIf { it.isNotBlank() }?.let { " — $it" } ?: ""),
                            color = Color.White.copy(alpha = 0.7f),
                            fontSize = 13.sp,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                        OutlinedButton(
                            onClick = onCancelJob,
                            modifier = Modifier.padding(top = 12.dp),
                        ) {
                            Text("Cancel")
                        }
                    }
                }

                !canTranslate && !canTranscribe -> {
                    Text(
                        text = "AI subtitle generation isn't available for this file — " +
                            "there are no translatable subtitle tracks and audio transcription is not enabled.",
                        color = Color.White.copy(alpha = 0.6f),
                        fontSize = 14.sp,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
                    )
                }

                else -> {
                    // Mode tabs — only when both paths are available.
                    if (canTranslate && canTranscribe) {
                        Row(
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            ModeTab("From subtitles", mode == AiMode.Subtitles) { mode = AiMode.Subtitles }
                            ModeTab("From audio", mode == AiMode.Audio) { mode = AiMode.Audio }
                        }
                    }

                    if (mode == AiMode.Subtitles) {
                        PickerRow(
                            label = "Source track",
                            options = sourceTracks.map { sub ->
                                listOfNotNull(
                                    sub.label?.takeIf { it.isNotBlank() },
                                    LanguageNames.displayName(sub.language),
                                ).joinToString(" · ")
                            },
                            selected = sourceTrackPos.coerceIn(0, (sourceTracks.size - 1).coerceAtLeast(0)),
                            onSelect = { sourceTrackPos = it },
                        )
                    } else {
                        PickerRow(
                            label = "Audio track",
                            options = audioTracks.map { track ->
                                listOfNotNull(
                                    LanguageNames.displayName(track.language),
                                    track.channelLayout?.takeIf { it.isNotBlank() },
                                    track.title?.takeIf { it.isNotBlank() },
                                ).joinToString(" · ")
                            },
                            selected = audioPos.coerceIn(0, (audioTracks.size - 1).coerceAtLeast(0)),
                            onSelect = { audioPos = it },
                        )
                    }

                    PickerRow(
                        label = "Target language",
                        options = LanguageNames.dropdownOptions.map { it.second },
                        selected = LanguageNames.dropdownOptions
                            .indexOfFirst { it.first == targetLanguage }
                            .coerceAtLeast(0),
                        onSelect = { targetLanguage = LanguageNames.dropdownOptions[it].first },
                    )

                    if (mode == AiMode.Audio && tools.quota != null) {
                        Text(
                            text = quotaLineText(tools.quota),
                            color = if (quotaExhausted) WarnAmber else Color.White.copy(alpha = 0.5f),
                            fontSize = 13.sp,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                        )
                    }

                    tools.translateError?.let { error ->
                        Text(
                            text = error,
                            color = ErrorRed,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                        )
                    }

                    Button(
                        onClick = {
                            if (mode == AiMode.Audio) {
                                val audio = audioTracks.getOrNull(audioPos) ?: return@Button
                                val kind = transcribeKindFor(audio.language, targetLanguage)
                                onSubmit(
                                    kind,
                                    audioPos, // audio source_index = list position (web parity)
                                    audio.language.orEmpty(),
                                    if (kind == "transcribe") "" else targetLanguage,
                                )
                            } else {
                                val source = sourceTracks.getOrNull(sourceTrackPos) ?: return@Button
                                onSubmit(
                                    "translate",
                                    source.index, // combined subtitle index from the session track list
                                    source.language.orEmpty(),
                                    targetLanguage,
                                )
                            }
                        },
                        enabled = !tools.translateSubmitting &&
                            !(mode == AiMode.Audio && quotaExhausted) &&
                            (if (mode == AiMode.Audio) audioTracks.isNotEmpty() else sourceTracks.isNotEmpty()),
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                    ) {
                        if (tools.translateSubmitting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = Color.White,
                            )
                        } else {
                            Text(if (mode == AiMode.Audio) "Generate" else "Translate")
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun ModeTab(label: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        text = label,
        color = if (selected) Color.White else Color.White.copy(alpha = 0.55f),
        fontSize = 14.sp,
        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        modifier = Modifier
            .background(
                if (selected) Color.White.copy(alpha = 0.14f) else Color.Transparent,
                RoundedCornerShape(16.dp),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 6.dp),
    )
}

@Composable
private fun PickerRow(
    label: String,
    options: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp)) {
        Text(
            text = label.uppercase(),
            color = Color.White.copy(alpha = 0.6f),
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(bottom = 4.dp),
        )
        Box {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White.copy(alpha = 0.08f), RoundedCornerShape(8.dp))
                    .clickable { expanded = true }
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = options.getOrNull(selected) ?: "—",
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    imageVector = Icons.Filled.ArrowDropDown,
                    contentDescription = "Choose $label",
                    tint = Color.White.copy(alpha = 0.7f),
                )
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier.heightIn(max = 320.dp),
            ) {
                options.forEachIndexed { index, option ->
                    DropdownMenuItem(
                        text = { Text(option) },
                        onClick = {
                            onSelect(index)
                            expanded = false
                        },
                    )
                }
            }
        }
    }
}
```

**(c)** `PlayerOverlay.kt` — flip the translate gate on and mount the sheet. Change `showTranslateAction = false` (Task 3 placeholder) to:

```kotlin
        showTranslateAction = subtitleToolsAvailable &&
            subtitleTools.aiStatus?.let { it.enabled || it.transcribeEnabled } == true,
```

and after the `SubtitleSearchSheet` block add:

```kotlin
    if (aiTranslateVisible) {
        AiTranslateSheet(
            tools = subtitleTools,
            subtitleTracks = state.subtitleTracks,
            audioTracks = state.audioTracks,
            defaultTargetLanguage = com.continuum.app.android.ui.util.LanguageNames
                .searchCode(state.preferredTextLanguage),
            onRefreshQuota = viewModel::refreshAiQuota,
            onSubmit = viewModel::startAiJob,
            onCancelJob = viewModel::cancelAiJob,
            onDismiss = {
                aiTranslateVisible = false
                viewModel.onTranslateSheetClosed()
            },
        )
    }
```

- [ ] **Step 4: Run tests**

```
./gradlew :androidApp:testDebugUnitTest --tests "com.continuum.app.android.ui.screens.player.SubtitleAiSupportTest"
./gradlew :androidApp:compileDebugKotlinAndroid
./gradlew :androidApp:testDebugUnitTest
./gradlew :androidApp:assembleDebug
```

Manual checklist (server with AI engine configured; second pass with AI unconfigured):
- [ ] AI unconfigured → "Translate with AI…" row never appears (status probe fails silently); row also absent offline.
- [ ] Both translate + transcribe enabled → mode tabs shown; translate-only or bitmap-only-subtitle files → single mode, no tabs.
- [ ] Source picker lists only translatable subtitle tracks (no PGS/external ASS); audio picker shows "Language · layout" labels.
- [ ] Audio mode shows the quota line; exhaust the quota → amber exhausted text and disabled Generate; server 429 (stale counter) → error inline and counter refreshes to exhausted.
- [ ] Submit "From subtitles" mid-film → in-sheet progress percent + message updates; on completion the sheet dismisses, the new track is auto-selected and renders cues near the current position (start_position honored), playback never restarts.
- [ ] Cancel mid-job → job stops (verify via `GET /api/v1/subtitles/ai/jobs?media_file_id=N` status `cancelled`), sheet returns to the form.
- [ ] Exit the player mid-job → no crash; poll stops (no further job requests in server logs).
- [ ] Dismiss the sheet mid-job, reopen → progress still live (job keeps polling in the background); completion while closed still merges + auto-selects the track.

- [ ] **Step 5: Commit**

```
git add androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/player/SubtitleAiSupport.kt androidApp/src/androidUnitTest/kotlin/com/continuum/app/android/ui/screens/player/SubtitleAiSupportTest.kt androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/player/AiTranslateSheet.kt androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/player/PlayerOverlay.kt
git commit -m "Add mobile AI subtitle translation sheet

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>"
```

## Section T: TV player UI

## Dependencies (assumed landed from shared + mobile tasks)

The shared layer and mobile tasks land first. TV tasks assume these exact signatures (verify before starting; adjust call sites if the landed names differ):

```kotlin
// shared/src/commonMain/kotlin/com/continuum/app/model/subtitles/SubtitleModels.kt
@Serializable data class SubtitleResult(
    val id: String, val provider: String, val language: String,
    @SerialName("release_name") val releaseName: String, val format: String,
    val score: Int, val downloads: Int,
    @SerialName("hearing_impaired") val hearingImpaired: Boolean,
    @SerialName("upload_date") val uploadDate: String? = null,
)
@Serializable data class SubtitleSearchResponse(val results: List<SubtitleResult>, val warnings: List<String>? = null)
@Serializable data class SubtitleDownloadRequest(
    @SerialName("media_file_id") val mediaFileId: Int, val provider: String,
    @SerialName("subtitle_id") val subtitleId: String, val language: String,
    @SerialName("release_name") val releaseName: String, val format: String,
    val score: Int, @SerialName("hearing_impaired") val hearingImpaired: Boolean,
)
@Serializable data class DownloadedSubtitle(
    val id: Int, @SerialName("media_file_id") val mediaFileId: Int, val provider: String,
    val language: String, val format: String, @SerialName("release_name") val releaseName: String,
    val score: Int, @SerialName("hearing_impaired") val hearingImpaired: Boolean,
    @SerialName("created_at") val createdAt: String,
)
@Serializable data class AiStatus(val enabled: Boolean = false, @SerialName("transcribe_enabled") val transcribeEnabled: Boolean = false)
@Serializable data class AiQuota(val limited: Boolean, val limit: Int = 0, val used: Int = 0, val remaining: Int = 0, val period: String = "month")
@Serializable data class SubtitleAiJob(
    val id: Int, @SerialName("media_file_id") val mediaFileId: Int, val kind: String,
    @SerialName("source_index") val sourceIndex: Int,
    @SerialName("source_language") val sourceLanguage: String? = null,
    @SerialName("target_language") val targetLanguage: String,
    val status: String, val progress: Double = 0.0,
    @SerialName("progress_message") val progressMessage: String? = null,
    @SerialName("result_subtitle_id") val resultSubtitleId: Int? = null,
    @SerialName("error_message") val errorMessage: String? = null,
)
@Serializable data class SubtitleTranslateRequest(
    @SerialName("media_file_id") val mediaFileId: Int, val kind: String = "translate",
    @SerialName("source_index") val sourceIndex: Int,
    @SerialName("source_language") val sourceLanguage: String? = null,
    @SerialName("target_language") val targetLanguage: String,
    @SerialName("start_position") val startPosition: Double? = null,
)

// shared/src/commonMain/kotlin/com/continuum/app/repository/SubtitlesRepository.kt (registered in RepositoryModule → Koin get() resolves it in androidTvApp)
class SubtitlesRepository(...) {
    suspend fun search(mediaFileId: Int, languages: List<String>): ApiResult<SubtitleSearchResponse>
    suspend fun download(request: SubtitleDownloadRequest): ApiResult<DownloadedSubtitle>
    suspend fun list(mediaFileId: Int): ApiResult<List<DownloadedSubtitle>>
    suspend fun aiStatus(): ApiResult<AiStatus>
    suspend fun aiQuota(): ApiResult<AiQuota>
    suspend fun translate(request: SubtitleTranslateRequest): ApiResult<SubtitleAiJob>
    suspend fun cancelJob(jobId: Int): ApiResult<Unit>
    suspend fun pollJob(jobId: Int, intervalMs: Long = 2_000, onUpdate: (SubtitleAiJob) -> Unit = {}): SubtitleJobOutcome
}
sealed interface SubtitleJobOutcome {
    data class Completed(val resultSubtitleId: Int?) : SubtitleJobOutcome
    data class Failed(val message: String?) : SubtitleJobOutcome
    data object Cancelled : SubtitleJobOutcome
}

// android-shared/src/androidMain/kotlin/com/continuum/app/common/player/subtitles/SubtitleTrackMerge.kt
// (mobile task extracts this as the SHARED pure function per spec; unit-tested there)
fun mergeDownloadedSubtitles(
    existing: List<PlayerSubtitleInfo>,
    downloaded: List<DownloadedSubtitle>,
    serverUrl: String,
): List<PlayerSubtitleInfo>                                  // appends entries not already present; URL per web's construction; source = "downloaded"
fun downloadedSubtitleLabel(subtitle: DownloadedSubtitle): String  // "{Language} ({provider})" — must equal the label mergeDownloadedSubtitles writes
```

TV-side facts these tasks build on (verified against current code): `TvPlayerViewModel.UiState` holds `subtitleUrls: List<PlayerSubtitleInfo>`; `TvPlayerScreen` mounts them once via `playerFactory.buildMediaItem(streamUrl, playMethod, serverUrl, subtitles = state.subtitleUrls, title, artworkUrl)` → `controller.setMediaItem` → `prepare()` in a `LaunchedEffect(mediaController, state.streamUrl, state.sessionId)`; sidecar labels surface as `Format.label` in `extractTrackEntries`, so `PlayerTrackEntry.label == PlayerSubtitleInfo.label`; selection is `SubtitleManager.selectSubtitle(player, ordinalTextGroupIndex)`. Media3 cannot add `SubtitleConfiguration`s to a live item, so refresh = re-set the same stream URL with the enlarged subtitle list and resume position — the server playback session is untouched (mirrors the web's no-restart behavior at the session level). `media_file_id` comes from `PlaybackSessionResponse.mediaFileId` (already on the wire model, currently unread by the TV VM).

---

### Task T1: TvPlayerViewModel — aiStatus probe, refreshSubtitles merge + Media3 rebuild, search/download/translate orchestration

**Files:**
- Modify: `/Users/dev/projects/silo/silo-android/androidTvApp/src/androidMain/kotlin/com/continuum/app/tv/ui/screens/player/TvPlayerViewModel.kt`
- Modify: `/Users/dev/projects/silo/silo-android/androidTvApp/src/androidMain/kotlin/com/continuum/app/tv/ui/screens/player/TvPlayerScreen.kt`
- Modify: `/Users/dev/projects/silo/silo-android/androidTvApp/src/androidMain/kotlin/com/continuum/app/tv/di/AndroidTvModule.kt`

- [ ] **Step 1: Write the failing test**

No new unit test — this task is ViewModel + screen wiring around a `MediaController` and network repository; the TV module has no harness for either, and the only pure logic (merge, poll loop) is already unit-tested in the shared/mobile tasks this depends on. Verification is the manual D-pad checklist in Step 4. Note: existing `TvPlayerHudTabsTest` and `TvPlayerRemoteKeyActionTest` must stay green.

- [ ] **Step 2: Run test to verify it fails**

n.a. (no failing test). Capture the green baseline first:
```bash
./gradlew :androidTvApp:compileDebugKotlinAndroid :androidTvApp:testDebugUnitTest
```

- [ ] **Step 3: Implementation**

**3a. `TvPlayerViewModel.kt` — imports.** Add to the existing import block:

```kotlin
import com.continuum.app.common.player.subtitles.downloadedSubtitleLabel
import com.continuum.app.common.player.subtitles.mergeDownloadedSubtitles
import com.continuum.app.model.subtitles.AiQuota
import com.continuum.app.model.subtitles.AiStatus
import com.continuum.app.model.subtitles.DownloadedSubtitle
import com.continuum.app.model.subtitles.SubtitleDownloadRequest
import com.continuum.app.model.subtitles.SubtitleResult
import com.continuum.app.model.subtitles.SubtitleTranslateRequest
import com.continuum.app.repository.SubtitleJobOutcome
import com.continuum.app.repository.SubtitlesRepository
```

**3b. Feature UI-state types.** Add at file scope (below the `PlayerStatsSnapshot` declarations, above `class TvPlayerViewModel`):

```kotlin
/**
 * Subtitle provider search/download state backing [TvSubtitleSearchDialog].
 * `completedNonce` increments when a download lands and the track list has
 * been refreshed — the dialog observes it and dismisses itself.
 */
data class SubtitleSearchUiState(
    val language: String = "en",
    val isSearching: Boolean = false,
    val hasSearched: Boolean = false,
    val results: List<SubtitleResult> = emptyList(),
    val error: String? = null,
    /** [SubtitleResult.id] currently downloading (inline row spinner), or null. */
    val downloadingResultId: String? = null,
    val completedNonce: Int = 0,
)

/** Lifecycle of the in-dialog AI job for [TvAiTranslateDialog]. */
sealed interface AiJobPhase {
    data object Idle : AiJobPhase
    data object Submitting : AiJobPhase
    data class Running(val progress: Double, val message: String?) : AiJobPhase
    data class Failed(val message: String) : AiJobPhase
}

/**
 * AI translate/transcribe state. `status` defaults to both-flags-false so the
 * HUD row stays hidden until the lazy probe succeeds (matching the web: a
 * failed probe also leaves both flags false and surfaces no error).
 */
data class AiTranslateUiState(
    val statusLoaded: Boolean = false,
    val status: AiStatus = AiStatus(enabled = false, transcribeEnabled = false),
    val quota: AiQuota? = null,
    val phase: AiJobPhase = AiJobPhase.Idle,
    val completedNonce: Int = 0,
)
```

**3c. Constructor + DI.** Add a constructor parameter after `sleepTimer: SleepTimerController,`:

```kotlin
    private val sleepTimer: SleepTimerController,
    private val subtitlesRepository: SubtitlesRepository,
    private val contentId: String,
```

And in `AndroidTvModule.kt`, inside the `TvPlayerViewModel(...)` factory (after `sleepTimer = get(),`):

```kotlin
            sleepTimer = get(),
            subtitlesRepository = get(),
            contentId = params.get(),
```

**3d. `UiState` additions.** Inside `data class UiState`, after `val subtitleUrls: List<PlayerSubtitleInfo> = emptyList(),`:

```kotlin
        // Server media file id for the active version — required by the
        // subtitle search/download and AI translate endpoints. Sourced from
        // PlaybackSessionResponse.mediaFileId in loadContent; null until the
        // session starts (the HUD hides the Search row while null).
        val mediaFileId: Int? = null,
        // Bumped by refreshSubtitles after merging downloaded subtitles into
        // subtitleUrls. The screen rebuilds the MediaItem (same stream URL,
        // enlarged sidecar list) on each bump — keyed on the nonce, NOT on
        // subtitleUrls, so the initial prepare effect stays the only path
        // for session start / stream-URL changes.
        val subtitleRefreshNonce: Int = 0,
        // Dialog visibility — owned here so HUD rows can request them and
        // the screen renders the Popups above the open HUD.
        val showSubtitleSearchDialog: Boolean = false,
        val showAiTranslateDialog: Boolean = false,
```

**3e. New flows + bookkeeping fields.** After the `sessionState` property declaration:

```kotlin
    private val _subtitleSearch = MutableStateFlow(SubtitleSearchUiState())
    val subtitleSearch: StateFlow<SubtitleSearchUiState> = _subtitleSearch.asStateFlow()

    private val _aiTranslate = MutableStateFlow(AiTranslateUiState())
    val aiTranslate: StateFlow<AiTranslateUiState> = _aiTranslate.asStateFlow()

    /**
     * Ordinal text-group index to select after a subtitle refresh lands.
     * Mirrors the seekRequests idiom: the screen collects and calls
     * SubtitleManager.selectSubtitle — the VM never touches the controller.
     */
    private val _subtitleSelectRequests = MutableSharedFlow<Int>(extraBufferCapacity = 1)
    val subtitleSelectRequests: SharedFlow<Int> = _subtitleSelectRequests
```

And next to the existing `private var progressJob: Job? = null` block:

```kotlin
    private var aiStatusRequested = false
    private var aiJobPollJob: Job? = null
    private var activeAiJobId: Int? = null
    private var pendingSubtitleSelectLabel: String? = null
```

**3f. Capture `mediaFileId` in `loadContent`.** In the big `_uiState.update { it.copy(...) }` after the session resolves, add one line after `selectedFileId = version.fileId,`:

```kotlin
                        selectedFileId = version.fileId,
                        mediaFileId = resolved.mediaFileId.takeIf { it > 0 } ?: session.mediaFileId,
```

(`onUnsupportedPlayback`'s synthetic `PlaybackSessionResponse(mediaFileId = 0, ...)` never flows back into UiState, so the captured id survives transcode fallback.)

**3g. Pending-selection resolution.** Replace the two `onTracksChanged` overloads' bodies so both resolve pending selection:

```kotlin
    fun onTracksChanged(audio: List<PlayerTrackEntry>, subtitle: List<PlayerTrackEntry>) {
        _uiState.update { it.copy(audioTracks = audio, subtitleTracks = subtitle) }
        resolvePendingSubtitleSelection(subtitle)
    }

    fun onTracksChanged(
        audio: List<PlayerTrackEntry>,
        subtitle: List<PlayerTrackEntry>,
        video: List<PlayerTrackEntry>,
    ) {
        _uiState.update { it.copy(audioTracks = audio, subtitleTracks = subtitle, videoTracks = video) }
        resolvePendingSubtitleSelection(subtitle)
    }

    /**
     * After refreshSubtitles bumps the nonce, the screen re-prepares the item
     * and a fresh onTracksChanged arrives. Sidecar tracks expose their
     * SubtitleConfiguration label as Format.label, which extractTrackEntries
     * copies into PlayerTrackEntry.label — so matching by label is exact.
     * Emits the ordinal text-group index for SubtitleManager.selectSubtitle.
     */
    private fun resolvePendingSubtitleSelection(subtitle: List<PlayerTrackEntry>) {
        val label = pendingSubtitleSelectLabel ?: return
        val match = subtitle.firstOrNull { it.label == label } ?: return
        pendingSubtitleSelectLabel = null
        _subtitleSelectRequests.tryEmit(match.index)
    }
```

**3h. Core feature methods.** Add after `onSeekToChapter`:

```kotlin
    // ---- Subtitle suite: AI status probe + dialog visibility --------------------

    /**
     * Lazy once-per-player-session AI status probe, fired by the HUD the
     * first time the Subtitles pane is shown. On any failure both flags stay
     * false → the "Translate with AI" row is simply hidden (web parity; no
     * error surfaced).
     */
    fun onSubtitlesPaneShown() {
        if (aiStatusRequested) return
        aiStatusRequested = true
        viewModelScope.launch {
            when (val r = subtitlesRepository.aiStatus()) {
                is ApiResult.Success -> _aiTranslate.update {
                    it.copy(statusLoaded = true, status = r.data)
                }
                is ApiResult.Error -> _aiTranslate.update { it.copy(statusLoaded = true) }
                is ApiResult.NetworkError -> _aiTranslate.update { it.copy(statusLoaded = true) }
            }
        }
    }

    fun openSubtitleSearchDialog() {
        val defaultLang = _uiState.value.preferredTextLanguage
            ?.takeIf { it.isNotBlank() }?.take(2)?.lowercase() ?: "en"
        _subtitleSearch.update {
            // Keep prior results/language when reopening mid-session.
            if (it.hasSearched) it else it.copy(language = defaultLang)
        }
        _uiState.update { it.copy(showSubtitleSearchDialog = true) }
    }

    fun closeSubtitleSearchDialog() {
        _uiState.update { it.copy(showSubtitleSearchDialog = false) }
    }

    fun openAiTranslateDialog() {
        refreshAiQuota() // spec: quota refreshed on open
        _aiTranslate.update { it.copy(phase = AiJobPhase.Idle) }
        _uiState.update { it.copy(showAiTranslateDialog = true) }
    }

    /** Dismiss the dialog. A running job keeps polling — reopening shows live progress. */
    fun closeAiTranslateDialog() {
        _uiState.update { it.copy(showAiTranslateDialog = false) }
    }

    // ---- Subtitle suite: provider search / download ------------------------------

    fun setSubtitleSearchLanguage(code: String) {
        _subtitleSearch.update { it.copy(language = code) }
    }

    fun searchSubtitles() {
        val mediaFileId = _uiState.value.mediaFileId ?: return
        if (_subtitleSearch.value.isSearching) return
        val language = _subtitleSearch.value.language
        _subtitleSearch.update {
            it.copy(isSearching = true, hasSearched = true, error = null, results = emptyList())
        }
        viewModelScope.launch {
            when (val r = subtitlesRepository.search(mediaFileId, listOf(language))) {
                is ApiResult.Success -> _subtitleSearch.update {
                    it.copy(isSearching = false, results = r.data.results)
                }
                // No capability probe exists — "no providers configured" arrives
                // here as a plain server error; surface its text verbatim.
                is ApiResult.Error -> _subtitleSearch.update {
                    it.copy(isSearching = false, error = r.message)
                }
                is ApiResult.NetworkError -> _subtitleSearch.update {
                    it.copy(isSearching = false, error = r.exception.message ?: "Network error")
                }
            }
        }
    }

    fun downloadSubtitle(result: SubtitleResult) {
        val mediaFileId = _uiState.value.mediaFileId ?: return
        if (_subtitleSearch.value.downloadingResultId != null) return
        _subtitleSearch.update { it.copy(downloadingResultId = result.id, error = null) }
        viewModelScope.launch {
            val request = SubtitleDownloadRequest(
                mediaFileId = mediaFileId,
                provider = result.provider,
                subtitleId = result.id,
                language = result.language,
                releaseName = result.releaseName,
                format = result.format,
                score = result.score,
                hearingImpaired = result.hearingImpaired,
            )
            when (val r = subtitlesRepository.download(request)) {
                is ApiResult.Success -> {
                    refreshSubtitles(autoSelectSubtitleId = r.data.id)
                    _subtitleSearch.update {
                        it.copy(downloadingResultId = null, completedNonce = it.completedNonce + 1)
                    }
                }
                is ApiResult.Error -> _subtitleSearch.update {
                    it.copy(downloadingResultId = null, error = r.message)
                }
                is ApiResult.NetworkError -> _subtitleSearch.update {
                    it.copy(downloadingResultId = null, error = r.exception.message ?: "Network error")
                }
            }
        }
    }

    // ---- Subtitle suite: track refresh (web-parity, no session restart) ---------

    /**
     * Refetch the downloaded-subtitle list, merge new entries into
     * [UiState.subtitleUrls] via the shared pure merge, and bump
     * [UiState.subtitleRefreshNonce] so the screen re-prepares the MediaItem
     * (same stream URL + session — only the sidecar list grows). Selection is
     * label-driven: the freshly downloaded track's label when
     * [autoSelectSubtitleId] matches, otherwise the currently selected track's
     * label so the rebuild preserves the user's choice (Media3 track-group
     * overrides don't survive a re-prepare — groups are new instances).
     */
    suspend fun refreshSubtitles(autoSelectSubtitleId: Int?) {
        val state = _uiState.value
        val mediaFileId = state.mediaFileId ?: return
        val downloaded: List<DownloadedSubtitle> = when (val r = subtitlesRepository.list(mediaFileId)) {
            is ApiResult.Success -> r.data
            is ApiResult.Error -> {
                Log.w(TAG, "refreshSubtitles failed: ${r.code} ${r.message}")
                return
            }
            is ApiResult.NetworkError -> {
                Log.w(TAG, "refreshSubtitles network error", r.exception)
                return
            }
        }
        val merged = mergeDownloadedSubtitles(
            existing = state.subtitleUrls,
            downloaded = downloaded,
            serverUrl = state.serverUrl,
        )
        val autoSelectLabel = autoSelectSubtitleId
            ?.let { id -> downloaded.firstOrNull { it.id == id } }
            ?.let { downloadedSubtitleLabel(it) }
        if (merged == state.subtitleUrls) {
            // Nothing new to mount (e.g. re-download of an existing entry) —
            // honor auto-select against the already-mounted tracks and skip
            // the rebuild entirely.
            autoSelectLabel?.let { label ->
                state.subtitleTracks.firstOrNull { it.label == label }
                    ?.let { _subtitleSelectRequests.tryEmit(it.index) }
            }
            return
        }
        pendingSubtitleSelectLabel = autoSelectLabel
            ?: state.subtitleTracks.firstOrNull { it.isSelected }?.label
        _uiState.update {
            it.copy(subtitleUrls = merged, subtitleRefreshNonce = it.subtitleRefreshNonce + 1)
        }
    }

    // ---- Subtitle suite: AI translate / transcribe -------------------------------

    fun refreshAiQuota() {
        viewModelScope.launch {
            when (val r = subtitlesRepository.aiQuota()) {
                is ApiResult.Success -> _aiTranslate.update { it.copy(quota = r.data) }
                else -> Unit // quota line is simply absent on failure
            }
        }
    }

    /**
     * Submit an AI job and poll to completion. `start_position` = current
     * playhead (web parity); no `session_id` — Android polls instead of
     * streaming live cues. Runs in viewModelScope so player exit cancels the
     * poll via structured concurrency (the server job itself keeps running).
     */
    fun submitAiTranslate(
        kind: String,
        sourceIndex: Int,
        sourceLanguage: String?,
        targetLanguage: String,
    ) {
        val mediaFileId = _uiState.value.mediaFileId ?: return
        val phase = _aiTranslate.value.phase
        if (phase is AiJobPhase.Submitting || phase is AiJobPhase.Running) return
        _aiTranslate.update { it.copy(phase = AiJobPhase.Submitting) }
        aiJobPollJob?.cancel()
        aiJobPollJob = viewModelScope.launch {
            val request = SubtitleTranslateRequest(
                mediaFileId = mediaFileId,
                kind = kind,
                sourceIndex = sourceIndex,
                sourceLanguage = sourceLanguage,
                targetLanguage = targetLanguage,
                startPosition = _uiState.value.position,
            )
            val job = when (val r = subtitlesRepository.translate(request)) {
                is ApiResult.Success -> r.data
                is ApiResult.Error -> {
                    // 429 = quota exhausted → refresh quota so the dialog
                    // flips to the exhausted state; 503 = engine unconfigured.
                    if (r.code == 429) refreshAiQuota()
                    _aiTranslate.update {
                        it.copy(phase = AiJobPhase.Failed(r.message ?: "Translation failed"))
                    }
                    return@launch
                }
                is ApiResult.NetworkError -> {
                    _aiTranslate.update {
                        it.copy(phase = AiJobPhase.Failed(r.exception.message ?: "Network error"))
                    }
                    return@launch
                }
            }
            activeAiJobId = job.id
            _aiTranslate.update {
                it.copy(phase = AiJobPhase.Running(job.progress, job.progressMessage))
            }
            val outcome = subtitlesRepository.pollJob(
                jobId = job.id,
                onUpdate = { update ->
                    _aiTranslate.update {
                        it.copy(phase = AiJobPhase.Running(update.progress, update.progressMessage))
                    }
                },
            )
            activeAiJobId = null
            when (outcome) {
                is SubtitleJobOutcome.Completed -> {
                    refreshSubtitles(autoSelectSubtitleId = outcome.resultSubtitleId)
                    _aiTranslate.update {
                        it.copy(phase = AiJobPhase.Idle, completedNonce = it.completedNonce + 1)
                    }
                }
                is SubtitleJobOutcome.Failed -> _aiTranslate.update {
                    it.copy(phase = AiJobPhase.Failed(outcome.message ?: "Translation failed"))
                }
                SubtitleJobOutcome.Cancelled -> _aiTranslate.update {
                    it.copy(phase = AiJobPhase.Idle)
                }
            }
        }
    }

    /** Dialog Cancel row: stop polling, ask the server to cancel, return to the form. */
    fun cancelAiTranslateJob() {
        val jobId = activeAiJobId
        aiJobPollJob?.cancel()
        aiJobPollJob = null
        activeAiJobId = null
        _aiTranslate.update { it.copy(phase = AiJobPhase.Idle) }
        if (jobId != null) {
            viewModelScope.launch { subtitlesRepository.cancelJob(jobId) }
        }
    }

    /** Failed phase → back to the form after the user acknowledges the error. */
    fun clearAiTranslateError() {
        _aiTranslate.update { it.copy(phase = AiJobPhase.Idle) }
    }
```

**3i. `TvPlayerScreen.kt` — the real Media3 rebuild + auto-select collector.** Insert directly after the existing "Prepare the player when a stream URL becomes available" `LaunchedEffect` (the one keyed on `mediaController, state.streamUrl, state.sessionId`):

```kotlin
    // Subtitle refresh (search download / AI completion): Media3 cannot add
    // SubtitleConfigurations to a live item, so rebuild the SAME MediaItem —
    // identical stream URL + playback session — with the merged sidecar list
    // and resume at the captured position. Keyed on the refresh nonce so the
    // initial prepare effect above remains the only session-start path.
    LaunchedEffect(mediaController, state.subtitleRefreshNonce) {
        if (state.subtitleRefreshNonce == 0) return@LaunchedEffect
        val controller = mediaController ?: return@LaunchedEffect
        val url = state.streamUrl ?: return@LaunchedEffect
        val method = state.playMethod ?: return@LaunchedEffect
        val resumeMs = controller.currentPosition.coerceAtLeast(0L)
        val wasPlaying = controller.playWhenReady
        val mediaItem = playerFactory.buildMediaItem(
            streamUrl = url,
            playMethod = method,
            serverUrl = state.serverUrl,
            subtitles = state.subtitleUrls,
            title = state.title.ifBlank { null },
            artworkUrl = state.artworkUrl,
        )
        controller.setMediaItem(mediaItem, resumeMs)
        controller.prepare()
        controller.playWhenReady = wasPlaying
    }

    // Auto-select a freshly downloaded/translated subtitle track once the
    // rebuilt item's tracks land (the VM matches by label in onTracksChanged
    // and emits the ordinal text-group index). Mirrors the seekRequests idiom.
    LaunchedEffect(mediaController) {
        viewModel.subtitleSelectRequests.collect { idx ->
            mediaController?.let { subtitleManager.selectSubtitle(it, idx) }
        }
    }
```

- [ ] **Step 4: Run tests**

```bash
./gradlew :androidTvApp:compileDebugKotlinAndroid :androidTvApp:testDebugUnitTest :androidTvApp:assembleDebug
```

Manual D-pad checklist (Tasks 2–3 add the UI; at this point verify no regression):
- [ ] Play a title → playback starts, HUD opens (Up), all panes render as before.
- [ ] Subtitle track picker still selects/deselects ("Off") correctly.
- [ ] Transcode-fallback title still plays (mediaFileId capture didn't disturb the fallback path).
- [ ] Exit player → no crash, progress reported (logcat: no new errors from `TvPlayerViewModel`).

- [ ] **Step 5: Commit**

```bash
git add androidTvApp/src/androidMain/kotlin/com/continuum/app/tv/ui/screens/player/TvPlayerViewModel.kt \
        androidTvApp/src/androidMain/kotlin/com/continuum/app/tv/ui/screens/player/TvPlayerScreen.kt \
        androidTvApp/src/androidMain/kotlin/com/continuum/app/tv/di/AndroidTvModule.kt
git commit -m "Add subtitle suite state and track refresh to TV player VM

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>"
```

---

### Task T2: TvSubtitleSearchDialog + HUD "Search subtitles" action row

**Files:**
- Create: `/Users/dev/projects/silo/silo-android/androidTvApp/src/androidMain/kotlin/com/continuum/app/tv/ui/screens/player/TvSubtitleSearchDialog.kt`
- Modify: `/Users/dev/projects/silo/silo-android/androidTvApp/src/androidMain/kotlin/com/continuum/app/tv/ui/screens/player/TvPlayerHud.kt`
- Modify: `/Users/dev/projects/silo/silo-android/androidTvApp/src/androidMain/kotlin/com/continuum/app/tv/ui/screens/player/TvPlayerScreen.kt`

- [ ] **Step 1: Write the failing test**

No new unit test — pure Compose TV UI (Popup + focus traversal); covered by the manual D-pad checklist in Step 4. `TvPlayerHudTabsTest` must stay green after the `TvPlayerHud` signature change (it only exercises `visibleHudTabs`, which is untouched).

- [ ] **Step 2: Run test to verify it fails**

n.a. (no failing test). Green baseline:
```bash
./gradlew :androidTvApp:compileDebugKotlinAndroid :androidTvApp:testDebugUnitTest
```

- [ ] **Step 3: Implementation**

**3a. Create `TvSubtitleSearchDialog.kt`** (also hosts the cycler row + language helpers reused by Task 3 — same package):

```kotlin
package com.continuum.app.tv.ui.screens.player

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Glow
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.continuum.app.model.subtitles.SubtitleResult
import com.continuum.app.tv.ui.theme.DarkBackground
import com.continuum.app.tv.ui.theme.FocusedContainer
import com.continuum.app.tv.ui.theme.FocusedContent
import java.util.Locale

/**
 * Language codes offered by the search/translate pickers — matches the web's
 * common-language set. Cycled left/right on a [TvDialogCyclerRow]; no text
 * input anywhere (TV constraint that put this feature in scope).
 */
internal val TvSubtitleLanguageOptions: List<String> = listOf(
    "en", "es", "fr", "de", "it", "pt", "nl", "pl", "ru", "ja", "ko", "zh",
    "ar", "tr", "sv", "no", "da", "fi", "cs", "el", "he", "hi", "hu", "id",
    "ro", "th", "uk", "vi",
)

/** ISO 639-1 → English display name, fallback uppercased code (spec LanguageNames behavior). */
internal fun tvLanguageDisplayName(code: String): String =
    Locale(code).getDisplayLanguage(Locale.ENGLISH).ifBlank { code.uppercase() }

/** Score bucket colors — web parity: >=70 green, >=40 amber, else red. */
internal fun subtitleScoreColor(score: Int): Color = when {
    score >= 70 -> Color(0xFF22C55E)
    score >= 40 -> Color(0xFFF59E0B)
    else -> Color(0xFFEF4444)
}

internal fun subtitleProviderAbbreviation(provider: String): String =
    when (provider.lowercase()) {
        "opensubtitles" -> "OS"
        "subdl" -> "SDL"
        "subsource" -> "SS"
        else -> provider.take(3).uppercase()
    }

/** Provider badge colors — keep in sync with the web's provider styles. */
internal fun subtitleProviderColor(provider: String): Color =
    when (provider.lowercase()) {
        "opensubtitles" -> Color(0xFF3B82F6)
        "subdl" -> Color(0xFF8B5CF6)
        "subsource" -> Color(0xFF14B8A6)
        else -> Color.White.copy(alpha = 0.40f)
    }

/**
 * D-pad subtitle provider-search dialog. Panel + row idiom mirrors
 * TvOptionDialog (Popup, dark panel, ClickableSurface rows). Flow: cycle
 * language ← →, Select on "Search", focus a result row, Select to download
 * (inline spinner), then the VM refreshes + auto-selects the track and bumps
 * `completedNonce` — observed here to self-dismiss.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TvSubtitleSearchDialog(
    state: SubtitleSearchUiState,
    onLanguageChanged: (String) -> Unit,
    onSearch: () -> Unit,
    onDownload: (SubtitleResult) -> Unit,
    onDismiss: () -> Unit,
) {
    val languageRowFocus = remember { FocusRequester() }
    val initialNonce = remember { state.completedNonce }

    LaunchedEffect(Unit) { runCatching { languageRowFocus.requestFocus() } }

    // Download finished → track merged + auto-selected by the VM → close.
    LaunchedEffect(state.completedNonce) {
        if (state.completedNonce != initialNonce) onDismiss()
    }

    Popup(
        alignment = Alignment.Center,
        onDismissRequest = onDismiss,
        properties = PopupProperties(
            focusable = true,
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
            clippingEnabled = false,
        ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 72.dp, top = 100.dp, end = 72.dp, bottom = 84.dp),
            contentAlignment = Alignment.Center,
        ) {
            val panelShape = RoundedCornerShape(28.dp)
            Column(
                modifier = Modifier
                    .width(560.dp)
                    .background(color = DarkBackground.copy(alpha = 0.68f), shape = panelShape)
                    .border(1.2.dp, Color.White.copy(alpha = 0.20f), panelShape)
                    .padding(horizontal = 14.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = "SEARCH SUBTITLES",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontSize = 12.sp,
                        letterSpacing = 1.8.sp,
                        fontWeight = FontWeight.Bold,
                    ),
                    color = Color.White.copy(alpha = 0.58f),
                    modifier = Modifier.padding(horizontal = 8.dp),
                )

                val langIndex = TvSubtitleLanguageOptions.indexOf(state.language)
                    .takeIf { it >= 0 } ?: 0
                TvDialogCyclerRow(
                    title = "Language",
                    value = tvLanguageDisplayName(TvSubtitleLanguageOptions[langIndex]),
                    onPrevious = {
                        val prev = (langIndex - 1 + TvSubtitleLanguageOptions.size) %
                            TvSubtitleLanguageOptions.size
                        onLanguageChanged(TvSubtitleLanguageOptions[prev])
                    },
                    onNext = {
                        val next = (langIndex + 1) % TvSubtitleLanguageOptions.size
                        onLanguageChanged(TvSubtitleLanguageOptions[next])
                    },
                    modifier = Modifier.focusRequester(languageRowFocus),
                )

                TvDialogActionRow(
                    title = if (state.isSearching) "Searching…" else "Search",
                    enabled = !state.isSearching && state.downloadingResultId == null,
                    onClick = onSearch,
                )

                state.error?.let { error ->
                    Text(
                        text = error,
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
                        color = Color(0xFFEF4444),
                        modifier = Modifier.padding(horizontal = 8.dp),
                    )
                }

                if (state.hasSearched && !state.isSearching &&
                    state.results.isEmpty() && state.error == null
                ) {
                    Text(
                        text = "No subtitles found",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.56f),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                    )
                }

                if (state.results.isNotEmpty()) {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 320.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        items(
                            state.results,
                            key = { "${it.provider}:${it.id}" },
                        ) { result ->
                            TvSubtitleResultRow(
                                result = result,
                                isDownloading = state.downloadingResultId == result.id,
                                enabled = state.downloadingResultId == null,
                                onClick = { onDownload(result) },
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * One provider search hit: score badge (bucket-colored), release name, and a
 * meta line with provider abbreviation badge, optional HI marker, and
 * download count. OK downloads; an inline spinner replaces the chevron slot
 * while this row's download is in flight.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun TvSubtitleResultRow(
    result: SubtitleResult,
    isDownloading: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val shape = RoundedCornerShape(16.dp)

    Surface(
        onClick = { if (enabled) onClick() },
        interactionSource = interactionSource,
        shape = ClickableSurfaceDefaults.shape(shape = shape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.White.copy(alpha = 0.04f),
            contentColor = Color.White,
            focusedContainerColor = FocusedContainer,
            focusedContentColor = FocusedContent,
            pressedContainerColor = FocusedContainer,
            pressedContentColor = FocusedContent,
            disabledContainerColor = Color.White.copy(alpha = 0.03f),
            disabledContentColor = Color.White.copy(alpha = 0.38f),
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.02f),
        border = ClickableSurfaceDefaults.border(
            focusedBorder = Border(
                border = BorderStroke(2.dp, DarkBackground.copy(alpha = 0.82f)),
                shape = shape,
            ),
        ),
        glow = ClickableSurfaceDefaults.glow(
            focusedGlow = Glow(
                elevationColor = Color.White.copy(alpha = 0.18f),
                elevation = 16.dp,
            ),
        ),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .then(
                if (isFocused) {
                    Modifier.border(2.dp, Color.White.copy(alpha = 0.98f), shape)
                } else {
                    Modifier
                },
            ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Score badge — bucket color, white score text.
            Box(
                modifier = Modifier
                    .background(subtitleScoreColor(result.score), RoundedCornerShape(8.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            ) {
                Text(
                    text = result.score.toString(),
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                    ),
                    color = Color.White,
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Text(
                    text = result.releaseName.ifBlank { "Unnamed release" },
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontSize = 16.sp,
                        lineHeight = 19.sp,
                        fontWeight = FontWeight.SemiBold,
                    ),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = if (isFocused) FocusedContent else Color.White,
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .background(
                                subtitleProviderColor(result.provider),
                                RoundedCornerShape(6.dp),
                            )
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    ) {
                        Text(
                            text = subtitleProviderAbbreviation(result.provider),
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                            ),
                            color = Color.White,
                        )
                    }
                    if (result.hearingImpaired) {
                        Text(
                            text = "HI",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                            ),
                            color = if (isFocused) {
                                FocusedContent.copy(alpha = 0.70f)
                            } else {
                                Color.White.copy(alpha = 0.66f)
                            },
                        )
                    }
                    Text(
                        text = "${result.downloads} downloads · ${tvLanguageDisplayName(result.language)}",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                        color = if (isFocused) {
                            FocusedContent.copy(alpha = 0.70f)
                        } else {
                            Color.White.copy(alpha = 0.56f)
                        },
                    )
                }
            }
            if (isDownloading) {
                CircularProgressIndicator(
                    color = if (isFocused) FocusedContent else Color.White,
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

/**
 * Focusable "Title        ‹ Value ›" row — left/right cycles the value while
 * the row holds focus, Select also advances. Shared by the search dialog's
 * language picker and the AI dialog's mode/source/target pickers.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
internal fun TvDialogCyclerRow(
    title: String,
    value: String,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val shape = RoundedCornerShape(16.dp)

    Surface(
        onClick = { if (enabled) onNext() },
        interactionSource = interactionSource,
        shape = ClickableSurfaceDefaults.shape(shape = shape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.White.copy(alpha = 0.04f),
            contentColor = if (enabled) Color.White else Color.White.copy(alpha = 0.42f),
            focusedContainerColor = FocusedContainer,
            focusedContentColor = FocusedContent,
            pressedContainerColor = FocusedContainer,
            pressedContentColor = FocusedContent,
            disabledContainerColor = Color.White.copy(alpha = 0.03f),
            disabledContentColor = Color.White.copy(alpha = 0.38f),
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.025f),
        border = ClickableSurfaceDefaults.border(
            focusedBorder = Border(
                border = BorderStroke(2.dp, DarkBackground.copy(alpha = 0.82f)),
                shape = shape,
            ),
        ),
        glow = ClickableSurfaceDefaults.glow(
            focusedGlow = Glow(
                elevationColor = Color.White.copy(alpha = 0.18f),
                elevation = 16.dp,
            ),
        ),
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .onPreviewKeyEvent { ev ->
                if (!enabled || ev.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (ev.key) {
                    Key.DirectionLeft -> { onPrevious(); true }
                    Key.DirectionRight -> { onNext(); true }
                    else -> false
                }
            }
            .then(
                if (isFocused) {
                    Modifier.border(2.dp, Color.White.copy(alpha = 0.98f), shape)
                } else {
                    Modifier
                },
            ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                ),
                modifier = Modifier.weight(1f),
                color = if (isFocused) FocusedContent else Color.White,
            )
            Text(
                text = "‹  $value  ›",
                style = MaterialTheme.typography.titleMedium.copy(fontSize = 16.sp),
                color = if (isFocused) FocusedContent else Color.White.copy(alpha = 0.80f),
            )
        }
    }
}

/** Centered full-width action row (Search / Start / Cancel) — TvOptionDialog row idiom. */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
internal fun TvDialogActionRow(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val shape = RoundedCornerShape(16.dp)

    Surface(
        onClick = { if (enabled) onClick() },
        interactionSource = interactionSource,
        shape = ClickableSurfaceDefaults.shape(shape = shape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.White.copy(alpha = 0.08f),
            contentColor = if (enabled) Color.White else Color.White.copy(alpha = 0.42f),
            focusedContainerColor = FocusedContainer,
            focusedContentColor = FocusedContent,
            pressedContainerColor = FocusedContainer,
            pressedContentColor = FocusedContent,
            disabledContainerColor = Color.White.copy(alpha = 0.03f),
            disabledContentColor = Color.White.copy(alpha = 0.38f),
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.025f),
        border = ClickableSurfaceDefaults.border(
            focusedBorder = Border(
                border = BorderStroke(2.dp, DarkBackground.copy(alpha = 0.82f)),
                shape = shape,
            ),
        ),
        glow = ClickableSurfaceDefaults.glow(
            focusedGlow = Glow(
                elevationColor = Color.White.copy(alpha = 0.18f),
                elevation = 16.dp,
            ),
        ),
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .then(
                if (isFocused) {
                    Modifier.border(2.dp, Color.White.copy(alpha = 0.98f), shape)
                } else {
                    Modifier
                },
            ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                ),
                color = if (isFocused) FocusedContent else Color.White,
            )
        }
    }
}
```

**3b. `TvPlayerHud.kt` — pane callback + action row.** Edit point 1 — `TvPlayerHud` signature, after `onSubtitleDelayChanged: (Int) -> Unit,`:

```kotlin
    subtitleDelayMs: Int,
    onSubtitleDelayChanged: (Int) -> Unit,
    // Subtitle suite: fired the first time the Subtitles pane composes (lazy
    // AI-status probe lives in the VM); Search row shown only when the player
    // knows its media file id; Translate row hidden while null (AI gating).
    onSubtitlesPaneShown: () -> Unit,
    onSearchSubtitles: (() -> Unit)?,
    onTranslateWithAi: (() -> Unit)? = null,
```

Edit point 2 — the `HudTab.Subtitles ->` branch in the `when (selectedTab)` block:

```kotlin
                HudTab.Subtitles -> HudSubtitlesPane(
                    subtitleTracks = subtitleTracks,
                    onSelectSubtitle = onSelectSubtitle,
                    subtitleDelayMs = subtitleDelayMs,
                    onSubtitleDelayChanged = onSubtitleDelayChanged,
                    onPaneShown = onSubtitlesPaneShown,
                    onSearchSubtitles = onSearchSubtitles,
                    onTranslateWithAi = onTranslateWithAi,
                )
```

Edit point 3 — replace `HudSubtitlesPane` in full (action rows sit between the track picker and the delay stepper, per spec "beneath the track list"):

```kotlin
@Composable
private fun HudSubtitlesPane(
    subtitleTracks: List<PlayerTrackEntry>,
    onSelectSubtitle: (Int) -> Unit,
    subtitleDelayMs: Int,
    onSubtitleDelayChanged: (Int) -> Unit,
    onPaneShown: () -> Unit,
    onSearchSubtitles: (() -> Unit)?,
    onTranslateWithAi: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    // Lazy AI-status probe — once per player session (VM guards re-entry).
    LaunchedEffect(Unit) { onPaneShown() }

    Column(
        modifier = modifier.fillMaxSize().padding(Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.lg),
    ) {
        Text(
            text = "Subtitle track",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        HudPickerPane(
            // "Off" is the canonical first entry per tvOS spec; the picker
            // is therefore never empty even when no subtitle tracks are
            // advertised.
            options = buildList {
                add(TrackOption(-1, "Off", subtitleTracks.none { it.isSelected }))
                addAll(subtitleTracks.map { TrackOption(it.index, it.label, it.isSelected) })
            },
            onSelect = onSelectSubtitle,
        )

        // Subtitle suite action rows — explicit Select press (clickable, not
        // focus-to-commit) so traversing past them never opens a dialog.
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (onSearchSubtitles != null) {
                HudActionRow(label = "Search subtitles", onClick = onSearchSubtitles)
            }
            if (onTranslateWithAi != null) {
                HudActionRow(label = "Translate with AI", onClick = onTranslateWithAi)
            }
        }

        Text(
            text = "Subtitle delay: ${subtitleDelayMs} ms",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        DelayStepperRow(
            valueMs = subtitleDelayMs,
            onChange = onSubtitleDelayChanged,
        )
    }
}

/**
 * Full-width action row for HUD panes — [HudPickerRow]'s visual idiom but a
 * true click target like [DelayStepperButton]: an explicit Select press is
 * required (focus-driven commit would fire dialogs during plain traversal).
 */
@Composable
private fun HudActionRow(
    label: String,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    val bg = if (isFocused) Color.White.copy(alpha = 0.94f) else Color.White.copy(alpha = 0.06f)
    val fg = if (isFocused) Color.Black else Color.White.copy(alpha = 0.86f)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(bg)
            .clickable(interactionSource = interactionSource, indication = null) { onClick() }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            color = fg,
            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
            modifier = Modifier.weight(1f),
        )
    }
}
```

**3c. `TvPlayerScreen.kt` — wire HUD + render the dialog.** Add the search-state collection next to the other `collectAsState()` calls at the top of `TvPlayerScreen`:

```kotlin
    val subtitleSearch by viewModel.subtitleSearch.collectAsState()
    val aiTranslate by viewModel.aiTranslate.collectAsState()
```

In the `TvPlayerHud(...)` call, after `onSubtitleDelayChanged = viewModel::onSubtitleDelayChanged,`:

```kotlin
                            onSubtitleDelayChanged = viewModel::onSubtitleDelayChanged,
                            onSubtitlesPaneShown = viewModel::onSubtitlesPaneShown,
                            onSearchSubtitles = if (state.mediaFileId != null) {
                                { viewModel.openSubtitleSearchDialog() }
                            } else {
                                null
                            },
```

After the `if (state.hudOpen) { ... }` block (inside the `state.streamUrl != null ->` branch), add:

```kotlin
                if (state.showSubtitleSearchDialog) {
                    TvSubtitleSearchDialog(
                        state = subtitleSearch,
                        onLanguageChanged = viewModel::setSubtitleSearchLanguage,
                        onSearch = viewModel::searchSubtitles,
                        onDownload = viewModel::downloadSubtitle,
                        onDismiss = viewModel::closeSubtitleSearchDialog,
                    )
                }
```

- [ ] **Step 4: Run tests**

```bash
./gradlew :androidTvApp:compileDebugKotlinAndroid :androidTvApp:testDebugUnitTest :androidTvApp:assembleDebug
```

Manual D-pad checklist (real device/emulator against a server with at least one subtitle provider configured):
- [ ] Play a title → Up opens HUD → Right to Subtitles tab: "Search subtitles" row sits below the track picker, above the delay stepper.
- [ ] D-pad down through track rows to the action row: traversal does NOT open the dialog; track rows still focus-select as before.
- [ ] Select on "Search subtitles" → dialog opens centered, Language row holds initial focus, value defaults to the profile subtitle language (else English).
- [ ] Left/Right on the Language row cycles display names; Down reaches "Search".
- [ ] Select "Search" → row reads "Searching…", then result rows appear: colored score badge (green ≥70 / amber ≥40 / red), release name (2-line ellipsis), provider badge (OS/SDL/SS), HI marker when applicable, downloads count.
- [ ] Full D-pad traversal of results scrolls the list; Back closes the dialog only (HUD stays open).
- [ ] Select a result → inline spinner on that row, other rows inert; on success the dialog closes itself, the Subtitles pane shows the new "{Language} (provider)" track, it is auto-selected (checkmark), and cues render at the current position — playback did NOT restart from 0 (brief rebuffer at the same position is expected).
- [ ] Reopen the dialog → previous results still listed.
- [ ] Server with no providers: Search surfaces the server error text inline.
- [ ] Network drop mid-download: error text inline, row spinner clears, dialog stays open.

- [ ] **Step 5: Commit**

```bash
git add androidTvApp/src/androidMain/kotlin/com/continuum/app/tv/ui/screens/player/TvSubtitleSearchDialog.kt \
        androidTvApp/src/androidMain/kotlin/com/continuum/app/tv/ui/screens/player/TvPlayerHud.kt \
        androidTvApp/src/androidMain/kotlin/com/continuum/app/tv/ui/screens/player/TvPlayerScreen.kt
git commit -m "Add TV subtitle search dialog and HUD action row

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>"
```

---

### Task T3: TvAiTranslateDialog + HUD "Translate with AI" row (AI-status gated)

**Files:**
- Create: `/Users/dev/projects/silo/silo-android/androidTvApp/src/androidMain/kotlin/com/continuum/app/tv/ui/screens/player/TvAiTranslateDialog.kt`
- Modify: `/Users/dev/projects/silo/silo-android/androidTvApp/src/androidMain/kotlin/com/continuum/app/tv/ui/screens/player/TvPlayerScreen.kt`

- [ ] **Step 1: Write the failing test**

No new unit test — Compose TV UI plus VM orchestration already landed in Task 1; the poll loop and merge are unit-tested in the shared tasks. Verification is the manual D-pad checklist in Step 4 (including cancel-mid-job and exit-during-job).

- [ ] **Step 2: Run test to verify it fails**

n.a. (no failing test). Green baseline:
```bash
./gradlew :androidTvApp:compileDebugKotlinAndroid :androidTvApp:testDebugUnitTest
```

- [ ] **Step 3: Implementation**

**3a. Create `TvAiTranslateDialog.kt`** (reuses `TvDialogCyclerRow`, `TvDialogActionRow`, `TvSubtitleLanguageOptions`, `tvLanguageDisplayName` from Task 2 — same package):

```kotlin
package com.continuum.app.tv.ui.screens.player

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.continuum.app.model.playback.PlayerSubtitleInfo
import com.continuum.app.tv.ui.theme.DarkBackground

/** Which capture mode the dialog is in — availability comes from AiStatus. */
private enum class TvAiTranslateMode(val label: String) {
    Subtitles("From subtitles"),
    Audio("From audio"),
}

/**
 * D-pad AI translate/transcribe dialog (TvOptionDialog panel idiom). Pure
 * pickers — no text input. Mode row appears only when both modes are
 * available; otherwise the single available mode is fixed. Submitting flips
 * the dialog body to an in-dialog progress view (percent + progress_message
 * + Cancel). Completion: the VM refreshes the track list, auto-selects
 * `result_subtitle_id`, and bumps `completedNonce` — observed here to dismiss.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TvAiTranslateDialog(
    aiState: AiTranslateUiState,
    /** Text-based session subtitle tracks (PGS/DVD filtered out by the caller). */
    subtitleSources: List<PlayerSubtitleInfo>,
    /** ExoPlayer audio track entries (ordinal index = server audio track index). */
    audioSources: List<PlayerTrackEntry>,
    defaultTargetLanguage: String,
    onSubmit: (kind: String, sourceIndex: Int, sourceLanguage: String?, targetLanguage: String) -> Unit,
    onCancelJob: () -> Unit,
    onClearError: () -> Unit,
    onDismiss: () -> Unit,
) {
    val subtitlesAvailable = aiState.status.enabled && subtitleSources.isNotEmpty()
    val audioAvailable = aiState.status.transcribeEnabled && audioSources.isNotEmpty()

    var mode by remember(subtitlesAvailable, audioAvailable) {
        mutableStateOf(if (subtitlesAvailable) TvAiTranslateMode.Subtitles else TvAiTranslateMode.Audio)
    }
    var subtitleSourcePos by remember { mutableIntStateOf(0) }
    var audioSourcePos by remember {
        mutableIntStateOf(audioSources.indexOfFirst { it.isSelected }.coerceAtLeast(0))
    }
    var targetPos by remember {
        mutableIntStateOf(
            TvSubtitleLanguageOptions.indexOf(defaultTargetLanguage.take(2).lowercase())
                .takeIf { it >= 0 } ?: 0,
        )
    }
    val firstRowFocus = remember { FocusRequester() }
    val initialNonce = remember { aiState.completedNonce }

    LaunchedEffect(Unit) { runCatching { firstRowFocus.requestFocus() } }
    LaunchedEffect(aiState.completedNonce) {
        if (aiState.completedNonce != initialNonce) onDismiss()
    }

    Popup(
        alignment = Alignment.Center,
        onDismissRequest = onDismiss,
        properties = PopupProperties(
            focusable = true,
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
            clippingEnabled = false,
        ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 72.dp, top = 100.dp, end = 72.dp, bottom = 84.dp),
            contentAlignment = Alignment.Center,
        ) {
            val panelShape = RoundedCornerShape(28.dp)
            Column(
                modifier = Modifier
                    .width(520.dp)
                    .background(color = DarkBackground.copy(alpha = 0.68f), shape = panelShape)
                    .border(1.2.dp, Color.White.copy(alpha = 0.20f), panelShape)
                    .padding(horizontal = 14.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = "TRANSLATE WITH AI",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontSize = 12.sp,
                        letterSpacing = 1.8.sp,
                        fontWeight = FontWeight.Bold,
                    ),
                    color = Color.White.copy(alpha = 0.58f),
                    modifier = Modifier.padding(horizontal = 8.dp),
                )

                when (val phase = aiState.phase) {
                    is AiJobPhase.Running -> {
                        TvAiJobProgress(
                            progress = phase.progress,
                            message = phase.message,
                            onCancel = onCancelJob,
                            cancelFocus = firstRowFocus,
                        )
                    }
                    AiJobPhase.Submitting -> {
                        Text(
                            text = "Submitting…",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White.copy(alpha = 0.72f),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 12.dp),
                        )
                    }
                    is AiJobPhase.Failed, AiJobPhase.Idle -> {
                        if (!subtitlesAvailable && !audioAvailable) {
                            // Neither mode usable: AI configured but no
                            // translatable text tracks and transcription
                            // unavailable — explanatory empty state.
                            Text(
                                text = "No translatable subtitle tracks, and audio " +
                                    "transcription is not available on this server.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.White.copy(alpha = 0.66f),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
                            )
                        } else {
                            if (subtitlesAvailable && audioAvailable) {
                                TvDialogCyclerRow(
                                    title = "Mode",
                                    value = mode.label,
                                    onPrevious = {
                                        mode = if (mode == TvAiTranslateMode.Subtitles) {
                                            TvAiTranslateMode.Audio
                                        } else {
                                            TvAiTranslateMode.Subtitles
                                        }
                                    },
                                    onNext = {
                                        mode = if (mode == TvAiTranslateMode.Subtitles) {
                                            TvAiTranslateMode.Audio
                                        } else {
                                            TvAiTranslateMode.Subtitles
                                        }
                                    },
                                    modifier = Modifier.focusRequester(firstRowFocus),
                                )
                            }

                            val sourceFocusModifier =
                                if (!(subtitlesAvailable && audioAvailable)) {
                                    Modifier.focusRequester(firstRowFocus)
                                } else {
                                    Modifier
                                }
                            if (mode == TvAiTranslateMode.Subtitles) {
                                val pos = subtitleSourcePos.coerceIn(0, subtitleSources.lastIndex)
                                TvDialogCyclerRow(
                                    title = "Source subtitle",
                                    value = subtitleSourceLabel(subtitleSources[pos]),
                                    onPrevious = {
                                        subtitleSourcePos =
                                            (pos - 1 + subtitleSources.size) % subtitleSources.size
                                    },
                                    onNext = {
                                        subtitleSourcePos = (pos + 1) % subtitleSources.size
                                    },
                                    modifier = sourceFocusModifier,
                                )
                            } else {
                                val pos = audioSourcePos.coerceIn(0, audioSources.lastIndex)
                                TvDialogCyclerRow(
                                    title = "Source audio",
                                    value = audioSources[pos].label
                                        .ifBlank { "Track ${audioSources[pos].index + 1}" },
                                    onPrevious = {
                                        audioSourcePos =
                                            (pos - 1 + audioSources.size) % audioSources.size
                                    },
                                    onNext = { audioSourcePos = (pos + 1) % audioSources.size },
                                    modifier = sourceFocusModifier,
                                )
                            }

                            TvDialogCyclerRow(
                                title = "Target language",
                                value = tvLanguageDisplayName(TvSubtitleLanguageOptions[targetPos]),
                                onPrevious = {
                                    targetPos = (targetPos - 1 + TvSubtitleLanguageOptions.size) %
                                        TvSubtitleLanguageOptions.size
                                },
                                onNext = {
                                    targetPos = (targetPos + 1) % TvSubtitleLanguageOptions.size
                                },
                            )

                            // Quota applies to transcribe kinds only; admins are
                            // exempt (limited=false → no line).
                            val quotaExhausted = mode == TvAiTranslateMode.Audio &&
                                aiState.quota?.limited == true &&
                                (aiState.quota.remaining) <= 0
                            if (mode == TvAiTranslateMode.Audio &&
                                aiState.quota?.limited == true
                            ) {
                                val q = aiState.quota
                                Text(
                                    text = if (quotaExhausted) {
                                        "Transcription quota exhausted (${q.used} of ${q.limit} used ${quotaPeriodText(q.period)})"
                                    } else {
                                        "${q.remaining} of ${q.limit} transcriptions left ${quotaPeriodText(q.period)}"
                                    },
                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
                                    color = if (quotaExhausted) {
                                        Color(0xFFF59E0B)
                                    } else {
                                        Color.White.copy(alpha = 0.66f)
                                    },
                                    modifier = Modifier.padding(horizontal = 8.dp),
                                )
                            }

                            if (phase is AiJobPhase.Failed) {
                                Text(
                                    text = phase.message,
                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
                                    color = Color(0xFFEF4444),
                                    modifier = Modifier.padding(horizontal = 8.dp),
                                )
                            }

                            TvDialogActionRow(
                                title = if (mode == TvAiTranslateMode.Subtitles) {
                                    "Translate"
                                } else {
                                    "Transcribe"
                                },
                                enabled = !quotaExhausted,
                                onClick = {
                                    if (phase is AiJobPhase.Failed) onClearError()
                                    val targetLanguage = TvSubtitleLanguageOptions[targetPos]
                                    if (mode == TvAiTranslateMode.Subtitles) {
                                        val src = subtitleSources[
                                            subtitleSourcePos.coerceIn(0, subtitleSources.lastIndex),
                                        ]
                                        // source_index = the session's combined
                                        // subtitle index (PlayerSubtitleInfo.index),
                                        // NOT the ExoPlayer text-group ordinal.
                                        onSubmit("translate", src.index, src.language, targetLanguage)
                                    } else {
                                        val src = audioSources[
                                            audioSourcePos.coerceIn(0, audioSources.lastIndex),
                                        ]
                                        val sameLanguage = src.language
                                            ?.take(2)
                                            ?.equals(targetLanguage.take(2), ignoreCase = true) == true
                                        onSubmit(
                                            if (sameLanguage) "transcribe" else "transcribe_translate",
                                            src.index,
                                            src.language,
                                            targetLanguage,
                                        )
                                    }
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

/** In-dialog job progress: percent bar + server progress_message + Cancel row. */
@Composable
private fun TvAiJobProgress(
    progress: Double,
    message: String?,
    onCancel: () -> Unit,
    cancelFocus: FocusRequester,
) {
    val fraction = progress.coerceIn(0.0, 1.0).toFloat()

    LaunchedEffect(Unit) { runCatching { cancelFocus.requestFocus() } }

    Column(
        modifier = Modifier.padding(horizontal = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = "Translating… ${(fraction * 100).toInt()}%",
            style = MaterialTheme.typography.titleMedium.copy(
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
            ),
            color = Color.White,
        )
        // Determinate bar — plain Boxes to keep the TV dialog idiom (no
        // Material phone widgets beyond what the player already uses).
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(Color.White.copy(alpha = 0.14f)),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction)
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(Color.White.copy(alpha = 0.92f)),
            )
        }
        message?.takeIf { it.isNotBlank() }?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
                color = Color.White.copy(alpha = 0.66f),
            )
        }
        TvDialogActionRow(
            title = "Cancel",
            onClick = onCancel,
            modifier = Modifier.focusRequester(cancelFocus),
        )
    }
}

private fun subtitleSourceLabel(info: PlayerSubtitleInfo): String =
    info.label
        ?: info.language?.let { tvLanguageDisplayName(it) }
        ?: "Track ${info.index}"

private fun quotaPeriodText(period: String): String = when (period.lowercase()) {
    "day" -> "today"
    "week" -> "this week"
    "month" -> "this month"
    else -> "this $period"
}
```

**3b. `TvPlayerScreen.kt` — gate + wire the Translate row and render the dialog.** In the `TvPlayerHud(...)` call, directly after the `onSearchSubtitles = ...` argument added in Task 2:

```kotlin
                            onTranslateWithAi = if (
                                state.mediaFileId != null &&
                                (aiTranslate.status.enabled || aiTranslate.status.transcribeEnabled)
                            ) {
                                { viewModel.openAiTranslateDialog() }
                            } else {
                                null
                            },
```

Directly after the `if (state.showSubtitleSearchDialog) { ... }` block added in Task 2:

```kotlin
                if (state.showAiTranslateDialog) {
                    // Translate sources = the session's sidecar subtitle list,
                    // text formats only — PGS/DVD bitmaps can't be translated.
                    // source_index for the server is PlayerSubtitleInfo.index
                    // (the session's combined subtitle index).
                    val translatableSubtitleSources = remember(state.subtitleUrls) {
                        state.subtitleUrls.filter {
                            when (it.codec?.lowercase()) {
                                null, "srt", "subrip", "ass", "ssa",
                                "vtt", "webvtt", "ttml" -> true
                                else -> false
                            }
                        }
                    }
                    TvAiTranslateDialog(
                        aiState = aiTranslate,
                        subtitleSources = translatableSubtitleSources,
                        audioSources = state.audioTracks,
                        defaultTargetLanguage = state.preferredTextLanguage
                            ?.takeIf { it.isNotBlank() } ?: "en",
                        onSubmit = viewModel::submitAiTranslate,
                        onCancelJob = viewModel::cancelAiTranslateJob,
                        onClearError = viewModel::clearAiTranslateError,
                        onDismiss = viewModel::closeAiTranslateDialog,
                    )
                }
```

(`aiTranslate` was already collected in Task 2's screen edit; `remember` is already imported.)

- [ ] **Step 4: Run tests**

```bash
./gradlew :androidTvApp:compileDebugKotlinAndroid :androidTvApp:testDebugUnitTest :androidTvApp:assembleDebug
```

Manual D-pad checklist (server with AI configured; repeat the gating check against a server without AI):
- [ ] AI-disabled server: Subtitles pane shows NO "Translate with AI" row (and no error anywhere) — including after toggling panes repeatedly (status probed exactly once; check logcat for a single `/subtitles/ai/status` call).
- [ ] AI-enabled server: row appears after the pane's first composition; Select opens the dialog; first row holds focus.
- [ ] Both modes available → Mode row present, Left/Right toggles "From subtitles"/"From audio" and the source row swaps accordingly; only one available → no Mode row, fixed mode.
- [ ] Source cycler lists the expected tracks (text subtitle tracks only — a PGS-only title with transcribe disabled shows the explanatory empty text); Target language cycles with display names.
- [ ] Audio mode with a quota-limited (non-admin) user: "X of Y transcriptions left this {period}" line; exhaust the quota → amber exhausted line, submit row disabled (Select is a no-op); after a 429 the quota line refreshes to exhausted.
- [ ] Submit from subtitles → dialog flips to progress (percent climbs, progress_message updates), focus lands on Cancel.
- [ ] Cancel mid-job → dialog returns to the form; server job shows cancelled (verify in web admin / job list).
- [ ] Let a job complete → dialog dismisses itself, Subtitles pane lists the new translated track auto-selected, cues render at the current playhead, playback position preserved (no restart from 0).
- [ ] Force a failure (e.g. unconfigured engine → 503) → error_message inline in the dialog, "Translate" retries after clearing.
- [ ] Back during a running job → dialog closes, job keeps running; reopening shows live progress; exiting the player mid-job → no crash, poll cancelled (no further job polling in logcat).
- [ ] Full D-pad traversal: every row reachable Up/Down, Left/Right never escapes the dialog from a cycler row.

- [ ] **Step 5: Commit**

```bash
git add androidTvApp/src/androidMain/kotlin/com/continuum/app/tv/ui/screens/player/TvAiTranslateDialog.kt \
        androidTvApp/src/androidMain/kotlin/com/continuum/app/tv/ui/screens/player/TvPlayerScreen.kt
git commit -m "Add TV AI translate dialog gated on server AI status

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>"
```
