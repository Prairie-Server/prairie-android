# Admin Core Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Restore + extend the admin surface per docs/superpowers/specs/2026-06-12-admin-core-design.md. Mobile = stats + users + sessions + logs + scans; TV = stats dashboard only. Metadata editing is OUT of scope. Live data via REST + pull-to-refresh.

**Architecture:** Shared KMP layer (acting-admin gate + AdminApi/AdminRepository/models verified against the Go server) feeds an acting-admin-gated mobile Admin hub with sub-screens and a TV stats screen. Reuses the established list/pull-to-refresh/cursor-pagination idioms (requests/inbox screens).

**Tech Stack:** Kotlin Multiplatform, Ktor, kotlinx.serialization, Koin, Jetpack Compose + TV Compose.

**Sections:** S = shared (S1-S4), H = mobile hub/stats/users + TV stats (H1-H4), D = mobile data screens sessions/logs/scans (D1-D3). Order: S, then H (H1 creates the hub + route placeholders + gating), then D (overwrites the sessions/logs/scans placeholders). Mobile/TV tasks carry Dependencies notes with ASSUMED shared signatures — executors verify against landed code (landed wins).

---

## Section S: Shared layer

The convention is `./gradlew :shared:testDebugUnitTest --tests "..."` for commonTest. I now have all evidence needed. Let me write the complete plan tasks.

---

### Task S1: Gating — `Profile.isPrimary` + shared `isActingAdmin`

**Files:**
- Modify: `/Users/dev/projects/silo/prairie-android/shared/src/commonMain/kotlin/com/continuum/app/model/profile/ProfileModels.kt`
- Create: `/Users/dev/projects/silo/prairie-android/shared/src/commonMain/kotlin/com/continuum/app/model/auth/AdminPermissions.kt`
- Test: `/Users/dev/projects/silo/prairie-android/shared/src/commonTest/kotlin/com/continuum/app/model/auth/AdminPermissionsTest.kt`

Evidence (server gate, design §"Admin gating"): acting admin = `user.role == "admin"` AND active `profile.is_primary == true`. `Profile` currently has **no** `is_primary` field (verified in ProfileModels.kt) so it must be added. The UI computes this from `AuthRepository`/`AuthApi.getMe()` (returns `User` with `role`) and `ProfileRepository.getActiveProfile(): Profile?`. Placing `isActingAdmin` in `model/auth` keeps it package-adjacent to `User` while accepting a `Profile` param.

- [ ] **Step 1: Write the failing test** (full code)

```kotlin
// shared/src/commonTest/kotlin/com/continuum/app/model/auth/AdminPermissionsTest.kt
package com.continuum.app.model.auth

import com.continuum.app.model.profile.Profile
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AdminPermissionsTest {

    private fun user(role: String) = User(
        id = 1,
        username = "admin",
        email = "admin@example.com",
        role = role,
    )

    private fun profile(isPrimary: Boolean) = Profile(
        id = "prof-1",
        name = "Owner",
        isPrimary = isPrimary,
    )

    @Test
    fun `admin role on primary profile is acting admin`() {
        assertTrue(isActingAdmin(user("admin"), profile(isPrimary = true)))
    }

    @Test
    fun `admin role on non-primary profile is not acting admin`() {
        assertFalse(isActingAdmin(user("admin"), profile(isPrimary = false)))
    }

    @Test
    fun `admin role with null profile is acting admin (profile not yet resolved)`() {
        assertTrue(isActingAdmin(user("admin"), null))
    }

    @Test
    fun `non-admin role is never acting admin`() {
        assertFalse(isActingAdmin(user("user"), profile(isPrimary = true)))
        assertFalse(isActingAdmin(user("user"), null))
    }

    @Test
    fun `null user is never acting admin`() {
        assertFalse(isActingAdmin(null, profile(isPrimary = true)))
        assertFalse(isActingAdmin(null, null))
    }

    @Test
    fun `profile defaults is_primary to false when wire omits it`() {
        val p = Profile(id = "p", name = "Kid")
        assertFalse(p.isPrimary)
    }
}
```

- [ ] **Step 2: Run test to verify it fails** (command + expected failure)

```bash
cd /Users/dev/projects/silo/prairie-android && ./gradlew :shared:testDebugUnitTest --tests "com.continuum.app.model.auth.AdminPermissionsTest"
```

Expected: compilation failure — `Profile` has no `isPrimary` member and `isActingAdmin` is unresolved (`unresolved reference: isPrimary`, `unresolved reference: isActingAdmin`).

- [ ] **Step 3: Implementation** (complete code)

Add the `isPrimary` field to `Profile` (insert after the `id`/`name`/`avatar` block, before `hasPin`; `@SerialName("is_primary")`, default `false` so older wire payloads decode):

```kotlin
// In ProfileModels.kt, inside data class Profile(...), add this line after `val avatar: String? = null,`
    @SerialName("is_primary") val isPrimary: Boolean = false,
```

Resulting `Profile` head:

```kotlin
@Serializable
data class Profile(
    val id: String,
    val name: String,
    val avatar: String? = null,
    @SerialName("is_primary") val isPrimary: Boolean = false,
    @SerialName("has_pin") val hasPin: Boolean = false,
    // ... rest unchanged
```

Create the permission helper:

```kotlin
// shared/src/commonMain/kotlin/com/continuum/app/model/auth/AdminPermissions.kt
package com.continuum.app.model.auth

import com.continuum.app.model.profile.Profile

/** Admin role wire value (server `user.role`). */
const val ADMIN_ROLE = "admin"

/**
 * Client mirror of the server's `RequireActingAdmin` gate (web
 * `isActingAdmin(user, profile)`): the account role must be admin AND the
 * active household profile must be the primary (owner) profile.
 *
 * A null [profile] is treated as "not yet resolved" and does NOT block an
 * admin user — the active profile may not be loaded when the gate is first
 * evaluated, and every admin route is still gated server-side (defense in
 * depth). A null [user] is never acting-admin.
 */
fun isActingAdmin(user: User?, profile: Profile?): Boolean =
    user?.role == ADMIN_ROLE && (profile == null || profile.isPrimary)
```

- [ ] **Step 4: Run tests** (command)

```bash
cd /Users/dev/projects/silo/prairie-android && ./gradlew :shared:testDebugUnitTest --tests "com.continuum.app.model.auth.AdminPermissionsTest" --tests "com.continuum.app.model.profile.*"
```

- [ ] **Step 5: Commit**

```bash
git add shared/src/commonMain/kotlin/com/continuum/app/model/profile/ProfileModels.kt \
        shared/src/commonMain/kotlin/com/continuum/app/model/auth/AdminPermissions.kt \
        shared/src/commonTest/kotlin/com/continuum/app/model/auth/AdminPermissionsTest.kt && \
git commit -m "Add Profile.isPrimary and shared isActingAdmin gate

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>"
```

---

### Task S2: `model/admin/AdminModels.kt` — admin DTOs + serialization tests

**Files:**
- Create: `/Users/dev/projects/silo/prairie-android/shared/src/commonMain/kotlin/com/continuum/app/model/admin/AdminModels.kt`
- Test: `/Users/dev/projects/silo/prairie-android/shared/src/commonTest/kotlin/com/continuum/app/model/admin/AdminModelsSerializationTest.kt`

Evidence (Go structs, exact `json:"..."` tags):
- `AdminStats` + `WatchProviderActivity` from `admin_stats.go` (quoted above): all snake_case; `total_storage_bytes int64`; `WatchProviderActivity` has `trakt_connected_profiles`, `trakt_enabled_profiles`, `trakt_export_enabled`, `trakt_scrobble_enabled`, `last_sync_completed_at *time.Time (omitempty)`, `sync_runs_24h`, `sync_errors_24h`, `imported_watched_24h`, `imported_progress_24h`, `exported_watched_24h`, `pending_exports`, `failed_exports`, `open_scrobbles`, `scrobbles_24h`.
- `adminUserResponse` from `admin.go` lines 211-228 (quoted): `id, username, email, role, permissions[], enabled, library_ids[], max_playback_quality, max_streams, max_transcodes, max_profiles, download_allowed, download_transcode_allowed, created_at, updated_at, last_active_at(omitempty)`.
- `createUserRequest` admin.go 118-133: adds `password`, `create_default_profile bool`, `default_profile_name(omitempty)`; `library_ids []int`; `max_streams/max_transcodes/max_profiles *int(omitempty)`; `download_allowed/download_transcode_allowed *bool(omitempty)`.
- `updateUserRequest` admin.go 194-208: all `*` / omitempty (partial PUT).
- `playbackSessionRow` from `playback_sessions.go` 23-72 (quoted): full source/target transcode detail. Note `TranscodeNodeURL` is `json:"-"` (NOT serialized — omit from the model).
- `EntryRow` (`opslog/repo.go`) + `ListResult{entries, next_cursor(omitempty)}`; `AuditEntry` (`activitylog/repo.go`) + its `ListResult`.
- `scanRequest{library_id *int, path}`, `scanResponse{status, mode, library_id}`, `scanCancelRequest{library_id}`, `scanCancelResponse{cancelled int, library_id}` from libraries.go 201-219.
- Sessions endpoint returns a **bare JSON array** of session rows (`writeJSON(w, 200, sessions)` admin.go:624) — no envelope. Users list also returns an array via `toAdminUserResponse` (no envelope wrapper).

- [ ] **Step 1: Write the failing test** (full code)

```kotlin
// shared/src/commonTest/kotlin/com/continuum/app/model/admin/AdminModelsSerializationTest.kt
package com.continuum.app.model.admin

import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AdminModelsSerializationTest {

    // Mirrors ContinuumJson (network/ContinuumHttpClientImpl.kt).
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
        explicitNulls = false
        coerceInputValues = true
    }

    @Test
    fun `decodes admin stats with watch provider activity`() {
        val payload = """
            {
              "total_items": 1200, "total_files": 1500, "total_users": 8,
              "total_movies": 400, "total_movie_files": 410,
              "total_shows": 80, "total_show_files": 1090,
              "active_streams": 3, "total_storage_bytes": 987654321012,
              "watch_provider_activity": {
                "trakt_connected_profiles": 5, "trakt_enabled_profiles": 4,
                "trakt_export_enabled": 3, "trakt_scrobble_enabled": 2,
                "last_sync_completed_at": "2026-06-12T08:00:00Z",
                "sync_runs_24h": 12, "sync_errors_24h": 1,
                "imported_watched_24h": 30, "imported_progress_24h": 7,
                "exported_watched_24h": 9, "pending_exports": 2,
                "failed_exports": 0, "open_scrobbles": 1, "scrobbles_24h": 14
              }
            }
        """.trimIndent()

        val stats = json.decodeFromString(AdminStats.serializer(), payload)

        assertEquals(1200, stats.totalItems)
        assertEquals(987654321012L, stats.totalStorageBytes)
        assertEquals(3, stats.activeStreams)
        assertEquals(5L, stats.watchProviderActivity.traktConnectedProfiles)
        assertEquals(14L, stats.watchProviderActivity.scrobbles24h)
        assertEquals("2026-06-12T08:00:00Z", stats.watchProviderActivity.lastSyncCompletedAt)
    }

    @Test
    fun `decodes admin stats when watch provider activity omitted defaults to empty`() {
        val payload = """
            {"total_items":0,"total_files":0,"total_users":0,"total_movies":0,
             "total_movie_files":0,"total_shows":0,"total_show_files":0,
             "active_streams":0,"total_storage_bytes":0}
        """.trimIndent()

        val stats = json.decodeFromString(AdminStats.serializer(), payload)

        assertEquals(0L, stats.watchProviderActivity.traktConnectedProfiles)
        assertNull(stats.watchProviderActivity.lastSyncCompletedAt)
    }

    @Test
    fun `decodes admin user with optional last_active_at present`() {
        val payload = """
            {
              "id": 7, "username": "alice", "email": "a@x.io", "role": "user",
              "permissions": ["request"], "enabled": true,
              "library_ids": [1,2], "max_playback_quality": "1080p",
              "max_streams": 2, "max_transcodes": 1, "max_profiles": 5,
              "download_allowed": true, "download_transcode_allowed": false,
              "created_at": "2026-01-01T00:00:00Z", "updated_at": "2026-06-01T00:00:00Z",
              "last_active_at": "2026-06-12T07:00:00Z"
            }
        """.trimIndent()

        val u = json.decodeFromString(AdminUser.serializer(), payload)

        assertEquals(7, u.id)
        assertEquals(listOf("request"), u.permissions)
        assertEquals(listOf(1, 2), u.libraryIds)
        assertEquals("1080p", u.maxPlaybackQuality)
        assertEquals(2, u.maxStreams)
        assertEquals("2026-06-12T07:00:00Z", u.lastActiveAt)
    }

    @Test
    fun `decodes admin user with last_active_at absent`() {
        val payload = """
            {"id":1,"username":"root","email":"r@x.io","role":"admin",
             "permissions":[],"enabled":true,"library_ids":[],
             "max_playback_quality":"original","max_streams":0,"max_transcodes":0,
             "max_profiles":0,"download_allowed":false,"download_transcode_allowed":false,
             "created_at":"2026-01-01T00:00:00Z","updated_at":"2026-01-01T00:00:00Z"}
        """.trimIndent()

        val u = json.decodeFromString(AdminUser.serializer(), payload)
        assertNull(u.lastActiveAt)
        assertTrue(u.permissions.isEmpty())
    }

    @Test
    fun `decodes a bare array of admin users`() {
        val payload = """
            [{"id":1,"username":"root","email":"r@x.io","role":"admin","permissions":[],
              "enabled":true,"library_ids":[],"max_playback_quality":"original",
              "max_streams":0,"max_transcodes":0,"max_profiles":0,
              "download_allowed":false,"download_transcode_allowed":false,
              "created_at":"2026-01-01T00:00:00Z","updated_at":"2026-01-01T00:00:00Z"}]
        """.trimIndent()

        val users = json.decodeFromString(ListSerializer(AdminUser.serializer()), payload)
        assertEquals(1, users.size)
        assertEquals("root", users[0].username)
    }

    @Test
    fun `create user request omits null optional fields when encoded`() {
        val req = CreateUserRequest(
            username = "bob",
            email = "b@x.io",
            password = "secret",
            role = "user",
            permissions = listOf("request"),
            createDefaultProfile = true,
            libraryIds = listOf(3),
            maxPlaybackQuality = "1080p",
        )

        val encoded = json.encodeToString(CreateUserRequest.serializer(), req)

        assertTrue("\"username\":\"bob\"" in encoded)
        assertTrue("\"create_default_profile\":true" in encoded)
        assertTrue("\"library_ids\":[3]" in encoded)
        // explicitNulls = false → omitted optionals absent
        assertTrue("max_streams" !in encoded)
        assertTrue("default_profile_name" !in encoded)
        assertTrue("download_allowed" !in encoded)
    }

    @Test
    fun `update user request encodes only set fields (partial PUT)`() {
        val req = UpdateUserRequest(enabled = false, maxStreams = 4)
        val encoded = json.encodeToString(UpdateUserRequest.serializer(), req)
        assertTrue("\"enabled\":false" in encoded)
        assertTrue("\"max_streams\":4" in encoded)
        assertTrue("username" !in encoded)
        assertTrue("permissions" !in encoded)
        assertTrue("password" !in encoded)
    }

    @Test
    fun `decodes a rich playback session row with full transcode detail`() {
        val payload = """
            {
              "session_id": "sess-9", "user_id": 3, "username": "alice",
              "profile_id": "prof-2", "profile_name": "Alice",
              "media_file_id": 88, "requested_media_file_id": 88,
              "content_id": "c-1", "media_title": "Cold Harbor", "media_type": "episode",
              "series_name": "Severance", "episode_name": "Cold Harbor",
              "season_number": 2, "episode_number": 10,
              "poster_url": "https://cdn/p.jpg",
              "play_method": "transcode", "reporting_node": "node-a",
              "node_display_name": "Node A", "file_duration": 3600,
              "started_at": "2026-06-12T09:00:00Z", "updated_at": "2026-06-12T09:10:00Z",
              "position_seconds": 612.5, "is_paused": false,
              "has_playback_control": true, "client_ip": "10.0.0.5",
              "audio_track_index": 1, "transcode_audio": true, "stream_bitrate_kbps": 8000,
              "target_resolution": "1080p", "target_video_codec": "h264",
              "target_audio_codec": "aac", "target_bitrate_kbps": 8000,
              "transcode_hw_accel": "vaapi",
              "source_container": "mkv", "source_bitrate_kbps": 20000,
              "source_video_codec": "hevc", "source_video_resolution": "2160p",
              "source_audio_codec": "truehd", "source_audio_channels": 8,
              "source_audio_language": "eng", "source_audio_title": "Surround",
              "source_audio_layout": "7.1",
              "requested_video_codec": "h264", "requested_video_resolution": "1080p",
              "video_decision": "transcode", "audio_decision": "transcode"
            }
        """.trimIndent()

        val s = json.decodeFromString(AdminSession.serializer(), payload)

        assertEquals("sess-9", s.sessionId)
        assertEquals("transcode", s.playMethod)
        assertEquals(true, s.hasPlaybackControl)
        assertEquals(612.5, s.positionSeconds)
        assertEquals(8000, s.streamBitrateKbps)
        assertEquals("2160p", s.sourceVideoResolution)
        assertEquals(8, s.sourceAudioChannels)
        assertEquals("h264", s.targetVideoCodec)
        assertEquals(3600, s.fileDuration)
    }

    @Test
    fun `decodes minimal session row defaulting omitted fields`() {
        val payload = """
            {"session_id":"s1","user_id":1,"username":"u","profile_id":"p",
             "media_file_id":1,"requested_media_file_id":1,"media_title":"M",
             "media_type":"movie","play_method":"direct","reporting_node":"n",
             "started_at":"2026-06-12T09:00:00Z","updated_at":"2026-06-12T09:00:00Z",
             "position_seconds":0,"is_paused":false,"has_playback_control":false,
             "audio_track_index":0,"transcode_audio":false}
        """.trimIndent()

        val s = json.decodeFromString(AdminSession.serializer(), payload)
        assertEquals("s1", s.sessionId)
        assertNull(s.streamBitrateKbps)
        assertNull(s.fileDuration)
        assertEquals("", s.profileName)
        assertNull(s.seasonNumber)
    }

    @Test
    fun `decodes app log page with entries and next_cursor`() {
        val payload = """
            {
              "entries": [
                {"id": 101, "timestamp": "2026-06-12T09:00:00Z", "level": "info",
                 "component": "scanner", "message": "scan complete",
                 "request_id": "req-1", "user_id": 3, "session_id": "sess-1",
                 "playback_session_id": "ps-1", "client_ip": "10.0.0.1",
                 "node_id": "node-a", "attrs": {"folder": "movies", "count": 12}}
              ],
              "next_cursor": "Y3Vyc29y"
            }
        """.trimIndent()

        val page = json.decodeFromString(AdminLogPage.serializer(), payload)

        assertEquals(1, page.entries.size)
        assertEquals(101L, page.entries[0].id)
        assertEquals("scanner", page.entries[0].component)
        assertEquals(3, page.entries[0].userId)
        assertEquals("Y3Vyc29y", page.nextCursor)
        assertTrue(page.entries[0].attrs!!.containsKey("folder"))
    }

    @Test
    fun `decodes app log page without next_cursor and minimal entry`() {
        val payload = """
            {"entries":[{"id":1,"timestamp":"2026-06-12T09:00:00Z","level":"warn",
              "component":"http","message":"slow"}]}
        """.trimIndent()

        val page = json.decodeFromString(AdminLogPage.serializer(), payload)
        assertNull(page.nextCursor)
        assertNull(page.entries[0].requestId)
        assertNull(page.entries[0].userId)
        assertNull(page.entries[0].attrs)
    }

    @Test
    fun `decodes audit log page`() {
        val payload = """
            {
              "entries": [
                {"id": 5, "timestamp": "2026-06-12T09:00:00Z", "client_ip": "10.0.0.2",
                 "user_id": 3, "impersonator_user_id": 1, "session_id": "sess-2",
                 "request_id": "req-9", "method": "POST", "path": "/api/v1/admin/users",
                 "path_pattern": "/api/v1/admin/users", "status_code": 201,
                 "user_agent": "prairie/1.0", "duration_ms": 42}
              ],
              "next_cursor": "Y3Vy"
            }
        """.trimIndent()

        val page = json.decodeFromString(AdminAuditPage.serializer(), payload)

        assertEquals(5L, page.entries[0].id)
        assertEquals("POST", page.entries[0].method)
        assertEquals(201, page.entries[0].statusCode)
        assertEquals(1, page.entries[0].impersonatorUserId)
        assertEquals("Y3Vy", page.nextCursor)
    }

    @Test
    fun `scan request encodes library_id and omits null path`() {
        val req = ScanRequest(libraryId = 4)
        val encoded = json.encodeToString(ScanRequest.serializer(), req)
        assertTrue("\"library_id\":4" in encoded)
        assertTrue("path" !in encoded)
    }

    @Test
    fun `decodes scan response and cancel response`() {
        val scan = json.decodeFromString(
            ScanResponse.serializer(),
            """{"status":"scanning","mode":"incremental","library_id":4}""",
        )
        assertEquals("scanning", scan.status)
        assertEquals("incremental", scan.mode)
        assertEquals(4, scan.libraryId)

        val cancel = json.decodeFromString(
            ScanCancelResponse.serializer(),
            """{"cancelled":2,"library_id":4}""",
        )
        assertEquals(2, cancel.cancelled)
        assertEquals(4, cancel.libraryId)
    }
}
```

- [ ] **Step 2: Run test to verify it fails** (command + expected failure)

```bash
cd /Users/dev/projects/silo/prairie-android && ./gradlew :shared:testDebugUnitTest --tests "com.continuum.app.model.admin.AdminModelsSerializationTest"
```

Expected: compilation failure — `unresolved reference: AdminStats / AdminUser / CreateUserRequest / AdminSession / AdminLogPage / AdminAuditPage / ScanRequest / ScanResponse / ScanCancelResponse` (the model file does not yet exist).

- [ ] **Step 3: Implementation** (complete code)

```kotlin
// shared/src/commonMain/kotlin/com/continuum/app/model/admin/AdminModels.kt
package com.continuum.app.model.admin

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

// ---------------------------------------------------------------------------
// Stats — GET /api/v1/admin/stats[?refresh=true]
// (prairie-server internal/api/handlers/admin_stats.go: AdminStats / WatchProviderActivity)
// ---------------------------------------------------------------------------

@Serializable
data class AdminStats(
    @SerialName("total_items") val totalItems: Int = 0,
    @SerialName("total_files") val totalFiles: Int = 0,
    @SerialName("total_users") val totalUsers: Int = 0,
    @SerialName("total_movies") val totalMovies: Int = 0,
    @SerialName("total_movie_files") val totalMovieFiles: Int = 0,
    @SerialName("total_shows") val totalShows: Int = 0,
    @SerialName("total_show_files") val totalShowFiles: Int = 0,
    @SerialName("active_streams") val activeStreams: Int = 0,
    @SerialName("total_storage_bytes") val totalStorageBytes: Long = 0,
    @SerialName("watch_provider_activity") val watchProviderActivity: WatchProviderActivity = WatchProviderActivity(),
)

@Serializable
data class WatchProviderActivity(
    @SerialName("trakt_connected_profiles") val traktConnectedProfiles: Long = 0,
    @SerialName("trakt_enabled_profiles") val traktEnabledProfiles: Long = 0,
    @SerialName("trakt_export_enabled") val traktExportEnabled: Long = 0,
    @SerialName("trakt_scrobble_enabled") val traktScrobbleEnabled: Long = 0,
    @SerialName("last_sync_completed_at") val lastSyncCompletedAt: String? = null,
    @SerialName("sync_runs_24h") val syncRuns24h: Long = 0,
    @SerialName("sync_errors_24h") val syncErrors24h: Long = 0,
    @SerialName("imported_watched_24h") val importedWatched24h: Long = 0,
    @SerialName("imported_progress_24h") val importedProgress24h: Long = 0,
    @SerialName("exported_watched_24h") val exportedWatched24h: Long = 0,
    @SerialName("pending_exports") val pendingExports: Long = 0,
    @SerialName("failed_exports") val failedExports: Long = 0,
    @SerialName("open_scrobbles") val openScrobbles: Long = 0,
    @SerialName("scrobbles_24h") val scrobbles24h: Long = 0,
)

// ---------------------------------------------------------------------------
// Users — GET/POST /admin/users, GET/PUT/DELETE /admin/users/{id}
// (admin.go: adminUserResponse / createUserRequest / updateUserRequest)
// The list endpoint returns a bare JSON array of AdminUser.
// ---------------------------------------------------------------------------

@Serializable
data class AdminUser(
    val id: Int,
    val username: String,
    val email: String,
    val role: String,
    val permissions: List<String> = emptyList(),
    val enabled: Boolean = true,
    @SerialName("library_ids") val libraryIds: List<Int> = emptyList(),
    @SerialName("max_playback_quality") val maxPlaybackQuality: String = "",
    @SerialName("max_streams") val maxStreams: Int = 0,
    @SerialName("max_transcodes") val maxTranscodes: Int = 0,
    @SerialName("max_profiles") val maxProfiles: Int = 0,
    @SerialName("download_allowed") val downloadAllowed: Boolean = false,
    @SerialName("download_transcode_allowed") val downloadTranscodeAllowed: Boolean = false,
    @SerialName("created_at") val createdAt: String = "",
    @SerialName("updated_at") val updatedAt: String = "",
    @SerialName("last_active_at") val lastActiveAt: String? = null,
)

/**
 * POST /admin/users body. Required: username/email/password/role. The server
 * treats `permissions` and `library_ids` as present-when-sent; optional caps
 * (`max_streams` etc.) and `download_*` are pointer fields server-side, so we
 * leave them nullable and rely on explicitNulls=false to omit them.
 */
@Serializable
data class CreateUserRequest(
    val username: String,
    val email: String,
    val password: String,
    val role: String,
    val permissions: List<String> = emptyList(),
    @SerialName("create_default_profile") val createDefaultProfile: Boolean = false,
    @SerialName("default_profile_name") val defaultProfileName: String? = null,
    @SerialName("library_ids") val libraryIds: List<Int> = emptyList(),
    @SerialName("max_playback_quality") val maxPlaybackQuality: String = "",
    @SerialName("max_streams") val maxStreams: Int? = null,
    @SerialName("max_transcodes") val maxTranscodes: Int? = null,
    @SerialName("max_profiles") val maxProfiles: Int? = null,
    @SerialName("download_allowed") val downloadAllowed: Boolean? = null,
    @SerialName("download_transcode_allowed") val downloadTranscodeAllowed: Boolean? = null,
)

/**
 * PUT /admin/users/{id} — fully partial; every field is optional. With
 * explicitNulls=false, unset (null) fields are omitted from the body, matching
 * the server's "omitted key keeps current value" pointer semantics.
 */
@Serializable
data class UpdateUserRequest(
    val username: String? = null,
    val email: String? = null,
    val password: String? = null,
    val role: String? = null,
    val permissions: List<String>? = null,
    val enabled: Boolean? = null,
    @SerialName("library_ids") val libraryIds: List<Int>? = null,
    @SerialName("max_playback_quality") val maxPlaybackQuality: String? = null,
    @SerialName("max_streams") val maxStreams: Int? = null,
    @SerialName("max_transcodes") val maxTranscodes: Int? = null,
    @SerialName("max_profiles") val maxProfiles: Int? = null,
    @SerialName("download_allowed") val downloadAllowed: Boolean? = null,
    @SerialName("download_transcode_allowed") val downloadTranscodeAllowed: Boolean? = null,
)

// ---------------------------------------------------------------------------
// Sessions — GET /admin/sessions returns a bare JSON array of AdminSession.
// (playback_sessions.go: playbackSessionRow — note `transcode_node_url` is
// json:"-" on the server and intentionally NOT modeled here.)
// ---------------------------------------------------------------------------

@Serializable
data class AdminSession(
    @SerialName("session_id") val sessionId: String,
    @SerialName("user_id") val userId: Int,
    val username: String,
    @SerialName("profile_id") val profileId: String,
    @SerialName("profile_name") val profileName: String = "",
    @SerialName("media_file_id") val mediaFileId: Int,
    @SerialName("requested_media_file_id") val requestedMediaFileId: Int,
    @SerialName("content_id") val contentId: String = "",
    @SerialName("media_title") val mediaTitle: String,
    @SerialName("media_type") val mediaType: String,
    @SerialName("series_name") val seriesName: String = "",
    @SerialName("episode_name") val episodeName: String = "",
    @SerialName("season_number") val seasonNumber: Int? = null,
    @SerialName("episode_number") val episodeNumber: Int? = null,
    @SerialName("poster_url") val posterUrl: String = "",
    @SerialName("play_method") val playMethod: String,
    @SerialName("reporting_node") val reportingNode: String,
    @SerialName("node_display_name") val nodeDisplayName: String = "",
    @SerialName("file_duration") val fileDuration: Int? = null,
    @SerialName("started_at") val startedAt: String,
    @SerialName("updated_at") val updatedAt: String,
    @SerialName("position_seconds") val positionSeconds: Double = 0.0,
    @SerialName("is_paused") val isPaused: Boolean = false,
    @SerialName("has_playback_control") val hasPlaybackControl: Boolean = false,
    @SerialName("client_ip") val clientIp: String = "",
    @SerialName("audio_track_index") val audioTrackIndex: Int = 0,
    @SerialName("transcode_audio") val transcodeAudio: Boolean = false,
    @SerialName("stream_bitrate_kbps") val streamBitrateKbps: Int? = null,
    @SerialName("target_resolution") val targetResolution: String = "",
    @SerialName("target_video_codec") val targetVideoCodec: String = "",
    @SerialName("target_audio_codec") val targetAudioCodec: String = "",
    @SerialName("target_bitrate_kbps") val targetBitrateKbps: Int? = null,
    @SerialName("transcode_hw_accel") val transcodeHwAccel: String = "",
    @SerialName("source_container") val sourceContainer: String = "",
    @SerialName("source_bitrate_kbps") val sourceBitrateKbps: Int? = null,
    @SerialName("source_video_codec") val sourceVideoCodec: String = "",
    @SerialName("source_video_resolution") val sourceVideoResolution: String = "",
    @SerialName("source_audio_codec") val sourceAudioCodec: String = "",
    @SerialName("source_audio_channels") val sourceAudioChannels: Int? = null,
    @SerialName("source_audio_language") val sourceAudioLanguage: String = "",
    @SerialName("source_audio_title") val sourceAudioTitle: String = "",
    @SerialName("source_audio_layout") val sourceAudioLayout: String = "",
    @SerialName("requested_video_codec") val requestedVideoCodec: String = "",
    @SerialName("requested_video_resolution") val requestedVideoResolution: String = "",
    @SerialName("video_decision") val videoDecision: String = "",
    @SerialName("audio_decision") val audioDecision: String = "",
)

/**
 * Session control body for POST /admin/sessions/{id}/{pause|resume|stop|terminate|message}.
 * (admin_playback_control.go: playbackControlRequest — `message`/`title`
 * required only for the message action.) All fields optional here; callers
 * populate only what the chosen action needs.
 */
@Serializable
data class SessionControlRequest(
    val reason: String? = null,
    val title: String? = null,
    val message: String? = null,
    @SerialName("deadline_ms") val deadlineMs: Int? = null,
)

/** Response from a session control action (admin_playback_control.go: playbackControlResponse). */
@Serializable
data class SessionControlResponse(
    @SerialName("command_id") val commandId: String,
    val status: String,
)

/** Known session control actions (URL path segment). */
enum class SessionControlAction(val wire: String) {
    Pause("pause"),
    Resume("resume"),
    Stop("stop"),
    Terminate("terminate"),
    Message("message"),
}

// ---------------------------------------------------------------------------
// Logs — GET /admin/logs/app and /admin/logs/audit
// (opslog.EntryRow / activitylog.AuditEntry; both pages: {entries, next_cursor?})
// ---------------------------------------------------------------------------

@Serializable
data class AdminLogEntry(
    val id: Long,
    val timestamp: String,
    val level: String,
    val component: String,
    val message: String,
    @SerialName("request_id") val requestId: String? = null,
    @SerialName("user_id") val userId: Int? = null,
    @SerialName("session_id") val sessionId: String? = null,
    @SerialName("playback_session_id") val playbackSessionId: String? = null,
    @SerialName("client_ip") val clientIp: String? = null,
    @SerialName("node_id") val nodeId: String? = null,
    val attrs: Map<String, JsonElement>? = null,
)

@Serializable
data class AdminAuditEntry(
    val id: Long,
    val timestamp: String,
    @SerialName("client_ip") val clientIp: String,
    @SerialName("user_id") val userId: Int? = null,
    @SerialName("impersonator_user_id") val impersonatorUserId: Int? = null,
    @SerialName("session_id") val sessionId: String? = null,
    @SerialName("playback_session_id") val playbackSessionId: String? = null,
    @SerialName("request_id") val requestId: String? = null,
    @SerialName("node_id") val nodeId: String? = null,
    val method: String,
    val path: String,
    @SerialName("path_pattern") val pathPattern: String? = null,
    @SerialName("status_code") val statusCode: Int,
    @SerialName("user_agent") val userAgent: String? = null,
    @SerialName("duration_ms") val durationMs: Int = 0,
)

/** App log page (opslog.ListResult). */
@Serializable
data class AdminLogPage(
    val entries: List<AdminLogEntry> = emptyList(),
    @SerialName("next_cursor") val nextCursor: String? = null,
)

/** Audit log page (activitylog.ListResult). */
@Serializable
data class AdminAuditPage(
    val entries: List<AdminAuditEntry> = emptyList(),
    @SerialName("next_cursor") val nextCursor: String? = null,
)

// ---------------------------------------------------------------------------
// Scans — POST /libraries/scan and /libraries/scan/cancel
// (libraries.go: scanRequest / scanResponse / scanCancelRequest / scanCancelResponse)
// NOTE: these live under /libraries, NOT /admin — see AdminApi for placement.
// ---------------------------------------------------------------------------

@Serializable
data class ScanRequest(
    @SerialName("library_id") val libraryId: Int? = null,
    val path: String? = null,
)

@Serializable
data class ScanResponse(
    val status: String,
    val mode: String,
    @SerialName("library_id") val libraryId: Int,
)

@Serializable
data class ScanCancelRequest(
    @SerialName("library_id") val libraryId: Int,
)

@Serializable
data class ScanCancelResponse(
    val cancelled: Int,
    @SerialName("library_id") val libraryId: Int,
)
```

- [ ] **Step 4: Run tests** (command)

```bash
cd /Users/dev/projects/silo/prairie-android && ./gradlew :shared:testDebugUnitTest --tests "com.continuum.app.model.admin.AdminModelsSerializationTest"
```

- [ ] **Step 5: Commit**

```bash
git add shared/src/commonMain/kotlin/com/continuum/app/model/admin/AdminModels.kt \
        shared/src/commonTest/kotlin/com/continuum/app/model/admin/AdminModelsSerializationTest.kt && \
git commit -m "Add shared admin DTOs with serialization tests

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>"
```

---

### Task S3: `network/api/AdminApi.kt` (interface + Default) + NetworkModule + MockEngine tests

**Files:**
- Create: `/Users/dev/projects/silo/prairie-android/shared/src/commonMain/kotlin/com/continuum/app/network/api/AdminApi.kt`
- Modify: `/Users/dev/projects/silo/prairie-android/shared/src/commonMain/kotlin/com/continuum/app/di/NetworkModule.kt`
- Test: `/Users/dev/projects/silo/prairie-android/shared/src/commonTest/kotlin/com/continuum/app/network/api/AdminApiTest.kt`

Evidence (routes, all under `/api/v1`, design §"Server contracts"; verified handlers):
- `GET /admin/stats` with optional `?refresh=true` (admin_stats.go provider; refresh forces a cache bypass server-side).
- `GET /admin/users`, `POST /admin/users`, `GET/PUT/DELETE /admin/users/{id}` (admin.go).
- `GET /admin/sessions` (admin.go:617). Controls: `POST /admin/sessions/{id}/{action}` where action ∈ pause|resume|stop|terminate|message (admin_playback_control.go). The body is optional JSON (`decodeOptionalJSONBody`); message requires `message`.
- Logs: `GET /admin/logs/app`, `GET /admin/logs/audit` with query `level/component/node_id/request_id/session_id/playback_session_id/user_id/from/to/q/cursor/limit` (app) and `method/path_prefix/status_code/client_ip/request_id/session_id/playback_session_id/user_id/from/to/cursor/limit` (audit) (admin_logs.go `parseOperationalLogOptionsFromRequest` / `parseAuditLogOptionsFromRequest`; `limit` capped at 200).
- Scans: `POST /libraries/scan`, `POST /libraries/scan/cancel` (libraries.go). Documented in AdminApi as living under `/libraries`.
- Use `internal safeApiCall` (same package), `parameter(...)` (null-omitting), `client.delete` (already an established import), 204→Unit (safeApiCall handles `T==Unit`).

- [ ] **Step 1: Write the failing test** (full code)

```kotlin
// shared/src/commonTest/kotlin/com/continuum/app/network/api/AdminApiTest.kt
package com.continuum.app.network.api

import com.continuum.app.model.admin.CreateUserRequest
import com.continuum.app.model.admin.ScanCancelRequest
import com.continuum.app.model.admin.ScanRequest
import com.continuum.app.model.admin.SessionControlAction
import com.continuum.app.model.admin.SessionControlRequest
import com.continuum.app.model.admin.UpdateUserRequest
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

class AdminApiTest {

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
    ): Pair<AdminApi, Captured> {
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
        return DefaultAdminApi(client) to captured
    }

    private val statsBody = """
        {"total_items":1,"total_files":1,"total_users":1,"total_movies":1,
         "total_movie_files":1,"total_shows":0,"total_show_files":0,
         "active_streams":0,"total_storage_bytes":10,
         "watch_provider_activity":{"trakt_connected_profiles":1,"scrobbles_24h":2}}
    """.trimIndent()

    @Test
    fun `getStats omits refresh when false`() = runTest {
        val (api, captured) = api(responseBody = statsBody)
        val result = api.getStats(refresh = false)
        assertEquals(HttpMethod.Get, captured.method)
        assertEquals("/api/v1/admin/stats", captured.path)
        assertFalse("refresh" in captured.query.keys)
        assertIs<ApiResult.Success<*>>(result)
        assertEquals(2L, (result as ApiResult.Success).data.watchProviderActivity.scrobbles24h)
    }

    @Test
    fun `getStats passes refresh=true`() = runTest {
        val (api, captured) = api(responseBody = statsBody)
        api.getStats(refresh = true)
        assertEquals("true", captured.query["refresh"])
    }

    @Test
    fun `getUsers hits users path`() = runTest {
        val (api, captured) = api(responseBody = "[]")
        val result = api.getUsers()
        assertEquals(HttpMethod.Get, captured.method)
        assertEquals("/api/v1/admin/users", captured.path)
        assertIs<ApiResult.Success<*>>(result)
    }

    @Test
    fun `getUser hits id path`() = runTest {
        val (api, captured) = api(
            responseBody = """{"id":7,"username":"a","email":"a@x.io","role":"user",
                "permissions":[],"enabled":true,"library_ids":[],"max_playback_quality":"",
                "max_streams":0,"max_transcodes":0,"max_profiles":0,
                "download_allowed":false,"download_transcode_allowed":false,
                "created_at":"t","updated_at":"t"}""",
        )
        val result = api.getUser(7)
        assertEquals("/api/v1/admin/users/7", captured.path)
        assertIs<ApiResult.Success<*>>(result)
        assertEquals(7, (result as ApiResult.Success).data.id)
    }

    @Test
    fun `createUser posts body to users path`() = runTest {
        val (api, captured) = api(
            responseBody = """{"id":9,"username":"bob","email":"b@x.io","role":"user",
                "permissions":[],"enabled":true,"library_ids":[],"max_playback_quality":"",
                "max_streams":0,"max_transcodes":0,"max_profiles":0,
                "download_allowed":false,"download_transcode_allowed":false,
                "created_at":"t","updated_at":"t"}""",
        )
        val result = api.createUser(
            CreateUserRequest(
                username = "bob", email = "b@x.io", password = "pw", role = "user",
                createDefaultProfile = true,
            ),
        )
        assertEquals(HttpMethod.Post, captured.method)
        assertEquals("/api/v1/admin/users", captured.path)
        val sent = ContinuumJson.parseToJsonElement(captured.body).jsonObject
        assertEquals("bob", sent["username"]?.toString()?.trim('"'))
        assertTrue("password" in sent.keys)
        assertTrue("max_streams" !in sent.keys) // null omitted
        assertIs<ApiResult.Success<*>>(result)
    }

    @Test
    fun `updateUser puts partial body to id path`() = runTest {
        val (api, captured) = api(
            responseBody = """{"id":7,"username":"a","email":"a@x.io","role":"user",
                "permissions":[],"enabled":false,"library_ids":[],"max_playback_quality":"",
                "max_streams":4,"max_transcodes":0,"max_profiles":0,
                "download_allowed":false,"download_transcode_allowed":false,
                "created_at":"t","updated_at":"t"}""",
        )
        val result = api.updateUser(7, UpdateUserRequest(enabled = false, maxStreams = 4))
        assertEquals(HttpMethod.Put, captured.method)
        assertEquals("/api/v1/admin/users/7", captured.path)
        val sent = ContinuumJson.parseToJsonElement(captured.body).jsonObject
        assertEquals(setOf("enabled", "max_streams"), sent.keys) // only set fields
        assertIs<ApiResult.Success<*>>(result)
    }

    @Test
    fun `deleteUser deletes id path and maps 204 to Unit`() = runTest {
        val (api, captured) = api(status = HttpStatusCode.NoContent, responseBody = "")
        val result = api.deleteUser(7)
        assertEquals(HttpMethod.Delete, captured.method)
        assertEquals("/api/v1/admin/users/7", captured.path)
        assertEquals(ApiResult.Success(Unit), result)
    }

    @Test
    fun `getSessions hits sessions path`() = runTest {
        val (api, captured) = api(responseBody = "[]")
        val result = api.getSessions()
        assertEquals(HttpMethod.Get, captured.method)
        assertEquals("/api/v1/admin/sessions", captured.path)
        assertIs<ApiResult.Success<*>>(result)
    }

    @Test
    fun `sessionControl posts action path with body and decodes response`() = runTest {
        val (api, captured) = api(
            status = HttpStatusCode.Accepted,
            responseBody = """{"command_id":"cmd-1","status":"dispatched"}""",
        )
        val result = api.sessionControl(
            "sess-9",
            SessionControlAction.Message,
            SessionControlRequest(title = "Heads up", message = "Stopping soon"),
        )
        assertEquals(HttpMethod.Post, captured.method)
        assertEquals("/api/v1/admin/sessions/sess-9/message", captured.path)
        val sent = ContinuumJson.parseToJsonElement(captured.body).jsonObject
        assertEquals("Stopping soon", sent["message"]?.toString()?.trim('"'))
        assertTrue("reason" !in sent.keys) // null omitted
        assertIs<ApiResult.Success<*>>(result)
        assertEquals("cmd-1", (result as ApiResult.Success).data.commandId)
    }

    @Test
    fun `sessionControl pause uses pause segment`() = runTest {
        val (api, captured) = api(
            status = HttpStatusCode.Accepted,
            responseBody = """{"command_id":"c","status":"dispatched"}""",
        )
        api.sessionControl("s1", SessionControlAction.Pause, SessionControlRequest(deadlineMs = 5000))
        assertEquals("/api/v1/admin/sessions/s1/pause", captured.path)
        val sent = ContinuumJson.parseToJsonElement(captured.body).jsonObject
        assertEquals("5000", sent["deadline_ms"]?.toString())
    }

    @Test
    fun `getAppLogs passes filters and cursor and limit, omits nulls`() = runTest {
        val (api, captured) = api(responseBody = """{"entries":[]}""")
        val result = api.getAppLogs(
            level = "error",
            component = "scanner",
            nodeId = null,
            requestId = null,
            sessionId = null,
            playbackSessionId = null,
            userId = 3,
            from = "2026-06-12T00:00:00Z",
            to = null,
            query = "fail",
            cursor = "cur-1",
            limit = 50,
        )
        assertEquals("/api/v1/admin/logs/app", captured.path)
        assertEquals("error", captured.query["level"])
        assertEquals("scanner", captured.query["component"])
        assertEquals("3", captured.query["user_id"])
        assertEquals("2026-06-12T00:00:00Z", captured.query["from"])
        assertEquals("fail", captured.query["q"])
        assertEquals("cur-1", captured.query["cursor"])
        assertEquals("50", captured.query["limit"])
        assertFalse("node_id" in captured.query.keys)
        assertFalse("to" in captured.query.keys)
        assertIs<ApiResult.Success<*>>(result)
    }

    @Test
    fun `getAuditLogs passes audit filters and omits nulls`() = runTest {
        val (api, captured) = api(responseBody = """{"entries":[]}""")
        api.getAuditLogs(
            method = "POST",
            pathPrefix = "/api/v1/admin",
            statusCode = 201,
            clientIp = null,
            requestId = null,
            sessionId = null,
            playbackSessionId = null,
            userId = null,
            from = null,
            to = null,
            cursor = null,
            limit = 100,
        )
        assertEquals("/api/v1/admin/logs/audit", captured.path)
        assertEquals("POST", captured.query["method"])
        assertEquals("/api/v1/admin", captured.query["path_prefix"])
        assertEquals("201", captured.query["status_code"])
        assertEquals("100", captured.query["limit"])
        assertFalse("client_ip" in captured.query.keys)
        assertFalse("cursor" in captured.query.keys)
    }

    @Test
    fun `triggerScan posts to libraries scan with body`() = runTest {
        val (api, captured) = api(
            responseBody = """{"status":"scanning","mode":"incremental","library_id":4}""",
        )
        val result = api.triggerScan(ScanRequest(libraryId = 4))
        assertEquals(HttpMethod.Post, captured.method)
        assertEquals("/api/v1/libraries/scan", captured.path)
        val sent = ContinuumJson.parseToJsonElement(captured.body).jsonObject
        assertEquals("4", sent["library_id"]?.toString())
        assertTrue("path" !in sent.keys)
        assertIs<ApiResult.Success<*>>(result)
        assertEquals(4, (result as ApiResult.Success).data.libraryId)
    }

    @Test
    fun `cancelScan posts to libraries scan cancel with body`() = runTest {
        val (api, captured) = api(responseBody = """{"cancelled":1,"library_id":4}""")
        val result = api.cancelScan(ScanCancelRequest(libraryId = 4))
        assertEquals(HttpMethod.Post, captured.method)
        assertEquals("/api/v1/libraries/scan/cancel", captured.path)
        val sent = ContinuumJson.parseToJsonElement(captured.body).jsonObject
        assertEquals("4", sent["library_id"]?.toString())
        assertIs<ApiResult.Success<*>>(result)
        assertEquals(1, (result as ApiResult.Success).data.cancelled)
    }

    @Test
    fun `server error surfaces as ApiResult Error with message`() = runTest {
        val (api, _) = api(
            status = HttpStatusCode.Forbidden,
            responseBody = """{"error":"forbidden","message":"Admin access required"}""",
        )
        val result = api.getStats(refresh = false)
        assertIs<ApiResult.Error>(result)
        assertEquals(403, result.code)
        assertEquals("Admin access required", result.message)
    }
}
```

- [ ] **Step 2: Run test to verify it fails** (command + expected failure)

```bash
cd /Users/dev/projects/silo/prairie-android && ./gradlew :shared:testDebugUnitTest --tests "com.continuum.app.network.api.AdminApiTest"
```

Expected: compilation failure — `unresolved reference: AdminApi / DefaultAdminApi` (the api file does not yet exist).

- [ ] **Step 3: Implementation** (complete code)

```kotlin
// shared/src/commonMain/kotlin/com/continuum/app/network/api/AdminApi.kt
package com.continuum.app.network.api

import com.continuum.app.model.admin.AdminAuditPage
import com.continuum.app.model.admin.AdminLogPage
import com.continuum.app.model.admin.AdminSession
import com.continuum.app.model.admin.AdminStats
import com.continuum.app.model.admin.AdminUser
import com.continuum.app.model.admin.CreateUserRequest
import com.continuum.app.model.admin.ScanCancelRequest
import com.continuum.app.model.admin.ScanCancelResponse
import com.continuum.app.model.admin.ScanRequest
import com.continuum.app.model.admin.ScanResponse
import com.continuum.app.model.admin.SessionControlAction
import com.continuum.app.model.admin.SessionControlRequest
import com.continuum.app.model.admin.SessionControlResponse
import com.continuum.app.model.admin.UpdateUserRequest
import com.continuum.app.network.ApiResult
import io.ktor.client.HttpClient
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType

/**
 * Core-admin surface (stats, users, sessions + controls, logs, scans). Every
 * route is gated server-side on acting-admin; the UI mirrors that with
 * [com.continuum.app.model.auth.isActingAdmin]. Behind an interface so the
 * repository and its tests can fake the transport (matching
 * NotificationsApi/SubtitlesApi).
 *
 * NOTE: the scan endpoints ([triggerScan]/[cancelScan]) live under
 * `/api/v1/libraries`, NOT `/admin` — they are kept on this interface for
 * cohesion with the admin "Scans" sub-screen, which is the only admin caller.
 */
interface AdminApi {

    /** GET /api/v1/admin/stats[?refresh=true]. */
    suspend fun getStats(refresh: Boolean = false): ApiResult<AdminStats>

    /** GET /api/v1/admin/users — bare array. */
    suspend fun getUsers(): ApiResult<List<AdminUser>>

    /** GET /api/v1/admin/users/{id}. */
    suspend fun getUser(id: Int): ApiResult<AdminUser>

    /** POST /api/v1/admin/users. */
    suspend fun createUser(request: CreateUserRequest): ApiResult<AdminUser>

    /** PUT /api/v1/admin/users/{id} — partial; null fields omitted. */
    suspend fun updateUser(id: Int, request: UpdateUserRequest): ApiResult<AdminUser>

    /** DELETE /api/v1/admin/users/{id} — 204. */
    suspend fun deleteUser(id: Int): ApiResult<Unit>

    /** GET /api/v1/admin/sessions — bare array of active sessions. */
    suspend fun getSessions(): ApiResult<List<AdminSession>>

    /** POST /api/v1/admin/sessions/{id}/{action} — body optional per action. */
    suspend fun sessionControl(
        sessionId: String,
        action: SessionControlAction,
        request: SessionControlRequest = SessionControlRequest(),
    ): ApiResult<SessionControlResponse>

    /** GET /api/v1/admin/logs/app — cursor-paginated; null filters omitted. */
    suspend fun getAppLogs(
        level: String? = null,
        component: String? = null,
        nodeId: String? = null,
        requestId: String? = null,
        sessionId: String? = null,
        playbackSessionId: String? = null,
        userId: Int? = null,
        from: String? = null,
        to: String? = null,
        query: String? = null,
        cursor: String? = null,
        limit: Int = 100,
    ): ApiResult<AdminLogPage>

    /** GET /api/v1/admin/logs/audit — cursor-paginated; null filters omitted. */
    suspend fun getAuditLogs(
        method: String? = null,
        pathPrefix: String? = null,
        statusCode: Int? = null,
        clientIp: String? = null,
        requestId: String? = null,
        sessionId: String? = null,
        playbackSessionId: String? = null,
        userId: Int? = null,
        from: String? = null,
        to: String? = null,
        cursor: String? = null,
        limit: Int = 100,
    ): ApiResult<AdminAuditPage>

    /** POST /api/v1/libraries/scan (NOT /admin). */
    suspend fun triggerScan(request: ScanRequest): ApiResult<ScanResponse>

    /** POST /api/v1/libraries/scan/cancel (NOT /admin). */
    suspend fun cancelScan(request: ScanCancelRequest): ApiResult<ScanCancelResponse>
}

class DefaultAdminApi(private val client: HttpClient) : AdminApi {

    override suspend fun getStats(refresh: Boolean): ApiResult<AdminStats> = safeApiCall {
        client.get("/api/v1/admin/stats") {
            if (refresh) parameter("refresh", "true")
        }
    }

    override suspend fun getUsers(): ApiResult<List<AdminUser>> = safeApiCall {
        client.get("/api/v1/admin/users")
    }

    override suspend fun getUser(id: Int): ApiResult<AdminUser> = safeApiCall {
        client.get("/api/v1/admin/users/$id")
    }

    override suspend fun createUser(request: CreateUserRequest): ApiResult<AdminUser> = safeApiCall {
        client.post("/api/v1/admin/users") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }
    }

    override suspend fun updateUser(id: Int, request: UpdateUserRequest): ApiResult<AdminUser> = safeApiCall {
        client.put("/api/v1/admin/users/$id") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }
    }

    override suspend fun deleteUser(id: Int): ApiResult<Unit> = safeApiCall {
        client.delete("/api/v1/admin/users/$id")
    }

    override suspend fun getSessions(): ApiResult<List<AdminSession>> = safeApiCall {
        client.get("/api/v1/admin/sessions")
    }

    override suspend fun sessionControl(
        sessionId: String,
        action: SessionControlAction,
        request: SessionControlRequest,
    ): ApiResult<SessionControlResponse> = safeApiCall {
        client.post("/api/v1/admin/sessions/$sessionId/${action.wire}") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }
    }

    override suspend fun getAppLogs(
        level: String?,
        component: String?,
        nodeId: String?,
        requestId: String?,
        sessionId: String?,
        playbackSessionId: String?,
        userId: Int?,
        from: String?,
        to: String?,
        query: String?,
        cursor: String?,
        limit: Int,
    ): ApiResult<AdminLogPage> = safeApiCall {
        client.get("/api/v1/admin/logs/app") {
            level?.let { parameter("level", it) }
            component?.let { parameter("component", it) }
            nodeId?.let { parameter("node_id", it) }
            requestId?.let { parameter("request_id", it) }
            sessionId?.let { parameter("session_id", it) }
            playbackSessionId?.let { parameter("playback_session_id", it) }
            userId?.let { parameter("user_id", it) }
            from?.let { parameter("from", it) }
            to?.let { parameter("to", it) }
            query?.let { parameter("q", it) }
            cursor?.let { parameter("cursor", it) }
            parameter("limit", limit)
        }
    }

    override suspend fun getAuditLogs(
        method: String?,
        pathPrefix: String?,
        statusCode: Int?,
        clientIp: String?,
        requestId: String?,
        sessionId: String?,
        playbackSessionId: String?,
        userId: Int?,
        from: String?,
        to: String?,
        cursor: String?,
        limit: Int,
    ): ApiResult<AdminAuditPage> = safeApiCall {
        client.get("/api/v1/admin/logs/audit") {
            method?.let { parameter("method", it) }
            pathPrefix?.let { parameter("path_prefix", it) }
            statusCode?.let { parameter("status_code", it) }
            clientIp?.let { parameter("client_ip", it) }
            requestId?.let { parameter("request_id", it) }
            sessionId?.let { parameter("session_id", it) }
            playbackSessionId?.let { parameter("playback_session_id", it) }
            userId?.let { parameter("user_id", it) }
            from?.let { parameter("from", it) }
            to?.let { parameter("to", it) }
            cursor?.let { parameter("cursor", it) }
            parameter("limit", limit)
        }
    }

    override suspend fun triggerScan(request: ScanRequest): ApiResult<ScanResponse> = safeApiCall {
        client.post("/api/v1/libraries/scan") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }
    }

    override suspend fun cancelScan(request: ScanCancelRequest): ApiResult<ScanCancelResponse> = safeApiCall {
        client.post("/api/v1/libraries/scan/cancel") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }
    }
}
```

Register in NetworkModule (add the line after the NotificationsApi binding):

```kotlin
// In shared/src/commonMain/kotlin/com/continuum/app/di/NetworkModule.kt, inside the module { ... }
    single<AdminApi> { DefaultAdminApi(get()) }
```

(The `import com.continuum.app.network.api.*` wildcard already covers `AdminApi`/`DefaultAdminApi`.)

- [ ] **Step 4: Run tests** (command)

```bash
cd /Users/dev/projects/silo/prairie-android && ./gradlew :shared:testDebugUnitTest --tests "com.continuum.app.network.api.AdminApiTest"
```

- [ ] **Step 5: Commit**

```bash
git add shared/src/commonMain/kotlin/com/continuum/app/network/api/AdminApi.kt \
        shared/src/commonMain/kotlin/com/continuum/app/di/NetworkModule.kt \
        shared/src/commonTest/kotlin/com/continuum/app/network/api/AdminApiTest.kt && \
git commit -m "Add AdminApi with MockEngine tests and DI registration

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>"
```

---

### Task S4: `repository/AdminRepository.kt` — pass-throughs + RepositoryModule + repository test

**Files:**
- Create: `/Users/dev/projects/silo/prairie-android/shared/src/commonMain/kotlin/com/continuum/app/repository/AdminRepository.kt`
- Modify: `/Users/dev/projects/silo/prairie-android/shared/src/commonMain/kotlin/com/continuum/app/di/RepositoryModule.kt`
- Test: `/Users/dev/projects/silo/prairie-android/shared/src/commonTest/kotlin/com/continuum/app/repository/AdminRepositoryTest.kt`

Evidence: existing repos are thin `ApiResult` pass-throughs around an interface-backed API (e.g. `SubtitlesRepository(get())` in RepositoryModule; `SubtitlesApi`/`NotificationsApi` are interfaces faked in tests). `AdminApi` is an interface (Task 3), so the repository is constructed `AdminRepository(get())` and the test fakes `AdminApi`. The scan endpoints live under `/libraries` but are reached via `AdminApi` (documented there); the repository exposes them as `triggerScan`/`cancelScan` so the admin "Scans" sub-screen has a single dependency — KDoc records the `/libraries` placement.

- [ ] **Step 1: Write the failing test** (full code)

```kotlin
// shared/src/commonTest/kotlin/com/continuum/app/repository/AdminRepositoryTest.kt
package com.continuum.app.repository

import com.continuum.app.model.admin.AdminAuditPage
import com.continuum.app.model.admin.AdminLogPage
import com.continuum.app.model.admin.AdminSession
import com.continuum.app.model.admin.AdminStats
import com.continuum.app.model.admin.AdminUser
import com.continuum.app.model.admin.CreateUserRequest
import com.continuum.app.model.admin.ScanCancelRequest
import com.continuum.app.model.admin.ScanCancelResponse
import com.continuum.app.model.admin.ScanRequest
import com.continuum.app.model.admin.ScanResponse
import com.continuum.app.model.admin.SessionControlAction
import com.continuum.app.model.admin.SessionControlRequest
import com.continuum.app.model.admin.SessionControlResponse
import com.continuum.app.model.admin.UpdateUserRequest
import com.continuum.app.network.ApiResult
import com.continuum.app.network.api.AdminApi
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class AdminRepositoryTest {

    /** Records calls and returns canned successes. */
    private class FakeAdminApi : AdminApi {
        val calls = mutableListOf<String>()

        var statsRefresh: Boolean? = null
        var lastSessionControl: Triple<String, SessionControlAction, SessionControlRequest>? = null
        var lastAppLogLimit: Int? = null

        override suspend fun getStats(refresh: Boolean): ApiResult<AdminStats> {
            calls += "getStats"
            statsRefresh = refresh
            return ApiResult.Success(AdminStats(totalUsers = 9))
        }

        override suspend fun getUsers(): ApiResult<List<AdminUser>> {
            calls += "getUsers"
            return ApiResult.Success(emptyList())
        }

        override suspend fun getUser(id: Int): ApiResult<AdminUser> {
            calls += "getUser:$id"
            return ApiResult.Success(
                AdminUser(id = id, username = "u", email = "u@x.io", role = "user"),
            )
        }

        override suspend fun createUser(request: CreateUserRequest): ApiResult<AdminUser> {
            calls += "createUser:${request.username}"
            return ApiResult.Success(
                AdminUser(id = 1, username = request.username, email = request.email, role = request.role),
            )
        }

        override suspend fun updateUser(id: Int, request: UpdateUserRequest): ApiResult<AdminUser> {
            calls += "updateUser:$id"
            return ApiResult.Success(
                AdminUser(id = id, username = "u", email = "u@x.io", role = "user"),
            )
        }

        override suspend fun deleteUser(id: Int): ApiResult<Unit> {
            calls += "deleteUser:$id"
            return ApiResult.Success(Unit)
        }

        override suspend fun getSessions(): ApiResult<List<AdminSession>> {
            calls += "getSessions"
            return ApiResult.Success(emptyList())
        }

        override suspend fun sessionControl(
            sessionId: String,
            action: SessionControlAction,
            request: SessionControlRequest,
        ): ApiResult<SessionControlResponse> {
            calls += "sessionControl:$sessionId:${action.wire}"
            lastSessionControl = Triple(sessionId, action, request)
            return ApiResult.Success(SessionControlResponse(commandId = "c", status = "dispatched"))
        }

        override suspend fun getAppLogs(
            level: String?, component: String?, nodeId: String?, requestId: String?,
            sessionId: String?, playbackSessionId: String?, userId: Int?,
            from: String?, to: String?, query: String?, cursor: String?, limit: Int,
        ): ApiResult<AdminLogPage> {
            calls += "getAppLogs"
            lastAppLogLimit = limit
            return ApiResult.Success(AdminLogPage())
        }

        override suspend fun getAuditLogs(
            method: String?, pathPrefix: String?, statusCode: Int?, clientIp: String?,
            requestId: String?, sessionId: String?, playbackSessionId: String?, userId: Int?,
            from: String?, to: String?, cursor: String?, limit: Int,
        ): ApiResult<AdminAuditPage> {
            calls += "getAuditLogs"
            return ApiResult.Success(AdminAuditPage())
        }

        override suspend fun triggerScan(request: ScanRequest): ApiResult<ScanResponse> {
            calls += "triggerScan:${request.libraryId}"
            return ApiResult.Success(
                ScanResponse(status = "scanning", mode = "incremental", libraryId = request.libraryId ?: -1),
            )
        }

        override suspend fun cancelScan(request: ScanCancelRequest): ApiResult<ScanCancelResponse> {
            calls += "cancelScan:${request.libraryId}"
            return ApiResult.Success(ScanCancelResponse(cancelled = 1, libraryId = request.libraryId))
        }
    }

    @Test
    fun `getStats passes refresh through and returns api result`() = runTest {
        val api = FakeAdminApi()
        val repo = AdminRepository(api)

        val result = repo.getStats(refresh = true)

        assertEquals(listOf("getStats"), api.calls)
        assertEquals(true, api.statsRefresh)
        assertIs<ApiResult.Success<*>>(result)
        assertEquals(9, (result as ApiResult.Success).data.totalUsers)
    }

    @Test
    fun `user CRUD pass-throughs delegate to api`() = runTest {
        val api = FakeAdminApi()
        val repo = AdminRepository(api)

        repo.getUsers()
        repo.getUser(7)
        repo.createUser(CreateUserRequest("bob", "b@x.io", "pw", "user"))
        repo.updateUser(7, UpdateUserRequest(enabled = false))
        repo.deleteUser(7)

        assertEquals(
            listOf("getUsers", "getUser:7", "createUser:bob", "updateUser:7", "deleteUser:7"),
            api.calls,
        )
    }

    @Test
    fun `sessions and control pass-throughs delegate to api`() = runTest {
        val api = FakeAdminApi()
        val repo = AdminRepository(api)

        repo.getSessions()
        val result = repo.sessionControl(
            "sess-1", SessionControlAction.Stop, SessionControlRequest(reason = "policy"),
        )

        assertEquals(listOf("getSessions", "sessionControl:sess-1:stop"), api.calls)
        assertEquals("sess-1", api.lastSessionControl?.first)
        assertEquals(SessionControlAction.Stop, api.lastSessionControl?.second)
        assertIs<ApiResult.Success<*>>(result)
    }

    @Test
    fun `log pass-throughs forward limit`() = runTest {
        val api = FakeAdminApi()
        val repo = AdminRepository(api)

        repo.getAppLogs(level = "error", limit = 25)
        repo.getAuditLogs(method = "POST")

        assertEquals(listOf("getAppLogs", "getAuditLogs"), api.calls)
        assertEquals(25, api.lastAppLogLimit)
    }

    @Test
    fun `scan pass-throughs delegate to api`() = runTest {
        val api = FakeAdminApi()
        val repo = AdminRepository(api)

        val scan = repo.triggerScan(ScanRequest(libraryId = 4))
        val cancel = repo.cancelScan(ScanCancelRequest(libraryId = 4))

        assertEquals(listOf("triggerScan:4", "cancelScan:4"), api.calls)
        assertIs<ApiResult.Success<*>>(scan)
        assertEquals(4, (scan as ApiResult.Success).data.libraryId)
        assertIs<ApiResult.Success<*>>(cancel)
        assertEquals(1, (cancel as ApiResult.Success).data.cancelled)
    }
}
```

- [ ] **Step 2: Run test to verify it fails** (command + expected failure)

```bash
cd /Users/dev/projects/silo/prairie-android && ./gradlew :shared:testDebugUnitTest --tests "com.continuum.app.repository.AdminRepositoryTest"
```

Expected: compilation failure — `unresolved reference: AdminRepository` (the repository does not yet exist).

- [ ] **Step 3: Implementation** (complete code)

```kotlin
// shared/src/commonMain/kotlin/com/continuum/app/repository/AdminRepository.kt
package com.continuum.app.repository

import com.continuum.app.model.admin.AdminAuditPage
import com.continuum.app.model.admin.AdminLogPage
import com.continuum.app.model.admin.AdminSession
import com.continuum.app.model.admin.AdminStats
import com.continuum.app.model.admin.AdminUser
import com.continuum.app.model.admin.CreateUserRequest
import com.continuum.app.model.admin.ScanCancelRequest
import com.continuum.app.model.admin.ScanCancelResponse
import com.continuum.app.model.admin.ScanRequest
import com.continuum.app.model.admin.ScanResponse
import com.continuum.app.model.admin.SessionControlAction
import com.continuum.app.model.admin.SessionControlRequest
import com.continuum.app.model.admin.SessionControlResponse
import com.continuum.app.model.admin.UpdateUserRequest
import com.continuum.app.network.ApiResult
import com.continuum.app.network.api.AdminApi

/**
 * Thin pass-through over [AdminApi] for the mobile admin sub-screens and the
 * TV stats dashboard. Stateless (no cached flows): each admin screen owns its
 * ViewModel state and refreshes via pull-to-refresh, so the repository just
 * surfaces the typed [ApiResult] from the transport.
 *
 * Every admin route is gated server-side on acting-admin; the UI gates entry
 * with [com.continuum.app.model.auth.isActingAdmin].
 *
 * NOTE: [triggerScan]/[cancelScan] hit `/api/v1/libraries/scan[/cancel]`, NOT
 * `/admin/*` (the scan endpoints live on the libraries handler server-side).
 * They are exposed here so the admin "Scans" sub-screen has a single
 * repository dependency.
 */
class AdminRepository(private val api: AdminApi) {

    suspend fun getStats(refresh: Boolean = false): ApiResult<AdminStats> =
        api.getStats(refresh)

    suspend fun getUsers(): ApiResult<List<AdminUser>> = api.getUsers()

    suspend fun getUser(id: Int): ApiResult<AdminUser> = api.getUser(id)

    suspend fun createUser(request: CreateUserRequest): ApiResult<AdminUser> =
        api.createUser(request)

    suspend fun updateUser(id: Int, request: UpdateUserRequest): ApiResult<AdminUser> =
        api.updateUser(id, request)

    suspend fun deleteUser(id: Int): ApiResult<Unit> = api.deleteUser(id)

    suspend fun getSessions(): ApiResult<List<AdminSession>> = api.getSessions()

    suspend fun sessionControl(
        sessionId: String,
        action: SessionControlAction,
        request: SessionControlRequest = SessionControlRequest(),
    ): ApiResult<SessionControlResponse> = api.sessionControl(sessionId, action, request)

    suspend fun getAppLogs(
        level: String? = null,
        component: String? = null,
        nodeId: String? = null,
        requestId: String? = null,
        sessionId: String? = null,
        playbackSessionId: String? = null,
        userId: Int? = null,
        from: String? = null,
        to: String? = null,
        query: String? = null,
        cursor: String? = null,
        limit: Int = 100,
    ): ApiResult<AdminLogPage> = api.getAppLogs(
        level, component, nodeId, requestId, sessionId, playbackSessionId,
        userId, from, to, query, cursor, limit,
    )

    suspend fun getAuditLogs(
        method: String? = null,
        pathPrefix: String? = null,
        statusCode: Int? = null,
        clientIp: String? = null,
        requestId: String? = null,
        sessionId: String? = null,
        playbackSessionId: String? = null,
        userId: Int? = null,
        from: String? = null,
        to: String? = null,
        cursor: String? = null,
        limit: Int = 100,
    ): ApiResult<AdminAuditPage> = api.getAuditLogs(
        method, pathPrefix, statusCode, clientIp, requestId, sessionId,
        playbackSessionId, userId, from, to, cursor, limit,
    )

    /** POST /api/v1/libraries/scan (not /admin) — see class KDoc. */
    suspend fun triggerScan(request: ScanRequest): ApiResult<ScanResponse> =
        api.triggerScan(request)

    /** POST /api/v1/libraries/scan/cancel (not /admin) — see class KDoc. */
    suspend fun cancelScan(request: ScanCancelRequest): ApiResult<ScanCancelResponse> =
        api.cancelScan(request)
}
```

Register in RepositoryModule (add after the `SubtitlesRepository` binding, with the import):

```kotlin
// In shared/src/commonMain/kotlin/com/continuum/app/di/RepositoryModule.kt:
// add import near the other repository imports
import com.continuum.app.repository.AdminRepository
// ...
// inside repositoryModule = module { ... }, after `single { SubtitlesRepository(get()) }`:
    single { AdminRepository(get()) }
```

- [ ] **Step 4: Run tests** (command)

```bash
cd /Users/dev/projects/silo/prairie-android && ./gradlew :shared:testDebugUnitTest --tests "com.continuum.app.repository.AdminRepositoryTest"
```

- [ ] **Step 5: Commit**

```bash
git add shared/src/commonMain/kotlin/com/continuum/app/repository/AdminRepository.kt \
        shared/src/commonMain/kotlin/com/continuum/app/di/RepositoryModule.kt \
        shared/src/commonTest/kotlin/com/continuum/app/repository/AdminRepositoryTest.kt && \
git commit -m "Add AdminRepository pass-throughs with DI and tests

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>"
```

## Section H: Mobile hub, stats, users + TV stats

I have everything. The TV app has no shared `formatBytes` util, so the recovered TvAdminScreen's private `formatBytes` is the right approach there. The mobile `formatBytes` in `ui/util/Formatters.kt` is `internal` and reachable within androidApp.

Now I'll produce the four plan tasks.

### Task H1: Admin gating + entry + hub + route

**Files:**
- Create `androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/admin/AdminHubScreen.kt`
- Create `androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/admin/AdminEntryViewModel.kt`
- Create `androidApp/src/androidUnitTest/kotlin/com/continuum/app/android/ui/screens/admin/AdminEntryViewModelTest.kt`
- Modify `androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/navigation/Routes.kt`
- Modify `androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/navigation/AppNavigation.kt`
- Modify `androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/settings/SettingsScreen.kt`
- Modify `androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/settings/AccountSection.kt`
- Modify `androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/settings/SettingsViewModel.kt`
- Modify `androidApp/src/androidMain/kotlin/com/continuum/app/android/di/AndroidModule.kt`

**Dependencies (assumed landed from shared layer):** `com.continuum.app.util.isActingAdmin(user: User?, profile: Profile?): Boolean`; `Profile.isPrimary: Boolean` (`@SerialName("is_primary")`); `com.continuum.app.repository.AdminRepository` with `getStats/getUsers/createUser/updateUser/deleteUser`; `com.continuum.app.model.admin.*` (`AdminStats`, `AdminUser`, `CreateUserRequest`, `UpdateUserRequest`). Executor verifies signatures against landed code before writing.

**Sequencing decision (reported):** Sessions/Logs/Scans screens land in the sibling plan. To keep this task self-contained and compiling, the hub renders all five sub-section rows but only wires nav callbacks for the three rows owned by *this* plan (Dashboard → Task 2, Users → Task 3). The Sessions/Logs/Scans rows are rendered with `enabled = false` placeholder callbacks (no new routes registered for them here). The sibling plan flips them on by passing real callbacks and registering routes. This avoids tiny throwaway placeholder screens entirely — the hub is a single screen taking per-row callbacks, so no dead routes need overwriting.

- [ ] **Step 1: Write the failing test** — `AdminEntryViewModelTest.kt`. The gate decision (should the Settings "Admin" entry show?) is the pure bit. Test the VM that folds user+active-profile into `isAdminVisible`.

```kotlin
package com.continuum.app.android.ui.screens.admin

import com.continuum.app.model.auth.User
import com.continuum.app.model.profile.Profile
import com.continuum.app.network.ApiResult
import com.continuum.app.repository.AuthRepository
import com.continuum.app.repository.ProfileRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class AdminEntryViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()

    @BeforeTest fun setUp() { Dispatchers.setMain(dispatcher) }
    @AfterTest fun tearDown() { Dispatchers.resetMain() }

    private fun user(role: String) = User(id = 1, username = "u", email = "e@x.io", role = role)
    private fun profile(primary: Boolean) =
        Profile(id = "p1", name = "Primary", isPrimary = primary)

    private fun vm(user: User?, profile: Profile?) = AdminEntryViewModel(
        authRepository = FakeAuthRepo(user),
        profileRepository = FakeProfileRepo(profile),
    )

    @Test fun `admin role with primary profile is visible`() = runTest(dispatcher) {
        assertTrue(vm(user("admin"), profile(true)).uiState.value.isAdminVisible)
    }

    @Test fun `admin role with non-primary profile is hidden`() = runTest(dispatcher) {
        assertFalse(vm(user("admin"), profile(false)).uiState.value.isAdminVisible)
    }

    @Test fun `non-admin role is hidden even on primary profile`() = runTest(dispatcher) {
        assertFalse(vm(user("user"), profile(true)).uiState.value.isAdminVisible)
    }

    @Test fun `admin with no active profile is visible`() = runTest(dispatcher) {
        assertTrue(vm(user("admin"), null).uiState.value.isAdminVisible)
    }

    @Test fun `null user is hidden`() = runTest(dispatcher) {
        assertFalse(vm(null, profile(true)).uiState.value.isAdminVisible)
    }
}

private class FakeAuthRepo(private val user: User?) : AuthRepository(/* stub deps per landed ctor */) {
    override suspend fun getCurrentUser(): ApiResult<User> =
        user?.let { ApiResult.Success(it) } ?: ApiResult.Error(code = 401, error = "unauth", message = "")
}

private class FakeProfileRepo(private val profile: Profile?) : ProfileRepository(/* stub deps per landed ctor */) {
    override suspend fun getActiveProfile(): Profile? = profile
}
```

> Note for executor: `AuthRepository`/`ProfileRepository` constructors take real deps — if they cannot be cheaply faked via `open`/override, instead inject a tiny `gateProvider: suspend () -> Boolean` into `AdminEntryViewModel` and test that, keeping `isActingAdmin(user, profile)` (shared, already unit-tested in the shared plan) as the production wiring. Prefer overriding `open` methods if the repos already expose them (they do: `getActiveProfile` is `open suspend`; verify `getCurrentUser`).

- [ ] **Step 2: Run test to verify it fails** — `./gradlew :androidApp:testDebugUnitTest --tests "*AdminEntryViewModelTest*"` (fails: `AdminEntryViewModel` does not exist).

- [ ] **Step 3: Implementation**

`AdminEntryViewModel.kt`:
```kotlin
package com.continuum.app.android.ui.screens.admin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.continuum.app.model.auth.User
import com.continuum.app.network.ApiResult
import com.continuum.app.repository.AuthRepository
import com.continuum.app.repository.ProfileRepository
import com.continuum.app.util.isActingAdmin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Resolves whether the acting user may see admin surfaces. The Settings
 * "Admin" entry and the hub itself both gate on [AdminUiState.isAdminVisible],
 * mirroring the server's RequireActingAdmin (role == "admin" && profile primary).
 */
class AdminEntryViewModel(
    private val authRepository: AuthRepository,
    private val profileRepository: ProfileRepository,
) : ViewModel() {

    data class AdminUiState(
        val isLoading: Boolean = true,
        val isAdminVisible: Boolean = false,
        val user: User? = null,
    )

    private val _uiState = MutableStateFlow(AdminUiState())
    val uiState: StateFlow<AdminUiState> = _uiState.asStateFlow()

    init { refresh() }

    fun refresh() {
        viewModelScope.launch {
            val user = (authRepository.getCurrentUser() as? ApiResult.Success)?.data
            val profile = profileRepository.getActiveProfile()
            _uiState.update {
                it.copy(
                    isLoading = false,
                    user = user,
                    isAdminVisible = isActingAdmin(user, profile),
                )
            }
        }
    }
}
```

`AdminHubScreen.kt` — lists the five sub-sections; Dashboard/Users are live, Sessions/Logs/Scans are disabled rows owned by the sibling plan (callbacks default to no-op + `enabled=false`). Re-gates defensively via `AdminEntryViewModel`.
```kotlin
package com.continuum.app.android.ui.screens.admin

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Article
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.continuum.app.android.ui.components.ContinuumTopBar
import com.continuum.app.android.ui.components.LoadingIndicator
import com.continuum.app.android.ui.screens.settings.SettingsSectionCard
import org.koin.compose.viewmodel.koinViewModel

/**
 * Admin hub. Lists the admin sub-sections. Dashboard + Users are wired in this
 * plan; Sessions/Logs/Scans render disabled until the sibling plan supplies
 * their callbacks. Re-checks the acting-admin gate as defense in depth.
 */
@Composable
fun AdminHubScreen(
    onBackClick: () -> Unit,
    onOpenDashboard: () -> Unit,
    onOpenUsers: () -> Unit,
    onOpenSessions: (() -> Unit)? = null,
    onOpenLogs: (() -> Unit)? = null,
    onOpenScans: (() -> Unit)? = null,
    viewModel: AdminEntryViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = { ContinuumTopBar(title = "Admin", onBackClick = onBackClick) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        when {
            state.isLoading -> LoadingIndicator(modifier = Modifier.padding(padding))
            !state.isAdminVisible -> NotAuthorized(modifier = Modifier.padding(padding))
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    SettingsSectionCard {
                        HubRow(Icons.Default.Dashboard, "Dashboard", "Server stats & activity", onOpenDashboard)
                        HubRow(Icons.Default.People, "Users", "Manage accounts & access", onOpenUsers)
                        HubRow(Icons.Default.PlayCircle, "Sessions", "Now playing & controls", onOpenSessions, enabled = onOpenSessions != null)
                        HubRow(Icons.Default.Article, "Logs", "App & audit logs", onOpenLogs, enabled = onOpenLogs != null)
                        HubRow(Icons.Default.Sync, "Scans", "Library scans", onOpenScans, enabled = onOpenScans != null)
                    }
                }
            }
        }
    }
}

@Composable
private fun HubRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: (() -> Unit)?,
    enabled: Boolean = true,
) {
    val tint = if (enabled) MaterialTheme.colorScheme.onSurface
    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (enabled && onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(14.dp))
        androidx.compose.foundation.layout.Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = tint)
            Text(
                subtitle, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (enabled) {
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Text("Soon", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun NotAuthorized(modifier: Modifier = Modifier) {
    androidx.compose.foundation.layout.Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("You are not authorized to view this page.", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
```

`Routes.kt` — add under the Notifications/Personal section:
```kotlin
    // --- Admin (acting-admin gated) ---
    data object Admin : Route("admin")
    data object AdminStats : Route("admin/stats")
    data object AdminUsers : Route("admin/users")
```

`AppNavigation.kt` — add imports and a composable block. Settings already forwards callbacks; add `onNavigateToAdmin`:
```kotlin
import com.continuum.app.android.ui.screens.admin.AdminHubScreen
```
In the `Route.Settings` composable, add to the `SettingsScreen(...)` call:
```kotlin
                onNavigateToAdmin = { navController.navigate(Route.Admin.route) },
```
Add a new destination block:
```kotlin
        composable(Route.Admin.route) {
            AdminHubScreen(
                onBackClick = { navController.popBackStack() },
                onOpenDashboard = { navController.navigate(Route.AdminStats.route) },
                onOpenUsers = { navController.navigate(Route.AdminUsers.route) },
                // Sessions/Logs/Scans wired by the sibling plan.
            )
        }
```

`SettingsViewModel.kt` — fold the gate into existing state. Add `profileRepository` is already injected. Add field + load in `loadUserInfo()`:
```kotlin
// in SettingsUiState:
    val isAdminVisible: Boolean = false,
```
Add import `import com.continuum.app.util.isActingAdmin`. In `loadUserInfo()`, after the active-profile fetch, compute the gate (reuse the user already in state + the profile fetched there):
```kotlin
            val user = _uiState.value.user
            val activeProfile = profileRepository.getActiveProfile()
            _uiState.update { it.copy(isAdminVisible = isActingAdmin(user, activeProfile)) }
```
> Note: `getCurrentUser()` is the same call already used; `getActiveProfile()` returns the cached active profile. Place this after the user is stored so `_uiState.value.user` is populated.

`SettingsScreen.kt` — add param + pass to `AccountSection`:
```kotlin
    onNavigateToAdmin: () -> Unit = {},
```
Update the `AccountSection(...)` call:
```kotlin
                AccountSection(
                    user = state.user,
                    isLoadingUser = state.isLoadingUser,
                    isAdminVisible = state.isAdminVisible,
                    onManageSessions = viewModel::loadSessions,
                    onPairDevice = onPairDevice,
                    onRequests = onNavigateToRequests,
                    onAdmin = onNavigateToAdmin,
                    onSignOut = viewModel::logout,
                )
```

`AccountSection.kt` — add `isAdminVisible: Boolean` + `onAdmin: () -> Unit` params and render the row only when visible (placed above Sign Out):
```kotlin
import androidx.compose.material.icons.filled.AdminPanelSettings
...
            if (isAdminVisible) {
                SettingsClickableRow(
                    icon = Icons.Default.AdminPanelSettings,
                    label = "Admin",
                    onClick = onAdmin,
                )
            }
```

`AndroidModule.kt` — register the VM (imports + line near other admin/settings VMs):
```kotlin
import com.continuum.app.android.ui.screens.admin.AdminEntryViewModel
...
    viewModel { AdminEntryViewModel(get(), get()) }
```
> `SettingsViewModel` ctor already receives `profileRepository` (5th `get()`); no signature change there.

- [ ] **Step 4: Run tests** — `./gradlew :androidApp:compileDebugKotlinAndroid :androidApp:testDebugUnitTest :androidApp:assembleDebug`.
  Manual checklist:
  - Sign in as admin on primary profile → Settings shows an "Admin" row under the account card.
  - Tap Admin → hub lists Dashboard, Users (tappable), Sessions/Logs/Scans (greyed, "Soon").
  - Dashboard/Users navigate to their (Task 2/3) routes.
  - Sign in as non-admin (or admin on a non-primary profile) → no Admin row; deep-linking to `admin` shows the "not authorized" state, no crash.

- [ ] **Step 5: Commit** — `git add -A && git commit` with message "Add admin gating, Settings entry, and admin hub" (+ Co-Authored-By trailer).

---

### Task H2: AdminStatsScreen (mobile)

**Files:**
- Create `androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/admin/AdminStatsScreen.kt`
- Create `androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/admin/AdminStatsViewModel.kt`
- Create `shared/src/commonTest/kotlin/com/continuum/app/viewmodel/AdminStatsViewModelTest.kt`
- Modify `androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/navigation/AppNavigation.kt`
- Modify `androidApp/src/androidMain/kotlin/com/continuum/app/android/di/AndroidModule.kt`

> **Placement decision:** `AdminStatsViewModel` is pure (no Android deps) and follows the shared-VM pattern (`CalendarViewModel`), so it goes in `shared/src/commonMain/kotlin/com/continuum/app/viewmodel/AdminStatsViewModel.kt` with its test in `shared/commonTest`. Executor: confirm the shared `viewmodel` package is the home for new VMs (it is — Calendar/Requests live there) and place accordingly. (If the team prefers per-screen androidApp VMs, mirror Task 1's androidUnitTest location instead.)

**Dependencies (assumed):** `AdminStats(totalItems, totalFiles, totalUsers, totalMovies, totalMovieFiles, totalShows, totalShowFiles, activeStreams, totalStorageBytes, watchProviderActivity)` and `WatchProviderActivity(traktConnectedProfiles, …, scrobbles24h)`; `AdminRepository.getStats(refresh: Boolean = false): ApiResult<AdminStats>`. Executor verifies exact field names against landed `AdminModels.kt`.

- [ ] **Step 1: Write the failing test** — `AdminStatsViewModelTest.kt` (shared, mirrors `CalendarViewModelTest`):

```kotlin
package com.continuum.app.viewmodel

import com.continuum.app.model.admin.AdminStats
import com.continuum.app.model.admin.WatchProviderActivity
import com.continuum.app.network.ApiResult
import com.continuum.app.network.api.AdminApi
import com.continuum.app.repository.AdminRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

@OptIn(ExperimentalCoroutinesApi::class)
class AdminStatsViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()
    @BeforeTest fun setUp() { Dispatchers.setMain(dispatcher) }
    @AfterTest fun tearDown() { Dispatchers.resetMain() }

    private fun stats() = AdminStats(
        totalItems = 10, totalFiles = 20, totalUsers = 3,
        totalMovies = 4, totalMovieFiles = 4, totalShows = 6, totalShowFiles = 16,
        activeStreams = 2, totalStorageBytes = 1024L * 1024L * 1024L,
        watchProviderActivity = WatchProviderActivity(traktConnectedProfiles = 1, scrobbles24h = 7),
    )

    @Test fun `loads stats on init`() = runTest(dispatcher) {
        val api = FakeAdminApi(ApiResult.Success(stats()))
        val state = AdminStatsViewModel(AdminRepository(api)).uiState.value
        assertFalse(state.isLoading)
        assertNull(state.error)
        assertEquals(2, state.stats?.activeStreams)
        assertEquals(false, api.calls.last()) // initial load: refresh=false
    }

    @Test fun `refresh requests a server recompute`() = runTest(dispatcher) {
        val api = FakeAdminApi(ApiResult.Success(stats()))
        val vm = AdminStatsViewModel(AdminRepository(api))
        vm.refresh()
        assertEquals(true, api.calls.last()) // refresh=true
        assertFalse(vm.uiState.value.isRefreshing)
    }

    @Test fun `error surfaces server message with fallback`() = runTest(dispatcher) {
        val api = FakeAdminApi(ApiResult.Error(code = 500, error = "internal", message = ""))
        assertEquals("Failed to load admin stats", AdminStatsViewModel(AdminRepository(api)).uiState.value.error)
    }

    @Test fun `network failure surfaces standard copy`() = runTest(dispatcher) {
        val api = FakeAdminApi(ApiResult.NetworkError(IllegalStateException("offline")))
        assertEquals("Network error. Check your connection.", AdminStatsViewModel(AdminRepository(api)).uiState.value.error)
    }
}

private class FakeAdminApi(var result: ApiResult<AdminStats>) : AdminApi {
    val calls = mutableListOf<Boolean>()
    override suspend fun getStats(refresh: Boolean): ApiResult<AdminStats> {
        calls += refresh
        return result
    }
    // Other AdminApi members throw NotImplementedError() — not exercised here.
}
```
> Executor: implement the remaining `AdminApi` members in the fake as `error("unused")`, matching the landed interface. If `AdminRepository(api)` doesn't take an `AdminApi` directly, adapt to the landed ctor.

- [ ] **Step 2: Run test to verify it fails** — `./gradlew :shared:testDebugUnitTest --tests "*AdminStatsViewModelTest*"` (fails: VM missing).

- [ ] **Step 3: Implementation**

`shared/.../viewmodel/AdminStatsViewModel.kt`:
```kotlin
package com.continuum.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.continuum.app.model.admin.AdminStats
import com.continuum.app.network.ApiResult
import com.continuum.app.network.errorMessage
import com.continuum.app.repository.AdminRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AdminStatsUiState(
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val stats: AdminStats? = null,
    val error: String? = null,
)

/**
 * Shared admin dashboard ViewModel. Mirrors CalendarViewModel: generation-gated
 * fetches, pull-to-refresh, server-message error surfacing. `refresh()` asks the
 * server to recompute (`?refresh=true`); the initial load reads the cached stats.
 */
class AdminStatsViewModel(
    private val repository: AdminRepository,
) : ViewModel() {

    private var loadGeneration = 0
    private val _uiState = MutableStateFlow(AdminStatsUiState())
    val uiState: StateFlow<AdminStatsUiState> = _uiState.asStateFlow()

    init { load() }

    fun load() {
        val generation = ++loadGeneration
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            fetch(generation, refresh = false)
        }
    }

    fun refresh() {
        val generation = ++loadGeneration
        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshing = true, error = null) }
            fetch(generation, refresh = true)
            if (generation == loadGeneration) {
                _uiState.update { it.copy(isRefreshing = false) }
            }
        }
    }

    private suspend fun fetch(generation: Int, refresh: Boolean) {
        val result = repository.getStats(refresh = refresh)
        if (generation != loadGeneration) return
        when (result) {
            is ApiResult.Success -> _uiState.update {
                it.copy(isLoading = false, stats = result.data, error = null)
            }
            is ApiResult.Error, is ApiResult.NetworkError -> _uiState.update {
                it.copy(isLoading = false, error = result.errorMessage("Failed to load admin stats"))
            }
        }
    }
}
```

`AdminStatsScreen.kt` — restore the recovered dashboard, extend with all `AdminStats` fields + a Trakt section; pull-to-refresh + loading/error. Uses the app's `formatBytes` (`com.continuum.app.android.ui.util.formatBytes`):
```kotlin
package com.continuum.app.android.ui.screens.admin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.continuum.app.android.ui.components.ContinuumTopBar
import com.continuum.app.android.ui.components.ErrorView
import com.continuum.app.android.ui.components.LoadingIndicator
import com.continuum.app.android.ui.util.formatBytes
import com.continuum.app.model.admin.AdminStats
import com.continuum.app.viewmodel.AdminStatsViewModel
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminStatsScreen(
    onBackClick: () -> Unit,
    viewModel: AdminStatsViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = { ContinuumTopBar(title = "Dashboard", onBackClick = onBackClick) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        when {
            state.isLoading && state.stats == null -> LoadingIndicator(modifier = Modifier.padding(padding))
            state.error != null && state.stats == null ->
                ErrorView(message = state.error!!, onRetry = viewModel::load, modifier = Modifier.padding(padding))
            else -> PullToRefreshBox(
                isRefreshing = state.isRefreshing,
                onRefresh = viewModel::refresh,
                modifier = Modifier.fillMaxSize().padding(padding),
            ) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    state.stats?.let { stats ->
                        item { StatsGrid(stats) }
                        item { TraktSection(stats) }
                    }
                    item { Spacer(Modifier.height(24.dp)) }
                }
            }
        }
    }
}

@Composable
private fun StatsGrid(stats: AdminStats) {
    val tiles = listOf(
        Triple(Icons.Default.Inventory2, "Total Items", stats.totalItems.toString()),
        Triple(Icons.Default.Movie, "Movies", "${stats.totalMovies} (${stats.totalMovieFiles} files)"),
        Triple(Icons.Default.Tv, "Shows", "${stats.totalShows} (${stats.totalShowFiles} files)"),
        Triple(Icons.Default.VideoLibrary, "Files", stats.totalFiles.toString()),
        Triple(Icons.Default.People, "Users", stats.totalUsers.toString()),
        Triple(Icons.Default.PlayArrow, "Active Streams", stats.activeStreams.toString()),
        Triple(Icons.Default.Storage, "Storage", formatBytes(stats.totalStorageBytes)),
    )
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        tiles.chunked(2).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { (icon, label, value) ->
                    StatCard(icon, label, value, Modifier.weight(1f))
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun StatCard(icon: ImageVector, label: String, value: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
        shape = MaterialTheme.shapes.medium,
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(12.dp))
            Column {
                Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun TraktSection(stats: AdminStats) {
    val activity = stats.watchProviderActivity
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Watch Provider Activity", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
            shape = MaterialTheme.shapes.medium,
        ) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (activity == null) {
                    Text("No watch-provider activity reported.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    ActivityRow(Icons.Default.People, "Trakt-connected profiles", activity.traktConnectedProfiles.toString())
                    ActivityRow(Icons.Default.Bolt, "Scrobbles (24h)", activity.scrobbles24h.toString())
                }
            }
        }
    }
}

@Composable
private fun ActivityRow(icon: ImageVector, label: String, value: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
    }
}
```
> Executor: confirm the exact `WatchProviderActivity` fields and render whatever the landed model exposes (the spec lists `trakt_connected_profiles, …, scrobbles_24h` — the `…` means additional fields likely exist; surface them as extra `ActivityRow`s).

`AppNavigation.kt` — add import + destination:
```kotlin
import com.continuum.app.android.ui.screens.admin.AdminStatsScreen
...
        composable(Route.AdminStats.route) {
            AdminStatsScreen(onBackClick = { navController.popBackStack() })
        }
```

`AndroidModule.kt`:
```kotlin
import com.continuum.app.viewmodel.AdminStatsViewModel
...
    viewModel { AdminStatsViewModel(get()) }
```

- [ ] **Step 4: Run tests** — `./gradlew :shared:testDebugUnitTest --tests "*AdminStatsViewModelTest*" :androidApp:compileDebugKotlinAndroid :androidApp:testDebugUnitTest :androidApp:assembleDebug`.
  Manual checklist:
  - Admin → Dashboard shows count/file/storage cards (storage human-readable via `formatBytes`) and the Watch Provider Activity section.
  - Pull to refresh spins, re-fetches with `?refresh=true`, updates values.
  - Server error before any data → ErrorView with Retry; transient error after data loaded → keeps showing data.

- [ ] **Step 5: Commit** — "Add admin stats dashboard (mobile)" (+ trailer).

---

### Task H3: AdminUsersScreen + user detail/edit (mobile)

**Files:**
- Create `androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/admin/AdminUsersScreen.kt`
- Create `androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/admin/AdminUserEditScreen.kt`
- Create `shared/src/commonMain/kotlin/com/continuum/app/viewmodel/AdminUsersViewModel.kt`
- Create `shared/src/commonMain/kotlin/com/continuum/app/viewmodel/AdminUserForm.kt` (pure helpers: role display, validation)
- Create `shared/src/commonTest/kotlin/com/continuum/app/viewmodel/AdminUsersViewModelTest.kt`
- Create `shared/src/commonTest/kotlin/com/continuum/app/viewmodel/AdminUserFormTest.kt`
- Modify `androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/navigation/Routes.kt` (add `AdminUserEdit` param route)
- Modify `androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/navigation/AppNavigation.kt`
- Modify `androidApp/src/androidMain/kotlin/com/continuum/app/android/di/AndroidModule.kt`

**Dependencies (assumed):** `AdminUser(id, username, email, role, permissions, enabled, libraryIds?, maxPlaybackQuality, maxStreams, maxTranscodes, maxProfiles, downloadAllowed, downloadTranscodeAllowed, createdAt, updatedAt, lastActiveAt?)`; `CreateUserRequest(username, email, password, role, …, createDefaultProfile, defaultProfileName)`; `UpdateUserRequest(all optional incl. password)`; `AdminRepository.getUsers()/createUser(req)/updateUser(id, req)/deleteUser(id)`. Executor verifies exact fields/signatures.

- [ ] **Step 1: Write the failing tests**

`AdminUserFormTest.kt` (pure bits — role display + validation):
```kotlin
package com.continuum.app.viewmodel

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertNotNull

class AdminUserFormTest {

    @Test fun `role display capitalizes`() {
        assertEquals("Admin", roleDisplayName("admin"))
        assertEquals("User", roleDisplayName("user"))
        assertEquals("Unknown", roleDisplayName(""))
    }

    @Test fun `create validation requires username email and password`() {
        assertNotNull(validateCreateUser(username = "", email = "a@b.io", password = "secret1"))
        assertNotNull(validateCreateUser(username = "u", email = "bad", password = "secret1"))
        assertNotNull(validateCreateUser(username = "u", email = "a@b.io", password = "123"))
        assertNull(validateCreateUser(username = "u", email = "a@b.io", password = "secret1"))
    }

    @Test fun `quota parsing rejects negatives and non-numbers`() {
        assertNull(parseQuota(""))            // blank → unlimited / unchanged
        assertEquals(3, parseQuota("3"))
        assertEquals(QuotaInvalid, runCatching { parseQuotaOrInvalid("-1") }.getOrNull())
    }
}
```
> Executor: keep the helper surface minimal and matched to what the screen needs. The point is that `roleDisplayName`, `validateCreateUser`, and quota parsing are pure and testable. Adjust names to whatever you implement; delete the `parseQuotaOrInvalid` case if you fold validation differently.

`AdminUsersViewModelTest.kt` (list/CRUD round-trips against a fake repo):
```kotlin
package com.continuum.app.viewmodel

import com.continuum.app.model.admin.AdminUser
import com.continuum.app.model.admin.CreateUserRequest
import com.continuum.app.model.admin.UpdateUserRequest
import com.continuum.app.network.ApiResult
import com.continuum.app.network.api.AdminApi
import com.continuum.app.repository.AdminRepository
import com.continuum.app.model.admin.AdminStats
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class AdminUsersViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()
    @BeforeTest fun setUp() { Dispatchers.setMain(dispatcher) }
    @AfterTest fun tearDown() { Dispatchers.resetMain() }

    private fun u(id: Int, name: String, enabled: Boolean = true) = AdminUser(
        id = id, username = name, email = "$name@x.io", role = "user",
        permissions = emptyList(), enabled = enabled,
    )

    @Test fun `loads users on init`() = runTest(dispatcher) {
        val api = FakeAdminUsersApi(users = mutableListOf(u(1, "a"), u(2, "b")))
        val vm = AdminUsersViewModel(AdminRepository(api))
        assertEquals(2, vm.uiState.value.users.size)
    }

    @Test fun `delete removes user and refreshes`() = runTest(dispatcher) {
        val api = FakeAdminUsersApi(users = mutableListOf(u(1, "a"), u(2, "b")))
        val vm = AdminUsersViewModel(AdminRepository(api))
        vm.deleteUser(1)
        assertTrue(vm.uiState.value.users.none { it.id == 1 })
    }

    @Test fun `error surfaces server message`() = runTest(dispatcher) {
        val api = FakeAdminUsersApi(listError = ApiResult.Error(code = 500, error = "x", message = ""))
        assertEquals("Failed to load users", AdminUsersViewModel(AdminRepository(api)).uiState.value.error)
    }
}

private class FakeAdminUsersApi(
    private val users: MutableList<AdminUser> = mutableListOf(),
    private val listError: ApiResult<List<AdminUser>>? = null,
) : AdminApi {
    override suspend fun getUsers(): ApiResult<List<AdminUser>> = listError ?: ApiResult.Success(users.toList())
    override suspend fun createUser(request: CreateUserRequest): ApiResult<AdminUser> {
        val created = AdminUser(id = (users.maxOfOrNull { it.id } ?: 0) + 1, username = request.username, email = request.email, role = request.role ?: "user", permissions = emptyList(), enabled = true)
        users += created; return ApiResult.Success(created)
    }
    override suspend fun updateUser(id: Int, request: UpdateUserRequest): ApiResult<AdminUser> {
        val idx = users.indexOfFirst { it.id == id }
        return if (idx >= 0) ApiResult.Success(users[idx]) else ApiResult.Error(404, "nf", "")
    }
    override suspend fun deleteUser(id: Int): ApiResult<Unit> { users.removeAll { it.id == id }; return ApiResult.Success(Unit) }
    override suspend fun getStats(refresh: Boolean): ApiResult<AdminStats> = error("unused")
}
```
> Executor: match `AdminApi`'s exact member set/signatures; fill unused members with `error("unused")`.

- [ ] **Step 2: Run test to verify it fails** — `./gradlew :shared:testDebugUnitTest --tests "*AdminUsersViewModelTest*" --tests "*AdminUserFormTest*"` (fails: types missing).

- [ ] **Step 3: Implementation**

`AdminUserForm.kt` (pure helpers):
```kotlin
package com.continuum.app.viewmodel

fun roleDisplayName(role: String): String = when {
    role.isBlank() -> "Unknown"
    else -> role.replaceFirstChar { it.uppercase() }
}

private val EMAIL_REGEX = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")

/** Returns an error message, or null when the create form is valid. */
fun validateCreateUser(username: String, email: String, password: String): String? = when {
    username.isBlank() -> "Username is required"
    !EMAIL_REGEX.matches(email) -> "Enter a valid email"
    password.length < 6 -> "Password must be at least 6 characters"
    else -> null
}

/** Parses an optional quota field. Blank → null (unlimited/unchanged); invalid/negative → null too (caller validates separately if strict). */
fun parseQuota(raw: String): Int? = raw.trim().toIntOrNull()?.takeIf { it >= 0 }
```

`AdminUsersViewModel.kt` (shared; list + delete + post-mutation refresh):
```kotlin
package com.continuum.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.continuum.app.model.admin.AdminUser
import com.continuum.app.network.ApiResult
import com.continuum.app.network.errorMessage
import com.continuum.app.repository.AdminRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AdminUsersUiState(
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val users: List<AdminUser> = emptyList(),
    val error: String? = null,
    /** One-shot user-facing message after a mutation (toast). */
    val message: String? = null,
)

class AdminUsersViewModel(
    private val repository: AdminRepository,
) : ViewModel() {

    private var loadGeneration = 0
    private val _uiState = MutableStateFlow(AdminUsersUiState())
    val uiState: StateFlow<AdminUsersUiState> = _uiState.asStateFlow()

    init { load() }

    fun load() {
        val generation = ++loadGeneration
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            fetch(generation)
        }
    }

    fun refresh() {
        val generation = ++loadGeneration
        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshing = true, error = null) }
            fetch(generation)
            if (generation == loadGeneration) _uiState.update { it.copy(isRefreshing = false) }
        }
    }

    fun deleteUser(id: Int) {
        viewModelScope.launch {
            when (val r = repository.deleteUser(id)) {
                is ApiResult.Success -> {
                    _uiState.update { s -> s.copy(users = s.users.filter { it.id != id }, message = "User deleted") }
                }
                is ApiResult.Error, is ApiResult.NetworkError ->
                    _uiState.update { it.copy(message = r.errorMessage("Failed to delete user")) }
            }
        }
    }

    fun consumeMessage() = _uiState.update { it.copy(message = null) }

    private suspend fun fetch(generation: Int) {
        val result = repository.getUsers()
        if (generation != loadGeneration) return
        when (result) {
            is ApiResult.Success -> _uiState.update { it.copy(isLoading = false, users = result.data, error = null) }
            is ApiResult.Error, is ApiResult.NetworkError ->
                _uiState.update { it.copy(isLoading = false, error = result.errorMessage("Failed to load users")) }
        }
    }
}
```
> The edit/create screen drives create/update through the repository directly (or via a small `AdminUserEditViewModel` — executor's choice; keep create/update there and have the list `refresh()` on return). Submit returns to the list on success and the list reloads via `LaunchedEffect`/back-result.

`AdminUsersScreen.kt` — list (username/email, role badge via `roleDisplayName`, enabled chip, last-active), FAB → create, row tap → edit, swipe/menu delete with confirm dialog, pull-to-refresh, loading/error/empty:
```kotlin
package com.continuum.app.android.ui.screens.admin

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.People
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.continuum.app.android.ui.components.ContinuumTopBar
import com.continuum.app.android.ui.components.EmptyStateView
import com.continuum.app.android.ui.components.ErrorView
import com.continuum.app.android.ui.components.LoadingIndicator
import com.continuum.app.model.admin.AdminUser
import com.continuum.app.viewmodel.AdminUsersViewModel
import com.continuum.app.viewmodel.roleDisplayName
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminUsersScreen(
    onBackClick: () -> Unit,
    onCreateUser: () -> Unit,
    onEditUser: (Int) -> Unit,
    viewModel: AdminUsersViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var pendingDelete by remember { mutableStateOf<AdminUser?>(null) }

    // Reload when re-entering after a create/edit.
    LaunchedEffect(Unit) { viewModel.refresh() }

    LaunchedEffect(state.message) {
        state.message?.let { snackbarHostState.showSnackbar(it); viewModel.consumeMessage() }
    }

    Scaffold(
        topBar = { ContinuumTopBar(title = "Users", onBackClick = onBackClick) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            FloatingActionButton(onClick = onCreateUser) {
                Icon(Icons.Default.Add, contentDescription = "Add user")
            }
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        when {
            state.isLoading && state.users.isEmpty() -> LoadingIndicator(Modifier.padding(padding))
            state.error != null && state.users.isEmpty() ->
                ErrorView(state.error!!, onRetry = viewModel::load, modifier = Modifier.padding(padding))
            state.users.isEmpty() -> EmptyStateView(
                title = "No users", subtitle = "Tap + to create one.",
                icon = Icons.Outlined.People, modifier = Modifier.padding(padding),
            )
            else -> PullToRefreshBox(
                isRefreshing = state.isRefreshing, onRefresh = viewModel::refresh,
                modifier = Modifier.fillMaxSize().padding(padding),
            ) {
                LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(state.users, key = { it.id }) { user ->
                        UserRow(user, onClick = { onEditUser(user.id) }, onLongPress = { pendingDelete = user })
                    }
                    item { Spacer(Modifier.height(72.dp)) }
                }
            }
        }
    }

    pendingDelete?.let { user ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Delete ${user.username}?") },
            text = { Text("This permanently removes the account. This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = { viewModel.deleteUser(user.id); pendingDelete = null }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("Cancel") } },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun UserRow(user: AdminUser, onClick: () -> Unit, onLongPress: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(user.username, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
                Text(user.email, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                user.lastActiveAt?.let {
                    Text("Last active $it", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    roleDisplayName(user.role),
                    style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Medium,
                    color = if (user.role == "admin") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    if (user.enabled) "Enabled" else "Disabled",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (user.enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                )
                TextButton(onClick = onLongPress, contentPadding = PaddingValues(0.dp)) { Text("Delete", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall) }
            }
        }
    }
}
```

`AdminUserEditScreen.kt` — create + edit form. Param `userId: Int?` (null = create). Fields: username/email (create only editable), password (create: required; edit: "Reset password" optional), role picker (admin/user), enabled switch, library access (comma/chips of library ids), quota fields (max streams/transcodes/profiles). Validates via `validateCreateUser`/`parseQuota`; submits `CreateUserRequest`/`UpdateUserRequest` through the repository (inject `AdminRepository` directly into a small screen-scoped `AdminUserEditViewModel`, or `koinInject<AdminRepository>()` + `rememberCoroutineScope`). On success, `onSaved()` pops back; the list refreshes via its `LaunchedEffect(Unit)`.
> Executor: keep the form fields aligned to the landed `CreateUserRequest`/`UpdateUserRequest`. Build only fields the requests support. Use the existing `SettingsRow`/`OutlinedTextField`/`Switch` idioms; reuse `roleDisplayName` for the role chips' labels.

`Routes.kt` — add the param route:
```kotlin
    data class AdminUserEdit(val userId: Int? = null) : Route(
        if (userId != null) "admin/users/$userId/edit" else "admin/users/create"
    ) {
        companion object {
            const val ROUTE = "admin/users/{userId}/edit"
            const val CREATE_ROUTE = "admin/users/create"
            const val ARG_USER_ID = "userId"
        }
    }
```

`AppNavigation.kt` — wire users list + edit/create. Update the hub destination to pass `onOpenUsers` (already done in Task 1) and add:
```kotlin
import com.continuum.app.android.ui.screens.admin.AdminUsersScreen
import com.continuum.app.android.ui.screens.admin.AdminUserEditScreen
...
        composable(Route.AdminUsers.route) {
            AdminUsersScreen(
                onBackClick = { navController.popBackStack() },
                onCreateUser = { navController.navigate(Route.AdminUserEdit().route) },
                onEditUser = { id -> navController.navigate(Route.AdminUserEdit(id).route) },
            )
        }
        composable(Route.AdminUserEdit.CREATE_ROUTE) {
            AdminUserEditScreen(userId = null, onBackClick = { navController.popBackStack() }, onSaved = { navController.popBackStack() })
        }
        composable(
            route = Route.AdminUserEdit.ROUTE,
            arguments = listOf(navArgument(Route.AdminUserEdit.ARG_USER_ID) { type = NavType.IntType }),
        ) { backStackEntry ->
            AdminUserEditScreen(
                userId = backStackEntry.arguments?.getInt(Route.AdminUserEdit.ARG_USER_ID),
                onBackClick = { navController.popBackStack() },
                onSaved = { navController.popBackStack() },
            )
        }
```

`AndroidModule.kt`:
```kotlin
import com.continuum.app.viewmodel.AdminUsersViewModel
...
    viewModel { AdminUsersViewModel(get()) }
    // If a separate edit VM is used:
    // viewModel { params -> AdminUserEditViewModel(get(), params.getOrNull<Int>()) }
```

- [ ] **Step 4: Run tests** — `./gradlew :shared:testDebugUnitTest --tests "*AdminUsers*" --tests "*AdminUserForm*" :androidApp:compileDebugKotlinAndroid :androidApp:testDebugUnitTest :androidApp:assembleDebug`.
  Manual checklist:
  - Users list shows username/email/role/enabled/last-active; pull-to-refresh works.
  - FAB → create form; invalid email/short password blocks submit with inline error; valid create adds the user and returns to a refreshed list.
  - Tap a user → edit; change role/enabled/quota → save persists (re-open shows new values); "Reset password" with a new value succeeds and is not pre-filled.
  - Delete → confirm dialog → user disappears; cancel leaves it. Server failures show a toast and keep the list intact.

- [ ] **Step 5: Commit** — "Add admin user management (mobile)" (+ trailer).

---

### Task H4: TvAdminScreen (restore + live stats)

**Files:**
- Create `androidTvApp/src/androidMain/kotlin/com/continuum/app/tv/ui/screens/admin/TvAdminScreen.kt`
- Create `androidTvApp/src/androidMain/kotlin/com/continuum/app/tv/ui/screens/admin/TvAdminStatsViewModel.kt`
- Create `androidTvApp/src/androidUnitTest/kotlin/com/continuum/app/tv/ui/screens/admin/TvAdminGateTest.kt`
- Modify `androidTvApp/src/androidMain/kotlin/com/continuum/app/tv/ui/navigation/TvRoute.kt`
- Modify `androidTvApp/src/androidMain/kotlin/com/continuum/app/tv/ui/shell/TvMainShell.kt`
- Modify `androidTvApp/src/androidMain/kotlin/com/continuum/app/tv/ui/screens/settings/TvSettingsScreen.kt`
- Modify `androidTvApp/src/androidMain/kotlin/com/continuum/app/tv/ui/screens/settings/TvSettingsViewModel.kt`
- Modify `androidTvApp/src/androidMain/kotlin/com/continuum/app/tv/di/AndroidTvModule.kt`

> **Decision:** Reuse the shared `AdminStatsViewModel` from Task 2 (it has no Android/mobile dependency) rather than a TV-specific copy — the recovered TV screen referenced a `AdminViewModel` that no longer exists, so we point it at the landed `com.continuum.app.viewmodel.AdminStatsViewModel`. The only TV-new piece is reachability gating; the acting-admin gate is folded into `TvSettingsViewModel` (which already loads the user) plus the active profile. (Listed `TvAdminStatsViewModel.kt` above is therefore optional — prefer reusing the shared VM; if the team wants a TV-named alias, make it a thin subclass. Report which was chosen.)

**Dependencies (assumed):** same as Task 2 plus `isActingAdmin` + `Profile.isPrimary`. `androidTvApp` has no shared `formatBytes`, so keep the recovered private `formatBytes` in the TV screen.

- [ ] **Step 1: Write the failing test** — the only TV-pure bit is the settings gate. Add `adminVisible` to `TvSettingsViewModel.UiState` and test it (mirror Task 1).

```kotlin
package com.continuum.app.tv.ui.screens.admin

import com.continuum.app.model.auth.User
import com.continuum.app.model.profile.Profile
import com.continuum.app.util.isActingAdmin
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The TV Settings "Admin" row reachability is the acting-admin gate, identical
 * to mobile. This pins the gate the TvSettingsViewModel folds into UiState.
 */
class TvAdminGateTest {
    private fun user(role: String) = User(id = 1, username = "u", email = "e@x.io", role = role)
    private fun profile(primary: Boolean) = Profile(id = "p", name = "p", isPrimary = primary)

    @Test fun `admin on primary profile sees admin`() = assertTrue(isActingAdmin(user("admin"), profile(true)))
    @Test fun `admin on non-primary hidden`() = assertFalse(isActingAdmin(user("admin"), profile(false)))
    @Test fun `non-admin hidden`() = assertFalse(isActingAdmin(user("user"), profile(true)))
    @Test fun `admin without profile visible`() = assertTrue(isActingAdmin(user("admin"), null))
}
```
> Executor: if `isActingAdmin` already has a shared unit test (it does, per the shared plan), this TV test is a thin reachability guard. If a per-VM test is preferred, fake the auth/profile repos as in Task 1 and assert `TvSettingsViewModel.uiState.value.adminVisible`.

- [ ] **Step 2: Run test to verify it fails** — `./gradlew :androidTvApp:testDebugUnitTest --tests "*TvAdminGateTest*"` (fails until `Profile.isPrimary`/`isActingAdmin` land; once they land it passes — this guards the gate semantics the screen depends on).

- [ ] **Step 3: Implementation**

`TvAdminStatsViewModel.kt` (thin alias reusing the shared VM, so `koinViewModel<...>()` resolves a TV-scoped type; optional):
```kotlin
package com.continuum.app.tv.ui.screens.admin

import com.continuum.app.repository.AdminRepository
import com.continuum.app.viewmodel.AdminStatsViewModel

/** TV alias of the shared admin stats VM so the TV Koin graph can register it
 *  by a TV-specific type without duplicating logic. */
class TvAdminStatsViewModel(repository: AdminRepository) : AdminStatsViewModel(repository)
```
> Requires `AdminStatsViewModel` to be `open`. If the team prefers no alias, register and inject the shared `AdminStatsViewModel` directly and delete this file.

`TvAdminScreen.kt` — restore the recovered 2-column grid; point at the live VM; extend tiles to include movie/show file counts and the storage tile; keep the private `formatBytes`. Add pull-style refresh isn't idiomatic on TV — instead reload on entry + a "Refresh" action via the existing `BackHandler`-style focusable card (optional). Minimal restore:
```kotlin
package com.continuum.app.tv.ui.screens.admin

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.continuum.app.model.admin.AdminStats
import com.continuum.app.tv.ui.components.TvErrorScreen
import com.continuum.app.tv.ui.components.TvLoadingScreen
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TvAdminScreen(
    onBack: () -> Unit,
    viewModel: TvAdminStatsViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    BackHandler(enabled = true) { onBack() }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Header()
        when {
            state.isLoading && state.stats == null -> TvLoadingScreen()
            state.error != null && state.stats == null -> TvErrorScreen(message = state.error!!, onRetry = viewModel::load)
            state.stats != null -> StatsGrid(state.stats!!)
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun Header() {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 48.dp, vertical = 32.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Icon(Icons.Filled.AdminPanelSettings, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(40.dp))
        Text("Admin Dashboard", style = MaterialTheme.typography.displaySmall, color = MaterialTheme.colorScheme.onBackground)
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun StatsGrid(stats: AdminStats) {
    val tiles = listOf(
        StatTile("Total Items", stats.totalItems.toString()),
        StatTile("Movies", "${stats.totalMovies} / ${stats.totalMovieFiles} files"),
        StatTile("TV Shows", "${stats.totalShows} / ${stats.totalShowFiles} files"),
        StatTile("Users", stats.totalUsers.toString()),
        StatTile("Active Streams", stats.activeStreams.toString()),
        StatTile("Storage", formatBytes(stats.totalStorageBytes)),
    )
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        contentPadding = PaddingValues(horizontal = 48.dp, vertical = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(32.dp),
        verticalArrangement = Arrangement.spacedBy(32.dp),
    ) {
        items(tiles, key = { it.label }) { StatCard(it) }
    }
}

private data class StatTile(val label: String, val value: String)

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun StatCard(tile: StatTile) {
    Card(onClick = {}, shape = CardDefaults.shape(shape = RoundedCornerShape(20.dp)), modifier = Modifier.fillMaxWidth().height(180.dp)) {
        Column(Modifier.fillMaxSize().padding(32.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Text(tile.label, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(tile.value, style = MaterialTheme.typography.displaySmall, color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.ExtraBold)
        }
    }
}

private fun formatBytes(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val kb = bytes / 1024.0; if (kb < 1024) return "%.1f KB".format(kb)
    val mb = kb / 1024.0; if (mb < 1024) return "%.1f MB".format(mb)
    val gb = mb / 1024.0; if (gb < 1024) return "%.1f GB".format(gb)
    return "%.2f TB".format(gb / 1024.0)
}
```

`TvRoute.kt` — add an Admin sub-route inside the shell (`TvMainRoute`):
```kotlin
    data object Admin : TvMainRoute("main/admin")
```

`TvSettingsViewModel.kt` — fold the gate into `UiState`. Add `val adminVisible: Boolean = false` to `UiState`; in `loadUser()` after the user lands, fetch the active profile and set it:
```kotlin
import com.continuum.app.util.isActingAdmin
...
// in loadUser(), inside ApiResult.Success branch (or right after) :
                    val profile = profileRepository.getActiveProfile()
                    _uiState.update { it.copy(adminVisible = isActingAdmin(r.data, profile)) }
```
> `profileRepository` is already a ctor dependency.

`TvSettingsScreen.kt` — add `onNavigateToAdmin: () -> Unit = {}` param; render an "Admin Dashboard" action in a new gated section (only when `state.adminVisible`), placed before the Library section:
```kotlin
        if (state.adminVisible) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SectionHeader(title = "Admin")
                    SettingsRowAction(label = "Admin Dashboard", onClick = onNavigateToAdmin)
                }
            }
        }
```

`TvMainShell.kt` — register the nested Admin destination and pass the callback to `TvSettingsScreen`:
```kotlin
import com.continuum.app.tv.ui.screens.admin.TvAdminScreen
...
// inside the NavHost:
                composable(TvMainRoute.Admin.route) {
                    TvAdminScreen(onBack = {
                        if (nestedNav.previousBackStackEntry != null) nestedNav.popBackStack()
                    })
                }
// in the TvSettingsScreen(...) call, add:
                        onNavigateToAdmin = {
                            navigateToRoute(TvMainRoute.Admin.route)
                            moveFocusToContent(TvMainRoute.Admin.route)
                        },
```

`AndroidTvModule.kt`:
```kotlin
import com.continuum.app.tv.ui.screens.admin.TvAdminStatsViewModel
...
    viewModel { TvAdminStatsViewModel(get()) }
```
> If reusing the shared VM directly instead of the alias, register `viewModel { AdminStatsViewModel(get()) }` and import it in the screen.

- [ ] **Step 4: Run tests** — `./gradlew :androidTvApp:compileDebugKotlinAndroid :androidTvApp:testDebugUnitTest :androidTvApp:assembleDebug` (and `:shared:testDebugUnitTest` if the shared VM was touched).
  Manual checklist:
  - Sign in as admin on primary profile on TV → Settings shows an "Admin" section with "Admin Dashboard".
  - Activate it → 2-column stat grid loads live `/admin/stats` (counts, file breakdowns, storage human-readable, active streams). D-pad navigates the grid; Back returns to Settings with focus restored.
  - Non-admin (or non-primary profile) → no Admin section.
  - Stats fetch error before data → `TvErrorScreen` with retry.

- [ ] **Step 5: Commit** — "Restore TV admin stats dashboard" (+ trailer).

## Section D: Mobile sessions, logs, scans

### Dependencies (assumed shared layer lands first; executor verifies against landed code)
- `com.continuum.app.model.admin.AdminSession` with at least: `id: String`, `userLabel: String`, `mediaTitle: String`, `seasonNumber: Int?`, `episodeNumber: Int?`, `playMethod: String`, `positionSeconds: Double`, `durationSeconds: Double`, `isPaused: Boolean`, `hasPlaybackControl: Boolean`, `posterUrl: String?`, `posterThumbhash: String?`, `videoCodecSource: String?`, `videoCodecTarget: String?`, `bitrateBps: Long?`, `widthTarget: Int?`, `heightTarget: Int?`, `isTranscoding: Boolean`. (Adapt field names to the landed AdminSession — it carries the full source/target transcode detail; map what's needed.)
- `AdminRepository.getSessions(): ApiResult<List<AdminSession>>`, `sessionControl(id, action, body: SessionControlRequest? = null): ApiResult<SessionControlResponse>` with `action ∈ {pause,resume,stop,terminate,message}`, `SessionControlRequest(reason?, title?, message?, deadlineMs?)`.
- `AdminLogEntry`, `AdminAuditEntry`, `LogPage<T>(entries, nextCursor?)`; `getAppLogs(filters: Map<String,String>, cursor: String?=null): ApiResult<LogPage<AdminLogEntry>>`, `getAuditLogs(...)`. (If the landed repo bakes cursor/limit into the filter map instead, fold them into the query builder.)
- `ScanRequest(libraryId: Int?=null, path: String?=null)`, `ScanResponse`, `ScanCancelResponse`; `triggerScan(request): ApiResult<ScanResponse>`, `cancelScan(libraryId): ApiResult<ScanCancelResponse>`.
- Library list source confirmed: `PersonalDataRepository.listUserLibraries(): ApiResult<List<UserLibrary>>` (UserLibrary = id/name/type/sortOrder/posterUrl?). **UserLibrary has no last-scanned field** — show name+type + transient action status only.
- Routes `Routes.AdminSessions/AdminLogs/AdminScans` are defined by the hub task; these tasks OVERWRITE the hub's placeholders keeping the `onBackClick`-only `composable(...)` signatures. ViewModels co-located in each screen file (LibrariesViewModel idiom), registered `viewModel { ... }` in AndroidModule.kt.

---

### Task D1: AdminSessionsScreen + AdminSessionsViewModel (mobile)

**Files:**
- Create `androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/admin/AdminSessionFormatters.kt`
- Create `androidApp/src/androidUnitTest/kotlin/com/continuum/app/android/ui/screens/admin/AdminSessionFormattersTest.kt`
- Create `androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/admin/AdminSessionsScreen.kt` (screen + co-located `AdminSessionsViewModel`)
- Modify `androidApp/src/androidMain/kotlin/com/continuum/app/android/di/AndroidModule.kt` (register `viewModel { AdminSessionsViewModel(get()) }`)
- Modify `androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/navigation/AppNavigation.kt` (overwrite the hub's `AdminSessions` placeholder)

- [ ] **Step 1: Write the failing test**

Create `AdminSessionFormattersTest.kt`:

```kotlin
package com.continuum.app.android.ui.screens.admin

import kotlin.test.Test
import kotlin.test.assertEquals

class AdminSessionFormattersTest {

    @Test
    fun directPlayShowsDirectWithBitrateAndResolution() {
        val line = sessionSummaryLine(
            isTranscoding = false, playMethod = "DirectPlay",
            bitrateBps = 12_000_000, widthTarget = 1920, heightTarget = 1080,
            videoCodecSource = "h264", videoCodecTarget = "h264",
        )
        assertEquals("Direct Play • 12.0 Mbps • 1080p", line)
    }

    @Test
    fun transcodeShowsCodecArrowAndResolution() {
        val line = sessionSummaryLine(
            isTranscoding = true, playMethod = "Transcode",
            bitrateBps = 4_500_000, widthTarget = 1280, heightTarget = 720,
            videoCodecSource = "hevc", videoCodecTarget = "h264",
        )
        assertEquals("Transcode hevc→h264 • 4.5 Mbps • 720p", line)
    }

    @Test
    fun missingBitrateAndResolutionAreOmitted() {
        val line = sessionSummaryLine(
            isTranscoding = false, playMethod = "DirectStream",
            bitrateBps = null, widthTarget = null, heightTarget = null,
            videoCodecSource = null, videoCodecTarget = null,
        )
        assertEquals("Direct Stream", line)
    }

    @Test
    fun resolutionBucketsToNearestStandardLabel() {
        assertEquals("4K", resolutionLabel(3840, 2160))
        assertEquals("1080p", resolutionLabel(1920, 1080))
        assertEquals("720p", resolutionLabel(1280, 720))
        assertEquals("480p", resolutionLabel(854, 480))
        assertEquals("576p", resolutionLabel(720, 576))
    }

    @Test
    fun bitrateRendersMbpsWithOneDecimal() {
        assertEquals("4.5 Mbps", bitrateLabel(4_500_000))
        assertEquals("950 Kbps", bitrateLabel(950_000))
    }

    @Test
    fun progressLabelIsPositionOfDuration() {
        assertEquals("0:30 / 1:00:00", sessionProgressLabel(30.0, 3600.0))
        assertEquals("0:30", sessionProgressLabel(30.0, 0.0))
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

`./gradlew :androidApp:testDebugUnitTest --tests "com.continuum.app.android.ui.screens.admin.AdminSessionFormattersTest"` — fails to compile (formatters absent).

- [ ] **Step 3: Implementation**

Create `AdminSessionFormatters.kt` (pure; reuses `formatClockTime` from `ui/util/Formatters.kt`):

```kotlin
package com.continuum.app.android.ui.screens.admin

import com.continuum.app.android.ui.util.formatClockTime

internal fun playMethodLabel(playMethod: String): String = when (playMethod.lowercase()) {
    "directplay", "direct_play", "direct play" -> "Direct Play"
    "directstream", "direct_stream", "direct stream" -> "Direct Stream"
    "transcode" -> "Transcode"
    else -> playMethod.ifBlank { "Playing" }
}

internal fun bitrateLabel(bitrateBps: Long?): String? {
    val bps = bitrateBps ?: return null
    if (bps <= 0) return null
    return if (bps >= 1_000_000) "%.1f Mbps".format(bps / 1_000_000.0) else "${bps / 1000} Kbps"
}

internal fun resolutionLabel(width: Int?, height: Int?): String? {
    val h = height ?: return null
    if (h <= 0) return null
    return when {
        h >= 2000 -> "4K"
        h >= 1080 -> "1080p"
        h >= 720 -> "720p"
        h <= 480 -> "480p"
        else -> "${h}p"
    }
}

internal fun sessionSummaryLine(
    isTranscoding: Boolean,
    playMethod: String,
    bitrateBps: Long?,
    widthTarget: Int?,
    heightTarget: Int?,
    videoCodecSource: String?,
    videoCodecTarget: String?,
): String {
    val head = if (isTranscoding) {
        val src = videoCodecSource?.takeIf { it.isNotBlank() }
        val dst = videoCodecTarget?.takeIf { it.isNotBlank() }
        if (src != null && dst != null && src != dst) "${playMethodLabel(playMethod)} $src→$dst"
        else playMethodLabel(playMethod)
    } else playMethodLabel(playMethod)
    val parts = listOfNotNull(head, bitrateLabel(bitrateBps), resolutionLabel(widthTarget, heightTarget))
    return parts.joinToString(" • ")
}

internal fun sessionProgressLabel(positionSeconds: Double, durationSeconds: Double): String {
    val pos = formatClockTime(positionSeconds)
    if (durationSeconds <= 0.0 || durationSeconds.isNaN()) return pos
    return "$pos / ${formatClockTime(durationSeconds)}"
}

internal fun seasonEpisodeLabel(season: Int?, episode: Int?): String? =
    if (season != null && episode != null) "S${season}E$episode" else null
```

Create `AdminSessionsScreen.kt` — `PullToRefreshBox` + `LazyColumn` (RequestsScreen idiom), `ThumbhashImage` poster, per-row `DropdownMenu` action menu gated on `hasPlaybackControl`, message → `AlertDialog` (Send disabled until body non-blank), terminate → confirm `AlertDialog`, control results toast then refresh. Co-located `AdminSessionsViewModel(repository: AdminRepository)` with `uiState` (isLoading/isRefreshing/sessions/error) + a `MutableSharedFlow<String>` toasts; `load()/refresh()/control(id, action, body)`. (Use the full composable from the drafter output: state class AdminSessionsUiState, the ViewModel with fetch()/control(), SessionRow with the summary/progress/SxEy lines and the menu.)

NOTE FOR IMPLEMENTER: the full AdminSessionsScreen.kt source is in the drafter output; reproduce it, but READ the landed AdminSession field names first and map the SessionRow accordingly (the assumed names may differ — e.g. transcode detail may be nested). Use `ApiResult.errorMessage(...)` for failures. Register the VM + route as below.

Register in `AndroidModule.kt`: `viewModel { AdminSessionsViewModel(get()) }`. Wire `composable(Routes.AdminSessions) { AdminSessionsScreen(onBackClick = { navController.popBackStack() }) }` in `AppNavigation.kt`.

- [ ] **Step 4: Run tests + manual checklist**

`./gradlew :androidApp:compileDebugKotlinAndroid :androidApp:testDebugUnitTest :androidApp:assembleDebug`

Manual (acting-admin device with a live stream): list shows poster/user/media+SxEy/playing-paused+progress/transcode-summary; action menu only when `has_playback_control`; pause↔resume after refresh; stop ends stream; message dialog (Send disabled until body); terminate confirms; pull-to-refresh; empty + error+Retry states.

- [ ] **Step 5: Commit**

`git add androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/admin/AdminSessionFormatters.kt androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/admin/AdminSessionsScreen.kt androidApp/src/androidUnitTest/kotlin/com/continuum/app/android/ui/screens/admin/AdminSessionFormattersTest.kt androidApp/src/androidMain/kotlin/com/continuum/app/android/di/AndroidModule.kt androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/navigation/AppNavigation.kt && git commit -m "Add admin sessions screen + session summary formatter

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>"`

---

### Task D2: AdminLogsScreen + AdminLogsViewModel (mobile)

**Files:**
- Create `androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/admin/AdminLogQuery.kt`
- Create `androidApp/src/androidUnitTest/kotlin/com/continuum/app/android/ui/screens/admin/AdminLogQueryTest.kt`
- Create `androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/admin/AdminLogsScreen.kt` (screen + co-located `AdminLogsViewModel`)
- Modify `androidApp/src/androidMain/kotlin/com/continuum/app/android/di/AndroidModule.kt`
- Modify `androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/navigation/AppNavigation.kt` (overwrite `AdminLogs` placeholder)

- [ ] **Step 1: Write the failing test**

Create `AdminLogQueryTest.kt`:

```kotlin
package com.continuum.app.android.ui.screens.admin

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AdminLogQueryTest {

    @Test fun blankFieldsAreOmitted() {
        val q = buildLogQuery(level = null, query = "  ", component = "", limit = 100)
        assertFalse(q.containsKey("level")); assertFalse(q.containsKey("q"))
        assertFalse(q.containsKey("component")); assertEquals("100", q["limit"])
    }

    @Test fun setFieldsAreTrimmedAndIncluded() {
        val q = buildLogQuery(level = "error", query = "  timeout ", component = "scanner", limit = 50)
        assertEquals("error", q["level"]); assertEquals("timeout", q["q"])
        assertEquals("scanner", q["component"]); assertEquals("50", q["limit"])
    }

    @Test fun limitIsClampedToServerMax() {
        assertEquals("200", buildLogQuery(null, null, null, 500)["limit"])
        assertEquals("1", buildLogQuery(null, null, null, 0)["limit"])
    }

    @Test fun allLevelSentinelIsTreatedAsNoFilter() {
        assertFalse(buildLogQuery(level = "All", query = null, component = null, limit = 100).containsKey("level"))
    }

    @Test fun auditRowDetailLineCombinesMethodPathStatus() {
        assertEquals("GET /api/v1/admin/stats → 200", auditSummaryLine("get", "/api/v1/admin/stats", 200))
    }

    @Test fun appLevelSeverityOrderingForBadgeColorSelection() {
        assertTrue(logLevelRank("error") > logLevelRank("warn"))
        assertTrue(logLevelRank("warn") > logLevelRank("info"))
        assertEquals(0, logLevelRank("trace"))
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

`./gradlew :androidApp:testDebugUnitTest --tests "com.continuum.app.android.ui.screens.admin.AdminLogQueryTest"` — fails to compile.

- [ ] **Step 3: Implementation**

Create `AdminLogQuery.kt` (pure):

```kotlin
package com.continuum.app.android.ui.screens.admin

internal const val LOG_LEVEL_ALL = "All"
internal val LOG_LEVELS = listOf(LOG_LEVEL_ALL, "debug", "info", "warn", "error")
internal const val LOG_PAGE_LIMIT = 100
private const val LOG_SERVER_MAX = 200

internal fun buildLogQuery(
    level: String?,
    query: String?,
    component: String?,
    limit: Int = LOG_PAGE_LIMIT,
): Map<String, String> = buildMap {
    level?.trim()?.takeIf { it.isNotEmpty() && it != LOG_LEVEL_ALL }?.let { put("level", it) }
    query?.trim()?.takeIf { it.isNotEmpty() }?.let { put("q", it) }
    component?.trim()?.takeIf { it.isNotEmpty() }?.let { put("component", it) }
    put("limit", limit.coerceIn(1, LOG_SERVER_MAX).toString())
}

internal fun logLevelRank(level: String): Int = when (level.lowercase()) {
    "error", "fatal" -> 4
    "warn", "warning" -> 3
    "info" -> 2
    "debug" -> 1
    else -> 0
}

internal fun auditSummaryLine(method: String, path: String, statusCode: Int): String =
    "${method.uppercase()} $path → $statusCode"
```

Create `AdminLogsScreen.kt`: `ContinuumTopBar(title="Logs")` + `TabRow` (App/Audit), filter row (level `DropdownMenu` from `LOG_LEVELS`, search `OutlinedTextField` with ImeAction.Search → applyFilters, optional component field on App tab), cursor-paginated `LazyColumn` with the InboxScreen `derivedStateOf` near-end `loadMore` + footer spinner. Co-located `AdminLogsViewModel(repository)` with `AdminLogsUiState(tab, level, query, component, isLoading, isLoadingMore, appEntries, auditEntries, nextCursor, error)` and `selectTab/onLevelChange/onQueryChange/onComponentChange/applyFilters/load/loadMore`; `fetch(cursor)` builds `buildLogQuery(level,query,component)` and calls getAppLogs/getAuditLogs per tab, appends-or-replaces, sets nextCursor. App vs audit rows are distinct tappable composables toggling local `expanded` to reveal detail (app: attrs/requestId/clientIp; audit: `auditSummaryLine` + requestId/user/session). Use `logLevelRank` for the level badge color.

NOTE FOR IMPLEMENTER: reproduce the screen from the drafter's prose spec; READ the landed AdminLogEntry/AdminAuditEntry/LogPage field names + getAppLogs/getAuditLogs signatures and adapt (filters map vs baked cursor — the query builder covers both). Reuse InboxScreen's pagination wiring verbatim.

Register `viewModel { AdminLogsViewModel(get()) }`; wire `composable(Routes.AdminLogs) { AdminLogsScreen(onBackClick = { navController.popBackStack() }) }`.

- [ ] **Step 4: Run tests + manual checklist**

`./gradlew :androidApp:compileDebugKotlinAndroid :androidApp:testDebugUnitTest :androidApp:assembleDebug`

Manual: tabs switch + reset list/cursor; level dropdown + search + component filter; near-bottom auto-loads next page (footer spinner), no infinite refetch at null cursor; row expands to detail; audit row shows "METHOD path → status"; error+Retry; empty state.

- [ ] **Step 5: Commit**

`git commit -m "Add admin logs screen + log query builder

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>"`

---

### Task D3: AdminScansScreen + AdminScansViewModel (mobile)

**Files:**
- Create `androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/admin/AdminScansScreen.kt` (screen + co-located `AdminScansViewModel`)
- Modify `androidApp/src/androidMain/kotlin/com/continuum/app/android/di/AndroidModule.kt`
- Modify `androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/navigation/AppNavigation.kt` (overwrite `AdminScans` placeholder)

Library list = `PersonalDataRepository.listUserLibraries()`; UserLibrary has no last-scanned field → show name+type + transient action status only (revisit if a richer source appears). No extractable pure helper → no new unit test; manual-check only.

- [ ] **Step 1: Write the failing test** — n.a. (no extractable pure logic; manual-check only, Step 4).
- [ ] **Step 2: Run test to verify it fails** — n.a.
- [ ] **Step 3: Implementation**

Create `AdminScansScreen.kt` — `ContinuumTopBar(title="Scans")` with a "Scan all" action, `PullToRefreshBox` + `LazyColumn` of library rows (name + type + per-row Scan + Cancel buttons disabled while that row is busy). Co-located `AdminScansViewModel(adminRepository: AdminRepository, personalDataRepository: PersonalDataRepository)` with `AdminScansUiState(isLoading, isRefreshing, libraries, busyLibraryIds, scanningAll, error)` + toasts; `load()/refresh()` reads `listUserLibraries()` sorted by sortOrder; `scanLibrary(id)` → `triggerScan(ScanRequest(libraryId=id))`; `cancelLibrary(id)` → `cancelScan(id)`; `scanAll()` → `triggerScan(ScanRequest())`; each toasts result + refreshes; busy set tracks in-flight rows. (Reproduce the full source from the drafter output.)

NOTE FOR IMPLEMENTER: READ the landed AdminRepository scan signatures + UserLibrary fields and adapt; `triggerScan`/`cancelScan` may live on a libraries repository rather than AdminRepository — wire to whatever the shared layer landed.

Register `viewModel { AdminScansViewModel(get(), get()) }`; wire `composable(Routes.AdminScans) { AdminScansScreen(onBackClick = { navController.popBackStack() }) }`.

- [ ] **Step 4: Run tests + manual checklist**

`./gradlew :androidApp:compileDebugKotlinAndroid :androidApp:testDebugUnitTest :androidApp:assembleDebug`

Manual: library list loads; per-row Scan + Cancel + top-bar Scan-all; scan single → toast "Scan started"; scan all → "Scanning…" while in flight; cancel → toast "Scan cancelled"; row buttons disable while in flight; pull-to-refresh; error+Retry; empty state.

- [ ] **Step 5: Commit**

`git commit -m "Add admin scans screen

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>"`
